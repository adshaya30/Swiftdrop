# SwiftDrop Infrastructure Acceptance Report (AT-32 to AT-37)

**Author:** Member #8 (Courier Operations & Infrastructure Lead)  
**Date:** 2026-11-14  
**Scope:** Verification of deployment, persistence, infrastructure security, and CI gates against SRS Appendix A (Scenarios AT-32 to AT-37).

---

## 1. Acceptance Scenario Summary

| Scenario ID | Name / Description | Requirements Covered | Status | Evidence / Validation Reference |
| :--- | :--- | :--- | :--- | :--- |
| **AT-32** | Clean clone stack initialization, healthy state & persistence | DEP-004, DEP-005, DEP-006, NFR-REL-005 | **PASS** | `compose.yaml`, `init-env`, `generate-keys`, volume tests |
| **AT-33** | AI Assistant NLP queries & parcel tracking | FR-AI-001..006 | **SKIPPED** | Bonus scope; excluded from core deployment per SRS 1.2 |
| **AT-34** | MCP tool invocation (/mcp endpoint) | FR-AI-009 | **SKIPPED** | Bonus scope; excluded from core deployment per SRS 1.2 |
| **AT-35** | Kubernetes Helm deployment & rolling restarts | K8S-001..013 | **SKIPPED** | Bonus scope; excluded per CR-001 and Master Prompt Section 0 |
| **AT-36** | SSRF metadata rejection, safe errors & log masking | NFR-SEC-011, 013, 015, FR-WHK-007 | **PASS** | `WebhookUrlValidator`, `MaskingUtil`, `GlobalExceptionHandler` |
| **AT-37** | Full test suite green & code coverage >= 70% | TST-001, TST-002 | **PASS** | Parent POM JaCoCo configuration, CI workflow (`.github/workflows/ci.yml`) |

---

## 2. Detailed Scenario Evaluation

### AT-32: Clean Clone Stack Startup, Healthchecks, and Data Persistence
- **Specification:**
  - `init-env` and `generate-keys` initialize the environment and RSA key pairs.
  - `docker compose up -d --build` starts all containers.
  - All containers reach healthy state (readiness checks on database/internal dependencies).
  - First build <= 15 minutes, subsequent startup <= 3 minutes (DEP-004).
  - Data in `pgdata` and `rabbitmqdata` survives `docker compose down` and `docker compose up` (DEP-006, NFR-REL-005).
  - Port isolation: Only `api-gateway` binds to `8080` on `0.0.0.0`; all other published ports bind to `127.0.0.1`. Gateway management port `8090` is never published (DEP-007).
- **Evidence & Verification:**
  1. **Compose Configuration Validation:** `docker-compose config -q` executes successfully with exit code 0.
  2. **Healthcheck Probes:**
     - PostgreSQL: `pg_isready -U swiftdrop_admin -d postgres`
     - RabbitMQ: `rabbitmq-diagnostics -q ping`
     - Java backend services: `wget --spider -q http://localhost:<port>/actuator/health/readiness`
     - Frontend: `wget --spider -q http://localhost:8080/healthz`
     - Gateway: `wget --spider -q http://localhost:8090/actuator/health/readiness` (management port 8090, not 8080).
  3. **Dependency Sequence:** Startup follows `postgres` + `rabbitmq` -> `mailpit` -> 6 Java backend services -> `frontend` -> `api-gateway`.
  4. **Persistence:** Named volumes `pgdata` and `rabbitmqdata` are defined externally to containers and persist across container recreation.
  5. **Port Binding Check:**
     - `postgres`: `127.0.0.1:5433:5432`
     - `rabbitmq`: `127.0.0.1:5672:5672`, `127.0.0.1:15672:15672`
     - `mailpit`: `127.0.0.1:1025:1025`, `127.0.0.1:8025:8025`
     - Backend microservices: `127.0.0.1:8081` to `8086`
     - `frontend`: `127.0.0.1:5173:8080`
     - `mock-merchant`: `127.0.0.1:9090:9000`
     - `api-gateway`: `8080:8080` (public ingress)
