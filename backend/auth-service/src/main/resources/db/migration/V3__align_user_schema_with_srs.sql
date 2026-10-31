-- =============================================================================
-- V3 – Align users schema with SRS v2.1 Section 6.3
-- - Primary key is UUID
-- - Default status is ACTIVE
-- - Remove unnecessary fields (email_verified, deleted_at)
-- - Update uniqueness constraint on email
-- =============================================================================

-- Enable pgcrypto for UUID generation if not already available
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Drop foreign keys referencing users(id)
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;
ALTER TABLE oauth_accounts DROP CONSTRAINT IF EXISTS oauth_accounts_user_id_fkey;

-- Drop users primary key
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_pkey;

-- Alter users.id to UUID
ALTER TABLE users ALTER COLUMN id DROP DEFAULT;
ALTER TABLE users ALTER COLUMN id TYPE UUID USING gen_random_uuid();
ALTER TABLE users ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE users ADD PRIMARY KEY (id);

-- Alter referencing tables user_id to UUID
ALTER TABLE refresh_tokens ALTER COLUMN user_id TYPE UUID USING gen_random_uuid();
ALTER TABLE oauth_accounts ALTER COLUMN user_id TYPE UUID USING gen_random_uuid();

-- Re-create foreign keys
ALTER TABLE refresh_tokens ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE oauth_accounts ADD CONSTRAINT oauth_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- Drop unnecessary indexes that reference deleted_at
DROP INDEX IF EXISTS uq_users_email;
DROP INDEX IF EXISTS idx_users_phone;

-- Drop unnecessary columns
ALTER TABLE users DROP COLUMN IF EXISTS email_verified;
ALTER TABLE users DROP COLUMN IF EXISTS deleted_at;

-- Create SRS-compliant case-insensitive unique index on lower(email)
CREATE UNIQUE INDEX uq_users_email ON users (LOWER(email));

-- Lookup by phone
CREATE INDEX idx_users_phone ON users (phone) WHERE phone IS NOT NULL;

-- Normalize existing user statuses to ACTIVE and set default to ACTIVE
UPDATE users SET status = 'ACTIVE'::user_status;
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE'::user_status;

