# MEMBER #5: Parcel lifecycle and admin (3 prompts)

The master prompt (v3.0) and the SRS are already loaded in your session. Your load looks light but **Prompt 1 is on the critical path**: #4, #6 and #8 all call your code.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | Parcel lifecycle: state machine and access policy | **Yes**: #4 (cancel), #6 (merchant cancel, internal transition), #8 (courier updates) |
| 2 | Admin backend (parcels, reports, zones/pricing, audit API) + admin frontend | No |
| 3 | Documentation and submission pack (end of project) | No |

---

## PROMPT 1 of 3: ParcelTransitionService and ParcelAccessPolicy

You are **Member #5**. Package `com.swiftdrop.parcel.lifecycle.{service,repository,dto,security}`. Merge in **two pull requests**: PR A is the service (merge as soon as green), PR B the extra tests.

**Preconditions:** #4's parcel `domain` (`Parcel`, `ParcelRepository`) merged; common-core and common-lib security + events merged (mock any that are missing and say so).

### Requirements (FR-PAR-001, 002, 003, 017, 018; BR-03, 04, 06, 07, 16, 17)
1. **`ParcelTransitionService`** is the **only** code that changes `parcels.status`. Public API exactly as master 8.1: `transition(TransitionCommand)`, `cancelAsOwner(parcelId, CurrentUser, reason)`, `cancelAsAdmin(parcelId, admin, reason)`, `failAsAdmin(parcelId, admin, FailureReason, note)`, all returning `TransitionResult(parcelId, trackingNumber, status, sequence, statusChangedAt)`.
2. **Transition table (Table 2.3), by actor role:**
   - `COURIER` (the caller, delivery-service, has already checked the assignment): `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`, and `FAILED` from `CREATED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`.
   - `ADMIN`: cancel from `CREATED` (reason required, 10-300 chars); force-fail from `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY` only (note always required, 5-500).
   - `CUSTOMER` or `MERCHANT` owner: cancel from `CREATED` (reason optional, up to 300).
   - `DELIVERED`, `FAILED`, `CANCELLED` are terminal; steps cannot be skipped or reversed. Any other (from, to, actor) → 409 `INVALID_STATUS_TRANSITION` with a safe detail like "This parcel cannot move from CREATED to DELIVERED."
3. **Validation order:** first transition legality (409), then inputs (400 `VALIDATION_ERROR` with the field): `DELIVERED` needs `recipientName` 2-100 (BR-17); notes up to 500; `FAILED` needs a `failureReason` allowed for the stage by Table 2.4 (`CREATED`: `SENDER_UNAVAILABLE_AT_PICKUP, WRONG_OR_INCOMPLETE_ADDRESS, ACCESS_RESTRICTED, OTHER`; `PICKED_UP`/`IN_TRANSIT`: `PARCEL_DAMAGED, OTHER`; `OUT_FOR_DELIVERY`: `RECIPIENT_UNAVAILABLE, WRONG_OR_INCOMPLETE_ADDRESS, RECIPIENT_REFUSED, PARCEL_DAMAGED, ACCESS_RESTRICTED, OTHER`; admin override: `PARCEL_DAMAGED, OTHER`; use `FailureReason.allowedFor` from common-core); `OTHER` needs a note of 5-500; reason not allowed → 400 (field `failureReason`).
4. **Effects in one transaction:** update the parcel with optimistic locking (`@Version`); `status_sequence + 1`; `terminal_at` set for `DELIVERED`, `FAILED`, `CANCELLED` and never changed afterwards (BR-06); write the `parcel.status.changed` outbox row via `OutboxService` with data `parcelId, trackingNumber, sequence, previousStatus, status, statusChangedAt, actorUserId? (null for SYSTEM), actorRole, note?, failureReasonCode?, ownerType, customerId?, merchantId?, merchantReference?`. The staff note travels only here (BR-28). A lost optimistic-lock update → 409 `CONCURRENT_UPDATE` (FR-PAR-003); do not retry silently.
5. **`ParcelAccessPolicy.requireAccessible(parcelId, CurrentUser)`** (FR-PAR-017): CUSTOMER if `customer_id = sub`, MERCHANT if `merchant_id = mid`, ADMIN always, using `OwnershipPolicy`; an existing parcel that is not theirs → 404 `NOT_FOUND`. The role check (a role that is never allowed on the endpoint → 403) is done by `@PreAuthorize` on the controller before the policy runs.
6. `cancelAsOwner` uses the access policy and CREATED-only rule; `cancelAsAdmin` and `failAsAdmin` also write an audit event (`AuditEvents.record`, actions `PARCEL_CANCELLED_BY_ADMIN`, `PARCEL_FAILED_BY_ADMIN`, entityType `PARCEL`, details: previous status and reason code only, **no** note, no contact data).

