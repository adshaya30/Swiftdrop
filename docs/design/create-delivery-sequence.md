# Customer creates a delivery

SRS §4.2, FR-PRC-002/004/009, FR-PAR-004/005/006, FR-UI-004/007 and BR-28.
Sources: `DefaultParcelCreationService`, `IdempotencyService`, `CustomerParcelController`, pricing public/internal controllers and the customer wizard.

```mermaid
sequenceDiagram
    actor Customer
    participant SPA as Customer SPA
    participant GW as API gateway
    participant Pricing as pricing-service
    participant PricingDB as Pricing database
    participant Parcel as parcel-service
    participant DB as Parcel database (parcels and keys)
    participant Outbox as Parcel database (outbox_events)
    participant Publisher as OutboxPublisher
    participant MQ as RabbitMQ
    participant Tracking as tracking-service
    participant Delivery as delivery-service
    participant Notify as notification-service
    participant Auth as auth-service

    Customer->>SPA: Enter pickup, drop-off and parcel details
    SPA->>GW: GET /api/v1/public/districts
    GW->>Pricing: Forward reference-data request
    Pricing->>PricingDB: Read active districts and sizes
    Pricing-->>GW: Districts, sizes and configured maxWeightKg
    GW-->>SPA: Reference data
    Note over Customer,SPA: Validate against server reference data; retain inputs
    SPA->>GW: POST /api/v1/public/quotes (400 ms debounce)
    GW->>Pricing: Forward quote inputs
    Pricing->>PricingDB: Read district zones, directed pricing and size surcharge
    Pricing-->>GW: Authoritative LKR fee breakdown
    GW-->>SPA: Quote response
    Customer->>SPA: Review confirmation screen
    Note over SPA: Generate one crypto.randomUUID() key; freeze body and quoted expectedFee
    Customer->>SPA: Confirm delivery
    Note over SPA: Disable submission; synchronous duplicate-request guard
    SPA->>GW: POST /api/v1/customer/parcels (Bearer, Idempotency-Key, expectedFee)
    GW->>Parcel: Forward request
    Parcel->>Parcel: Authenticate CUSTOMER, validate body and canonical request hash
    Note over Parcel,DB: Idempotency lookup precedes pricing and fee comparison
    Parcel->>DB: Lookup (CUSTOMER, authenticated user id, key), within 24 h
    alt Stored result and same hash
        DB-->>Parcel: Stored status/body and parcel id
        Parcel-->>GW: Stored 201, Location, Idempotent-Replay: true
        GW-->>SPA: Original response even after a later price change
    else Stored key with different hash
        Parcel-->>GW: 422 IDEMPOTENCY_KEY_REUSED
        GW-->>SPA: Show rejection; no creation
    else No reusable stored result
        Parcel->>Pricing: POST /internal/v1/pricing/calculate (X-Internal-Key, customer discount 0)
        Pricing->>PricingDB: Read and calculate authoritative price and snapshots
        alt Pricing validation, business error or outage
            Pricing-->>Parcel: 400 or 422 business error, or unavailable
            Parcel-->>GW: Business error unchanged, or 503 SERVICE_UNAVAILABLE
            GW-->>SPA: Preserve input; no successful key stored
        else Calculated fee differs from expectedFee
            Pricing-->>Parcel: Current fee and snapshots
            Parcel-->>GW: 409 FEE_CHANGED with fee extension
            GW-->>SPA: No parcel or successful key created
            Note over Customer,SPA: Display server fee; require review and a new confirmation key
            Note over SPA: Current shared-client gap: if fee is absent, request a fresh public quote first
        else Calculated fee matches expectedFee
            Pricing-->>Parcel: Current fee and snapshots
            rect rgb(240, 245, 250)
                Note over Parcel,Outbox: One database transaction; all writes commit or roll back together
                Parcel->>DB: BEGIN; generate tracking number; insert CREATED parcel and snapshots
                opt Idempotency key supplied
                    Parcel->>DB: Insert successful key, canonical hash and response body
                end
                Parcel->>Outbox: Insert contact-free parcel.created event
                Parcel->>DB: COMMIT
            end
            Parcel-->>GW: 201 with Location, parcel id, tracking number and fee
            GW-->>SPA: Successful creation response
        end
    end
    opt Confirmed creation or replay
        SPA->>SPA: Navigate to /customer/parcels/{id}
    end
    Note over SPA,Parcel: Uncertain network/5xx result: retry the same frozen body and key, not a new delivery
    opt New creation committed (not replay)
        Publisher->>Outbox: Poll unpublished committed events
        Publisher->>MQ: Publish parcel.created
        MQ-->>Tracking: Consume; create tracking record and initial timeline
        MQ-->>Delivery: Consume; create unassigned delivery
        MQ-->>Notify: Consume creation notification
        Notify->>Auth: Fetch sender contact through authenticated internal API
        Notify->>Notify: Send sender notifications; do not store contact data in event rows
    end
```

The asynchronous publish section applies only to a newly committed creation; replay does not insert another parcel, key or outbox event. Event delivery is independent of the HTTP response and can lag behind it. The intended composed detail page retries a temporary timeline 404 every two seconds for up to 30 seconds; Member #9's authenticated frontend timeline contract is currently missing.

On a concurrent duplicate key constraint violation, the losing transaction rolls back, then `DefaultParcelCreationService` re-reads and replays the winner. Tracking-number unique collisions retry up to five transaction attempts. Failed requests store no successful idempotency result; expired keys are treated as absent and purged hourly. The key is optional in the API but always supplied by the wizard.

The pricing and parcel database lifelines are separate databases. Outbox is a logical table lifeline in the **same parcel database**, not another service/database. Consumers own their own databases. Contact data is absent from `parcel.created`; its allowed payload contains ids, tracking/status, district/zone names, cities and creation time.
