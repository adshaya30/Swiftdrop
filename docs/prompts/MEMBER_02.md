# MEMBER #2: Google login, profile and admin users (2 prompts)

The master prompt (v2.2) and the SRS are already loaded in your session.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | Backend: Google login, profile, bootstrap admin, admin users | No (needs #1 base, #3 common-lib, #6 outbox) |
| 2 | Frontend: callback, profile, change password, admin users, docs | No |

---

## PROMPT 1 of 2: auth-service backend (google, profile, users slices)

You are **Member #2**. Work only in `com.swiftdrop.auth.google`, `com.swiftdrop.auth.profile`, `com.swiftdrop.auth.users` (each with the layers you need). `application.yml`, pom and V1 are #1's: send #1 a snippet if you need a change.

**Preconditions (check the repo, mock what is missing):** #1 Prompt 1 merged (`User`, `Merchant`, `RefreshToken`, `JwtService`, `RefreshTokenService`, `PasswordPolicy`, `PasswordHasher`); #3 common-core + common-lib security; #6 common-lib events and `AuditEvents`. For anything missing, code against master 8.1 and mock it in tests; never copy it.

### Requirements

**Google login (FR-AUTH-009, 010, 011; Flow 4.5; AT-03, AT-04)**
- `GET /api/v1/auth/config` (public) → `{"googleEnabled": bool}`, true when `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set.
- `GET /api/v1/auth/google/login`: if Google is not configured redirect to `/login?error=google_failed`. Otherwise generate `state`, `nonce` and a PKCE `code_verifier`; store `nonce`, verifier and the **SHA-256 hash of state** in `oauth_login_states` (valid 5 minutes); set cookie `sd_oauth` (the state value; HttpOnly, SameSite=Lax, path `/api/v1/auth/google`, 5 minutes, `Secure` per `COOKIE_SECURE`); 302 to Google with `response_type=code`, `scope=openid email profile`, `client_id`, `redirect_uri` (= `GOOGLE_REDIRECT_URI`), `state`, `nonce`, `code_challenge`, `code_challenge_method=S256`.
- `GET /api/v1/auth/google/callback?code&state`: verify `state` equals the `sd_oauth` cookie **and** exists unused and unexpired in the database (then delete it); mismatch or missing → `/login?error=google_failed`; Google `error=access_denied` → `google_denied`. Exchange the code server-side (client secret + verifier) with Spring `RestClient`; validate the ID token (signature against Google's JWKS using the Nimbus decoder that ships with Spring Security, `iss` = `https://accounts.google.com` or `accounts.google.com`, `aud` = client id, `exp`, `nonce`, `email_verified=true`). Google endpoint URLs are fixed defaults in `application.yml` as plain Spring properties (no new environment variable; ask #1 to add them) so tests can point them to WireMock.
- Account rules (FR-AUTH-010): `email_verified` false → `google_failed`; user already linked by `google_sub` → log in (deactivated → `account_deactivated`); no link but a local account with the same email → `account_exists`, **never merge**; otherwise create a CUSTOMER with `auth_provider=GOOGLE`, no password, `google_sub`, name from Google, no phone. Only customers can use Google login. On success issue a refresh token with `RefreshTokenService`, set `sd_refresh`, update `last_login_at`, redirect to `${PUBLIC_BASE_URL}/auth/callback`. **All** failures redirect to `/login?error=<code>` with one of `google_denied, google_failed, account_exists, account_deactivated`; never expose Google error details or tokens.

**Profile (FR-AUTH-012, 013, 014)**
- `GET /api/v1/me` → `id, fullName, email, phone, role, authProvider, mustChangePassword, createdAt` and for merchants `merchant {id, businessName, status, discountPercent}`. `PUT /api/v1/me` updates **only** `fullName` and `phone` (normalise with `PhoneNumberNormalizer`; unknown properties rejected).
- `POST /api/v1/me/change-password` `{currentPassword, newPassword}`: `currentPassword` required for local accounts and verified; new password follows `PasswordPolicy` and differs from the current; revokes **all** refresh families (`revokeAllForUser`), clears `must_change_password`, returns 204 (the client logs in again). Google-only accounts → 400 `VALIDATION_ERROR`. Verify that the `pwc` whitelist works for `/me` and this endpoint (FR-AUTH-014 itself is common-lib).

**Bootstrap admin (FR-AUTH-015, DEP-010)**
- At startup, if no ADMIN exists, create one from `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_NAME`, `BOOTSTRAP_ADMIN_PASSWORD` (policy enforced, a clear startup failure message if invalid or missing) with `must_change_password=true`. Idempotent.

**Admin users (FR-AUTH-020, 021, 023; courier events; Flow 5)** all `ADMIN` only
- `GET /api/v1/admin/users` (filters `role`, `status`, `q` = name or email prefix; paginated, sortable by whitelisted fields). `GET /api/v1/admin/users/{id}`.
- `POST /api/v1/admin/users` creates a **COURIER** (`fullName, email, phone, vehicleType, vehicleNumber`; `vehicleType` in `MOTORBIKE, THREE_WHEELER, VAN`, number up to 20 chars, writes `courier_profiles`) or an **ADMIN** (`fullName, email, phone`) with a generated 16-character temporary password (secure random, satisfies the policy) returned **once** in the response (`temporaryPassword`), `must_change_password=true`. Other roles → 400. Email unique (409 `EMAIL_ALREADY_REGISTERED`).
- `PATCH /api/v1/admin/users/{id}` edits name, phone and (couriers) vehicle data.
- `POST /api/v1/admin/users/{id}/deactivate` and `/activate`. An admin cannot deactivate themself (the SRS gives no code: answer 403 `ACCESS_DENIED` and list it in your report as an SRS gap). Deactivating a **COURIER** (Flow 5): call delivery-service `GET /internal/v1/couriers/{id}/active-count` through `InternalRestClientFactory` (client class `users.client.DeliveryClient`); `inProgress > 0` → 409 `COURIER_HAS_ACTIVE_DELIVERIES`; delivery-service unreachable or 5xx → 503 `SERVICE_UNAVAILABLE` and **nothing changes**. Otherwise in one transaction: deactivate, revoke all refresh tokens (BR-23), write `courier.changed` (status `DEACTIVATED`) and an audit event. Customers and merchants can only be deactivated or activated. Activating a courier writes `courier.changed` (`ACTIVE`).
- `courier.changed` (data `courierId, fullName, status, vehicleType, vehicleNumber`) is written through `OutboxService` on courier **create, edit, activate and deactivate**.
- `POST /api/v1/admin/users/{id}/reset-password` (any user): new temporary password shown once, `must_change_password=true`, revoke all refresh tokens; Google-only accounts → 400.
- **Audit (FR-AUTH-023):** every create, edit, (de)activate and reset writes `AuditEvents.record` in the same transaction (actions `USER_CREATED, USER_UPDATED, USER_DEACTIVATED, USER_ACTIVATED, USER_PASSWORD_RESET`, entityType `USER`). Details never contain passwords, secrets or contact data (use ids and role only).

**Demo seed (FR-AUTH-025, S: only after everything above is done):** with `DEMO_SEED_ENABLED=true` idempotently create `customer@demo.example`, an APPROVED merchant `merchant@demo.example` (discount 10 %), couriers `courier1@demo.example`, `courier2@demo.example`, `courier3@demo.example` (each writes `courier.changed`); password `DEMO_USER_PASSWORD`, `must_change_password=false`. Disabled by default.

### Tests (Testcontainers PostgreSQL/RabbitMQ, WireMock, Spring Security Test)
- **TST-020 Google part** with WireMock standing in for Google (token endpoint + JWKS): valid login creates a customer; invalid state; invalid nonce; unverified email; existing local email → `account_exists` and no merge; deactivated linked user; denied consent; state reuse impossible (deleted after use).
- Profile: update rules, change-password (wrong current, policy, same as current, Google-only 400, revokes refresh families, clears the flag), bootstrap admin (creates once, invalid password fails startup).
- **TST-029 auth side:** courier deactivation blocked while `inProgress > 0` (WireMock for the 7.4 contract), allowed otherwise and revokes tokens, 503 leaves everything unchanged, outbox rows `courier.changed` and audit written in the same transaction, admin cannot deactivate self.
- **TST-033** for your endpoints: 401 unauthenticated, 403 for CUSTOMER / MERCHANT / COURIER on every `/admin/users` endpoint, `typ=client` token 403 on `/me`.
- No contact data or passwords in outbox payloads or audit details; temporary passwords never logged.
- Scenarios AT-03, AT-04, AT-26 (first login forces a password change). Coverage at least 70 %.

### Done when
All endpoints work, `mvn -pl backend/auth-service -am verify` is green, report sent (including SRS gaps you met).

---

## PROMPT 2 of 2: frontend pages and docs

You are **Member #2**. Folders: `frontend/src/pages/auth/callback`, `pages/auth/profile`, `pages/auth/changePassword`, `pages/admin/users`; API files `src/api/profile.js` and `src/api/adminUsers.js`. You also own the shared component `src/components/GoogleLoginButton.jsx` (calls `getAuthConfig`, renders nothing when `googleEnabled` is false, otherwise a plain link/redirect to `${VITE_API_BASE_URL}/api/v1/auth/google/login`, not a fetch); #1's login and register pages import it. Use only the shared client, components, layouts and formatters listed in master 8.1; if #1's foundation is not merged yet, build against those names and tell the member.

### Pages (default export in `index.jsx` of each folder; the route table picks them up)
1. **`/auth/callback`** (`pages/auth/callback`): calls `POST /auth/refresh` through the auth context to restore the session; success → role home (customer dashboard; `/change-password` when `mustChangePassword`); failure → `/login?error=google_failed`. Show a loading state; no token in URLs.
2. **`/profile`** (`pages/auth/profile`, any role): shows `GET /me` data (merchant block for merchants); form to edit `fullName` and `phone` (Zod mirrors server limits; server field errors kept without losing input); a change-password section (current password hidden for Google-only accounts, a note explains that); on success the app logs the user out and goes to `/login` with a confirmation.
3. **`/change-password`** (`pages/auth/changePassword`): forced page for `mustChangePassword`; password policy hints and a live checklist (10-64, upper, lower, digit, special, not containing the email local part); on success log in again.
4. **`/admin/users`** (`pages/admin/users`, ADMIN): table with filters `role`, `status`, `q` (name or email prefix), pagination, sort; `StatusChip`-style chips for ACTIVE/DEACTIVATED with text. Dialogs: **create user** (COURIER fields: name, email, phone, vehicle type select, vehicle number; ADMIN fields) showing the **temporary password once** in a dialog with a copy button and a warning that it will not be shown again; **edit**; **deactivate / activate** and **reset password** with `ConfirmDialog` (reset shows the new temporary password once). Handle `COURIER_HAS_ACTIVE_DELIVERIES` ("This courier still has deliveries in progress"), `SERVICE_UNAVAILABLE` ("Could not check the courier's deliveries, try again"), and the cannot-deactivate-yourself case (403). Loading, empty and error states everywhere; the admin cannot see passwords after the dialog closes.
5. **API files:** `profile.js` (`getMe`, `updateMe`, `changePassword`), `adminUsers.js` (list, get, create, update, deactivate, activate, resetPassword) using `apiRequest`.

### Tests (Vitest + Testing Library; `describe("TST-040 ...")`)
Callback success and failure, profile form validation and server errors, change-password flow and forced redirect, admin users: filters call the API with the right query, create shows the temporary password once, deactivate asks for confirmation, error codes show the right messages. Accessible labels, keyboard use, visible focus. Check at 360 px: no horizontal scroll on profile and change-password.

### Docs
- `docs/design/google-login-sequence.md`: Mermaid sequence diagram of Flow 4.5 (browser, SPA, gateway, auth-service, Google, database) including state, nonce, PKCE and the failure redirects.
- `docs/design/auth-er.md`: Mermaid ER diagram of the auth database (users, refresh_tokens, oauth_login_states, courier_profiles, merchants, merchant_credentials, outbox_events) with cardinalities from SRS 6.4.

### Done when
All four pages work against the running auth-service, `npm test -- --coverage` and `npm run build` pass, diagrams committed, report sent.

