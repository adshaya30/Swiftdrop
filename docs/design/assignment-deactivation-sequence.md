# Delivery Assignment and Courier Deactivation Sequences

These sequences follow the current delivery-service assignment flow and the auth-service courier deactivation flow. The services own separate databases; event propagation between them is asynchronous through their transactional outboxes and RabbitMQ.

## Admin assignment and reassignment

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant SPA as Admin frontend
    participant Controller as delivery-service AdminDeliveryController
    participant Service as DeliveryAdminService
    participant DB as delivery-service PostgreSQL
    participant Publisher as delivery-service OutboxPublisher
    participant Rabbit as RabbitMQ

    Admin->>SPA: Choose courier and explicitly confirm
    SPA->>Controller: POST /api/v1/admin/deliveries/{parcelId}/assign {courierId}
    Controller->>Service: assign(parcelId, courierId)
    Service->>DB: BEGIN transaction
    Service->>DB: SELECT delivery FOR UPDATE by parcelId
    Note over Service,DB: Pessimistic write lock serializes assignment and status changes.
    alt Delivery does not exist
        Note over Service,DB: Transaction rolls back.
        Service-->>Controller: NOT_FOUND domain error
        Controller-->>SPA: 404 NOT_FOUND
    else Delivery exists
        Service->>DB: Find courier by courierId
        alt Courier does not exist
            Note over Service,DB: Transaction rolls back.
            Service-->>Controller: NOT_FOUND domain error
            Controller-->>SPA: 404 NOT_FOUND
        else Courier exists
            alt Delivery is not CREATED or courier is not ACTIVE
                Note over Service,DB: No assignment changes; transaction rolls back.
                Service-->>Controller: PARCEL_NOT_ASSIGNABLE domain error
                Controller-->>SPA: 409 PARCEL_NOT_ASSIGNABLE
            else Delivery is CREATED and courier is ACTIVE
                alt courierId equals current delivery courierId
                    Note over Service,DB: Same-courier assignment is a no-op; no new history, outbox, or audit row.
                    Service-->>Controller: Existing delivery detail
                    Controller-->>SPA: 200 OK
                else Courier changes
                    opt An assignment is already open
                        Service->>DB: Set prior delivery_assignments.unassigned_at
                    end
                    Service->>DB: Set deliveries.courier_id and assigned_at
                    Service->>DB: INSERT new delivery_assignments history row
                    Service->>DB: INSERT delivery.assigned outbox event
                    Service->>DB: INSERT audit.recorded outbox event
                    Note over Service,DB: Business changes and both outbox rows participate in this transaction.
                    Service->>DB: COMMIT
                    Service-->>Controller: Delivery detail and assignment history
                    Controller-->>SPA: 200 OK
                end
            end
        end
    end
    Note over Publisher,DB: The scheduled publisher reads committed unpublished outbox rows.
    Publisher->>DB: Lock unpublished batch
    Publisher->>Rabbit: Publish persistent event and await publisher confirm
    alt Broker confirms delivery
        Publisher->>DB: Mark event published
    else Publish fails, is nacked, or is unroutable
        Publisher->>DB: Increment attempts; leave event unpublished
        Note over Publisher,Rabbit: A later publisher run retries the event.
    end
```

The service validates both the delivery state and courier status after locking the delivery row. A missing delivery or courier is a `NOT_FOUND` domain error; an ineligible delivery or inactive courier is a `PARCEL_NOT_ASSIGNABLE` conflict. A same-courier request returns existing details after eligibility checks and before writing a new assignment, outbox event, or audit record.

## Courier deactivation

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant Auth as auth-service AdminUserService
    participant AuthDB as auth-service PostgreSQL
    participant DeliveryAPI as delivery-service InternalCourierController and service
    participant DeliveryDB as delivery-service PostgreSQL
    participant AuthPublisher as auth-service OutboxPublisher
    participant Rabbit as RabbitMQ
    participant Listener as delivery-service DeliveryEventsListener
    participant Handler as delivery-service DeliveryEventHandler
    participant DeliveryPublisher as delivery-service OutboxPublisher

    Admin->>Auth: Request courier deactivation
    Auth->>AuthDB: BEGIN auth transaction
    Auth->>AuthDB: Read user and courier profile
    Auth->>Auth: Check self-deactivation and current status
    Auth->>DeliveryAPI: GET /internal/v1/couriers/{id}/active-count (X-Internal-Key)
    DeliveryAPI->>DeliveryDB: Count PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY deliveries
    DeliveryDB-->>DeliveryAPI: inProgress count
    DeliveryAPI-->>Auth: inProgress count
    alt Delivery service unavailable or returns an error
        Auth->>AuthDB: ROLLBACK auth transaction
        Auth-->>Admin: Reject deactivation with service-unavailable error
    else inProgress is greater than zero
        Auth->>AuthDB: ROLLBACK auth transaction
        Auth-->>Admin: Reject deactivation; courier has active deliveries
    else No in-progress deliveries
        Auth->>AuthDB: Set user and courier profile status to DEACTIVATED
        Auth->>AuthDB: Revoke active refresh tokens
        Auth->>AuthDB: Write courier.changed and audit.recorded outbox rows
        Auth->>AuthDB: COMMIT
        Auth-->>Admin: Deactivation response
        AuthPublisher->>AuthDB: Read committed unpublished outbox row
        AuthPublisher->>Rabbit: Publish courier.changed
        Rabbit-->>Listener: Deliver courier.changed
        Listener->>Handler: Handle event
        Handler->>DeliveryDB: BEGIN delivery transaction
        Handler->>DeliveryDB: Insert event id in processed_events
        alt Event id was already processed
            Handler->>DeliveryDB: COMMIT without repeating changes
        else First delivery of event
            Handler->>DeliveryDB: Upsert courier as DEACTIVATED
            Handler->>DeliveryDB: Lock only this courier's CREATED deliveries
            loop Each matching CREATED delivery
                Handler->>DeliveryDB: Clear delivery courier_id and assigned_at
                Handler->>DeliveryDB: Close its open delivery_assignments row
                Handler->>DeliveryDB: Write delivery.unassigned outbox row
            end
            Note over Handler,DeliveryDB: PICKED_UP, IN_TRANSIT, and OUT_FOR_DELIVERY deliveries are not selected or unassigned.
            Handler->>DeliveryDB: COMMIT
        end
        DeliveryPublisher->>DeliveryDB: Read committed unpublished delivery.unassigned rows
        DeliveryPublisher->>Rabbit: Publish delivery.unassigned
    end
```

The count endpoint returns the number of deliveries in `PICKED_UP`, `IN_TRANSIT`, or `OUT_FOR_DELIVERY`; auth-service rejects deactivation when that count is positive. If deactivation succeeds, delivery-service handles the subsequently published `courier.changed` event in its own transaction. Its query locks only `CREATED` deliveries, closes each current assignment, clears the current courier reference, and writes one `delivery.unassigned` outbox row per delivery. In-progress deliveries are not changed by this consumer. The delivery consumer also records event IDs transactionally so a previously processed event is skipped.
