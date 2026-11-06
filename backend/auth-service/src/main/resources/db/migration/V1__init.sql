-- =============================================================================
-- V1__init.sql — SwiftDrop auth-service baseline schema
-- SRS v2.1 Section 6.1, 6.2, 6.3
-- Tables: users, refresh_tokens, oauth_login_states, courier_profiles,
--         merchants, merchant_credentials, outbox_events
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. users
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 VARCHAR(254)    NOT NULL,
    password_hash         VARCHAR(100),
    full_name             VARCHAR(100)    NOT NULL,
    phone                 VARCHAR(16),
    role                  VARCHAR(10)     NOT NULL CHECK (role IN ('CUSTOMER', 'MERCHANT', 'COURIER', 'ADMIN')),
    status                VARCHAR(12)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    auth_provider         VARCHAR(10)     NOT NULL DEFAULT 'LOCAL' CHECK (auth_provider IN ('LOCAL', 'GOOGLE')),
    google_sub            VARCHAR(64),
    must_change_password  BOOLEAN         NOT NULL DEFAULT FALSE,
    failed_login_count    INT             NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version               BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_users_email ON users (LOWER(email));
CREATE UNIQUE INDEX uq_users_google_sub ON users (google_sub) WHERE google_sub IS NOT NULL;
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_status ON users (status);

-- -----------------------------------------------------------------------------
-- 2. refresh_tokens (FR-AUTH-007, FR-AUTH-008)
-- -----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id             UUID            NOT NULL,
    token_hash            CHAR(64)        NOT NULL UNIQUE,
    expires_at            TIMESTAMPTZ     NOT NULL,
    family_created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    used_at               TIMESTAMPTZ,
    revoked_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    user_agent            VARCHAR(512),
    ip_address            VARCHAR(45)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- -----------------------------------------------------------------------------
-- 3. oauth_login_states (PKCE transient flow, FR-AUTH-009, FR-AUTH-026)
-- -----------------------------------------------------------------------------
CREATE TABLE oauth_login_states (
    state_hash            CHAR(64)        PRIMARY KEY,
    nonce                 VARCHAR(64)     NOT NULL,
    code_verifier         VARCHAR(128)    NOT NULL,
    expires_at            TIMESTAMPTZ     NOT NULL,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_oauth_login_states_expires_at ON oauth_login_states (expires_at);

-- -----------------------------------------------------------------------------
-- 4. courier_profiles
-- -----------------------------------------------------------------------------
CREATE TABLE courier_profiles (
    user_id               UUID            PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    vehicle_type          VARCHAR(20)     NOT NULL CHECK (vehicle_type IN ('MOTORBIKE', 'THREE_WHEELER', 'VAN')),
    vehicle_number        VARCHAR(20)     NOT NULL,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- 5. merchants
-- -----------------------------------------------------------------------------
CREATE TABLE merchants (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID            NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    business_name         VARCHAR(120)    NOT NULL,
    business_registration_no VARCHAR(40),
    address_line1         VARCHAR(150),
    city                  VARCHAR(80),
    status                VARCHAR(12)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    status_reason         VARCHAR(300),
    discount_percent      NUMERIC(4,2)    NOT NULL DEFAULT 0.00 CHECK (discount_percent >= 0 AND discount_percent <= 50),
    decided_at            TIMESTAMPTZ,
    decided_by            UUID,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version               BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_merchants_user_id ON merchants (user_id);
CREATE INDEX idx_merchants_status ON merchants (status);

-- -----------------------------------------------------------------------------
-- 6. merchant_credentials
-- -----------------------------------------------------------------------------
CREATE TABLE merchant_credentials (
    id                    UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id           UUID            NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
    client_id             VARCHAR(40)     NOT NULL UNIQUE,
    client_secret_hash    CHAR(64)        NOT NULL,
    label                 VARCHAR(50)     NOT NULL,
    status                VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_used_at          TIMESTAMPTZ,
    revoked_at            TIMESTAMPTZ
);

CREATE INDEX idx_merchant_credentials_merchant_id ON merchant_credentials (merchant_id);

-- -----------------------------------------------------------------------------
-- 7. outbox_events (Shared Definition, SRS 3.5, 6.1, 6.3)
-- -----------------------------------------------------------------------------
CREATE TABLE outbox_events (
    id                    UUID            PRIMARY KEY,
    event_type            VARCHAR(40)     NOT NULL,
    routing_key           VARCHAR(40)     NOT NULL,
    payload               JSONB           NOT NULL,
    correlation_id        UUID,
    created_at            TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    published_at          TIMESTAMPTZ,
    attempts              INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
