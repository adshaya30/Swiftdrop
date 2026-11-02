# Authentication and Session Management — Design Patterns & Principles

## 1. Overview and Architecture

The SwiftDrop Authentication Service (`auth-service`) provides customer registration, email/password authentication, account lockout protection, RS256 JWT access token issuance, sliding-window refresh token rotation with concurrent-tab grace handling and replay detection, session termination (logout), and automated housekeeping purge jobs.

### Microservices Data Boundary

In accordance with microservices best practices:
- **Strict Data Encapsulation**: The `auth-service` database (`swiftdrop_auth`) exclusively owns user credentials (`users`) and session state (`refresh_tokens`). No other microservice reads from or writes to these tables.
- **Stateless Downstream Verification**: Downstream microservices (Parcel, Courier, Merchant, Tracking) never query `auth-service` or its database to authenticate requests. Instead, they verify self-contained RS256 JWT access tokens statelessly using the shared public key (`kid=swiftdrop-1`).
- **Minimal Claims Footprint**: Access tokens omit sensitive personal identifiable information (PII) such as email, phone numbers, or password hashes. Tokens only carry `sub` (user UUID), `role` (`CUSTOMER | MERCHANT | COURIER | ADMIN`), and conditional flags (`pwc`, `mid`).

---

## 2. Design Patterns Implemented in Codebase

### Pattern 1: Controller-Service-Repository Layering
- **Where Implemented**:
  - Controller: [`AuthController.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/controller/AuthController.java)
  - Services: [`RegistrationService.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/service/RegistrationService.java), [`LoginService.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/service/LoginService.java), [`RefreshTokenService.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/service/RefreshTokenService.java), [`RefreshTokenCleanupService.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/service/RefreshTokenCleanupService.java)
  - Repositories: [`UserRepository.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/repository/UserRepository.java), [`RefreshTokenRepository.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/repository/RefreshTokenRepository.java)
- **Why Used**: Separates HTTP request handling and header/cookie management from core domain business rules and database persistence.
- **Trade-offs**: Introduces more classes and interfaces, but provides isolation, testability, and clear separation of concerns.

### Pattern 2: Dependency Injection & Inversion of Control
- **Where Implemented**: Throughout all Spring components ([`SecurityConfig.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/config/SecurityConfig.java), [`SchedulingConfig.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/config/SchedulingConfig.java)).
- **Why Used**: Decouples component construction from usage. Enables deterministic time testing by allowing tests to inject fixed `java.time.Clock` instances rather than relying on non-deterministic real-time clock reads.
- **Trade-offs**: Requires a dependency injection framework (Spring Framework).

### Pattern 3: DTO and Validation Separation
- **Where Implemented**:
  - Request DTOs: [`RegistrationRequest.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/dto/request/RegistrationRequest.java), [`LoginRequest.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/dto/request/LoginRequest.java)
  - Response DTOs: [`UserResponse.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/dto/response/UserResponse.java), [`LoginResponse.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/dto/response/LoginResponse.java), [`RefreshResult.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/dto/response/RefreshResult.java)
- **Why Used**: Prevents mass assignment vulnerabilities and separates the database model from API contracts. Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Pattern`) enforces strict input constraints before business logic runs.
- **Trade-offs**: Requires mapping between DTOs and JPA Entities.

### Pattern 4: Repository Abstraction (Spring Data JPA)
- **Where Implemented**: [`UserRepository.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/repository/UserRepository.java), [`RefreshTokenRepository.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/repository/RefreshTokenRepository.java)
- **Why Used**: Provides type-safe queries and abstracts SQL dialect specifics. Includes row-level pessimistic locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) to eliminate race conditions during concurrent token rotation.
- **Trade-offs**: High-volume queries must be carefully indexed to prevent table lock escalation.

### Pattern 5: Single Responsibility Principle (SRP)
- **Where Implemented**:
  - `RegistrationService`: Handles only new account creation and phone/email uniqueness verification.
  - `LoginService`: Handles credential verification, failed attempt counters, and 15-minute account lockout logic.
  - `RefreshTokenService`: Handles token rotation, multi-tab grace window (10s), and replay revocation.
  - `RefreshTokenCleanupService`: Handles automated retention cleanup (purging tokens > 7 days post-expiry).
  - `JwtTokenService`: Dedicated strictly to cryptographic RS256 token signing and verification.
- **Why Used**: Ensures modifications to one aspect of authentication do not destabilize unrelated authentication features.

### Pattern 6: Reusable Global Exception Handling (RFC 9457 Problem Details)
- **Where Implemented**: [`GlobalExceptionHandler.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/exception/GlobalExceptionHandler.java)
- **Why Used**: Standardizes all client error responses into RFC 9457 Problem Details format (`type`, `title`, `status`, `detail`, `code`, `traceId`, `timestamp`). Replaces opaque stack traces with clean machine-readable schemas.
- **Trade-offs**: Custom exceptions must be explicitly registered in the handler.

