# MEMBER #6: Merchant orders, receipts and the events library (3 prompts)

The master prompt (v3.0) and the SRS are already loaded in your session.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | common-lib events, outbox, consumer helpers, audit events | **Yes: every publisher and consumer** (week 1) |
| 2 | parcel-service order slice, receipts, dashboard, internal endpoints | **Yes**: #8 needs the internal transition endpoint, #9 needs the internal recipient endpoint |
| 3 | Merchant frontend, Postman tools, diagrams | No |

---

## PROMPT 1 of 3: events library (common-lib events part)

You are **Member #6**. You own the `com.swiftdrop.common.events` package of `backend/common-lib` (and its tests). #3 owns the common-lib `pom.xml`, which already declares your dependencies, and created an empty `EventsAutoConfiguration` that you now own and fill. Deliver in **two pull requests**: PR A (topology, envelope, outbox, audit) first, PR B (consumer helpers and resilience tests).

**Preconditions:** #3's common-core and common-lib PR 2 merged (`ErrorCode`, `CurrentUsers`, test support, `pom.xml`).

### PR A: envelope, topology, outbox, audit
1. **`EventEnvelope<T>`** (`eventId` uuid, `eventType`, `schemaVersion` 1, `occurredAt` ISO UTC, `correlationId` uuid, `data`), **event data records** for every event of SRS 3.5: `ParcelCreatedData, ParcelStatusChangedData, DeliveryAssignedData, DeliveryUnassignedData, CourierChangedData, MerchantStatusChangedData, AuditRecordedData` with exactly the listed fields (camelCase, `?` = nullable), **`EventTypes`** constants equal to the routing keys (`parcel.created, parcel.status.changed, delivery.assigned, delivery.unassigned, courier.changed, merchant.status.changed, audit.recorded`).
2. **`EventTopology`** (declarables): exchange `swiftdrop.events` (topic, durable); dead-letter exchange `swiftdrop.dlx` (direct, durable); queues `tracking.events`, `delivery.events`, `notification.events` (durable, `x-dead-letter-exchange=swiftdrop.dlx`, dead-letter key `<queue>.dlq`) with the bindings of SRS 3.5 (`tracking.events`: `parcel.created, parcel.status.changed, delivery.assigned, delivery.unassigned, audit.recorded`; `delivery.events`: `parcel.created, parcel.status.changed, courier.changed`; `notification.events`: `parcel.created, parcel.status.changed, merchant.status.changed`) and three durable `<queue>.dlq` queues with `x-message-ttl` of 3 days. Every publisher and consumer declares it at startup through the auto-configuration; **a missing or down broker must not stop a service from starting** (retry declaration on connection, `missing-queues-fatal=false`), so services start in any order.
3. **Outbox:** `OutboxEvent` entity and repository mapped to the shared `outbox_events` table (`id` = `eventId`, `event_type`, `routing_key` `VARCHAR(40)`, `payload JSONB` = the full envelope, `correlation_id`, `created_at`, `published_at`, `attempts`); register the entity and repository packages through the auto-configuration so services need no `@EntityScan`. **`OutboxService.add(routingKey, data)`** builds the envelope (random `eventId`, `occurredAt` now, `correlationId` from MDC `requestId` when it is a UUID, otherwise a fresh UUID) and inserts the row; it must run inside an existing transaction (`Propagation.MANDATORY`).
4. **`OutboxPublisher`** scheduled every `OUTBOX_POLL_INTERVAL_MS` (default 1000): in a transaction read up to 100 unpublished rows ordered by `created_at` with `FOR UPDATE SKIP LOCKED` (native query), publish one by one with a `RabbitTemplate` configured for **publisher confirms and mandatory**, persistent messages and AMQP properties `contentType=application/json`, `deliveryMode=2`, `messageId=eventId`, `correlationId`, `type=eventType`; wait for the confirm (bounded timeout); on confirm set `published_at`; an unroutable (returned) or unconfirmed message leaves the row unpublished and increments `attempts`, and the batch stops at the first failure so a down broker is not hammered. **If the broker is down, business transactions still succeed** and events wait in the outbox. (S) a daily job deletes published rows older than 1 day; (S) a Micrometer gauge for the outbox backlog.
5. **`AuditEvents.record(action, entityType, entityId, details)`** (actor from `CurrentUsers`) plus an overload with explicit `actorUserId, actorRole` for system actors: writes `audit.recorded` with data `service` (from `spring.application.name`), `actorUserId`, `actorRole`, `action`, `entityType`, `entityId`, `details`, `occurredAt`. Defensive guard: reject detail keys such as `password`, `secret`, `token`, `email`, `phone`, `name`, `address` (throw `IllegalArgumentException` so mistakes are caught in tests).
6. Unit and integration tests (Testcontainers PostgreSQL + RabbitMQ): envelope JSON shape, topology (queue arguments, DLQ TTL, bindings), outbox row written in the caller's transaction and rolled back with it, publisher publishes in order, **two publishers never publish the same row** (SKIP LOCKED), unroutable message stays unpublished, `OutboxService` refuses to run without a transaction, audit guard.

