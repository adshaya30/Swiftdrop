# Pricing service — Member #4 Prompt 1, PR A and PR B

Port: **8083**. PR A supplies the persistence and service foundation. PR B adds public quotes, internal discounted calculation, and active district/size reference data.

`domain` is shared with Member #5's admin slice. It contains `Zone`, `District`, `ZonePricing` (ordered `ZonePricingId`), `SizeSurcharge`, and their Spring Data repositories. Zone codes cannot be updated through the entity mapping. Monetary values use `BigDecimal`; sizes use common-core `ParcelSize`.

Flyway V1 creates the complete SRS 6.3 pricing schema and the shared outbox table. V2 seeds Appendix B: six zones, 25 districts, 36 directed pricing pairs, and three surcharges. These migrations must not be edited after application. Outbox behavior, JWT verification, internal-key checks, logging, request IDs, and database startup retry come from common-lib.

Configuration is in `src/main/resources/application.yml`. Environment variables follow SRS 9.2; mandatory credentials have no committed defaults. `MAX_PARCEL_WEIGHT_KG` belongs only to pricing-service. The uppercase JWT path and outbox interval defaults are overridden directly by the environment, matching the shared library's property names without circular placeholders.

## Verification

Use Java 21 and a running Docker Desktop Linux engine. From the repository root, confirm the wrapper uses Java 21 (check `JAVA_HOME` if it reports another version):

```powershell
.\backend\mvnw.cmd -version
docker version
.\backend\mvnw.cmd -f backend/pom.xml -B -ntp -pl pricing-service,parcel-service -am verify
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

## PR C verification record

On 10 November 2026, commit `393ff1a7d50b8d1f89a3c25ce0373ca18a1bd05f`, the full command above completed with **BUILD SUCCESS** using Maven 3.10.0, Temurin 21.0.13 and Docker Engine 29.9.2 (Linux). No tests were excluded or skipped.

| Module | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| common-core | 19 | 0 | 0 | 0 |
| common-lib | 127 | 0 | 0 | 0 |
| parcel-service | 10 | 0 | 0 | 0 |
| pricing-service | 87 | 0 | 0 | 0 |
| Total | 243 | 0 | 0 | 0 |

All configured JaCoCo gates passed. Pricing's core service and security packages each reported 100% line coverage; overall pricing line coverage was 91.02%. The command did not include `clean`, so coverage uses the existing JaCoCo execution data. Reports are in each module's `target/surefire-reports/` and `target/site/jacoco/index.html`; these generated files are not committed.

An earlier attempt failed in common-lib's `EventConsumerFlowTest.handledIsAcked` with `RejectedExecutionException` during RabbitMQ shutdown. The one authorized full retry passed all seven tests in that class. The error appears intermittent; its cause has not been resolved.

## Docker verification still pending

**Docker image builds, container startup and container smoke tests have NOT been verified.** Host Maven success verifies tests and Spring test contexts, not image builds or deployed readiness. AT-32 remains pending.

The merged Compose port fix aligns pricing's application port, loopback mapping and readiness check on 8083. The isolated image builder still needs secure Docker API access and access to Testcontainers' published PostgreSQL/RabbitMQ ports. A running host Docker engine and the two existing build arguments alone do not provide that arrangement. Member #8 will handle the remaining shared build infrastructure; no socket mount or unauthenticated daemon exposure is authorized by this README. Do not bypass tests to build an image.

After Member #8 supplies and documents the secure builder setup, use its required credentials/arguments with `docker compose build --no-cache pricing-service parcel-service`. Prepare `.env` and JWT keys as described in the root README, then run `docker compose up -d pricing-service parcel-service` and `docker compose ps` from the repository root. These are future verification steps, not recorded passing results.

## Smoke tests after container startup

Run these PowerShell commands only after the pending build setup and startup succeed:

```powershell
Invoke-RestMethod http://localhost:8083/actuator/health/readiness
Invoke-RestMethod http://localhost:8083/actuator/health/liveness
$reference = Invoke-RestMethod http://localhost:8083/api/v1/public/districts
$reference
$quote = @{ originDistrictCode = 'COLOMBO'; destinationDistrictCode = 'KANDY'; weightKg = 2.00; size = 'MEDIUM' }
Invoke-RestMethod -Method Post -Uri http://localhost:8083/api/v1/public/quotes -ContentType 'application/json' -Body ($quote | ConvertTo-Json)
```

Expect readiness/liveness status `UP`, 25 seeded districts and three sizes on a fresh database, and AT-05 total 800.00 (SMALL gives 650.00). AT-06: use GAMPAHA/SMALL with weights 1.00 and 1.01 for totals 350.00 and 450.00. For AT-07, obtain the configured internal key securely in `$internalApiKey` (do not print or commit it):

```powershell
$calculation = @{ originDistrictCode = 'COLOMBO'; destinationDistrictCode = 'JAFFNA'; weightKg = 5.00; size = 'LARGE'; discountPercent = 10 }
Invoke-RestMethod -Method Post -Uri http://localhost:8083/internal/v1/pricing/calculate -Headers @{ 'X-Internal-Key' = $internalApiKey } -ContentType 'application/json' -Body ($calculation | ConvertTo-Json)
```

Expect subtotal 1800.00, discountAmount 180.00 and total 1620.00. Missing/wrong internal keys must be rejected; public endpoints require no token. Weights 0.00, 30.01 (with the default maximum) and 2.001 must return 400 `VALIDATION_ERROR`, identifying `weightKg`. Readiness checks only the database; liveness has no external dependencies. Gateway routing and full-stack AT-32 must be verified separately after shared integration is ready.

## Request to owner: Member #8

`backend/pom.xml` currently sets Java/compiler source, target, release, and the compiler plugin release to 17. SRS D-30 and Master section 3 require 21. Please update those existing values to 21. This service change leaves the parent unchanged; using a Java 21 executable alone still emits Java 17 bytecode until the parent is corrected.
