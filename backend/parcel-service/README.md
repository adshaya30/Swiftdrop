# Parcel service foundation — Member #4 Prompt 1, PR A

Port: **8082**. This change supplies the shared parcel domain, schema, and service configuration. Parcel creation, idempotency behavior, lifecycle rules, merchant order APIs, and receipts are deferred to their owning prompts/slices.

`domain.Parcel` is shared with Members #5 and #6. It uses common-core status/size enums, a generated UUID v4, `@Version`, embedded `PickupDetails`/`DropoffDetails`, and embedded origin/destination `ZoneSnapshot` values. Contacts, district/zone snapshots, ownership, tracking number, parcel details, and fee components are not updatable through the mapping. Lifecycle setters are persistence accessors only; transition policies belong to Member #5.

`ParcelRepository` provides Spring Data CRUD/specifications and tracking-number lookup. Other slices should consume this domain rather than duplicate it. The constructor takes the complete creation snapshot; API DTOs must remain separate from entities.

Flyway V1 creates every SRS 6.3 parcel-service table: `parcels`, `idempotency_keys`, and shared `outbox_events`. The merchant-reference unique index excludes CANCELLED and FAILED, but includes DELIVERED. Owner UUIDs have no cross-service foreign keys. `idempotency_keys` is storage only in PR A. Outbox behavior is reused from common-lib. Do not edit V1 after it has been applied.

Environment variables follow SRS 9.2 in `application.yml`. Pricing/auth/public base URLs are bound for later slices; no REST clients are implemented here. This service does not configure or enforce the maximum parcel weight.

## Verification

Use Java 21 and a running Docker Desktop Linux engine. From the repository root, confirm Maven uses Java 21 (check `JAVA_HOME` if another version is reported):

```powershell
.\backend\mvnw.cmd -version
docker version
.\backend\mvnw.cmd -f backend/pom.xml -B -ntp -pl pricing-service,parcel-service -am verify
```

PostgreSQL migration, repository, startup, health, and optimistic-lock tests require Docker. The dependency also runs RabbitMQ Testcontainers tests. Missing Docker causes a visible failure, not a skipped test. Security MVC and offline Hibernate mapping tests run without Docker; they do not replace database validation.

Build the image with `backend/` as context and `parcel-service/Dockerfile`. Its specific ignore file preserves the Maven wrapper. The builder runs `verify` without skipping tests and therefore needs a reachable Docker daemon for Testcontainers; `DOCKER_HOST` and `TESTCONTAINERS_HOST_OVERRIDE` are accepted build arguments. Runtime is non-root with UTC and a database-only readiness check.

## PR C verification record

On 10 November 2026 at commit `393ff1a7d50b8d1f89a3c25ce0373ca18a1bd05f`, the combined verification completed with **BUILD SUCCESS** using Maven 3.10.0, Temurin 21.0.13 and Docker Engine 29.9.2 (Linux). All 243 tests passed: common-core 19, common-lib 127, parcel-service 10 and pricing-service 87; failures, errors and skipped tests were all zero. All configured JaCoCo gates passed. Parcel overall line coverage was 82.88%, and its create/security package reported 100%. The command did not include `clean`, so coverage uses existing JaCoCo execution data.

Test results are in `backend/parcel-service/target/surefire-reports/`, and coverage is in `backend/parcel-service/target/site/jacoco/index.html`. Generated reports are not committed. See the pricing-service README for the full module table and the earlier common-lib RabbitMQ shutdown error, which passed on the one authorized retry and remains potentially intermittent.

## Docker verification still pending

**Docker image builds, container startup and container smoke tests have NOT been verified.** Passing host Maven tests and Spring test-context startup do not establish that image builds or deployed containers work. Full-stack AT-32 remains pending.

The merged Compose port fix aligns parcel's application port, loopback mapping and readiness check on 8082. Testcontainers inside the isolated builder still needs secure Docker API access and reachable published PostgreSQL/RabbitMQ ports. Host Docker availability and the existing build arguments alone do not establish this. Member #8 will handle the shared build infrastructure. Do not skip tests, expose an unauthenticated daemon or mount a Docker socket to work around the blocker.

Once Member #8 provides the secure setup, follow its documented credentials/arguments and run `docker compose build --no-cache pricing-service parcel-service` from the repository root. Prepare `.env` and JWT keys using the root README, then run `docker compose up -d pricing-service parcel-service` and `docker compose ps`. These steps remain unverified.

## Smoke tests after container startup

After the pending image builds and startup succeed, run from PowerShell:

```powershell
Invoke-RestMethod http://localhost:8082/actuator/health/readiness
Invoke-RestMethod http://localhost:8082/actuator/health/liveness
docker compose logs --tail 100 parcel-service
```

Expect both health responses to report `UP`. Confirm Flyway V1 applies (or is already current), Hibernate schema validation succeeds, and no startup failures appear in the logs. Readiness includes only the database; liveness has no external dependencies. This foundation has no customer parcel-creation endpoint yet; that belongs to Prompt 2. Use the pricing-service README for quote smoke tests. Full-stack gateway and persistence checks remain a shared AT-32 integration task.

## Request to owner: Member #8

Please change the existing Java/compiler version values and compiler-plugin release from 17 to 21 in `backend/pom.xml` (SRS D-30, Master section 3). The parent is unchanged by PR A; running Maven on Java 21 currently still compiles with release 17.
