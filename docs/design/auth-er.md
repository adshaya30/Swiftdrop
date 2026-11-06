# Authentication & Identity Database Entity-Relationship Diagram

This document presents the entity-relationship model of the SwiftDrop authentication database schema per **SRS v2.1 Section 6.3 & 6.4** and **`MEMBER_02.md`**.

```mermaid
erDiagram
    users ||--o{ refresh_tokens : "has"
    users ||--o| courier_profiles : "has"
    users ||--o| merchants : "has"
    merchants ||--o| merchant_credentials : "owns"
    users ||--o{ outbox_events : "generates audit events for"
    courier_profiles ||--o{ outbox_events : "generates courier.changed events for"

    users {
        uuid id PK "UUID"
        varchar email "VARCHAR(254) UNIQUE NOT NULL (stored in lowercase)"
        varchar full_name "VARCHAR(100) NOT NULL"
        varchar phone "VARCHAR(16)"
        varchar password_hash "VARCHAR(100) (null for Google-only)"
        varchar google_sub "VARCHAR(64) UNIQUE (null for local accounts)"
        varchar role "VARCHAR(10) NOT NULL (CUSTOMER, MERCHANT, COURIER, ADMIN)"
        varchar auth_provider "VARCHAR(10) NOT NULL (LOCAL, GOOGLE)"
        varchar status "VARCHAR(12) NOT NULL (ACTIVE, DEACTIVATED)"
        boolean must_change_password "BOOLEAN NOT NULL DEFAULT false"
        int failed_login_count "INT NOT NULL DEFAULT 0"
        timestamp locked_until "TIMESTAMP WITH TIME ZONE"
        timestamp last_login_at "TIMESTAMP WITH TIME ZONE"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        timestamp updated_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        bigint version "BIGINT NOT NULL DEFAULT 0 (Optimistic Lock)"
    }

    refresh_tokens {
        uuid id PK "UUID"
        uuid user_id FK "UUID NOT NULL -> users(id)"
        uuid family_id "UUID NOT NULL"
        char token_hash "CHAR(64) UNIQUE NOT NULL (SHA-256)"
        timestamp expires_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        timestamp revoked_at "TIMESTAMP WITH TIME ZONE"
        timestamp used_at "TIMESTAMP WITH TIME ZONE"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
    }

    oauth_login_states {
        char state_hash PK "CHAR(64) NOT NULL (SHA-256)"
        varchar nonce "VARCHAR(64) NOT NULL"
        varchar code_verifier "VARCHAR(128) NOT NULL (PKCE verifier)"
        timestamp expires_at "TIMESTAMP WITH TIME ZONE NOT NULL (valid 5 min)"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
    }

    courier_profiles {
        uuid id PK "UUID"
        uuid user_id FK "UUID UNIQUE NOT NULL -> users(id)"
        varchar vehicle_type "VARCHAR(50) NOT NULL (MOTORBIKE, THREE_WHEELER, VAN)"
        varchar vehicle_number "VARCHAR(30) NOT NULL"
        varchar status "VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        timestamp updated_at "TIMESTAMP WITH TIME ZONE NOT NULL"
    }

    merchants {
        uuid id PK "UUID"
        uuid user_id FK "UUID UNIQUE NOT NULL -> users(id)"
        varchar business_name "VARCHAR(100) NOT NULL"
        varchar status "VARCHAR(20) NOT NULL (PENDING, APPROVED, REJECTED, SUSPENDED)"
        numeric discount_percent "NUMERIC(4,2) NOT NULL DEFAULT 0.00"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        timestamp updated_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        bigint version "BIGINT NOT NULL DEFAULT 0 (Optimistic Lock)"
    }

    merchant_credentials {
        uuid id PK "UUID"
        uuid merchant_id FK "UUID UNIQUE NOT NULL -> merchants(id)"
        varchar client_id "VARCHAR(64) UNIQUE NOT NULL"
        varchar client_secret_hash "VARCHAR(100) NOT NULL"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
        timestamp updated_at "TIMESTAMP WITH TIME ZONE NOT NULL"
    }

    outbox_events {
        uuid id PK "UUID"
        varchar aggregate_type "VARCHAR(50) NOT NULL ('USER', 'COURIER', 'MERCHANT')"
        uuid aggregate_id "UUID NOT NULL"
        varchar event_type "VARCHAR(100) NOT NULL ('courier.changed', 'audit.recorded')"
        text payload "TEXT NOT NULL (JSON)"
        timestamp published_at "TIMESTAMP WITH TIME ZONE (null until published)"
        timestamp created_at "TIMESTAMP WITH TIME ZONE NOT NULL"
    }
```

---

## Cardinalities & Relational Rules (SRS Section 6.4)

1. **User to Refresh Tokens (`1 : N`)**:
   - A single user may possess multiple refresh tokens across multiple login sessions / devices.
   - When reuse is detected or password is changed/reset, all tokens in the family (or all tokens for the user) are revoked simultaneously.
2. **User to Courier Profile (`1 : 0..1`)**:
   - Users with role `COURIER` possess exactly one operational profile containing vehicle details and dispatch status.
   - Non-courier accounts have no associated courier profile record.
3. **User to Merchant (`1 : 0..1`)**:
   - Users with role `MERCHANT` possess exactly one merchant record capturing business name, approval status, and negotiated discount rate.
4. **Merchant to Merchant Credentials (`1 : 0..1`)**:
   - Approved merchants may be issued a single set of API client credentials (`client_id`, `client_secret_hash`) for B2B system integration.
5. **Transient OAuth States**:
   - `oauth_login_states` records have no foreign keys to `users` because they are generated before authentication completes.
   - States are purged immediately upon use or automatically 5 minutes after creation.
