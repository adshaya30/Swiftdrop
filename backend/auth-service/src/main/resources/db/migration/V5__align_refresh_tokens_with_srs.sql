-- =============================================================================
-- V5 – Align refresh_tokens schema with SRS v2.1 Section 6.3
-- - Primary key is UUID (gen_random_uuid())
-- - token_hash is VARCHAR(64) storing 64-char hex of SHA-256
-- - created_at is TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- - family_created_at is TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- =============================================================================

-- 1. Alter primary key to UUID
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_pkey;
ALTER TABLE refresh_tokens ALTER COLUMN id DROP DEFAULT;
ALTER TABLE refresh_tokens ALTER COLUMN id TYPE UUID USING gen_random_uuid();
ALTER TABLE refresh_tokens ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE refresh_tokens ADD PRIMARY KEY (id);

-- 2. Add and sync created_at column with existing issued_at
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();
UPDATE refresh_tokens SET created_at = issued_at WHERE created_at IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN created_at SET DEFAULT NOW();

-- 3. Ensure family_created_at is NOT NULL and defaults to NOW()
UPDATE refresh_tokens SET family_created_at = created_at WHERE family_created_at IS NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_created_at SET NOT NULL;
ALTER TABLE refresh_tokens ALTER COLUMN family_created_at SET DEFAULT NOW();

-- 4. Alter token_hash to VARCHAR(64) for SHA-256 hex string
ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);

-- 5. Indexes for fast lookup during token rotation and family revocation
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens (family_id);

