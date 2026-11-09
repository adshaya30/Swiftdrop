# MEMBER #3: Merchant onboarding, API access and common-lib (3 prompts)

The master prompt (v3.0) and the SRS are already loaded in your session.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | common-core + common-lib (non-event parts) | **Yes: everyone** (do it first, in 3 pull requests) |
| 2 | auth-service merchant slice (backend) | No |
| 3 | Merchant and admin-merchant frontend, diagram | No |

---

## PROMPT 1 of 3: common-core and common-lib (shared foundation)

You are **Member #3**. You own `backend/common-core` and `backend/common-lib` except the events part (#6). Everyone builds on this, so deliver **three small pull requests** and merge each immediately after it is green. Do not wait for review beyond the minimum.

**Preconditions:** #8 has merged the repo skeleton and parent pom (modules `common-core` and `common-lib` exist as empty poms; you own their `pom.xml` from now on).

### PR 1: common-core (pure Java, no Spring MVC, servlet, JPA or AMQP; the gateway uses it too)
- **`ErrorCode` enum** with HTTP status as a number, all codes of SRS 7.2: `VALIDATION_ERROR` 400; `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `INVALID_CREDENTIALS` 401; `ACCESS_DENIED`, `PASSWORD_CHANGE_REQUIRED`, `ACCOUNT_DEACTIVATED`, `MERCHANT_PENDING`, `MERCHANT_REJECTED`, `MERCHANT_SUSPENDED`, `MERCHANT_NOT_ACTIVE` 403; `NOT_FOUND` 404; `EMAIL_ALREADY_REGISTERED`, `FEE_CHANGED`, `INVALID_STATUS_TRANSITION`, `PARCEL_NOT_ASSIGNABLE`, `DUPLICATE_MERCHANT_REFERENCE`, `CONCURRENT_UPDATE`, `COURIER_HAS_ACTIVE_DELIVERIES`, `CREDENTIAL_LIMIT_REACHED`, `ZONE_IN_USE` 409; `PAYLOAD_TOO_LARGE` 413; `UNSUPPORTED_MEDIA_TYPE` 415; `IDEMPOTENCY_KEY_REUSED`, `PRICING_NOT_CONFIGURED`, `ZONE_NOT_SERVICEABLE` 422; `ACCOUNT_LOCKED`, `RATE_LIMITED` 429; `INTERNAL_ERROR` 500; `SERVICE_UNAVAILABLE`, `ASSISTANT_UNAVAILABLE` 503. Each has a default title.
- **`DomainException`** (`ErrorCode`, safe `detail`, optional `List<FieldError(field, message)>`, optional `Map<String,Object> extensions` for extra response members such as the new fee of `FEE_CHANGED`).
- **`ProblemBody`** (record: `type` = `https://swiftdrop.example/errors/<code>`, `title`, `status`, `detail`, `code`, `traceId`, `timestamp`, `errors[]`, extensions): the RFC 9457 body shared by common-lib and the gateway.
- **Enumerations of SRS 6.2** as enums with labels where given: `Role`, `UserStatus`, `AuthProvider`, `MerchantStatus`, `ParcelStatus` (with labels "Created", "Picked up", "In transit", "Out for delivery", "Delivered", "Delivery failed", "Cancelled", `isTerminal()`), `ParcelSize`, `VehicleType`, `OwnerType`, `FailureReason` (with the seven labels), `ActorRole` (incl. `SYSTEM`), `CredentialStatus`, `NotificationChannel`, `RecipientKind`, `NotificationStatus`, `WebhookDeliveryStatus`. Also the Table 2.4 helper `FailureReason.allowedFor(ParcelStatus before, boolean adminOverride)`.
- **`ApiHeaders`** constants (`X-Request-Id`, `X-Internal-Key`, `X-Requested-With`, `Idempotency-Key`, `Idempotent-Replay`, `Retry-After`).
- **`MaskingUtil`** (`+94*****1234`, `j***@example.com`; null safe), **`Pagination`** DTO (`items, page, size, totalItems, totalPages`).
- **`PhoneNumberNormalizer`**: accepts `07XXXXXXXX`, `+947XXXXXXXX`, `947XXXXXXXX` and landline forms (spaces, dashes and brackets stripped), returns E.164 matching `^\+94[1-9][0-9]{8}$` or empty.
- **`TrackingNumberFormat`**: regex `^SD-[0-9A-HJKMNP-TV-Z]{10}$`, alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ`, `isValid`.
- Unit tests for everything (coverage at least 70 %), including enum labels, error code statuses, masking, phone forms, tracking-number regex.

### PR 2: common-lib security and web (servlet services only; the gateway never uses it)
Pre-declare in the common-lib `pom.xml` **every dependency the events part (#6) will need** so #6 never edits it: `spring-boot-starter-web`, `security`, `oauth2-resource-server`, `data-jpa`, `validation`, `amqp`, `spring-retry`, `jackson`, `micrometer`, plus test dependencies (`spring-boot-starter-test`, `spring-security-test`, `testcontainers` junit/postgresql/rabbitmq, `awaitility`, `wiremock`) and the Maven `test-jar` goal. Create `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` listing `com.swiftdrop.common.security.SecurityAutoConfiguration` and `com.swiftdrop.common.events.EventsAutoConfiguration`, plus an **empty** `EventsAutoConfiguration` class (bootstrap exception: #6 owns and fills it). Adding the dependency must be enough for a service to get everything below (no per-service wiring except the beans named here).

Implement (packages `com.swiftdrop.common.security|web|client|logging`):
1. **JWT verification:** resource server with `NimbusJwtDecoder` using the public key from `JwtPublicKeyProvider` (default reads `JWT_PUBLIC_KEY_PATH`; accepts the `BEGIN PUBLIC KEY` X.509 PEM; overridable by auth-service); validates `iss=swiftdrop`, `aud=swiftdrop-api`, `exp` with 30 s skew, RS256 only. Maps `role` to authority `ROLE_<role>` and each scope to `SCOPE_<scope>`.
2. **`CurrentUser`** (`userId, role, typ, mid, scopes, passwordChangeRequired`) with `CurrentUsers.get()`; **`OwnershipPolicy.isOwnerOrAdmin(user, customerId, merchantId)`** (CUSTOMER if `customerId = userId`, MERCHANT if `merchantId = mid`, ADMIN always).
3. **`SecurityRules`** bean contract (master 8.1) and the default `SecurityFilterChain`: deny by default, `permitAll` for the service's `publicPaths()` and `/actuator/health/**`, method security on (`@PreAuthorize`), stateless, CSRF off for the bearer API, no sessions.
4. **Token-type rule, implemented once:** a `typ=client` token is accepted only on `/api/v1/merchant/orders` and `/api/v1/merchant/orders/**`, anywhere else 403 `ACCESS_DENIED`.
5. **`PasswordChangeRequiredFilter`:** `pwc=true` → 403 `PASSWORD_CHANGE_REQUIRED` on every authenticated endpoint except the service's `passwordChangeWhitelist()`.
6. **`InternalKeyFilter`** (own filter chain for `/internal/**`): header `X-Internal-Key` compared in constant time (`MessageDigest.isEqual`) with `INTERNAL_API_KEY`; missing or wrong → 401 `UNAUTHENTICATED`; JWT is not used there. **Fail fast at startup** if `INTERNAL_API_KEY` is under 32 characters or is a placeholder (`CHANGE_ME…`, `changeme`, `secret`, `password`, empty).
7. **`AuthenticationEntryPoint` and `AccessDeniedHandler`** writing `ProblemBody` as `application/problem+json`: expired token → 401 `TOKEN_EXPIRED`; missing or invalid token → 401 `UNAUTHENTICATED`; role, token type or scope not allowed → 403 `ACCESS_DENIED`. The filters above write their errors the same way.
8. **`GlobalExceptionHandler`** (`@RestControllerAdvice`): `DomainException` → its status and code; Bean Validation and binding errors → 400 `VALIDATION_ERROR` with `errors[{field, message}]`; unknown JSON property, unreadable body, type mismatch → 400 `VALIDATION_ERROR`; no handler found → 404 `NOT_FOUND`; method not allowed; `MaxUploadSizeExceededException` and oversized bodies → 413 `PAYLOAD_TOO_LARGE`; unsupported media type → 415 `UNSUPPORTED_MEDIA_TYPE`; `ObjectOptimisticLockingFailureException` → 409 `CONCURRENT_UPDATE`; `AccessDeniedException` → 403; anything else → 500 `INTERNAL_ERROR` with only `traceId` and a generic detail (log the exception with the request id, never return it). `detail` is always safe and contains no personal data.
9. **`RequestIdFilter`** (reads or creates `X-Request-Id`, MDC `requestId`, returns it on the response) and a request-id propagation interceptor.
10. **`InternalRestClientFactory.create(baseUrl)`:** Spring `RestClient` with 5 s connect and 10 s read timeouts, `X-Internal-Key`, `X-Request-Id` propagation; response handling: a callee Problem Details body with a business code is rethrown as a `DomainException` with the **same** code, status and detail; connection failures, timeouts and 5xx become 503 `SERVICE_UNAVAILABLE`.
11. **JSON logging configuration:** Spring Boot structured logging (`logstash` format) with fields `timestamp, level, service name, logger, message, requestId`; level from `LOG_LEVEL`.
12. **Database start-up retry:** a `FlywayMigrationStrategy` that retries migration with back-off for at least 90 s, and Hikari `initializationFailTimeout` so services start in any order (NFR-REL-003); readiness must not depend on the broker.
13. **Test support (test-jar):** `common.testsupport.TestTokens` generating an RSA key pair and signing tokens for any role, `typ`, `mid`, scopes, `pwc`, expired or with a wrong audience, plus a test decoder/`JwtPublicKeyProvider` so every member can write authorisation tests without auth-service.
14. Tests (**TST-013 shared part** and authorisation basics) with `@WebMvcTest`/`SpringBootTest` and a tiny test controller: expired → `TOKEN_EXPIRED`, tampered, wrong audience, wrong issuer, token signed with another key → 401, client token on non-order endpoints → 403 and on `/merchant/orders` → allowed, `pwc` whitelist, internal key missing/wrong/right and startup failure for a weak key, error bodies have no stack trace or class names, `FEE_CHANGED`-style extensions are serialised, optimistic lock → 409, upload too large → 413, no handler → 404, request id returned and in MDC, constant-time compare used, `InternalRestClientFactory` error translation with WireMock. Coverage at least 70 % for the non-event parts.

### PR 3: hardening and handover
Add a short `backend/common-lib/README.md` (how to use `SecurityRules`, `TestTokens`, `InternalRestClientFactory`, error handling), verify that a bare service can start with only the dependency plus a `SecurityRules` bean, and tell #6 in one line that `EventsAutoConfiguration` is theirs.

### Done when
All three PRs merged, `mvn -pl backend/common-core,backend/common-lib -am verify` green, report sent. **This is the first thing the team needs: merge PR 1 within the first day.**

---

## PROMPT 2 of 3: auth-service merchant slice (backend)

You are **Member #3**. Package `com.swiftdrop.auth.merchant.{controller,service,repository,entity,dto,mapper,client,security}`. `application.yml`, pom and V1 are #1's.

**Preconditions:** #1 Prompt 1 merged (`User`, `Merchant`, `JwtService.issueClientToken`, `RefreshTokenService`, `PasswordPolicy`, `PasswordHasher`); your own common-lib; #6 outbox (`OutboxService`, `AuditEvents`, merged; mock until then).

### Requirements
- **FR-AUTH-016** `POST /api/v1/auth/register-merchant` (public): `businessName` (2-120), `businessRegistrationNo?` (up to 40), `contactName` (2-100, stored as the user's full name), `email`, `phone`, `password` (policy), `addressLine1` (1-150), `city` (1-80). Creates a MERCHANT user (ACTIVE) and a merchant with status `PENDING`, in one transaction. 201 with a "pending approval" message and no tokens. Duplicate email → 409 `EMAIL_ALREADY_REGISTERED`.
- **FR-AUTH-017** (ADMIN): `GET /api/v1/admin/merchants` (filters `status`, `q` = business name prefix; paginated), `GET /api/v1/admin/merchants/{id}`, `POST .../approve` (from PENDING or REJECTED), `POST .../reject {reason}` (from PENDING), `POST .../suspend {reason}` (from APPROVED), `POST .../reinstate` (from SUSPENDED), `PATCH .../discount {discountPercent}` (0.00-50.00, two decimals). Reject and suspend need a reason of 10-300 characters. Any other transition → 409 `INVALID_STATUS_TRANSITION` (BR-12). Set `status_reason`, `decided_at`, `decided_by`. **Every status action** writes a `merchant.status.changed` event (`merchantId, businessName, previousStatus, status, reason?`) and an audit event in the same transaction (actions `MERCHANT_APPROVED`, `MERCHANT_REJECTED`, `MERCHANT_SUSPENDED`, `MERCHANT_REINSTATED`, `MERCHANT_DISCOUNT_CHANGED`). Suspending revokes all refresh tokens of the merchant's user (BR-23). Admin detail returns merchant fields, contact name, email, phone, address, status, reason, discount, dates.
- **FR-AUTH-018** API credentials (role MERCHANT, dashboard login only: `typ=client` already gets 403 from common-lib): `POST /api/v1/merchant/credentials {label 1-50}` creates `client_id` = `sdc_` + 24 random chars and `client_secret` = `sds_` + 43 URL-safe chars from 32 random bytes; the secret is returned **once** and only its SHA-256 hash is stored; `GET /api/v1/merchant/credentials` lists `id, label, status, createdAt, lastUsedAt` (+ `clientId`, never the secret); `DELETE /api/v1/merchant/credentials/{id}` revokes (own credential only, otherwise 404; revoking an already revoked one answers 204). At most **2 ACTIVE** credentials: the third → 409 `CREDENTIAL_LIMIT_REACHED` (lock the merchant row to be race safe). Create and revoke are audited (`CREDENTIAL_CREATED`, `CREDENTIAL_REVOKED`).
- **FR-AUTH-019** `POST /api/v1/oauth/token` (public, `application/x-www-form-urlencoded`): `grant_type=client_credentials`, `client_id`, `client_secret` (HTTP Basic also accepted), optional `scope` (subset of `orders:read orders:write`, default both). Success `{access_token, token_type:"Bearer", expires_in:900, scope}` where the JWT is `typ=client`, `role=MERCHANT`, `sub` = the merchant's **user id**, `mid` = merchant id, `scope`. Errors in RFC 6749 format `{error, error_description}`: `invalid_client` 401; `invalid_request`, `unsupported_grant_type`, `invalid_scope` 400. A merchant that is not APPROVED, a DEACTIVATED merchant user and a revoked credential all give `invalid_client`. Compare secret hashes in **constant time** (`MessageDigest.isEqual`, do a dummy compare for unknown client ids), update `last_used_at`, answer with `Cache-Control: no-store`.
- **FR-AUTH-022 internal endpoints** (`X-Internal-Key`, never through the gateway): `GET /internal/v1/users/{id}/contact` → `{id, fullName, email, phone}`; `GET /internal/v1/merchants/{merchantId}` → `{id, userId, businessName, status, userStatus, discountPercent, contactEmail}`; unknown → 404.
- **FR-AUTH-024** `GET /api/v1/admin/dashboard/merchants` → `{pendingMerchants}`.
- **FR-AUTH-023** details of audit events never contain passwords, secrets or contact data.
- Entities in your slice: `MerchantCredential`. Everything through DTOs and MapStruct, OpenAPI annotations, `DomainException` errors.

### Tests (JUnit 5, Testcontainers, Spring Security Test, TestTokens)
- Merchant lifecycle: every allowed transition and every disallowed one (BR-12), reasons 10-300, discount bounds, events and audit rows written in the same transaction, refresh tokens revoked on suspend (**TST-029 merchant lifecycle**).
- **TST-013 constant-time secret check** and credential generation formats (prefixes, lengths, URL-safe), secret shown once and never stored, limit of 2 with a concurrent create race.
- **TST-032 (auth part):** a `typ=client` token gets 403 on `/merchant/credentials`, `/me`, `/admin/*`; pending, rejected and suspended merchants get `invalid_client` at the token endpoint and are refused at login; token claims (`typ`, `mid`, `scope`, `sub` = user id); wrong secret `invalid_client`; scope subset and `invalid_scope`; Basic auth accepted; revoked credential refused.
- **TST-033** for your endpoints (401, 403 for other roles on `/admin/merchants` and `/merchant/credentials`, internal key missing/wrong).
- AT-12, AT-13 checked. Coverage at least 70 %.

### Done when
All endpoints work, `mvn -pl backend/auth-service -am verify` green, report sent.

---

## PROMPT 3 of 3: frontend (merchant + admin merchants) and diagram

You are **Member #3**. Folders: `frontend/src/pages/merchant/register`, `pages/merchant/credentials`, `pages/admin/merchants`; API files `src/api/merchantAccount.js`, `src/api/adminMerchants.js`. Use only the shared client, components and layouts of #1 (master 8.1).

1. **`/register-merchant`** (`pages/merchant/register`): form with Zod mirroring FR-AUTH-016 limits (businessName 2-120, registration no up to 40 optional, contactName 2-100, email, phone, password with live policy hints, addressLine1 1-150, city 1-80); server `errors[]` shown without clearing input; success page "Your registration is waiting for admin approval" (no login attempt).
2. **`/merchant/credentials`** (`pages/merchant/credentials`, MERCHANT dashboard): list `clientId`, label, status chip (ACTIVE/REVOKED with text), created and last used (Asia/Colombo); **create** dialog (label 1-50) → shows `client_id` and the `client_secret` **once** with a copy button and a clear "you will not see this again" warning, secret kept only in component state and cleared when the dialog closes; **revoke** with `ConfirmDialog`; when 2 credentials are active, the create button explains the limit and handles `CREDENTIAL_LIMIT_REACHED`. A short note explains the API-only order flow and links to the token endpoint example (`POST /api/v1/oauth/token`). No "rotate" and no "copy later" (SRS has neither).
3. **`/admin/merchants`** (`pages/admin/merchants`, ADMIN): table with status filter, name-prefix search, pagination; detail drawer or page with all merchant data; actions by status: **approve** (PENDING, REJECTED), **reject** (reason 10-300), **suspend** (reason 10-300), **reinstate** (SUSPENDED), each through `ConfirmDialog` (reason inside the dialog with Zod); **discount** edit (0-50, two decimals); status shown as chip with text; handle `INVALID_STATUS_TRANSITION` by refetching.
4. **`pages/admin/merchants/PendingMerchantsCard.jsx`** (named export and default): dashboard card calling `GET /admin/dashboard/merchants` with its own loading/error state and a link to `/admin/merchants?status=PENDING`; #5 imports it into the admin dashboard.
5. **API files** with one function per endpoint: `merchantAccount.js` (`registerMerchant`, `listCredentials`, `createCredential`, `revokeCredential`), `adminMerchants.js` (list, get, approve, reject, suspend, reinstate, setDiscount, pendingCount).
6. **Tests** (`describe("TST-040 ...")`): registration validation and server errors, the secret is shown once and cleared, revoke needs confirmation, limit message, admin actions with reasons, pending card states. Check keyboard use, labels, focus, 360 px.

### Docs
`docs/design/merchant-use-cases.md`: Mermaid use-case style diagram for the merchant actor (UC-M01 to UC-M10 of SRS 4.1, including alternative flows such as pending, rejected, suspended).

### Done when
Pages work against the running service, `npm test -- --coverage` and `npm run build` pass, report sent.
