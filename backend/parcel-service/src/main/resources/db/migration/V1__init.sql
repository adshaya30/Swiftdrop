-- SRS 6.3, BR-02/09/22/27: service-local data and creation snapshots.
CREATE TABLE parcels (
    id UUID PRIMARY KEY,
    tracking_number VARCHAR(13) NOT NULL UNIQUE,
    customer_id UUID,
    merchant_id UUID,
    merchant_reference VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED'
        CHECK (status IN ('CREATED','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','CANCELLED')),
    status_sequence INT NOT NULL DEFAULT 1,
    pickup_contact_name VARCHAR(100) NOT NULL,
    pickup_contact_phone VARCHAR(16) NOT NULL,
    pickup_address_line1 VARCHAR(150) NOT NULL,
    pickup_address_line2 VARCHAR(150),
    pickup_city VARCHAR(80) NOT NULL,
    pickup_district_code VARCHAR(20) NOT NULL,
    pickup_district_name VARCHAR(40) NOT NULL,
    pickup_postal_code VARCHAR(5),
    dropoff_contact_name VARCHAR(100) NOT NULL,
    dropoff_contact_phone VARCHAR(16) NOT NULL,
    dropoff_address_line1 VARCHAR(150) NOT NULL,
    dropoff_address_line2 VARCHAR(150),
    dropoff_city VARCHAR(80) NOT NULL,
    dropoff_district_code VARCHAR(20) NOT NULL,
    dropoff_district_name VARCHAR(40) NOT NULL,
    dropoff_postal_code VARCHAR(5),
    dropoff_contact_email VARCHAR(254),
    weight_kg NUMERIC(5,2) NOT NULL,
    size VARCHAR(10) NOT NULL CHECK (size IN ('SMALL','MEDIUM','LARGE')),
    description VARCHAR(200),
    delivery_instructions VARCHAR(300),
    origin_zone_code VARCHAR(20) NOT NULL,
    origin_zone_name VARCHAR(60) NOT NULL,
    destination_zone_code VARCHAR(20) NOT NULL,
    destination_zone_name VARCHAR(60) NOT NULL,
    fee_base NUMERIC(12,2) NOT NULL,
    fee_weight NUMERIC(12,2) NOT NULL,
    fee_size NUMERIC(12,2) NOT NULL,
    fee_discount NUMERIC(12,2) NOT NULL,
    fee_total NUMERIC(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'LKR',
    terminal_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_parcels_exactly_one_owner CHECK ((customer_id IS NOT NULL) <> (merchant_id IS NOT NULL))
);
CREATE UNIQUE INDEX uq_parcels_merchant_reference ON parcels (merchant_id, merchant_reference)
    WHERE merchant_reference IS NOT NULL AND status NOT IN ('CANCELLED','FAILED');
CREATE INDEX idx_parcels_customer_created ON parcels (customer_id, created_at DESC);
CREATE INDEX idx_parcels_merchant_created ON parcels (merchant_id, created_at DESC);
CREATE INDEX idx_parcels_status_created ON parcels (status, created_at);
CREATE INDEX idx_parcels_terminal_at ON parcels (terminal_at);

-- FR-PAR-006 storage only; behavior belongs to Prompt 2.
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(10) NOT NULL CHECK (owner_type IN ('CUSTOMER','MERCHANT')),
    owner_id UUID NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status INT NOT NULL,
    response_body JSONB NOT NULL,
    parcel_id UUID NOT NULL REFERENCES parcels(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency_keys_owner_key UNIQUE (owner_type, owner_id, idempotency_key)
);
CREATE INDEX idx_idempotency_keys_parcel_id ON idempotency_keys (parcel_id);
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
