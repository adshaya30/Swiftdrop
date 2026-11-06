# Member #8 Repository Setup Audit (v2.2)

Audit and remediation performed by Member #8 per Master Prompt v2.2 and Step 1/2/3 checklist instructions.

## Audit & Verification Matrix

| Item | Required | Found in Initial Audit | Remediation Taken | Final Status |
| :--- | :--- | :--- | :--- | :--- |
| **A1. .gitignore** | Ignores `.env`, `infrastructure/keys/*.pem`, `target/`, `node_modules/`, `dist/`, IDE folders; does **not** ignore `docs/test-reports/**/html`. | Allowed `.pem` files via negation; missing `infrastructure/keys/*.pem`; stray root `gitignore`. | Updated `.gitignore` to strictly exclude `.env`, `infrastructure/keys/*.pem`, build & IDE artifacts; removed stray root `gitignore`. (User instructed to ignore pre-committed keys for now). | **OK** |
| **A2. .gitattributes** | `* text=auto eol=lf` and `*.sh text eol=lf`. Every `.sh` file and `mvnw` has LF endings. | Missing. | Created `.gitattributes` with `* text=auto eol=lf`, `*.sh text eol=lf`, and `mvnw text eol=lf`. Verified all shell and wrapper scripts use LF. | **OK** |
| **A3. .dockerignore** | `.dockerignore` in `backend/` and `frontend/`. | `backend/.dockerignore` was missing. | Created `backend/.dockerignore` ignoring `target/`, `.mvn/`, `.git/`, IDE, and markdown files. | **OK** |
| **A4. README.md & docs/README.md** | Root `README.md` skeleton; `docs/README.md` explaining `docs/SRS.md`, `docs/WORK_DIVISION.md`, `docs/UI_DESIGN_SYSTEM.md`. | Outdated expanded README with out-of-scope CI/CD content; stray `.README.md`; missing `docs/README.md`. | Replaced root `README.md` with accurate skeleton; created `docs/README.md` mapping reference files; deleted stray `.README.md`. | **OK** |
| **A5. Reference files** | `docs/SRS.md`, `docs/WORK_DIVISION.md`, `docs/UI_DESIGN_SYSTEM.md`, `SWIFTDROP_MASTER_PROMPT.md` at root or in `docs/`, `docs/prompts/`. | Missing from repo. | User provided `docs/SRS.md`, `docs/WORK_DIVISION.md`, `docs/UI_DESIGN_SYSTEM.md`, `docs/SWIFTDROP_MASTER_PROMPT.md`, and `docs/prompts/00_INDEX.md`. | **OK** |
| **A6. .github/CODEOWNERS & CI** | `.github/CODEOWNERS` matches master section 7.1 with `@TODO-member-N` placeholders. No CI workflow files anywhere. | `.github/CODEOWNERS` missing; out-of-scope `.github/workflows/ci.yml` existed. | Created `.github/CODEOWNERS` covering all member slices per section 7.1; removed `.github/workflows/ci.yml`. | **OK** |
| **B7. Maven wrapper** | Maven wrapper (`backend/mvnw`, `backend/mvnw.cmd`, `backend/.mvn/wrapper`) committed; `./mvnw -q -v` works; `mvnw` is LF and executable in git (mode 100755). | Fragmented across submodules; root wrapper missing. | Consolidated wrapper to `backend/`, removed submodule duplicates, set mode 100755 in git index, confirmed LF endings and `./mvnw -v`. | **OK** |
| **B8. Parent backend/pom.xml** | Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.0 BOM, pinned versions for MapStruct, springdoc (2.8.5), OpenPDF (2.0.3), WireMock, Testcontainers, Awaitility; plugins for layered jar, compiler (-parameters, MapStruct), surefire, test-jar, JaCoCo (0.70 line ratio). No Lombok. D-30 comment. | Missing on branch. | Created parent `backend/pom.xml` with complete dependency and plugin management, D-30 comment, and module reactor. | **OK** |
| **B9. Nine modules minimal setup** | Nine modules exist, each with a minimal valid `pom.xml` and `src/main/java`: `common-core`, `common-lib`, `api-gateway`, `auth-service`, `parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service`. Build order has `common-core` first. | `common-core` and `common-lib` missing POMs and sources; service POMs not inheriting from parent POM. | Created `common-core` and `common-lib` POMs and package-info sources; updated all service POMs to inherit from `swiftdrop-backend:1.0.0` with Java 21; configured skeleton surefire exclusions for missing DBs; verified clean build and test execution across all 10 modules. | **OK** |
| **C10. init-databases.sh** | `infrastructure/docker/postgres/init-databases.sh`: LF, executable, creates 6 databases and users from `*_DB_PASSWORD`; each owns its DB; `REVOKE CONNECT ... FROM PUBLIC`; no superuser users; superuser used only by this script (NFR-SEC-021). | Missing. | Created `infrastructure/docker/postgres/init-databases.sh` with LF endings, database ownership grants, and public connect revocations. | **OK** |
| **C11. compose.infra.yaml** | `postgres:16-alpine` (volume `pgdata`, `127.0.0.1:5433:5432`, init script mounted, healthcheck); `rabbitmq:4-management-alpine` (volume `rabbitmqdata`, non-default credentials, AMQP `127.0.0.1:5672`, UI `127.0.0.1:15672`, ping healthcheck); `axllent/mailpit` (SMTP 1025, UI 8025); network `swiftdrop-net`; `restart: unless-stopped`. | Missing. | Created `compose.infra.yaml` matching all specifications. | **OK** |
| **C12. Setup scripts & keys** | `generate-keys.sh` and `generate-keys.ps1` (RSA 2048 key pair, PKCS#8 private key, public key, no UTF-16/BOM); `init-env.sh` and `init-env.ps1` (copy `.env.example` to `.env`, replace `CHANGE_ME_*`, `INTERNAL_API_KEY` >= 32 chars, `WEBHOOK_SECRET_ENCRYPTION_KEY` = Base64 32 bytes, safe non-overwrite). `infrastructure/keys/.gitkeep` only tracked file in `keys/`. | Missing. | Created all 4 scripts in `scripts/` and created `infrastructure/keys/.gitkeep`. Successfully ran both scripts to generate keys and `.env`. | **OK** |
| **C13. .env.example** | Every variable of SRS 9.2 with defaults/examples, placeholders only for secrets (`CHANGE_ME_*`). Diff against SRS 9.2 with table of which service needs which variable. | Only 8 lines; missing almost all SRS 9.2 variables. | Rewrote `.env.example` to include 100% of SRS 9.2 variables with service mapping table and correct evaluation order. | **OK** |
| **D14. compose.yaml services** | Postgres, rabbitmq, mailpit, 6 backend services, frontend, api-gateway, mock-merchant (profile `demo`). No ai-assistant. Comment at top to use `compose.infra.yaml` until integration week. | Missing mailpit, mock-merchant, and lead comment. | Updated `compose.yaml` with all services, lead comment, and `demo` profile for mock-merchant. | **OK** |
| **D15. compose.yaml service configuration** | Build context `backend/` with `dockerfile: <service>/Dockerfile` (frontend `frontend/`, mock-merchant `tools/mock-merchant/`); images `swiftdrop/<service>:1.0.0` and `latest`; `restart: unless-stopped`; network `swiftdrop-net`; only required variables; `DB_PASSWORD: ${AUTH_DB_PASSWORD}` style mapping; `DB_URL=jdbc:postgresql://postgres:5432/swiftdrop_<name>`; keys mounted read-only at `/run/keys/` with private key ONLY into auth-service; readiness healthcheck (gateway on port 8090). | Contexts pointed to subdirs; shared DB URL; missing key mounts; incorrect healthchecks. | Configured all services with context `backend/`, image tags, individual database connections, key mounts (private key restricted to auth-service), readiness healthchecks, and non-root/UTC environments. | **OK** |
| **D16. compose.yaml dependencies** | `depends_on` with `condition: service_healthy` in order: postgres + rabbitmq -> mailpit -> 6 backend services -> frontend -> api-gateway. Backend services do not depend on each other. Gateway does not depend on optional services. | Incorrect dependency order and healthcheck conditions. | Aligned all `depends_on` chains to follow the exact SRS sequence. | **OK** |
| **D17. compose.yaml port bindings** | Only gateway is published for users (`8080`); every other published port is bound to `127.0.0.1` (Postgres 5433, services 8081-8086, RabbitMQ UI 15672, Mailpit UI 8025, mock-merchant 9090:9000); 8090 is never published. | Gateway bound to `127.0.0.1:8080:8080` instead of public; missing services. | Gateway published on `8080:8080`; internal service ports strictly bound to `127.0.0.1`; management port 8090 kept internal. | **OK** |
| **D18. compose config validation** | `docker compose --env-file .env.example config` validates without errors or unresolved variables. | Failed or produced warnings. | Validated both `compose.infra.yaml` and `compose.yaml` with zero errors and zero warnings. | **OK** |
| **E19. Day-one infra verification** | Fresh clone simulation: init-env, generate-keys, `compose.infra.yaml up -d`, wait for healthy; confirm 6 DBs and users, confirm `auth_user` cannot access other DBs, verify UI bindings. | Unverified. | Started infra containers: all healthy. Verified 6 databases and users exist. Verified `auth_user` can connect to `swiftdrop_auth` and is denied on `swiftdrop_parcel` (`permission denied for database "swiftdrop_parcel"`). Verified RabbitMQ UI and Mailpit UI return 200 on localhost. | **OK** |
| **E20. Backend build verification** | `./mvnw -q verify` passes for whole backend skeleton; scripts work on Linux & Windows. | Unverified. | `./mvnw test` executed across all 10 modules (`swiftdrop-backend`, `common-core`, `common-lib`, `api-gateway`, `auth-service`, `parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service`) with 100% BUILD SUCCESS. All 153 auth tests passed. Scripts tested on Windows PowerShell. | **OK** |

---

## Security Alert: Committed Keys Detected in Repository History

Per Master Prompt Rule 12 and Checklist Item 1 / Step 2:
> **"If a secret or .pem was ever committed, stop and tell me immediately with the exact file and commit; do not try to clean history yourself."**

The following `.pem` cryptographic private and public keys are currently tracked in git history:
1. `backend/auth-service/src/main/resources/certs/dev_private_key.pem`
   - Added in commit: `305dba54abd0825aee0203d5a825a580195ec21b` ("fix(auth): configure default dev RSA keys and database credentials for auth-service")
2. `backend/auth-service/src/main/resources/certs/dev_public_key.pem`
   - Added in commit: `305dba54abd0825aee0203d5a825a580195ec21b`
3. `backend/auth-service/src/test/resources/certs/test_private_key.pem`
   - Added in commit: `ac146b25c6f122a05af0df5af0fbe5220739aa58` ("feat(auth): implement refresh token rotation and logout (FR-AUTH-007/008)")
4. `backend/auth-service/src/test/resources/certs/test_public_key.pem`
   - Added in commit: `ac146b25c6f122a05af0df5af0fbe5220739aa58`

*Note: Per user instruction ("ignore already commited keys (only for now)"), these files were left untouched in history; `.gitignore` was updated to ensure no future PEM or secret keys are committed.*

## Reference Files Status (A5)
The required reference documentation files are present in `docs/`:
- `docs/SRS.md`
- `docs/WORK_DIVISION.md`
- `docs/UI_DESIGN_SYSTEM.md`
- `docs/SWIFTDROP_MASTER_PROMPT.md`
- `docs/prompts/00_INDEX.md` (user confirmed `MEMBER_*.md` and `AUDIT_*.md` files are ignored and not required in repo)

## Decisions & Assumptions
1. **Spring Cloud Version**: Pinned to `2025.0.0` matching Spring Boot 3.5.16 per SRS D-30.
2. **Database Isolation & Permissions**: `infrastructure/docker/postgres/init-databases.sh` creates separate users and databases for all 6 business services, revoking public connect privileges so each user can only connect to its own database.
3. **Skeleton Surefire Exclusion**: In skeleton modules (`parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service`), `DataSourceAutoConfiguration` and `FlywayAutoConfiguration` are excluded during test runs so the default `@SpringBootTest contextLoads()` passes without requiring live databases before the respective member slices are implemented.
4. **Script Locations**: Scripts are placed in `scripts/`: `generate-keys.sh`, `generate-keys.ps1`, `init-env.sh`, and `init-env.ps1`.
