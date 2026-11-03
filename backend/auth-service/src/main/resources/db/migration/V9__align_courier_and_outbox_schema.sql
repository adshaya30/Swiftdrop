-- =============================================================================
-- V9 – Align Courier Profiles PK and Outbox Event Schema with SRS v2.1
-- SRS v2.1 Section 3.5, 6.3
-- =============================================================================

-- 1. Align courier_profiles primary key to user_id referencing users(id)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'courier_profiles' AND column_name = 'id'
    ) THEN
        ALTER TABLE courier_profiles DROP CONSTRAINT IF EXISTS courier_profiles_pkey;
        ALTER TABLE courier_profiles DROP COLUMN IF EXISTS id;
        ALTER TABLE courier_profiles ADD PRIMARY KEY (user_id);
    END IF;
END $$;

-- 2. Add SRS v2.1 Section 3.5 outbox fields
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS routing_key VARCHAR(100);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(64);
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS attempts INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_outbox_events_routing_key ON outbox_events (routing_key);
