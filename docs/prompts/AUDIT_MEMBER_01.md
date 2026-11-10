# AUDIT PROMPT: Member #1 (auth foundation, SPA foundation, login API)

Paste the master prompt (v3.0) first in a **new** AI session, then everything below. Your spec is `docs/prompts/MEMBER_01.md` (Prompts 1 to 3) in the repo. If that file is not in the repo, ask the member to attach it before you start.

---

You are **Member #1**. Your work (auth-service foundation, SPA foundation, login/register pages, login API) is already built, but it was built before the final plan. Your job now is an **audit and alignment**, not a new build. Other members will code against the exact signatures, paths, package names and file names in the master prompt (section 7 and 8.1) and in `MEMBER_01.md`, so anything that differs must be fixed **on your side**, never on theirs.

Rules for this task: no new features; keep behaviour that already works; change the minimum needed to match the spec; stage files by explicit path; never run destructive commands without asking the member first.

## Step 1: Read-only inventory (no edits yet)

Read the master prompt, SRS sections 3.6, 6.1 to 6.4, 7.1, 9.2 and the FR-AUTH requirements, and `MEMBER_01.md`. Then inspect the repo and write `docs/audit/M1-audit.md`: a table with the columns **Item | Spec says | Found in repo | Status (OK / Fix / Missing / Wrong) | Action**. Check every row below.

**Backend (`backend/auth-service`)**
1. Module and build: parent is `backend/pom.xml`; Java 21, Spring Boot 3.5.16; **no Lombok**; the dependency list of Prompt 1 (including common-core and common-lib); layered-jar; `Dockerfile` (multi-stage, non-root, port 8081, `TZ=UTC`, HEALTHCHECK on readiness) and `.dockerignore`.
2. Packages: `com.swiftdrop.auth.<slice>.<layer>` (slice first) and `com.swiftdrop.auth.domain` for entities and repositories. Report any layer-first packages (`controller/`, `service/` at the top).
3. `application.yml`: every auth-service variable of SRS 9.2 with its default, port 8081, graceful shutdown 30 s, `ddl-auto=validate`, `fail-on-unknown-properties=true`, readiness group = database only, liveness without dependencies, Swagger only when `SWAGGER_ENABLED=true`, Prometheus exposed, JSON logging.
4. Migration: file named `V1__init.sql`; all auth tables, columns, CHECK lists, unique constraints, indexes and the `outbox_events` partial index exactly as SRS 6.3; enum columns are `VARCHAR` with CHECK. Extra Vn files that should be merged into V1.
5. Domain: `User`, `Merchant`, `RefreshToken` with `@Version` where specified; repositories with the methods listed in Prompt 1 (`findByEmail`, `findByGoogleSub`, `existsByRole`, `MerchantRepository.findByUserId`, token lookup by hash incl. `PESSIMISTIC_WRITE`, bulk revoke by user and by family).
6. `SecurityRules` bean (master 8.1): public paths and the password-change whitelist (FR-AUTH-014), full paths `/api/v1/...`.
7. Keys: private key from `JWT_PRIVATE_KEY_PATH`, public key **derived from the private key**, fail fast on missing or non-RSA key.
8. `JwtService`: method signatures exactly as master 8.1; RS256, `kid=swiftdrop1`; claims exactly `sub, role, typ, mid, scope, pwc, iss=swiftdrop, aud=swiftdrop-api, iat, exp, jti`; no personal data.
9. `RefreshTokenService`: hashing (SHA-256 hex, 256-bit random), family id and absolute cap, `rotate` outcomes (unknown/expired/over-age, revoked reuse, outside grace, inside grace without new cookie, deactivated user or non-APPROVED merchant), `revokeFamily`, `revokeAllForUser`; `RefreshCookieFactory` attributes (`sd_refresh`, HttpOnly, Secure by flag, SameSite=Strict, path `/api/v1/auth`).
10. `PasswordPolicy` (returns all violations) and `PasswordHasher` (BCrypt 12).
11. Login slice: register, login, refresh, logout endpoints, bodies, status codes and error codes of Prompt 3 (check order: lockout first, same 401 for unknown email, wrong password and Google-only account, dummy BCrypt, merchant status codes, `X-Requested-With` on refresh/logout, 204 logout); purge scheduler.
12. Errors and shared code: uses common-core `ErrorCode`, `DomainException`, Problem Details; **list every class that duplicates something that belongs to common-core or common-lib** (problem body, error handler, filters, phone normaliser, enums, outbox classes). Check whether `backend/common-core` and `backend/common-lib` exist yet and what they contain.
13. Tests: TST-013, TST-020 (your part), TST-033 (your endpoints) exist, use `@DisplayName("TST-0xx ...")` + `@Tag`, run on Testcontainers; coverage gate 70 %. `docs/design/design-patterns.md` exists.

