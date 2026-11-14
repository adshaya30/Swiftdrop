# SwiftDrop — Parcel Delivery & Courier Management System

**Software Requirements Specification — Microservices Build Baseline v2.0**

| Item | Detail |
|---|---|
| Version / status | 2.1 microservices baseline (2.0 with the review corrections listed in D.2 applied). It replaces the monolith SRS v1.0 compact. It becomes FINAL and frozen only when the group leader signs it off (Appendix D). |
| Date | 24 October 2026 |
| Project | Enterprise Application Development — Group Assignment (20 marks, 10 members) |
| Scope | Core platform (Must); Kubernetes deployment (bonus); AI Delivery Assistant with MCP tools (bonus) |
| Architecture | 7 Spring Boot services (api-gateway, auth, parcel, pricing, delivery, tracking, notification) + React frontend + PostgreSQL + RabbitMQ |
| Group number / members | (to be filled in by the leader — Appendix D) |

## How to use this document with AI coding tools

- Every member uploads this whole document to their AI tool, then states which service (or the frontend) they are building. The AI shall build only that service, and shall follow the IDs, names, paths, fields and status codes below literally.
- Build against the contracts, not against other members' code: shared library (3.4), events (3.5), internal endpoints (7.4), endpoint catalogue (7.3), error codes (7.2), enums (6.2), data model (6.3).
- The AI shall never invent behaviour, fields, endpoints, error codes, table columns or event types. If something is missing, it is out of scope, or a change request is raised (1.1).
- The AI shall cite requirement IDs in code comments and test names so reviewers can trace code to requirements.
- Every service has exactly one owner subgroup. Nobody edits another service's code or migrations; they ask the owner or raise a change request.

## Contents

1. Introduction
2. Business Rules and Lifecycle
3. Architecture and Integration
4. Use Cases and Key Flows
5. Functional Requirements (by service)
6. Data Requirements
7. Interfaces
8. Non-Functional Requirements
9. Deployment and Environment
10. Bonus: Kubernetes
11. Bonus: AI Assistant
12. Testing and Quality
13. Deliverables
- Appendix A — Acceptance Scenarios and Demo Script
- Appendix B — Reference Data
- Appendix C — Design Decisions
- Appendix D — Sign-off, Open Items and Review Resolution
- Appendix E — Service Ownership

---

## 1. Introduction

### 1.1 Purpose and rules for reading

This SRS is the single source of truth for building SwiftDrop, a parcel delivery and courier management platform for an island-wide courier company in Sri Lanka. It replaces phone and spreadsheet bookings with a web platform where customers and merchants send and track parcels, dispatchers assign deliveries and couriers update progress in near real time. Every decision the assignment brief leaves open is made here and is binding (Appendix C).

- Text in `monospace` is a literal value (path, field, header, status, variable, code) to be used exactly as written.
- If two statements conflict, the Business Rules (2.2) prevail; otherwise the more specific statement prevails.
- IDs: `FR-<MODULE>-nnn` functional, `NFR-<CAT>-nnn` non-functional, `BR-nn` business rule, `DEP-nnn` deployment, `K8S-nnn` Kubernetes, `FR-AI-nnn` assistant, `TST-nnn` test, `AT-nn` acceptance scenario, `D-nn` decision, `UC-xnn` use case. Gaps in numbering are intentional; withdrawn IDs are never reused.
- Priority: every requirement is Must unless marked (S) = Should (build only after all Must items). Sections 10 and 11 are bonus scope.
- Formats: currency LKR with two decimals; timestamps ISO 8601 UTC in APIs and storage; "today" and daily buckets use Asia/Colombo (UTC+05:30); JSON fields camelCase; DB columns snake_case; enum values UPPER_SNAKE_CASE; API paths lower-case kebab-case; all ids are UUID v4 strings unless stated.
- Change control: a change to a requirement, rule, interface, event or data definition needs a written change request (change, reason, affected IDs) approved by the group leader and the owner of each affected service. Code, tests and this document are updated in the same pull request.

### 1.2 Scope

| Area | In scope |
|---|---|
| Customers | Local sign-up and login, Login with Google (OAuth 2.0/OIDC), fee quotes, delivery requests, live tracking, history, PDF receipts, public mobile tracking page. |
| Merchants | Registration with admin approval, OAuth 2.0 client-credentials API to create and cancel orders, signed webhooks with retries, dashboard. |
| Couriers | Login, daily delivery list, status updates with notes and proof of delivery (recipient name, optional photo), failure reasons. |
| Dispatcher/Admin | Unassigned queue, assignment, courier workloads, management of users, merchants, zones and pricing, reports, audit log. |
| Platform | JWT (RS256) auth and RBAC, RabbitMQ events with transactional outbox, notification service (email, mock SMS, webhooks), SSE live tracking, Docker Compose deployment. |
| Bonus | Kubernetes with Helm (Section 10); AI Delivery Assistant with MCP tools (Section 11). |

**Out of scope (shall not be built):** online payments, cash on delivery, invoicing, taxes; live GPS, maps, route optimisation, delivery time estimates; multi-leg routing, re-delivery (a FAILED parcel is final), pickup windows, returns; international shipping, multi-currency, multi-language UI (English only), native apps, push notifications; email verification at sign-up, self-service forgot-password (an admin resets passwords), MFA, merchant sub-accounts, address book; real SMS gateway; merchant order creation in the web UI (API only); customer self-deletion, bulk assignment, CSV export, report charts, merchant discount in public quotes; Playwright and k6 load tests, CI/CD pipelines, dependency scanning, Prometheus/Grafana dashboards, horizontal scaling (every service runs as exactly one instance), service discovery, config server, distributed tracing tools, Kubernetes autoscaling and network policies.

### 1.3 Users

| Class | Description | How created |
|---|---|---|
| Public visitor | Anyone with a tracking number (including recipients). | No account. |
| CUSTOMER | Individual sending parcels (mobile or desktop). | Self-registration or Google login. |
| MERCHANT | Partner online store; owner uses the dashboard, developers use the API. | Self-registration; needs admin approval. |
| COURIER | Delivery staff using a smartphone browser. | Created by an admin; must change password at first login. |
| ADMIN | Dispatcher/Admin office staff. | First admin from environment bootstrap; more created by an admin. |

### 1.4 Technology stack (final)

Pin exact versions in build files on day 1 and record them in the parent POM; no major upgrades during the project. Free support for Spring Boot 3.5 and Spring Cloud 2025.0 ended on 30 June 2026 (3.5.16 was the last free patch). The project deliberately stays on them, for the reason and with the accepted risk recorded in D-30, so no "still supported" check applies to these two.

| Layer | Choice |
|---|---|
| Frontend | React 18, JavaScript (JSX, as in the existing repo: `App.jsx`, `main.jsx`), Vite, React Router, TanStack Query, MUI, React Hook Form with Zod, browser EventSource, Vitest and React Testing Library. Built to static files served by Nginx. |
| Backend services | Java 21, Spring Boot 3.5.16 (the last free 3.5 patch, see D-30; Boot 4.x is deliberately not used), Spring Web, Spring Security (OAuth2 resource server), Spring Data JPA, Bean Validation, Flyway, Spring AMQP, springdoc-openapi, MapStruct, Micrometer, Maven (one parent POM). Service-specific: OpenPDF (parcel-service), Spring Mail (notification-service), `java.net.http.HttpClient` (notification-service webhooks; delivery, parcel, auth, notification internal clients use Spring `RestClient`). |
| api-gateway | Spring Cloud Gateway (reactive) on the Spring Cloud 2025.0.x release train that matches Boot 3.5 (see D-30). Use the Gateway artifact and property names of that release train, which were renamed in 2025.0 (AI tools often generate the old names; the gateway owner confirms them on spring.io before coding). The gateway does not use common-lib (3.4). No database. |
| AI assistant (bonus) | Python 3.12, FastAPI, Uvicorn, anthropic SDK, official mcp SDK, httpx, Pydantic; pytest. |
| Data and messaging | PostgreSQL 16 (one server, six databases, one least-privilege user each); RabbitMQ 4.x with management plugin (image tag `rabbitmq:4-management-alpine`); Mailpit as SMTP sink (SMTP 1025, UI 8025). |
| Containers | Docker, Compose v2, multi-stage builds. Bases: `eclipse-temurin:21-jre-alpine`, `python:3.12-slim`, `nginxinc/nginx-unprivileged:stable-alpine`, `node:22-alpine` (build only). |
| Testing | JUnit 5, Mockito, AssertJ, Spring Security Test, Testcontainers (PostgreSQL, RabbitMQ), WireMock, Awaitility, JaCoCo; Vitest; pytest. |
| API docs and VCS | OpenAPI 3 via springdoc per service (Swagger UI only when `SWAGGER_ENABLED=true`); Git monorepo, feature branches, pull requests with one reviewer. |

### 1.5 Environment and assumptions

- **Server:** Docker Engine 24+ with Compose v2 (mandatory); Kubernetes (minikube, a currently supported release), ingress-nginx, Helm 3 (bonus). Reference machine: 4 cores, 8 GB RAM free (16 GB for Kubernetes). All containers run in UTC.
- **Clients:** latest two major versions of Chrome, Edge, Firefox, Safari; Chrome on Android 10+, Safari on iOS 16+; minimum viewport 360 px.
- **Google:** the team creates an OAuth 2.0 Web client with redirect URI `http://localhost:8080/api/v1/auth/google/callback`. Without credentials the Google button is hidden and everything else works.
- **AI bonus:** `ANTHROPIC_API_KEY` available; a built-in mock provider allows tests and offline demos.
- One country (Sri Lanka phone numbers); couriers have a smartphone with a camera; merchants host an HTTPS webhook endpoint; only Docker is needed on the host (plus Git); Mailpit captures email.
- **External systems:** Google Identity (OAuth 2.0 code + PKCE, OIDC); merchant platforms (REST with client credentials, HMAC-signed webhooks); SMTP server; mock SMS adapter; Anthropic API (bonus).

### 1.6 Priority cut line (what to drop first if time runs out)

Drop in this order, never touching the items above the line:

1. Section 10 Kubernetes
2. Section 11 AI assistant
3. All (S) items
4. The refresh-token grace period (FR-AUTH-007: reuse then always revokes the family)
5. The database immutability trigger (FR-TRK-007 is code-level only)

The core Must set is: auth and roles, quotes and pricing, parcel creation with idempotency, assignment, courier updates with proof, tracking with SSE, notifications, merchant API with webhooks, admin functions, reports, Docker Compose.

---

## 2. Business Rules and Lifecycle

### 2.1 Roles and access matrix

Exactly four roles (BR-01). "Own" = resources owned by the caller; "Assigned" = parcels assigned to the caller; "-" = refused (403, or 401 if unauthenticated). The server enforces; the UI only hides actions.

| Capability | Public | Customer | Merchant | Courier | Admin |
|---|---|---|---|---|---|
| Register, log in (password); Google login (customers only) | Yes | - | - | - | - |
| Track by number (limited data); quote; list districts; AI public tools | Yes | Yes | Yes | Yes | Yes |
| AI: list own parcels | - | Own | - | - | - |
| Create delivery (UI), view own parcels, history, detail, timeline | - | Own | - | - | - |
| Cancel parcel while CREATED | - | Own | Own | - | Any |
| Download receipt | - | Own | Own | - | Any |
| View proof of delivery | - | Own | Own | Assigned | Any |
| Create orders (API only); list and cancel orders | - | - | Own (approved) | - | - |
| API credentials, webhook config, delivery log and retry | - | - | Own (dashboard login only) | - | - |
| View assigned deliveries; update status | - | - | - | Assigned | - |
| Unassigned queue; assign/reassign; fail stuck parcel; all parcels; workloads; dashboard | - | - | - | - | Yes |
| Manage users, merchants, zones, pricing, size surcharges; reports; audit log | - | - | - | - | Yes |
| Own profile and change password | - | Yes | Yes | Yes | Yes |

### 2.2 Business rules (system-wide invariants; tests shall verify them)

| ID | Rule |
|---|---|
| BR-01 | Roles: CUSTOMER, MERCHANT, COURIER, ADMIN. One role per user, set at creation, never changed. |
| BR-02 | Every parcel belongs to exactly one owner: a customer (`customer_id` = user id) or a merchant (`merchant_id`), never both, never neither. |
| BR-03 | Statuses: CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, CANCELLED. Only transitions in Table 2.3 are allowed; DELIVERED, FAILED, CANCELLED are terminal; steps cannot be skipped or reversed. |
| BR-04 | A parcel has at most one courier. Only an ADMIN assigns or reassigns, only to an ACTIVE courier, only while the status is CREATED. Assignment does not change status. An unassigned parcel cannot progress beyond CREATED. Deactivating a courier unassigns that courier's CREATED parcels. A parcel stuck after pickup is closed by an ADMIN via FR-PAR-012. |
| BR-05 | A courier reads and updates only parcels currently assigned to that courier. |
| BR-06 | Terminal parcels, status history rows, delivery proofs and the stored fee are never modified after being written. |
| BR-07 | Cancel only while CREATED, by the owner (customer or merchant) or an ADMIN. An ADMIN must give a reason (10-300 chars); owners may give an optional reason (up to 300). |
| BR-08 | `fee = round((Base + WeightFee + SizeSurcharge) x (1 - DiscountPercent/100))`. Base and PerKg come from the pricing row (origin zone, destination zone); `WeightFee = max(0, ceil(weightKg) - 1) x PerKg` (first kg included in Base); SizeSurcharge from the size; DiscountPercent is the merchant discount (0 for customers). Rounding HALF_UP to 2 decimals. `discountAmount = subtotal - total`. Money is BigDecimal / NUMERIC(12,2), never floating point. |
| BR-09 | Origin zone = zone of the pickup district; destination zone = zone of the drop-off district. District and zone codes and names and all fee components are snapshotted on the parcel at creation. |
| BR-10 | Weight at least 0.01 kg with at most 2 decimals (checked by parcel-service); the maximum, `MAX_PARCEL_WEIGHT_KG` (default 30.00 kg), is checked only by pricing-service (FR-PRC-004) and is never hard-coded elsewhere; size SMALL, MEDIUM or LARGE (Appendix B); both districts in active zones and a pricing row exists for the zone pair. |
| BR-11 | Tracking number: `SD-` + 10 characters from `0123456789ABCDEFGHJKMNPQRSTVWXYZ`, regex `^SD-[0-9A-HJKMNP-TV-Z]{10}$`, generated with a secure random generator, unique, immutable. |
| BR-12 | Merchant lifecycle: PENDING to APPROVED or REJECTED; REJECTED to APPROVED; APPROVED to SUSPENDED; SUSPENDED to APPROVED. Only APPROVED merchants with an ACTIVE user can log in, get API tokens and create or cancel orders. Reject and suspend need a reason (10-300 chars). Existing parcels continue their lifecycle when a merchant is suspended. |
| BR-13 | Merchant discount 0.00-50.00 percent, set only by an ADMIN, default 0. |
| BR-14 | One webhook configuration per merchant (one HTTPS URL, one secret). A webhook is sent for every `parcel.status.changed` of that merchant's parcels while enabled; delivery is at-least-once and signed. |
| BR-15 | Notifications follow Table 2.5. |
| BR-16 | FAILED needs a failure reason allowed for the current stage (Table 2.4); OTHER also needs a note (5-500 chars). An ADMIN override (FR-PAR-012) always needs a note. |
| BR-17 | DELIVERED needs a recipient name (2-100 chars); a note (up to 500) and one photo are optional; a photo is accepted only with DELIVERED. |
| BR-18 | Timestamps are stored and exchanged in UTC; "today", daily report buckets and displayed times use Asia/Colombo. |
| BR-19 | Password policy, lockout (5 failures, 15 min), access token 15 min, refresh token 7 days (cap 30 days): see FR-AUTH-002, 005, 006, 007. |
| BR-20 | Public tracking exposes only: tracking number, status, timestamps, origin and destination district and zone names, failure reason label. Never names, phones, emails, addresses, notes, proof, courier identity, fees or merchant data. |
| BR-21 | Create-parcel and create-order requests accept an optional `Idempotency-Key`, valid 24 h (FR-PAR-006). |
| BR-22 | `merchantReference` is unique per merchant among parcels that are not CANCELLED or FAILED. |
| BR-23 | Deactivating a user or suspending a merchant blocks login, token issue and refresh immediately (refresh tokens are revoked). Access tokens already issued stay valid until they expire (at most 15 min); this window is accepted for all roles (D-11). The one exception: parcel-service re-checks the merchant in the database for every merchant order create and cancel (FR-PAR-007, 008). |
| BR-24 | Every account created or reset by an admin has `must_change_password = true` until the user sets a new password. |
| BR-25 | A resource that exists but belongs to someone else returns 404 NOT_FOUND; an operation the role may not perform returns 403 ACCESS_DENIED. |
| BR-26 | Lists use `page` (0-based, default 0) and `size` (default 20, max 100). |
| BR-27 | Every service owns its database; no service reads another service's database. Data crosses services only through events (3.5) or internal REST (7.4). |
| BR-28 | Events and logs contain no customer, merchant or recipient contact data (names, emails, phones, addresses). Staff names are allowed: `delivery.assigned` carries the courier's first name and `courier.changed` the courier's full name. The optional free-text staff note travels in `parcel.status.changed` and is stored only with the tracking timeline; because it is free text it is never written to logs, audit details, notification rows, webhook payloads or the public view. Notification contact details are fetched from the owning service at send time (FR-NTF-007). |

### 2.3 Table 2.3: Parcel status transitions

| From | To | Who | Conditions and required input |
|---|---|---|---|
| (new) | CREATED | Customer or merchant | Valid create request; sequence 1. |
| CREATED | PICKED_UP | Assigned courier | Parcel assigned to caller; optional note. |
| PICKED_UP | IN_TRANSIT | Assigned courier | Optional note. |
| IN_TRANSIT | OUT_FOR_DELIVERY | Assigned courier | Optional note. |
| OUT_FOR_DELIVERY | DELIVERED | Assigned courier | `recipientName` required; note and photo optional. |
| CREATED (assigned only), PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY | FAILED | Assigned courier; ADMIN override from PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY (FR-PAR-012) | `failureReason` allowed for the stage (Table 2.4); note required when OTHER (always for ADMIN override); no photo. |
| CREATED | CANCELLED | Owner or admin | Reason required for admin, optional for owner. |
| DELIVERED, FAILED, CANCELLED | - | - | Terminal. |

### 2.4 Table 2.4: Failure reasons allowed per stage

| Parcel status before FAILED | Allowed `failureReason` values |
|---|---|
| CREATED (pickup attempt) | SENDER_UNAVAILABLE_AT_PICKUP, WRONG_OR_INCOMPLETE_ADDRESS, ACCESS_RESTRICTED, OTHER |
| PICKED_UP, IN_TRANSIT | PARCEL_DAMAGED, OTHER |
| OUT_FOR_DELIVERY | RECIPIENT_UNAVAILABLE, WRONG_OR_INCOMPLETE_ADDRESS, RECIPIENT_REFUSED, PARCEL_DAMAGED, ACCESS_RESTRICTED, OTHER |
| ADMIN override (PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY) | PARCEL_DAMAGED, OTHER |

Any other combination returns 400 VALIDATION_ERROR (field `failureReason`).

### 2.5 Table 2.5: Notification matrix

| Event | Sender (customer account holder) | Recipient (drop-off contact) | Merchant |
|---|---|---|---|
| `parcel.created` | Email + SMS (customer-owned parcels only) | - | - (gets the API response) |
| PICKED_UP | Email + SMS | SMS, plus email if given | Webhook |
| IN_TRANSIT | Email + SMS | - | Webhook |
| OUT_FOR_DELIVERY | Email + SMS | SMS, plus email if given | Webhook |
| DELIVERED | Email + SMS | SMS, plus email if given | Webhook |
| FAILED | Email + SMS | SMS, plus email if given, only when the status before FAILED was OUT_FOR_DELIVERY | Webhook |
| CANCELLED | Email + SMS | - | Webhook |
| `merchant.status.changed` | - | - | Email to the merchant account (approved, rejected, suspended, reinstated) |

Merchant-owned parcels have no sender notification (the merchant gets webhooks). Sender SMS is sent only if the account has a phone (else SKIPPED); recipient SMS is always sent where the table says so (drop-off phone is mandatory). A pickup failure (SENDER_UNAVAILABLE_AT_PICKUP) therefore never sends a "delivery failed" message to the recipient.

---

## 3. Architecture and Integration

### 3.1 Style and deployable units

Microservices behind one API gateway; REST for synchronous calls, RabbitMQ for events. Each service owns its data (BR-27). Each backend service runs as exactly one instance. There is no BFF: the SPA calls the gateway and composes pages from several services (7.6).

| Unit | Port | Responsibility | Database |
|---|---|---|---|
| api-gateway | 8080 (management 8090, internal only) | Single entry point: routing (3.7), rate limits, security headers, request id, body size limit, blocks `/actuator` and `/internal` (404) on port 8080, SSE-friendly proxying. Its health endpoints live only on the internal management port 8090, which is never published or routed. It has its own request-id and error handling and does not use common-lib (3.4). Serves the SPA by proxying to frontend. No database. | - |
| frontend | 8080 (internal) | React SPA built to static files on Nginx (history fallback). Dev server on 5173. | - |
| auth-service | 8081 | Users, login, JWT signing (RS256), refresh tokens, Google login, merchants (profile, approval, discount, API credentials), OAuth client-credentials token endpoint, admin user and merchant management. | `swiftdrop_auth` |
| parcel-service | 8082 | Parcels, status state machine, pricing calls, idempotency, customer and merchant order APIs, receipts, admin parcel search, cancel and force-fail, reports. | `swiftdrop_parcel` |
| pricing-service | 8083 | Zones, districts, zone price matrix, size surcharges, fee calculation, public quotes and districts list. | `swiftdrop_pricing` |
| delivery-service | 8084 | Courier directory, assignment, courier delivery list and status updates, proof of delivery (incl. photos), workloads. | `swiftdrop_delivery` |
| tracking-service (Tracking Service) | 8085 | Tracking read model, status history (timeline), public tracking view and SSE, audit log store and query. | `swiftdrop_tracking` |
| notification-service | 8086 | Email and mock SMS, merchant webhook configuration, signed webhook delivery with retry and log. | `swiftdrop_notification` |
| ai-assistant (bonus) | 8000 | Chat endpoint and MCP server; calls service REST APIs as tools. | stateless |
| postgres / rabbitmq / mailpit | 5432 (host 5433) / 5672, 15672 / 1025, 8025 | Databases / broker / SMTP sink. Volumes `pgdata`, `rabbitmqdata`. | - |
| mock-merchant (S) | 9000 (host 9090) | Demo webhook receiver that shows requests and verifies signatures. | in memory |