### PR B: consumer helpers and resilience
1. **`EventConsumerSupport`**: base class/helper for `@RabbitListener` methods with **manual acknowledge**, **prefetch 10**, JSON converter (`Jackson2JsonMessageConverter`, `EventEnvelope<T>` by `eventType`), `correlationId` put in MDC while handling, and **3 attempts in total with waits of 1 s then 5 s** (Spring `RetryTemplate`, exponential multiplier 5; make the delays configurable for tests). Success → `basicAck`; after the last failure → `basicNack` without requeue so the message goes to the DLQ. A distinct `RetryableEventException` for "not ready yet" cases (for example a status event for a parcel the consumer does not know yet).
2. **`ProcessedEvents.firstTime(UUID eventId)`** with a `ProcessedEvent` entity for the shared `processed_events` table (used by delivery-service and tracking-service only): inserts in the **caller's transaction**; a duplicate means "already processed", acknowledge and skip.
3. **Tests:** **TST-025** (with a test application in the module): with RabbitMQ stopped a business transaction that adds an outbox row **still commits**, the row stays unpublished, and after the container restarts the event is published; **TST-026 (S):** an always-failing message ends in the DLQ after exactly 3 attempts; redelivery of the same `eventId` is processed once; stale handling is the services' job. Use Awaitility (no `Thread.sleep`). Coverage at least 70 % for your package.
4. Add `backend/common-lib/EVENTS.md` (how to publish, consume, audit, test) with short code examples, and tell all members in one line that PR A is merged (publishers) and PR B (consumers).

### Done when
`mvn -pl backend/common-lib -am verify` green with coverage gate, a service with only the dependency plus a datasource can publish and consume. Report sent.

---

## PROMPT 2 of 3: parcel-service order slice, receipts, dashboard, internal endpoints

You are **Member #6**. Package `com.swiftdrop.parcel.order.{controller,service,repository,dto,mapper,client,security}`. You reuse #4's `ParcelCreationService`, `IdempotencyService`, `ParcelDetailMapper` and #5's `ParcelAccessPolicy` and `ParcelTransitionService` exactly as in master 8.1. Never re-implement idempotency, pricing calls or transitions.

**Preconditions:** your Prompt 1 merged; #4 Prompt 2 and #5 Prompt 1 merged (mock with Mockito/WireMock until then and say so); #3's auth internal endpoints (WireMock of the SRS 7.4 contract until merged).

