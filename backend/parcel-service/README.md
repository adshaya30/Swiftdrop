# Parcel service foundation — Member #4 Prompt 1, PR A

Port: **8082**. Prompt 1 supplies the shared parcel domain, schema and service configuration. Prompt 2 Phases 1-3 add customer creation, idempotency, pricing integration and owner-filtered customer queries. Lifecycle rules, merchant order APIs and receipts remain with their owning slices.

`domain.Parcel` is shared with Members #5 and #6. It uses common-core status/size enums, a generated UUID v4, `@Version`, embedded `PickupDetails`/`DropoffDetails`, and embedded origin/destination `ZoneSnapshot` values. Contacts, district/zone snapshots, ownership, tracking number, parcel details, and fee components are not updatable through the mapping. Lifecycle setters are persistence accessors only; transition policies belong to Member #5.

`ParcelRepository` provides Spring Data CRUD/specifications and tracking-number lookup. Other slices should consume this domain rather than duplicate it. The constructor takes the complete creation snapshot; API DTOs must remain separate from entities.

Flyway V1 creates every SRS 6.3 parcel-service table: `parcels`, `idempotency_keys`, and shared `outbox_events`. The merchant-reference unique index excludes CANCELLED and FAILED, but includes DELIVERED. Owner UUIDs have no cross-service foreign keys. `idempotency_keys` is storage only in PR A. Outbox behavior is reused from common-lib. Do not edit V1 after it has been applied.

Environment variables follow SRS 9.2 in `application.yml`. `PricingClient` uses the configured pricing URL through the shared internal client factory. This service does not configure or enforce the maximum parcel weight.

## Prompt 2 Phase 3 APIs and reuse

The CUSTOMER-only endpoints are `POST /api/v1/customer/parcels`, `GET /api/v1/customer/parcels` and `GET /api/v1/customer/parcels/{id}`. Creation takes the validated SRS 7.5 body and optional `Idempotency-Key`; owner identity comes exclusively from the authenticated user's `sub`. A new creation returns 201 with `Location`; replay returns the original status/body with `Idempotent-Replay: true`. Lists use the standard pagination envelope, owner filtering, inclusive Asia/Colombo dates and whitelisted sorting. Details exclude history, courier and proof. Cancellation is integrated below; the optional dashboard remains deferred.

Member #6 can inject `create.service.ParcelCreationService` and call `create(CreationRequest)` using the Master 8.1 records in `create.dto`. The default implementation checks idempotency before discount resolution or pricing, compares `expectedFee` when present, and uses pricing's authoritative snapshots and fees. Supply the complete validated body's canonical hash, the trusted owner identity, discount resolver and response builder. Customer creation requires `expectedFee`; merchant callers may omit it under FR-PAR-007. The customer controller uses `CustomerParcelCreatedResponse`; merchant callers provide their own response builder.

Each write attempt uses a separate transaction for the parcel, successful key/response and shared `parcel.created` outbox event. Tracking collisions retry up to five attempts. A concurrent key loser rolls back all its writes before reading the winning response in a fresh transaction; no rollback-only transaction is reused. `IdempotencyService.storeSuccess` requires the caller's transaction, while lookup and hourly expiry cleanup use separate transactions. No new migrations or event infrastructure are introduced.

The Phase 3 PostgreSQL/MockMvc/WireMock tests exercise AT-08/09, collision retries, concurrency, rollback, event privacy, authorization and customer queries. These tests do not establish Docker image-build or deployed-container startup success.

## Prompt 2 Phase 4 integration status

`create.mapper.ParcelDetailMapper` now exposes `toDetail(Parcel)`, `toAdminDetail(Parcel)` and `toInternalDetail(Parcel)`. `AdminParcelDetailDto` has the customer detail fields plus `customerId` and `merchantId`; `InternalParcelDetailDto` has the admin fields plus `statusSequence` (Master 8.1 / SRS 7.4). All views use the same nested snapshot/fee contracts. The existing customer response shape is unchanged. Mapping does not authorize access: endpoint owners must apply their role and ownership checks before returning these views. No admin or internal endpoint is added here.

Customer cancellation is integrated with Member #5: `POST /api/v1/customer/parcels/{id}/cancel` requires a CUSTOMER user token and obtains the caller from `CurrentUsers.get()`. It calls `lifecycle.service.ParcelTransitionService.cancelAsOwner(UUID, CurrentUser, String)`, which invokes `lifecycle.security.ParcelAccessPolicy.requireAccessible(UUID, CurrentUser)` before applying the authoritative transition. No lifecycle rules are duplicated. `TransitionResult.parcelId()` maps to response `id`; sequence stays internal. The response is `{id, trackingNumber, status, statusChangedAt}`.

