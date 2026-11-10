-- SRS 6.3, FR-PRC-009: UUIDs are supplied by the application.
CREATE TABLE zones (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(60) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE districts (
    code VARCHAR(20) PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    zone_id UUID NOT NULL REFERENCES zones(id)
);
CREATE INDEX idx_districts_zone_id ON districts (zone_id);
CREATE TABLE zone_pricing (
    origin_zone_id UUID NOT NULL REFERENCES zones(id),
    destination_zone_id UUID NOT NULL REFERENCES zones(id),
    base_fee NUMERIC(12,2) NOT NULL CHECK (base_fee >= 0),
    per_kg_fee NUMERIC(12,2) NOT NULL CHECK (per_kg_fee >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (origin_zone_id, destination_zone_id)
);
CREATE INDEX idx_zone_pricing_destination ON zone_pricing (destination_zone_id);
CREATE TABLE size_surcharges (
    size VARCHAR(10) PRIMARY KEY CHECK (size IN ('SMALL','MEDIUM','LARGE')),
    surcharge NUMERIC(12,2) NOT NULL CHECK (surcharge >= 0),
    description VARCHAR(80) NOT NULL
);
-- SRS 6.1 shared publisher table, used by common-lib.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(40) NOT NULL,
    routing_key VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL,
    correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    attempts INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
