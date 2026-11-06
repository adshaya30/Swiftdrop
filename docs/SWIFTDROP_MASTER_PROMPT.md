# SwiftDrop Master Prompt (v2.2)

> **Leader note (not for the AI).** Save this file in the repo root under the name your AI tool loads automatically (for Antigravity check whether it expects `AGENTS.md` or a file under `.agent/rules/`, and keep one copy only). Change it only by pull request when the team changes a decision. Put the three reference files in `docs/`: `SRS.md` (convert the PDF to Markdown and **keep backticks around monospace values**), `WORK_DIVISION.md`, `UI_DESIGN_SYSTEM.md`.

You are an AI coding assistant working on **SwiftDrop**, a parcel delivery and courier management platform for a courier company in Sri Lanka. Ten members work in one monorepo at the same time, each with their own AI session. These rules exist so that ten separately generated pieces fit together. Follow them in every session, for every task.

---

## 0. Precedence (who wins when documents disagree)

1. **SRS v2.1** (`docs/SRS.md`): binding, single source of truth.
2. **This master prompt**.
3. **The member's task prompt**: it only supplies assignment data (member number, requirement IDs, folders, tests, dependencies). It may narrow your work. It never widens it and never overrides a rule here.
4. **UI Design System** (`docs/UI_DESIGN_SYSTEM.md`): visual styling only (section 10).
5. **Your own preference**: last.

- If two of these conflict, follow the higher one and tell the member in one sentence ("Conflict: X says A, SRS says B; I followed B").
- Inside the SRS: Business Rules (SRS 2.2) prevail over other statements; otherwise the more specific statement prevails.
- Ownership in section 7 of this file overrides `docs/WORK_DIVISION.md`.
- Monospace in the SRS means a literal value (path, header, status, field, variable, code). Use it exactly. If the Markdown copy lost the formatting, treat anything that looks like an identifier, path, header, status, env variable or error code as literal.
- Never invent behaviour, fields, endpoints, error codes, table columns, event types, env variables or statuses. If something is missing from the SRS it is out of scope or needs a change request (section 13). Stop and ask.

## 1. Read before writing any code

Always read: SRS 1.1, 1.2, 1.4, 1.6, 2.1 to 2.5, 7.2, and this whole file.
Then, for your slice only: functional requirements in section 5 (by ID), data in 6.2 and 6.3, endpoints in 7.3, internal endpoints in 7.4, payloads in 7.5, the relevant flows in section 4 and acceptance scenarios in Appendix A.
Also: events, shared library, tokens: 3.3 to 3.6. Gateway routes: 3.7. Frontend: 5.8, 7.1, 7.6. Reference data: Appendix B (pricing only).
Do not load the whole SRS blindly, but you may read more whenever a requirement refers to it.

## 2. Who you work for

