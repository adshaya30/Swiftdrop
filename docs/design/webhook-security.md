# SwiftDrop Webhook Security Specification

This document details the security model, cryptographic protocols, verification procedures, and threat mitigation mechanisms for SwiftDrop webhooks per **SRS v2.1 (FR-WHK-001 through FR-WHK-010, NFR-SEC-022, NFR-SEC-023, BR-28, D-14)** and **Member #10**.

---

## 1. HMAC-SHA256 Request Signing

Every HTTP request dispatched by SwiftDrop includes a cryptographic signature in the `X-SwiftDrop-Signature` header calculated using HMAC-SHA256 over the timestamp and raw request body.

### 1.1 Headers Dispatched on Each Delivery

| Header | Description | Example |
|---|---|---|
| `X-SwiftDrop-Event` | Event type identifier | `parcel.status.changed` or `webhook.test` |
| `X-SwiftDrop-Delivery` | Unique delivery / event UUID | `d1e2f3a4-56b7-89c0-def1-234567890abc` |
| `X-SwiftDrop-Attempt` | 1-based attempt counter | `1` |
| `X-SwiftDrop-Timestamp` | Unix epoch time in seconds when request was built | `1794567890` |
| `X-SwiftDrop-Signature` | Hex-encoded HMAC-SHA256 signature | `sha256=a1b2c3d4...` (64 hex characters) |
| `User-Agent` | Client identification | `SwiftDrop-Webhook/1.0` |
| `Content-Type` | Payload content type | `application/json` |

### 1.2 Signature Computation Algorithm

1. Retrieve the merchant's private signing secret (formatted as `whsec_` followed by 43 URL-safe base64 characters).
2. Obtain the current Unix timestamp in seconds: `<timestamp>`.
3. Read the exact byte sequence of the HTTP request body: `<raw_body>`.
4. Construct the signature payload string by concatenating: `<timestamp>` + `.` + `<raw_body>`.
5. Compute the HMAC using the SHA-256 hash function with the secret key.
6. Encode the resulting HMAC bytes into lowercase hexadecimal.
7. Prefix with `sha256=`.

$$\text{Signature} = \text{"sha256="} + \text{HMAC-SHA256}(K_{\text{secret}}, \text{timestamp} + \text{"."} + \text{rawBody})$$

> **Important:** The timestamp is regenerated on **every attempt**, ensuring a fresh signature for every retry.

---

## 2. Merchant Verification Guidelines

To guarantee authenticity and protect against replay attacks and timing side-channels, receivers must follow these five steps:

1. **Answer 2xx quickly:** Acknowledge receipt with HTTP 200/204 within **10 seconds**. Heavy business processing should be offloaded to an asynchronous background worker.
2. **Read raw request bytes:** Verify against the raw, unparsed request body. Do not parse JSON before signature verification (formatting differences or JSON serializer key re-ordering will invalidate the HMAC).
3. **Reject timestamps older than 5 minutes (Replay Protection):**
   Compare $|t_{\text{now}} - t_{\text{header}}| \le 300\text{ seconds}$. If the timestamp is in the future or older than 5 minutes, reject the request with HTTP 400/401.
4. **Perform a constant-time comparison:** Always use a constant-time equality check (e.g., `crypto.timingSafeEqual` in Node.js, `MessageDigest.isEqual` in Java) to mitigate timing attacks.
5. **De-duplicate by Event ID:** Log incoming `X-SwiftDrop-Delivery` identifiers. Because SwiftDrop guarantees at-least-once delivery, ignore duplicate delivery UUIDs if already processed.

---

## 3. Verification Code Samples

### 3.1 Node.js (Built-in `crypto` only, no external dependencies)

```javascript
const crypto = require('node:crypto');

function verifySwiftDropWebhook(rawBodyBuffer, headers, secret) {
  const timestamp = headers['x-swiftdrop-timestamp'];
  const signature = headers['x-swiftdrop-signature'];
  
  if (!timestamp || !signature || !secret) {
    return { valid: false, reason: 'Missing signature headers or secret' };
  }

  // 1. Check 5-minute replay window (300 seconds)
  const now = Math.floor(Date.now() / 1000);
  const timeDiff = Math.abs(now - parseInt(timestamp, 10));
  if (isNaN(timeDiff) || timeDiff > 300) {
    return { valid: false, reason: 'Timestamp outside 5-minute freshness window' };
  }

  // 2. Parse signature header
  const match = /^sha256=([a-fA-F0-9]{64})$/.exec(signature);
  if (!match) {
    return { valid: false, reason: 'Invalid signature format' };
  }

  // 3. Compute expected HMAC over `${timestamp}.${rawBody}`
  const expectedHmac = crypto
    .createHmac('sha256', secret)
    .update(`${timestamp}.`)
    .update(rawBodyBuffer)
    .digest('hex');

  // 4. Constant-time comparison
  const receivedBuf = Buffer.from(match[1].toLowerCase(), 'utf8');
  const expectedBuf = Buffer.from(expectedHmac, 'utf8');
  const isValid = receivedBuf.length === expectedBuf.length && crypto.timingSafeEqual(receivedBuf, expectedBuf);

  return isValid
    ? { valid: true }
    : { valid: false, reason: 'Signature mismatch' };
}
```

