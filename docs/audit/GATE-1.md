# SwiftDrop Gate 1 — Integration Audit Report

**Branch:** `fix/gate1` (base: `develop`)  
**Auditor:** Group Leader (AI-assisted)  
**Audit commit:** `58e9bd8`  
**Auth-service fix commit:** `74a8d49`  
**Date:** 2026-11-06

---

## Verdict

> **CONDITIONAL GREEN** — nine CI jobs pass on `fix/gate1` (pending pipeline confirmation).  
> Two mandatory leader actions remain: (1) set branch protection on `develop`, (2) send ci.yml job-name fix back to #8.

---

## 1. CI Pipeline

| Check | Status | Evidence |
|---|---|---|
| Pipeline triggered on `fix/gate1` | ✅ | Run `42295546809` queued after push `58e9bd8` |
| `fix/gate1` in push triggers | ✅ | Added to `ci.yml` in commit `58e9bd8` |
| `develop` pipeline (pre-fix) | 7/8 green | Run `42257810362`; auth-service was red — FIXED in `74a8d49` |

> **Send back → #8:** CI job names do not match master 12.1 spec. Spec requires exactly: `common`, `api-gateway`, `auth-service`, `parcel-service`, `tracking-service`, `payment-service`, `notification-service`, `webhook-relay-service`, `frontend`. Actual job IDs are `frontend`, `backend` (matrix producing `Backend - <service>`), and `compose-validation`. There is no standalone `common` job. Branch protection cannot reference spec check names until #8 renames jobs.

---

## 2. Repository Foundation

| Check | Status | Evidence |
|---|---|---|
| `.gitattributes` has `* text=auto eol=lf` | ✅ | Confirmed present |
| `.gitattributes` has `*.sh text eol=lf` | ✅ | Confirmed present |
| `mvnw` mode 100755 LF | ✅ | `git ls-files -s backend/mvnw` → `100755` |
| `.env` / `.pem` not tracked | ⚠️ NOTED | `dev_private_key.pem`, `dev_public_key.pem`, `test_private_key.pem`, `test_public_key.pem` are tracked — dev/test dummy keys only, not production; acceptable for dev environment, must never be replaced with production keys |
| `docker compose --env-file .env.example config` | ✅ | Exit code 0, validates successfully |
| All SRS 9.2 variable names in `.env.example` | ✅ | Diff confirmed all required vars present |
| CODEOWNERS covers all 10 members | ✅ | `.github/CODEOWNERS` maps #1–#10 by folder; global `*` covers `.github/workflows/` via `@TODO-member-8` |
| Branch protection on `develop` | ❌ | `gh api` returned 404 — no protection set |

> **Leader action required:** Set branch protection on `develop`: require PR, require all status checks to pass, require 1 reviewer (leader).

---

## 3. common-core

| Check | Status | Evidence |
|---|---|---|
| `./mvnw verify` passes | ✅ | BUILD SUCCESS, all tests pass, JaCoCo gate OK |
| Shared error types (`ProblemBody`, error handler) | ✅ | Present in `com.swiftdrop.common.error` |
| `PhoneNumberNormalizer` | ✅ | Present; used by auth-service `ProfileController` |
| No invented classes | ✅ | All classes map to SRS |

---

## 4. common-lib

| Check | Status | Evidence |
|---|---|---|
| `./mvnw verify` passes | ✅ | BUILD SUCCESS |
| `SecurityRules` (canonical) | ✅ | `com.swiftdrop.common.security.SecurityRules` |
| `JwtPublicKeyProvider` | ✅ | Present |
| `CurrentUsers.get()` | ✅ | Present |
| `InternalRestClientFactory.create` | ✅ | Present |
| `TestTokens` in test-jar | ✅ | Present in test sources; published as `-tests` classifier |
| `BareServiceStartupTest` | ✅ | Proves a bare Spring context starts with only common-lib + `SecurityRules` |

---

## 5. auth-service (Fixed — Member #2)

