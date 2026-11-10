# Pricing service — Member #4 Prompt 1, PR A and PR B

Port: **8083**. PR A supplies the persistence and service foundation. PR B adds public quotes, internal discounted calculation, and active district/size reference data.

`domain` is shared with Member #5's admin slice. It contains `Zone`, `District`, `ZonePricing` (ordered `ZonePricingId`), `SizeSurcharge`, and their Spring Data repositories. Zone codes cannot be updated through the entity mapping. Monetary values use `BigDecimal`; sizes use common-core `ParcelSize`.

Flyway V1 creates the complete SRS 6.3 pricing schema and the shared outbox table. V2 seeds Appendix B: six zones, 25 districts, 36 directed pricing pairs, and three surcharges. These migrations must not be edited after application. Outbox behavior, JWT verification, internal-key checks, logging, request IDs, and database startup retry come from common-lib.

Configuration is in `src/main/resources/application.yml`. Environment variables follow SRS 9.2; mandatory credentials have no committed defaults. `MAX_PARCEL_WEIGHT_KG` belongs only to pricing-service. The uppercase JWT path and outbox interval defaults are overridden directly by the environment, matching the shared library's property names without circular placeholders.

## Verification

Use Java 21. From `backend/`:

```powershell
.\mvnw.cmd -pl pricing-service,parcel-service -am verify
```

Docker is mandatory for the PostgreSQL 16 foundation tests and the dependency's RabbitMQ tests. Tests do not silently skip when Docker is missing. Security MVC tests and offline Hibernate mapping validation do not require Docker; offline validation does not replace PostgreSQL schema validation.

For pricing-only PR B verification, run `.\mvnw.cmd -B -pl pricing-service -am verify` from `backend/`. This retains every PR A test, exercises PostgreSQL-backed HTTP contracts and the dependency's RabbitMQ tests, and enforces the existing 70% minimum independently on the pricing core service/security packages. The module configures package selection explicitly; the shared parent is unchanged.

## PR B pricing contracts

| Endpoint | Request / response | Access |
|---|---|---|
| `POST /api/v1/public/quotes` | `originDistrictCode`, `destinationDistrictCode`, `weightKg`, `size` → `currency`, origin/destination zones (`code`, `name`), `baseFee`, `weightFee`, `sizeSurcharge`, `discountAmount`, `total` | Public; no discount input |
| `POST /internal/v1/pricing/calculate` | Same input plus optional `discountPercent` (default zero, range 0–50); response adds origin/destination districts (`code`, `name`), `subtotal`, `discountPercent` | `X-Internal-Key`; JWT alone is insufficient |
| `GET /api/v1/public/districts` | `maxWeightKg`, `sizes[{size,surcharge,description}]`, `districts[{code,name,zoneCode,zoneName}]` for active zones only | Public |

FR-PRC-001/BR-08 use one `BigDecimal` calculator: `max(0, ceil(weightKg) - 1) * perKgFee`, plus the base and size fees, then the discount, with `HALF_UP` to two decimals only on the final total. The discount amount is subtotal minus that rounded total. No floating-point arithmetic is used.

FR-PRC-004 rejects unknown districts, invalid/missing sizes, weights below 0.01, weights with more than two decimal places, and weights above the configured maximum with `400 VALIDATION_ERROR`. Numeric weight validation errors identify `weightKg`. Structurally unreadable JSON uses the shared handler's generic validation response. Inactive zones give `422 ZONE_NOT_SERVICEABLE`; missing directed pricing pairs give `422 PRICING_NOT_CONFIGURED`. Unknown JSON fields, including an attempted public `discountPercent`, are rejected. The public and internal responses are separate records mapped using MapStruct, and persistence entities are never serialized.

Pricing operations are read-only and publish no events. Rate limiting belongs to the gateway; JWT/internal-key filters and Problem Details handling remain in common-lib. Admin pricing, parcel creation, and later prompts are outside this implementation.

TST-010 includes AT-05 (`800.00` / `650.00`), AT-06 (`350.00` / `450.00`), AT-07 (subtotal `1800.00`, discount `180.00`, total `1620.00`), rounding and validation boundaries. TST-035 verifies missing/wrong internal keys and JWT-only rejection. Tests also check exact response field sets, active-zone filtering, the configurable maximum, unknown JSON fields, and generated OpenAPI security documentation.

## Container build

Build context is `backend/`, Dockerfile is `pricing-service/Dockerfile`. The Dockerfile-specific ignore file includes the Maven wrapper excluded by the shared backend ignore file. The build runs `verify` without test-skipping flags, so its builder must reach a Docker daemon for Testcontainers. `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` are accepted as build arguments for that setup. Runtime is non-root, UTC, and uses database-only readiness.

## Request to owner: Member #8

`backend/pom.xml` currently sets Java/compiler source, target, release, and the compiler plugin release to 17. SRS D-30 and Master section 3 require 21. Please update those existing values to 21. This service change leaves the parent unchanged; using a Java 21 executable alone still emits Java 17 bytecode until the parent is corrected.
