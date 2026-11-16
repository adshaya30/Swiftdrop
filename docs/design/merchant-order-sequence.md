# Merchant order and webhook sequence (Flow 4.3)

Owner: Member #6. Source: SRS v2.1 Flow 4.3, FR-AUTH-019, FR-PAR-006/007, FR-WHK-002 to 006, SRS 3.5.

A merchant's own system gets a client-credentials token, creates an order, and later receives a signed webhook when
the parcel's status changes. The webhook part is shown in more detail in `webhook-sequence.md` (Member #10).

```mermaid
sequenceDiagram
    autonumber
    participant M as Merchant system
    participant GW as api-gateway :8080
    participant A as auth-service
    participant P as parcel-service
    participant PR as pricing-service
    participant PDB as swiftdrop_parcel
    participant MQ as RabbitMQ (swiftdrop.events)
    participant N as notification-service
    participant NDB as swiftdrop_notification

    Note over M,A: 1. Access token (FR-AUTH-019)
    M->>GW: POST /api/v1/oauth/token (client_credentials, client_id, client_secret)
    GW->>A: forward (rate limited)
    A->>A: verify secret hash (constant time)<br/>merchant APPROVED, user ACTIVE
    alt valid
        A-->>M: 200 {access_token: JWT typ=client, role=MERCHANT, mid, scope, expires_in 900}
    else not usable
        A-->>M: 401 invalid_client
    end

    Note over M,PDB: 2. Create order (FR-PAR-007, Flow 4.3 order of checks)
    M->>GW: POST /api/v1/merchant/orders + Bearer token + Idempotency-Key?
    GW->>P: forward
    P->>P: verify JWT, role MERCHANT, scope orders:write, validate body
    P->>PDB: idempotency lookup (MERCHANT, mid, key)
    alt same key, same body, within 24 h
        P-->>M: stored 201 + Idempotent-Replay: true
    else same key, other body
        P-->>M: 422 IDEMPOTENCY_KEY_REUSED
    else new request
        P->>A: GET /internal/v1/merchants/{mid} (X-Internal-Key)
        A-->>P: {status, userStatus, discountPercent}
        opt not APPROVED or user not ACTIVE (BR-23)
            P-->>M: 403 MERCHANT_NOT_ACTIVE
        end
        P->>PR: POST /internal/v1/pricing/calculate (with discountPercent)
        PR-->>P: fee breakdown (400 for an unknown district)
        opt expectedFee present and different
            P-->>M: 409 FEE_CHANGED
        end
        P->>PDB: ONE transaction: parcel CREATED + idempotency row + outbox_events (parcel.created)
        alt merchantReference already in use (BR-22)
            P-->>M: 409 DUPLICATE_MERCHANT_REFERENCE
        else stored
            P-->>M: 201 {id, trackingNumber, status, merchantReference, fee, trackingUrl, createdAt}
        end
    end

    Note over P,NDB: 3. Later: status change becomes a webhook (Flow 3, SRS 3.5)
    P->>PDB: ONE transaction: status + status_sequence + outbox_events (parcel.status.changed)
    loop OutboxPublisher every 1 s
        P->>PDB: up to 100 unpublished rows, FOR UPDATE SKIP LOCKED
        P->>MQ: publish (persistent, mandatory, confirms)
        MQ-->>P: confirm
        P->>PDB: set published_at
    end
    MQ->>N: notification.events (manual ack, prefetch 10)
    N->>NDB: merchant webhook config enabled? insert webhook_deliveries (unique event_id, PENDING)
    N-->>MQ: ack (3 attempts, then notification.events.dlq)

    Note over N,M: 4. Signed delivery with retries (FR-WHK-003 to 006)
    loop scheduler every 2 s, at most 6 attempts
        N->>NDB: claim due rows (FOR UPDATE SKIP LOCKED)
        N->>N: decrypt secret (AES-256-GCM), SSRF check of the URL<br/>signature = HMAC-SHA256(timestamp + "." + body)
        N->>M: POST webhook URL (X-SwiftDrop-Event, -Delivery, -Attempt, -Timestamp, -Signature), 10 s, no redirects
        M->>M: verify signature in constant time, reject timestamps older than 5 min,<br/>de-duplicate by event id
        alt 2xx
            M-->>N: 200
            N->>NDB: SUCCEEDED
        else non-2xx, timeout or blocked
            N->>NDB: attempt_count + 1, next try after 1 min, 5 min, 30 min, 2 h, 6 h<br/>FAILED after the 6th attempt
        end
    end
    opt FAILED
        M->>GW: POST /api/v1/merchant/webhook/deliveries/{id}/retry (dashboard login)
        GW->>N: forward
        N->>NDB: attempts 0, PENDING
    end
```

## Notes

- Client tokens (`typ=client`) are accepted only on `/api/v1/merchant/orders*` (SRS 3.6); the dashboard, receipts,
  timelines and webhook settings need a dashboard login.
- The merchant is re-checked in auth-service on every create and cancel, because an access token stays valid for up
  to 15 minutes after a suspension (BR-23, D-11).
- Events and webhook payloads carry no customer, merchant or recipient contact data (BR-28).
- `tools/postman/merchant-demo.sh` runs steps 1 and 2 against the running stack (DEP-015).
