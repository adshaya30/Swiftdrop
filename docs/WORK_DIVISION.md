# SwiftDrop — Team Work Division for 10 Members

Parcel Delivery and Courier Management System (EAD group assignment). Based on SRS v2.1 (24 October 2026).

## 1. How to read this document

- The work is split by feature slice. Each member owns one slice from start to finish: backend, frontend, tests, build and diagrams. This way every member can explain their own part in the demo.
- IDs such as AUTH-001, PAR-005, TST-021 and Flow 4.2 are requirement, test and flow numbers from SRS v2.1. Open the SRS and search the ID to see the exact rule.
- **Build** means the Docker side of each service: Dockerfile, `application.yml` and the database migration.
- **Load** is approximate (Heavy, Medium or Light), counted from requirement numbers and pages. It is not exact.

## 2. Overview of all 10 members

Write the member name in the second column.

| # | Member name | Feature slice | Service folders | Frontend pages | Load |
|---|---|---|---|---|---|
| 1 | | Login and sessions | auth-service | SPA foundation, login, register | Medium |
| 2 | | Google login, profile and admin users | auth-service | Google button, profile, admin users | Medium |
| 3 | | Merchant onboarding and API access | auth-service, common-lib | Merchant register, API credentials, admin merchants | Heavy |
| 4 | | Quote and create delivery | pricing-service, parcel-service | New-delivery wizard, customer list and detail | Heavy |
| 5 | | Parcel lifecycle and admin | parcel-service, pricing-service, tracking-service | Admin parcels, reports, zones/pricing, audit log | Light |
| 6 | | Merchant orders and receipts | parcel-service, common-lib | Merchant dashboard, receipt download | Heavy |
| 7 | | Dispatcher assignment and gateway | delivery-service, api-gateway | Assign queue, courier workloads | Medium |
| 8 | | Courier operations and infrastructure | delivery-service, infrastructure, compose files | Courier mobile screens | Heavy |
| 9 | | Tracking and notifications | tracking-service, notification-service | Public tracking page (live, SSE) | Heavy |
| 10 | | Webhooks | notification-service, tools/mock-merchant | Webhook settings, delivery log | Light |

## 3. Who owns which folder

Some services are shared by two or three members. To avoid merge conflicts, each member works only in their own sub-package, and one member (the migration lead) writes the V1 database migration.

| Service / folder | Members | Migration lead | Sub-packages |
|---|---|---|---|
| auth-service | #1, #2, #3 | #1 | login (#1), google, profile, users (#2), merchant (#3) |
| parcel-service | #4, #5, #6 | #4 | create (#4), lifecycle (#5), order (#6) |
| pricing-service | #4, #5 | #4 | core (#4), admin (#5) |
| delivery-service | #7, #8 | #7 | assignment (#7), courier (#8) |
| tracking-service | #9, #5 | #9 | core (#9), audit (#5) |
| notification-service | #9, #10 | #9 | notification (#9), webhook (#10) |
| common-lib | #3, #6 | #3 | security (#3), events and outbox (#6) |
| api-gateway | #7 | n/a | No database, does not use common-lib |
| frontend shared (api client, layouts, components) | #1 | n/a | Other members add pages and `src/api/<area>.js` files only |
| infrastructure, compose files, root files | #8 | n/a | Others ask #8 to add their service block |

## 4. Member cards (full workload)

### #1 Login and sessions (Load: Medium)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/auth-service` (login package); frontend shared foundation: `src/api`, `context`, `layouts`, `components` |
| Backend | AUTH-001 to 008, 026: register, login, JWT (RS256), refresh token rotation, logout, account lockout, password policy, purge job |
| Frontend | SPA foundation: access token in memory, API client, route guards, theme, layouts; login and register pages |
| Tests | TST-013 (JWT and refresh helpers), TST-020 (auth flows), TST-033 (401/403 on all endpoints); frontend tests for forms and route guards (TST-040) |
| Build (Docker) | auth-service Dockerfile, `application.yml`; V1 migration lead for the auth database |
| Diagrams and docs | Design patterns note |
| Notes | Publishes the SPA foundation in week 1 so other frontend members can start |

