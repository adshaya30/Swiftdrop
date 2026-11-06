# AUDIT PROMPT: Member #2 (Google login, profile, admin users)

Paste the master prompt (v3.0) first in a **new** AI session, then everything below. Your spec is `docs/prompts/MEMBER_02.md` (Prompts 1 and 2) in the repo. If that file is not in the repo, ask the member to attach it before you start. **Start Step 3 only after #1 has merged their audit PRs** (you build on #1's domain, `JwtService` and `RefreshTokenService`; if #1 moves or renames classes, your imports change). Steps 1 and 2 can run right away.

---

You are **Member #2**. Your work (Google login, profile, change password, bootstrap admin, admin users, and their frontend pages and diagrams) is already built, but before the final plan. Your job now is an **audit and alignment**, not a new build. Others depend on your `courier.changed` events and your page folders, so everything must match the master prompt (sections 7 and 8) and `MEMBER_02.md`. You work only in `com.swiftdrop.auth.google`, `auth.profile`, `auth.users`, your frontend folders and `src/components/GoogleLoginButton.jsx`.

Rules for this task: no new features; keep behaviour that already works; change the minimum needed; stage files by explicit path; never run destructive commands without asking the member first.

## Step 1: Read-only inventory (no edits yet)

Read the master prompt, SRS sections for FR-AUTH-009 to 015, 020 to 025, Flow 4.5, Flow 5, section 7.4 (delivery contract) and `MEMBER_02.md`. Write `docs/audit/M2-audit.md`: a table with **Item | Spec says | Found in repo | Status (OK / Fix / Missing / Wrong) | Action**. Check every row.

**Backend**
1. Packages: only `com.swiftdrop.auth.google.<layer>`, `auth.profile.<layer>`, `auth.users.<layer>` (slice first); no edits to #1's files (pom, `application.yml`, V1, `domain`, security config). List every place where you changed a file you do not own.
2. Google login: `GET /api/v1/auth/config`; `/auth/google/login` (state, nonce, PKCE S256, SHA-256 of state stored, 5-minute validity, `sd_oauth` cookie attributes, redirect parameters); `/auth/google/callback` (state equals cookie and exists unused in the database, then deleted; server-side code exchange with `RestClient`; ID token validation of signature, `iss`, `aud`, `exp`, `nonce`, `email_verified`); Google endpoint URLs as plain properties; account rules (linked, local-email conflict `account_exists` with **no merge**, new CUSTOMER with no password); only the four error codes `google_denied, google_failed, account_exists, account_deactivated`; success redirects to `${PUBLIC_BASE_URL}/auth/callback` with `sd_refresh` set through `RefreshTokenService`.
3. Profile: `GET/PUT /api/v1/me` (PUT updates only `fullName` and `phone`, unknown properties rejected, merchant block); `POST /api/v1/me/change-password` (rules, revoke all families, clears the flag, 204, Google-only 400, `pwc` whitelist works).
4. Bootstrap admin: only when no ADMIN exists, idempotent, policy enforced, `must_change_password=true`, clear failure message.
5. Admin users: list, get, create (COURIER and ADMIN only, 16-character temporary password returned once, never logged), patch, deactivate/activate (self-deactivation 403 `ACCESS_DENIED`; courier deactivation uses `DeliveryClient` through `InternalRestClientFactory`, `inProgress>0` 409 `COURIER_HAS_ACTIVE_DELIVERIES`, unreachable or 5xx 503 `SERVICE_UNAVAILABLE` with nothing changed; revoke tokens in the same transaction), reset password.
6. **Events and audit:** `courier.changed` (data `courierId, fullName, status, vehicleType, vehicleNumber`) is written through `OutboxService` on courier create, edit, activate and deactivate, in the same transaction; every change writes `AuditEvents.record` with the actions of Prompt 1 and no passwords, secrets or contact data. **If `OutboxService`, `AuditEvents` or `InternalRestClientFactory` were not available when you built this, check what you did**: a local copy or a direct insert into `outbox_events` must be listed (see Step 4).
7. Demo seed (`DEMO_SEED_ENABLED`, off by default, idempotent, three couriers write `courier.changed`).
8. Tests: TST-020 Google part with WireMock, profile tests, TST-029 auth side, TST-033 for your endpoints (401, 403 for CUSTOMER/MERCHANT/COURIER on `/admin/users`, `typ=client` token 403 on `/me`); `@DisplayName("TST-0xx ...")` + `@Tag`; no contact data or passwords in outbox or audit; coverage 70 %.