### Requirements
- **Access rule for orders:** role MERCHANT; a dashboard user token (`typ=user`) is allowed on every order endpoint; a client token (`typ=client`, the only place common-lib accepts it) needs scope `orders:write` for create and cancel and `orders:read` for list and detail; a token with only `orders:read` cannot create (403 `ACCESS_DENIED`). Owner = the `mid` claim.
- **FR-PAR-007 `POST /api/v1/merchant/orders`:** body as FR-PAR-005 plus `merchantReference?` (up to 64); `expectedFee` optional (compared only when present). Use `ParcelCreationService.create` with `ownerType=MERCHANT`, `ownerId=mid`, your `discountPercentResolver` and `responseBuilder`. Order (Flow 4.3): idempotency lookup **first**; then the resolver calls auth-service `GET /internal/v1/merchants/{mid}` (`order.client.AuthClient`) and returns 403 `MERCHANT_NOT_ACTIVE` unless the merchant is `APPROVED` **and** `userStatus` is `ACTIVE` (BR-23: this database re-check happens on **every** create and cancel); then pricing with the merchant `discountPercent`; then `merchantReference` uniqueness among parcels not CANCELLED or FAILED (409 `DUPLICATE_MERCHANT_REFERENCE`, also when the partial unique index trips in a race). 201 `{id, trackingNumber, status, merchantReference, fee{currency, baseFee, weightFee, sizeSurcharge, discountAmount, total}, trackingUrl, createdAt}` with `trackingUrl = PUBLIC_BASE_URL + /track/{trackingNumber}` and `Location: /api/v1/merchant/orders/{id}`; `Idempotency-Key` behaves exactly as for customers with owner type MERCHANT and owner id = `mid`.
- **FR-PAR-008:** `GET /api/v1/merchant/orders` (own orders; filters `status`, `from`, `to`, `merchantReference`, `q`; pagination and sorting as FR-PAR-009), `GET /api/v1/merchant/orders/{id}` (detail via `ParcelDetailMapper`; another merchant's order → 404), `POST /api/v1/merchant/orders/{id}/cancel` (optional `{"reason" up to 300}`; merchant re-checked as above; `ParcelTransitionService.cancelAsOwner`; `200 {id, trackingNumber, status, statusChangedAt}`; non-CREATED → 409 `INVALID_STATUS_TRANSITION`).
- **FR-PAR-010 receipt** `GET /api/v1/parcels/{id}/receipt`: roles CUSTOMER, MERCHANT, ADMIN only (`@PreAuthorize`, so a COURIER gets 403 **before** the policy; a client token gets 403 from common-lib); a non-owner customer or merchant gets 404 (`ParcelAccessPolicy`). Generate on demand with OpenPDF: "SwiftDrop" name, "Delivery Receipt" title, receipt number = tracking number, issue date (Asia/Colombo), pickup and drop-off contact names and addresses, parcel details (weight, size, description), fee breakdown with currency, current status label, and for delivered parcels the delivery time (`terminal_at`, Asia/Colombo). `Content-Type: application/pdf`, filename `SwiftDrop-Receipt-<trackingNumber>.pdf` in `Content-Disposition`, `Cache-Control: no-store`. User text is plain text in the PDF (no markup interpretation).
- **FR-PAR-014** `GET /api/v1/merchant/dashboard/summary` (dashboard login only: client tokens 403): `{activeCounts{CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY}, last30DaysCounts{all seven statuses}, recentOrders[10 × {id, trackingNumber, status, merchantReference, dropoffCity, createdAt}]}` (state this interpretation in your report).
- **FR-PAR-016 internal endpoints** (`X-Internal-Key`, never through the gateway): `GET /internal/v1/parcels/{id}` → `InternalParcelDetailDto` (admin detail + `statusSequence`, plus `updatedAt`); `GET /internal/v1/parcels/{id}/recipient` → `{name, phone, email}` of the drop-off contact; `POST /internal/v1/parcels/{id}/transition` body `{toStatus, actorUserId, actorRole, note?, failureReason?, recipientName?}` (`actorRole` must be `COURIER` or `ADMIN`, else 400) → `ParcelTransitionService.transition`, answers `{parcelId, status, sequence, statusChangedAt}`; business errors are returned as Problem Details with their code; unknown parcel 404.
- `SecurityRules`: no public paths. OpenAPI annotations, DTOs and MapStruct.

### Tests (Testcontainers PostgreSQL + RabbitMQ, WireMock for auth-service, TestTokens)
- **TST-021 merchant part:** order created with the merchant discount (**AT-07** numbers through WireMock pricing: 1,620.00), duplicate `merchantReference` (also after CANCELLED or FAILED it is allowed again), merchant not approved or user not active → `MERCHANT_NOT_ACTIVE`, idempotent replay and key reuse for merchants, `expectedFee` optional and compared when present, atomicity, outbox payload without contact data.
- **TST-032 (orders part):** merchant cannot read or cancel another merchant's order (404); a client token works only on `/merchant/orders*` and gets 403 on receipt, timeline, dashboard summary and customer/admin endpoints; a token with only `orders:read` cannot create or cancel; suspended merchants refused on create **and** cancel (re-check at order time).
- **TST-031 receipt part:** a customer cannot download another customer's receipt (404), a COURIER gets 403, ADMIN any, merchant owner own; **AT-30:** the PDF contains the required fields (extract text with OpenPDF's reader) and the delivery time for delivered parcels.
- **TST-025 parcel part:** with RabbitMQ stopped a transition through the internal endpoint still succeeds and the outbox row stays unpublished until restart.
- Internal endpoints: missing or wrong key → 401, valid key works, unknown parcel 404, `actorRole` validation. Scenario **AT-14** checked. Coverage at least 70 %.