### Tests (JUnit 5, Mockito, Testcontainers PostgreSQL/RabbitMQ)
- **TST-011:** a parameterised matrix over **every (from, to, actor)** combination: allowed ones succeed with correct side effects (new status, sequence + 1, `terminal_at` only when terminal, exactly one outbox row with the right payload), all others are rejected; `DELIVERED` without recipient name, `FAILED` without reason, `OTHER` without note, reason not allowed for the stage, admin cancel without a 10-300 reason, admin fail without a note: all 400; terminal parcels never change (BR-06); ADMIN override only from the three stages.
- Concurrency: two simultaneous transitions on one parcel → one succeeds, the other 409 `CONCURRENT_UPDATE`.
- Access policy: owner, non-owner (404), admin, wrong role (403 through a test controller); merchant owner uses `mid` not `sub`.
- Outbox payload has no contact data; the note appears only in the status event. Coverage at least 70 %.

### Done when
PR A merged, `mvn -pl backend/parcel-service -am verify` green. Tell #4, #6 and #8 in one line that `ParcelTransitionService` and `ParcelAccessPolicy` are merged. Report sent.

---

## PROMPT 2 of 3: admin backend and admin frontend

You are **Member #5**. Work in: parcel-service `lifecycle` slice (admin controllers, reports), pricing-service `admin` slice, tracking-service `audit` slice, and frontend `pages/admin/parcels`, `reports`, `zones`, `pricing`, `auditLogs`, `dashboard` with API files `src/api/adminParcels.js`, `adminReports.js`, `adminPricing.js`, `auditLogs.js`. Deliver backend first (PR A parcel, PR B pricing, PR C tracking audit), then frontend (PR D).

**Preconditions:** Prompt 1 merged; #4's pricing and parcel domain and `ParcelDetailMapper`; #9's tracking `domain` (`AuditLog` entity and repository). Mock what is missing; finish the audit API when #9 merges.