**Frontend**
9. Folders exactly: `pages/auth/callback`, `pages/auth/profile`, `pages/auth/changePassword`, `pages/admin/users`, each with a default-exported `index.jsx`; API files `src/api/profile.js` (`getMe`, `updateMe`, `changePassword`) and `src/api/adminUsers.js` (list, get, create, update, deactivate, activate, resetPassword) using `apiRequest`. No edits to #1's shared files (router, layouts, `http.js`).
10. Pages: callback (restores session through the auth context, failure goes to `/login?error=google_failed`), profile (edit rules, change-password section, logout after success), forced change-password page with live checklist, admin users (filters, pagination, sort, create with one-time temporary password dialog, edit, confirm dialogs, error messages for `COURIER_HAS_ACTIVE_DELIVERIES`, `SERVICE_UNAVAILABLE` and self-deactivation).
11. **`src/components/GoogleLoginButton.jsx` is yours now.** Calls `getAuthConfig`, renders nothing when `googleEnabled` is false, otherwise a plain redirect link to `${VITE_API_BASE_URL}/api/v1/auth/google/login` (not a fetch). If #1 built it, review it against this and take ownership; if nobody did, create it. #1's login and register pages import it from this path.
12. Tests TST-040 (your pages); docs `docs/design/google-login-sequence.md` and `docs/design/auth-er.md` (Mermaid) exist and match the final code.

## Step 2: Decide (stop and show the member)

Classify each Wrong or Missing row as **Fix in place** or **Rebuild that file**. Default: fix in place. Recommend rebuilding a whole slice only if most of its rows are Wrong **and** the cause is structural (wrong packages plus a wrong flow). Show the member `docs/audit/M2-audit.md` with your recommendation and **wait for their go-ahead** before changing anything.

## Step 3: Fix (small pull requests)

PR 1 backend, PR 2 frontend and docs. Use `git mv` for moves. Where you need a change in a file you do not own (pom, `application.yml`, a domain class, the route table), write the exact snippet and send it to the owner (#1 for auth-service and the SPA foundation, #6 for outbox and audit helpers, #3 for common-core and common-lib). Do not edit it yourself. Keep existing tests green and rename or add tests to follow the master.

## Step 4: Phase 2, adopt the shared libraries

For anything marked `// TODO(common): replace with ...` (a local outbox writer, local audit recorder, local `InternalRestClientFactory`, problem body, phone normaliser): list it in `docs/audit/M2-adopt-common.md`. Replace it only after the provider has merged (#3's common-core and common-lib, #6's events/outbox/audit). If they are not merged yet, stop here and tell the member: "Phase 2 is waiting for #3/#6. When merged, paste this prompt again with the line `Continue with Phase 2 only.`"

## Step 5: Verify and merge (you merge your own PRs)

1. `git pull --rebase origin main`, `./mvnw -pl backend/auth-service -am verify`, `npm test -- --coverage`, `npm run build`.
2. Run on the real stack where possible: bootstrap admin login forces a password change, admin creates a courier (temporary password shown once, `courier.changed` row in `outbox_events`), deactivate/activate, profile edit, change password. Google login with the real credentials if the member has them, otherwise rely on the WireMock tests.
3. Confirm `git diff --name-only` shows only your files, then squash-merge each PR.
4. Final report (master 13.9): audit summary, changes, what remains in `M2-adopt-common.md`, and the SRS gaps you met (self-deactivation code, and any others).

### Done when
Every audit row is OK, tests are green, `GoogleLoginButton` is yours and imported by #1's pages, `courier.changed` is produced as the SRS says, PRs are merged, report sent.