### Done when
All endpoints work, `mvn -pl backend/parcel-service -am verify` green. Tell #8 and #9 in one line that the internal endpoints are merged. Report sent.

---

## PROMPT 3 of 3: merchant frontend, Postman tools and diagrams

You are **Member #6**. Folders `frontend/src/pages/merchant/dashboard`, `pages/merchant/orders`, `pages/merchant/components/ReceiptButton.jsx`; API files `src/api/merchantOrders.js`, `src/api/receipts.js`; and `tools/postman/`. Use only the shared client, components and layouts of #1 (master 8.1). **There is no create-order form** (orders are API only, D-23).

### Frontend
1. **`/merchant/dashboard`** (`pages/merchant/dashboard`): cards for orders by status (active and last 30 days) and the 10 most recent orders from `GET /merchant/dashboard/summary`; a webhook-health card using `getWebhookSummary` from `src/api/webhook.js` (#10): `failed24h`, `pending`, `succeeded24h`, and shows **"unavailable"** when notification-service does not answer, without breaking the rest of the page; refresh every 30 s (FR-UI-008). A short info panel says orders are created through the API and points to the credentials page.
2. **`/merchant/orders`** (`pages/merchant/orders`): list with filters `status`, `from`, `to`, `merchantReference`, `q`, sorting and pagination; `StatusChip`; cancel action only for CREATED with `ConfirmDialog` (optional reason up to 300); handles `MERCHANT_NOT_ACTIVE` and `INVALID_STATUS_TRANSITION`.
3. **`/merchant/orders/:id`:** composes order detail (`GET /merchant/orders/{id}`), timeline (`getTimeline`, `ParcelTimeline`), proof only when DELIVERED (`ProofViewer`), receipt, cancel; refetch on every status event through `useTrackingStream` (FR-UI-007; keep a thin adapter in your own folder until #9 and #8 are merged).
4. **`ReceiptButton`** (`pages/merchant/components/ReceiptButton.jsx`, named + default export, reused by customer and admin detail pages): fetches the PDF through `apiBlob` with the bearer token (`src/api/receipts.js`, `downloadReceipt(parcelId)`), saves it from the blob as `SwiftDrop-Receipt-<trackingNumber>.pdf` and revokes the object URL; never `<a href>` to the API or `window.open`. Loading and error states.
5. API files with one function per endpoint: `merchantOrders.js` (`listOrders`, `getOrder`, `cancelOrder`, `getDashboardSummary`; `createOrder` is not needed in the UI), `receipts.js`.
6. **Tests** (`describe("TST-040 ...")`): dashboard shows "unavailable" for the webhook card on failure, 30 s refresh, order filters, cancel confirmation and errors, receipt button uses the blob flow and never opens a URL, detail refetch on status events. Accessibility and 360 px checks.

### tools/postman (DEP-015)
A Postman collection `tools/postman/SwiftDrop-Merchant.postman_collection.json` and a curl script `tools/postman/merchant-demo.sh` (LF line endings, relative to `http://localhost:8080`, variables for client id and secret): get a token (`POST /api/v1/oauth/token`, form-encoded), create an order, **replay it with the same `Idempotency-Key`** (shows `Idempotent-Replay: true`), duplicate `merchantReference` (409), invalid district (400), list orders, cancel. A short `tools/postman/README.md` explains the steps.

### Docs (Mermaid in `docs/design/`)
- `merchant-order-sequence.md`: Flow 4.3 (token, order, outbox, notification consumer, webhook row, scheduler, signed POST, merchant verification, retry) as a sequence diagram.
- `integration-events.md`: the integration and event design of SRS 3.4-3.6: shared libraries, topology diagram with exchanges, queues, bindings and DLQs, envelope, outbox and consumer reliability rules, token design and the client-token rule.

### Done when
Pages work against the running services, `npm test -- --coverage` and `npm run build` pass, the Postman script runs end to end, diagrams committed, report sent. Scenarios AT-14, AT-30 checked.
