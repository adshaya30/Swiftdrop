# Member #1 Common Library Adoption Plan (`M1-adopt-common.md`)

**Date:** 2026-11-06  
**Auditor:** Member #1 (Auth & SPA Foundation Lead)  
**Spec Reference:** Master Prompt Sections 7, 8.1, Prompt 1, Prompt 3

---

## 1. Context

Member #3 and Member #6 are responsible for implementing and publishing `com.swiftdrop:common-core` and `com.swiftdrop:common-lib`. Currently, both modules exist in `backend/` with `package-info.java` only.
Per master prompt instructions:
> "If common-lib is not merged yet, build everything that does not need it, and code against the signatures in master 8.1... Identify all duplicate classes intended for common-core or common-lib, mark them with `// TODO(common): replace with common-core/common-lib`, and record them in `docs/audit/M1-adopt-common.md`."

---

## 2. Inventory of Classes Awaiting Common Library Adoption

| Class in `auth-service` | Target Common Class | Target Module | Owner | Current Status / Action |
|---|---|---|---|---|
| `com.swiftdrop.auth.security.SecurityRules` | `com.swiftdrop.common.security.SecurityRules` | `common-lib` | #3 | Local interface defined. Replace once #3 merges security rules. |
| `com.swiftdrop.auth.security.JwtPublicKeyProvider` | `com.swiftdrop.common.security.JwtPublicKeyProvider` | `common-lib` | #3 | Local interface defined and implemented by `JwtService`. |
| `com.swiftdrop.auth.entity.UserRole` | `com.swiftdrop.common.enums.UserRole` | `common-core` | #3 | Local enum defined. Replace with common-core enum. |
| `com.swiftdrop.auth.entity.UserStatus` | `com.swiftdrop.common.enums.UserStatus` | `common-core` | #3 | Local enum defined. Replace with common-core enum. |
| `com.swiftdrop.auth.entity.AuthProvider` | `com.swiftdrop.common.enums.AuthProvider` | `common-core` | #3 | Local enum defined. Replace with common-core enum. |
| `com.swiftdrop.auth.entity.MerchantStatus` | `com.swiftdrop.common.enums.MerchantStatus` | `common-core` | #3 | Local enum defined. Replace with common-core enum. |
| `com.swiftdrop.auth.exception.GlobalExceptionHandler` | `com.swiftdrop.common.error.GlobalExceptionHandler` | `common-lib` | #3 | Local exception handler following RFC 7807 Problem Details. |
| `com.swiftdrop.auth.exception.DomainException` | `com.swiftdrop.common.error.DomainException` | `common-core` | #3 | Local domain exceptions in place. |
| `com.swiftdrop.auth.service.OutboxService` | `com.swiftdrop.common.events.OutboxService` | `common-lib` | #6 | Local outbox publisher for auth events. |
| `com.swiftdrop.auth.domain.OutboxEvent` | `com.swiftdrop.common.events.OutboxEvent` | `common-lib` | #6 | Local entity mapping shared `outbox_events` table. |

---

## 3. Migration Instructions for Future Turns

When Member #3 and Member #6 merge `common-core` and `common-lib`:
1. Remove duplicate local interfaces `SecurityRules` and `JwtPublicKeyProvider` and switch imports to `com.swiftdrop.common.security.*`.
2. Replace local enums in `com.swiftdrop.auth.entity` with `com.swiftdrop.common.enums.*`.
3. Switch outbox publishing to `com.swiftdrop.common.events.OutboxService`.
4. Re-verify with `mvn -pl auth-service -am verify`.
