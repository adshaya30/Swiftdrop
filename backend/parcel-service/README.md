# Parcel service foundation — Member #4 Prompt 1, PR A

Port: **8082**. Prompt 1 supplies the shared parcel domain, schema and service configuration. Prompt 2 Phases 1-3 add customer creation, idempotency, pricing integration and owner-filtered customer queries. Lifecycle rules, merchant order APIs and receipts remain with their owning slices.

`domain.Parcel` is shared with Members #5 and #6. It uses common-core status/size enums, a generated UUID v4, `@Version`, embedded `PickupDetails`/`DropoffDetails`, and embedded origin/destination `ZoneSnapshot` values. Contacts, district/zone snapshots, ownership, tracking number, parcel details, and fee components are not updatable through the mapping. Lifecycle setters are persistence accessors only; transition policies belong to Member #5.

`ParcelRepository` provides Spring Data CRUD/specifications and tracking-number lookup. Other slices should consume this domain rather than duplicate it. The constructor takes the complete creation snapshot; API DTOs must remain separate from entities.

Flyway V1 creates every SRS 6.3 parcel-service table: `parcels`, `idempotency_keys`, and shared `outbox_events`. The merchant-reference unique index excludes CANCELLED and FAILED, but includes DELIVERED. Owner UUIDs have no cross-service foreign keys. `idempotency_keys` is storage only in PR A. Outbox behavior is reused from common-lib. Do not edit V1 after it has been applied.

Environment variables follow SRS 9.2 in `application.yml`. `PricingClient` uses the configured pricing URL through the shared internal client factory. This service does not configure or enforce the maximum parcel weight.

## Prompt 2 Phase 3 APIs and reuse

The CUSTOMER-only endpoints are `POST /api/v1/customer/parcels`, `GET /api/v1/customer/parcels` and `GET /api/v1/customer/parcels/{id}`. Creation takes the validated SRS 7.5 body and optional `Idempotency-Key`; owner identity comes exclusively from the authenticated user's `sub`. A new creation returns 201 with `Location`; replay returns the original status/body with `Idempotent-Replay: true`. Lists use the standard pagination envelope, owner filtering, inclusive Asia/Colombo dates and whitelisted sorting. Details exclude history, courier and proof. Cancellation and the optional dashboard are not implemented in this phase.

Member #6 can inject `create.service.ParcelCreationService` and call `create(CreationRequest)` using the Master 8.1 records in `create.dto`. The default implementation checks idempotency before discount resolution or pricing, compares `expectedFee` when present, and uses pricing's authoritative snapshots and fees. Supply the complete validated body's canonical hash, the trusted owner identity, discount resolver and response builder. Customer creation requires `expectedFee`; merchant callers may omit it under FR-PAR-007. The customer controller uses `CustomerParcelCreatedResponse`; merchant callers provide their own response builder.

Each write attempt uses a separate transaction for the parcel, successful key/response and shared `parcel.created` outbox event. Tracking collisions retry up to five attempts. A concurrent key loser rolls back all its writes before reading the winning response in a fresh transaction; no rollback-only transaction is reused. `IdempotencyService.storeSuccess` requires the caller's transaction, while lookup and hourly expiry cleanup use separate transactions. No new migrations or event infrastructure are introduced.

The Phase 3 PostgreSQL/MockMvc/WireMock tests exercise AT-08/09, collision retries, concurrency, rollback, event privacy, authorization and customer queries. These tests do not establish Docker image-build or deployed-container startup success.

## Prompt 2 Phase 4 integration status

`create.mapper.ParcelDetailMapper` now exposes `toDetail(Parcel)`, `toAdminDetail(Parcel)` and `toInternalDetail(Parcel)`. `AdminParcelDetailDto` has the customer detail fields plus `customerId` and `merchantId`; `InternalParcelDetailDto` has the admin fields plus `statusSequence` (Master 8.1 / SRS 7.4). All views use the same nested snapshot/fee contracts. The existing customer response shape is unchanged. Mapping does not authorize access: endpoint owners must apply their role and ownership checks before returning these views. No admin or internal endpoint is added here.

Customer cancellation is blocked: neither this branch nor the fetched `origin/develop` contains Member #5's `lifecycle.service.ParcelAccessPolicy.requireAccessible(UUID, CurrentUser)`, `ParcelTransitionService.cancelAsOwner(UUID, CurrentUser, String)` or agreed `TransitionResult(parcelId, trackingNumber, status, sequence, statusChangedAt)`. No substitute lifecycle classes or cancellation endpoint have been introduced. Request to Member #5: publish these contracts and implementations so Member #4 can wire owner cancellation without duplicating transition logic.

Remaining cancellation checks are AT-25 and the cancellation portion of TST-031: cancel CREATED, reject later statuses with `INVALID_STATUS_TRANSITION`, foreign-owner 404, wrong-role/client-token 403, unauthenticated 401, optional reason up to 300 characters, and the success body `{id, trackingNumber, status, statusChangedAt}`. These checks cannot be reported as passing until the integration is available. The optional dashboard remains deferred because this Must integration is incomplete.

## Verification

Use Java 21. From `backend/`:

```powershell
.\mvnw.cmd -pl pricing-service,parcel-service -am verify
```

PostgreSQL migration, repository, startup, health, and optimistic-lock tests require Docker. The dependency also runs RabbitMQ Testcontainers tests. Missing Docker causes a visible failure, not a skipped test. Security MVC and offline Hibernate mapping tests run without Docker; they do not replace database validation.

Build the image with `backend/` as context and `parcel-service/Dockerfile`. Its specific ignore file preserves the Maven wrapper. The builder runs `verify` without skipping tests and therefore needs a reachable Docker daemon for Testcontainers; `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` are accepted build arguments. Runtime is non-root with UTC and a database-only readiness check.

## Request to owner: Member #8

Please change the existing Java/compiler version values and compiler-plugin release from 17 to 21 in `backend/pom.xml` (SRS D-30, Master section 3). The parent is unchanged by PR A; running Maven on Java 21 currently still compiles with release 17.
