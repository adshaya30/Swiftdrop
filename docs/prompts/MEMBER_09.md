# MEMBER #9: Tracking and notifications (3 prompts)

The master prompt (v3.0) and the SRS are already loaded in your session. You are the migration lead of **tracking-service and notification-service**.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | Foundations of tracking-service and notification-service | **Yes**: #5 (audit API) and #10 (webhook tables) need your skeletons, V1 and domain |
| 2 | tracking-service features + public tracking frontend | **Yes (frontend)**: `useTrackingStream`, `ParcelTimeline`, `tracking.js` are used by #4, #5, #6, #8 |
| 3 | notification-service features, cross-service tests, diagrams | No |

---

## PROMPT 1 of 3: foundations (both services)

You are **Member #9**. Deliver in **one pull request per service** and merge fast.

**Preconditions:** #8 skeleton and parent pom merged; common-core (#3) merged.

### For each of `backend/tracking-service` (port 8085) and `backend/notification-service` (port 8086)
1. `pom.xml` (as in #1's prompt; notification adds `spring-boot-starter-mail`), `Application` class (`@EnableScheduling`), `Dockerfile` + `.dockerignore` (multi-stage, non-root, `TZ=UTC`, one exposed port, `wget` HEALTHCHECK on readiness), `application.yml` (same rules as #1's prompt: all SRS 9.2 variables with defaults, validate DDL, unknown JSON rejected, readiness = database only, graceful shutdown, Swagger flag, structured logging, Prometheus (S)).
   - tracking: `SPRING_PROFILES_ACTIVE, LOG_LEVEL, INTERNAL_API_KEY, SWAGGER_ENABLED, DB_*, JWT_PUBLIC_KEY_PATH, RABBITMQ_*`. Public paths: `GET /api/v1/public/tracking/{trackingNumber}` and `/stream`.
   - notification: the same plus `AUTH_SERVICE_URL, PARCEL_SERVICE_URL, PUBLIC_BASE_URL, SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, SMTP_FROM, SMS_PROVIDER, WEBHOOK_SECRET_ENCRYPTION_KEY, WEBHOOK_ALLOW_PRIVATE_TARGETS, WEBHOOK_ALLOW_HTTP, WEBHOOK_TIMEOUT_SECONDS, WEBHOOK_MAX_ATTEMPTS, WEBHOOK_RETRY_DELAYS` (**all** of them, so #10 never edits the yml). notification has no outbox and no `processed_events`.
2. **`SecurityRules`** beans (no password-change whitelist). Fail fast at startup when `WEBHOOK_SECRET_ENCRYPTION_KEY` is missing or not Base64 of 32 bytes (hand this check to #10's code later; for now bind the property).

### tracking-service `V1__init.sql` and domain
- Tables per SRS 6.3: `tracking_parcels` (`parcel_id` PK, `tracking_number` unique, `status`, `status_sequence`, `owner_type`, `customer_id`, `merchant_id`, `origin_district_name`, `origin_zone_name`, `destination_district_name`, `destination_zone_name`, `courier_id`, `courier_first_name VARCHAR(50)`, `created_at`, `updated_at`), `tracking_events` (`id BIGSERIAL`, `parcel_id`, `sequence` with `unique(parcel_id, sequence)`, `from_status` null, `to_status`, `actor_user_id` null, `actor_role VARCHAR(10)`, `failure_reason_code VARCHAR(40)`, `note VARCHAR(500)`, `occurred_at`, `created_at`), `audit_logs` (`id BIGSERIAL`, `event_id` unique, `service VARCHAR(30)`, `actor_user_id`, `actor_role`, `action VARCHAR(60)`, `entity_type VARCHAR(30)`, `entity_id VARCHAR(40)`, `details JSONB`, `occurred_at`, `created_at`), `processed_events` (shared definition). **(S) FR-TRK-007:** in the same V1 add the trigger function and trigger that reject any `UPDATE` or `DELETE` on `tracking_events` (BR-06).
- `tracking.domain`: entities `TrackingParcel`, `TrackingEvent`, `AuditLog` and repositories. `TrackingEvent` repository exposes **no update or delete method** (FR-TRK-006; #5 reads `AuditLog` for the audit API).

### notification-service `V1__init.sql` and domain
- Tables per SRS 6.3: `notifications` (`id`, `event_id`, `event_type VARCHAR(40)`, `tracking_number VARCHAR(13)` null, `channel`, `recipient_kind`, `status`, `template_code VARCHAR(60)`, `destination_masked VARCHAR(100)`, `body_preview VARCHAR(300)`, `attempts INT`, `error_summary VARCHAR(200)`, `created_at`, `sent_at`, **unique `(event_id, channel, recipient_kind)`**), `merchant_webhook_config` (`merchant_id` PK, `url VARCHAR(500)`, `secret_encrypted BYTEA`, `enabled` default true, timestamps), `webhook_deliveries` (`id`, `event_id` **unique**, `merchant_id` indexed, `event_type VARCHAR(40)`, `tracking_number VARCHAR(13)` null, `payload JSONB`, `target_url VARCHAR(500)`, `status`, `attempt_count INT` default 0, `next_attempt_at` with an index on `(status, next_attempt_at)`, `last_http_status INT`, `last_error VARCHAR(200)`, `created_at`, `last_attempt_at`, `completed_at`).
- `notification.domain`: entity `Notification` and repository. The webhook entities are written by #10.

### Tests and done
Flyway applies on Testcontainers for both services and `ddl-auto=validate` passes; the immutability trigger rejects update and delete on `tracking_events` (S); both contexts start. `mvn -pl backend/tracking-service,backend/notification-service -am verify` green. Tell #5 and #10 in one line when merged. Report sent.

---

## PROMPT 2 of 3: tracking-service features and public tracking frontend

You are **Member #9**. Backend package `com.swiftdrop.tracking.core.{controller,service,repository,dto,mapper,messaging,security,scheduler}` (the audit-logs **API** is #5's slice, but the `audit.recorded` **consumer** is yours). Frontend `pages/public/**`, `src/api/tracking.js`, `pages/public/hooks/useTrackingStream.js`, `pages/public/components/ParcelTimeline.jsx`.

**Preconditions:** Prompt 1 merged; common-lib security and events (#3, #6) merged (`EventConsumerSupport`, `ProcessedEvents`).

### Backend (FR-TRK-001 to 004, 006; SRS 3.5, 3.8; AT-10, 11)
1. **Consumer on `tracking.events`:**
   - `parcel.created` → create `tracking_parcels` (status `CREATED`, sequence 1, names of districts and zones) and the first timeline row (`sequence 1`, `from_status` null, `to_status CREATED`, `actor_role` = the event's `ownerType`, `actor_user_id` = `customerId` or null).
   - `parcel.status.changed` → **unknown parcel = retryable error**; **stale sequence is acknowledged and ignored**; append a `tracking_events` row (`sequence`, `from_status` = `previousStatus`, `to_status`, actor, `failure_reason_code`, `note`, `occurred_at` = `statusChangedAt`), update `tracking_parcels` status, sequence, `updated_at`; **push to the SSE emitters only after the transaction commits** (transaction synchronisation).
   - `delivery.assigned` → set `courier_id` and `courier_first_name`; `delivery.unassigned` → clear both. (Unknown parcel → retryable.)
   - `audit.recorded` → append `audit_logs` (unique `event_id`, idempotent).
   - Every handler inserts into `processed_events` in the same transaction; duplicates acknowledged and skipped; 3 attempts then dead-letter (helpers from #6).
2. **FR-TRK-002 `GET /api/v1/public/tracking/{trackingNumber}`** (no auth): `{trackingNumber, status, createdAt, updatedAt, origin{districtName, zoneName}, destination{districtName, zoneName}, history[{status, at, failureReasonLabel?}]}` and **nothing else** (BR-20: no names, phones, emails, addresses, notes, proof, courier identity, fees or merchant data); `Cache-Control: no-store`. **Malformed and unknown numbers both return 404 `NOT_FOUND`, never 400** (use `TrackingNumberFormat`).
3. **FR-TRK-003 `GET /api/v1/public/tracking/{trackingNumber}/stream`** (SSE, SRS 3.8): 404 before the stream starts for unknown or malformed numbers; event `snapshot` (same JSON as the public view), then event `status` (same JSON) for each `parcel.status.changed` of that parcel, and a comment `: heartbeat` every 25 s; in-memory map `trackingNumber → emitters` (single instance); register the emitter **before** sending the snapshot so no event is lost; complete the stream after a terminal-status event and complete immediately if the parcel is already terminal; maximum stream lifetime 30 minutes; clean up on completion, timeout and error; supports at least 200 concurrent streams (S: 500); `Cache-Control: no-store`, `X-Accel-Buffering: no`. Target: commit to browser p95 within 3 s.
4. **FR-TRK-004 `GET /api/v1/tracking/parcels/{parcelId}/timeline`** (roles CUSTOMER, MERCHANT, COURIER, ADMIN): `{parcelId, trackingNumber, status, courierFirstName?, history[{sequence, fromStatus, toStatus, at, actorRole, note, failureReasonCode, failureReasonLabel}]}`. Customer: `customer_id = sub`; merchant: `merchant_id = mid`; courier: currently assigned (`courier_id = sub`); admin: any; otherwise 404 (role check first, then 404 for non-owners). `courierFirstName` for customers and merchants; admins also get `courierId`; couriers get neither. A parcel not yet projected → 404.
5. **FR-TRK-006:** `tracking_events` are append-only in code (no update or delete path).
6. Tests (Testcontainers PostgreSQL + RabbitMQ, TestTokens):
   - **TST-027:** snapshot then status after a change; stream completes after a terminal status; unknown and malformed numbers → 404 before the stream; heartbeat; max lifetime; emitter cleanup; no event lost between registration and snapshot.
   - **TST-023 (b, d parts, tracking side):** publish `parcel.created` then `parcel.status.changed` to `swiftdrop.events` and assert the tracking rows and the SSE push; a duplicate event is processed once; stale sequence ignored; unknown parcel retried then dead-lettered; gaps and ordering; audit rows stored once.
   - **TST-034 (tracking part):** the public view and SSE payloads contain **exactly** the allowed keys (a whitelist test on JSON keys) and never notes, actor, courier identity or ids of owners; error bodies have no stack traces; captured logs contain no phone, email or note text.
   - Timeline authorisation: customer own, other customer 404, merchant by `mid`, courier assigned vs other 404, admin any; client token 403; unauthenticated 401. Coverage at least 70 %.

### Frontend (public pages and shared tracking pieces; master 9)
1. **`src/api/tracking.js`:** `getPublicTracking(trackingNumber)` (no auth), `getTimeline(parcelId)` (auth), plus the stream URL helper.
2. **`useTrackingStream(trackingNumber, {onStatus})`** (`pages/public/hooks/useTrackingStream.js`): opens `EventSource` on `/api/v1/public/tracking/{n}/stream`; handles `snapshot` and `status`; calls `onStatus` on every status event and every polling result; **closes the EventSource immediately after a terminal status** (browsers otherwise reconnect); reconnects at the 30-minute end only if the parcel is not terminal; **after 3 consecutive failures polls `GET /public/tracking/{n}` every 15 s** and retries SSE every 2 minutes; returns `{data, connectionMode ('live'|'polling'), error, notFound}`; cleans up on unmount.
3. **`ParcelTimeline`** (`pages/public/components/ParcelTimeline.jsx`, named + default export): props `history`, `variant` (`"public"` shows status chip, label, date and time only; `"detailed"` also shows actor role, note and failure reason label for owners, couriers and admins); each step has an icon, a text label and the time in Asia/Colombo 12-hour format (never colour alone); an "updating" state used by detail pages.
4. **`/` landing page** (`pages/public/landing`): `PublicLayout`, hero with a tracking-number box (navigates to `/track/<number>`), login and register links, a short honest description of what the service does (no features outside the SRS).
5. **`/track` and `/track/:trackingNumber`** (`pages/public/track`): entry form (trim and upper-case the number); the live view uses `useTrackingStream`, shows status chip, origin and destination district and zone names, the history and the failure reason label; an `aria-live="polite"` region announces updates; **malformed and unknown numbers both show "We could not find a parcel with that tracking number"**; works at **360 px without horizontal scroll**; no personal data.
6. Tests (`describe("TST-040 ...")`): tracking page with a mocked `EventSource` (snapshot, status, terminal close), fallback to polling after 3 failures, same not-found message for malformed and unknown, `aria-live` updates, timeline variants. Accessibility, keyboard use. Scenarios **AT-10, AT-11, AT-31** checked.

### Done when
`mvn -pl backend/tracking-service -am verify` green, frontend tests and build pass, report sent. Tell #4, #5, #6 and #8 in one line that `useTrackingStream`, `ParcelTimeline` and `tracking.js` are merged.

---

## PROMPT 3 of 3: notification-service features, cross-service tests and diagrams

You are **Member #9**. Backend package `com.swiftdrop.notification.notification.{controller,service,repository,client,dto,messaging,security,config}`. #10 owns the `webhook` slice and publishes `WebhookEnqueueService` (master 8.1); never write webhook code yourself.

**Preconditions:** Prompt 1 merged; common-lib events (#6) merged; #3's auth internal endpoints, #6's parcel internal endpoints (use **WireMock stubs of SRS 7.4** until merged); #10's `WebhookEnqueueService` (mock with Mockito until merged).

### Requirements (FR-NTF-001 to 008; Table 2.5; AT-23)
1. **Consumer on `notification.events`** (`EventConsumerSupport`: manual ack, 3 attempts, then DLQ). **No `processed_events`**: idempotency comes from the unique key `(event_id, channel, recipient_kind)` (FR-NTF-004).
2. **Who gets what (Table 2.5), implemented in one pure `NotificationPlanner`:**
   - `parcel.created`: sender (customer-owned parcels **only**) EMAIL + SMS.
   - `parcel.status.changed`, customer-owned parcels: sender EMAIL + SMS for `PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, CANCELLED`. Merchant-owned parcels have **no** sender notification.
   - Recipient (drop-off contact), **both** customer- and merchant-owned parcels: SMS always (the drop-off phone is mandatory) plus EMAIL if an email was given, for `PICKED_UP`, `OUT_FOR_DELIVERY`, `DELIVERED`, and `FAILED` **only when `previousStatus` was `OUT_FOR_DELIVERY`**. Never for `IN_TRANSIT` or `CANCELLED`. A pickup failure (`previousStatus = CREATED`) therefore never sends a "delivery failed" message to the recipient.
   - Merchant webhook: for `parcel.status.changed` call `WebhookEnqueueService.enqueueStatusChanged(envelope)` (a no-op without `merchantId` or config); a failure there is a processing error.
   - `merchant.status.changed`: EMAIL to the merchant account (`APPROVED` from `SUSPENDED` = reinstated, otherwise approved; `REJECTED`; `SUSPENDED`), including the reason for rejected and suspended.
3. **Contact data fetched at processing time and never stored in full (FR-NTF-007):** sender from auth-service `GET /internal/v1/users/{customerId}/contact`, recipient from parcel-service `GET /internal/v1/parcels/{parcelId}/recipient`, merchant email from auth-service `GET /internal/v1/merchants/{merchantId}` (`contactEmail`), through `InternalRestClientFactory` in `notification.client`. A failed fetch is a **processing error** (retry policy). Only tracking number, status and reason come from the event. A missing destination (for example no sender phone) is stored as `SKIPPED`.
4. **FR-NTF-004/005 handling:** for each planned notification upsert the row as `PENDING`; a `SENT` or `SKIPPED` row is never sent again; a `PENDING` or `FAILED` row is retried. On a send error record the failure in a **separate transaction** (`REQUIRES_NEW`) as `FAILED` with an `error_summary` (no personal data) and `attempts + 1`, then rethrow so the retry policy applies; after the last attempt the message is dead-lettered and the row stays `FAILED`.
5. **FR-NTF-002 channels** behind interfaces `EmailSender` (`SmtpEmailSender` with `JavaMailSender`, `SMTP_HOST`, `SMTP_PORT`, optional credentials, `SMTP_FROM`) and `SmsSender` (`MockSmsSender`: stores the message in `notifications.body_preview` and logs an INFO line with the **masked** number). `SMS_PROVIDER=mock`.
6. **FR-NTF-003 templates (English only):** parcel created (sender); each status for the sender; `PICKED_UP`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED` for the recipient; merchant approved, rejected, suspended, reinstated. Every parcel message contains the tracking number and the link `${PUBLIC_BASE_URL}/track/<trackingNumber>`; failed messages include the **reason label** (from the failure reason code); merchant rejected and suspended messages include the reason. **SMS at most 160 characters, no names or addresses.** Email bodies are plain text (a greeting with the full name is allowed in emails only); escape any user-provided text.
7. **FR-NTF-006:** logs and error summaries never contain emails, phones, names or addresses; destinations stored masked with `MaskingUtil`.
8. **(S) FR-NTF-008** `GET /internal/v1/notifications?limit=50` (`X-Internal-Key`) lists recent notifications including SMS bodies for demos. Do this last.
9. OpenAPI annotations, DTOs, no business logic in controllers. Mailpit receives the emails (`SMTP_HOST=mailpit`, port 1025).

### Tests
- **TST-015:** recipient and sender selection for every status and owner type per Table 2.5 (parameterised), **no recipient FAILED message for a pickup failure**, FAILED after `OUT_FOR_DELIVERY` notifies, SMS templates ≤ 160 chars and free of personal data, merchant templates with reasons, masking utility.
- **TST-023 (b and the webhook link):** with Testcontainers RabbitMQ and PostgreSQL publish a `parcel.status.changed` envelope into `swiftdrop.events`: the message arrives in `notification.events`, is consumed and recorded **exactly once even if delivered twice**; for a merchant-owned parcel `WebhookEnqueueService` is called exactly once (a real `webhook_deliveries` row if #10 is merged); WireMock stubs of auth and parcel contacts; failed fetch retried then dead-lettered with a `FAILED` row; error summaries without personal data; SMTP against a test SMTP server or the mailpit container.
- **TST-034 (notification part):** notification rows, logs and SMS previews contain no emails, phones, names, addresses or staff notes.
- Redelivery produces no duplicate email or SMS (**AT-23**). Coverage at least 70 %.

### Cross-service tests and docs
- Write `docs/test-reports/event-flow-notes.md` describing how TST-023 (a) is covered in common-lib (#6), (b) in notification-service (you), (c) in notification-service webhook tests (#10), (d) in tracking-service (you), and list what AT-23 and AT-24 verify at integration time.
- **Diagrams (Mermaid in `docs/design/`):** `event-topology.md` (exchange, queues, bindings, DLQs, producers and consumers per SRS 3.5), `tracking-er.md` and `notification-er.md`.

### Done when
`mvn -pl backend/notification-service -am verify` green, emails visible in Mailpit during a manual run, diagrams committed, report sent.
