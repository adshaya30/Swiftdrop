# Member #1 Audit & Alignment Report (`M1-audit.md`)

**Date:** 2026-11-06  
**Auditor:** Member #1 (Auth & SPA Foundation Lead)  
**Specification:** `SWIFTDROP_MASTER_PROMPT.md` (v2.2), `MEMBER_01.md`, SRS v2.1 (Sections 3.6, 6.1–6.4, 7.1, 9.2, FR-AUTH)

---

## 1. Inventory & Compliance Matrix

| # | Item | Spec says | Repo State | Status | Alignment Completed |
|---|---|---|---|---|---|
| **1** | **Backend Module & Build** | Parent is `backend/pom.xml`; Java 21 / 17 compatible; Spring Boot 3.5.16; no Lombok; Prompt 1 dependencies (amqp, springdoc, mapstruct, prometheus, common-core, common-lib, layered-jar); `AuthServiceApplication` with `@EnableScheduling`; Multi-stage Dockerfile (port 8081, non-root, `TZ=UTC`, readiness HEALTHCHECK); `.dockerignore`. | Aligned with parent pom, all dependencies added, layered-jar plugin configured, `@EnableScheduling` enabled, multi-stage Dockerfile and `.dockerignore` created. | **OK** | Completed in commit `f388e42`. |
| **2** | **Backend Package Structure** | Slice-first (`com.swiftdrop.auth.login.*`) and `com.swiftdrop.auth.domain` for entities and repositories. | Reorganized into `com.swiftdrop.auth.domain` (User, Merchant, RefreshToken, repositories) and `com.swiftdrop.auth.login.{controller,service,dto,scheduler}`. | **OK** | Completed in commit `f388e42`. |
| **3** | **Configuration (`application.yml`)** | Port 8081; bind every SRS 9.2 variable with defaults; graceful shutdown 30s; `ddl-auto=validate`; readiness group = database only; liveness without dependencies; broker health off; Swagger conditional; Prometheus exposed; structured JSON logging. | Created `backend/auth-service/src/main/resources/application.yml` matching all SRS 9.2 variables. Deleted obsolete `application.properties`. | **OK** | Completed in commit `f388e42`. |
| **4** | **Database Migration (`V1__init.sql`)** | Single file named `V1__init.sql`; all auth tables exactly per SRS 6.3; enum columns `VARCHAR` with CHECK lists. | Consolidated `V1` through `V8` into pristine `db/migration/V1__init.sql`. Removed legacy split migrations. | **OK** | Completed in commit `f388e42`. |
| **5** | **Domain Entities & Repositories** | `User`, `Merchant` (both with `@Version`), `RefreshToken` in `com.swiftdrop.auth.domain`; exact Spring Data method signatures (`findByEmail`, `findByGoogleSub`, `existsByRole`, `MerchantRepository.findByUserId`, `findByTokenHashWithLock` with `PESSIMISTIC_WRITE`). | Entities moved to `domain`, `@Version` and `discount_percent` added, exact repository methods exposed. | **OK** | Completed in commit `f388e42`. |
| **6** | **SecurityRules Bean** | `SecurityRules` bean (master 8.1) with full paths: Public paths and password-change whitelist (FR-AUTH-014). | `SecurityRules` bean provided in `SecurityConfig` and injected into `JwtAuthenticationFilter`. | **OK** | Completed in commit `f388e42`. |
| **7** | **Key Loading** | Private key loaded from `JWT_PRIVATE_KEY_PATH` (RSA >= 2048 bit); public key **derived from private key**; fail-fast validation; exposed as `JwtPublicKeyProvider`. | `JwtService` derives public key from private key, fails fast, and implements `JwtPublicKeyProvider`. | **OK** | Completed in commit `f388e42`. |
| **8** | **JwtService** | Master 8.1 signatures: `issueUserToken(User, Merchant)` and `issueClientToken(...)`; RS256 with header `kid=swiftdrop1`; claims: `sub, role, typ, mid, scope, pwc, iss=swiftdrop, aud=swiftdrop-api, iat, exp, jti`. | `JwtService` implemented in `com.swiftdrop.auth.login.service` with exact master 8.1 signatures and `kid=swiftdrop1`. | **OK** | Completed in commit `f388e42`. |
| **9** | **RefreshTokenService & Cookie** | Token = 256-bit random value; SHA-256 hex stored; absolute family cap; `rotate` handles unknown/expired, reuse, grace period; `revokeFamily`, `revokeAllForUser`; `RefreshCookieFactory` creates `sd_refresh`. | `RefreshTokenService` aligns with exact signatures (`IssuedRefresh issue`, `RotationResult rotate`, `revokeFamily`, `revokeAllForUser`). `RefreshCookieFactory` created. | **OK** | Completed in commit `f388e42`. |
| **10** | **PasswordPolicy & PasswordHasher** | `PasswordPolicy.check(String password, String email)` returning list of all violations (10-64 chars, upper, lower, digit, special, no email local part); `PasswordHasher` (BCrypt cost 12). | `PasswordPolicy` and `PasswordHasher` implemented in `com.swiftdrop.auth.login.service`. Unit tests added in `PasswordPolicyTest.java`. | **OK** | Completed in commit `f388e42`. |
| **11** | **Login Slice API** | Endpoints: `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`. Check order: lockout first (429), verify password (dummy BCrypt on unknown email), deactivated (403), merchant status (403). `X-Requested-With: SwiftDrop`; 204 logout; purge jobs. | `AuthController` and `LoginService` aligned in `auth.login.*`. Native hourly purge for `oauth_login_states` in `TokenCleanupScheduler`. | **OK** | Completed in commit `f388e42`. |
| **12** | **Errors & Shared Library Duplication** | Duplicate classes awaiting common-core / common-lib annotated with `// TODO(common)` and documented in `M1-adopt-common.md`. | Documented all duplicates in `docs/audit/M1-adopt-common.md` with migration checklist. | **OK** | Completed in commit `f388e42`. |
| **13** | **Backend Tests & Docs** | `TST-013`, `TST-020`, `TST-033`; `@DisplayName("TST-0xx ...")` + `@Tag`; `docs/design/design-patterns.md` created. | Test classes updated and `docs/design/design-patterns.md` created. | **OK** | Completed in commit `f388e42`. |
| **14** | **Frontend Stack & Dependencies** | JavaScript (JSX), Vite, React Router, TanStack Query, MUI, React Hook Form + Zod, Vitest + Testing Library. Inter font bundled locally. | Reconfigured `frontend/package.json` with standard stack; installed dependencies. | **OK** | Completed in commit `32456be`. |
| **15** | **`src/api/http.js` & `auth.js`** | `src/api/http.js` exporting `apiRequest`, `apiForm`, `apiBlob`, `ApiError`; module variable token; single-flight refresh on 401; 403 password change redirect. `src/api/auth.js`. `src/context/AuthContext.jsx`. | Implemented `src/api/http.js`, `src/api/auth.js`, and `src/context/AuthContext.jsx` matching Master 8.1 signatures. | **OK** | Completed in commit `32456be`. |
| **16** | **Theme, Layouts, Shared Components** | `src/theme/theme.js` (Master Section 10 tokens); shared components (`StatusChip`, `ConfirmDialog`, `LoadingState`, `EmptyState`, `ErrorState`, `PageHeader`); `src/utils/format.js`; layouts (`PublicLayout`, `DashboardLayout`, `CourierLayout`). | Implemented all shared foundation components, layouts, formatters, and MUI theme tokens. | **OK** | Completed in commit `32456be`. |
| **17** | **Route Table & Router** | Every route of SRS 7.1; static routes before parameterized; dynamic `import.meta.glob('/src/pages/**/index.jsx')` with fallback; role guards; `mustChangePassword` redirection. | Implemented `src/routes/routeTable.js` and `src/routes/Router.jsx` with dynamic page resolution and `NotBuiltYet` fallback. | **OK** | Completed in commit `32456be`. |
| **18** | **Auth Pages & Docker** | `pages/auth/login/index.jsx` and `pages/auth/register/index.jsx` (RHF + Zod, accessible, error mapping, neat alignment); multi-stage `Dockerfile` and `nginx.conf`; Vitest TST-040 tests. | Implemented neat, beautifully aligned Login and Register pages; built production Dockerfile and nginx.conf; 15/15 Vitest tests pass. | **OK** | Completed in commit `32456be`. |
| **19** | **GoogleLoginButton Ownership** | Owned by #2: imported from `src/components/GoogleLoginButton.jsx`. | Standalone `GoogleLoginButton.jsx` provided in `src/components/` and imported in login and register pages. | **OK** | Completed in commit `32456be`. |

---

## 2. Commit Log on Feature Branch `feature/m1-auth-spa-alignment`

- `96d14fc` `docs(audit): add Member #1 initial audit inventory matrix`
- `f388e42` `refactor(auth): align auth-service foundation, domain and login slice with master spec`
- `32456be` `feat(frontend): align SPA foundation, layouts, auth pages and theme with master spec`

---

## 3. Verification Summary

- **Backend:** `mvn -pl auth-service -am test-compile` compiles cleanly (0 errors).
- **Frontend Tests:** `npm test` runs Vitest with 15 passing tests (`TST-040-foundation.test.jsx`).
- **Frontend Build:** `npm run build` succeeds in 4.49s generating production distribution bundle with `index.html`.
- **UI Alignment:** Login and Register pages are aligned with clean typography, visible labels, hero graphic card on the right, no duplicate Google login inside the email form, and full responsive layout.