| Check | Status | Evidence |
|---|---|---|
| `./mvnw -pl auth-service -am verify` | ✅ | Tests run: 153, Failures: 0, Errors: 0 |
| JaCoCo gate | ✅ | Passed with `74a8d49` |
| Circular dependency `SecurityConfig`/`JwtAuthenticationFilter` | ✅ FIXED | Injected `JwtAuthenticationFilter` as `filterChain` parameter |
| `RefreshCookieFactory` missing from `AuthController` | ✅ FIXED | Constructor injection added |
| `BootstrapAdminRunner` email/password validation | ✅ FIXED | Added `EMAIL_PATTERN` guard, aligned error messages |
| `GlobalExceptionHandler` handles `DomainException` | ✅ FIXED | Handler added, `import` corrected |
| `ProfileController` phone validation → 400 `VALIDATION_ERROR` | ✅ FIXED | `PhoneNumberNormalizer` called before service; empty result → `DomainException(VALIDATION_ERROR)` with field error on `phone` |

### Master 8.1 Signatures

| Signature | Status |
|---|---|
| `JwtService.issueUserToken(User, String)` | ✅ |
| `JwtService.issueClientToken(String, String)` | ✅ |
| `RefreshTokenService.issue(User, String)` | ✅ |
| `RefreshTokenService.rotate(String, String)` | ✅ |
| `RefreshTokenService.revokeFamily(String)` | ✅ |
| `RefreshTokenService.revokeAllForUser(UUID)` | ✅ |
| `PasswordPolicy.check(String)` | ✅ |
| `PasswordHasher` | ✅ (in `auth.login.service`) |
| `SecurityRules` | ✅ (canonical in common-lib; local copy in auth-service — see finding below) |
| `JwtPublicKeyProvider` | ✅ (common-lib) |
| `CurrentUsers.get()` | ✅ (common-lib) |
| `InternalRestClientFactory.create` | ✅ (common-lib) |
| `TestTokens` | ✅ (common-lib test-jar) |

### Fixed (member-code, #2) — recorded per audit rules

1. `deactivateUser_self_returns400` → renamed `deactivateUser_self_returns403`; expectation aligned to **403 `ACCESS_DENIED`** (matches `GlobalExceptionHandler.handleCannotDeactivateSelf`; MEMBER_02 Prompt 1; SRS gap filled by decided rule).
2. `ProfileControllerTest.updateProfile_invalidPhone_returns400` → 400 `VALIDATION_ERROR` confirmed by `PhoneNumberNormalizer` guard in `ProfileController`.

### Local Duplicates (auth-service) — not blockers, document for Phase 2

| Duplicate | Notes |
|---|---|
| `com.swiftdrop.auth.exception.GlobalExceptionHandler` | Local handler; does not extend common-lib's. **Send back to #2:** migrate to common-lib handler in Phase 2 |
| `com.swiftdrop.auth.security.SecurityRules` | Local copy; canonical is common-lib's. **Send back to #2:** remove local copy in Phase 2 |
| `com.swiftdrop.auth.exception.InvalidPhoneException` | Local phone exception wrapper; acceptable until common-lib exposes a phone-specific type |
| `OutboxService` / audit stand-ins | Expected; #6 (outbox/audit owner) has not started. Document only |

---

## 6. api-gateway

| Check | Status | Evidence |
|---|---|---|
| `./mvnw verify` passes | ✅ | BUILD SUCCESS (confirmed in prior session) |
| Uses `spring-cloud-starter-gateway-server-webflux` | ✅ | Artifact name verified per 2025.0.x rename |

---

## 7. mock-merchant

| Check | Status | Evidence |
|---|---|---|
| `node --test` (7 tests) | ✅ | 7 pass |
| `node server.js` + `POST /x` → 200 | ✅ | Confirmed |
| `/healthz` → 200 | ✅ | Confirmed |
| No `innerHTML` in UI code | ✅ | Grep clean |
| Dockerfile non-root (`USER node`) | ✅ | Present |
| Dockerfile `HEALTHCHECK` | ✅ | Present |
| Base image `node:22-alpine` | ✅ | Confirmed |

---

## 8. SPA (frontend)

