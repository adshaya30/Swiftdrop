# MEMBER #10: Webhooks (3 prompts)

The master prompt (v3.0) and the SRS are already loaded in your session.

| Prompt | Title | Blocks others? |
|---|---|---|
| 1 | tools/mock-merchant (no dependencies, start on day 1) | No |
| 2 | notification-service webhook slice (backend) | **Partly**: #9's consumer calls your `WebhookEnqueueService` (publish the interface first) |
| 3 | Webhook frontend, docs, security note | No |

---

## PROMPT 1 of 3: tools/mock-merchant (FR-WHK-011, S; DEP-016)

You are **Member #10**. Folder `tools/mock-merchant/` only. This is a demo webhook receiver; it needs nothing from the other members. Write it with **Node 22 and no dependencies** (built-in `http` and `crypto` only). Note for the leader: the SRS lists `node:22-alpine` as a build-stage image, so using it as the runtime image of this demo tool needs their OK (the alternative is `python:3.12-slim`); state in your report which you used.

### Requirements
1. **Server on port 9000** (host port 9090 is mapped by #8): `POST` to **any path** is accepted as a webhook, answers `200` with an empty JSON body, keeps the **raw body bytes** and all headers in memory (most recent 200 entries, in memory only, no files, no database).
2. **UI at `GET /`** (static HTML + JS served by the same process, no external hosts or CDNs): a list of received requests (time, path, `X-SwiftDrop-Event`, `X-SwiftDrop-Delivery`, `X-SwiftDrop-Attempt`, signature verdict) with a detail view showing **all headers and the body** pretty-printed. Render all received data with `textContent` (never `innerHTML`), because headers and bodies are untrusted. Auto-refresh every 2 s and a "Clear" button.
3. **Signature verification:** the UI has an input for the secret (`whsec_…`, kept only in the page, never sent anywhere); for each request it verifies `X-SwiftDrop-Signature: sha256=<hex>` = HMAC-SHA256 of `<X-SwiftDrop-Timestamp>.<raw body>` with the secret, using a **constant-time compare**, and checks that the timestamp is within **5 minutes** of now (SRS FR-WHK-003); show "valid", "invalid signature", "timestamp too old", or "no secret entered". Verification runs on the server side in the same process using a `POST /_verify` helper or in the browser with Web Crypto; pick one and explain why in the README (the secret should not be stored on the server).
4. **De-duplication hint:** show when the same `X-SwiftDrop-Delivery` id arrives again (retry or duplicate) with the attempt number, which makes AT-22 visible.
5. `Dockerfile` + `.dockerignore` (multi-stage not needed; non-root user, `TZ=UTC`, expose **9000**, `wget` HEALTHCHECK on `/healthz`), `README.md` (how to run with `node server.js`, how it is used in the demo: saved URL `http://mock-merchant:9000/<any path>` is allowed by the demo flags `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` and `WEBHOOK_ALLOW_HTTP=true`).
6. Tests with Node's built-in test runner (`node --test`, no new dependency): HMAC known vector (compute one with the documented algorithm), constant-time compare usage, timestamp window, XSS safety (the UI code contains no `innerHTML`), body kept as raw bytes, in-memory cap. Add `npm test` in `package.json` (no dependencies).

### Done when
`node --test` passes, container builds and starts, a `curl` POST shows in the UI, report sent. Tell #8 the image name and port (9000) in one line.

---

## PROMPT 2 of 3: notification-service webhook slice (backend)

You are **Member #10**. Package `com.swiftdrop.notification.webhook.{controller,service,repository,entity,dto,mapper,scheduler,security,config}` in notification-service. `pom.xml`, `application.yml`, V1 and the `domain` package are #9's: all `WEBHOOK_*` variables and the three webhook tables already exist; send #9 a snippet if you need a change. Use `java.net.http.HttpClient` for sending (not RestClient).

**Preconditions:** #9 Prompt 1 merged (tables `merchant_webhook_config`, `webhook_deliveries`, `Notification` entity); common-lib security merged (#3). Publish `WebhookEnqueueService` **first**, in its own small pull request, so #9's consumer can compile against it.

### PR A: publish `WebhookEnqueueService` (merge first)
`enqueueStatusChanged(EventEnvelope<ParcelStatusChangedData> envelope)` (master 8.1): a **no-op** unless the event has a `merchantId` and that merchant's config exists **and is enabled**; otherwise insert one `webhook_deliveries` row (`event_id` = the event id, **unique**, an existing row means already queued; `merchant_id`, `event_type`, `tracking_number`, `payload`, `target_url` = the configured URL, `status PENDING`, `attempt_count 0`, `next_attempt_at` now). Entities `MerchantWebhookConfig` and `WebhookDelivery` with repositories (yours). Payload (FR-WHK-002), exact JSON body: `{"id": eventId, "type": "parcel.status.changed", "createdAt": occurredAt, "data": {"parcelId", "trackingNumber", "merchantReference", "sequence", "previousStatus", "status", "statusChangedAt", "failureReasonCode"}}`. **No personal data and no staff note.** Test body for pings: type `webhook.test`, data `{"message":"SwiftDrop webhook test"}`.

### PR B: security building blocks (pure classes, unit tested first)
1. **AES-256-GCM** (`SecretCipher`): key = `WEBHOOK_SECRET_ENCRYPTION_KEY` (Base64 of 32 bytes; the service **fails fast at startup** if missing, not Base64 or not 32 bytes); output = 12-byte random nonce + ciphertext + tag stored in `secret_encrypted`; decrypt only in memory at send time; never log secrets (FR-WHK-008).
2. **`HmacSigner`:** `X-SwiftDrop-Signature` = `sha256=` + hex(HMAC-SHA256(`<timestamp>.<raw body>`, secret)); secret format for new secrets `whsec_` + 43 URL-safe characters from 32 random bytes.
3. **`SsrfValidator`** (FR-WHK-007, NFR-SEC-013), run **when the merchant saves the URL and again at every send**: scheme must be `https` (`http` only when `WEBHOOK_ALLOW_HTTP=true`); no credentials (userinfo) in the URL; resolve the host with `InetAddress.getAllByName` and **reject if any resolved address is blocked**. Always blocked regardless of flags: link-local `169.254.0.0/16` (cloud metadata `169.254.169.254`), `fe80::/10`, `0.0.0.0/8` and the unspecified address, multicast. Blocked unless `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` (local demo only): loopback `127.0.0.0/8` and `::1`, private `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, unique-local `fc00::/7`. Handle IPv4-mapped IPv6 (`::ffff:169.254.169.254`) and numeric host forms. The residual DNS-rebinding window is accepted and documented (D-14); do not build a custom resolver.
4. **`RetrySchedule`:** attempt n failed → next attempt after `WEBHOOK_RETRY_DELAYS` (ISO-8601 list, default `PT1M,PT5M,PT30M,PT2H,PT6H`) for n = 1..5; `WEBHOOK_MAX_ATTEMPTS` = 6.
5. Tests **TST-014** and **AES-GCM round trip (TST-013 part)**: HMAC for a known vector, retry delay calculation and attempt limit, SSRF validator (private, loopback, link-local with the private flag on and off, IPv6, IPv4-mapped, credentials in URL, scheme `http` with the flag on and off, numeric hosts), AES-GCM round trip, tampered ciphertext fails, wrong key length fails at startup, nonce uniqueness.

### PR C: configuration API, sender, delivery log (FR-WHK-001, 003 to 006, 008, 009, 010)
Endpoints are role MERCHANT, **dashboard login only** (`typ=user`; client tokens get 403 from common-lib); owner = `mid`.
- **`GET /api/v1/merchant/webhook`** → `{url, enabled, hasSecret}` (never the secret; unconfigured → `{url:null, enabled:false, hasSecret:false}`).
- **`PUT /api/v1/merchant/webhook` `{url, enabled}`:** HTTPS URL up to 500 chars (SSRF-validated, see above); the **first save generates the secret** and returns `{url, enabled, hasSecret, secret}` once; later saves return `{url, enabled, hasSecret}`.
- **`POST /api/v1/merchant/webhook/rotate-secret`** → `{secret}` once (new `whsec_` secret, encrypted at rest). **`POST /api/v1/merchant/webhook/test`** creates a `webhook.test` delivery (new random UUID as `event_id`), signed, sent and logged like any delivery; without a saved config → 400 `VALIDATION_ERROR`.
- **Sender (FR-WHK-003/004/005/006):** a scheduler every **2 s** claims due rows (`status = PENDING and next_attempt_at <= now`) with `FOR UPDATE SKIP LOCKED` and a short lease (move `next_attempt_at` forward in the claim transaction), then sends **outside** that transaction. Per attempt: read URL and secret from the **local config at send time** (missing or disabled config counts as a failed attempt), re-run the SSRF validation, build the body bytes from the stored payload, set headers `Content-Type: application/json`, `User-Agent: SwiftDrop-Webhook/1.0`, `X-SwiftDrop-Event`, `X-SwiftDrop-Delivery` (event id), `X-SwiftDrop-Attempt` (1-based), `X-SwiftDrop-Timestamp` (Unix seconds, **regenerated every attempt**), `X-SwiftDrop-Signature`; sign exactly the bytes sent. Success only on **HTTP 2xx within `WEBHOOK_TIMEOUT_SECONDS` (10)**; redirects are **not** followed (`Redirect.NEVER`); anything else (non-2xx, timeout, connection error, blocked target) is a failed attempt. Store only the status code and an error summary up to 200 characters (no response bodies, no personal data, no secrets). After failed attempt n schedule the next one per `RetrySchedule`; after the sixth failure the delivery is `FAILED`; success → `SUCCEEDED` with `completed_at`.
- **`GET /api/v1/merchant/webhook/deliveries?status=&page=&size=`:** the log for the caller's merchant only (event id, tracking number, status, attempt count, last HTTP status, last error summary, timestamps). **`POST /api/v1/merchant/webhook/deliveries/{id}/retry`:** re-queues a `FAILED` delivery (attempts reset to 0, status `PENDING`, `next_attempt_at` now), anything else → 409 `INVALID_STATUS_TRANSITION`, another merchant's id → 404. **`GET /api/v1/merchant/webhook/summary`** → `{failed24h, pending, succeeded24h}`.
- **(S) FR-WHK-010:** a daily job deletes `webhook_deliveries` older than 30 days and `notifications` rows older than 90 days (use #9's `NotificationRepository`).
- OpenAPI annotations, DTOs, MapStruct, `DomainException`, secrets never logged or returned except once.

### Tests (Testcontainers PostgreSQL, WireMock as the merchant endpoint with the private-target and http flags on, TestTokens)
- **TST-024:** WireMock returns 500, 500, then 200 → exactly 3 attempts and `SUCCEEDED` (delays shortened by configuration, Awaitility, no `Thread.sleep`); six failures end `FAILED` after exactly 6 attempts; a manual retry resets the schedule and a later success completes it; timeout and redirect responses count as failures; missing or disabled config counts as a failed attempt; timestamp regenerated each attempt and the signature verifies against the received body.
- Enqueue: no merchant or no config or disabled → no row; one row per event even when delivered twice; payload exactly as specified and free of personal data and notes.
- API: first save returns the secret once, later saves do not, rotate returns a new secret and the old no longer verifies, secret never appears in GET, logs or error messages; SSRF refusal on save (**AT-36**: `http://169.254.169.254/` rejected while the mock-merchant URL is accepted with the demo flags); retry only from `FAILED`; other merchants' deliveries 404; log filter and pagination; summary counts.
- **TST-032 (webhook part) and TST-033:** client token → 403 on every webhook endpoint, CUSTOMER / COURIER / ADMIN → 403, unauthenticated → 401, `pwc` rules apply.
- Scenarios AT-15, AT-16, AT-22, AT-36 checked. Coverage at least 70 %.

### Done when
`mvn -pl backend/notification-service -am verify` green, #9's consumer can call `WebhookEnqueueService`, a test ping reaches `tools/mock-merchant` with a valid signature. Report sent.

---

## PROMPT 3 of 3: webhook frontend, documentation and security note

You are **Member #10**. Folder `frontend/src/pages/merchant/webhook`, API file `src/api/webhook.js`, docs in `docs/design/`. Use only the shared client, components and layouts of #1 (master 8.1).

### Frontend (`/merchant/webhook`, MERCHANT dashboard)
1. **Settings:** URL field (HTTPS, up to 500 chars, Zod mirrors the limit), enabled toggle, Save; on the **first save** show the secret **once** in a dialog with a copy button and a warning that it will not be shown again (kept only in component state, cleared on close). The page shows "secret is set" (masked) when `hasSecret`, never the value. **Rotate secret** with `ConfirmDialog`, showing the new secret once. **Send test ping** button (disabled without a saved config) with the resulting delivery status in the log. Server field errors (for example SSRF refusal) shown without losing input.
2. **Delivery log:** table with filter `status` (`PENDING`, `SUCCEEDED`, `FAILED` shown as chips with text), event id, tracking number, attempt count, last HTTP status, last error summary, timestamps (Asia/Colombo 12-hour), pagination; **Retry** for `FAILED` rows with `ConfirmDialog`; refresh every 30 s; loading, empty and error states.
3. **Summary counters** from `GET /merchant/webhook/summary` (`failed24h`, `pending`, `succeeded24h`); if notification-service does not answer show "unavailable" without breaking the page.
4. **`src/api/webhook.js`:** `getWebhook`, `saveWebhook`, `rotateSecret`, `sendTest`, `listDeliveries`, `retryDelivery`, `getWebhookSummary` (the last one is reused by #6's merchant dashboard; keep the name and return shape `{failed24h, pending, succeeded24h}`).
5. **Tests** (`describe("TST-040 ...")`): the secret is shown once and cleared, never rendered from GET data; URL validation; rotate and retry confirmations; log filters call the API correctly; "unavailable" counter state. Accessibility, keyboard use, 360 px.

### Docs
- `docs/design/webhook-sequence.md`: the webhook part of Flow 4.3 as a Mermaid sequence diagram (status event, notification consumer, `WebhookEnqueueService`, `webhook_deliveries`, scheduler with `SKIP LOCKED`, SSRF check, signed POST, retries and manual retry).
- `docs/design/webhook-security.md`: security note covering HMAC signing (`timestamp.body`), how merchants verify (recompute with the raw body, constant-time compare, reject timestamps older than 5 minutes, de-duplicate by event id, answer 2xx quickly) with a short Node and a short Java sample, the retry schedule and at-least-once delivery, AES-GCM secret storage, SSRF protection and the accepted DNS-rebinding window (D-14, NFR-SEC-023), what is never in the payload (BR-28).
- A short section in `tools/mock-merchant/README.md` (if not present) on how the demo uses it with AT-15, AT-16 and AT-22.

### Done when
The page works against the running service and the mock merchant, `npm test -- --coverage` and `npm run build` pass, docs committed, report sent.