### 3.2 Repository layout and package structure

The layout follows the existing repository README. Paths are literal.

| Path | Content |
|---|---|
| `backend/pom.xml` | Parent POM (dependency management, Java 21, plugins, JaCoCo); modules below. |
| `backend/common-lib/` | Shared library for the six servlet services (3.4); depends on common-core. |
| `backend/common-core/` | Pure-Java shared module (3.4): error codes, constants, masking, pagination DTO; built first; also usable by api-gateway. |
| `backend/api-gateway`, `auth-service`, `parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service` | One Spring Boot module each, with its own Dockerfile. Docker build context is `backend/`. |
| `backend/ai-assistant/` | Python FastAPI service (bonus). |
| `frontend/` | React + Vite app (`src/api`, `components`, `layouts`, `pages`, `services`, `hooks`, `context`, `utils`), Dockerfile, `nginx.conf`. |
| `infrastructure/docker/postgres/init-databases.sh` | Creates the six databases and users. |
| `infrastructure/keys/` | `jwt-private.pem`, `jwt-public.pem` (generated locally, git-ignored). |
| `infrastructure/scripts/` | Helper scripts (key generation notes). |
| `infrastructure/helm/swiftdrop/` | Helm chart (bonus). |
| `tools/mock-merchant/`, `tools/postman/` | Demo webhook receiver; Postman collection and curl examples for merchant orders (DEP-015). |
| `compose.yaml`, `.env.example`, `.env`, `.gitignore`, `.gitattributes` | Compose stack; `.env` is never committed; `.gitattributes` contains `* text=auto eol=lf` so shell scripts keep LF line endings on Windows. |
| `docs/` | SRS, design documents, test reports, contribution sheet. |

Java package root per service: `com.swiftdrop.<name>` where `<name>` is `gateway`, `auth`, `parcel`, `pricing`, `delivery`, `tracking`, `notification`, `common` (common-core and common-lib). Sub-packages (create only those needed): `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `client` (REST clients to other services), `messaging` (event publishers and listeners), `security`, `config`, `scheduler`.

Layer rules: controllers hold no business logic and never touch repositories or entities; services are transactional and use repositories; entities are never serialised (DTOs + MapStruct); queries are parameterised; a service never uses another service's classes except those in common-core and common-lib.

### 3.3 Cross-service interaction map

Synchronous calls use the internal REST endpoints of 7.4 (header `X-Internal-Key`, 5 s connect / 10 s read timeout, `X-Request-Id` propagated). A failed call surfaces as 503 SERVICE_UNAVAILABLE to the caller unless stated.

| Caller | Callee | When | Purpose |
|---|---|---|---|
| parcel-service | pricing-service | Every quote-for-create (customer parcel, merchant order) | Fee, zones and district names. |
| parcel-service | auth-service | Merchant order create and cancel | Merchant status, user status, discount. |
| delivery-service | parcel-service | Courier status update; courier delivery detail | Perform the transition; read parcel details. |
| auth-service | delivery-service | Courier deactivation | Count of the courier's in-progress parcels. |
| notification-service | auth-service, parcel-service | Processing an event | Sender contact, merchant email, recipient contact. |
| ai-assistant (bonus) | tracking, pricing, parcel services | Tool calls | Read-only data. |

Everything else is event-driven (3.5). No other service-to-service calls are allowed.

### 3.4 Shared library common-lib and common-core (built first)

Because every member generates code with AI, these items exist once and are not re-implemented in services. The library owner publishes them in week 1 and nobody else edits them without a change request.

- **Two modules.** `common-core` is pure Java (no Spring MVC, servlet, JPA or AMQP classes) and may be used by every Java service, including the reactive api-gateway. `common-lib` depends on common-core, holds the servlet, MVC, security, JPA and AMQP parts, and is used by the six services other than api-gateway. Spring Cloud Gateway refuses to start with Spring MVC on its classpath, so api-gateway never depends on common-lib; it has its own request-id and error handling (FR-GW-008). Items below belong to common-lib unless marked common-core.
- **common-core:** `ErrorCode` enum (all codes of 7.2 with HTTP status as a number) and `DomainException`. **common-lib:** one `GlobalExceptionHandler` (Problem Details, RFC 9457) for exceptions raised in controllers, validation-error mapping.
- `RequestIdFilter` (reads or creates `X-Request-Id`, puts it in MDC as `requestId`, returns it on the response) and a propagation helper for outbound calls (servlet services only; the gateway has its own, FR-GW-004, FR-GW-008).
- **Security:** resource-server configuration that verifies RS256 JWTs with the public key (`JWT_PUBLIC_KEY_PATH`), checks `iss`, `aud`, `exp` (30 s skew), maps `role` to authority `ROLE_<role>`, and `scope` to `SCOPE_<scope>`; `CurrentUser` (userId, role, typ, mid, scopes); `PasswordChangeRequiredFilter` (token claim `pwc=true` gives 403 PASSWORD_CHANGE_REQUIRED on every endpoint except those a service whitelists); `InternalKeyFilter` for `/internal/**`; `OwnershipPolicy.isOwnerOrAdmin(user, customerId, merchantId)`. Errors raised inside security filters cannot reach `GlobalExceptionHandler`, so common-lib also provides an `AuthenticationEntryPoint` and an `AccessDeniedHandler` (and the filters above write their errors the same way) that emit Problem Details of 7.2: 401 TOKEN_EXPIRED for an expired token (the refresh logic of FR-UI-001 depends on it), 401 UNAUTHENTICATED for a missing or invalid token, 403 ACCESS_DENIED for a role, token type or scope that is not allowed. Token type rule, implemented once: a token with `typ=client` is accepted only on `/merchant/orders` and `/merchant/orders/**`; on every other authenticated endpoint of every service it gets 403 ACCESS_DENIED (3.6).
- **Events:** `EventEnvelope`, event data records for every event of 3.5, `EventTypes` (routing keys), `EventTopology` (exchange, queue, DLQ and binding declarations and arguments, so no service declares a queue with different arguments), publisher and consumer helpers (JSON converter, retry configuration), `OutboxEvent` entity and repository, `OutboxService.add(...)`, `OutboxPublisher` scheduler, `AuditEvents.record(...)` helper.
- **common-core:** `MaskingUtil` (`+94*****1234`, `j***@example.com`), Pagination DTO (`items`, `page`, `size`, `totalItems`, `totalPages`); **common-lib:** `InternalRestClientFactory` (timeouts, internal key, request id), JSON logging configuration (Spring Boot structured logging, format logstash, fields timestamp, level, service name, logger, message, requestId).
- **common-core constants:** parcel statuses, roles, sizes, failure reasons with labels (6.2).

Each service's Flyway migration creates the tables it needs, including the shared table definitions of 6.3 (`outbox_events`, `processed_events`) copied exactly.

### 3.5 Event model

Exchange `swiftdrop.events` (topic, durable); dead-letter exchange `swiftdrop.dlx` (direct, durable). `EventTopology` in common-lib declares exchanges, queues and bindings; every publisher and consumer calls it at startup, so services start in any order. Messages are persistent and published with confirms and the mandatory flag; an unroutable or unconfirmed message leaves the outbox row unpublished for retry.

| Queue | Consumer | Bindings (routing keys) | Properties |
|---|---|---|---|
| `tracking.events` | tracking-service | `parcel.created`, `parcel.status.changed`, `delivery.assigned`, `delivery.unassigned`, `audit.recorded` | Durable; dead-letters to `swiftdrop.dlx` with key `tracking.events.dlq`. |
| `delivery.events` | delivery-service | `parcel.created`, `parcel.status.changed`, `courier.changed` | Durable; DLQ key `delivery.events.dlq`. |
| `notification.events` | notification-service | `parcel.created`, `parcel.status.changed`, `merchant.status.changed` | Durable; DLQ key `notification.events.dlq`. |
| `<queue>.dlq` (three) | Operators | the DLQ keys above | Durable; messages expire after 3 days (`x-message-ttl`). |

**Envelope** (JSON; AMQP properties `contentType=application/json`, `deliveryMode=2`, `messageId=eventId`, `correlationId`, `type=eventType`):

```json
{ "eventId": "uuid", "eventType": "...", "schemaVersion": 1, "occurredAt": "ISO-UTC", "correlationId": "uuid", "data": {} }
```

| Event (routing key) | Producer | `data` fields |
|---|---|---|
| `parcel.created` | parcel-service | parcelId, trackingNumber, status (CREATED), ownerType (CUSTOMER or MERCHANT), customerId?, merchantId?, merchantReference?, originDistrictCode, originDistrictName, originZoneName, destinationDistrictCode, destinationDistrictName, destinationZoneName, pickupCity, dropoffCity, createdAt. |
| `parcel.status.changed` | parcel-service | parcelId, trackingNumber, sequence (2, 3, ...), previousStatus, status, statusChangedAt, actorUserId? (null for SYSTEM), actorRole, note?, failureReasonCode?, ownerType, customerId?, merchantId?, merchantReference?. |
| `delivery.assigned` | delivery-service | parcelId, courierId, courierFirstName, assignedAt. |
| `delivery.unassigned` | delivery-service | parcelId, unassignedAt, reason (COURIER_DEACTIVATED). |
| `courier.changed` | auth-service | courierId, fullName, status (ACTIVE or DEACTIVATED), vehicleType, vehicleNumber. Emitted on courier create, edit, activate, deactivate. |
| `merchant.status.changed` | auth-service | merchantId, businessName, previousStatus, status, reason?. |
| `audit.recorded` | auth, parcel, pricing, delivery service | actorUserId, actorRole, action, entityType, entityId, details (JSON object, no passwords, secrets or contact data), occurredAt. |

**Reliability rules:**

- **Outbox:** a service writes the event to its `outbox_events` table in the same transaction as the business change. `OutboxPublisher` runs every `OUTBOX_POLL_INTERVAL_MS` (default 1000), reads up to 100 unpublished rows ordered by `created_at` with `FOR UPDATE SKIP LOCKED`, publishes one by one, waits for the confirm, then sets `published_at`. Published rows older than 1 day are deleted by a daily job (S).
- Consumers use manual ack after success, prefetch 10, and 3 attempts in total with waits of 1 s then 5 s between them (Spring AMQP stateless retry, multiplier 5); then the message is rejected without requeue into the DLQ.
- Consumers de-duplicate on `eventId` through `processed_events` (inserted in the same transaction as the handling). An event whose `sequence` is not greater than the stored sequence for that parcel is stale: it is acknowledged and ignored. A `parcel.status.changed` for a parcel the consumer does not know yet is a retryable error (so ordering gaps resolve through the retry policy).
- `X-Request-Id` of the originating request is stored in the outbox and carried as `correlationId`; consumers put it in MDC.
- If the broker is down, business operations still succeed and events accumulate in the outbox, then publish after recovery (duplicates after a crash are acceptable).

### 3.6 Authentication and token design

- Only auth-service holds the private key (`JWT_PRIVATE_KEY_PATH`) and signs tokens (RS256, header `kid=swiftdrop1`). Every other Java service holds only the public key (`JWT_PUBLIC_KEY_PATH`) and verifies tokens itself; the gateway does not validate tokens (it only routes).
- **Access token claims** (FR-AUTH-006): `sub` = user id (always a user id), `role`, `typ` (`user` or `client`), `mid` = merchant id (present only when role = MERCHANT, in both token types), `scope` (client tokens only), `pwc = true` (only while the user must change the password), `iss = swiftdrop`, `aud = swiftdrop-api`, `iat`, `exp`, `jti`. No email, phone or name.
- Ownership rules use `sub` for customers and `mid` for merchants. Nothing else may be assumed from `sub`.
- Internal endpoints (`/internal/**`) are secured only by `X-Internal-Key` (constant-time compare), not by JWT.
- **Token type rule:** a client-credentials token (`typ=client`) is accepted only on `/merchant/orders` and `/merchant/orders/**`, with scope `orders:read` or `orders:write`. Every other authenticated endpoint of every service returns 403 ACCESS_DENIED for it (including `/me`, receipts, timelines, proofs, credentials and webhook endpoints). The rule is implemented once in common-lib (3.4).

### 3.7 Gateway routing, limits and headers

Routes are evaluated in this order; the first match wins. Every `/api/v1/...` path in this SRS appears in exactly one route.

| # | Path predicate | Upstream |
|---|---|---|
| 1 | `/actuator/**`, `/internal/**` | Explicit 404 NOT_FOUND, never proxied |
| 2 | `/api/v1/assistant/**`, `/mcp` (bonus) | ai-assistant:8000 (503 ASSISTANT_UNAVAILABLE if down) |
| 3 | `/api/v1/auth/**`, `/api/v1/oauth/**`, `/api/v1/me`, `/api/v1/me/**`, `/api/v1/merchant/credentials`, `/api/v1/merchant/credentials/**`, `/api/v1/admin/users`, `/api/v1/admin/users/**`, `/api/v1/admin/merchants`, `/api/v1/admin/merchants/**`, `/api/v1/admin/dashboard/merchants` | auth-service:8081 |
| 4 | `/api/v1/public/districts`, `/api/v1/public/quotes`, `/api/v1/admin/zones`, `/api/v1/admin/zones/**`, `/api/v1/admin/districts/**`, `/api/v1/admin/pricing`, `/api/v1/admin/pricing/**`, `/api/v1/admin/size-surcharges` | pricing-service:8083 |
| 5 | `/api/v1/customer/**`, `/api/v1/merchant/orders`, `/api/v1/merchant/orders/**`, `/api/v1/merchant/dashboard/**`, `/api/v1/parcels/**`, `/api/v1/admin/parcels`, `/api/v1/admin/parcels/**`, `/api/v1/admin/reports/**`, `/api/v1/admin/dashboard/parcels` | parcel-service:8082 |
| 6 | `/api/v1/courier/**`, `/api/v1/admin/deliveries`, `/api/v1/admin/deliveries/**`, `/api/v1/admin/couriers`, `/api/v1/admin/couriers/**`, `/api/v1/admin/dashboard/dispatch`, `/api/v1/proofs/**` | delivery-service:8084 |
| 7 | `/api/v1/public/tracking/**`, `/api/v1/tracking/**`, `/api/v1/admin/audit-logs` | tracking-service:8085 (stream path: no response timeout) |
| 8 | `/api/v1/merchant/webhook`, `/api/v1/merchant/webhook/**` | notification-service:8086 |
| 9 | any other `/api/**` | Gateway answers 404 NOT_FOUND |
| 10 | everything else | frontend:8080 (static SPA) |

**Gateway rules (FR-GW):** per client IP and per minute, in-memory token buckets (configurable, `RATE_LIMIT_ENABLED`):

- `POST /auth/login`, `/auth/register`, `/auth/register-merchant`, `/oauth/token`: 30 (burst 30)
- `/auth/refresh`: 60 (burst 30)
- `/public/tracking/**` and `/public/quotes`: 120 (burst 40)
- assistant: 10 (burst 5)
- all other `/api/**`: 300 (burst 100)

Exceeding gives 429 RATE_LIMITED with `Retry-After`. Request body limit 1 MB, except `POST /api/v1/courier/deliveries/*/status` 11 MB (413 PAYLOAD_TOO_LARGE by Content-Length). Security headers (NFR-SEC-015) are added to every response. The browser uses relative URLs on one origin (`http://localhost:8080`); CORS allows only `PUBLIC_BASE_URL` (plus `http://localhost:5173` in the dev profile).

### 3.8 Real-time tracking (SSE with polling fallback)

- The browser opens `GET /api/v1/public/tracking/{trackingNumber}/stream` with EventSource. tracking-service sends event `snapshot` (same data as the public view), then event `status` (same JSON) for each `parcel.status.changed` of that parcel, and a comment `: heartbeat` every 25 s. Unknown or malformed number: 404 NOT_FOUND before the stream starts.
- tracking-service keeps an in-memory map trackingNumber to emitters; the event consumer updates the database and then pushes to the emitters (single instance).
- After a terminal-status event the server completes the stream and the client calls `close()` on its EventSource immediately (browsers otherwise reconnect). Maximum stream lifetime 30 min; the client then reconnects only if the parcel is not terminal.
- After 3 consecutive stream failures the client polls `GET /api/v1/public/tracking/{trackingNumber}` every 15 s and retries SSE every 2 min.
- On every status event (and every poll result) a logged-in owner's detail page also refetches its other data (parcel detail, timeline, proof), see FR-UI-007.
- Target latency p95 at most 3 s from commit to browser.

---

## 4. Use Cases and Key Flows

### 4.1 Use cases by actor (input for the use-case diagrams, one diagram per actor group)

| Actor | Use cases (and key alternative flows) |
|---|---|
| Public | UC-P01 Track a parcel by number (unknown number shows a generic not-found message). |
| Customer | UC-C01 Register (email exists, weak password); UC-C02 Log in with password (wrong credentials, lockout, deactivated, change required); UC-C03 Log in with Google (consent denied, email already registered locally); UC-C04 Get fee quote; UC-C05 Create delivery request (fee changed, validation, duplicate submit); UC-C06 Track own parcel live; UC-C07 View history; UC-C08 Download receipt; UC-C09 Cancel while CREATED; UC-C10 View proof of delivery; UC-C11 Manage profile and change password. |
| Merchant | UC-M01 Register (waits for approval); UC-M02 Log in to dashboard (pending, rejected, suspended); UC-M03 Manage API credentials; UC-M04 Obtain access token (system); UC-M05 Create order via API (duplicate reference, idempotent replay, validation); UC-M06 Configure webhook and test ping; UC-M07 Receive webhook (verify signature, retries); UC-M08 View dashboard and orders; UC-M09 Cancel order; UC-M10 View webhook log and retry. |
| Courier | UC-K01 Log in and change initial password; UC-K02 View today's deliveries; UC-K03 Update parcel status (next valid action only); UC-K04 Record proof of delivery; UC-K05 Report failed delivery. |
| Admin | UC-A01 Review unassigned parcels; UC-A02 Assign/reassign; UC-A03 Monitor workloads; UC-A04 Manage users; UC-A05 Manage merchants; UC-A06 Manage zones and districts; UC-A07 Manage pricing; UC-A08 View reports; UC-A09 Search parcels, cancel CREATED, fail stuck parcel; UC-A10 View audit log. |
| Any | UC-X01 Ask the AI assistant (bonus). |

### 4.2 Flow 1: customer creates a delivery request

1. Customer opens New Delivery and enters pickup, drop-off, weight, size. The SPA calls `POST /api/v1/public/quotes` (pricing-service, debounced 400 ms) and shows the breakdown.
2. Customer confirms. The SPA generates a UUID `Idempotency-Key` once per confirmation screen, disables the button and sends `POST /api/v1/customer/parcels` with `expectedFee`.
3. parcel-service authenticates the JWT, checks role CUSTOMER and validates the body (400 VALIDATION_ERROR).
4. Idempotency lookup first (FR-PAR-006): if a stored result exists for (owner, key) with the same request hash, return the stored 201 with `Idempotent-Replay: true` and stop; with a different hash return 422 IDEMPOTENCY_KEY_REUSED. This happens before any pricing or fee comparison, so a retry after a price change still returns the original 201.
5. No stored result: parcel-service calls pricing-service `POST /internal/v1/pricing/calculate`. If the total differs from `expectedFee`: 409 FEE_CHANGED with the new fee, nothing created.
6. One transaction: generate the tracking number, insert the parcel (CREATED, `status_sequence` 1), insert the `idempotency_keys` row (with the response body), insert the `parcel.created` outbox row. Return 201 with `Location`. A concurrent duplicate request waits on the unique key, then replays the stored response.
7. OutboxPublisher publishes `parcel.created`. tracking-service creates the tracking record and timeline; delivery-service creates the delivery row (unassigned); notification-service fetches the sender contact from auth-service and sends email and SMS (Table 2.5).
8. The SPA opens the parcel detail page: parcel data from parcel-service, timeline from tracking-service (FR-UI-007 explains the short delay).

### 4.3 Flow 2: merchant creates an order and receives a webhook

1. Merchant system: `POST /api/v1/oauth/token` (`grant_type=client_credentials`, `client_id`, `client_secret`). auth-service verifies the secret hash and that the merchant is APPROVED and the user ACTIVE; returns a JWT (`typ=client`, `role=MERCHANT`, `mid`, `scope`, 900 s).
2. `POST /api/v1/merchant/orders` with the bearer token and optional `Idempotency-Key`. parcel-service validates token, scope `orders:write` and body; checks idempotency first (as Flow 1 step 4); calls auth-service `GET /internal/v1/merchants/{mid}` and returns 403 MERCHANT_NOT_ACTIVE unless the merchant is APPROVED and the user ACTIVE; prices through pricing-service with the merchant discount; checks `merchantReference` uniqueness; persists parcel, key and outbox row in one transaction; returns 201 with tracking number and fee.
3. Later a courier changes the status (Flow 3); parcel-service writes a `parcel.status.changed` outbox row.
4. notification-service consumes it. For a parcel with `merchantId` it looks up the merchant's webhook config in its own database; if missing or disabled it ignores the webhook part. Otherwise it inserts a `webhook_deliveries` row (unique by event id).
5. The webhook scheduler picks the due row, builds the payload, signs `timestamp.body` with HMAC-SHA256, validates the URL (FR-WHK-007) and POSTs to the merchant. The merchant verifies the signature and timestamp, deduplicates by event id, answers 2xx.
6. On 2xx the delivery is SUCCEEDED; on failure a retry is scheduled (FR-WHK-005).

