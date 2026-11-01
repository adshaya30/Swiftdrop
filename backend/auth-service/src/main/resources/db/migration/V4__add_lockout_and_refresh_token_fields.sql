-- =============================================================================
-- V4 – Feature 2: Account Lockout, Merchant Status Gate, and Session Fields
-- SRS v2.1 Section 6.2, 6.3, FR-AUTH-003, FR-AUTH-004, FR-AUTH-005, FR-AUTH-007
-- =============================================================================

-- Add COURIER to user_role enum if not already present
ALTER TYPE user_role ADD VALUE IF NOT EXISTS 'COURIER';

-- Add DEACTIVATED to user_status enum if not already present (SRS v2.1 Section 6.2)
ALTER TYPE user_status ADD VALUE IF NOT EXISTS 'DEACTIVATED';

-- Add lockout and audit fields to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

-- Add session/family fields to refresh_tokens table (FR-AUTH-007)
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS family_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS family_created_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS used_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens (family_id);

-- Create merchants table for merchant approval gate (FR-AUTH-003, FR-AUTH-004, FR-MER-001/002)
CREATE TABLE IF NOT EXISTS merchants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(120) NOT NULL,
    business_registration_no VARCHAR(40),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    status_reason VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_merchants_user_id ON merchants (user_id);
