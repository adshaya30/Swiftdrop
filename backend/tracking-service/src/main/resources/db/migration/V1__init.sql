-- V1__init.sql – tracking-service (SRS 6.3, FR-TRK-006, FR-TRK-007)

-- processed_events: shared definition (SRS 6.3, Member #6 ProcessedEvents.java)
CREATE TABLE processed_events (
    event_id    UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- tracking_parcels (SRS 6.3)
CREATE TABLE tracking_parcels (
    parcel_id                UUID        PRIMARY KEY,
    tracking_number          VARCHAR(13) NOT NULL UNIQUE,
    status                   VARCHAR(20) NOT NULL,
    status_sequence          INT         NOT NULL,
    owner_type               VARCHAR(10) NOT NULL,
    customer_id              UUID,
    merchant_id              UUID,
    origin_district_name     VARCHAR(100) NOT NULL,
    origin_zone_name         VARCHAR(100) NOT NULL,
    destination_district_name VARCHAR(100) NOT NULL,
    destination_zone_name    VARCHAR(100) NOT NULL,
    courier_id               UUID,
    courier_first_name       VARCHAR(50),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- tracking_events (SRS 6.3, append-only FR-TRK-006)
CREATE TABLE tracking_events (
    id                  BIGSERIAL   PRIMARY KEY,
    parcel_id           UUID        NOT NULL REFERENCES tracking_parcels(parcel_id),
    sequence            INT         NOT NULL,
    from_status         VARCHAR(20),
    to_status           VARCHAR(20) NOT NULL,
    actor_user_id       UUID,
    actor_role          VARCHAR(10) NOT NULL,
    failure_reason_code VARCHAR(40),
    note                VARCHAR(500),
    occurred_at         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (parcel_id, sequence)
);

-- audit_logs (SRS 6.3)
CREATE TABLE audit_logs (
    id              BIGSERIAL   PRIMARY KEY,
    event_id        UUID        NOT NULL UNIQUE,
    service         VARCHAR(30) NOT NULL,
    actor_user_id   UUID,
    actor_role      VARCHAR(10) NOT NULL,
    action          VARCHAR(60) NOT NULL,
    entity_type     VARCHAR(30) NOT NULL,
    entity_id       VARCHAR(40) NOT NULL,
    details         JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- (S) FR-TRK-007: trigger rejecting UPDATE or DELETE on tracking_events (BR-06)
CREATE OR REPLACE FUNCTION reject_tracking_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'tracking_events is append-only: UPDATE and DELETE are forbidden (BR-06)';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_tracking_events_immutable
    BEFORE UPDATE OR DELETE ON tracking_events
    FOR EACH ROW EXECUTE FUNCTION reject_tracking_event_mutation();

