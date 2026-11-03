-- =============================================================================
-- V7 – Add Google OAuth 2.0 Support (SRS v2.1 Section 6.3, FR-AUTH-009, FR-AUTH-010)
-- Covers:
-- 1. users.google_sub column with unique partial index
-- 2. oauth_login_states table for PKCE code_verifier and nonce storage
-- =============================================================================

-- 1. Add google_sub column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub VARCHAR(64);

-- Unique index for non-null google_sub (prevents duplicate Google linking)
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;

-- 2. Create oauth_login_states table for OAuth 2.0 + PKCE state persistence
CREATE TABLE IF NOT EXISTS oauth_login_states (
    state_hash      VARCHAR(64)     PRIMARY KEY,
    nonce           VARCHAR(64)     NOT NULL,
    code_verifier   VARCHAR(128)    NOT NULL,
    expires_at      TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Index for hourly cleanup job (FR-AUTH-026)
CREATE INDEX IF NOT EXISTS idx_oauth_login_states_expires_at ON oauth_login_states (expires_at);