### #2 Google login, profile and admin users (Load: Medium)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/auth-service` (google, profile, users packages); frontend `pages/auth`, `pages/admin/users` |
| Backend | AUTH-009 to 015, 020, 021, 025: Google login (OIDC + PKCE), `/auth/config`, `/me`, change password, forced password change, bootstrap admin, admin user management, reset password, demo seed (S) |
| Frontend | Google login button, profile page, change password page, admin users page |
| Tests | TST-020 (Google flow with a stubbed Google server: valid, bad state, bad nonce, unverified email, existing email) |
| Build (Docker) | Google and bootstrap-admin environment variables in `application.yml` and `.env.example` (ask #8 to add to compose) |
| Diagrams and docs | Google login sequence diagram (Flow 4.5); auth ER diagram |
| Notes | Bonus: AI assistant (only after the core work is done) |

### #3 Merchant onboarding and API access (Load: Heavy)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/auth-service` (merchant package); `backend/common-lib` (security part); frontend `pages/merchant` (register, credentials), `pages/admin/merchants` |
| Backend | AUTH-016 to 019, 022 to 024: merchant registration, approve/reject/suspend, discount, API credentials, `POST /oauth/token` (client credentials), internal endpoints, audit events. common-lib security: JWT resource server, `CurrentUser`, error handlers, request id, internal key filter |
| Frontend | Merchant registration page; API credentials page (secret shown once); admin merchants page (approve, reject, suspend) |
| Tests | TST-032 (merchant isolation, token scopes, suspended merchants); merchant lifecycle tests; common-lib coverage at least 70% |
| Build (Docker) | common-lib module (publish in week 1) |
| Diagrams and docs | Merchant use case diagram |
| Notes | Week 1 blocker: common-lib security must be published early |

### #4 Quote and create delivery (Load: Heavy)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/pricing-service` (core); `backend/parcel-service` (create package); frontend `pages/customer` |
| Backend | PRC-001 to 005, 009: fee calculator, public quote, districts list, internal calculate, validation, Flyway V2 seed data. PAR-004 to 006, 009, 015: tracking number, create parcel, idempotency, customer list, customer dashboard summary (S) |
| Frontend | New-delivery wizard (details, quote, confirm; 400 ms debounce; FEE_CHANGED handling); customer parcel list and detail pages |
| Tests | TST-010 (pricing), TST-012 (validators, tracking number, idempotency hash), TST-021 (parcel creation), TST-031 (customer isolation) |
| Build (Docker) | Dockerfiles for pricing-service and parcel-service; V1 migration lead for pricing and parcel; pricing V2 seed |
| Diagrams and docs | Create delivery sequence diagram (Flow 4.2); customer use case diagram; pricing ER and parcel ER diagrams |

### #5 Parcel lifecycle and admin (Load: Light)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/parcel-service` (lifecycle package); `backend/pricing-service` (admin package); `backend/tracking-service` (audit package); frontend `pages/admin` |
| Backend | PAR-001 to 003, 011 to 013, 017, 018: state machine (`ParcelTransitionService`), optimistic locking, admin search, force-fail, reports, access policy, events. PRC-006 to 008: zones, districts, pricing matrix, audit events. TRK-005: audit log API |
| Frontend | Admin parcels page, reports page; zones and pricing admin pages; audit log page |
| Tests | TST-011 (state machine), TST-028 (reports, S), TST-029 (force-fail, zones, audit entries) |
| Build (Docker) | Coordinate with #4 (parcel, pricing migrations) and #9 (tracking migrations) |
| Diagrams and docs | Parcel state diagram, dispatcher/admin use case diagram; design PDF assembly, test summary (`docs/test-reports/SUMMARY.md`), contribution sheet |
| Notes | Has the lightest code load, so owns the documents |

### #6 Merchant orders and receipts (Load: Heavy)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/parcel-service` (order package); `backend/common-lib` (events part); frontend `pages/merchant` |
| Backend | PAR-007, 008, 010, 014, 016: merchant order API (idempotent by reference), list and cancel orders, receipt PDF, dashboard summary, internal endpoints. common-lib events: `EventEnvelope`, `EventTypes`, `EventTopology`, `OutboxEvent`, `OutboxService`, `OutboxPublisher`, consumer helpers |
| Frontend | Merchant dashboard (orders list, cancel); receipt download (through the API client with token, as a blob) |
| Tests | TST-025 (RabbitMQ stopped), TST-026 (DLQ, S), merchant order tests, outbox tests |
| Build (Docker) | common-lib events (publish in week 1) |
| Diagrams and docs | Merchant order sequence diagram (Flow 4.3); integration and event design (SRS 3.4 to 3.6) |
| Notes | Week 1 blocker: events and outbox must be published early |

### #7 Dispatcher assignment and gateway (Load: Medium)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/delivery-service` (assignment package); `backend/api-gateway`; frontend `pages/admin` (dispatch) |
| Backend | DEL-001, 002, 009 to 012: deliveries table, assign and reassign, admin deliveries list, courier deactivation, active-count endpoint, events. GW-001 to 008: routing, rate limits, security headers, block `/internal` and `/actuator` on port 8080 |
| Frontend | Unassigned queue with assign dialog; courier workloads page |
| Tests | TST-029 (assignment, deactivation); TST-035 (`/internal` and `/actuator` return 404 on 8080; management port 8090 works) |
| Build (Docker) | Dockerfiles for delivery-service and api-gateway; V1 migration lead for delivery; gateway has no database and does not use common-lib |
| Diagrams and docs | Assignment and deactivation sequence diagram (Flow 4.6); delivery ER diagram |

### #8 Courier operations and infrastructure (Load: Heavy)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/delivery-service` (courier package); `infrastructure/`, `compose.yaml`, `compose.infra.yaml`, root files; frontend `pages/courier` |
| Backend | DEL-003 to 008: courier delivery list, status update (multipart), photo validation, proof of delivery, courier isolation (404) |
| Frontend | Courier mobile screens: list, detail, status form, camera input, `tel:` links (44 px touch targets) |
| Tests | TST-022 (proof and transition together, concurrent update gives 409); TST-030 (courier cannot access another courier's parcels) |
| Build (Docker) | Day 1: repo skeleton, parent `pom.xml`, `compose.yaml`, `compose.infra.yaml`, `init-databases.sh`, JWT keys, `.env.example`, `.gitattributes`, README. Later: CI workflow, monitoring (compose profile), Kubernetes (bonus) |
| Diagrams and docs | Courier update sequence diagram (Flow 4.4); courier use case diagram; deployment diagram |
| Notes | Week 1 blocker: repo skeleton and compose on Day 1. CI, monitoring and Kubernetes only after the core is done |

### #9 Tracking and notifications (Load: Heavy)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/tracking-service` (core); `backend/notification-service` (notification package); frontend `pages/public` |
| Backend | TRK-001 to 004, 006, 007: consume events, public tracking view, SSE stream, timeline, append-only events. NTF-001 to 008: recipients per Table 2.5, email and mock SMS, templates, idempotency, masking |
| Frontend | Public tracking page `/track` and `/track/:trackingNumber` (EventSource with polling fallback, usable at 360 px) |
| Tests | TST-015 (recipients, masking), TST-023 (event flow with Testcontainers), TST-027 (SSE), TST-034 (no sensitive data in public view and logs) |
| Build (Docker) | Dockerfiles for tracking-service and notification-service; V1 migration lead for tracking and notification; SMTP to Mailpit |
| Diagrams and docs | Event topology diagram; tracking ER and notification ER diagrams |

### #10 Webhooks (Load: Light)

**Member name:**

| Area | Details |
|---|---|
| Service folders | `backend/notification-service` (webhook package); `tools/mock-merchant`; frontend `pages/merchant` (webhook) |
| Backend | WHK-001 to 011: webhook settings, HMAC signing, up to 6 attempts with retry, SSRF validator, AES-GCM secret encryption, delivery log, purge job |
| Frontend | Webhook settings page (URL, rotate secret, test); webhook delivery log (filters, manual retry) |
| Tests | TST-014 (HMAC, SSRF, retry delays); TST-024 (500, 500, 200 gives 3 attempts; six failures end FAILED); AES-GCM round trip |
| Build (Docker) | mock-merchant Dockerfile (port 9000, host 9090); `WEBHOOK_*` environment variables |
| Diagrams and docs | Webhook flow sequence diagram (part of Flow 4.3); security note |
| Notes | Helps #8 with monitoring dashboards when the core is done |

## 5. Build tasks every service owner does

| Item | What it means | Who |
|---|---|---|
| Dockerfile and `.dockerignore` | Multi-stage build, non-root user, health check, one exposed port | Service owner |
| `application.yml` | All settings come from environment variables, no secrets in code | Service owner |
| `db/migration/V1__create_tables.sql` | Flyway migration with the tables of the service (SRS 6.3), including `outbox_events` and `processed_events` | Migration lead of that service |
| Actuator health and Prometheus dependency | Health endpoint for Docker; metrics endpoint for monitoring (later) | Service owner |
| Service block in `compose.yaml` | Owner sends the snippet, #8 adds it | #8 |

## 6. Rules to avoid conflicts

- One folder, one owner. Do not edit another member's package. Ask the owner or open a pull request they review.
- Never edit an old migration file. Add a new one (V3, V4 and so on). Flyway checks file checksums.
- `compose.yaml`, parent `pom.xml` and `.env.example` are edited by #8 only. Send #8 your changes.
- common-lib changes need agreement from #3 and #6 (a short change request).
- Build against the SRS contracts (endpoints, events, error codes), not against another member's code. Use WireMock stubs for other services in tests.
- Frontend: each member adds only their `pages/<area>` folder and `src/api/<area>.js` file. Shared files are edited by #1 only.
- Branches: `feature/<service>-<what>`. Pull request with one reviewer. Merge only when the build and tests pass.
- Use GitHub CODEOWNERS so the right owner is asked to review each folder.

## 7. Week plan

| Week | Goal | Key tasks |
|---|---|---|
| 1 | Foundation | #8: repo skeleton, parent pom, `compose.infra.yaml`, `init-databases.sh`, JWT keys, `.env.example` (Day 1). #3: common-lib security. #6: common-lib events and outbox. #1: SPA foundation (API client, route guards, theme). Everyone: empty service with a hello endpoint, Dockerfile, V1 migration |
| 2 | Build each slice | Each member builds their backend, frontend (with mock data if needed) and tests. Use WireMock stubs for other services. |
| 3 | Integrate | Run `docker compose up --build`. Connect real services. Run the acceptance scenarios (SRS Appendix A). Fix bugs. |
| 4 | Finish | Diagrams, README, test reports and coverage, design PDF, contribution sheet, demo rehearsal. Then optional extras: CI, monitoring, Kubernetes, AI assistant. |

## 8. Notes

- Heavy members (#3, #4, #6, #8, #9) should start early. Light members (#5, #10) can help them with tests and diagrams, through a pull request that the owner approves.
- If time runs short, drop in this order: Kubernetes, AI assistant, (S) items, CI and monitoring. Never drop the core flow.
- The SRS marks CI/CD and Prometheus/Grafana as out of scope. If the team adds them, raise a small change request first.
- The group leader should copy the folder owners from section 3 into SRS Appendix E.
