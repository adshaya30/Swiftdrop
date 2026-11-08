# Pricing service foundation — Member #4 Prompt 1, PR A

Port: **8083**. This change supplies persistence and service configuration, not the PR B pricing API or calculator.

`domain` is shared with Member #5's admin slice. It contains `Zone`, `District`, `ZonePricing` (ordered `ZonePricingId`), `SizeSurcharge`, and their Spring Data repositories. Zone codes cannot be updated through the entity mapping. Monetary values use `BigDecimal`; sizes use common-core `ParcelSize`.

Flyway V1 creates the complete SRS 6.3 pricing schema and the shared outbox table. V2 seeds Appendix B: six zones, 25 districts, 36 directed pricing pairs, and three surcharges. These migrations must not be edited after application. Outbox behavior, JWT verification, internal-key checks, logging, request IDs, and database startup retry come from common-lib.

Configuration is in `src/main/resources/application.yml`. Environment variables follow SRS 9.2; mandatory credentials have no committed defaults. `MAX_PARCEL_WEIGHT_KG` belongs only to pricing-service. The uppercase JWT path and outbox interval defaults are overridden directly by the environment, matching the shared library's property names without circular placeholders.

## Verification

Use Java 21. From `backend/`:

```powershell
.\mvnw.cmd -pl pricing-service,parcel-service -am verify
```

Docker is mandatory for the PostgreSQL 16 foundation tests and the dependency's RabbitMQ tests. Tests do not silently skip when Docker is missing. Security MVC tests and offline Hibernate mapping validation do not require Docker; offline validation does not replace PostgreSQL schema validation.

## Container build

Build context is `backend/`, Dockerfile is `pricing-service/Dockerfile`. The Dockerfile-specific ignore file includes the Maven wrapper excluded by the shared backend ignore file. The build runs `verify` without test-skipping flags, so its builder must reach a Docker daemon for Testcontainers. `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` are accepted as build arguments for that setup. Runtime is non-root, UTC, and uses database-only readiness.

## Request to owner: Member #8

`backend/pom.xml` currently sets Java/compiler source, target, release, and the compiler plugin release to 17. SRS D-30 and Master section 3 require 21. Please update those existing values to 21. This service change leaves the parent unchanged; using a Java 21 executable alone still emits Java 17 bytecode until the parent is corrected.