The request body may be absent or contain an optional `reason` of at most 300 characters. Bean Validation rejects longer reasons. Real lifecycle integration tests cover CREATED cancellation, post-pickup rejection, ownership concealment, role/token authentication and reason boundaries. Creation tests also verify pricing's maximum-weight error passthrough, creation without a key, and rollback after an actual outbox insert.

Springdoc annotations describe all four customer operations, JWT authentication, schemas, creation success headers and error responses. `ParcelOpenApiTests` inspects the generated specification and exports it to `target/generated-openapi/parcel-service.json`. The parcel POM checks each matching service, messaging and security PACKAGE separately against the inherited 70% LINE minimum; its selectors use dotted Java package names (slash paths do not match PACKAGE rules), and violations halt the build.

The optional FR-PAR-015 dashboard remains deferred.

## Verification

Use Java 21. From `backend/`:

```powershell
.\mvnw.cmd -pl pricing-service,parcel-service -am verify
```

PostgreSQL migration, repository, startup, health, and optimistic-lock tests require Docker. The dependency also runs RabbitMQ Testcontainers tests. Missing Docker causes a visible failure, not a skipped test. Security MVC and offline Hibernate mapping tests run without Docker; they do not replace database validation.

Build the image with `backend/` as context and `parcel-service/Dockerfile`. Its specific ignore file preserves the Maven wrapper. The builder runs `verify` without skipping tests and therefore needs a reachable Docker daemon for Testcontainers; `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` are accepted build arguments. Runtime is non-root with UTC and a database-only readiness check.

## Request to owner: Member #8

Please change the existing Java/compiler version values and compiler-plugin release from 17 to 21 in `backend/pom.xml` (SRS D-30, Master section 3). The parent is unchanged by PR A; running Maven on Java 21 currently still compiles with release 17.

## Final cancellation verification (2026-11-11)

The exact clean command below completed with BUILD SUCCESS on Temurin Java 21.0.13 and Docker Engine 29.9.2. Set JAVA_HOME to the installed Java 21 JDK first: this workstation's default JAVA_HOME points to Java 25.

```powershell
.\backend\mvnw.cmd -f backend/pom.xml -B -ntp -pl pricing-service,parcel-service -am clean verify
```

| Module | Passed | Failed | Errored | Skipped |
|---|---:|---:|---:|---:|
| common-core | 19 | 0 | 0 | 0 |
| common-lib | 127 | 0 | 0 | 0 |
| parcel-service | 209 | 0 | 0 | 0 |
| pricing-service | 87 | 0 | 0 | 0 |

AT-08/09, the customer AT-25 cases, cancellation TST-031 checks, reason boundaries, above-maximum pricing error passthrough, keyless creation and rollback after a real outbox insertion all pass. Rollback assertions leave zero parcel, idempotency and outbox rows for the request owner. The generated OpenAPI test passes and exports `target/generated-openapi/parcel-service.json`.

Parcel PACKAGE LINE coverage: create.service 93.53% (159/170), create.security 100% (4/4), lifecycle.service 92.31% (132/143), lifecycle.security 90% (9/10). There is currently no parcel messaging package. The normal 70% gate passes. Running `jacoco:check@check -Djacoco.line.minimum=1.00` against the same coverage intentionally exits 1 with named PACKAGE violations for create.service, lifecycle.service and lifecycle.security. This proves selectors actually match; it changes no source threshold or test configuration. Local ignored evidence: `verification.log` and `coverage-gate-proof.log`.

Request to owner / follow-up outside this task's permitted files: pricing-service/pom.xml also uses slash-separated PACKAGE selectors, which do not match JaCoCo's dotted package names. Replace them with `com.swiftdrop.pricing.core.service` and `com.swiftdrop.pricing.core.security`; keep the existing threshold. Measured pricing coverage is 100% in both packages. Pricing and the parent POM were left unchanged. The pre-existing parent Java 17 compiler-release request above also remains open. The optional FR-PAR-015 dashboard is deferred; no required cancellation integration remains.

All seven changed files belong to Member #4 within parcel-service. Member #5 lifecycle files and Member #6 order files are unchanged. No commit, push or pull request was created.