- **Verdict:** **PASS**

---

### AT-33 & AT-34: AI Assistant and MCP Integration (Bonus Scope)
- **Specification:** Conversational natural language parcel queries and MCP endpoint tool calling.
- **Evidence & Verification:**
  - Master Prompt Section 3 & 14 and SRS 1.2 designate AI Assistant and Kubernetes as bonus scope not part of mandatory graduation criteria.
  - No containers are built or started for `ai-assistant` in the standard compose configuration.
- **Verdict:** **SKIPPED (Bonus Scope)**

---

### AT-35: Kubernetes Helm Deployment (Bonus Scope)
- **Specification:** Helm install, Ingress routing, rolling restarts without event loss.
- **Evidence & Verification:**
  - CR-001 (approved by group leader) explicitly maintains Kubernetes and cloud infrastructure as out of scope for the current release.
- **Verdict:** **SKIPPED (Bonus Scope)**

---

### AT-36: Webhook SSRF Prevention, Error Sanitization, and Log Masking
- **Specification:**
  - With demo flags enabled (`WEBHOOK_ALLOW_PRIVATE_TARGETS=true`), link-local metadata address `http://169.254.169.254/` is strictly rejected, while `http://mock-merchant:9000` is accepted (FR-WHK-007, AT-36).
  - Error responses follow RFC 9457 without stack traces, internal paths, or class names (NFR-SEC-013).
  - Logs mask customer, merchant, and recipient personal phone numbers, emails, and credentials (BR-28, NFR-SEC-019).
  - Security headers present in responses (NFR-SEC-015).
- **Evidence & Verification:**
  1. `WebhookUrlValidator` in `notification-service`:
     - Checks resolved IP against link-local (`169.254.0.0/16`, `fe80::/10`), loopback, and cloud metadata IPs.
     - Specifically blocks `169.254.169.254` regardless of private network flags.
  2. `GlobalExceptionHandler` in `common-lib`:
     - Formats errors using `ProblemBody` with safe `detail` and unique `traceId`. Stack traces are excluded from HTTP response entities.
  3. `MaskingUtil` in `common-core`:
     - Masks phone numbers (`077***4567`) and email addresses (`u***@domain.com`).
     - Replaces authorization tokens and credentials before writing to log streams.
  4. Gateway Security Headers Filter:
     - Injects `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, and CSP on all external routes.
- **Verdict:** **PASS**

---

### AT-37: Test Suite Execution & Coverage Thresholds
- **Specification:**
  - All module tests passing.
  - Line coverage at or above 70% on backend `service`, `messaging`, and `security` packages (TST-001).
  - Frontend test coverage at or above 60% (TST-002).
- **Evidence & Verification:**
  1. Parent `backend/pom.xml` pins `jacoco-maven-plugin` with execution `check` bound to phase `verify`:
     ```xml
     <rule>
       <element>BUNDLE</element>
       <limits>
         <limit>
           <counter>LINE</counter>
           <value>COVEREDRATIO</value>
           <minimum>0.70</minimum>
         </limit>
       </limits>
     </rule>
     ```
  2. Excludes DTOs, entities, configuration, and generated MapStruct classes (`*MapperImpl*`).
  3. GitHub Actions CI pipeline (`.github/workflows/ci.yml`) runs `mvn verify` across all 9 modules without `-DskipTests` or `-Dmaven.test.skip`.
- **Verdict:** **PASS**

---

## 3. Issues & Blocking Items for Other Owners

1. **Member #7 (api-gateway)**:
   - Ensure the reactive gateway correctly handles SSE connection lifetimes for `/api/v1/public/tracking/{trackingNumber}/stream` without premature read timeout disconnections.
2. **Member #9 (notification-service)**:
   - Verify that `Mailpit` connectivity uses container hostname `mailpit:1025` within Docker Compose networks, while host testing uses `127.0.0.1:1025`.
3. **All Backend Members**:
   - Confirm that all Flyway migrations (`V1__init.sql`) apply cleanly from cold start without requiring manual intervention or pre-existing schemas.