### Pattern 7: Selective Transaction Rollback Exclusion
- **Where Implemented**: [`RefreshTokenService.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/service/RefreshTokenService.java) (`@Transactional(noRollbackFor = {UnauthenticatedException.class})`)
- **Why Used**: When a refresh token replay attack is detected, the service revokes the entire token family in the database and then throws `UnauthenticatedException` (401). By excluding this exception from transaction rollback, the revocation is permanently committed before the 401 response reaches the attacker.
- **Trade-offs**: Requires deliberate exception hierarchy design.

### Pattern 8: Scheduled Housekeeping / Purge Pattern (FR-AUTH-026)
- **Where Implemented**: [`RefreshTokenCleanupService.java`](file:///backend/auth-service/src/main/java/com/swiftdrop/auth/service/RefreshTokenCleanupService.java)
- **Why Used**: Periodically purges stale tokens whose 7-day retention period has passed. Uses bounded batch deletion (`LIMIT :batchSize`) to prevent long transaction locks on high-traffic databases.
- **Trade-offs**: Requires `@EnableScheduling` and dedicated index on `expires_at`.

---

## 3. Frontend Architecture and Patterns

### Pattern 9: Zero Local Storage Policy (In-Memory Token Architecture)
- **Where Implemented**: [`axios.js`](file:///frontend/src/api/axios.js), [`AuthContext.tsx`](file:///frontend/src/context/AuthContext.tsx)
- **Why Used**: Completely eliminates XSS token theft vectors by refusing to store JWT access tokens in `localStorage` or `sessionStorage`. Tokens reside strictly in JavaScript memory.
- **Trade-offs**: Page refresh clears memory; mitigated by silent session recovery using HttpOnly `sd_refresh` cookies.

### Pattern 10: Single-Flight Axios Response Interceptor with Request Queuing
- **Where Implemented**: [`axios.js`](file:///frontend/src/api/axios.js)
- **Why Used**: When multiple concurrent API calls fail with 401 (e.g., when an access token expires while several dashboard widgets are loading), only a single `/auth/refresh` call is dispatched. Remaining requests are queued in memory and replayed automatically once the new token arrives.
- **Trade-offs**: Additional queue management logic in the HTTP client.

### Pattern 11: Protected Route & Role-Based Route Guards
- **Where Implemented**: [`ProtectedRoute.tsx`](file:///frontend/src/components/ProtectedRoute.tsx), [`PublicOnlyRoute.tsx`](file:///frontend/src/components/PublicOnlyRoute.tsx)
- **Why Used**: Prevents unauthorized navigation before rendering protected components. Checks:
  1. `isInitializing`: Prevents screen flash while silent session recovery is underway.
  2. `isAuthenticated`: Redirects unauthenticated visitors to `/login` preserving intended destination in router state.
  3. `mustChangePassword`: Forces redirect to `/change-password` when temporary credentials are used.
  4. `allowedRoles`: Prevents role boundary violations (e.g. CUSTOMER navigating to `/admin`).
- **Trade-offs**: Client-side guards provide user experience routing only; server-side endpoints must independently enforce role authorization.

---

## 4. Cross-Member Integration Contracts & Dependencies

| Service / Area | Owner | Required Integration Contract |
| :--- | :--- | :--- |
| **OAuth Login States Table** | Member 2 | Table `oauth_login_states (id UUID PRIMARY KEY, state VARCHAR(64) UNIQUE, expires_at TIMESTAMPTZ, created_at TIMESTAMPTZ)` for FR-AUTH-026 hourly state purge. |
| **Password Change Endpoint** | Member 2 | `PATCH /api/v1/users/password` implementation and `/change-password` frontend form component. |
| **Token Verification Filter** | Member 3 | Import RS256 public key (`kid=swiftdrop-1`) in `common-lib` to validate JWT signatures across microservices. |
| **API Gateway Configuration** | Member 7 | Route `/api/v1/auth/**` to `auth-service:8080`, ensuring `X-Requested-With` header and `sd_refresh` cookie are forwarded without stripping. |
| **Docker Compose Secret Mount** | Member 8 | Configure production RSA private key mount (`JWT_PRIVATE_KEY_PATH`) in `compose.yaml` to replace local development keys. |

---

## 5. Technology Stack Alignment Note

- **Current Workspace State**: React 19, TypeScript, Tailwind CSS, Vite.
- **SRS v2.1 Specification**: React 18, JavaScript, Material UI, React Hook Form, Zod, Vitest.
- **Recommendation for Team**: To avoid destabilizing active frontend branches currently under development by group members, maintain the working TypeScript/Vite foundation while introducing `@testing-library/react` and `vitest` as devDependencies for automated component testing.