### 3.2 Java 21 (`java.security` & `javax.crypto`)

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

public class SwiftDropWebhookVerifier {

    private static final long MAX_ALLOWED_DRIFT_SECONDS = 300; // 5 minutes

    public static boolean verify(byte[] rawBodyBytes, String timestampHeader, String signatureHeader, String secret) {
        if (timestampHeader == null || signatureHeader == null || secret == null) {
            return false;
        }

        // 1. Replay attack window check
        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            return false;
        }

        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestampSeconds) > MAX_ALLOWED_DRIFT_SECONDS) {
            return false;
        }

        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String receivedHex = signatureHeader.substring("sha256=".length()).toLowerCase();

        // 2. Compute HMAC-SHA256
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            // Update timestamp.
            mac.update((timestampHeader + ".").getBytes(StandardCharsets.UTF_8));
            // Update raw body bytes
            byte[] computedHmacBytes = mac.doFinal(rawBodyBytes);
            String computedHex = HexFormat.of().formatHex(computedHmacBytes);

            // 3. Constant-time comparison
            return MessageDigest.isEqual(
                    receivedHex.getBytes(StandardCharsets.UTF_8),
                    computedHex.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## 4. Secret Storage & Encryption at Rest (NFR-SEC-022)

* Merchant webhook secrets are never stored in plaintext.
* Every secret is encrypted using **AES-256-GCM** with a cryptographically secure 12-byte random initialization vector (IV) per encryption.
* The output byte array stored in `merchant_webhook_config.secret_encrypted` contains the 12-byte IV prepended to the ciphertext and authentication tag.
* Decryption occurs strictly in-memory immediately prior to computing the request HMAC.
* The AES encryption key is sourced from `WEBHOOK_SECRET_ENCRYPTION_KEY` (a 32-byte Base64 environment variable). If missing or invalid at startup, the service fails fast.

---

## 5. SSRF Defense & Accepted Rebinding Window (FR-WHK-007, D-14, NFR-SEC-023)

To prevent Server-Side Request Forgery (SSRF) against internal cluster microservices or cloud metadata endpoints:
1. **Target URL Restrictions:**
   * URLs must use the `https://` scheme (HTTP is prohibited except when `WEBHOOK_ALLOW_HTTP=true` in local development).
   * User credentials (`user:pass@host`) and user-info components are strictly forbidden.
   * Decimal/numeric IPv4 shortcuts and octal notations are rejected.
2. **IP Range Restrictions:**
   * Destination hostnames are resolved via DNS prior to connection.
   * The resolved IP is verified against:
     * Loopback addresses (`127.0.0.0/8`, `::1`)
     * Link-local cloud metadata services (`169.254.169.254`, `fe80::/10`)
     * Private RFC-1918 networks (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
     * IPv4-mapped IPv6 ranges (`::ffff:0:0/96`)
     * Multicast and broadcast addresses.
3. **DNS Rebinding Window (D-14):**
   * The system validates the resolved address at configuration save time and immediately prior to dispatch.
   * Java's standard HTTP client connection happens immediately after validation. The residual window between DNS lookup and socket connection is documented as accepted risk per D-14 / NFR-SEC-023.

---

## 6. Payload Data Minimization (BR-28)

Webhook payloads strictly adhere to **BR-28**:
* **Never included:** Customer contact names, recipient contact numbers, physical drop-off addresses, email addresses, or courier internal notes.
* **Included fields only:**
  * `id`: Unique event UUID.
  * `type`: Event descriptor (`parcel.status.changed` or `webhook.test`).
  * `createdAt`: ISO 8601 UTC timestamp.
  * `data.trackingNumber`: Parcel tracking identifier (e.g. `SD-1234567890`).
  * `data.status`: Lifecycle status (`PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED`, etc.).
  * `data.reason`: Optional standard failure reason code if status is `FAILED`.

---

## 7. Retry Schedule & At-Least-Once Delivery

* Scheduled dispatch runs every **2 seconds** claiming rows with `SELECT ... FOR UPDATE SKIP LOCKED`.
* Successful delivery occurs when the destination answers **HTTP 2xx** within **10 seconds** (`WEBHOOK_TIMEOUT_SECONDS`).
* HTTP redirects (`3xx`) are **never followed** (`HttpClient.Redirect.NEVER`).
* Failed attempts trigger exponential backoff according to:
  $$\text{Attempts: } 1 \to \text{PT1M}, \quad 2 \to \text{PT5M}, \quad 3 \to \text{PT30M}, \quad 4 \to \text{PT2H}, \quad 5 \to \text{PT6H}$$
* If 6 consecutive attempts fail, the delivery permanently transitions to `FAILED`.
* Merchants may manually re-queue any `FAILED` delivery via `POST /api/v1/merchant/webhook/deliveries/{id}/retry`.

