-- =============================================================================
-- V6 – Index on refresh_tokens.expires_at for FR-AUTH-026 scheduled purge job
-- =============================================================================

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

