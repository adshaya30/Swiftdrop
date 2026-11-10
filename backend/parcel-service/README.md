# Parcel service foundation — Member #4 Prompt 1, PR A

Port: **8082**. This change supplies the shared parcel domain, schema, and service configuration. Parcel creation, idempotency behavior, lifecycle rules, merchant order APIs, and receipts are deferred to their owning prompts/slices.

`domain.Parcel` is shared with Members #5 and #6. It uses common-core status/size enums, a generated UUID v4, `@Version`, embedded `PickupDetails`/`DropoffDetails`, and embedded origin/destination `ZoneSnapshot` values. Contacts, district/zone snapshots, ownership, tracking number, parcel details, and fee components are not updatable through the mapping. Lifecycle setters are persistence accessors only; transition policies belong to Member #5.

`ParcelRepository` provides Spring Data CRUD/specifications and tracking-number lookup. Other slices should consume this domain rather than duplicate it. The constructor takes the complete creation snapshot; API DTOs must remain separate from entities.

Flyway V1 creates every SRS 6.3 parcel-service table: `parcels`, `idempotency_keys`, and shared `outbox_events`. The merchant-reference unique index excludes CANCELLED and FAILED, but includes DELIVERED. Owner UUIDs have no cross-service foreign keys. `idempotency_keys` is storage only in PR A. Outbox behavior is reused from common-lib. Do not edit V1 after it has been applied.

Environment variables follow SRS 9.2 in `application.yml`. Pricing/auth/public base URLs are bound for later slices; no REST clients are implemented here. This service does not configure or enforce the maximum parcel weight.

## Verification

Use Java 21. From `backend/`:

```powershell
.\mvnw.cmd -pl pricing-service,parcel-service -am verify
```

PostgreSQL migration, repository, startup, health, and optimistic-lock tests require Docker. The dependency also runs RabbitMQ Testcontainers tests. Missing Docker causes a visible failure, not a skipped test. Security MVC and offline Hibernate mapping tests run without Docker; they do not replace database validation.

Build the image with `backend/` as context and `parcel-service/Dockerfile`. Its specific ignore file preserves the Maven wrapper. The builder runs `verify` without skipping tests and therefore needs a reachable Docker daemon for Testcontainers; `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` are accepted build arguments. Runtime is non-root with UTC and a database-only readiness check.

## Request to owner: Member #8

Please change the existing Java/compiler version values and compiler-plugin release from 17 to 21 in `backend/pom.xml` (SRS D-30, Master section 3). The parent is unchanged by PR A; running Maven on Java 21 currently still compiles with release 17.
