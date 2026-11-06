# Member #2 Shared Library Adoption Register (Phase 2)

**Auditor:** Member #2 (Google login, profile, admin users)  
**Spec Reference:** `docs/prompts/MEMBER_02.md`, Master Prompt v2.2 (Section 8.1 & 13.9)  
**Status:** Phase 1 complete. Awaiting merges from Member #3 (`common-core`, `common-lib`) and Member #6 (outbox & audit helpers).

---

## Shared Library Duplicates to Adopt in Phase 2

| # | Current Local Implementation | Target Shared Component | Provider | Impact & Action to Take upon Merge |
|---|-----------------------------|-------------------------|----------|-----------------------------------|
| **1** | `com.swiftdrop.auth.service.OutboxService` (`recordCourierChangedEvent`) | `com.swiftdrop.common.outbox.OutboxPublisher` / `com.swiftdrop.common.events.CourierChangedEvent` | **Member #6** | Replace local `OutboxEvent` repository writes with standard event publisher class. |
| **2** | `com.swiftdrop.auth.service.OutboxService` (`recordAuditEvent`) | `com.swiftdrop.common.audit.AuditEvents.record(...)` | **Member #6** | Replace local JSON audit serialization with shared `AuditEvents` helper. |
| **3** | `com.swiftdrop.auth.users.client.DeliveryClient` | `com.swiftdrop.common.client.InternalRestClientFactory` | **Member #3** | Switch manual `RestClient` configuration to the shared internal client factory injecting `X-Internal-Key` and timeouts automatically. |
| **4** | `ProfileService.normalisePhone` and `RegistrationService.normalisePhone` | `com.swiftdrop.common.util.PhoneNumberNormalizer` | **Member #3** | Replace duplicate regex stripping and E.164 verification with the shared normalizer. |
| **5** | `SecurityRules` contract in `com.swiftdrop.auth.security` | `com.swiftdrop.common.security.SecurityRules` | **Member #3** | Remove local interface stub and import shared interface. |

---

## Instructions for Phase 2 Transition

When Member #3 and Member #6 have merged their libraries into develop/main:
1. Re-run audit prompt with: `Continue with Phase 2 only.`
2. Replace local implementations with the shared dependencies from `common-core` and `common-lib`.
3. Verify tests and delete local helpers.
