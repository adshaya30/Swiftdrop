# SwiftDrop member prompts: index

Load `SWIFTDROP_MASTER_PROMPT.md` (v2.2) once per AI session, keep `docs/SRS.md` in the repo, then paste the member's prompts one at a time when their turn comes. Each prompt is self-contained and ends with tests, a "Done when" list and a report.

| Member | Prompt | Title | Type |
|---|---|---|---|
| #1 | 1 | auth-service foundation (skeleton, V1, domain, JwtService, RefreshTokenService) | backend, blocker |
| #1 | 2 | SPA foundation + login/register pages + frontend Docker | frontend, blocker |
| #1 | 3 | Login API, sessions, purge job, tests, design-patterns note | backend |
| #2 | 1 | Google login, profile, bootstrap admin, admin users (backend) | backend |
| #2 | 2 | callback, profile, change-password, admin users pages + diagrams | frontend + docs |
| #3 | 1 | common-core + common-lib security/web (3 PRs) | shared library, blocker |
| #3 | 2 | merchant slice: registration, approval, credentials, `/oauth/token`, internal endpoints | backend |
| #3 | 3 | merchant register/credentials/admin merchants pages + diagram | frontend + docs |
| #4 | 1 | pricing-service (complete) + parcel-service foundation | backend, blocker |
| #4 | 2 | parcel creation, idempotency, customer API | backend, blocker for #6 |
| #4 | 3 | customer frontend + diagrams | frontend + docs |
| #5 | 1 | ParcelTransitionService + ParcelAccessPolicy | backend, blocker |
| #5 | 2 | admin backend (parcels, reports, zones/pricing, audit API) + admin frontend | backend + frontend |
| #5 | 3 | documentation and submission pack (end) | docs |
| #6 | 1 | common-lib events, outbox, consumer helpers, audit events | shared library, blocker |
| #6 | 2 | merchant orders, receipts, dashboard, internal endpoints | backend |
| #6 | 3 | merchant frontend, Postman tools, diagrams | frontend + docs |
| #7 | 1 | delivery-service foundation, consumers, assignment, admin APIs | backend, blocker (PR A) |
| #7 | 2 | api-gateway | backend |
| #7 | 3 | dispatch and workload frontend + diagrams | frontend + docs |
| #8 | 1 | repository foundation and infrastructure (Day 1) | infrastructure, blocker |
| #8 | 2 | courier operations (backend + frontend) | backend + frontend |
| #8 | 3 | integration, full-stack verification, README | infrastructure + docs |
| #9 | 1 | tracking-service and notification-service foundations | backend, blocker |
| #9 | 2 | tracking features + public tracking frontend | backend + frontend, blocker for FE |
| #9 | 3 | notification features, cross-service tests, diagrams | backend + docs |
| #10 | 1 | tools/mock-merchant | tool, no dependencies |
| #10 | 2 | notification-service webhook slice | backend |
| #10 | 3 | webhook frontend + docs + security note | frontend + docs |

## Decisions these prompts made (check them)

1. **Google button** (`GoogleLoginButton`) is owned by **#2**, who owns the whole Google flow; #1's login and register pages import it. (Decided by the leader.)
2. **common-core additions** by #3: `PhoneNumberNormalizer`, `TrackingNumberFormat`, `ProblemBody`, `ApiHeaders`, all SRS 6.2 enums.
3. **SRS gaps** the prompts resolved (each member must list them in their report): admin deactivating self → 403 `ACCESS_DENIED`; duplicate zone code or name → 400; size-surcharge PUT body shape; merchant dashboard summary shape; recipient `statusChangedAt` in the lost-response case.
4. **mock-merchant** base image: **#10 decides** (Node 22 with no dependencies, or a slim image of their choice), reports the choice, and it must start from compose.
5. **Paths:** controllers map full paths (`/api/v1/...`, `/internal/v1/...`); the gateway forwards unchanged.
6. **CI/CD, monitoring and Kubernetes** are **not built** (SRS 1.2 says out of scope). #8 lists them in the README as future work.
7. **Merging:** each member merges their own PRs (master 12). Shared files need the owner's OK in the PR.
8. **Already done:** repo setup (#8 P1), and #1 and #2's work. They run the audit prompts (`AUDIT_MEMBER_01.md`, `AUDIT_MEMBER_02.md`) instead of their normal prompts.

## Setup already done: #8 Prompt 1

Paste this line **before** MEMBER_08 Prompt 1: "The repository setup already exists. Do not rebuild it. Audit it against this prompt, list what is missing or different, fix only that in one small PR, and skip anything that already matches."

## Audit prompts (members 1 and 2)

`AUDIT_MEMBER_01.md` and `AUDIT_MEMBER_02.md` replace their normal prompts. Each does: inventory, decision (fix in place by default), small fix PRs, adopt shared libraries after #3/#6 merge, verify, self-merge. #2 starts fixing only after #1's audit PRs are merged.
