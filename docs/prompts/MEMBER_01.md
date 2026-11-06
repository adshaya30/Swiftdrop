# MEMBER #1: Login and sessions (3 prompts)

Paste each prompt into your AI session when its turn comes. The master prompt (v2.2) and the SRS are already loaded in your session.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | auth-service foundation | **Yes**: #2 and #3 need the skeleton, V1, domain, JwtService, RefreshTokenService |
| 2 | SPA foundation + login/register pages | **Yes**: every frontend member needs the shared client, auth context, layouts, route table |
| 3 | Login API, sessions, tests and docs | No |

---

## PROMPT 1 of 3: auth-service foundation

You are **Member #1**. Follow the master prompt. This prompt builds the base of auth-service that #2 and #3 depend on. Deliver it as **two small pull requests** and merge each as early as possible.

**Preconditions:** #8 has merged the repo skeleton and parent pom; #3 has merged common-core (and later common-lib security). If common-lib is not merged yet, build everything that does not need it, and code against the signatures in master 8.1.

### PR A: skeleton, migration, domain (merge first)

1. `backend/auth-service` module: `pom.xml` (parent `backend/pom.xml`; web, security, oauth2-resource-server, data-jpa, validation, amqp, actuator, flyway-core + flyway-database-postgresql, postgresql, springdoc-openapi, mapstruct + processor, micrometer-registry-prometheus, common-core, common-lib; test: starter-test, spring-security-test, testcontainers junit/postgresql/rabbitmq, wiremock, awaitility; layered-jar plugin), `AuthServiceApplication` (`@EnableScheduling`), `Dockerfile` and `.dockerignore` (multi-stage via Maven wrapper, non-root, `TZ=UTC`, `-Duser.timezone=UTC`, port 8081, HEALTHCHECK `wget` on `/actuator/health/readiness`).
2. `application.yml`: bind **every** auth-service variable of SRS 9.2 with the listed defaults: `SPRING_PROFILES_ACTIVE, LOG_LEVEL, INTERNAL_API_KEY, SWAGGER_ENABLED, DB_URL, DB_USER, DB_PASSWORD, JWT_PUBLIC_KEY_PATH, RABBITMQ_*, OUTBOX_POLL_INTERVAL_MS, JWT_PRIVATE_KEY_PATH, JWT_ACCESS_TTL_MINUTES, REFRESH_TTL_DAYS, REFRESH_ABSOLUTE_TTL_DAYS, REFRESH_GRACE_SECONDS, COOKIE_SECURE, PUBLIC_BASE_URL, DELIVERY_SERVICE_URL, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GOOGLE_REDIRECT_URI, BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_NAME, BOOTSTRAP_ADMIN_PASSWORD, DEMO_SEED_ENABLED, DEMO_USER_PASSWORD`. `server.port=8081`, graceful shutdown 30 s, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jackson.deserialization.fail-on-unknown-properties=true`, Flyway on, readiness group = database only, liveness without dependencies, broker health indicator off, Swagger only when `SWAGGER_ENABLED=true`, Prometheus endpoint exposed (S), structured JSON logging. (#2 and #3 add no yml; they send you snippets.)
3. `SecurityRules` bean (master 8.1) with full paths. Public: `POST /api/v1/auth/register`, `/auth/register-merchant`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `GET /api/v1/auth/google/login`, `/auth/google/callback`, `/auth/config`, `POST /api/v1/oauth/token`. Password-change whitelist (FR-AUTH-014): `GET /api/v1/me`, `POST /api/v1/me/change-password`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`.
4. `db/migration/V1__init.sql` (SRS 6.1 name): **all** auth tables exactly per SRS 6.3: `users` (email unique lower-case, `google_sub` unique when not null, role/status/auth_provider CHECKs, defaults, `failed_login_count`, `locked_until`, `last_login_at`, `version`), `refresh_tokens` (`token_hash CHAR(64)` unique, indexes on `user_id`, `family_id`), `oauth_login_states`, `courier_profiles`, `merchants` (`discount_percent NUMERIC(4,2)` CHECK 0-50, unique `user_id`), `merchant_credentials` (`client_id` unique), `outbox_events` (shared definition, partial index on `created_at where published_at is null`). Enum columns are `VARCHAR` with CHECK lists from SRS 6.2.
5. `auth.domain`: entities `User`, `Merchant` (both `@Version`), `RefreshToken` and Spring Data repositories (`findByEmail`, `findByGoogleSub`, `existsByRole`, `MerchantRepository.findByUserId`, token lookup by hash with `PESSIMISTIC_WRITE` variant, bulk revoke by user and by family). Use common-core enums. `OauthLoginState`, `CourierProfile`, `MerchantCredential` entities are written by #2 and #3 in their own slices.
6. Test: Flyway applies V1 on Testcontainers PostgreSQL and `ddl-auto=validate` passes; context starts.

