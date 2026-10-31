-- =============================================================================
-- V1 – SwiftDrop Auth Service Baseline Schema
-- SRS v2.1 Section 6.3
-- Covers: users, refresh_tokens (stub for Member 1 Feature 2),
--         oauth_accounts (stub for Member 2)
-- =============================================================================

-- Enable case-insensitive text extension (citext)
CREATE EXTENSION IF NOT EXISTS citext;

-- ---------------------------------------------------------------------------
-- ENUM types
-- ---------------------------------------------------------------------------
CREATE TYPE user_role AS ENUM ('CUSTOMER', 'MERCHANT', 'DRIVER', 'ADMIN');
CREATE TYPE auth_provider AS ENUM ('LOCAL', 'GOOGLE');
CREATE TYPE user_status AS ENUM ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED');

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,
    full_name       VARCHAR(100)    NOT NULL,
    email           CITEXT          NOT NULL,          -- CITEXT = case-insensitive uniqueness
    phone           VARCHAR(20),
    password_hash   VARCHAR(72),                       -- BCrypt $2a$ hash, NULL for OAuth-only accounts
    role            user_role       NOT NULL DEFAULT 'CUSTOMER',
    auth_provider   auth_provider   NOT NULL DEFAULT 'LOCAL',
    status          user_status     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ                        -- soft-delete
);

-- Unique email (CITEXT already enforces case-insensitive comparison)
CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE deleted_at IS NULL;

-- Lookup by phone
CREATE INDEX idx_users_phone ON users (phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;

-- Lookup by status (e.g., admin dashboards)
CREATE INDEX idx_users_status ON users (status);

-- ---------------------------------------------------------------------------
-- refresh_tokens  (stub – Member 1 Feature 2: Login)
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(256)    NOT NULL UNIQUE,
    issued_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    revoked_at      TIMESTAMPTZ,
    user_agent      VARCHAR(512),
    ip_address      VARCHAR(45)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- oauth_accounts  (stub – Member 2: Google OAuth)
-- ---------------------------------------------------------------------------
CREATE TABLE oauth_accounts (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider        auth_provider   NOT NULL,
    provider_id     VARCHAR(256)    NOT NULL,
    provider_email  CITEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_oauth_accounts_provider ON oauth_accounts (provider, provider_id);
CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts (user_id);