### 4.4 Flow 3: courier status update

1. The courier opens an assigned delivery and chooses the next action (DELIVERED also needs recipient name, optional photo and note). The SPA sends `POST /api/v1/courier/deliveries/{parcelId}/status` as multipart/form-data to delivery-service.
2. delivery-service checks role COURIER and loads, inside the transaction of step 3, its `deliveries` row where `courier_id` equals the caller with a pessimistic write lock (else 404), then validates the input and the photo (FR-DEL-006). An admin assignment or reassignment (FR-DEL-002) takes the same lock on the same row, so the two operations never run at the same time on one parcel: the one that gets the lock second sees the committed result (a courier whose parcel was just reassigned gets 404; an admin whose parcel was just picked up gets 409 PARCEL_NOT_ASSIGNABLE).
3. In one local transaction: if DELIVERED, insert the proof rows; call parcel-service `POST /internal/v1/parcels/{parcelId}/transition`; on success update the local delivery row (status, sequence, terminal time). If the call answers 409 or 5xx or times out, the transition may nevertheless have been applied (for example the response was lost), so delivery-service re-reads the parcel through `GET /internal/v1/parcels/{parcelId}` and compares its real status and status sequence with the update being made. When the parcel already has the requested status and the sequence is the local sequence plus 1, the transition is treated as successful: the local transaction commits (proof rows and delivery row as above) and the courier gets the normal success response. This also lets a courier's retry succeed after an earlier attempt rolled back, instead of failing with 409 and losing the proof. In every other case (no match, or the re-read also fails) the local transaction rolls back, so no proof is stored for a transition that did not happen, and the error is returned to the courier (409 errors keep their code, other failures 503 SERVICE_UNAVAILABLE).
4. parcel-service (`ParcelTransitionService`) validates the transition (FR-PAR-002), updates the parcel with optimistic locking, increments `status_sequence`, sets `terminal_at` for terminal statuses and writes the `parcel.status.changed` outbox row, in one transaction.
5. The event reaches `tracking.events` (timeline, SSE push), `delivery.events` (ignored if already applied, by sequence) and `notification.events` (emails, SMS, webhook rows per Table 2.5).
6. Consumers retry 3 attempts, then dead-letter on processing errors.

### 4.5 Flow 4: Login with Google (OAuth 2.0 code flow with PKCE)

1. Customer clicks "Continue with Google"; the browser goes to `GET /api/v1/auth/google/login`.
2. auth-service generates `state`, `nonce` and a PKCE `code_verifier`; stores nonce, verifier and a hash of state in `oauth_login_states` (valid 5 min); sets cookie `sd_oauth` (state value; HttpOnly, SameSite=Lax, path `/api/v1/auth/google`, 5 min).
3. It redirects (302) to Google with `response_type=code`, `scope=openid email profile`, `client_id`, `redirect_uri`, `state`, `nonce`, `code_challenge` (S256).
4. Google redirects to `/api/v1/auth/google/callback?code=...&state=...`. auth-service verifies `state` equals the `sd_oauth` cookie and exists unused and unexpired in the database (then deletes it); on mismatch it redirects to `/login?error=google_denied` or `google_failed`.
5. It exchanges the code server-side (with client secret and verifier) and validates the ID token: signature against Google keys, `iss`, `aud`, `exp`, `nonce`, `email_verified = true`.
6. It applies FR-AUTH-010 (log in a linked user, create a new CUSTOMER, or reject with `account_exists` / `account_deactivated`), creates a refresh token, sets cookie `sd_refresh`, redirects to `${PUBLIC_BASE_URL}/auth/callback`.
7. The SPA calls `POST /api/v1/auth/refresh` to get the access token and user, then opens the customer dashboard.

### 4.6 Flow 5: assignment and courier deactivation

- **Assign:** admin calls `POST /api/v1/admin/deliveries/{parcelId}/assign` on delivery-service, which locks its delivery row (pessimistic write lock, as in Flow 3) and checks it (status CREATED) and its courier directory (ACTIVE), updates assignment rows, writes outbox rows `delivery.assigned` and `audit.recorded` in one transaction.
- **Deactivate courier:** admin calls `POST /api/v1/admin/users/{id}/deactivate` on auth-service. For a COURIER it calls delivery-service `GET /internal/v1/couriers/{id}/active-count`; if `inProgress` is above 0 it returns 409 COURIER_HAS_ACTIVE_DELIVERIES. Otherwise it deactivates the user, revokes refresh tokens and writes `courier.changed` (DEACTIVATED) and audit rows in one transaction. delivery-service consumes `courier.changed`, marks the courier inactive and unassigns that courier's CREATED deliveries (emitting `delivery.unassigned`).

---

## 5. Functional Requirements (by service)

API paths are relative to `/api/v1`. Full endpoint list: 7.3; error codes: 7.2. Requirements are Must unless marked (S). Each subsection is owned by one service.

### 5.1 auth-service (FR-AUTH)