### PR B: token and password foundations (merge second)

1. **Key loading:** read the PKCS#8 PEM private key from `JWT_PRIVATE_KEY_PATH` (RSA at least 2048 bit), derive the public key from the `RSAPrivateCrtKey` (modulus + public exponent) and expose it as the auth-service `JwtPublicKeyProvider`, because compose mounts only the private key into auth-service. Missing, unreadable or non-RSA key: fail fast with a clear message (FR-AUTH-006, NFR-SEC-010).
2. **`JwtService`** (signatures in master 8.1): RS256, header `kid=swiftdrop1`. Claims exactly (SRS 3.6): `sub` (user id), `role`, `typ` (`user` or `client`), `mid` (only for role MERCHANT, in both types), `scope` (client tokens only, space-separated), `pwc=true` (only while `must_change_password`), `iss=swiftdrop`, `aud=swiftdrop-api`, `iat`, `exp` (`JWT_ACCESS_TTL_MINUTES`, default 15), `jti`. No email, phone or name.
3. **`RefreshTokenService`** (FR-AUTH-007): token = 256-bit random value, base64url; only the SHA-256 hex hash is stored with `family_id`; `expires_at` = created + `REFRESH_TTL_DAYS`, never beyond `family_created_at` + `REFRESH_ABSOLUTE_TTL_DAYS`. `rotate(raw)` locks the row (`PESSIMISTIC_WRITE`) and: unknown, expired or over-age family → 401 `UNAUTHENTICATED`; revoked → revoke the whole family, 401; used more than `REFRESH_GRACE_SECONDS` ago → revoke the family, 401; used within the grace period → return a new access token **without** a new refresh token (no new cookie); fresh → mark used, issue a new token in the same family. A deactivated user or a merchant that is not APPROVED → revoke the family, 401. Plus `revokeFamily`, `revokeAllForUser`. A `RefreshCookieFactory` builds `sd_refresh` (HttpOnly, `Secure` when `COOKIE_SECURE=true`, SameSite=Strict, path `/api/v1/auth`, Max-Age 7 days) and the clearing cookie.
4. **`PasswordPolicy`** (FR-AUTH-002: 10-64 chars, at least one upper, lower, digit and special character, must not contain the email local part, case-insensitive) returning all violations, and **`PasswordHasher`** (BCrypt cost 12; never log or return hashes).
5. Tests (Testcontainers PostgreSQL where a database is needed): **TST-013** JWT create/validate with the key pair (claims exactly as above, expired, wrong audience, wrong issuer, tampered, signed with another key), refresh rotation, reuse detection revoking the family, grace-period behaviour (just inside and just outside), family cap, deactivated user, password policy cases, BCrypt cost, missing-key startup failure.

### Done when
Both PRs merged, `mvn -pl backend/auth-service -am verify` green, #2 and #3 can compile against `JwtService`, `RefreshTokenService`, `PasswordPolicy` and the domain package. Send the report (master 13.9) and tell #2/#3 in one line that the base is merged.

---

## PROMPT 2 of 3: SPA foundation + login and register pages

You are **Member #1**. The frontend foundation unblocks all other frontend members, so deliver it in **two pull requests**: PR A (client, auth context, route table, theme, layouts, shared components) first, PR B (pages, Docker, tests) second.

**Preconditions:** repo skeleton from #8. The existing frontend has `App.jsx`, `main.jsx` (SRS 3.2). Stay on JavaScript (JSX), Vite, React Router, TanStack Query, MUI, React Hook Form + Zod, Vitest + Testing Library.

### PR A: foundation

