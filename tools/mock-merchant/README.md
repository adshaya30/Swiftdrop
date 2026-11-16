# SwiftDrop Mock Merchant

This demo webhook receiver runs on Node.js 22 with no third-party packages.

## Run locally

```sh
cd tools/mock-merchant
npm test
node server.js
```

Open http://localhost:9000/. The service listens on container port `9000`.

## Docker

Runtime image used: `node:22-alpine`. This satisfies the SRS build-stage image reference while keeping Node available in the demo runtime.

```sh
docker build -t swiftdrop/mock-merchant tools/mock-merchant
docker run --rm -p 9090:9000 swiftdrop/mock-merchant
```

The UI accepts webhook secrets only in browser memory. Verification is performed in the browser with Web Crypto so the secret is never transmitted to the server.

## Demo Scenarios (AT-15, AT-16, AT-22)

The mock merchant plays a central role during live demonstration scenarios:

1. **AT-15 (Webhook Configuration & Test Ping):**
   * The merchant configures `http://mock-merchant:9000/webhook` in the dashboard (allowed via `WEBHOOK_ALLOW_PRIVATE_TARGETS=true` and `WEBHOOK_ALLOW_HTTP=true`).
   * Upon saving, the merchant copies the generated `whsec_` secret into the mock merchant UI input.
   * Clicking **Send Test Ping** in the dashboard dispatches an immediate `webhook.test` request.
   * The mock merchant UI displays the incoming request with a green **Valid Signature** badge, and the delivery log registers the successful attempt.

2. **AT-16 (Live Parcel Status Event Delivery):**
   * When a courier updates the status of a merchant-owned parcel (e.g. to `PICKED_UP`, `IN_TRANSIT`, or `DELIVERED`), the notification consumer calls `WebhookEnqueueService`.
   * The sender dispatches a signed `parcel.status.changed` POST to the mock merchant.
   * The mock merchant displays the raw body payload (tracking number, status, reason) without any customer PII or staff internal notes (BR-28), and verifies the HMAC signature.

3. **AT-22 (Receiver Outage, Exponential Backoff, & Manual Retry):**
   * If mock-merchant is stopped or returning errors, dispatch attempts fail.
   * SwiftDrop increments attempt counters and reschedules retries according to `PT1M, PT5M, PT30M, PT2H, PT6H`.
   * When mock-merchant resumes, the next attempt succeeds (`HTTP 200`). The mock UI highlights repeat `X-SwiftDrop-Delivery` UUIDs with their attempt count.
   * If 6 consecutive attempts fail, the delivery transitions to `FAILED`. A merchant can then click **Retry** in the dashboard to re-queue the delivery immediately for dispatch.


## Signature algorithm

The documented algorithm is:

```text
expected = HMAC-SHA256(secret, timestamp + "." + raw_request_body_bytes)
header = sha256=<lowercase hexadecimal digest>
```

The timestamp must be no more than 300 seconds from the current Unix timestamp. Known vector: with secret `whsec_test`, timestamp `1700000000`, and body `hello`, the signature is documented by the test suite using Node crypto.