| ID | Requirement |
|---|---|
| FR-AUTH-001 | A visitor self-registers (`POST /auth/register`) as CUSTOMER with `fullName` (2-100), `email` (valid, max 254, unique case-insensitive, stored lower-case), `phone` (Sri Lankan: `07XXXXXXXX`, `+947XXXXXXXX` or landline forms, normalised to E.164 `^\+94[1-9][0-9]{8}$`) and `password`. The server sets the role; unknown properties (for example `role`) are rejected with 400 VALIDATION_ERROR. Existing email returns 409 EMAIL_ALREADY_REGISTERED. Returns 201 with the user summary (no tokens). |
| FR-AUTH-002 | Password policy (all local accounts): 10-64 chars, at least one upper, lower, digit and special character, must not contain the email local part. Hash with BCrypt cost 12; never log or return it. |
| FR-AUTH-003 | `POST /auth/login` (email, password) returns `{accessToken, tokenType:"Bearer", expiresIn:900, user}` and sets the refresh cookie. `user` = id, fullName, role, mustChangePassword and, for merchants, merchantStatus and merchantId. |
| FR-AUTH-004 | Unknown email, wrong password or a Google-only account all return the same 401 INVALID_CREDENTIALS ("Invalid email or password"). Only after valid credentials: deactivated returns 403 ACCOUNT_DEACTIVATED; merchant PENDING, REJECTED or SUSPENDED returns 403 MERCHANT_PENDING, MERCHANT_REJECTED or MERCHANT_SUSPENDED (no reason in the response). |
| FR-AUTH-005 | After 5 consecutive failed logins for an existing account, lock it for 15 min: further attempts return 429 ACCOUNT_LOCKED with `Retry-After`, even with the right password. A successful login resets the counter. Counters and lock time are stored in `users`. |
| FR-AUTH-006 | Access tokens: JWT RS256 signed with the private key from `JWT_PRIVATE_KEY_PATH` (the service refuses to start if the key file is missing or invalid). Claims exactly as 3.6. Lifetime 15 min for user and client tokens. `pwc` is true while `must_change_password` is true. |
| FR-AUTH-007 | Refresh tokens: 256-bit random opaque value in cookie `sd_refresh` (HttpOnly, Secure when `COOKIE_SECURE=true`, SameSite=Strict, path `/api/v1/auth`, Max-Age 7 days); only the SHA-256 hash is stored with a `family_id`. `POST /auth/refresh` (header `X-Requested-With: SwiftDrop` required) rotates: marks the token used, issues a new one in the same family and a new access token. A revoked token, or a used token presented more than `REFRESH_GRACE_SECONDS` (default 10) after use, revokes the whole family and returns 401 UNAUTHENTICATED. Within the grace period (two tabs refreshing together) the response has a new access token but no new cookie. A family lives at most 30 days. Refresh for a deactivated user or a non-APPROVED merchant returns 401 UNAUTHENTICATED and revokes the family. |
| FR-AUTH-008 | `POST /auth/logout` (same header) revokes the family and clears the cookie; returns 204 even without a valid cookie. |
| FR-AUTH-009 | Google login follows Flow 4: code flow with PKCE S256, state bound to the browser via `sd_oauth`, nonce, server-side exchange, full ID-token validation. Endpoints `GET /auth/google/login` and `/auth/google/callback`. All failures redirect to `/login?error=<code>` with `google_denied`, `google_failed`, `account_exists` or `account_deactivated`; Google error details and tokens are never exposed. |
| FR-AUTH-010 | Google account rules: (a) `email_verified` must be true else `google_failed`; (b) a user already linked by Google `sub` logs in (blocked if deactivated); (c) no link but a local account with the same email: reject with `account_exists`, never auto-merge (local sign-up does not verify email, so merging would allow takeover); (d) otherwise create a CUSTOMER with `auth_provider=GOOGLE`, no password, `google_sub`, name from Google, no phone. Only customers can use Google login. |
| FR-AUTH-011 | `GET /auth/config` (public) returns `{"googleEnabled": bool}`; true when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set. |
| FR-AUTH-012 | `GET /me` returns id, fullName, email, phone, role, authProvider, mustChangePassword, createdAt and, for merchants, `merchant {id, businessName, status, discountPercent}`. `PUT /me` updates fullName and phone only. |
| FR-AUTH-013 | `POST /me/change-password` needs `currentPassword` (local accounts) and `newPassword` (policy, different from current). Revokes all refresh families, clears `must_change_password`, returns 204; the client logs in again. Google-only accounts get 400. |
| FR-AUTH-014 | While `pwc` is true, every authenticated endpoint in every service except `GET /me`, `POST /me/change-password`, `POST /auth/refresh`, `POST /auth/logout` returns 403 PASSWORD_CHANGE_REQUIRED (common-lib filter). The SPA redirects to `/change-password`. |
| FR-AUTH-015 | At startup, if no ADMIN exists, create one from `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NAME`, `BOOTSTRAP_ADMIN_PASSWORD` (policy enforced; clear startup failure if invalid) with `must_change_password = true`. |
| FR-AUTH-016 | `POST /auth/register-merchant` creates a MERCHANT user and a merchant with status PENDING. Body: businessName (2-120), businessRegistrationNo? (up to 40), contactName (2-100, stored as the user's full name), email, phone, password, addressLine1 (1-150), city (1-80). Returns 201 with a "pending approval" message and no tokens. |
| FR-AUTH-017 | Merchant administration: `GET /admin/merchants` (filters status, q = business name prefix, paginated), `GET /admin/merchants/{id}`, `POST .../approve` (from PENDING or REJECTED), `/reject {reason}`, `/suspend {reason}`, `/reinstate` (from SUSPENDED), `PATCH .../discount {discountPercent 0-50}`. Invalid transitions: 409 INVALID_STATUS_TRANSITION. Every status action writes `merchant.status.changed` and an audit event in the same transaction. |
| FR-AUTH-018 | API credentials (dashboard login only, `typ=user`): `POST /merchant/credentials {label 1-50}` creates `client_id` (`sdc_` + 24 random chars) and `client_secret` (`sds_` + 43 URL-safe chars from 32 random bytes); the secret is shown once and only its SHA-256 hash is stored. `GET /merchant/credentials` lists id, label, status, createdAt, lastUsedAt (never the secret). `DELETE /merchant/credentials/{id}` revokes. At most 2 ACTIVE credentials; more returns 409 CREDENTIAL_LIMIT_REACHED. Create and revoke are audited. |
| FR-AUTH-019 | `POST /oauth/token` (public, form-encoded) client-credentials grant: `grant_type=client_credentials`, `client_id`, `client_secret` (HTTP Basic also accepted), optional `scope` (subset of `orders:read orders:write`, default both). Returns `{access_token, token_type:"Bearer", expires_in:900, scope}`; token is a JWT `typ=client`, `role=MERCHANT`, `sub` = the merchant's user id, `mid` = merchant id, `scope`. Errors per RFC 6749 as `{error, error_description}`: `invalid_client` 401; `invalid_request`, `unsupported_grant_type`, `invalid_scope` 400. A merchant that is not APPROVED, a DEACTIVATED merchant user and a revoked credential all give `invalid_client`. Secrets are compared in constant time; `last_used_at` is updated. |
| FR-AUTH-020 | Users: `GET /admin/users` (filters role, status, q = name or email prefix, paginated); `POST /admin/users` creates a COURIER (fullName, email, phone, vehicleType, vehicleNumber) or ADMIN (fullName, email, phone) with a generated 16-char temporary password shown once and `must_change_password = true`; `GET /admin/users/{id}`; `PATCH /admin/users/{id}` edits name, phone and courier vehicle data; `POST /admin/users/{id}/deactivate` and `/activate`. An admin cannot deactivate self. Deactivating a COURIER follows Flow 5 (409 COURIER_HAS_ACTIVE_DELIVERIES; 503 SERVICE_UNAVAILABLE if delivery-service cannot answer, nothing changes). Deactivation revokes all refresh tokens. Customers and merchants can only be deactivated or activated. Courier create, edit and (de)activation write `courier.changed`. |
| FR-AUTH-021 | `POST /admin/users/{id}/reset-password` (any user) generates a new temporary password (shown once, given to the user out of band), sets `must_change_password = true`, revokes refresh tokens. Google-only accounts return 400. |
| FR-AUTH-022 | Internal endpoints (7.4): `GET /internal/v1/users/{id}/contact`, `GET /internal/v1/merchants/{merchantId}`. |
| FR-AUTH-023 | Every mutating admin action (user create/edit/(de)activate/reset, merchant status and discount) and credential create/revoke writes an `audit.recorded` outbox event (via `AuditEvents`) in the same transaction. Details never contain passwords, secrets or contact data. |
| FR-AUTH-024 | `GET /admin/dashboard/merchants` returns `{pendingMerchants}`. |
| FR-AUTH-025 (S) | With `DEMO_SEED_ENABLED=true` auth-service idempotently creates demo accounts (password `DEMO_USER_PASSWORD`, `must_change_password=false`): customer `customer@demo.example`, approved merchant `merchant@demo.example` (discount 10 %), couriers `courier1@demo.example`, `courier2@demo.example`, `courier3@demo.example` (each emits `courier.changed`). Disabled by default. |
| FR-AUTH-026 | Purge jobs: used or expired refresh tokens 7 days after expiry; `oauth_login_states` hourly. |

### 5.2 pricing-service (FR-PRC)

| ID | Requirement |
|---|---|
| FR-PRC-001 | One `FeeCalculator` computes the fee exactly by BR-08 with decimals. Reference results AT-05 to AT-07 shall be unit tests. |
| FR-PRC-002 | `POST /public/quotes` (no auth, rate limited): `originDistrictCode`, `destinationDistrictCode`, `weightKg`, `size` returns `{currency, originZone{code,name}, destinationZone{code,name}, baseFee, weightFee, sizeSurcharge, discountAmount (0), total}`. |
| FR-PRC-003 | Internal `POST /internal/v1/pricing/calculate` takes the same body plus `discountPercent` (default 0) and returns the quote fields plus `originDistrict{code,name}`, `destinationDistrict{code,name}`, `subtotal`, `discountPercent`. Used by parcel-service for every create. |
| FR-PRC-004 | Unknown district, invalid size, or a weight below 0.01, with more than 2 decimals, or above `MAX_PARCEL_WEIGHT_KG` (this service is the only one that enforces the maximum): 400 VALIDATION_ERROR (field `weightKg` for a weight); district in an inactive zone: 422 ZONE_NOT_SERVICEABLE; zone pair without a pricing row: 422 PRICING_NOT_CONFIGURED. |
| FR-PRC-005 | `GET /public/districts` returns `{maxWeightKg, sizes[{size, surcharge, description}], districts[{code, name, zoneCode, zoneName}]}` for active zones. |
| FR-PRC-006 | Zones: `GET /admin/zones` (with districts), `POST /admin/zones {code, name}`, `PATCH /admin/zones/{id} {name, active}`, `PUT /admin/districts/{districtCode}/zone {zoneId}`. A district belongs to exactly one zone; a zone that still has districts cannot be deactivated (409 ZONE_IN_USE). Zone codes match `[A-Z_]{2,20}` and are immutable. |
| FR-PRC-007 | Pricing: `GET /admin/pricing` (full matrix), `PUT /admin/pricing/{originZoneId}/{destinationZoneId} {baseFee, perKgFee}` (decimals >= 0, 2 places, max 1,000,000.00), `GET/PUT /admin/size-surcharges`. Changes affect future quotes and parcels only (parcels snapshot their fee, BR-09). |
| FR-PRC-008 | Zone, district move, pricing and surcharge changes write `audit.recorded` outbox events. |
| FR-PRC-009 | Flyway V2 seeds Appendix B (6 zones, 25 districts, 36 pricing rows, 3 size surcharges). The maximum weight comes from `MAX_PARCEL_WEIGHT_KG` (default 30). |

### 5.3 parcel-service (FR-PAR)

| ID | Requirement |
|---|---|
| FR-PAR-001 | Statuses and transitions are exactly BR-03 and Table 2.3; anything else returns 409 INVALID_STATUS_TRANSITION. |
| FR-PAR-002 | `ParcelTransitionService` is the only code that changes `parcels.status`. It validates the actor role, the transition, the failure reason against Table 2.4, `recipientName` for DELIVERED (2-100) and notes (up to 500), then in one transaction updates the parcel (optimistic `version`, `status_sequence` + 1, `terminal_at` for terminal statuses) and writes the `parcel.status.changed` outbox row (carrying note, actor and failure reason code). It is used by owner cancel, admin cancel, admin fail and the internal transition endpoint. |
| FR-PAR-003 | A lost optimistic-lock update returns 409 CONCURRENT_UPDATE. |
| FR-PAR-004 | Tracking numbers follow BR-11; on a unique-constraint collision retry generation up to 5 times. |
| FR-PAR-005 | `POST /customer/parcels` body: `pickup {contactName, contactPhone, addressLine1, addressLine2?, city, districtCode, postalCode?}`, `dropoff {same plus contactEmail?}`, `parcel {weightKg, size, description?}`, `deliveryInstructions?`, `expectedFee`. Limits: names 2-100, addressLine1 1-150, addressLine2 up to 150, city 1-80, postalCode 5 digits, description up to 200, deliveryInstructions up to 300, phones as FR-AUTH-001, weight format and minimum (at least 0.01, 2 decimals) and size per BR-10. parcel-service does not check the maximum weight: pricing-service does, and its 400 VALIDATION_ERROR for a weight above the maximum is returned to the caller unchanged and nothing is created. The server recomputes the fee (pricing-service) and never trusts client values; a different total returns 409 FEE_CHANGED with the new fee and creates nothing. Success: 201, `Location` header, status CREATED, tracking number, fee breakdown; writes `parcel.created`. Role CUSTOMER; owner = `sub`. |
| FR-PAR-006 | Idempotency (the only implementation, for `POST /customer/parcels` and `POST /merchant/orders`): header `Idempotency-Key` (1-64 chars of `[A-Za-z0-9_-]`), optional. Lookup by (owner_type, owner_id, key) happens first, before pricing and fee comparison. Same key, same owner, same request hash (SHA-256 of the canonical JSON of the validated body, including `expectedFee`) within 24 h returns the original status and body with `Idempotent-Replay: true` and creates nothing. Same key with a different hash: 422 IDEMPOTENCY_KEY_REUSED. A failed request (4xx or 5xx) stores nothing, so the same key can be retried. On success the key row, response body, parcel and outbox row are inserted in one transaction; a unique-constraint race is resolved by re-reading and replaying the stored response. There is no "in progress" state. `owner_type` is CUSTOMER (owner id = user id) or MERCHANT (owner id = merchant id). Keys older than 24 h are purged hourly. |
| FR-PAR-007 | `POST /merchant/orders` (scope `orders:write`, or a dashboard user token; the dashboard has no create screen, D-23). Body as FR-PAR-005 plus `merchantReference?` (up to 64); `expectedFee` is optional: when present it is compared like FR-PAR-005, when absent no comparison is made. The merchant is checked through auth-service (APPROVED and user ACTIVE, else 403 MERCHANT_NOT_ACTIVE) and its `discountPercent` is used for pricing. 201: `{id, trackingNumber, status, merchantReference, fee{currency, baseFee, weightFee, sizeSurcharge, discountAmount, total}, trackingUrl, createdAt}`, where `trackingUrl = PUBLIC_BASE_URL + /track/{trackingNumber}` (parcel-service reads `PUBLIC_BASE_URL`, 9.2). A duplicate non-cancelled, non-failed reference returns 409 DUPLICATE_MERCHANT_REFERENCE. Owner = `mid`. |
| FR-PAR-008 | `GET /merchant/orders` (scope `orders:read` or dashboard): own orders with filters status, from, to, merchantReference, q, pagination and sorting as FR-PAR-009. `GET /merchant/orders/{id}`: parcel detail; another merchant's order returns 404. `POST /merchant/orders/{id}/cancel` (scope `orders:write` or dashboard; merchant re-checked as in FR-PAR-007) cancels while CREATED (BR-07); optional `{"reason": up to 300}`. |
| FR-PAR-009 | Customer: `GET /customer/parcels` (own parcels; filters status, from, to in Asia/Colombo inclusive, q = tracking number prefix; pagination BR-26; sort by createdAt, status or trackingNumber, default `createdAt,desc`); `GET /customer/parcels/{id}` (addresses, contacts, parcel data, fee breakdown, status; no history, courier or proof, which come from tracking-service and delivery-service); `POST /customer/parcels/{id}/cancel` (optional reason; otherwise 409 INVALID_STATUS_TRANSITION). Another customer's parcel returns 404. |
| FR-PAR-010 | `GET /parcels/{id}/receipt` returns a PDF "Delivery Receipt" (`application/pdf`, filename `SwiftDrop-Receipt-<trackingNumber>.pdf`) generated on demand with OpenPDF: SwiftDrop name, receipt number (tracking number), issue date, pickup and drop-off contact names and addresses, parcel details, fee breakdown with currency, current status, and for delivered parcels the delivery time (`terminal_at`). Allowed roles: CUSTOMER, MERCHANT and ADMIN only. A COURIER (a role never allowed) and any client-credentials token get 403 ACCESS_DENIED; a customer or merchant who is not the owner gets 404 NOT_FOUND (`ParcelAccessPolicy`, BR-25). User text is escaped. |
| FR-PAR-011 | Admin: `GET /admin/parcels` searches all parcels (filters status, ownerType, from, to, q = tracking number or merchant reference prefix, origin or destination district code; pagination; sorting); `GET /admin/parcels/{id}` full detail incl. contacts, addresses, fee, owner ids, merchant reference; `POST /admin/parcels/{id}/cancel {reason 10-300}` cancels a CREATED parcel. Cancel is audited. Courier-related filters live in delivery-service (FR-DEL-009). |
| FR-PAR-012 | `POST /admin/parcels/{id}/fail {failureReason, note 5-500}` lets an ADMIN close a parcel stuck after pickup. Allowed only from PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY (else 409 INVALID_STATUS_TRANSITION), reason from Table 2.4 (admin override row). Runs through `ParcelTransitionService` (actorRole ADMIN), is audited, and produces the normal notifications and webhook. Afterwards the courier can be deactivated. |
| FR-PAR-013 | Reports (admin): `GET /admin/reports/summary?from&to` returns totals created, delivered, failed, cancelled, in progress, success rate, failure rate. `GET /admin/reports/daily?from&to` returns one row per Asia/Colombo day: created (by creation day) and delivered, failed, cancelled (by day of `terminal_at`). Success rate = delivered / (delivered + failed), failure rate = failed / (delivered + failed), over parcels whose `terminal_at` is in range, percent with one decimal (0 if denominator 0). Default range 30 days, max 366. `GET /admin/dashboard/parcels` returns `{deliveredToday, failedToday}`. |
| FR-PAR-014 | Merchant dashboard: `GET /merchant/dashboard/summary` returns order counts by status (all active, and last 30 days) and the 10 most recent orders. Dashboard login only. |
| FR-PAR-015 (S) | `GET /customer/dashboard/summary` returns counts of active and delivered parcels and the five most recent parcels. |
| FR-PAR-016 | Internal endpoints (7.4): `GET /internal/v1/parcels/{id}` (full detail plus `statusSequence`, used by delivery-service in Flow 3), `GET /internal/v1/parcels/{id}/recipient`, `POST /internal/v1/parcels/{id}/transition` (body `{toStatus, actorUserId, actorRole, note?, failureReason?, recipientName?}`, returns `{parcelId, status, sequence, statusChangedAt}`; actorRole COURIER or ADMIN; the caller, delivery-service, has already checked assignment). |
| FR-PAR-017 | `ParcelAccessPolicy` (one class): CUSTOMER if `customer_id = sub`; MERCHANT if `merchant_id = mid`; ADMIN always. The role check comes first: a role that is never allowed on the endpoint (for example COURIER on the receipt) gets 403 ACCESS_DENIED before the policy runs; a permitted role that is not the owner gets 404 NOT_FOUND. Uses `OwnershipPolicy` from common-lib. |
| FR-PAR-018 | Events: `parcel.created` once per parcel (in place of a status event), `parcel.status.changed` for every later transition including CANCELLED and FAILED (schemas 3.5). |

### 5.4 delivery-service (FR-DEL)

| ID | Requirement |
|---|---|
| FR-DEL-001 | delivery-service keeps a `deliveries` table, one row per parcel (id = parcel id), created from `parcel.created` and updated from `parcel.status.changed` (status, sequence, terminal time) and by its own actions. It also keeps a `couriers` directory updated from `courier.changed`. |
| FR-DEL-002 | `POST /admin/deliveries/{parcelId}/assign {courierId}` assigns or reassigns under BR-04. 404 unknown parcel or courier; 409 PARCEL_NOT_ASSIGNABLE (status not CREATED, courier inactive). Sets `courier_id` and `assigned_at`, closes the previous `delivery_assignments` row, inserts a new one, and writes `delivery.assigned` and an audit event in one transaction, holding the same pessimistic write lock on the `deliveries` row as Flow 3. |
| FR-DEL-003 | `GET /courier/deliveries?scope=today\|active\|completed`: `today` (default) = deliveries assigned to the caller in any active status plus those whose `terminal_at` is today in Asia/Colombo (this includes a cancelled assigned parcel, which tells the courier about the cancellation); `active` = active statuses; `completed` = terminal, paginated. Sorted by status order then oldest first. Items: parcelId, trackingNumber, status, pickupCity, dropoffCity, originDistrictName, destinationDistrictName, assignedAt. The SPA re-polls every 30 s. |
| FR-DEL-004 | `GET /courier/deliveries/{parcelId}`: parcel details fetched from parcel-service (contact names, phones, addresses, instructions, size, weight, description) without fees, plus status, `allowedNextStatuses`, `allowedFailureReasons` and the proof if delivered. Not assigned to the caller: 404. |
| FR-DEL-005 | `POST /courier/deliveries/{parcelId}/status` (multipart) fields `status`, `note?`, `recipientName?`, `failureReason?`, `photo?` performs one transition of Table 2.3 as Flow 3. Invalid: 409 INVALID_STATUS_TRANSITION. `photo` only with `status=DELIVERED` (else 400). A courier may add an optional note (up to 500) to any transition; it is visible to the owner and admins, not on the public view. A concurrent modification returns 409 CONCURRENT_UPDATE. Locking and lost-response handling follow Flow 3 steps 2 and 3 (pessimistic lock on the `deliveries` row; on timeout, 5xx or 409 the parcel's real status and sequence are re-read and a match counts as success). The UI shows only `allowedNextStatuses`. Response: `{parcelId, status, sequence, statusChangedAt}`. |
| FR-DEL-006 | Photo: JPEG or PNG detected by content inspection (not file name); upload at most `MAX_UPLOAD_MB` (default 10 MB; 413 PAYLOAD_TOO_LARGE, 415 UNSUPPORTED_MEDIA_TYPE); re-encoded to JPEG (quality 0.8) with longest side at most 1600 px (metadata stripped); the stored image must be at most `MAX_STORED_PHOTO_MB` (default 5 MB), else 413. Stored in `proof_photos`. |
| FR-DEL-007 | Proof of delivery (`delivery_proofs`: recipient name, note, has_photo, captured time) is written only for DELIVERED and never changed (BR-06). `GET /proofs/{parcelId}` returns `{recipientName, note, hasPhoto, capturedAt}`; `GET /proofs/{parcelId}/photo` returns `image/jpeg` (`Cache-Control: no-store`). Allowed: owner customer (`customer_id = sub`), owner merchant (`merchant_id = mid`), assigned courier, admin; others 404; no proof 404. |
| FR-DEL-008 | A courier can never read or change unassigned parcels or another courier's parcels (404) and cannot cancel. |
| FR-DEL-009 | Admin: `GET /admin/deliveries` lists deliveries (filters assigned (true or false), status, courierId, origin and destination district codes, creation date range, q = tracking-number prefix; paginated; unassigned queue = `assigned=false&status=CREATED`, oldest first); `GET /admin/deliveries/{parcelId}` returns `{parcelId, trackingNumber, status, courier{id, fullName, vehicleType, vehicleNumber}?, assignedAt?, assignments[{courierId, courierName, assignedBy, assignedAt, unassignedAt}]}`; `GET /admin/couriers`: couriers with vehicle details, status, activeDeliveries with counts per status (CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY), deliveredToday, failedToday, sorted by activeDeliveries ascending; `GET /admin/couriers/{id}/deliveries` (scopes as FR-DEL-003); `GET /admin/dashboard/dispatch` returns `{unassigned, inProgress, activeCouriers}`. |
| FR-DEL-010 | On `courier.changed` with status DEACTIVATED the service marks the courier inactive and, in one transaction, unassigns that courier's CREATED deliveries (closes assignment rows, writes `delivery.unassigned`). |
| FR-DEL-011 | Internal `GET /internal/v1/couriers/{id}/active-count` returns `{inProgress}` = deliveries of that courier in PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY. |
| FR-DEL-012 | Events: `delivery.assigned`, `delivery.unassigned` (3.5); audit event for every assignment. |

### 5.5 tracking-service (FR-TRK)

| ID | Requirement |
|---|---|
| FR-TRK-001 | Consumes `parcel.created` (creates `tracking_parcels` and timeline row sequence 1), `parcel.status.changed` (appends a `tracking_events` row), `delivery.assigned` and `delivery.unassigned` (sets or clears the courier id and first name), `audit.recorded` (appends `audit_logs`). Idempotent and stale-safe per 3.5. |
| FR-TRK-002 | `GET /public/tracking/{trackingNumber}` returns `trackingNumber`, `status`, `createdAt`, `updatedAt`, `origin {districtName, zoneName}`, `destination {districtName, zoneName}`, `history[{status, at, failureReasonLabel?}]` and nothing else; `Cache-Control: no-store`. Malformed and unknown numbers both return 404 NOT_FOUND (never 400). |
| FR-TRK-003 | `GET /public/tracking/{trackingNumber}/stream` implements 3.8. Each service instance supports at least 200 concurrent streams (S: 500). |
| FR-TRK-004 | `GET /tracking/parcels/{parcelId}/timeline` (roles CUSTOMER, MERCHANT, COURIER, ADMIN): returns `{parcelId, trackingNumber, status, courierFirstName?, history[{sequence, fromStatus, toStatus, at, actorRole, note, failureReasonCode, failureReasonLabel}]}`. Customer: `customer_id = sub`; merchant: `merchant_id = mid`; courier: currently assigned to the caller; admin: any; otherwise 404. `courierFirstName` is returned to customers and merchants; admins also get `courierId`. A parcel not yet projected returns 404. |
| FR-TRK-005 | `GET /admin/audit-logs` (admin): filters action, actorId, from, to, paginated, newest first. Entries are kept 1 year. |
| FR-TRK-006 | `tracking_events` rows are append-only: no update or delete in code. |
| FR-TRK-007 (S) | A Flyway-created database trigger rejects any UPDATE or DELETE on `tracking_events` (BR-06). |

### 5.6 notification-service (FR-NTF and FR-WHK)

| ID | Requirement |
|---|---|
| FR-NTF-001 | Consumes `notification.events` and creates notifications exactly as Table 2.5 (EMAIL, SMS) and webhook deliveries (FR-WHK). |
| FR-NTF-002 | Channels: EMAIL via SMTP (`SMTP_HOST`, `SMTP_PORT`, optional credentials, `SMTP_FROM`) and SMS via a mock provider that stores the message in the database and logs an INFO line with the masked number. Behind `EmailSender` and `SmsSender` interfaces. |
| FR-NTF-003 | Templates: parcel created (sender); each status for the sender; PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED for the recipient; merchant approved, rejected, suspended, reinstated. Every parcel message has the tracking number and link `${PUBLIC_BASE_URL}/track/<trackingNumber>`; failed messages include the reason label; merchant rejected/suspended messages include the reason. SMS at most 160 chars, no names or addresses. English only. |
| FR-NTF-004 | `notifications` has a unique key (`event_id`, `channel`, `recipient_kind`); a redelivered event never sends twice (a SENT or SKIPPED row is not sent again; a PENDING or FAILED row is retried). |
| FR-NTF-005 | A send failure follows the consumer retry policy; after the last failure the row is stored with status FAILED and an error summary (no personal data) and the message is dead-lettered. A missing destination (for example no sender phone) is stored as SKIPPED. |
| FR-NTF-006 | Logs and error summaries never contain emails, phones, names or addresses; destinations are masked (`+94*****1234`, `j***@example.com`). |
| FR-NTF-007 | Contact data is fetched at processing time and never stored in full: sender (customer-owned parcels) from auth-service `GET /internal/v1/users/{customerId}/contact`; recipient from parcel-service `GET /internal/v1/parcels/{parcelId}/recipient`; merchant email from auth-service `GET /internal/v1/merchants/{merchantId}`. A failed fetch is a processing error (retry policy). Only tracking number, status and reason come from the event. |
| FR-NTF-008 (S) | `GET /internal/v1/notifications?limit=50` (header `X-Internal-Key`) lists recent notifications incl. SMS bodies for demos. |
| FR-WHK-001 | Merchant webhook configuration (dashboard login only, `typ=user`, owner = `mid`): `GET /merchant/webhook` returns `{url, enabled, hasSecret}` (never the secret); `PUT /merchant/webhook {url, enabled}` (HTTPS URL up to 500 chars; HTTP only when `WEBHOOK_ALLOW_HTTP=true`); the first save generates the secret and returns `{url, enabled, hasSecret, secret}` once; `POST /merchant/webhook/rotate-secret` returns `{secret}` once (secret format `whsec_` + 43 URL-safe chars); `POST /merchant/webhook/test` creates a delivery of type `webhook.test` that is signed, sent and logged. The secret is stored encrypted with AES-256-GCM (`WEBHOOK_SECRET_ENCRYPTION_KEY`, 12-byte nonce prefix). |
| FR-WHK-002 | For `parcel.status.changed` notification-service acts only if the event has a `merchantId` and that merchant's config exists and is enabled (otherwise no delivery row). Body (JSON UTF-8): `{"id": eventId, "type": "parcel.status.changed", "createdAt": occurredAt, "data": {"parcelId","trackingNumber","merchantReference","sequence","previousStatus","status","statusChangedAt","failureReasonCode"}}`. Test body: type `webhook.test`, data `{"message":"SwiftDrop webhook test"}`. No personal data. |
| FR-WHK-003 | Headers: `Content-Type: application/json`, `User-Agent: SwiftDrop-Webhook/1.0`, `X-SwiftDrop-Event`, `X-SwiftDrop-Delivery` (event id), `X-SwiftDrop-Attempt` (1-based), `X-SwiftDrop-Timestamp` (Unix seconds, regenerated every attempt), `X-SwiftDrop-Signature: sha256=<hex>` = HMAC-SHA256 of `<timestamp>.<raw body>` with the merchant secret. Merchants are told to verify in constant time and reject timestamps older than 5 min. |
| FR-WHK-004 | An attempt succeeds only on HTTP 2xx within 10 s (`WEBHOOK_TIMEOUT_SECONDS`). Redirects are not followed. Anything else (non-2xx, timeout, connection error, blocked target) is a failed attempt. Response bodies are not stored; only the status code and an error summary up to 200 chars. |
| FR-WHK-005 | At most 6 attempts (`WEBHOOK_MAX_ATTEMPTS`). The first attempt happens as soon as the scheduler (every 2 s) picks the row; after failed attempt n the next is after 1 min, 5 min, 30 min, 2 h, 6 h for n = 1..5 (`WEBHOOK_RETRY_DELAYS`, ISO-8601 list). After the sixth failure the delivery is FAILED. A manual retry resets attempts to 0 and status to PENDING. |
| FR-WHK-006 | Deliveries are in `webhook_deliveries` with unique `event_id` (test deliveries get a new UUID); due rows are selected with `FOR UPDATE SKIP LOCKED`. The URL and secret are read from the local config at send time; a missing or disabled config at send time counts as a failed attempt. |
| FR-WHK-007 | SSRF protection: only https URLs (http if `WEBHOOK_ALLOW_HTTP=true`); no credentials in the URL. Validation runs when the merchant saves the URL and again at every send: resolve the host name with `InetAddress.getAllByName` and reject if any resolved address is blocked. **Always blocked, regardless of any flag:** link-local 169.254.0.0/16 (includes the cloud metadata address 169.254.169.254), fe80::/10, 0.0.0.0/8, multicast. **Blocked unless `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` (local demo only):** loopback 127.0.0.0/8, ::1, private 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, unique-local fc00::/7. The JDK HttpClient cannot pin the connection to a validated IP, so the residual DNS-rebinding window between validation and connect is accepted and documented (D-14); no custom DNS resolver is built. |
| FR-WHK-008 | Signing secrets are decrypted only in memory at send time and never logged. |
| FR-WHK-009 | `GET /merchant/webhook/deliveries?status=&page=&size=` shows the log (event id, tracking number, status, attempt count, last HTTP status, last error summary, timestamps) for the caller's merchant only; `POST /merchant/webhook/deliveries/{id}/retry` re-queues a FAILED delivery (409 INVALID_STATUS_TRANSITION otherwise); `GET /merchant/webhook/summary` returns `{failed24h, pending, succeeded24h}`. |
| FR-WHK-010 (S) | Delivery rows older than 30 days and notification rows older than 90 days are deleted by a daily job. |
| FR-WHK-011 (S) | `tools/mock-merchant` shows received webhooks with headers and body and verifies the signature when the secret is entered. |

### 5.7 api-gateway (FR-GW)

| ID | Requirement |
|---|---|
| FR-GW-001 | Routes exactly as 3.7. Upstream URLs from environment variables. A route whose upstream is unreachable returns 503 SERVICE_UNAVAILABLE (Problem Details); the gateway starts even when some or all upstreams are down. Port 8080 returns 404 NOT_FOUND for `/actuator/**` (route 1). The gateway serves `/actuator/health`, `/actuator/health/liveness` and `/actuator/health/readiness` only on a separate management port (default 8090, variable `MANAGEMENT_SERVER_PORT`), which is never published, routed or proxied; the Docker healthcheck and the Kubernetes probes use that port. The gateway's readiness and liveness do not depend on any upstream service. |
| FR-GW-002 | Per-IP rate limits as 3.7, switchable with `RATE_LIMIT_ENABLED` (false disables all limits). 429 RATE_LIMITED with Retry-After. |
| FR-GW-003 | Adds the security headers of NFR-SEC-015 to every response; removes server version headers. |
| FR-GW-004 | Accepts or generates `X-Request-Id` and forwards it; returns it on every response. |
| FR-GW-005 | Request body limits as 3.7. |
| FR-GW-006 | The tracking stream route has no response timeout and no response buffering. |
| FR-GW-007 | CORS as 3.7 and NFR-SEC-020. The gateway has no database, entity or repository layer. |
| FR-GW-008 | The gateway does not depend on common-lib, which contains servlet filters, MVC exception handlers and JPA entities (Spring Cloud Gateway refuses to start with Spring MVC on the classpath). It has its own request-id WebFilter (FR-GW-004), its own reactive error handler that returns Problem Details with the codes and fields of 7.2 (404 NOT_FOUND, 413 PAYLOAD_TOO_LARGE, 429 RATE_LIMITED, 503 SERVICE_UNAVAILABLE) and Spring Boot structured logging with the fields of NFR-OBS-001. It may depend on common-core only. |

### 5.8 Frontend (FR-UI)

| ID | Requirement |
|---|---|
| FR-UI-001 | The SPA keeps the access token in memory only (never localStorage, sessionStorage or cookies), restores the session at load via `POST /auth/refresh`, sends `Authorization: Bearer`, and on 401 TOKEN_EXPIRED refreshes once and retries once, else redirects to `/login`. Only one refresh request is in flight at a time; the refresh call always sends `X-Requested-With: SwiftDrop`. |
| FR-UI-002 | The SPA guards routes by role, hides unusable navigation, and redirects unauthorised route access to the role's home page. A user with `mustChangePassword` is redirected to `/change-password`. |
| FR-UI-003 | One shared API client (`src/api`) with one function per endpoint of 7.3; one MUI theme; role navigation and shared layout; server field errors (`errors[]`) always shown without losing input; loading, empty and error states on every data view; confirmation dialog for destructive actions (cancel, deactivate, revoke, reject, fail). |
| FR-UI-004 | Client validation mirrors server limits (Zod mirrors Bean Validation). New-delivery flow is three steps (details, quote, confirm); quote calls are debounced 400 ms; the Confirm button is disabled while the request runs; one `Idempotency-Key` per confirmation screen; FEE_CHANGED shows the new fee and asks the customer to confirm again with a new key. |
| FR-UI-005 | Status is shown as a coloured chip plus the label from 6.2 (never colour alone); times in Asia/Colombo, 12-hour clock; no user text rendered as HTML (`dangerouslySetInnerHTML` forbidden). |
| FR-UI-006 | Public tracking page `/track`, `/track/:trackingNumber`: live view per 3.8 including the polling fallback; malformed and unknown numbers both show "We could not find a parcel with that tracking number"; usable at 360 px without horizontal scroll. |
| FR-UI-007 | Composition and refresh rules for pages that combine services (7.6). On every SSE status event and every polling result, the customer, merchant and admin detail pages refetch parcel detail, timeline and proof. Right after creation the timeline endpoint may return 404 for a few seconds (event not yet processed): the detail page shows the parcel data and an "updating" timeline and retries every 2 s for up to 30 s. |
| FR-UI-008 | Courier UI is mobile-first (single column, touch targets at least 44 x 44 px, camera via `<input type="file" accept="image/*" capture="environment">`, phones as `tel:` links, addresses as plain text). The courier list refreshes every 30 s and on window focus so new assignments and cancellations appear; the merchant dashboard refreshes every 30 s; the webhook failure counter shows "unavailable" if notification-service does not answer. The courier note field shows the hint "Do not enter customer or recipient contact details" (BR-28). |
| FR-UI-009 | WCAG 2.1 AA basics: labelled inputs, keyboard use, visible focus, contrast, `aria-live` for live tracking. |
| FR-UI-010 | Build-time variable `VITE_API_BASE_URL` (empty in the container build so all calls are relative; `http://localhost:8080` for `npm run dev`). |
| FR-UI-011 | Proof photos and receipts need the bearer token, so the SPA never uses `GET /proofs/{parcelId}/photo` or `GET /parcels/{id}/receipt` in `<img src>`, a plain link or `window.open`. It fetches them through the shared API client with the Authorization header and shows the photo through a blob URL (`URL.createObjectURL`, revoked when the image is no longer shown) or saves the PDF as a download from the blob. The CSP already allows `img-src blob:` (NFR-SEC-007). |

---

## 6. Data Requirements

### 6.1 Conventions

- Primary keys are UUID v4 unless stated. Timestamps are TIMESTAMPTZ (UTC). Money is NUMERIC(12,2). Strings are VARCHAR(n); enums are VARCHAR with a CHECK listing the values. Foreign keys are enforced inside a database only; across services ids are plain UUID columns without foreign keys (BR-27). Index every foreign key and the access paths listed.
- Every table has `created_at`; mutable tables also have `updated_at`; users, merchants, parcels, deliveries also have `version BIGINT` for optimistic locking. (Reference and one-to-one tables show only the timestamps listed.)
- All schema changes are Flyway migrations inside the owning service (`src/main/resources/db/migration`, files `V1__init.sql`, `V2__...`). `spring.jpa.hibernate.ddl-auto=validate`. Nobody edits another service's migrations; an applied migration is never edited, a change is a new numbered file.
- **Shared table definitions** (copied exactly into each service that needs them):
  - `outbox_events` (auth, parcel, pricing, delivery): `id` UUID PK (= eventId), `event_type` VARCHAR(40), `routing_key` VARCHAR(40), `payload` JSONB (full envelope), `correlation_id` UUID, `created_at`, `published_at` null, `attempts` INT default 0; index on `created_at` where `published_at` is null.
  - `processed_events` (delivery, tracking): `event_id` UUID PK, `processed_at`.

### 6.2 Enumerations

| Name | Values |
|---|---|
| Role | CUSTOMER, MERCHANT, COURIER, ADMIN |
| UserStatus / AuthProvider | ACTIVE, DEACTIVATED / LOCAL, GOOGLE |
| MerchantStatus | PENDING, APPROVED, REJECTED, SUSPENDED |
| ParcelStatus | CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, CANCELLED |
| ParcelSize / VehicleType | SMALL, MEDIUM, LARGE / MOTORBIKE, THREE_WHEELER, VAN |
| OwnerType | CUSTOMER, MERCHANT |
| FailureReason (label) | RECIPIENT_UNAVAILABLE ("Recipient not available"), WRONG_OR_INCOMPLETE_ADDRESS ("Wrong or incomplete address"), RECIPIENT_REFUSED ("Recipient refused the parcel"), SENDER_UNAVAILABLE_AT_PICKUP ("Sender not available at pickup"), PARCEL_DAMAGED ("Parcel damaged"), ACCESS_RESTRICTED ("Location not accessible"), OTHER ("Other reason") |
| ActorRole (history) | CUSTOMER, MERCHANT, COURIER, ADMIN, SYSTEM |
| CredentialStatus | ACTIVE, REVOKED |
| Notification channel / recipient kind / status | EMAIL, SMS / SENDER, RECIPIENT, MERCHANT / PENDING, SENT, FAILED, SKIPPED |
| WebhookDeliveryStatus | PENDING, SUCCEEDED, FAILED |
| Status labels | CREATED "Created", PICKED_UP "Picked up", IN_TRANSIT "In transit", OUT_FOR_DELIVERY "Out for delivery", DELIVERED "Delivered", FAILED "Delivery failed", CANCELLED "Cancelled" |

### 6.3 Databases and tables

#### auth-service — `swiftdrop_auth`

| Table | Columns and constraints |
|---|---|
| `users` | id; email VARCHAR(254) unique lower-case; password_hash VARCHAR(100) (null for Google-only); full_name VARCHAR(100); phone VARCHAR(16) E.164 nullable; role VARCHAR(10); status VARCHAR(12) default ACTIVE; auth_provider; google_sub VARCHAR(64) unique when not null; must_change_password BOOLEAN default false; failed_login_count INT default 0; locked_until; last_login_at; created_at, updated_at, version. |
| `refresh_tokens` | id; user_id FK (indexed); family_id UUID (indexed); token_hash CHAR(64) unique (SHA-256 hex); expires_at (created + 7 days); family_created_at (30-day cap); used_at, revoked_at null; created_at. |
| `oauth_login_states` | state_hash CHAR(64) PK; nonce VARCHAR(64); code_verifier VARCHAR(128); expires_at (5 min); created_at. |
| `courier_profiles` | user_id PK/FK users (role COURIER); vehicle_type; vehicle_number VARCHAR(20). |
| `merchants` | id; user_id FK unique; business_name VARCHAR(120); business_registration_no VARCHAR(40) null; address_line1 VARCHAR(150); city VARCHAR(80); status default PENDING; status_reason VARCHAR(300) null; discount_percent NUMERIC(4,2) default 0 check 0-50; decided_at, decided_by UUID; created_at, updated_at, version. |
| `merchant_credentials` | id; merchant_id FK (indexed); client_id VARCHAR(40) unique (`sdc_`); client_secret_hash CHAR(64); label VARCHAR(50); status; created_at, last_used_at, revoked_at. |
| `outbox_events` | shared definition. |

#### parcel-service — `swiftdrop_parcel`

| Table | Columns and constraints |
|---|---|
| `parcels` | id; tracking_number VARCHAR(13) unique; customer_id UUID and merchant_id UUID (CHECK exactly one set); merchant_reference VARCHAR(64) (partial unique index on (merchant_id, merchant_reference) where reference not null and status not in CANCELLED, FAILED); status default CREATED; status_sequence INT starts 1; pickup_contact_name VARCHAR(100), pickup_contact_phone VARCHAR(16), pickup_address_line1 150, pickup_address_line2 150 null, pickup_city 80, pickup_district_code VARCHAR(20), pickup_district_name VARCHAR(40), pickup_postal_code VARCHAR(5) null; the same dropoff_* set plus dropoff_contact_email VARCHAR(254) null; weight_kg NUMERIC(5,2); size; description VARCHAR(200) null; delivery_instructions VARCHAR(300) null; origin_zone_code, origin_zone_name, destination_zone_code, destination_zone_name (snapshot); fee_base, fee_weight, fee_size, fee_discount, fee_total NUMERIC(12,2) (snapshot); currency CHAR(3) LKR; terminal_at null; created_at, updated_at, version. Indexes: unique tracking_number; (customer_id, created_at desc); (merchant_id, created_at desc); (status, created_at); (terminal_at). |
| `idempotency_keys` | id; owner_type; owner_id UUID; idempotency_key VARCHAR(64) (unique with owner_type, owner_id); request_hash CHAR(64); response_status INT; response_body JSONB; parcel_id; created_at (purged after 24 h). |
| `outbox_events` | shared definition. |

#### pricing-service — `swiftdrop_pricing`

| Table | Columns and constraints |
|---|---|
| `zones` | id; code VARCHAR(20) unique immutable; name VARCHAR(60) unique; active default true; created_at, updated_at. |
| `districts` | code VARCHAR(20) PK (for example KANDY); name VARCHAR(40); zone_id FK zones. |
| `zone_pricing` | PK (origin_zone_id, destination_zone_id) FK zones; base_fee and per_kg_fee NUMERIC(12,2) >= 0; updated_at. |
| `size_surcharges` | size VARCHAR(10) PK; surcharge NUMERIC(12,2) >= 0; description VARCHAR(80) (max dimensions). |
| `outbox_events` | shared definition. |

#### delivery-service — `swiftdrop_delivery`

| Table | Columns and constraints |
|---|---|
| `deliveries` | parcel_id UUID PK; tracking_number VARCHAR(13) unique; status; status_sequence INT; owner_type; customer_id UUID null; merchant_id UUID null; origin_district_code, origin_district_name, destination_district_code, destination_district_name; pickup_city, dropoff_city; courier_id UUID null; assigned_at null; parcel_created_at; terminal_at null; created_at, updated_at, version. Indexes: (courier_id, status); (status, parcel_created_at) where courier_id is null; (terminal_at). |
| `couriers` | id UUID PK (= user id); full_name; vehicle_type; vehicle_number; status (ACTIVE, DEACTIVATED); updated_at. |
| `delivery_assignments` | id; parcel_id (indexed); courier_id (indexed); assigned_by UUID; assigned_at; unassigned_at null (set on reassignment or courier deactivation). |
| `delivery_proofs` | parcel_id PK; recipient_name VARCHAR(100); note VARCHAR(500) null; has_photo BOOLEAN; captured_at. |
| `proof_photos` | parcel_id PK; content_type VARCHAR(20) (always image/jpeg); data BYTEA (at most 5 MB after re-encoding, loaded only by the photo endpoint). |
| `processed_events`, `outbox_events` | shared definitions. |

#### tracking-service — `swiftdrop_tracking`

| Table | Columns and constraints |
|---|---|
| `tracking_parcels` | parcel_id UUID PK; tracking_number VARCHAR(13) unique; status; status_sequence INT; owner_type; customer_id null; merchant_id null; origin_district_name, origin_zone_name, destination_district_name, destination_zone_name; courier_id UUID null; courier_first_name VARCHAR(50) null; created_at, updated_at. |
| `tracking_events` | id BIGSERIAL PK; parcel_id (unique with sequence); sequence INT (1 = creation); from_status null; to_status; actor_user_id UUID null; actor_role VARCHAR(10); failure_reason_code VARCHAR(40) null; note VARCHAR(500) null; occurred_at; created_at. Append-only (FR-TRK-006, 007). |
| `audit_logs` | id BIGSERIAL PK; event_id UUID unique; service VARCHAR(30); actor_user_id; actor_role; action VARCHAR(60) (for example MERCHANT_APPROVED); entity_type VARCHAR(30); entity_id VARCHAR(40); details JSONB; occurred_at; created_at. Kept 1 year. |
| `processed_events` | shared definition. |

#### notification-service — `swiftdrop_notification`

| Table | Columns and constraints |
|---|---|
| `notifications` | id; event_id UUID (unique with channel and recipient_kind); event_type VARCHAR(40); tracking_number VARCHAR(13) null; channel, recipient_kind, status; template_code VARCHAR(60); destination_masked VARCHAR(100); body_preview VARCHAR(300) (SMS only, no personal data); attempts INT; error_summary VARCHAR(200) (no personal data); created_at, sent_at. Purged after 90 days (S). |
| `merchant_webhook_config` | merchant_id UUID PK; url VARCHAR(500); secret_encrypted BYTEA (AES-256-GCM, 12-byte nonce prefix); enabled default true; created_at, updated_at. |
| `webhook_deliveries` | id; event_id UUID unique; merchant_id UUID (indexed); event_type VARCHAR(40); tracking_number VARCHAR(13) null (tests); payload JSONB (exact body, no personal data); target_url VARCHAR(500) (latest attempt); status; attempt_count INT default 0; next_attempt_at (index with status for the scheduler); last_http_status INT; last_error VARCHAR(200); created_at, last_attempt_at, completed_at. Purged after 30 days (S). |

### 6.4 Relationships (for the ER diagram, one diagram per database plus a context diagram of cross-service id references)

- users 1-N refresh_tokens; users 1-0..1 courier_profiles; users 1-0..1 merchants; merchants 1-N merchant_credentials.
- zones 1-N districts; zones N-N zones via zone_pricing (origin, destination).
- deliveries 1-N delivery_assignments; deliveries 1-0..1 delivery_proofs; delivery_proofs 1-0..1 proof_photos; couriers 1-N deliveries.
- tracking_parcels 1-N tracking_events.
- Cross-service references by id only: `parcels.customer_id` to `users.id`; `parcels.merchant_id` to `merchants.id`; `deliveries.parcel_id` to `parcels.id`; `deliveries.courier_id` and `couriers.id` to `users.id`; `tracking_parcels.parcel_id` to `parcels.id`; `merchant_webhook_config.merchant_id` to `merchants.id`.

### 6.5 Retention and privacy

Parcels, deliveries, proofs and tracking events are kept while the platform operates. Purge: published outbox rows 1 day (S), idempotency keys 24 h, refresh tokens 7 days after expiry, OAuth states 5 min, notifications 90 days (S), webhook deliveries 30 days (S), audit logs 1 year, DLQ messages 3 days; application logs go to stdout.

Customer, merchant and recipient personal data (names, emails, phones, addresses, proofs, photos) is stored only in auth-service (accounts), parcel-service (contacts and addresses) and delivery-service (proofs). Staff data is limited to the courier's name (full name in auth-service and the delivery-service courier directory, first name only in tracking-service) and the optional free-text staff note (tracking-service timeline). Events, logs, audit details, notification rows and webhook payloads contain no customer, merchant or recipient personal data, and none of them contains staff notes except the `parcel.status.changed` event, its outbox row and its DLQ copy (BR-28, BR-20, NFR-SEC-011). Processing follows the PDPA 2022 principles (purpose limitation, minimisation, accuracy, security, storage limitation).

---

## 7. Interfaces

### 7.1 User interface routes

| Route | Role | Purpose |
|---|---|---|
| `/` | Public | Landing page with tracking box, login and register links. |
| `/track`, `/track/:trackingNumber` | Public | Tracking entry and live view. |
| `/login`, `/register`, `/register-merchant`, `/auth/callback` | Public | Auth screens; Google button when enabled. |
| `/change-password`, `/profile` | Any | Forced password change; profile and password. |
| `/customer/dashboard`, `/customer/parcels/new`, `/customer/parcels`, `/customer/parcels/:id` | Customer | Dashboard; three-step new delivery; history; detail with live timeline, receipt, cancel, proof. |
| `/merchant/dashboard`, `/merchant/orders`, `/merchant/orders/:id`, `/merchant/credentials`, `/merchant/webhook` | Merchant | Order overview and filters, detail, API credentials, webhook settings and delivery log. |
| `/courier/deliveries`, `/courier/deliveries/:id` | Courier | Today's list; status update form with proof and failure reason. |
| `/admin/dashboard`, `/admin/parcels/unassigned`, `/admin/parcels`, `/admin/parcels/:id`, `/admin/couriers`, `/admin/users`, `/admin/merchants`, `/admin/zones`, `/admin/pricing`, `/admin/reports`, `/admin/audit-logs` | Admin | Dispatching, monitoring, administration. |
| AI chat widget (all pages) | All | Floating assistant (Section 11, bonus). |

### 7.2 API conventions and error codes

| Topic | Rule |
|---|---|
| Base and versioning | `/api/v1`; breaking changes need `/api/v2`. |
| Content types | `application/json; charset=utf-8`, except token endpoint (form-urlencoded), courier status update (multipart), receipt (`application/pdf`), photo (`image/jpeg`), SSE (`text/event-stream`). |
| Authentication | `Authorization: Bearer <JWT>`; public endpoints none. `/internal/**` needs `X-Internal-Key` = `INTERNAL_API_KEY` (constant-time compare) and is never routed externally. |
| Request id | `X-Request-Id` accepted or generated by the gateway and returned on every response. |
| Validation | Unknown JSON properties are rejected (400). |
| Errors | RFC 9457 `application/problem+json`: `{type:"https://swiftdrop.example/errors/<code>", title, status, detail, code, traceId, timestamp, errors:[{field, message}]}`. `traceId` = the request id. `detail` is safe and readable (no stack traces, SQL, class names, paths, secrets, personal data). The token endpoint uses the RFC 6749 error format. |
| Pagination | `page`, `size`, `sort=field,dir` (whitelisted fields); response `{items, page, size, totalItems, totalPages}`. |
| Dates and money | `from`/`to` are `yyyy-MM-dd` in Asia/Colombo, inclusive; response timestamps ISO 8601 UTC; money with two decimals and currency LKR. |
| Rate limit, cache | Gateway limits (3.7) return 429 RATE_LIMITED with `Retry-After`. Authenticated and tracking responses carry `Cache-Control: no-store`. |
| Success codes | 200 reads/updates, 201 creation, 204 actions without body. |
| Internal errors | Internal endpoints use the same Problem Details format; a caller that receives a business error code (for example 409 INVALID_STATUS_TRANSITION, 422 ZONE_NOT_SERVICEABLE) returns it unchanged to its own caller; connection failures, timeouts and 5xx become 503 SERVICE_UNAVAILABLE. |

| Error code | HTTP | Meaning |
|---|---|---|
| VALIDATION_ERROR | 400 | Invalid or missing input, unknown field, bad enum, district, size, weight or failure reason. |
| UNAUTHENTICATED, TOKEN_EXPIRED, INVALID_CREDENTIALS | 401 | Missing/invalid/expired token; login failed. |
| ACCESS_DENIED | 403 | Role (or token type, or scope) not allowed. |
| PASSWORD_CHANGE_REQUIRED, ACCOUNT_DEACTIVATED | 403 | Forced change pending; account deactivated. |
| MERCHANT_PENDING, MERCHANT_REJECTED, MERCHANT_SUSPENDED, MERCHANT_NOT_ACTIVE | 403 | Merchant cannot log in; merchant order call while not approved. |
| NOT_FOUND | 404 | Missing or owned by someone else. |
| EMAIL_ALREADY_REGISTERED | 409 | Email in use. |
| FEE_CHANGED | 409 | Fee differs from expectedFee; body carries the new fee. |
| INVALID_STATUS_TRANSITION | 409 | Transition not allowed for state or actor. |
| PARCEL_NOT_ASSIGNABLE, DUPLICATE_MERCHANT_REFERENCE, CONCURRENT_UPDATE | 409 | Assignment rules; BR-22; optimistic lock. |
| COURIER_HAS_ACTIVE_DELIVERIES, CREDENTIAL_LIMIT_REACHED, ZONE_IN_USE | 409 | Cannot deactivate courier; more than 2 active credentials; zone still has districts. |
| PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE | 413, 415 | Upload problems. |
| IDEMPOTENCY_KEY_REUSED, PRICING_NOT_CONFIGURED, ZONE_NOT_SERVICEABLE | 422 | Same key different body; no price; inactive zone. |
| ACCOUNT_LOCKED, RATE_LIMITED | 429 | Lockout or rate limit; Retry-After set. |
| INTERNAL_ERROR | 500 | Unexpected; only traceId shown. |
| SERVICE_UNAVAILABLE, ASSISTANT_UNAVAILABLE | 503 | Dependency unavailable. |

### 7.3 Endpoint catalogue (all under `/api/v1`; each service exposes exactly its own list)

Access: "Dash." = dashboard login only (`typ=user`); "Client" = client-credentials token with the stated scope is also accepted. Every endpoint without a "Client" mark returns 403 ACCESS_DENIED for a client token (3.6).

#### auth-service

| Method and path | Access | Purpose |
|---|---|---|
| `POST /auth/register`, `POST /auth/register-merchant` | Public | Registration. |
| `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` | Public / cookie | Login, rotate, logout. |
| `GET /auth/google/login`, `GET /auth/google/callback`, `GET /auth/config` | Public | Google flow; `{googleEnabled}`. |
| `POST /oauth/token` | Public (client auth) | Client-credentials token. |
| `GET /me`, `PUT /me`, `POST /me/change-password` | Authenticated | Profile. |
| `GET /merchant/credentials`, `POST /merchant/credentials`, `DELETE /merchant/credentials/{id}` | Merchant, Dash. | API credentials. |
| `GET/POST /admin/users`, `GET/PATCH /admin/users/{id}`, `POST /admin/users/{id}/deactivate`, `/activate`, `/reset-password` | Admin | Users. |
| `GET /admin/merchants`, `GET /admin/merchants/{id}`, `POST .../approve`, `/reject`, `/suspend`, `/reinstate`, `PATCH .../discount` | Admin | Merchants. |
| `GET /admin/dashboard/merchants` | Admin | Pending merchant count. |

#### pricing-service

| Method and path | Access | Purpose |
|---|---|---|
| `GET /public/districts`, `POST /public/quotes` | Public | Districts and sizes; fee quote. |
| `GET/POST /admin/zones`, `PATCH /admin/zones/{id}`, `PUT /admin/districts/{code}/zone` | Admin | Zones. |
| `GET /admin/pricing`, `PUT /admin/pricing/{originZoneId}/{destinationZoneId}`, `GET/PUT /admin/size-surcharges` | Admin | Pricing. |

#### parcel-service

| Method and path | Access | Purpose |
|---|---|---|
| `POST /customer/parcels`, `GET /customer/parcels`, `GET /customer/parcels/{id}`, `POST /customer/parcels/{id}/cancel`, `GET /customer/dashboard/summary` (S) | Customer (own) | Create (Idempotency-Key), history, detail, cancel, dashboard. |
| `GET /parcels/{id}/receipt` | Owner customer, owner merchant, admin | PDF receipt. |
| `POST /merchant/orders`, `GET /merchant/orders`, `GET /merchant/orders/{id}`, `POST /merchant/orders/{id}/cancel` | Merchant: Client `orders:write` / `orders:read` or Dash. | Order API. |
| `GET /merchant/dashboard/summary` | Merchant, Dash. | Dashboard. |
| `GET /admin/parcels`, `GET /admin/parcels/{id}`, `POST /admin/parcels/{id}/cancel`, `POST /admin/parcels/{id}/fail` | Admin | Search, detail, cancel, force-fail. |
| `GET /admin/reports/summary`, `GET /admin/reports/daily`, `GET /admin/dashboard/parcels` | Admin | Reports, counters. |

#### delivery-service

| Method and path | Access | Purpose |
|---|---|---|
| `GET /courier/deliveries?scope=`, `GET /courier/deliveries/{parcelId}`, `POST /courier/deliveries/{parcelId}/status` | Courier (assigned) | Own deliveries; multipart status update. |
| `GET /admin/deliveries`, `GET /admin/deliveries/{parcelId}`, `POST /admin/deliveries/{parcelId}/assign` | Admin | Queue, delivery info, dispatch. |
| `GET /admin/couriers`, `GET /admin/couriers/{id}/deliveries`, `GET /admin/dashboard/dispatch` | Admin | Workloads, counters. |
| `GET /proofs/{parcelId}`, `GET /proofs/{parcelId}/photo` | Owner, assigned courier, admin | Proof of delivery. |

#### tracking-service

| Method and path | Access | Purpose |
|---|---|---|
| `GET /public/tracking/{trackingNumber}`, `GET .../stream` | Public | Public view; SSE. |
| `GET /tracking/parcels/{parcelId}/timeline` | Customer, merchant, courier (own/assigned), admin | Full timeline. |
| `GET /admin/audit-logs` | Admin | Audit log. |

#### notification-service

| Method and path | Access | Purpose |
|---|---|---|
| `GET/PUT /merchant/webhook`, `POST /merchant/webhook/rotate-secret`, `POST /merchant/webhook/test` | Merchant, Dash. | Webhook settings. |
| `GET /merchant/webhook/deliveries`, `POST /merchant/webhook/deliveries/{id}/retry`, `GET /merchant/webhook/summary` | Merchant, Dash. | Log, retry, counters. |

### 7.4 Internal endpoints (header `X-Internal-Key`; never routed through the gateway)

| Service | Endpoint | Returns / does |
|---|---|---|
| auth-service | `GET /internal/v1/users/{id}/contact` | `{id, fullName, email, phone}` |
| auth-service | `GET /internal/v1/merchants/{merchantId}` | `{id, userId, businessName, status, userStatus, discountPercent, contactEmail}` |
| pricing-service | `POST /internal/v1/pricing/calculate` | FR-PRC-003 |
| parcel-service | `GET /internal/v1/parcels/{id}` | Full parcel detail (same fields as the admin detail, plus `statusSequence`) |
| parcel-service | `GET /internal/v1/parcels/{id}/recipient` | `{name, phone, email}` of the drop-off contact |
| parcel-service | `POST /internal/v1/parcels/{id}/transition` | FR-PAR-016 |
| delivery-service | `GET /internal/v1/couriers/{id}/active-count` | `{inProgress}` |
| notification-service | `GET /internal/v1/notifications` (S) | Recent notifications |
| every Java service | `/actuator/health`, `/actuator/health/readiness`, `/actuator/health/liveness`, `/actuator/prometheus` (S) | Health and metrics; reachable only inside the Docker network or on the local host port. On api-gateway they are served only on the management port 8090 (port 8080 returns 404) |

### 7.5 Key payloads

**Quote:** `POST /public/quotes`

```json
{"originDistrictCode":"COLOMBO","destinationDistrictCode":"KANDY","weightKg":2.0,"size":"MEDIUM"}
```

returns

```json
{"currency":"LKR","originZone":{"code":"WESTERN","name":"Western"},"destinationZone":{"code":"CENTRAL","name":"Central"},"baseFee":500.00,"weightFee":150.00,"sizeSurcharge":150.00,"discountAmount":0.00,"total":800.00}
```

**Create delivery:** `POST /customer/parcels` with `Idempotency-Key: <uuid>` and

```json
{"pickup":{"contactName":"Nimal Perera","contactPhone":"0771234567","addressLine1":"12 Galle Road","city":"Colombo 03","districtCode":"COLOMBO","postalCode":"00300"},"dropoff":{"contactName":"Kamala Silva","contactPhone":"0712345678","contactEmail":"kamala@example.com","addressLine1":"45 Peradeniya Road","city":"Kandy","districtCode":"KANDY"},"parcel":{"weightKg":2.0,"size":"MEDIUM","description":"Books"},"deliveryInstructions":"Call on arrival","expectedFee":800.00}
```

returns 201 with `Location` and `{"id","trackingNumber":"SD-7K3M9QX2TA","status":"CREATED","fee":{...},"createdAt"}`.

**Merchant token and order:** `POST /oauth/token` body

```
grant_type=client_credentials&client_id=sdc_...&client_secret=sds_...&scope=orders:write orders:read
```

returns `{"access_token","token_type":"Bearer","expires_in":900,"scope":"orders:read orders:write"}`; then `POST /merchant/orders` with the bearer token, optional `Idempotency-Key`:

```json
{"merchantReference":"ORD1001","pickup":{...},"dropoff":{...},"parcel":{"weightKg":1.5,"size":"SMALL"}}
```

**Courier update (multipart):** `status=DELIVERED, recipientName=Kamala Silva, note=Handed over at the gate, photo=<file.jpg>` or `status=FAILED, failureReason=RECIPIENT_UNAVAILABLE, note=Called twice`.

**Webhook received by the merchant:** headers `X-SwiftDrop-Event`, `X-SwiftDrop-Delivery`, `X-SwiftDrop-Attempt`, `X-SwiftDrop-Timestamp`, `X-SwiftDrop-Signature: sha256=<hex>`; body

```json
{"id":"<eventId>","type":"parcel.status.changed","createdAt":"2026-10-20T08:15:30.123Z","data":{"parcelId":"...","trackingNumber":"SD-7K3M9QX2TA","merchantReference":"ORD1001","sequence":3,"previousStatus":"PICKED_UP","status":"IN_TRANSIT","statusChangedAt":"...","failureReasonCode":null}}
```

**Error:** 409

```json
{"type":".../errors/INVALID_STATUS_TRANSITION","title":"Invalid status transition","status":409,"detail":"This parcel cannot move from CREATED to DELIVERED.","code":"INVALID_STATUS_TRANSITION","traceId":"<uuid>","timestamp":"2026-10-20T08:15:31Z"}
```

**Parcel detail** (parcel-service, customer and admin): `id`, `trackingNumber`, `status`, `createdAt`, `updatedAt`, `pickup` and `dropoff` (contact, address, districtName), `parcel {weightKg, size, description}`, `deliveryInstructions`, `fee {currency, baseFee, weightFee, sizeSurcharge, discountAmount, total}`, `originZone`, `destinationZone`, `merchantReference`, `ownerType`; admins also get `customerId`, `merchantId`. The courier view (delivery-service) is the same without `fee` and with `allowedNextStatuses`.

### 7.6 Pages that combine several services (the SPA composes; no service aggregates)

| Page | Calls |
|---|---|
| Customer parcel detail | parcel-service detail; tracking-service timeline; delivery-service proof (only when DELIVERED). |
| Merchant order detail | `GET /merchant/orders/{id}`; timeline; proof (only when DELIVERED). |
| Merchant dashboard | parcel-service summary; notification-service `GET /merchant/webhook/summary` (failure shown as "unavailable"). |
| Admin parcel detail | parcel-service admin detail; tracking timeline; delivery-service `GET /admin/deliveries/{parcelId}`; proof. |
| Admin unassigned queue and workloads | delivery-service only; the assign dialog lists couriers from `GET /admin/couriers`. |
| Admin dashboard | `GET /admin/dashboard/parcels`, `/dispatch`, `/merchants` (three independent cards). |
| Courier delivery detail | delivery-service detail; tracking timeline. |

---

## 8. Non-Functional Requirements

All items are measured on the reference machine (1.5) with the Compose stack running. Performance items are indicative and demonstrated with a simple script run against the services (not gated by the build); the gateway rate limit is switched off for such runs (`RATE_LIMIT_ENABLED=false`).

### 8.1 Performance, reliability, usability, maintainability, observability

| ID | Requirement |
|---|---|
| NFR-PERF-001 (S) | With 50 concurrent users, p95 of read APIs at most 500 ms and of write APIs at most 800 ms, excluding PDF and photo upload. |
| NFR-PERF-002 (S) | Receipt PDF p95 at most 2 s; 10 MB photo upload including re-encoding p95 at most 5 s. |
| NFR-PERF-003 | Commit of a status change to display on an open tracking page: p95 at most 3 s. |
| NFR-PERF-004 | Event publication to notification record p95 at most 30 s; first webhook attempt p95 at most 10 s. |
| NFR-PERF-005 (S) | Lists over 100,000 parcels within 500 ms (indexes of 6.3, pagination mandatory, no unbounded queries). |
| NFR-REL-001 | Failure of broker, notification-service, tracking-service or AI service never makes parcel creation or status updates fail (events wait in the outbox); events deliver after recovery. |
| NFR-REL-002 | No event loss: durable queues, persistent messages, publisher confirms, outbox, manual acks. |
| NFR-REL-003 | Services start in any order: retry database and broker connections with back-off (at least 60 s) and report ready only when the database is connected (the broker is not part of readiness; events wait in the outbox). Synchronous dependencies (3.3) are needed only at request time. |
| NFR-REL-004 | Graceful shutdown on SIGTERM: stop accepting work, finish in-flight requests and messages, exit within 30 s. |
| NFR-REL-005 | PostgreSQL and RabbitMQ data survives container restarts (volumes or PVCs); README documents backup and restore with `pg_dump`/`psql` per database. |
| NFR-REL-006 | All outbound calls have explicit timeouts (connect 5 s, read 10 s; LLM 30 s). |
| NFR-USA-001 | Responsive 360-1920 px on supported browsers; no horizontal scroll at 360 px on tracking and courier screens. User-facing errors are specific, polite, actionable, never technical. |
| NFR-MNT-001 | Layering rules of 3.2 respected; no business logic or repository access in controllers; no service uses another service's code except common-core and common-lib (api-gateway: common-core only). |
| NFR-MNT-002 | Domain exception hierarchy (`DomainException` with `ErrorCode`) mapped to Problem Details in the one handler of common-lib (servlet services) and in the gateway's own reactive error handler (FR-GW-008); no empty catch blocks, no swallowed exceptions without logging. |
| NFR-MNT-003 | No duplicated logic; shared code lives in common-core and common-lib (frontend: shared API client). |
| NFR-MNT-004 | Configuration only via environment variables (9.3) bound to typed config classes; no hard-coded URLs, credentials, ports or secrets. |
| NFR-MNT-005 (S) | Code style enforced by Spotless or Checkstyle (Java), ESLint + Prettier (frontend), Ruff (Python). |
| NFR-MNT-006 (S) | Javadoc on public classes and non-trivial methods; OpenAPI summaries and error responses. |
| NFR-MNT-007 | Feature branches, pull requests with one approving review, Conventional Commits; each member's commits traceable for the contribution sheet. |
| NFR-PRT-001 | Runs on Windows, macOS, Linux with only Docker (and Git) installed; shell scripts use LF line endings (`.gitattributes`); no host-specific paths. |
| NFR-OBS-001 | Structured JSON logs on stdout (3.4) with level from `LOG_LEVEL`. |
| NFR-OBS-002 | Every request has `X-Request-Id`, propagated to outbox events, consumers and internal calls. |
| NFR-OBS-003 | Spring services expose `/actuator/health` with readiness and liveness groups (Must): readiness covers the database only (not the broker and not other services, so a RabbitMQ outage never makes a pod unready or cuts traffic; NFR-REL-001, AT-24) and liveness has no dependencies; the api-gateway has no database, so its readiness has no dependency either; `/actuator/prometheus` metrics incl. outbox backlog (S). |

### 8.2 Security

| ID | Requirement |
|---|---|
| NFR-SEC-001 | Any deployment other than the local demo serves only HTTPS (TLS at the Ingress or in front of the gateway), sets `COOKIE_SECURE=true` and sends HSTS. Local demo over `http://localhost` is the only exception. |
| NFR-SEC-002 | Passwords, client secrets, refresh tokens and webhook secrets are handled per FR-AUTH-002, 007, 018 and FR-WHK-001. |
| NFR-SEC-003 | JWT RS256 with a 2048-bit RSA key pair; only auth-service holds the private key; every service validates `iss`, `aud`, `exp`; lifetime 15 min. |
| NFR-SEC-004 | Cookies HttpOnly, SameSite=Strict (refresh) and Lax (OAuth state), path-restricted, Secure outside the local demo. |
| NFR-SEC-005 | Server-side Bean Validation on every input with explicit length, format, range; DTOs separate from entities; unknown JSON properties rejected; max body 1 MB (11 MB for the courier status multipart; photo upload limit 10 MB, stored image limit 5 MB). |
| NFR-SEC-006 | SQL injection: parameterised queries only (JPA, Specifications); string-concatenated SQL forbidden. |
| NFR-SEC-007 | XSS: React output encoding only; CSP `default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`; emails and PDFs escape user text. |
| NFR-SEC-008 | CSRF: API auth uses a bearer header; cookie endpoints (`/auth/refresh`, `/auth/logout`) need SameSite=Strict and `X-Requested-With: SwiftDrop`. |
| NFR-SEC-009 | Deny by default (every endpoint not listed public needs a valid token); role checks by method security (`@PreAuthorize`); ownership in `ParcelAccessPolicy` and `OwnershipPolicy`; IDOR protection by 404; client-credentials tokens only on `/merchant/orders` (3.6); automated authorisation tests per service. |
| NFR-SEC-010 | No secrets in source, images, logs or repository; environment variables (`.env`, not committed) or Kubernetes Secrets; `.env.example` has placeholders only; a service fails fast at startup on a missing or weak mandatory secret (missing key file, `INTERNAL_API_KEY` under 32 chars, placeholder values). Key files in `infrastructure/keys/` are git-ignored. |
| NFR-SEC-011 | Logs and errors never contain passwords, tokens, secrets, full emails, full phones, street addresses or request bodies containing them; `MaskingUtil` is used; error responses have no stack trace, SQL, class names or internal paths; unexpected exceptions give 500 INTERNAL_ERROR with traceId only; public tracking and the AI assistant follow BR-20. |
| NFR-SEC-012 | Gateway rate limits per client IP (3.7); lockout per FR-AUTH-005. |
| NFR-SEC-013 | Webhook SSRF protection per FR-WHK-007 (metadata and link-local addresses are blocked in every deployment). |
| NFR-SEC-014 | Upload safety per FR-DEL-006; uploads never executed or served with a client-chosen type; `X-Content-Type-Options: nosniff`. |
| NFR-SEC-015 | Gateway headers on every response: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: camera=(self), geolocation=()`, the CSP above, HSTS when `HSTS_ENABLED=true`; server version hidden. |
| NFR-SEC-016 | Webhook authenticity: HMAC-SHA256 with timestamp (FR-WHK-003). |
| NFR-SEC-017 | Internal endpoints need `X-Internal-Key`, are never routed through the gateway or Ingress, constant-time comparison. |
| NFR-SEC-018 | Containers run non-root, contain no build tools or secrets, use minimal images, and (Kubernetes) drop all capabilities. |
| NFR-SEC-019 | Privileged actions are audited (FR-AUTH-023, FR-PRC-008, FR-DEL-002, FR-PAR-011, FR-PAR-012). |
| NFR-SEC-020 | CORS allows only `PUBLIC_BASE_URL` (and `http://localhost:5173` in the dev profile); no wildcards. |
| NFR-SEC-021 | Each service connects with its own database user owning only its database; no application user is superuser; the PostgreSQL superuser password is used only by the init script. |
| NFR-SEC-022 | Dedicated non-default broker credentials; management UI published only on 127.0.0.1 in Compose, never through Ingress. |
| NFR-SEC-023 | Accepted risks (documented): deactivated users and suspended merchants keep a valid access token for up to 15 min (BR-23); webhook DNS-rebinding window (FR-WHK-007); Spring Boot 3.5 and Spring Cloud 2025.0 are past free support (D-30). |

---

## 9. Deployment and Environment

### 9.1 Docker and Compose

| ID | Requirement |
|---|---|
| DEP-001 | Every deployable (frontend, api-gateway, six backend services, ai-assistant, mock-merchant) has its own multi-stage `Dockerfile` and `.dockerignore`. Java Dockerfiles live in `backend/<service>/` and build with context `backend/` (`mvn -pl <service> -am package -DskipTests`, Spring Boot layered jar). Final images run non-root, contain only runtime artefacts, set `TZ=UTC` (Java `-Duser.timezone=UTC`), expose one port, define a HEALTHCHECK using `wget` (Alpine images) or a Python one-liner (python images); `curl` is never assumed. |
| DEP-002 | Java images use `eclipse-temurin:21-jre-alpine`; the frontend builds with `node:22-alpine` and serves static files with `nginxinc/nginx-unprivileged:stable-alpine` (non-root, port 8080, history fallback); the AI image uses `python:3.12-slim`. |
| DEP-003 | Images are tagged `swiftdrop/<service>:1.0.0` and `latest` locally; no secrets in any layer. |
| DEP-004 | After creating `.env` from `.env.example` and generating the JWT key files (README), `docker compose up -d --build` starts the whole system and it is usable at `http://localhost:8080`. The first build downloads dependencies and may take up to 15 minutes; later starts with built images take at most 3 minutes. |
| DEP-005 | Every service has a healthcheck; `depends_on` uses `condition: service_healthy` (databases and broker first, then backend services, then frontend and gateway); the gateway never depends on ai-assistant; all use `restart: unless-stopped` and network `swiftdrop-net`. Backend services do not depend on each other at startup. Healthchecks call the readiness endpoint (database only, NFR-OBS-003); the gateway's healthcheck calls its management port 8090. |
| DEP-006 | Named volumes `pgdata`, `rabbitmqdata` keep data across `docker compose down`; `down -v` resets everything. |
| DEP-007 | Only the gateway (8080) is published for users. Other published ports bind to 127.0.0.1 only: PostgreSQL 5433 (to 5432), service ports 8081-8086 (the gateway management port 8090 is never published), RabbitMQ UI 15672, Mailpit UI 8025, mock-merchant 9090 (to 9000), Vite dev 5173 (when run on the host). |
| DEP-008 | `infrastructure/docker/postgres/init-databases.sh` (mounted at `/docker-entrypoint-initdb.d/`) creates the six databases and users from the `*_DB_PASSWORD` variables, each user owning only its database. |
| DEP-009 | Flyway applies each service's migrations at start (pricing V2 seeds Appendix B). |
| DEP-010 | First admin bootstrapped from the environment (FR-AUTH-015). |
| DEP-011 | Startup validation fails with a clear message if a mandatory variable or key file is missing or invalid. |
| DEP-012 | README contains: prerequisites; JWT key generation (commands below); Google OAuth setup; `.env` creation and secret generation; run, stop, reset; URLs (app, Swagger per service, RabbitMQ, Mailpit, mock-merchant); demo accounts; the demo script of Appendix A; running all tests and finding coverage reports; Kubernetes steps (if done); backup and restore; troubleshooting; architecture overview. |
| DEP-013 | Key generation (no host OpenSSL needed): `docker run --rm alpine/openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 > infrastructure/keys/jwt-private.pem`, then `docker run --rm -i alpine/openssl rsa -pubout < infrastructure/keys/jwt-private.pem > infrastructure/keys/jwt-public.pem`. Compose mounts the private key only into auth-service and the public key into all other Java services at `/run/keys/` (read-only). |
| DEP-014 | `.gitattributes` forces LF for `*.sh`; `.gitignore` excludes `.env`, `infrastructure/keys/*.pem`, build outputs. |
| DEP-015 | `tools/postman/` holds a Postman collection and a curl script for the merchant side of the demo (get token, create order, replay with the same Idempotency-Key, cancel), because the web UI has no create-order screen. |
| DEP-016 (S) | With `DEMO_SEED_ENABLED=true` the demo accounts of FR-AUTH-025 exist and the demo webhook receiver (mock-merchant) runs. |

**Compose start order (healthy):** postgres and rabbitmq; mailpit; then auth, parcel, pricing, delivery, tracking, notification services; then frontend; then api-gateway. Images: `postgres:16-alpine`, `rabbitmq:4-management-alpine`, `axllent/mailpit`.

### 9.2 Environment variables

All configuration comes from environment variables; secrets are never committed. Compose passes each service only what it needs. Hostnames are identical in Compose and Kubernetes: `postgres`, `rabbitmq`, `mailpit`, `frontend`, `api-gateway`, `auth-service`, `parcel-service`, `pricing-service`, `delivery-service`, `tracking-service`, `notification-service`, `ai-assistant`, `mock-merchant`. Names in the root `.env` that differ from the service variable are mapped in `compose.yaml` (for example `DB_PASSWORD: ${AUTH_DB_PASSWORD}`).

| Service | Variables | Default or example | Secret |
|---|---|---|---|
| All Java services | `SPRING_PROFILES_ACTIVE`, `LOG_LEVEL`, `INTERNAL_API_KEY`, `SWAGGER_ENABLED` | prod (dev locally), INFO, random 32+ chars, true/false | key |
| All Java services except gateway | `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://postgres:5432/swiftdrop_<name>`, `<name>_user` (name = auth, parcel, pricing, delivery, tracking, notification) | password |
| All Java services except gateway | `JWT_PUBLIC_KEY_PATH` | `/run/keys/jwt-public.pem` | no |
| Services that use the broker (all except gateway) | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `OUTBOX_POLL_INTERVAL_MS` (publishers) | rabbitmq, 5672, swiftdrop_user, -, 1000 | password |
| auth-service | `JWT_PRIVATE_KEY_PATH`, `JWT_ACCESS_TTL_MINUTES`, `REFRESH_TTL_DAYS`, `REFRESH_ABSOLUTE_TTL_DAYS`, `REFRESH_GRACE_SECONDS`, `COOKIE_SECURE`, `PUBLIC_BASE_URL`, `DELIVERY_SERVICE_URL` | `/run/keys/jwt-private.pem`, 15, 7, 30, 10, false local / true HTTPS, `http://localhost:8080`, `http://delivery-service:8084` | no |
| auth-service | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | redirect = `${PUBLIC_BASE_URL}/api/v1/auth/google/callback` | secret |
| auth-service | `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NAME`, `BOOTSTRAP_ADMIN_PASSWORD`, `DEMO_SEED_ENABLED`, `DEMO_USER_PASSWORD` | examples in `.env.example`; false | password |
| parcel-service | `PRICING_SERVICE_URL`, `AUTH_SERVICE_URL`, `PUBLIC_BASE_URL` | `http://pricing-service:8083`, `http://auth-service:8081`, `http://localhost:8080` | no |
| pricing-service | `MAX_PARCEL_WEIGHT_KG` | 30 | no |
| delivery-service | `PARCEL_SERVICE_URL`, `MAX_UPLOAD_MB`, `MAX_STORED_PHOTO_MB` | `http://parcel-service:8082`, 10, 5 | no |
| notification-service | `AUTH_SERVICE_URL`, `PARCEL_SERVICE_URL`, `PUBLIC_BASE_URL`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMS_PROVIDER` | mailpit, 1025, empty, empty, `noreply@swiftdrop.example`, mock | password |
| notification-service | `WEBHOOK_SECRET_ENCRYPTION_KEY`, `WEBHOOK_ALLOW_PRIVATE_TARGETS`, `WEBHOOK_ALLOW_HTTP`, `WEBHOOK_TIMEOUT_SECONDS`, `WEBHOOK_MAX_ATTEMPTS`, `WEBHOOK_RETRY_DELAYS` | Base64 of 32 random bytes; false (true only in the local demo); false; 10; 6; `PT1M,PT5M,PT30M,PT2H,PT6H` | key |
| api-gateway | `AUTH_SERVICE_URL`, `PARCEL_SERVICE_URL`, `PRICING_SERVICE_URL`, `DELIVERY_SERVICE_URL`, `TRACKING_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`, `FRONTEND_URL`, `AI_ASSISTANT_URL`, `PUBLIC_BASE_URL`, `RATE_LIMIT_ENABLED`, `HSTS_ENABLED`, `MANAGEMENT_SERVER_PORT` | service URLs of 3.1, `http://frontend:8080`, `http://ai-assistant:8000`, `http://localhost:8080`, true, false, 8090 | no |
| ai-assistant | `ANTHROPIC_API_KEY`, `LLM_PROVIDER`, `LLM_MODEL`, `TRACKING_SERVICE_URL`, `PRICING_SERVICE_URL`, `PARCEL_SERVICE_URL`, `PUBLIC_BASE_URL`, `AI_MAX_TOOL_ITERATIONS`, `AI_MAX_OUTPUT_TOKENS`, `AI_HISTORY_LIMIT`, `LOG_LEVEL` | -, anthropic or mock, claude-haiku-5-5, service URLs, 5, 600, 10 | key |
| frontend (build) | `VITE_API_BASE_URL` | empty in the container; `http://localhost:8080` for `npm run dev` | no |
| postgres | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `AUTH_DB_PASSWORD`, `PARCEL_DB_PASSWORD`, `PRICING_DB_PASSWORD`, `DELIVERY_DB_PASSWORD`, `TRACKING_DB_PASSWORD`, `NOTIFICATION_DB_PASSWORD` | swiftdrop_admin, -, postgres, - | yes |
| rabbitmq | `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS` | swiftdrop_user, - | password |

The local-demo `.env.example` sets `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` and `WEBHOOK_ALLOW_HTTP=true` so the mock merchant at `http://mock-merchant:9000` works; link-local and metadata targets stay blocked (FR-WHK-007).

---

## 10. Bonus: Kubernetes Deployment

Deploy the full system on Kubernetes with ConfigMaps, Secrets, Services, Ingress and a Helm chart. Start only after all core Must requirements are complete.

| ID | Requirement |
|---|---|
| K8S-001 | Helm 3 chart `infrastructure/helm/swiftdrop` with `Chart.yaml`, `values.yaml`, `values-local.yaml`, `values-secrets.example.yaml`, `templates/`; `helm lint` and `helm template` succeed; all objects in namespace `swiftdrop`. |
| K8S-002 | Deployments, 1 replica each: frontend, api-gateway, auth-service, parcel-service, pricing-service, delivery-service, tracking-service, notification-service, ai-assistant; StatefulSets: postgres (PVC 2 Gi), rabbitmq (PVC 1 Gi). mailpit and mock-merchant only with `values-local.yaml` or `demo.enabled=true`. |
| K8S-003 | One ConfigMap per service with the non-secret variables of 9.2, plus `postgres-init` with `init-databases.sh`. |
| K8S-004 | One Secret `swiftdrop-secrets` holds every secret variable of 9.2; the JWT private key is mounted from a Secret only into auth-service and the public key into the others, at `/run/keys/`. Real values are supplied at install (`-f values-secrets.yaml` or `--set`), never committed; pods use `secretKeyRef`. |
| K8S-005 | A ClusterIP Service per workload with the Compose names; postgres and rabbitmq also have headless governing services. |
| K8S-006 | One Ingress (class nginx, host `ingress.host`, default `localhost`) sending all paths to `api-gateway:8080` (the gateway implements 3.7, including the 404 for `/actuator` and `/internal`, so no snippet annotations are needed). Annotations `proxy-buffering: "off"`, `proxy-read-timeout: "3600"`, `proxy-body-size: 11m`. Optional TLS via `ingress.tls`. |
| K8S-007 | Readiness and liveness probes on every container (Spring `/actuator/health/readiness` and `/liveness`; AI `/health`; Nginx `/`; PostgreSQL `pg_isready`; RabbitMQ `rabbitmq-diagnostics -q ping`); Spring services also have a startupProbe (failure threshold 30, period 5 s). Readiness covers the database only and liveness has no dependencies (NFR-OBS-003); the api-gateway probes use its management port 8090. |
| K8S-008 | Resources (request / limit): gateway and backend services 100m, 256Mi / 500m, 512Mi (parcel and auth 200m, 384Mi / 750m, 768Mi); frontend 50m, 64Mi / 200m, 128Mi; postgres and rabbitmq 250m, 512Mi / 1 CPU, 1Gi; mailpit, mock-merchant 50m, 64Mi / 200m, 128Mi. All Helm values. |
| K8S-009 (S) | `runAsNonRoot: true`, `allowPrivilegeEscalation: false`, `capabilities.drop: [ALL]`, `readOnlyRootFilesystem: true` for stateless containers (emptyDir for `/tmp`), no default service-account token mount. |
| K8S-010 | RollingUpdate `maxUnavailable: 0`, `maxSurge: 1`; `terminationGracePeriodSeconds: 30`; init containers wait for PostgreSQL and RabbitMQ. |
| K8S-011 | Images are built locally and loaded into minikube (`minikube image load`), `imagePullPolicy: IfNotPresent`; registry optional. |
| K8S-012 | Google login uses the Compose origin `http://localhost:8080` via `kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80`; `PUBLIC_BASE_URL` is set to it in the chart. |
| K8S-013 | `helm uninstall` removes all objects except PVCs (documented as removed manually). |

**Deployment procedure (for the README):**

1. `minikube start` and `minikube addons enable ingress`.
2. Build the images with tag 1.0.0 and load them into minikube.
3. Copy `values-secrets.example.yaml` to `values-secrets.yaml` and fill the secrets.
4. `helm upgrade --install swiftdrop infrastructure/helm/swiftdrop -n swiftdrop --create-namespace -f infrastructure/helm/swiftdrop/values-local.yaml -f values-secrets.yaml`
5. Wait until `kubectl get pods -n swiftdrop` shows all Ready.
6. Run the port-forward of K8S-012 and open `http://localhost:8080`.

---

## 11. Bonus: AI Delivery Assistant

A generative-AI chatbot answering "Where is my parcel?" and "How much to send 2 kg to Kandy?" by calling SwiftDrop REST APIs, with the same capabilities exposed as MCP tools. It is read-only: it can never create, change or cancel anything. Start only after all core Must requirements are complete.

### 11.1 Architecture

- Separate service `ai-assistant` (Python 3.12, FastAPI) behind gateway paths `/api/v1/assistant/` and `/mcp`.
- A tool registry (name, description, JSON input schema, handler) is shared by the chat orchestrator and the MCP server so both expose identical capabilities.
- Handlers call the services directly with httpx (timeouts per NFR-REL-006): tracking-service, pricing-service, parcel-service. Public tools call public endpoints; the authenticated tool forwards the caller's Authorization header so the service's RBAC applies and the assistant never sees more than the user can.
- LLM access through an `LlmClient` interface: `AnthropicLlmClient` (Messages API with tool use) and `MockLlmClient` (deterministic keyword/regex, for tests and offline demos).
- Stateless: the browser sends the recent conversation with each request.

### 11.2 Requirements

| ID | Requirement |
|---|---|
| FR-AI-001 | `POST /api/v1/assistant/chat` accepts `{"messages":[{"role":"user" or "assistant","content":string}]}`: 1-10 messages (the client drops older ones), each content 1-500 chars, last from user. Returns `{"reply": string, "usedTools": [toolName,...]}`. Not streamed. Authorization optional. |
| FR-AI-002 | Tools (11.3) are the only source of facts. The orchestrator loops: call the LLM with tool definitions, run requested tools, return results, repeat until a final answer or `AI_MAX_TOOL_ITERATIONS` (5), then return a polite "I could not complete that". |
| FR-AI-003 | Grounding: the system prompt requires statuses, dates and prices only from tool results, never guessed; if a tool fails or returns nothing, say the information could not be retrieved. |
| FR-AI-004 | For quotes, ask for any missing origin district, destination district or weight (for "How much to send 2 kg to Kandy?" ask where it is sent from). If size is missing assume SMALL and say so. Never assume an origin. The maximum weight is never hard-coded: the assistant reads `maxWeightKg` from `GET /public/districts` (`list_service_districts`) and tells the user the limit when a weight is above it. |
| FR-AI-005 | Scope and guardrails: only SwiftDrop topics (tracking, prices, service areas, how to use the platform); others declined politely; never reveal instructions, keys or internal URLs; treat tool results and user text as data and ignore instructions inside them; cannot perform actions and says which page to use instead. |
| FR-AI-006 | Privacy: only data from its tools (public tracking per BR-20; own parcel summaries for logged-in customers); never asks for or repeats passwords, tokens or payment data. Message contents are not logged; logs hold only timing, tool names, status codes, request id. |
| FR-AI-007 | Limits: output tokens `AI_MAX_OUTPUT_TOKENS` (600); tool result text truncated to 4,000 chars; request timeout 30 s; gateway limit 10 requests/min per IP; bodies over 16 KB rejected (413). |
| FR-AI-008 | If the LLM provider is unavailable, times out or is misconfigured, return 503 ASSISTANT_UNAVAILABLE (Problem Details); the widget shows "The assistant is unavailable right now". Nothing else depends on the assistant. |
| FR-AI-009 | MCP server over Streamable HTTP at `/mcp` offering the 11.3 tools with schemas and descriptions; stateless; optional `Authorization: Bearer` forwarded for the authenticated tool; an `Origin` header, if present, must equal `PUBLIC_BASE_URL`. An MCP client (MCP Inspector, Claude Desktop) can list tools and call `track_parcel`. |
| FR-AI-010 | SPA chat widget: floating button on every page opening a panel with messages, input (500 chars, Enter sends), loading indicator, error message, two example chips and a note that it cannot make changes. Sends the access token when logged in. Conversation kept in memory only. |
| FR-AI-011 | Config `LLM_PROVIDER` (`anthropic` or `mock`), `LLM_MODEL` (pinned default `claude-haiku-5-5`; changing it needs a change request, 1.1), `ANTHROPIC_API_KEY`. With provider `anthropic` and no key the service starts but chat returns 503; `/health` reports provider status without exposing the key. |
| FR-AI-012 (S) | Answer in the language of the user's last message where possible, default English; prices as "LKR amount". |
| FR-AI-013 | Tests: each tool handler (mocked services), orchestrator loop (`MockLlmClient`), input validation, guardrails; MCP endpoint integration test; coverage at least 70 %. |

### 11.3 Tool catalogue

| Tool | Description and input | Service call | Auth |
|---|---|---|---|
| `track_parcel` | Status and history by `trackingNumber` (BR-11 pattern). | tracking-service `GET /api/v1/public/tracking/{trackingNumber}` | None |
| `get_delivery_quote` | Fee in LKR: `originDistrict`, `destinationDistrict` (name or code), `weightKg` (at least 0.01; the maximum is `maxWeightKg` from the districts endpoint, default 30), `size` (SMALL, MEDIUM, LARGE; default SMALL). | pricing-service `POST /api/v1/public/quotes` | None |
| `list_service_districts` | Districts served with zone and max weight; no input. | pricing-service `GET /api/v1/public/districts` | None |
| `list_my_parcels` | Logged-in customer's recent parcels: `status?`, `limit?` (1-10, default 5). | parcel-service `GET /api/v1/customer/parcels?size=...` | User JWT (customer) |

District names are normalised (case-insensitive, spaces to underscores) to codes such as `NUWARA_ELIYA`; an unknown district returns a tool error listing valid districts. The authenticated tool without a token or with a non-customer token returns "This needs a customer login" and the assistant explains it. Tool errors reach the model as text, never stack traces.

### 11.4 Flow 6: "How much to send 2 kg to Kandy?"

1. The visitor types the question; the SPA sends `POST /api/v1/assistant/chat`. The assistant validates and calls the LLM with the tools.
2. The LLM sees the origin is missing and asks "Which district are you sending from?".
3. The visitor answers "Colombo"; the SPA sends the whole conversation. The LLM requests `get_delivery_quote` (COLOMBO to KANDY, 2 kg, SMALL); the handler calls pricing-service.
4. The result returns to the LLM, which answers "LKR 650.00 for a small 2 kg parcel (assuming small size)." The widget displays it.

---

## 12. Testing and Quality

| Level | Tools | Scope |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ; Vitest; pytest | Business rules, pricing, state machine, validators, mappers, helpers, UI components. |
| Integration | Spring Boot Test, Testcontainers (PostgreSQL, RabbitMQ), MockMvc/WebTestClient, WireMock | REST + security + database per service; outbox, broker, consumers; webhook delivery and retry; inter-service clients against WireMock stubs. |
| Authorisation | Spring Security Test | Role and ownership rules for every endpoint group of each service. |
| Manual acceptance | Appendix A | Run once on a clean Compose deployment before submission. |

| ID | Requirement |
|---|---|
| TST-001 | JaCoCo line coverage (DTOs, entities, config, generated code excluded) must be at least 70 % for the service, messaging and security packages of each backend service and for common-lib; AI assistant at least 70 %; frontend at least 60 % statements (S). `mvn verify` fails below the threshold. |
| TST-002 | Test reports are submitted: HTML/XML results and coverage per service under `docs/test-reports/<service>/` and a one-page `docs/test-reports/SUMMARY.md` with total tests, passed, failed, skipped, coverage per service, date and commit hash. |
| TST-003 | One command per module (`mvn verify`, `npm test -- --coverage`, `pytest --cov`); deterministic (no sleeps, use Awaitility) and independent tests. Each service tests its internal clients against WireMock stubs of the contracts in 7.4, so services are built in parallel. |
| TST-010 | pricing-service: same and different zone; weights 1.00, 1.01, 2.00, 30.00, with 0.00 and 30.01 rejected with 400 VALIDATION_ERROR; each size; discounts 0, 10, 50 %; HALF_UP; missing pricing row; inactive zone; AT-05 to AT-07. |
| TST-011 | parcel-service state machine: every allowed transition (incl. ADMIN override to FAILED) succeeds with correct side effects; every other (from, to, actor) is rejected; DELIVERED without recipient name, FAILED without reason, OTHER without note, a reason not allowed for the stage are rejected. |
| TST-012 | Validators: password policy, phone normalisation, email, tracking-number format and generator (uniqueness, collision retry), district and size limits, weight format and minimum (the maximum weight is tested in pricing-service, TST-010), idempotency hash. |
| TST-013 | auth-service security helpers: JWT create/validate with the key pair (expiry, wrong audience, tampering, token signed with another key rejected by every service), refresh rotation, reuse detection and grace period, lockout counter, constant-time secret check. notification-service: AES-GCM round trip. |
| TST-014 | Webhook: HMAC for a known vector, retry delay calculation and attempt limit, SSRF validator (private, loopback, link-local with the private flag on and off, IPv6, credentials in URL, scheme). |
| TST-015 | Notification: recipient selection per Table 2.5 (including no recipient FAILED message for a pickup failure), SMS templates without personal data, masking utility. |
| TST-020 | Auth flows: register, login, refresh rotation, reuse detection revoking the family (not within the grace period), logout, lockout after 5 failures, forced password change in a service other than auth-service, Google callback with a stubbed Google server (valid, invalid state, invalid nonce, unverified email, existing local email). |
| TST-021 | Parcel creation: success; FEE_CHANGED; validation errors; idempotent replay (also after a price change), key reuse with another body, failed request leaves the key reusable; merchant duplicate reference; merchant not approved; atomicity (a failure after the parcel insert leaves no parcel, key or outbox row). |
| TST-022 | Courier status update: proof and parcel transition succeed together and a failed transition leaves no proof; concurrent updates give 409 CONCURRENT_UPDATE; a timed-out or lost transition response (parcel already moved) still stores the proof and returns success after the re-read, and a non-matching re-read rolls everything back; an admin reassignment during a courier update is serialised by the row lock (no PICKED_UP parcel with the wrong courier); photo validation (type, 10 MB upload limit, 5 MB stored limit, content). |
| TST-023 | Event flow with Testcontainers PostgreSQL and RabbitMQ: a status change gives (a) an outbox row published to `swiftdrop.events`, (b) a message in `notification.events` consumed and recorded exactly once (also if delivered twice), (c) a webhook delivery to a WireMock endpoint with a valid signature, (d) a tracking event and an SSE push. |
| TST-024 | Webhook retry: WireMock returns 500, 500, then 200 gives 3 attempts and SUCCEEDED (delays shortened by config); six failures end FAILED; manual retry resets the schedule. |
| TST-025 | Resilience: with RabbitMQ stopped, a status update still succeeds and the outbox row stays unpublished; after restart the event is published. |
| TST-026 (S) | DLQ: an always-failing message ends in the DLQ after 3 attempts. |
| TST-027 | SSE: client receives `snapshot` then `status` after a change; stream completes after a terminal status; unknown number returns 404. |
| TST-028 (S) | Reports: counts and rates for a prepared data set across Asia/Colombo day boundaries. |
| TST-029 | Admin rules: assignment, courier deactivation (blocked while in progress, unassigns CREATED), merchant lifecycle, zone/district/pricing rules, admin force-fail (allowed states, mandatory note, event emitted), audit entries in tracking-service. |
| TST-030 | A courier cannot read or update another courier's or an unassigned delivery (404) and cannot call customer, merchant or admin endpoints (403). |
| TST-031 | A customer cannot read, cancel, download the receipt of, view the timeline or the proof photo of another customer's parcel (404) and cannot call merchant, courier or admin endpoints (403). |
| TST-032 | A merchant cannot read or cancel another merchant's order (404); a client token cannot call any endpoint other than `/merchant/orders*` (for example credentials, webhook, `/me`, receipt, timeline, proof: 403); a token with only `orders:read` cannot create orders (403); suspended or pending merchants are refused (403 / `invalid_client`). |
| TST-033 | Unauthenticated calls to every protected endpoint return 401; every `/admin/**` endpoint returns 403 for every non-admin role (parameterised over the catalogue); deactivated users cannot log in or refresh. |
| TST-034 | Public tracking and SSE contain none of the fields forbidden by BR-20; error responses have no stack trace or internals; logs captured in the run contain no password, token or full phone number; events contain no customer, merchant or recipient contact data and no names other than the staff names allowed by BR-28 (`courierFirstName`, `fullName`); logs, audit details, notification rows, webhook payloads and the public view contain no staff notes. |
| TST-035 | Internal endpoints reject a missing or wrong `X-Internal-Key`; the gateway returns 404 for `/internal/**` and `/actuator/**` on port 8080, while its management port 8090 answers `/actuator/health/liveness` and `/actuator/health/readiness` and is not published. |
| TST-040 | Frontend: login and registration forms (validation, server errors), new-delivery flow with quote and fee-changed handling, route guards by role, courier status form (allowed actions, required fields), tracking page with mocked EventSource and polling fallback. |
| TST-041 | AI service tests per FR-AI-013 (bonus). |

---

## 13. Deliverables

| Deliverable | Content and location |
|---|---|
| Source repository and README | Monorepo of 3.2; README per DEP-012. |
| Design documentation (PDF) | Use-case diagrams (4.1, one per actor group), ER diagrams (6.3-6.4), architecture and service diagram (3.1, 3.3), integration design (3.4-3.6), sequence/activity diagrams (flows 4.2-4.6: create delivery, merchant order and webhook, courier update, Google login, assignment and deactivation; plus AI flow 11.4), design patterns note, this SRS as appendix. |
| Test reports and coverage summary | `docs/test-reports/` per TST-002. |
| Contribution sheet | `docs/contribution-sheet.pdf`: per member name, service or package, modules/files owned, tests written, demo part (from the work division and Git history). |

---

## Appendix A. Acceptance Scenarios and Demo Script

Every scenario shall pass on a clean Docker Compose deployment (AT-35 on Kubernetes). Demo order: AT-01 to 11 customer, 12 to 16 merchant, 17 to 20 dispatcher and courier, 21 to 24 security and integration, then the rest. For the demo, the leader may set `WEBHOOK_RETRY_DELAYS=PT5S,PT10S,PT15S,PT20S,PT30S`.

| ID | Scenario and expected result | Covers |
|---|---|---|
| AT-01 | A visitor registers with valid data and logs in; a weak password shows field errors; the same email again shows EMAIL_ALREADY_REGISTERED. | FR-AUTH-001..003 |
| AT-02 | Five wrong passwords lock the account: the sixth attempt (even with the right password) returns 429 ACCOUNT_LOCKED; after 15 min login works. | FR-AUTH-005 |
| AT-03 | Google login as a new user creates a customer account and lands on the dashboard without a password. | FR-AUTH-009, 010 |
| AT-04 | Google login with an email that already has a local account is refused with `account_exists`; accounts are not merged. | FR-AUTH-010 |
| AT-05 | Quote Colombo to Kandy, 2 kg, MEDIUM: total LKR 800.00 (base 500 + weight 150 + size 150). The same with SMALL: 650.00. | FR-PRC-001, 002 |
| AT-06 | Quote Colombo to Gampaha (same zone), SMALL: 1.00 kg gives 350.00; 1.01 kg gives 450.00. | FR-PRC-001 |
| AT-07 | Merchant with 10 % discount, Colombo to Jaffna, 5 kg, LARGE: subtotal 1,800.00 (800 + 4 x 150 + 400), discount 180.00, total 1,620.00. | FR-PRC-003, FR-PAR-007 |
| AT-08 | A customer previews a quote, an admin changes the price, the customer confirms: FEE_CHANGED with the new fee, nothing created. | FR-PAR-005 |
| AT-09 | Pressing Confirm twice (or replaying the same Idempotency-Key) creates exactly one parcel. After a lost response and an admin price change, retrying with the same key returns the original 201 (not FEE_CHANGED). | FR-PAR-006 |
| AT-10 | The public tracking page shows only the limited view; a wrong number shows the generic not-found message; no names, phones, addresses or notes appear. | FR-TRK-002, FR-UI-006, BR-20 |
| AT-11 | With a tracking page open, a courier changes status; the page updates within 3 s without reload; blocking the SSE stream makes the page fall back to polling. | FR-TRK-003, FR-UI-006, NFR-PERF-003 |
| AT-12 | A merchant registers; login says pending approval; the admin approves; the approval email appears in Mailpit and login works. Rejection with a reason blocks login; re-approval restores it. | FR-AUTH-016, 017 |
| AT-13 | The merchant creates credentials (secret shown once) and obtains a token; a wrong secret gives `invalid_client`; a third active credential is refused. | FR-AUTH-018, 019 |
| AT-14 | Order via API (Postman/curl): 201 with tracking number and discounted fee; same merchantReference gives DUPLICATE_MERCHANT_REFERENCE; invalid district gives VALIDATION_ERROR; suspended merchant gets MERCHANT_NOT_ACTIVE; the order appears in the dashboard. | FR-PAR-007, 008, 014 |
| AT-15 | The merchant saves the mock-merchant URL (`http://mock-merchant:9000/...`, allowed by the demo flags) and sends a test ping; the mock shows a request whose signature verifies; the delivery is in the log. | FR-WHK-001, 003, 009 |
| AT-16 | After a courier status change on a merchant order, mock-merchant receives `parcel.status.changed` with correct data and a valid signature. | FR-WHK-002 |
| AT-17 | The dispatcher assigns courier A; A sees it in today's list within 30 s; reassigning to B moves it and A no longer sees it; assigning after pickup is refused. | FR-DEL-002, 003 |
| AT-18 | Courier A moves a parcel Picked up, In transit, Out for delivery, Delivered with recipient name, note and a 7 MB phone photo; the customer sees each step live and the proof afterwards. | FR-DEL-005..007 |
| AT-19 | A courier marks a parcel Failed with a reason allowed for the stage; OTHER without a note and a reason not allowed for the stage are refused; the public page shows the reason label only. A pickup failure sends no message to the recipient. | FR-PAR-002, Table 2.4, 2.5 |
| AT-20 | Skipping a step (Created to Delivered), updating a terminal parcel and updating an unassigned parcel are all rejected. | FR-PAR-001, BR-03 |
| AT-21 | Courier B requesting courier A's delivery: 404. A customer requesting another's parcel: 404. A merchant client token on the credentials endpoint: 403. No token: 401. A token signed with a different key: 401 in every service. | TST-030..033 |
| AT-22 | Receiver down: attempts follow the retry schedule (shortened in the demo); when it returns the next attempt succeeds; after six failures the delivery is FAILED and a manual retry succeeds. | FR-WHK-004, 005, 009 |
| AT-23 | Each status change produces the emails (Mailpit) and SMS records defined by Table 2.5; redelivering the same event produces no duplicate. | FR-NTF-001..006 |
| AT-24 | Stop RabbitMQ, perform courier updates (they succeed), restart: events are published and notifications, webhooks and tracking updates arrive; nothing is lost. | NFR-REL-001, 002 |
| AT-25 | A customer cancels a CREATED parcel; cancelling after pickup gives INVALID_STATUS_TRANSITION; an admin cancel needs a reason; an assigned courier sees the cancelled parcel in the list. | FR-PAR-009, 011, FR-DEL-003 |
| AT-26 | An admin creates a courier; first login forces a password change and every other API of every service returns PASSWORD_CHANGE_REQUIRED until changed. | FR-AUTH-014, 020 |
| AT-27 | Workload page shows correct counts; deactivating a courier with an OUT_FOR_DELIVERY parcel is refused; one with only CREATED parcels is deactivated and the parcels become unassigned. | FR-DEL-009..011, FR-AUTH-020 |
| AT-28 | Moving a district to another zone and changing a price changes later quotes but not existing parcels. | FR-PRC-006, 007 |
| AT-29 | Reports for a prepared data set show correct per-day counts and success/failure rates. | FR-PAR-013 |
| AT-30 | The receipt PDF downloads with the required content for a delivered parcel. | FR-PAR-010 |
| AT-31 | At 360 px width the tracking page and courier screens work without horizontal scrolling. | NFR-USA-001, FR-UI-008 |
| AT-32 | From a clean clone with keys and `.env` created, `docker compose up -d --build` leaves all containers healthy; data survives down and up. | DEP-004..006 |
| AT-33 | AI: "Where is my parcel? SD-..." returns the status; "How much to send 2 kg to Kandy?" asks the origin and after "Colombo" answers LKR 650.00 (assuming small); a cancel request is declined with guidance; an unrelated question is declined; a logged-in customer can list parcels. (bonus) | FR-AI-001..006 |
| AT-34 | An MCP client connects to `/mcp`, lists the four tools and calls `track_parcel`. (bonus) | FR-AI-009 |
| AT-35 | Kubernetes: `helm install` leaves all pods ready; main flows work through the Ingress; a rolling restart of one service loses no events. (bonus) | K8S-001..013 |
| AT-36 | With the demo flags on (`WEBHOOK_ALLOW_PRIVATE_TARGETS=true`), saving `http://169.254.169.254/` as the webhook URL is rejected, while the mock-merchant URL is accepted. Forced errors show no stack traces; captured logs contain no passwords, tokens, secrets or full phones; headers include NFR-SEC-015. | NFR-SEC-011, 013, 015, FR-WHK-007 |
| AT-37 | Submitted test reports show all tests passing and coverage at or above TST-001. | TST-001, 002 |
| AT-38 | An admin fails an OUT_FOR_DELIVERY parcel with reason and note: status FAILED, customer notified, merchant webhook sent; without a note refused; failing a CREATED parcel refused; the courier can then be deactivated. | FR-PAR-012, FR-AUTH-020 |
| AT-39 | The audit log page shows entries for assignment, admin cancel, price change, merchant approval and user creation, without passwords or contact data. | FR-TRK-005, NFR-SEC-019 |

---

## Appendix B. Reference Data (Seed Values for pricing-service V2)

### B.1 Zones and districts (25 districts, 6 zones)

| Zone code | Zone name | Districts (code) |
|---|---|---|
| WESTERN | Western | Colombo (COLOMBO), Gampaha (GAMPAHA), Kalutara (KALUTARA) |
| CENTRAL | Central | Kandy (KANDY), Matale (MATALE), Nuwara Eliya (NUWARA_ELIYA) |
| SOUTHERN | Southern | Galle (GALLE), Matara (MATARA), Hambantota (HAMBANTOTA) |
| NORTH_EAST | Northern & Eastern | Jaffna (JAFFNA), Kilinochchi (KILINOCHCHI), Mannar (MANNAR), Vavuniya (VAVUNIYA), Mullaitivu (MULLAITIVU), Batticaloa (BATTICALOA), Ampara (AMPARA), Trincomalee (TRINCOMALEE) |
| NW_NC | North Western & North Central | Kurunegala (KURUNEGALA), Puttalam (PUTTALAM), Anuradhapura (ANURADHAPURA), Polonnaruwa (POLONNARUWA) |
| UVA_SAB | Uva & Sabaragamuwa | Badulla (BADULLA), Monaragala (MONARAGALA), Ratnapura (RATNAPURA), Kegalle (KEGALLE) |

### B.2 Base fee matrix (LKR, origin rows by destination columns)

| Origin / Dest. | WESTERN | CENTRAL | SOUTHERN | NORTH_EAST | NW_NC | UVA_SAB |
|---|---|---|---|---|---|---|
| WESTERN | 350 | 500 | 500 | 800 | 550 | 550 |
| CENTRAL | 500 | 350 | 650 | 750 | 550 | 550 |
| SOUTHERN | 500 | 650 | 350 | 950 | 750 | 600 |
| NORTH_EAST | 800 | 750 | 950 | 350 | 700 | 800 |
| NW_NC | 550 | 550 | 750 | 700 | 350 | 700 |
| UVA_SAB | 550 | 550 | 600 | 800 | 700 | 350 |

Per-kilogram fee (each whole kilogram above the first): LKR 100 when origin and destination zone are the same, LKR 150 otherwise. All 36 directed pairs are seeded (the matrix is symmetric).

### B.3 Size categories and surcharges

| Size | Maximum dimensions (L x W x H) | Surcharge (LKR) |
|---|---|---|
| SMALL | 30 x 20 x 10 cm | 0.00 |
| MEDIUM | 50 x 40 x 30 cm | 150.00 |
| LARGE | 80 x 60 x 50 cm | 400.00 |

The maximum weight for every size is the value of `MAX_PARCEL_WEIGHT_KG` (default 30.00 kg); parcels exceeding LARGE dimensions are not accepted.

---

## Appendix C. Design Decisions

These resolve every alternative the assignment brief leaves open and are final.

| ID | Decision | Reason |
|---|---|---|
| D-01 | Real-time by SSE with polling fallback (not WebSockets). | One-way data, simple, proxy friendly, works unauthenticated. |
| D-02 | Merchant API auth by OAuth 2.0 client credentials only (no API keys). | One mechanism, short-lived scoped tokens, meets the OAuth requirement. |
| D-03 | Seven services as in the repository README (gateway, auth, parcel, pricing, delivery, tracking, notification); webhooks live in notification-service; audit log lives in tracking-service; no BFF, no separate webhook service. | Matches the team's structure with the fewest deployables. |
| D-04 | RabbitMQ (not Kafka). | Key routing, per-consumer queues, DLQs; light for the team. |
| D-05 | Transactional outbox in every publishing service, shared through common-lib. | No lost events when the broker is down; one implementation. |
| D-06 | Events carry ids and non-contact fields only (staff names and the free-text staff note are allowed, BR-28); notification-service fetches contact data when it sends. | Removes customer, merchant and recipient contact data from events, DLQs and outbox tables (BR-28). |
| D-07 | One role per user; ADMIN is Dispatcher/Admin. | Matches the brief; simple RBAC. |
| D-08 | CANCELLED status added; FAILED terminal; no re-delivery. | Realistic operations, simple lifecycle. |
| D-09 | Payments, taxes, live GPS out of scope. | Not in the brief; fees only recorded. |
| D-10 | No email verification at sign-up; Google accounts never auto-merged with local accounts. | Prevents account takeover. |
| D-11 | JWT RS256, 15 min, signed only by auth-service; rotating refresh tokens in an HttpOnly cookie (7 days, 30-day cap). The 15-minute window after deactivation or suspension is accepted for all roles; merchant order create and cancel re-check the merchant. | No shared signing secret; simple, stateless services; the one high-value check is done explicitly. |
| D-12 | Proof photos in PostgreSQL BYTEA (re-encoded, 10 MB upload, 5 MB stored). | No shared file storage needed; phone photos accepted. |
| D-13 | One PostgreSQL server, six databases, one user per service. | Data ownership with minimal infrastructure. |
| D-14 | Webhook retries by a database scheduler, not broker delay queues. SSRF check by resolve-and-validate with the JDK HttpClient; connection pinning (custom DNS resolver) is not built and the rebinding window is accepted. | Visible attempt log, manual retry, no plugins; simple and documented. |
| D-15 | parcel-service owns parcel status; delivery-service performs courier updates by calling parcel-service's transition endpoint; proof stored locally in one transaction under a pessimistic lock on the `deliveries` row (also taken by assignment); on timeout, 5xx or 409 the parcel's real status and sequence are re-read and a match counts as success, otherwise the transaction rolls back. | One state machine, no distributed transaction framework. |
| D-16 | AI assistant in Python with Anthropic API and MCP; read-only tools; stateless chat. | Strong ecosystem; isolated; low risk. |
| D-17 | Kubernetes via Helm on minikube; Ingress sends everything to the gateway; host localhost through port-forward 8080. | Same Google redirect URI as Compose; no Ingress snippets. |
| D-18 | Rate limiting in the gateway, in memory, per IP, configurable and switchable. | No Redis; demo from one machine stays usable. |
| D-19 | Mailpit SMTP sink; mock SMS adapter. | Demonstrable notifications without external accounts. |
| D-20 | Foreign resources return 404, role violations 403. | Prevents resource enumeration (IDOR). |
| D-21 | UTC storage; business days and display in Asia/Colombo. | Correct daily reports and courier "today". |
| D-22 | Tracking number `SD-` + 10 Crockford base-32 characters. | Unambiguous, about 10^15 combinations, hard to guess. |
| D-23 | Merchants create orders through the API only; dashboard is for monitoring and settings. | Matches the brief ("via a secured API"). |
| D-24 | Fee confirmed against `expectedFee` (FEE_CHANGED); idempotency lookup comes first. | The customer always pays what was shown; retries never duplicate. |
| D-25 | Admin force-fail for parcels stuck after pickup; no re-assignment after pickup. | Unblocks an unreachable courier without a second assignment workflow. |
| D-26 | Optional extras not required by the brief were withdrawn. | Keeps scope achievable for ten members. |
| D-27 | Frontend is JavaScript (JSX), matching the repository. | Fewer moving parts; contracts are in this SRS, not in TypeScript types. |
| D-28 | Single instance per service; no horizontal scaling. | Keeps SSE, schedulers and outbox simple. |
| D-29 | The SPA composes pages from several services (7.6). | No aggregation service. |
| D-30 | Backend stays on Spring Boot 3.5.16 and Spring Cloud 2025.0.x although free support for both ended on 30 June 2026 (3.5.16 was the last free patch); Boot 4.1 (free support until July 2027) is not used. Accepted risk: no further free security patches for the framework; no upgrade is made during the project (NFR-SEC-023). | AI tools know Boot 3.x code best and Boot 4 is a major migration for a one-semester project; the choice is explicit instead of a "latest supported" claim. |

---

## Appendix D. Sign-off, Open Items and Review Resolution

### D.1 Open items (outside this document; must be closed before the status becomes FINAL)

| # | Item | Owner |
|---|---|---|
| 1 | Fill group number and member names (header). | Group leader |
| 2 | Add submission deadline and weekly milestones; enter the subgroup-to-service assignment in Appendix E (the table is prepared). | Group leader |
| 3 | Create the Google OAuth client and share the credentials privately (for AT-03, 04). | One member |
| 4 | Provide `ANTHROPIC_API_KEY` for the bonus assistant (or use the mock provider). | One member |
| 5 | Day-1 version pinning: record the exact versions of 1.4 in the parent POM (Spring Boot 3.5.16 and Spring Cloud 2025.0.x are the explicit exception of D-30). | Leader |

**Closing:** the group leader closes items 1 to 5 above and completes Appendix E, then records the sign-off (name and date) and sets the status to FINAL.

### D.2 Resolution of review findings

| Finding | Resolution in this version |
|---|---|
| Idempotency ran after the fee check | Lookup first, key row inserted with the parcel in one transaction, no in-progress state (FR-PAR-006, Flow 1). |
| AT-36 contradicted the demo flags | Link-local and metadata addresses are blocked regardless of flags (FR-WHK-007, AT-36). |
| Performance targets not demonstrable | Marked (S), indicative, lower numbers, gateway limits switchable; SSE connection cap removed (NFR section 8). |
| Photo limit too tight | 10 MB upload, 5 MB stored (FR-DEL-006, NFR-SEC-005). |
| SSRF pinning not buildable with the JDK client | Simpler documented approach (FR-WHK-007, D-14). |
| Event-carried state claim wrong | Events carry no customer, merchant or recipient contact data; consumers fetch what they need and retry (BR-28, FR-NTF-007, 3.3). |
| JWT `sub` ambiguity | `sub` is always the user id; merchant id is the `mid` claim; idempotency owner is typed (3.6, FR-PAR-006). |
| 15-minute deactivation gap | Accepted and documented; merchant order calls re-check (BR-23, D-11, NFR-SEC-023). |
| Failure notifications too broad | Failure reasons limited per stage; recipient FAILED message only after OUT_FOR_DELIVERY (Tables 2.4, 2.5). |
| Personal data in events and DLQs | Contact data removed from events; DLQ messages expire after 3 days; outbox purged after 1 day (BR-28, 3.5). |
| Smaller wording problems | `expectedFee` optional meaning (FR-PAR-007); build time realistic (DEP-004); detail refetch on status event (FR-UI-007); courier list refresh (FR-DEL-003, FR-UI-008). |
| Shared HS256 secret | RS256, only auth-service signs (FR-AUTH-006, 3.6). |
| Upload limits disagree | One rule everywhere (FR-DEL-006). |
| Courier deactivation across services | Flow 5, FR-AUTH-020, FR-DEL-010, 011. |
| Document said "final" with no open items | Status text corrected; open items listed in D.1. |
| Unbalanced and blocking work packages | Each service owns its migrations; only common-lib is a shared prerequisite (3.4). |
| Hidden dependencies and duplicated idempotency | One idempotency implementation in parcel-service; interaction map in 3.3; WireMock stubs of internal contracts (TST-003). |
| Unowned cross-cutting work | common-lib contents listed in 3.4. |
| No shared library | `backend/common-lib` with parent POM; `EventTopology` prevents queue-argument mismatches. |
| No schedule or cut line | Cut line in 1.6; schedule is open item D.1-2. |
| Environment gotchas | `.gitattributes` LF, wget healthchecks, gateway-level 404 for `/actuator` and `/internal`, switchable rate limits, version pinning (DEP-001, 014, FR-GW, D.1-5, D-30). |
| Demo gap for merchants | Postman collection and curl script (DEP-015). |
| Change control too heavy | Leader plus the owner of each affected service (1.1). |
| Monolith to microservices | Sections 3 to 5 restructured by service. |
| Spring Boot 3.5 out of free support | Explicit decision D-30 with the accepted risk; the "still supported" claim removed (1.4, D.1-5, NFR-SEC-023). |
| Gateway health check contradicted the 404 for `/actuator` | Health served on a separate management port 8090; port 8080 still returns 404 (3.1, FR-GW-001, 7.4, DEP-005, DEP-007, K8S-007, TST-035). |
| Readiness included the broker | Readiness covers the database only; liveness has no dependencies (NFR-OBS-003, NFR-REL-003, K8S-007). |
| common-lib unusable by the reactive gateway | Split into common-core and common-lib; the gateway never uses common-lib and has its own request-id and error handling (3.4, FR-GW-008). |
| Courier update failure windows | Pessimistic lock on the `deliveries` row, re-read and match counts as success (Flow 3, FR-DEL-002, FR-DEL-005, FR-PAR-016, D-15, TST-022). |
| BR-28 contradicted the event definitions | Rule redefined: no customer, merchant or recipient contact data; staff names and the staff note are allowed (BR-28, 6.5, D-06, TST-034, FR-UI-008). |
| Errors raised in security filters not specified | `AuthenticationEntryPoint` and `AccessDeniedHandler` in common-lib, with TOKEN_EXPIRED distinguished (3.4). |
| `PUBLIC_BASE_URL` missing for parcel-service | Added to the parcel-service variables; `trackingUrl` defined (9.2, FR-PAR-007). |
| `LLM_MODEL` not a literal value | Pinned to `claude-haiku-5-5` (9.2, FR-AI-011). |
| Wrong CORS cross-reference; receipt 403 or 404 | FR-GW-007 cites NFR-SEC-020; one rule: a role never allowed gets 403, a non-owner gets 404 (FR-PAR-010, FR-PAR-017). |
| Maximum weight checked in two services | pricing-service enforces the maximum; parcel-service checks format and minimum; the assistant reads `maxWeightKg` (BR-10, FR-PAR-005, FR-PRC-004, FR-AI-004, Appendix B). |
| Client-credentials tokens had no global rule | `typ=client` accepted only on `/merchant/orders*`, 403 elsewhere (3.4, 3.6, 7.3, NFR-SEC-009, TST-032). |
| Proof photos and receipts need a bearer token | FR-UI-011: fetch with the token and use a blob URL or a download. |
| Open items and service ownership | Appendix E added for the leader to complete; closing steps in D.1. |

---

## Appendix E. Service Ownership

Filled in by the group leader before sign-off (D.1-2). Every service has exactly one owner subgroup (1.1); only common-core and common-lib are shared prerequisites and are built first (3.4).

| # | Service or module | Owner subgroup |
|---|---|---|
| 1 | common-core and common-lib (built first, week 1) | (leader to fill in) |
| 2 | api-gateway | (leader to fill in) |
| 3 | auth-service | (leader to fill in) |
| 4 | parcel-service | (leader to fill in) |
| 5 | pricing-service | (leader to fill in) |
| 6 | delivery-service | (leader to fill in) |
| 7 | tracking-service | (leader to fill in) |
| 8 | notification-service | (leader to fill in) |
| 9 | frontend | (leader to fill in) |
| 10 | infrastructure (Compose, init script, README, tools) | (leader to fill in) |
| 11 | Bonus: Kubernetes (Helm chart) and ai-assistant | (leader to fill in) |