1. **Dependencies:** `@mui/material`, `@mui/icons-material`, `@emotion/react`, `@emotion/styled`, `@fontsource/inter`, `@tanstack/react-query`, `react-router-dom`, `react-hook-form`, `@hookform/resolvers`, `zod`; dev: `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`. Scripts: `dev`, `build`, `test` (`vitest run`), coverage (`npm test -- --coverage`, add `@vitest/coverage-v8`). `VITE_API_BASE_URL` read from `import.meta.env` (empty by default, `.env.development` sets `http://localhost:8080`).
2. **`src/api/http.js`** (exact exports in master 8.1): token held in a module variable only (never `localStorage`, `sessionStorage` or cookies); `apiRequest(method, path, {body, query, headers, idempotencyKey})` adds `Authorization: Bearer`, JSON, optional `Idempotency-Key`; `apiForm` for multipart (no manual content type); `apiBlob` returns a Blob using the token (FR-UI-011); `ApiError` with `status`, `code`, `detail`, `errors[]`, `traceId`, parsed from Problem Details (and `{error, error_description}` of the token endpoint). **Refresh logic:** on `401 TOKEN_EXPIRED` call `POST /auth/refresh` once (header `X-Requested-With: SwiftDrop`, `credentials: 'include'`), retry the original request once, otherwise clear the session and go to `/login`; only one refresh in flight (shared promise). `403 PASSWORD_CHANGE_REQUIRED` navigates to `/change-password`. Never hard-code hosts.
3. **`src/api/auth.js`:** `login`, `register`, `refresh`, `logout`, `getAuthConfig`.
4. **`src/context/AuthContext.jsx`:** restores the session at load with `POST /auth/refresh`; state `loading | authenticated | anonymous`; `useAuth()` returns `{user, status, login, logout, hasRole}`; `user = {id, fullName, role, mustChangePassword, merchantStatus?, merchantId?}`.
5. **Theme** `src/theme/theme.js`: one MUI theme with the master section 10 tokens (colours, status chip palette, radii, 44-48 px controls, 3 px orange focus ring, 150-200 ms transitions, `prefers-reduced-motion`); Inter bundled locally (import `@fontsource/inter` weights 400/500/600/700). No external fonts or CDNs (CSP).
6. **Shared components** (`src/components/`): `StatusChip` (label from SRS 6.2 + icon + colours; includes CANCELLED), `ConfirmDialog`, `LoadingState`, `EmptyState`, `ErrorState` (specific polite messages, never technical), `PageHeader`, (`GoogleLoginButton` is owned by #2: you only import it from `src/components/GoogleLoginButton.jsx`.) **`src/utils/format.js`:** `formatDateTime(iso)` and `formatDate` in Asia/Colombo with 12-hour clock (`Intl.DateTimeFormat`), `formatMoney(value)` ("LKR 1,234.00" from the server's decimal, no arithmetic), `applyServerErrors(setError, apiError)` mapping `errors[]` to React Hook Form fields without losing input.
7. **Layouts:** `PublicLayout` (white header, orange logo, navy nav, login/register links, mobile menu with 44-48 px rows), `DashboardLayout` (navy sidebar `#172033`, orange active item, 240-260 px expanded, drawer on mobile, white top bar with account menu, no search and no notification bell), `CourierLayout` (light, single column, large touch targets).
8. **Route table `src/routes/routeTable.js` + router:** every route of SRS 7.1 with allowed roles, layout and page folder; pages are resolved with `import.meta.glob('/src/pages/**/index.jsx')` and a "Not built yet" fallback, so other members never edit the router (master 7.4 lists the folder for each route). Static routes before parametrised ones (`/admin/parcels/unassigned` before `/admin/parcels/:id`). Guards: unauthenticated → `/login`; wrong role → that role's home (`customer: /customer/dashboard`, `merchant: /merchant/dashboard`, `courier: /courier/deliveries`, `admin: /admin/dashboard`); `mustChangePassword` → `/change-password` (only that page and logout allowed). Navigation items per role are derived from the same table and hide unusable entries. `QueryClientProvider` with sensible defaults, an error boundary, `main.jsx`/`App.jsx` wired.
9. Tests for PR A: token refresh single-flight and retry-once, `TOKEN_EXPIRED` handling, no storage use, route guards by role, `mustChangePassword` redirect, `StatusChip` labels, date/money formatting in Asia/Colombo.

### PR B: pages, Docker, tests

1. **`pages/auth/login/index.jsx`:** email + password (React Hook Form + Zod), visible labels, loading state, `GoogleLoginButton`, reads `?error=` (`google_denied`, `google_failed`, `account_exists`, `account_deactivated`) and shows a polite specific message, shows server errors without clearing input. Maps API errors: `INVALID_CREDENTIALS` ("Invalid email or password"), `ACCOUNT_DEACTIVATED`, `MERCHANT_PENDING` / `MERCHANT_REJECTED` / `MERCHANT_SUSPENDED` (explain status, no reason), `ACCOUNT_LOCKED` (429, show the `Retry-After` wait). After login go to the role home, or `/change-password` if `mustChangePassword`.
2. **`pages/auth/register/index.jsx`:** fullName 2-100, email, phone (07XXXXXXXX / +947XXXXXXXX / landline forms), password with policy hints (10-64, upper, lower, digit, special, not containing the email local part); server errors (`EMAIL_ALREADY_REGISTERED`, field errors); success message and link to login; link to `/register-merchant`. Google button here too.
3. **Frontend Dockerfile** (multi-stage: `node:22-alpine` build, `nginxinc/nginx-unprivileged:stable-alpine` runtime, non-root, port 8080, `VITE_API_BASE_URL` empty in the container build, `.dockerignore`, `wget` HEALTHCHECK) and **`nginx.conf`** (history fallback to `index.html`, long cache for hashed assets, no cache for `index.html`, gzip, no server version).
4. Tests **TST-040** (your part): login and registration forms (validation, server errors, field mapping), route guards, forced password change redirect.
5. Accessibility: labelled inputs, keyboard use, visible focus, contrast; no `dangerouslySetInnerHTML`.

### Done when
`npm run build` and `npm test -- --coverage` pass, other members can `import` everything listed in master 8.1 for the frontend, routes show "Not built yet" for missing pages, container builds and serves the SPA with history fallback. Report, and tell the frontend members in one line that PR A is merged.

---

## PROMPT 3 of 3: Login API, sessions, purge job, tests and docs

You are **Member #1**. Build the auth-service login slice on top of Prompt 1. Package `com.swiftdrop.auth.login.{controller,service,dto,mapper,scheduler}`.

**Preconditions:** Prompt 1 merged; #3 common-lib security merged (needed for `GlobalExceptionHandler`, filters); #6 outbox is not needed here.

### Requirements
- **FR-AUTH-001** `POST /api/v1/auth/register`: body `fullName` (2-100), `email` (valid, max 254, unique case-insensitive, stored lower-case), `phone` (normalised with common-core `PhoneNumberNormalizer` to E.164), `password` (FR-AUTH-002). Role is set by the server; unknown properties (for example `role`) → 400 `VALIDATION_ERROR`. Existing email → 409 `EMAIL_ALREADY_REGISTERED` (also when the unique constraint trips in a race). 201 with `{id, fullName, email, role}`, no tokens.
- **FR-AUTH-003/004/005** `POST /api/v1/auth/login` `{email, password}` → `{accessToken, tokenType:"Bearer", expiresIn:900, user}` (`user` = `id, fullName, role, mustChangePassword`, and for merchants `merchantStatus`, `merchantId`) and sets the `sd_refresh` cookie, updates `last_login_at`. Order of checks: locked account → 429 `ACCOUNT_LOCKED` with `Retry-After` (even with the right password); then verify the password. Unknown email, wrong password and a Google-only account (no hash) all give the same 401 `INVALID_CREDENTIALS` ("Invalid email or password"; do a dummy BCrypt check for unknown emails). Five consecutive failures for an existing account lock it for 15 minutes; a success resets the counter. Only after **valid** credentials: deactivated → 403 `ACCOUNT_DEACTIVATED`; merchant PENDING / REJECTED / SUSPENDED → 403 `MERCHANT_PENDING` / `MERCHANT_REJECTED` / `MERCHANT_SUSPENDED` (no reason in the response). Counters live in `users`.
- **FR-AUTH-007/008** `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` use `RefreshTokenService`; both require header `X-Requested-With: SwiftDrop` (missing → 403 `ACCESS_DENIED`). Refresh returns `{accessToken, tokenType, expiresIn, user}`; within the grace period no new cookie. Logout revokes the family, clears the cookie, returns 204 even without a valid cookie.
- **FR-AUTH-026** scheduled purge: used or expired refresh tokens 7 days after expiry; `oauth_login_states` hourly (the entity is #2's, so call a repository method in your own scheduler through a native delete statement on the table, no entity needed).
- Everything uses `DomainException` with common-core `ErrorCode`; OpenAPI annotations (summaries and error responses).

### Tests (JUnit 5, Testcontainers PostgreSQL, Spring Security Test; names with `@DisplayName("TST-0xx ...")` + `@Tag`)
- **TST-020** (your part): register, login, refresh rotation, reuse detection revoking the family (and not within the grace period), logout, lockout after 5 failures (the 6th returns 429 even with the right password; works again after the lock time), same error for unknown email / wrong password / Google-only account, merchant status refusals, deactivated refused, forced password change: a user with `pwc=true` gets `PASSWORD_CHANGE_REQUIRED` on a non-whitelisted endpoint (use a test-only controller in test sources) and the whitelisted ones work.
- **TST-033** (your endpoints): unauthenticated → 401 on protected endpoints, public endpoints reachable, deactivated users cannot log in or refresh.
- Purge job tests, `X-Requested-With` tests, cookie attribute tests (HttpOnly, SameSite=Strict, path, Secure by flag).
- Check scenarios AT-01, AT-02 manually or by test. Coverage of service, messaging and security packages at least 70 %.

### Docs
`docs/design/design-patterns.md`: short note naming the patterns used across the platform (layered architecture, DTO + mapper, outbox, idempotency key, optimistic and pessimistic locking, strategy/adapter for `EmailSender`/`SmsSender`, policy classes for ownership, API gateway), each with a one-line "where in the code".

### Done when
`mvn -pl backend/auth-service -am verify` green with coverage gate, login/register pages from Prompt 2 work against the running service, report sent.

