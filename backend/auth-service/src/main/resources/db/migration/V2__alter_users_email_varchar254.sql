-- =============================================================================
-- V2 – Fix users.email column type to VARCHAR(254) per SRS v2.1 Section 6.3
-- =============================================================================

-- Drop the previous index on citext column
DROP INDEX IF EXISTS uq_users_email;

-- Normalize existing emails to lowercase safely
UPDATE users SET email = LOWER(email::text);

-- Alter email column type from citext to VARCHAR(254)
ALTER TABLE users ALTER COLUMN email TYPE VARCHAR(254) USING LOWER(email::text)::VARCHAR(254);

-- Re-create case-insensitive unique index on lower(email)
CREATE UNIQUE INDEX uq_users_email ON users (LOWER(email)) WHERE deleted_at IS NULL;

-- Enable implicit casts from varchar to enum types for standard JDBC compatibility
CREATE CAST (character varying AS user_role) WITH INOUT AS IMPLICIT;
CREATE CAST (character varying AS auth_provider) WITH INOUT AS IMPLICIT;
CREATE CAST (character varying AS user_status) WITH INOUT AS IMPLICIT;
