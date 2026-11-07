# MEMBER #4: Quote and create delivery (3 prompts)

The master prompt (v3.0) and the SRS are already loaded in your session. You are the migration lead of **parcel-service and pricing-service**.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | pricing-service (complete) + parcel-service foundation | **Yes**: #5 and #6 need the parcel skeleton, V1 and domain; #5 admin needs pricing domain |
| 2 | Parcel creation, idempotency, customer API, tests | **Yes**: #6 reuses `ParcelCreationService` |
| 3 | Customer frontend + diagrams | No |

---

## PROMPT 1 of 3: pricing-service and parcel-service foundation

You are **Member #4**. Deliver in **three pull requests**: PR A (both skeletons, migrations, domains) first and merge fast, then PR B (pricing core), then PR C (pricing tests and Docker polish if left).

**Preconditions:** #8 skeleton and parent pom merged; #3 common-core merged (common-lib security needed to run, not to compile your skeleton).

### PR A: skeletons, migrations, domains
1. **`backend/pricing-service`** and **`backend/parcel-service`**: `pom.xml` (as in #1's prompt; parcel adds OpenPDF `com.github.librepdf:openpdf` for #6), `Application` classes (`@EnableScheduling`), `Dockerfile` + `.dockerignore` (multi-stage via Maven wrapper, non-root, `TZ=UTC`, ports 8083 and 8082, `wget` HEALTHCHECK on `/actuator/health/readiness`), `application.yml` binding **all** SRS 9.2 variables of the service: pricing: `SPRING_PROFILES_ACTIVE, LOG_LEVEL, INTERNAL_API_KEY, SWAGGER_ENABLED, DB_*, JWT_PUBLIC_KEY_PATH, RABBITMQ_*, OUTBOX_POLL_INTERVAL_MS, MAX_PARCEL_WEIGHT_KG` (default 30); parcel: the common ones plus `PRICING_SERVICE_URL, AUTH_SERVICE_URL, PUBLIC_BASE_URL`. Same yml rules as #1's prompt (validate DDL, unknown JSON properties rejected, readiness = database only, graceful shutdown, Swagger by flag, structured logging).
2. **`SecurityRules` beans.** Pricing public paths: `GET /api/v1/public/districts`, `POST /api/v1/public/quotes`. Parcel: no public paths. Password-change whitelist: empty for both.
3. **Pricing `V1__init.sql`** per SRS 6.3: `zones` (code unique immutable, name unique, `active` default true), `districts` (code PK, name `VARCHAR(40)`, `zone_id` FK), `zone_pricing` (PK origin+destination, `base_fee`, `per_kg_fee` `NUMERIC(12,2)` >= 0), `size_surcharges` (size PK, surcharge >= 0, description `VARCHAR(80)`), `outbox_events`. **`V2__seed_reference_data.sql`** seeds Appendix B exactly: 6 zones (WESTERN, CENTRAL, SOUTHERN, NORTH_EAST, NW_NC, UVA_SAB with their names), 25 districts with the listed codes and names, **36** zone pricing rows from the base-fee matrix with per-kg fee 100 for the same zone and 150 otherwise, 3 size surcharges (SMALL 0.00 "30 x 20 x 10 cm", MEDIUM 150.00 "50 x 40 x 30 cm", LARGE 400.00 "80 x 60 x 50 cm"). A migration test asserts the counts 6, 25, 36, 3 and spot-checks values.
4. **Pricing `domain`:** entities `Zone`, `District`, `ZonePricing`, `SizeSurcharge` and repositories (used by #5's admin slice too).
5. **Parcel `V1__init.sql`** per SRS 6.3: `parcels` with every column (tracking number `VARCHAR(13)` unique; `customer_id` and `merchant_id` with a CHECK that exactly one is set; `merchant_reference`; partial unique index on `(merchant_id, merchant_reference)` where the reference is not null and status not in (`CANCELLED`,`FAILED`); status CHECK, `status_sequence` starting at 1; all `pickup_*` and `dropoff_*` columns with the limits of 6.3 plus `dropoff_contact_email`; `weight_kg NUMERIC(5,2)`; size; description; instructions; zone and district snapshots; `fee_base`, `fee_weight`, `fee_size`, `fee_discount`, `fee_total` `NUMERIC(12,2)`; `currency CHAR(3)` default LKR; `terminal_at`; timestamps and `version`; indexes `(customer_id, created_at desc)`, `(merchant_id, created_at desc)`, `(status, created_at)`, `(terminal_at)`), `idempotency_keys` (unique on `owner_type, owner_id, idempotency_key`; `request_hash CHAR(64)`, `response_status INT`, `response_body JSONB`, `parcel_id`, `created_at`), `outbox_events`.
6. **Parcel `domain`:** `Parcel` entity (`@Version`, embedded pickup and dropoff value objects, snapshots) and `ParcelRepository`, enums from common-core. #5's lifecycle slice and #6 use them.
7. Tests: Flyway migrates both databases on Testcontainers and `ddl-auto=validate` passes; both contexts start.

### PR B: pricing core (FR-PRC-001..005, 009)
- **`FeeCalculator`** exactly BR-08 with `BigDecimal`: `WeightFee = max(0, ceil(weightKg) - 1) x PerKg`, `fee = round((Base + WeightFee + SizeSurcharge) x (1 - DiscountPercent/100))`, `HALF_UP`, 2 decimals, `discountAmount = subtotal - total`. Never use floating point (configure Jackson to read `weightKg` as `BigDecimal` so `2.001` is detected).
- `POST /api/v1/public/quotes` (no auth): `originDistrictCode, destinationDistrictCode, weightKg, size` → `{currency, originZone{code,name}, destinationZone{code,name}, baseFee, weightFee, sizeSurcharge, discountAmount (0), total}`.
- Internal `POST /internal/v1/pricing/calculate` (`X-Internal-Key`): same body plus `discountPercent` (default 0, 0-50) → the quote fields plus `originDistrict{code,name}`, `destinationDistrict{code,name}`, `subtotal`, `discountPercent`.
- **FR-PRC-004 validation:** unknown district, invalid size, weight below 0.01, more than 2 decimals, or above `MAX_PARCEL_WEIGHT_KG` (this service is the **only** one that checks the maximum) → 400 `VALIDATION_ERROR` (field `weightKg` for weights); a district in an inactive zone → 422 `ZONE_NOT_SERVICEABLE`; a zone pair without a pricing row → 422 `PRICING_NOT_CONFIGURED`.
- `GET /api/v1/public/districts` → `{maxWeightKg, sizes[{size, surcharge, description}], districts[{code, name, zoneCode, zoneName}]}` for **active** zones only.
- OpenAPI annotations, DTOs and MapStruct, no hard-coded maximum anywhere.
- **Tests TST-010:** same zone and different zone; weights 1.00, 1.01, 2.00, 30.00; 0.00 and 30.01 rejected with 400 (field `weightKg`); more than 2 decimals; each size; discounts 0, 10, 50 %; `HALF_UP` cases; missing pricing row; inactive zone; unknown district; **AT-05** (800.00 and 650.00), **AT-06** (350.00 and 450.00), **AT-07** (subtotal 1,800.00, discount 180.00, total 1,620.00) as unit tests; internal endpoint requires the key; public endpoints need no token. Authorisation tests for the internal endpoint. Coverage at least 70 %.

### Done when
Both modules build and start, V1/V2 apply, `mvn -pl backend/pricing-service,backend/parcel-service -am verify` green. Tell #5, #6 (and #7 for the gateway routes) in one line that the foundations are merged. Report sent.

---

## PROMPT 2 of 3: parcel creation, idempotency and customer API

You are **Member #4**. Package `com.swiftdrop.parcel.create.{controller,service,repository,entity,dto,mapper,client,scheduler,security}`.

**Preconditions:** Prompt 1 merged; common-lib security and events merged (`OutboxService`, `CurrentUsers`, `InternalRestClientFactory`); #5's `ParcelTransitionService` and `ParcelAccessPolicy` for cancel (if missing: finish everything else, mock them, and report the cancel wiring as pending).

### Requirements
- **FR-PAR-004 `TrackingNumberGenerator`:** `SD-` + 10 characters from `0123456789ABCDEFGHJKMNPQRSTVWXYZ` with `SecureRandom`; unique; on a unique-constraint collision retry up to 5 times.
- **FR-PAR-005 `POST /api/v1/customer/parcels`** (role CUSTOMER; owner = `sub`): body `pickup {contactName, contactPhone, addressLine1, addressLine2?, city, districtCode, postalCode?}`, `dropoff {same plus contactEmail?}`, `parcel {weightKg, size, description?}`, `deliveryInstructions?`, `expectedFee`. Limits: names 2-100, `addressLine1` 1-150, `addressLine2` up to 150, city 1-80, postalCode 5 digits, description up to 200, deliveryInstructions up to 300, phones via `PhoneNumberNormalizer`, weight at least 0.01 with at most 2 decimals, size per BR-10. **Do not check the maximum weight**: pricing-service does and its 400 is returned unchanged. Pricing business errors (`ZONE_NOT_SERVICEABLE`, `PRICING_NOT_CONFIGURED`, 400) pass through unchanged; pricing down → 503. A different total → 409 `FEE_CHANGED` carrying the new fee (extension member `fee`), nothing created. Success: **201**, `Location: /api/v1/customer/parcels/{id}`, body `{id, trackingNumber, status:"CREATED", fee{currency, baseFee, weightFee, sizeSurcharge, discountAmount, total}, createdAt}`. Snapshot districts (code and name), zones (code and name), fees and currency on the parcel (BR-09). Writes `parcel.created` (data per SRS 3.5: `parcelId, trackingNumber, status, ownerType, customerId?, merchantId?, merchantReference?, originDistrictCode, originDistrictName, originZoneName, destinationDistrictCode, destinationDistrictName, destinationZoneName, pickupCity, dropoffCity, createdAt`; **no contact data**) to the outbox in the same transaction.
- **FR-PAR-006 idempotency (the only implementation; #6 reuses it):** header `Idempotency-Key` optional, 1-64 chars `[A-Za-z0-9_-]` (invalid → 400). Lookup by `(owner_type, owner_id, key)` happens **first**, before pricing and the fee comparison. Same key, same owner, same request hash (SHA-256 of the canonical JSON of the validated body including `expectedFee`: sorted keys, normalised numbers) within 24 h → the stored status and body with header `Idempotent-Replay: true`, nothing created. Same key, different hash → 422 `IDEMPOTENCY_KEY_REUSED`. A failed request (4xx or 5xx) stores nothing, so the key stays reusable. On success the key row, response body, parcel and outbox row are inserted in **one transaction**; a unique-constraint race is resolved by re-reading and replaying the stored response; keys older than 24 h are treated as absent and purged hourly (scheduler). No "in progress" state.
- **Publish for #6** exactly the signatures of master 8.1: `ParcelCreationService.create(CreationRequest)` with `CreationResult`, `IdempotencyService`, `TrackingNumberGenerator`, `PricingClient` (internal client using `InternalRestClientFactory`, base URL `PRICING_SERVICE_URL`) and `ParcelDetailMapper` (`ParcelDetailDto`, `AdminParcelDetailDto`, `InternalParcelDetailDto`). Document them in a short Javadoc/README in the slice.
- **FR-PAR-009 customer API:** `GET /api/v1/customer/parcels` (own parcels; filters `status`, `from`, `to` as Asia/Colombo dates inclusive, `q` = tracking-number prefix; BR-26 pagination; sort `createdAt`, `status`, `trackingNumber`, default `createdAt,desc`); `GET /api/v1/customer/parcels/{id}` (detail per SRS 7.5: addresses, contacts, parcel data, fee breakdown, status, zones, `merchantReference`, `ownerType`; **no** history, courier or proof); `POST /api/v1/customer/parcels/{id}/cancel` (optional `reason` up to 300) → `ParcelTransitionService.cancelAsOwner`, answers `200 {id, trackingNumber, status, statusChangedAt}`; a non-CREATED parcel → 409 `INVALID_STATUS_TRANSITION`. Another customer's parcel → 404 (role check first, then `ParcelAccessPolicy`).
- **FR-PAR-015 (S, only after all Must items):** `GET /api/v1/customer/dashboard/summary` → counts of active and delivered parcels and the five most recent parcels.
- `SecurityRules`/`@PreAuthorize`: CUSTOMER only on `/customer/**`; others 403.

### Tests (Testcontainers PostgreSQL + RabbitMQ, WireMock for the pricing 7.4 contract, Spring Security Test + TestTokens)
- **TST-012:** tracking-number format and generator (uniqueness over many values, collision retry, 5th failure), district and size limits, weight format and minimum (maximum belongs to TST-010), idempotency hash (key order independent, includes `expectedFee`).
- **TST-021:** success; `FEE_CHANGED`; validation errors with `errors[]`; idempotent replay **also after a price change** (WireMock returns a new fee, the replay still returns the original 201); key reuse with another body → 422; a failed request leaves the key reusable; concurrent duplicate requests create exactly one parcel; weight above max passes through the pricing 400; atomicity (force a failure after the parcel insert, for example by making `OutboxService` throw: no parcel, no key, no outbox row remain); the `parcel.created` outbox payload has no contact data. (The merchant cases are #6's.)
- **TST-031 (your endpoints):** another customer's parcel → 404 on read and cancel, MERCHANT / COURIER / ADMIN tokens → 403 on `/customer/**`, unauthenticated → 401, `typ=client` → 403.
- Cancel tests use a Mockito mock of `ParcelTransitionService` until #5 merges, then the real one. Scenarios AT-08 and AT-09 checked. Coverage at least 70 %.

### Done when
Endpoints work, `mvn -pl backend/parcel-service -am verify` green, #6 can compile against `ParcelCreationService`. Report sent.

---

## PROMPT 3 of 3: customer frontend and diagrams

You are **Member #4**. Folder `frontend/src/pages/customer/**` (dashboard, new, list, detail); API files `src/api/quotes.js` and `src/api/customerParcels.js`. Use only the shared client, components, layouts and formatters of #1 (master 8.1). Customer pages use `DashboardLayout` or the customer white-header layout as the route table says.

### Pages
1. **`/customer/dashboard`:** summary cards (active, delivered) and the five most recent parcels from `GET /customer/dashboard/summary` (S: if the endpoint is not built yet, show a list from `GET /customer/parcels?size=5`), quick actions "New delivery" and "Track". Loading, empty, error states.
2. **`/customer/parcels/new`: three steps (details, quote, confirm)** (FR-UI-004): step 1 form with React Hook Form + Zod (pickup and drop-off contact name 2-100, phone, `addressLine1` 1-150, `addressLine2`, city 1-80, district select from `GET /public/districts`, postalCode 5 digits, drop-off email optional; parcel weight at least 0.01 with 2 decimals and **maximum = `maxWeightKg` from the districts endpoint (never hard-coded)**, size select showing description and surcharge, description up to 200, instructions up to 300); step 2 calls `POST /public/quotes` **debounced 400 ms** and shows the breakdown (base, weight, size surcharge, total, "LKR", values from the server only, no client arithmetic) with specific messages for `ZONE_NOT_SERVICEABLE`, `PRICING_NOT_CONFIGURED`, weight errors; step 3 summary and Confirm: generate **one** `Idempotency-Key` UUID (`crypto.randomUUID()`) per confirmation screen, disable Confirm while the request runs, send `expectedFee` = quoted total. On `409 FEE_CHANGED` show the new fee and ask the customer to confirm again with a **new key**; server `errors[]` mapped to fields without losing input; on 201 go to the detail page. Back/next keep input.
3. **`/customer/parcels`:** table or cards with filters `status`, `from`, `to`, `q`, sorting, pagination, `StatusChip`, link to detail; empty and error states.
4. **`/customer/parcels/:id`:** composes (SRS 7.6) parcel detail (`GET /customer/parcels/{id}`), timeline (`getTimeline` from `api/tracking.js`, rendered with `ParcelTimeline`), proof only when DELIVERED (`ProofViewer` from #8), receipt (`ReceiptButton` from #6), cancel (only CREATED, `ConfirmDialog`, optional reason up to 300, handles `INVALID_STATUS_TRANSITION`). **Refresh rules (FR-UI-007):** use `useTrackingStream(trackingNumber, {onStatus})` from #9 and refetch detail, timeline and proof on every status event and polling result; right after creation the timeline can answer 404 for a few seconds, so show an "updating" timeline and retry every 2 s for up to 30 s. If #9, #8 or #6 components are not merged yet, keep a thin adapter in your own folder and swap it later; do not copy their code.
5. **API files:** `quotes.js` (`getDistricts`, `postQuote`), `customerParcels.js` (`createParcel` with idempotency key, `listParcels`, `getParcel`, `cancelParcel`, `getDashboardSummary`).

### Tests (Vitest + Testing Library, `describe("TST-040 ...")`)
New-delivery flow: validation, quote debounce (fake timers), step navigation, one idempotency key per confirmation screen and a new key after `FEE_CHANGED`, Confirm disabled while pending, double click sends one request, the max weight comes from the districts response; list filters; detail refetch on status event and the timeline-404 retry; cancel confirmation. Accessibility (labels, keyboard, focus, contrast), no horizontal scroll at 360 px on list and wizard.

### Docs (Mermaid in `docs/design/`)
- `create-delivery-sequence.md`: Flow 4.2 (SPA, gateway, parcel-service, pricing-service, database, outbox, RabbitMQ, consumers) including the idempotency lookup first and the `FEE_CHANGED` branch.
- `customer-use-cases.md`: UC-C01 to UC-C11 and UC-P01 with alternative flows.
- `pricing-er.md` and `parcel-er.md`: ER diagrams of both databases (SRS 6.3, 6.4).

### Done when
Wizard, list, detail and dashboard work against the running services, `npm test -- --coverage` and `npm run build` pass, diagrams committed, report sent. Scenarios AT-05, AT-08, AT-09, AT-25 (customer part) checked.