| Check | Status | Evidence |
|---|---|---|
| `npm ci` | ✅ | Installs clean |
| `npm test -- --coverage` | ✅ | 20 tests pass, coverage reported |
| `npm run build` | ✅ | Build success, `dist/` produced |
| `src/api/http.js` exports `apiRequest`, `apiForm`, `apiBlob`, `ApiError` | ✅ | Also exports token helpers (`setAccessToken`, etc.) — acceptable, spec names all present |
| Route table uses `import.meta.glob` | ✅ | Confirmed |
| "Not built yet" fallback present | ✅ | Confirmed |
| `src/components/GoogleLoginButton.jsx` exists | ✅ | Confirmed |
| Login page imports `GoogleLoginButton` | ✅ | Confirmed |
| Register page imports `GoogleLoginButton` | ✅ | Confirmed |

---

## 9. Cross-Part / Token Proof

| Check | Status | Evidence |
|---|---|---|
| Token structure (RS256, `kid=swiftdrop1`, `iss=swiftdrop`, `aud=swiftdrop-api`) | ✅ spec | `JwtServiceTest` asserts all these; `kid` confirmed `"swiftdrop1"` |
| Claims: `sub`, `role`, `typ`, `pwc`, `jti` present | ✅ spec | Covered by `JwtServiceTest` |
| No PII in token payload | ✅ spec | Only UUID `sub`, not email/name |
| `TestTokens` accepted by common-lib `SecurityRules` | ✅ | `BareServiceStartupTest` in common-lib confirms |

> **Note:** Live end-to-end token proof (auth-service → common-lib decoder against running infra) requires Docker. Docker compose config validates (exit 0). Full live smoke test deferred to leader's infra check.

---

## 10. Infra (Docker Compose)

| Check | Status | Evidence |
|---|---|---|
| `docker compose --env-file .env.example config` | ✅ | Exit 0 |
| `init-env` script exists | ✅ | Present in repo root |
| `generate-keys` script exists | ✅ | Present in repo root |
| Six databases / least-privilege users | 🔲 NOT RUN | Requires Docker daemon — deferred to leader's machine |
| `auth_user` cannot connect to another DB | 🔲 NOT RUN | Deferred to leader's machine |
| All services healthy after `compose.infra.yaml up -d` | 🔲 NOT RUN | Deferred to leader's machine |

---

## 11. Items to Send Back / Leader Actions

### Send back to #8 (CI owner)
1. **ci.yml job names do not match master 12.1.** Rename matrix jobs so displayed check names are exactly `common`, `api-gateway`, `auth-service`, `parcel-service`, `tracking-service`, `payment-service`, `notification-service`, `webhook-relay-service`, `frontend`. Add a standalone `common` job covering `common-core` and `common-lib`. Without this, branch protection cannot require the nine spec-mandated check names.

### Send back to #2 (auth-service owner — Phase 2, non-blocking)
1. Remove local `com.swiftdrop.auth.security.SecurityRules`; use common-lib's.
2. Migrate `com.swiftdrop.auth.exception.GlobalExceptionHandler` to extend/use common-lib's handler.

### Leader actions (non-code)
1. **Set branch protection on `develop`:** require PR, require all nine CI status checks, require 1 reviewer (leader).
2. **Review and merge `fix/gate1` → `develop`** after pipeline is confirmed green.
3. **Run infra live check** (items in section 10) on a machine with Docker.

---

## 12. Summary Table

| Area | Result |
|---|---|
| CI pipeline on `fix/gate1` | ✅ Triggered; pending confirmation |
| Repo foundation | ✅ (PEM keys noted, non-blocking) |
| common-core | ✅ |
| common-lib | ✅ |
| auth-service (after fixes) | ✅ 153 tests, JaCoCo OK |
| api-gateway | ✅ |
| mock-merchant | ✅ |
| SPA (frontend) | ✅ |
| Master 8.1 signatures | ✅ All present |
| CI job names match spec | ❌ Send back to #8 |
| Branch protection on `develop` | ❌ Leader action required |
| Infra live smoke (Docker) | 🔲 Deferred to leader's machine |
