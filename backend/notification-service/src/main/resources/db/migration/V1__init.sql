-- V1__init.sql – notification-service (SRS 6.3)

-- notifications (SRS 6.3, FR-NTF-004)
CREATE TABLE notifications (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id            UUID        NOT NULL,
    event_type          VARCHAR(40) NOT NULL,
    tracking_number     VARCHAR(13),
    channel             VARCHAR(10) NOT NULL,
    recipient_kind      VARCHAR(10) NOT NULL,
    status              VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    template_code       VARCHAR(60) NOT NULL,
    destination_masked  VARCHAR(100),
    body_preview        VARCHAR(300),
    attempts            INT         NOT NULL DEFAULT 0,
    error_summary       VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    UNIQUE (event_id, channel, recipient_kind)
);

-- merchant_webhook_config (SRS 6.3, owned by Member #10)
CREATE TABLE merchant_webhook_config (
    merchant_id     UUID        PRIMARY KEY,
    url             VARCHAR(500) NOT NULL,
    secret_encrypted BYTEA      NOT NULL,
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- webhook_deliveries (SRS 6.3, owned by Member #10)
CREATE TABLE webhook_deliveries (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID        NOT NULL UNIQUE,
    merchant_id     UUID        NOT NULL,
    event_type      VARCHAR(40) NOT NULL,
    tracking_number VARCHAR(13),
    payload         JSONB       NOT NULL,
    target_url      VARCHAR(500) NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    attempt_count   INT         NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_http_status INT,
    last_error      VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_attempt_at TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX idx_webhook_deliveries_merchant ON webhook_deliveries(merchant_id);
CREATE INDEX idx_webhook_deliveries_status_next ON webhook_deliveries(status, next_attempt_at);

