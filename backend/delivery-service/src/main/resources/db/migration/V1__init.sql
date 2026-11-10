CREATE TABLE deliveries (
    parcel_id UUID PRIMARY KEY,
    tracking_number VARCHAR(13) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED', 'CANCELLED')),
    status_sequence INT NOT NULL,
    owner_type VARCHAR(10) NOT NULL CHECK (owner_type IN ('CUSTOMER', 'MERCHANT')),
    customer_id UUID,
    merchant_id UUID,
    origin_district_code VARCHAR(20) NOT NULL,
    origin_district_name VARCHAR(40) NOT NULL,
    destination_district_code VARCHAR(20) NOT NULL,
    destination_district_name VARCHAR(40) NOT NULL,
    pickup_city VARCHAR(80) NOT NULL,
    dropoff_city VARCHAR(80) NOT NULL,
    courier_id UUID,
    assigned_at TIMESTAMPTZ,
    parcel_created_at TIMESTAMPTZ NOT NULL,
    terminal_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_deliveries_owner CHECK (
        (owner_type = 'CUSTOMER' AND customer_id IS NOT NULL AND merchant_id IS NULL)
        OR (owner_type = 'MERCHANT' AND merchant_id IS NOT NULL AND customer_id IS NULL)
    )
);

CREATE INDEX idx_deliveries_courier_status ON deliveries (courier_id, status);
CREATE INDEX idx_deliveries_unassigned_status_created ON deliveries (status, parcel_created_at) WHERE courier_id IS NULL;
CREATE INDEX idx_deliveries_terminal_at ON deliveries (terminal_at);

CREATE TABLE couriers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('MOTORBIKE', 'THREE_WHEELER', 'VAN')),
    vehicle_number VARCHAR(20) NOT NULL,
    status VARCHAR(12) NOT NULL CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE deliveries
    ADD CONSTRAINT fk_deliveries_courier FOREIGN KEY (courier_id) REFERENCES couriers (id);

CREATE TABLE delivery_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parcel_id UUID NOT NULL REFERENCES deliveries (parcel_id),
    courier_id UUID NOT NULL REFERENCES couriers (id),
    assigned_by UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    unassigned_at TIMESTAMPTZ
);

CREATE INDEX idx_delivery_assignments_parcel_id ON delivery_assignments (parcel_id);
CREATE INDEX idx_delivery_assignments_courier_id ON delivery_assignments (courier_id);

CREATE TABLE delivery_proofs (
    parcel_id UUID PRIMARY KEY REFERENCES deliveries (parcel_id),
    recipient_name VARCHAR(100) NOT NULL,
    note VARCHAR(500),
    has_photo BOOLEAN NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE proof_photos (
    parcel_id UUID PRIMARY KEY REFERENCES delivery_proofs (parcel_id),
    content_type VARCHAR(20) NOT NULL CHECK (content_type = 'image/jpeg'),
    data BYTEA NOT NULL
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

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