# Architecture and Design Patterns (`docs/design/design-patterns.md`)

This document records the architectural and design patterns employed across SwiftDrop microservices and specifically within `auth-service` per Master Prompt Section 8.2 and Prompt 3.

---

## 1. Pattern Catalog

| Pattern | Description | Where in Code |
|---|---|---|
| **Layered Architecture** | Separation of concerns across Controller (REST API), Service (domain orchestration), Repository (data access), and Domain Entity models. | `com.swiftdrop.auth.login.{controller,service}`, `com.swiftdrop.auth.domain` |
| **DTO & Data Mapper** | Decoupling domain entities from HTTP wire contracts using immutable record DTOs. | `com.swiftdrop.auth.dto.*`, `com.swiftdrop.auth.login.dto.*` |
| **Transactional Outbox** | Ensuring reliable event publishing without two-phase commit by storing events in the database within the domain transaction. | `com.swiftdrop.auth.domain.OutboxEvent`, `com.swiftdrop.auth.service.OutboxService` |
| **Idempotency Key** | Safe retries for mutation endpoints via unique header tokens and replay caching. | `frontend/src/api/http.js` (`Idempotency-Key` header), `POST /api/v1/deliveries` |
| **Optimistic Locking** | Concurrency control using version columns (`@Version`) preventing lost updates on concurrent writes. | `com.swiftdrop.auth.domain.User.version`, `com.swiftdrop.auth.domain.Merchant.version` |
| **Pessimistic Locking** | Database row-level exclusive write locking (`SELECT ... FOR UPDATE`) preventing race conditions during token rotation. | `RefreshTokenRepository.findByTokenHashWithLock(tokenHash)` with `@Lock(LockModeType.PESSIMISTIC_WRITE)` |
| **Strategy / Adapter** | Encapsulating notification delivery providers (Email, SMS) behind uniform interfaces. | `NotificationSender` implementations in notification-service |
| **Policy Classes** | Dedicated domain policy evaluation isolating business invariants and access rules. | `com.swiftdrop.auth.login.service.PasswordPolicy`, `com.swiftdrop.auth.security.SecurityRules` |
| **API Gateway Pattern** | Reverse proxy entry point providing routing, TLS termination, and path-preserving forwarding. | `backend/api-gateway`, routing to `auth-service:8081`, `parcel-service:8082` |