The task prompt states the member number (#1 to #10). Section 7 tells you which files you may edit. If the number is missing, ask before doing anything. If the task needs something outside your files, do not do it: write a "Request to owner" (section 13).

---

## 3. Fixed technology stack (no substitutions, upgrades or additions)

- **Backend services:** Java 21, Spring Boot 3.5.16 (Boot 4.x deliberately not used, SRS D-30), Spring Web, Spring Security (OAuth2 resource server), Spring Data JPA, Bean Validation, Flyway, Spring AMQP, springdoc-openapi, MapStruct, Micrometer, Maven with one parent POM. Service-specific: OpenPDF (parcel-service), Spring Mail (notification-service), `java.net.http.HttpClient` (webhooks), Spring `RestClient` (internal clients).
- **api-gateway:** Spring Cloud Gateway (reactive) on Spring Cloud 2025.0.x. Artifact and property names were renamed in 2025.0 and AI tools generate the old ones (the new names are believed to be `spring-cloud-starter-gateway-server-webflux` and `spring.cloud.gateway.server.webflux.*`; **#7 must verify on spring.io**; everyone else does not touch the gateway).
- **Frontend:** React 18, JavaScript with JSX (not TypeScript), Vite, React Router, TanStack Query, MUI, React Hook Form with Zod, browser `EventSource`, Vitest and React Testing Library. Built to static files on `nginxinc/nginx-unprivileged:stable-alpine`.
- **Data and messaging:** PostgreSQL 16 (one server, six databases, one least-privilege user each), RabbitMQ 4.x, Mailpit.
- **Containers:** Docker, Compose v2, multi-stage builds; `eclipse-temurin:21-jre-alpine`, `node:22-alpine` (build only).
- **Testing:** JUnit 5, Mockito, AssertJ, Spring Security Test, Testcontainers (PostgreSQL, RabbitMQ), WireMock, Awaitility, JaCoCo; Vitest.
- **Companion packages** that come with a listed library are allowed (for example `@mui/icons-material`, `@hookform/resolvers`, `@fontsource/inter`, `micrometer-registry-prometheus`). Anything else, including **Lombok** (use records and plain classes), needs the member's approval, and dependency changes go to the pom owner (section 7.3).
- The AI assistant (Python) and Kubernetes are bonus scope: do not touch them unless the task prompt says so.

## 4. Global conventions

- JSON fields camelCase; DB columns snake_case; enum values UPPER_SNAKE_CASE; API paths lower-case kebab-case; ids are UUID v4 strings.
- All API paths are under `/api/v1`. Currency LKR, two decimals. Money is `BigDecimal` / `NUMERIC(12,2)`, never floating point. The frontend never calculates fees: it shows server values and sends `expectedFee` exactly as the server returned it.
- Timestamps: ISO 8601 UTC in APIs and storage. "Today", daily buckets and displayed times use Asia/Colombo (UTC+05:30). `from`/`to` are `yyyy-MM-dd` in Asia/Colombo, inclusive.
- Lists: `page` (0-based, default 0), `size` (default 20, max 100), `sort=field,dir` (whitelisted fields); response `{items, page, size, totalItems, totalPages}`.
- Enumerations, labels, failure reasons: SRS 6.2 and Tables 2.3 / 2.4 exactly.
- Cite SRS IDs (for example `FR-PAR-006`, `BR-25`) in comments on the logic that implements them.
- Requirements are Must unless marked (S). Build (S) only after every Must item of your slice is done.

## 5. Architecture rules (hard)

- Microservices behind one gateway. Each backend service runs as exactly one instance and owns its database (BR-27). No service reads another's database; no foreign keys across databases.
- Data crosses services only through events (SRS 3.5) or internal REST (SRS 7.4). The only allowed service-to-service calls are the SRS 3.3 table.
- Internal endpoints live under `/internal/**`, are secured only by `X-Internal-Key` (constant-time compare, `InternalKeyFilter` from common-lib), and are never routed through the gateway. Internal clients use 5 s connect / 10 s read, propagate `X-Request-Id`, return callee business errors unchanged, and map connection failures, timeouts and 5xx to `503 SERVICE_UNAVAILABLE`.
- **Events and outbox.** Publishers are auth, parcel, pricing and delivery only (tracking and notification publish nothing and have no outbox). A publisher writes the event to `outbox_events` in the same transaction as the business change, using the shared `EventEnvelope`, `EventTypes`, `EventTopology`, `OutboxService`, `AuditEvents` from common-lib. Never hand-roll these.
- **Consumers** (delivery, tracking, notification): manual ack, prefetch 10, 3 attempts (1 s, then 5 s), then dead-letter. Idempotency differs per service:
  - delivery-service and tracking-service: insert `event_id` into `processed_events` in the same transaction as the handling, and ignore stale `sequence` values;
  - notification-service has **no** `processed_events`: it relies on the unique keys of FR-NTF-004 (`notifications`) and FR-WHK-006 (`webhook_deliveries.event_id`).
  - `outbox_events` exists in auth, parcel, pricing, delivery; `processed_events` in delivery and tracking (SRS 6.1). Copy the definitions exactly.
- **common-core** (pure Java) and **common-lib** (servlet, security, JPA, AMQP) hold the shared code of SRS 3.4: error codes, `DomainException`, `GlobalExceptionHandler`, `RequestIdFilter`, security configuration, `CurrentUser`, `OwnershipPolicy`, `PasswordChangeRequiredFilter`, `InternalKeyFilter`, `InternalRestClientFactory`, `MaskingUtil`, `Pagination`, event classes, JSON logging. Use them; never re-implement them. This includes the token-type rule (`typ=client` is accepted only on `/merchant/orders` and `/merchant/orders/**`, 403 `ACCESS_DENIED` elsewhere): it is implemented once in common-lib, not per service.
- **api-gateway** is reactive, has no database, never depends on common-lib (only common-core), has its own request-id and error handling, and does not validate tokens.
- Auth: only auth-service holds the private key and signs RS256 JWTs. Every other service verifies with the public key only.

## 6. Backend coding rules

**Packages.** Root `com.swiftdrop.<name>` with `<name>` in `gateway, auth, parcel, pricing, delivery, tracking, notification, common`. Inside a shared service use **slice first, layer second**:

`com.swiftdrop.<name>.<slice>.{controller,service,repository,entity,dto,mapper,client,messaging,security,config,scheduler}` (create only what you need).

Slices: auth: `login, google, profile, users, merchant`; parcel: `create, lifecycle, order`; pricing: `core, admin`; delivery: `assignment, courier`; tracking: `core, audit`; notification: `notification, webhook`. Entities and repositories used by more than one slice live in `com.swiftdrop.<name>.domain`, owned by the service's migration lead (section 7.3). Entities used by one slice stay in that slice.

**Layering.** Controllers hold no business logic and never touch repositories or entities. Services are transactional and use repositories. Entities are never serialised: DTOs and MapStruct only. A service never uses another service's classes except common-core and common-lib.

**Validation.** Bean Validation on every input with explicit length, format and range. Unknown JSON properties are rejected (400). Parameterised queries only (JPA, Specifications).

**Authorisation.** Deny by default. `@PreAuthorize` for roles; ownership through the policy classes. Resource owned by someone else: `404 NOT_FOUND`; role not allowed: `403 ACCESS_DENIED`; unauthenticated: 401 (BR-25). The role check runs before the ownership check.

**Errors.** RFC 9457 Problem Details with the codes and statuses of SRS 7.2 only. `detail` is safe (no stack traces, SQL, class names, paths, secrets, personal data). Unexpected errors: `500 INTERNAL_ERROR` with `traceId` only. The token endpoint uses the RFC 6749 format.

**Logging.** Structured JSON on stdout. Never log passwords, tokens, secrets, full emails, full phones, addresses or request bodies containing them; use `MaskingUtil`. Events and logs carry no customer, merchant or recipient contact data (BR-28). The free-text staff note travels only in `parcel.status.changed` and the tracking timeline: never in logs, audit details, notification rows, webhook payloads or the public view.

**Configuration.** Environment variables bound in `application.yml` only. No secrets in code, images, logs or the repo. Fail fast on a missing or weak mandatory secret.

**Database.** Flyway only, in `src/main/resources/db/migration`. The SRS file names are `V1__init.sql`, `V2__...` (SRS 6.1; this overrides the `V1__create_tables.sql` name in WORK_DIVISION). The migration lead writes **all tables of the service from SRS 6.3 in V1, in week 1**. Never edit an applied migration; add the next free number (pricing already uses V2 for the Appendix B seed). Hibernate `ddl-auto=validate`.

**Docker (service owner).** Multi-stage, non-root, `TZ=UTC`, one exposed port, build context `backend/`, `HEALTHCHECK` with `wget` on `/actuator/health/readiness` (gateway: management port 8090). Readiness covers the database only, liveness has no dependencies. Services start in any order (retry database and broker). Prometheus: only the `micrometer-registry-prometheus` dependency and `/actuator/prometheus` (S); no Prometheus or Grafana containers.

**Invariants.** BR-01 to BR-28 are implemented literally and tested. `MAX_PARCEL_WEIGHT_KG` exists only in pricing-service; other code reads `maxWeightKg` from `GET /public/districts`. SpringDoc/Swagger only when `SWAGGER_ENABLED=true`.

---

## 7. Ownership: one file, one owner

You may create or edit files only inside the folders of the member you work for. Everything else is read-only.

### 7.1 Members and folders

| # | Slice | Backend you may edit | Frontend you may edit |
|---|---|---|---|
| 1 | Login and sessions | auth-service `login` slice; **auth-service migration lead** (see 7.3); auth `domain` (User, Merchant, RefreshToken and repositories) | shared foundation: `src/api` client core and `auth.js`, context, layouts, components (incl. `StatusChip`), theme, router and route table, `main.jsx`, `App.jsx`; frontend `Dockerfile`, `nginx.conf`; `pages/auth/login`, `pages/auth/register` |
| 2 | Google login, profile, admin users | auth `google`, `profile`, `users` slices | `pages/auth/callback`, `pages/auth/profile`, `pages/auth/changePassword`, `pages/admin/users`; `src/api/profile.js`, `adminUsers.js` |
| 3 | Merchant onboarding, API access | auth `merchant` slice; **common-core** and **common-lib everything except events/outbox/AuditEvents** (see 7.2) | `pages/merchant/register`, `pages/merchant/credentials`, `pages/admin/merchants`; `src/api/merchantAccount.js`, `adminMerchants.js` |
| 4 | Quote and create delivery | pricing `core` slice; parcel `create` slice; **migration lead for parcel and pricing**, pricing V2 seed | `pages/customer/**`; `src/api/quotes.js`, `customerParcels.js` |
| 5 | Parcel lifecycle and admin | parcel `lifecycle` slice; pricing `admin` slice; tracking `audit` slice (audit-log API only) | `pages/admin/parcels`, `reports`, `zones`, `pricing`, `auditLogs`, `dashboard`; `src/api/adminParcels.js`, `adminReports.js`, `adminPricing.js`, `auditLogs.js` |
| 6 | Merchant orders, receipts | parcel `order` slice; common-lib **events, outbox, AuditEvents** | `pages/merchant/dashboard`, `pages/merchant/orders`; `src/api/merchantOrders.js`, `receipts.js`; `ReceiptButton` component |
| 7 | Dispatcher assignment, gateway | delivery `assignment` slice; **delivery-service migration lead**; delivery `domain`; all of `api-gateway` (and its Dockerfile) | `pages/admin/dispatch` (unassigned queue, `DispatchCard`), `pages/admin/couriers`; `src/api/adminDelivery.js` |
| 8 | Courier operations, infrastructure | delivery `courier` slice; `infrastructure/`, `compose.yaml`, `compose.infra.yaml`, parent `pom.xml`, `.env.example`, `.gitignore`, `.gitattributes`, root `README.md` | `pages/courier/**`; `src/api/courier.js`, `proofs.js`; `ProofViewer` component |
| 9 | Tracking and notifications | tracking `core` slice (incl. the `audit.recorded` consumer and `AuditLog` entity); notification `notification` slice; **migration lead for tracking and notification** | `pages/public/**` (landing `/`, `/track`); `src/api/tracking.js`; `ParcelTimeline` component |
| 10 | Webhooks | notification `webhook` slice; `tools/mock-merchant` (and its Dockerfile) | `pages/merchant/webhook`; `src/api/webhook.js` |

Notes: `compose.infra.yaml` is a local-development convenience file that is not in the SRS; it must not replace `compose.yaml`. The SRS (1.2) puts CI/CD, Prometheus/Grafana dashboards and Kubernetes dashboards out of scope, so WORK_DIVISION items for "CI workflow" and "monitoring" are not to be built without an approved change request.

### 7.2 common-core and common-lib (default split, leader confirms in SRS Appendix E)

- **#3 owns** common-core (ErrorCode, DomainException, all SRS 6.2 enumerations with labels, constants, `MaskingUtil`, `Pagination`, plus `ProblemBody`, `ApiHeaders`, `PhoneNumberNormalizer` and `TrackingNumberFormat`, added here because FR-AUTH-001 / FR-PAR-005 and BR-11 are shared rules, NFR-MNT-003) and the common-lib non-event parts (`GlobalExceptionHandler`, `RequestIdFilter`, security configuration, entry point and denied handler, `CurrentUser`, `OwnershipPolicy`, `PasswordChangeRequiredFilter`, `InternalKeyFilter`, `InternalRestClientFactory`, JSON logging, database start-up retry). Published in week 1.
- **common-lib `pom.xml` belongs to #3**, who pre-declares every dependency the events part needs so #6 never has to edit it. #3 also creates the one `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file listing both auto-configurations, and (bootstrap exception) an empty `com.swiftdrop.common.events.EventsAutoConfiguration` class that #6 then owns and fills.
- **#6 owns** common-lib events: `EventEnvelope`, event data records, `EventTypes`, `EventTopology`, `OutboxEvent`, `OutboxService`, `OutboxPublisher`, consumer helpers, `AuditEvents`. Published in week 1.
- Everyone else treats both as read-only. A change needs a change request agreed by #3 and #6.

### 7.3 Shared files inside a shared service

For auth, parcel, pricing, delivery, tracking and notification, these files belong to the service's **migration lead** (auth #1, parcel #4, pricing #4, delivery #7, tracking #9, notification #9): `pom.xml`, `Dockerfile`, `.dockerignore`, `application.yml`, the main `Application` class, the Flyway migrations, the `domain` package, and the service-wide security configuration class. Other members send a snippet ("Request to owner"). Root-level `compose.yaml`, `compose.infra.yaml`, parent `pom.xml` and `.env.example` are edited only by #8: write the exact snippet for #8.

### 7.4 Frontend rules of ownership

- Shared files (API client core, layouts, shared components, theme, router, route table) are edited only by #1. Other members add only their own `pages/<area>/...` folders and their own `src/api/<file>.js` files from the table above.
- **Routes (SRS 7.1) are built from one route table owned by #1**, resolved with `import.meta.glob('/src/pages/**/index.jsx')` and a "not built yet" fallback, so that a page appears as soon as its owner adds the folder with a default-exported `index.jsx`. Nobody edits the router.
- Page folders per route: `/` and `/track*` → `pages/public`; `/login`, `/register` → `pages/auth/login`, `register`; `/register-merchant` → `pages/merchant/register`; `/auth/callback`, `/change-password`, `/profile` → `pages/auth/...` (#2); `/customer/*` → `pages/customer`; `/merchant/dashboard`, `/merchant/orders*` → `pages/merchant/dashboard`, `orders`; `/merchant/credentials` → `pages/merchant/credentials`; `/merchant/webhook` → `pages/merchant/webhook`; `/courier/*` → `pages/courier`; `/admin/dashboard` → `pages/admin/dashboard` (#5, composing `DispatchCard` from #7 and a pending-merchants card from #3); `/admin/parcels/unassigned` → `pages/admin/dispatch`; `/admin/parcels`, `/admin/parcels/:id` → `pages/admin/parcels`; `/admin/couriers` → `pages/admin/couriers`; `/admin/users` → `pages/admin/users`; `/admin/merchants` → `pages/admin/merchants`; `/admin/zones|pricing|reports|audit-logs` → #5 folders.
- `GoogleLoginButton` (`src/components/GoogleLoginButton.jsx`) is owned by **#2** (the Google flow is #2's); #1's login and register pages only import it.
- Reuse across areas: import the owner's published component or API function (`ParcelTimeline` #9, `ProofViewer` and `proofs.js` #8, `ReceiptButton` and `receipts.js` #6, `StatusChip` #1, `tracking.js` #9). Never copy it; if it is missing, send a Request to owner.

## 8. Cross-slice contracts (what to publish, what to consume)

Code against the SRS contract and the agreed interface. If the provider has not merged yet, mock it in tests (Mockito, WireMock) and tell the member; never copy or recreate the provider's class in your own package.

| Provider | Publishes (early) | Consumers |
|---|---|---|
| #3 | common-core, common-lib security (week 1) | everyone |
| #6 | common-lib events, outbox, AuditEvents (week 1) | #1 to #5, #7, #8 as publishers; #7, #9 as consumers |
| #1 | `JwtService` (sign user and client tokens), `RefreshTokenService` (revoke token, family, all of a user), auth `domain` | #2 (deactivate, reset, change password), #3 (client token, merchant suspend, BR-23) |
| #5 | `ParcelTransitionService`, `ParcelAccessPolicy` (parcel-service) | #4 (owner cancel), #6 (merchant cancel, receipts, internal transition endpoint), #8 (via internal endpoint) |
| #4 | `TrackingNumberGenerator`, `IdempotencyService`, `PricingClient`, parcel creation service and parcel `domain` | #6 (merchant order create: same idempotency and pricing, no second implementation) |
| #7 | delivery `domain` (Delivery, Courier, DeliveryAssignment) with the pessimistic-lock lookup `findByIdForUpdate` | #8 (Flow 3 uses the same lock as assignment) |
| #9 | the one `notification.events` consumer, which calls #10's `WebhookEnqueueService` for webhook rows | #10 |
| #1 (frontend) | API client, auth context, route guards, theme, layouts, `StatusChip`, route table | all frontend members |

Each client lives in the package of the member who needs it (parcel→pricing: #4; parcel→auth: #6; delivery→parcel: #8; auth→delivery active-count: #2; notification→auth/parcel: #9).

---

### 8.1 Agreed signatures (code against these exactly)

Paths: every controller maps its full path (`/api/v1/...` or `/internal/v1/...`); the gateway forwards paths unchanged. Package prefix `com.swiftdrop.`.

**common-lib (#3):** `common.security.SecurityRules` (a bean each service provides: `List<String> publicPaths()` as `"METHOD /api/v1/path"` patterns, `List<String> passwordChangeWhitelist()`); `common.security.JwtPublicKeyProvider` (default reads `JWT_PUBLIC_KEY_PATH`; auth-service overrides it by deriving the public key from its private key); `common.security.CurrentUser` (`userId, role, typ, mid, scopes, passwordChangeRequired`) obtained with `CurrentUsers.get()`; `OwnershipPolicy.isOwnerOrAdmin(user, customerId, merchantId)`; `common.client.InternalRestClientFactory.create(baseUrl)`; test-jar class `common.testsupport.TestTokens` (RSA key pair, tokens for any role, typ, mid, scope, pwc, and a decoder for tests).

**common-lib events (#6):** `common.events.EventTypes` (constants equal to the routing keys, eventType = routing key); `OutboxService.add(String routingKey, Object data)` (must run inside a transaction); `AuditEvents.record(String action, String entityType, String entityId, Map<String,Object> details)` (actor taken from `CurrentUser`; overload with explicit `actorUserId, actorRole` for system actors); `EventTopology` (declarables); `EventConsumerSupport` (manual ack, prefetch 10, 3 attempts with 1 s then 5 s, then reject without requeue); `ProcessedEvents.firstTime(UUID eventId)` (delivery and tracking only).

**auth-service (#1):** `auth.login.service.JwtService.issueUserToken(User, Merchant /*nullable*/)` and `issueClientToken(Merchant, UUID userId, Set<String> scopes)`; `RefreshTokenService`: `IssuedRefresh issue(User)`, `RotationResult rotate(String rawToken)`, `void revokeFamily(String rawToken)`, `void revokeAllForUser(UUID userId)`; `PasswordPolicy.check(String password, String email)` (list of violations); `PasswordHasher`. Phone normalising uses common-core `PhoneNumberNormalizer`.

**parcel-service:**
- #4: `create.service.ParcelCreationService.create(CreationRequest)` returning `CreationResult(int httpStatus, Object body, boolean replay, UUID parcelId)`. `CreationRequest(OwnerType ownerType, UUID ownerId, ParcelInput input, BigDecimal expectedFee /*nullable*/, String merchantReference /*nullable*/, String idempotencyKey /*nullable*/, String requestHash, Supplier<BigDecimal> discountPercentResolver, Function<CreatedParcel,Object> responseBuilder)`. Order inside: idempotency lookup, resolver call (merchant check for #6, 0 for customers), pricing, `expectedFee` check, then one transaction (reference uniqueness, tracking number, parcel, idempotency row with the response body, outbox row). Also `IdempotencyService`, `TrackingNumberGenerator`, `PricingClient`, and `create.mapper.ParcelDetailMapper` producing `ParcelDetailDto`, `AdminParcelDetailDto` (adds `customerId`, `merchantId`) and `InternalParcelDetailDto` (adds `statusSequence`).
- #5: `lifecycle.service.ParcelAccessPolicy.requireAccessible(UUID parcelId, CurrentUser user)` (404 if not owner; ADMIN always); `ParcelTransitionService`: `transition(TransitionCommand)`, `cancelAsOwner(UUID parcelId, CurrentUser user, String reason)`, `cancelAsAdmin(UUID parcelId, CurrentUser admin, String reason)`, `failAsAdmin(UUID parcelId, CurrentUser admin, FailureReason reason, String note)`, all returning `TransitionResult(parcelId, trackingNumber, status, sequence, statusChangedAt)`. `TransitionCommand(parcelId, toStatus, actorUserId, actorRole, note, failureReason, recipientName)`.
- Cancel endpoints of #4 and #6 answer `200 {id, trackingNumber, status, statusChangedAt}`.

**delivery-service (#7):** `delivery.domain.Delivery`, `DeliveryRepository.findByIdForUpdate(UUID)` (`PESSIMISTIC_WRITE`), `findByParcelIdAndCourierIdForUpdate(UUID, UUID)`; `domain.CourierDeliveryQueries.listForCourier(UUID courierId, Scope scope, Pageable)` with `Scope {TODAY, ACTIVE, COMPLETED}` returning `Page<DeliveryListItem>` (sorted by lifecycle status order, then `assigned_at` ascending).

**notification-service (#10):** `webhook.service.WebhookEnqueueService.enqueueStatusChanged(EventEnvelope<ParcelStatusChangedData>)` (no-op unless the event has a `merchantId` and that merchant's config exists and is enabled; idempotent on `event_id`).

**Frontend (all paths under `frontend/src`):** `api/http.js` (#1): `apiRequest(method, path, {body, query, headers, idempotencyKey})`, `apiForm(path, formData)`, `apiBlob(path)`, `ApiError {status, code, detail, errors, traceId}`; `context/AuthContext.jsx`: `useAuth()` returns `{user, status, login, logout, hasRole}`; `components/`: `StatusChip`, `ConfirmDialog`, `LoadingState`, `EmptyState`, `ErrorState`, `PageHeader`, `GoogleLoginButton`; `utils/format.js`: `formatDateTime`, `formatDate`, `formatMoney`, `applyServerErrors(setError, apiError)`; layouts `PublicLayout`, `DashboardLayout`, `CourierLayout`; route table `routes/routeTable.js`. From #9: `pages/public/hooks/useTrackingStream.js` (`useTrackingStream(trackingNumber, {onStatus})`), `pages/public/components/ParcelTimeline.jsx`, `api/tracking.js` (`getPublicTracking`, `getTimeline`). From #8: `pages/courier/components/ProofViewer.jsx`, `api/proofs.js`. From #6: `pages/merchant/components/ReceiptButton.jsx`, `api/receipts.js`. From #7: `pages/admin/dispatch/DispatchCard.jsx`. From #3: `pages/admin/merchants/PendingMerchantsCard.jsx`. From #10: `api/webhook.js` (`getWebhookSummary`).

### 8.2 Other ownership

- `docs/design/<topic>.md` (Mermaid diagrams): each member writes their own files. `docs/design/DESIGN.md`, `docs/test-reports/**`, `docs/contribution-sheet.md`: #5.
- `tools/postman/` (DEP-015): #6. `tools/mock-merchant/`: #10. `.github/CODEOWNERS`: #8.
- Maven wrapper (`backend/mvnw`, `backend/.mvn`): #8; Java Dockerfiles use `eclipse-temurin:21-jdk-alpine` with the wrapper as build stage.

## 9. Frontend rules (SRS 5.8, 7.1, 7.6)

- Access token in memory only (never `localStorage`, `sessionStorage`, cookies). Restore the session at load with `POST /auth/refresh` (always send `X-Requested-With: SwiftDrop`). On `401 TOKEN_EXPIRED` refresh once and retry once, else go to `/login`; one refresh in flight.
- One shared API client in `src/api`, one function per endpoint of SRS 7.3, relative URLs (`VITE_API_BASE_URL` empty in the container build, `http://localhost:8080` for `npm run dev`). No hard-coded hosts.
- Routes exactly as SRS 7.1. Guard by role, hide unusable navigation, redirect `mustChangePassword` users to `/change-password`. The server enforces; the UI only hides.
- Every data view has loading, empty and error states. Show server `errors[]` without losing input. Destructive actions (cancel, deactivate, revoke, reject, fail) need a confirmation dialog.
- Zod mirrors the server's Bean Validation limits, **except the maximum weight, which comes from `maxWeightKg` of `GET /public/districts`** and is never hard-coded.
- New delivery: three steps, quote debounced 400 ms, one `Idempotency-Key` per confirmation screen, Confirm disabled while running, `FEE_CHANGED` shows the new fee and needs a new key.
- Status is always chip with text label (SRS 6.2) and icon. Times in Asia/Colombo, 12-hour clock. Never `dangerouslySetInnerHTML`.
- Receipts and proof photos need the bearer token: fetch through the API client and show via blob URL (`URL.createObjectURL`, revoked when unused) or download. Never in `<img src>`, plain link or `window.open`.
- Pages that combine services compose them in the SPA (SRS 7.6); no service aggregates. Refetch parcel detail, timeline and proof on every SSE status event and polling result. After creation the timeline may be 404 for a few seconds: show an "updating" timeline and retry every 2 s up to 30 s.
- Public tracking: `/track`, `/track/:trackingNumber`; `EventSource`, close after a terminal status, polling fallback of SRS 3.8, `aria-live`, works at 360 px without horizontal scroll; unknown and malformed numbers show the same not-found message.
- Courier UI is mobile-first, single column, touch targets at least 44 x 44 px, `<input type="file" accept="image/*" capture="environment">`, phones as `tel:` links, 30 s refresh and refresh on focus; note field hint "Do not enter customer or recipient contact details". Offer only `allowedNextStatuses` and `allowedFailureReasons` from the API.
- WCAG 2.1 AA basics: labelled inputs, keyboard use, visible focus, contrast.

## 10. UI look and feel (colours, type, spacing only)

The UI Design System styles screens that the SRS already defines. **It never adds features.** Do not build what the SRS excludes even if the design document shows it:

- merchant "Create order" form or button (orders are API only); maps, courier markers, pins; ETA / delivery-time columns; report charts; push notifications or a notification bell; top-bar global search; courier capacity indicators or amber "near capacity" states; dashboard metrics that no endpoint provides; multi-language;
- courier actions named "Start Delivery" or "Arrived": they are not statuses. Courier buttons come from `allowedNextStatuses` (Picked up, In transit, Out for delivery, Delivered) and "Report failed delivery" with a required reason;
- "rotate" or "copy" for API credentials: the secret is shown once at creation and credentials are only created and revoked. Webhook secret rotation does exist (`POST /merchant/webhook/rotate-secret`).

Tokens live in the single shared MUI theme (owned by #1); pages reference tokens and never hard-code colours.

- Primary orange `#F97316` (hover `#EA580C`, pressed `#C2410C`); navy `#172033`; soft orange `#FFF7ED`; page `#F8FAFC`; surface `#FFFFFF`; text `#0F172A`; muted text `#64748B`; border `#E2E8F0`; muted surface `#F1F5F9`. Yellow `#FBBF24` only as a small highlight, never as text on white. Destructive red `#DC2626` (hover `#B91C1C`).
- Status chips (soft background / text): CREATED `#F1F5F9`/`#64748B`; PICKED_UP `#EFF6FF`/`#2563EB`; IN_TRANSIT `#F5F3FF`/`#7C3AED`; OUT_FOR_DELIVERY `#FFF7ED`/`#EA580C`; DELIVERED `#F0FDF4`/`#16A34A`; FAILED `#FEF2F2`/`#DC2626`; CANCELLED `#F1F5F9`/`#475569` (not in the design document; neutral slate). Label plus icon, never colour alone.
- Orange is for primary actions, the active navigation item and the OUT_FOR_DELIVERY chip. Delivered is green; Failed and destructive actions are red.
- Buttons 44 to 48 px (courier primary 48 to 56 px), radius 8 to 12 px, states as in the design system, 3 px orange focus ring. Cards radius 12 to 16 px with a 1 px border. Inputs 44 to 48 px with a visible label above.
- Transitions 150 to 200 ms; respect `prefers-reduced-motion`.
- Font Inter, bundled locally. The CSP is `default-src 'self'`: no external fonts, scripts or styles.
- Desktop dashboards: navy sidebar with orange active item; customer site: white header. Breakpoints: mobile up to 639 px, tablet 640 to 1023 px, desktop 1024 px and up. Minimum viewport 360 px.

## 11. Testing rules (SRS section 12)

- Tests are part of the task. Write the tests listed in the task prompt. Test IDs cannot be method names, so use `@DisplayName("TST-021 FR-PAR-006: ...")` plus `@Tag("TST-021")` in Java and `describe("TST-040 ...")` in Vitest; reference BR/FR IDs in the name.
- Backend: JUnit 5, Mockito, AssertJ, Spring Security Test, Testcontainers. Other services are tested against WireMock stubs of the SRS 7.4 contracts, never against another member's code.
- Every endpoint group needs authorisation tests: 401, 403, and 404 for someone else's resource. `TST-033` spans all services: each member covers their own endpoints, nobody writes tests in another member's folder.
- Deterministic and independent: no `Thread.sleep`, use Awaitility.
- JaCoCo line coverage at least 70 % on service, messaging and security packages (DTOs, entities, config, generated code excluded); `mvn verify` fails below it. Frontend at least 60 % (S).
- One command per module: `mvn verify`, `npm test -- --coverage`.
- Never weaken, skip or delete a test to make a build pass. Fix the code or tell the member.

## 12. Git, commits and safety

- Branch `feature/<service>-<what>`. One pull request per task. Never push to `main`; never force-push. **Each member merges their own pull request** (squash) after: `git pull --rebase origin main`, the build and tests pass again on the rebased branch, and `git diff --name-only` shows only files you own. A pull request that touches a shared file (7.3, 7.4, `common-core`, `common-lib`, compose files, parent pom) needs a written OK from that file's owner in the PR before merging. After merging, check your own part on `main` and announce the hand-off in the group chat.
- Conventional Commits; the PR description lists the requirement IDs implemented and the files outside your slice you touched (there should be none).
- Code, tests and documentation changes go in the same pull request. Do not reformat or refactor files outside your task.
- Stage files by explicit path (no `git add -A` or `git add .`). Before every commit run `git status` and `git diff --name-only` and confirm that every path is inside your ownership (section 7). If not, unstage it.
- Never commit `.env`, `infrastructure/keys/*.pem`, secrets or build output. `.env.example` has placeholders only.
- Do not run destructive commands (`rm -rf` outside your folder, `docker system prune`, `docker compose down -v`, dropping databases, editing git config or history) without the member's explicit approval.

## 13. How you work (every task)

1. **Read** the SRS sections for your IDs (section 1), then the existing repo files you will touch.
2. **Plan first.** Reply with a short plan: requirement IDs, files to create or edit (all inside your ownership), endpoints, events, tables, contracts you consume or publish (section 8), and questions. Wait for approval if the task is large.
3. **Build in small steps**, one requirement group at a time, each with its tests. Never generate the whole slice in one go. Order: Must items, then (S) items.
4. **Stay in scope.** No extra features, endpoints, fields or "improvements". Respect the out-of-scope list (SRS 1.2) and the cut line (SRS 1.6).
5. **Stop and ask** when something is missing, unclear or contradictory. Report SRS errors; never work around them silently.
6. **Request to owner** (for files you do not own): *What / Why / Which file / Proposed snippet or signature.*
7. **Change request** (for SRS content): *Change / Reason / Affected IDs / Affected services.* It needs approval from the group leader and the owner of each affected service. Never change the SRS, a rule, an interface, an event or a data definition yourself.
8. **Self-check before finishing:** every ID in the task is implemented or listed as not done; every changed path is yours; no hard-coded secrets, hosts, colours or `MAX_PARCEL_WEIGHT_KG`; no personal data in logs or events; tests pass with the single module command.
9. **Finish with a report:** requirement IDs implemented; files created or changed; tests added and how to run them; not done or blocked; requests to other owners, to #8 or to #1.

## 14. Never do these

- Edit another member's folder, an applied migration, or a shared file you do not own.
- Re-implement something that exists in common-core or common-lib, or a second idempotency, ownership or transition implementation.
- Read another service's database, or add a service-to-service call that is not in SRS 3.3.
- Store tokens in browser storage, log personal data or secrets, or hard-code secrets.
- Use floating point for money, or put `MAX_PARCEL_WEIGHT_KG` outside pricing-service.
- Skip, weaken or delete tests; skip authorisation checks; trust the UI to enforce access.
- Add CI/CD, Prometheus/Grafana, autoscaling, payments, maps, GPS, ETAs, push notifications or any other out-of-scope item unless an approved change request has changed the SRS.