**Frontend (`frontend/`)**
14. Stack: JavaScript (JSX), Vite, MUI, TanStack Query, React Router, RHF + Zod, Vitest; scripts `dev`, `build`, `test`; no external fonts or CDNs.
15. `src/api/http.js` exports exactly `apiRequest`, `apiForm`, `apiBlob`, `ApiError` with the signatures in master 8.1; token in a module variable only; single-flight refresh on `401 TOKEN_EXPIRED` with retry once; `403 PASSWORD_CHANGE_REQUIRED` redirect. `src/api/auth.js`, `src/context/AuthContext.jsx` (`useAuth()` shape).
16. `src/theme/theme.js` (master section 10 tokens); shared components `StatusChip`, `ConfirmDialog`, `LoadingState`, `EmptyState`, `ErrorState`, `PageHeader`; `utils/format.js` (`formatDateTime`, `formatDate`, `formatMoney`, `applyServerErrors`); layouts `PublicLayout`, `DashboardLayout`, `CourierLayout`.
17. **Route table** `src/routes/routeTable.js`: every SRS 7.1 route, roles, layout, page folder per master 7.4; pages resolved with `import.meta.glob('/src/pages/**/index.jsx')` and a "Not built yet" fallback; static routes before parametrised ones; guards and role homes; `mustChangePassword` redirect. Nobody else should ever need to edit the router.
18. Pages `pages/auth/login/index.jsx` and `pages/auth/register/index.jsx` (default exports, error mapping, `?error=` messages), frontend `Dockerfile` and `nginx.conf`, tests TST-040 (your part).
19. **`GoogleLoginButton` now belongs to #2.** If you built it, leave the file as it is, tell #2 it exists, and never edit it again. If you did not, the login and register pages must import it from `src/components/GoogleLoginButton.jsx` and #2 will add it; do not create a copy.

## Step 2: Decide (stop and show the member)

Classify each Wrong or Missing row as **Fix in place** or **Rebuild that file**. The default is to fix in place: the structure is almost always right and only names, paths, signatures or small behaviours differ. Recommend a full rebuild of a module (auth-service or the SPA foundation) only if **most rows are Wrong and the cause is structural** (for example, layer-first packages everywhere plus a different migration plus different token design) and rewriting would be quicker than moving files. Show the member `docs/audit/M1-audit.md` with your recommendation and **wait for their go-ahead** before changing anything.

## Step 3: Fix (small pull requests)

- PR 1 backend, PR 2 frontend. Use `git mv` for package moves, then fix imports. Match master 8.1 signatures even if that changes your internal callers.
- Nobody else has built on auth-service yet and V1 has not been released, so edit `V1__init.sql` in place instead of adding V2, and fold any extra migration into it. After that the local database must be recreated: **ask the member for approval** before `docker compose down -v` or dropping the database.
- Keep existing tests green; rename or add tests so IDs, `@DisplayName` and `@Tag` follow the master.
- Where something belongs to common-core or common-lib and that module is **not merged yet**: do not rebuild it and do not delete your local version. Mark it `// TODO(common): replace with common-core/common-lib` and list it in `docs/audit/M1-adopt-common.md` (class, package, what replaces it).

## Step 4: Phase 2, adopt the shared libraries

Only after #3 has merged `common-core` and `common-lib` (all three PRs of their Prompt 1). Replace each item in `M1-adopt-common.md` with the shared class, delete the local copy, run the tests, and merge a third small PR. If #3 has not merged yet, stop here and tell the member: "Phase 2 is waiting for #3. When it is merged, paste this prompt again with the line `Continue with Phase 2 only.`"

## Step 5: Verify and merge (you merge your own PRs)

1. `git pull --rebase origin main`, then `./mvnw -pl backend/auth-service -am verify` and, for the frontend, `npm test -- --coverage` and `npm run build`.
2. Start auth-service and the SPA from compose (ask #8's scripts if needed) and run: register, login, refresh, logout, wrong password five times (lockout), and `/login` and `/register` in the browser.
3. Confirm `git diff --name-only` contains only your files, then squash-merge each PR.
4. Final report (master 13.9) with: the audit table summary, what changed, anything left in `M1-adopt-common.md`, and one line for #2 and #3: "auth base is aligned and merged".

### Done when
Every row of the audit is OK, auth-service and the SPA foundation match master 8.1 exactly, tests are green, PRs are merged, and #2 and #3 have been told.