### Backend: parcel-service (FR-PAR-011, 012, 013; AT-25, 29, 38)
- `GET /api/v1/admin/parcels`: filters `status`, `ownerType`, `from`, `to` (Asia/Colombo), `q` = tracking number **or** merchant reference prefix, origin and destination district code; pagination; sorting on whitelisted fields (default `createdAt,desc`); no unbounded queries (JPA Specifications; indexes of 6.3).
- `GET /api/v1/admin/parcels/{id}`: `AdminParcelDetailDto` from #4's `ParcelDetailMapper` (contacts, addresses, fee, owner ids, merchant reference). Courier-related filters live in delivery-service.
- `POST /api/v1/admin/parcels/{id}/cancel` `{reason 10-300}` → `cancelAsAdmin`. `POST /api/v1/admin/parcels/{id}/fail` `{failureReason, note 5-500}` → `failAsAdmin` (allowed only from `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, else 409; reason `PARCEL_DAMAGED` or `OTHER`; normal notifications and webhook follow from the event).
- **Reports (FR-PAR-013):** `GET /api/v1/admin/reports/summary?from&to` → totals `created` (by `created_at`), `delivered`, `failed`, `cancelled` (by day of `terminal_at`), `inProgress` (parcels created in range that are not terminal), `successRate = delivered/(delivered+failed)` and `failureRate = failed/(delivered+failed)` over parcels whose `terminal_at` is in range, as percent with **one decimal**, 0 when the denominator is 0. `GET /api/v1/admin/reports/daily?from&to` → one row per **Asia/Colombo** day (zero rows included): `date, created, delivered, failed, cancelled`. Default range last 30 days, maximum 366 days (more → 400); `from`/`to` are `yyyy-MM-dd` Asia/Colombo inclusive. `GET /api/v1/admin/dashboard/parcels` → `{deliveredToday, failedToday}`.
- All admin only (others 403, unauthenticated 401, `typ=client` 403).

### Backend: pricing-service admin (FR-PRC-006, 007, 008)
- `GET /api/v1/admin/zones` (with districts), `POST /api/v1/admin/zones {code, name}` (code `[A-Z_]{2,20}`, immutable; unique code and name, a duplicate answers 400 `VALIDATION_ERROR`, list it as an SRS gap), `PATCH /api/v1/admin/zones/{id} {name, active}` (a zone that still has districts cannot be deactivated → 409 `ZONE_IN_USE`), `PUT /api/v1/admin/districts/{districtCode}/zone {zoneId}` (a district belongs to exactly one zone).
- `GET /api/v1/admin/pricing` (full matrix), `PUT /api/v1/admin/pricing/{originZoneId}/{destinationZoneId} {baseFee, perKgFee}` (decimals >= 0, 2 places, max 1,000,000.00; creates the pair if missing), `GET /api/v1/admin/size-surcharges` (list of `{size, surcharge, description}`), `PUT /api/v1/admin/size-surcharges` (body: list of `{size, surcharge}` for the three sizes; list the body shape as an assumption in your report).
- Changes affect **future** quotes and parcels only (BR-09).
- **Audit (FR-PRC-008):** zone, district move, pricing and surcharge changes write `audit.recorded` outbox events in the same transaction (actions `ZONE_CREATED, ZONE_UPDATED, DISTRICT_MOVED, PRICING_UPDATED, SURCHARGES_UPDATED`; no secrets or contact data).

### Backend: tracking-service audit (FR-TRK-005)
- `GET /api/v1/admin/audit-logs` (admin): filters `action`, `actorId`, `from`, `to`, paginated, newest first; entries `id, service, actorUserId, actorRole, action, entityType, entityId, details, occurredAt`. A daily scheduled purge deletes audit rows older than 1 year. Reads the `AuditLog` entity of #9's domain.

### Tests
- **TST-028 (S):** reports over a prepared data set across Asia/Colombo day boundaries (a parcel terminal at 18:45 UTC belongs to the next Colombo day), zero days, rates, default and maximum range. Do after the Must items.
- **TST-029 (your parts):** admin force-fail (allowed states, mandatory note, event emitted, courier can be deactivated afterwards is #2's), admin cancel needs a reason, zones/district/pricing rules (`ZONE_IN_USE`, immutable code, district move changes later quotes and not existing parcels: **AT-28**), audit entries written for each admin action (**AT-39** with no passwords or contact data).
- **TST-033 (your endpoints):** 401 unauthenticated, 403 for CUSTOMER / MERCHANT / COURIER and client tokens on every `/admin/**` endpoint of the three services (parameterised), 404 style does not apply.
- Coverage at least 70 % in each module.

### Frontend (PR D)
Use the shared client, components, `DashboardLayout` and formatters of #1; no charts, maps, ETAs or search bar (master 10).
1. **`/admin/parcels`** (`pages/admin/parcels`): table with all filters of FR-PAR-011, `StatusChip`, pagination, sort; link to detail.
2. **`/admin/parcels/:id`:** composes parcel admin detail, tracking timeline (`getTimeline`, `ParcelTimeline`), delivery info from `GET /admin/deliveries/{parcelId}` (`api/adminDelivery.js` by #7: courier and assignments), proof when DELIVERED (`ProofViewer`); actions **cancel** (only CREATED, reason 10-300) and **fail** (only `PICKED_UP`/`IN_TRANSIT`/`OUT_FOR_DELIVERY`, reason select limited to `PARCEL_DAMAGED`/`OTHER`, note 5-500), both with `ConfirmDialog`; refetch everything on every status event via `useTrackingStream`.
3. **`/admin/reports`:** date-range picker (default last 30 days, max 366), summary cards (created, delivered, failed, cancelled, in progress, success and failure rate) and a daily table. **No charts.**
4. **`/admin/zones`:** zones with districts, create zone, rename and activate/deactivate, move a district to another zone; `ZONE_IN_USE` message. **`/admin/pricing`:** matrix editor (base and per-kg per zone pair, two decimals) and size surcharges; confirmation dialog before saving (pricing change).
5. **`/admin/audit-logs`:** table with filters `action`, `actorId`, date range, pagination, details shown as read-only text.
6. **`/admin/dashboard`:** three **independent** cards (7.6): parcels (`deliveredToday`, `failedToday`) written by you, `DispatchCard` imported from `pages/admin/dispatch/DispatchCard.jsx` (#7) and `PendingMerchantsCard` from `pages/admin/merchants/PendingMerchantsCard.jsx` (#3), each with its own loading and error state; a failure of one card never blanks the others. If a card component is not merged yet, show a placeholder tile and swap it later.
7. API files: `adminParcels.js`, `adminReports.js`, `adminPricing.js`, `auditLogs.js`. Tests (`describe("TST-040 ...")`): filters call the API correctly, fail/cancel dialogs validate and confirm, report date limits, pricing form validation, dashboard card isolation. 360 px usable, accessible.

### Done when
All backend endpoints and pages work, `mvn verify` green for the three modules, `npm test -- --coverage` and `npm run build` pass, report sent. Scenarios AT-25, AT-28, AT-29, AT-38, AT-39 checked.

---

## PROMPT 3 of 3: documentation and submission pack (run near the end)

You are **Member #5**. Folders: `docs/design/` (your diagrams and `DESIGN.md`), `docs/test-reports/**`, `docs/contribution-sheet.md`. You own these files. Do not change code.

1. **Diagrams** (Mermaid, one file each in `docs/design/`): `parcel-state-diagram.md` (all statuses, Table 2.3 transitions with actors and conditions, terminal states), `admin-use-cases.md` (UC-A01 to UC-A10 for the dispatcher/admin actor).
2. **`docs/design/DESIGN.md`**: the design document required by SRS section 13: index and embedded copies (or links) of every diagram written by the members (use cases per actor group, ER diagrams per database plus a context diagram of cross-service id references from SRS 6.4, architecture and service diagram from 3.1 and 3.3, integration design 3.4-3.6, sequence diagrams for Flows 4.2-4.6 and, if built, the AI flow 11.4, the design-patterns note, deployment diagram) with short captions, and a note that the SRS is appendix A. Check that every diagram exists; list any missing one with the owner in your report. Add a one-line instruction for exporting the Markdown to PDF.
3. **Test reports (TST-002):** a script `docs/test-reports/collect.sh` (LF endings, no host tools beyond Maven, Git and Node) that, after `mvn verify` in each backend module and `npm test -- --coverage` in `frontend`, copies the Surefire HTML/XML reports and JaCoCo reports to `docs/test-reports/<service>/` and generates **`docs/test-reports/SUMMARY.md`** (one page): total tests, passed, failed, skipped, line coverage per module against the 70 % gate (frontend against 60 % (S)), date and commit hash. Run it once and commit the result; document in the file how to rerun it.
4. **Contribution sheet (`docs/contribution-sheet.md`, exportable to PDF):** one row per member: name (leave `TODO` placeholders), service or package owned (from master section 7), modules and files owned, tests written (TST IDs), demo part (AT IDs in demo order from SRS Appendix A: 1-11 customer, 12-16 merchant, 17-20 dispatcher and courier, 21-24 security and integration), plus Git evidence generated from `git shortlog` and `git log --stat` per folder. Do not guess names.
5. Check TST-002 and AT-37 requirements are met and list gaps in the report.

### Done when
Files committed, script runs cleanly, report lists missing diagrams or failing coverage with owners.
