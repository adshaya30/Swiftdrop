#!/usr/bin/env bash
# infrastructure/docker/postgres/init-databases.sh
# NFR-SEC-021, DEP-008: Create the six SwiftDrop databases and least-privilege users.
# Mounted at /docker-entrypoint-initdb.d/ and run once by the postgres entrypoint.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

  -- Revoke default public connect so users cannot see each other's databases
  REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;

  -- ── auth ─────────────────────────────────────────────────────────────────
  CREATE DATABASE swiftdrop_auth;
  CREATE USER auth_user WITH ENCRYPTED PASSWORD '${AUTH_DB_PASSWORD}';
  GRANT CONNECT  ON DATABASE swiftdrop_auth TO auth_user;
  GRANT CREATE   ON DATABASE swiftdrop_auth TO auth_user;
  ALTER DATABASE swiftdrop_auth OWNER TO auth_user;
  REVOKE CONNECT ON DATABASE swiftdrop_auth FROM PUBLIC;

  -- ── parcel ───────────────────────────────────────────────────────────────
  CREATE DATABASE swiftdrop_parcel;
  CREATE USER parcel_user WITH ENCRYPTED PASSWORD '${PARCEL_DB_PASSWORD}';
  GRANT CONNECT  ON DATABASE swiftdrop_parcel TO parcel_user;
  GRANT CREATE   ON DATABASE swiftdrop_parcel TO parcel_user;
  ALTER DATABASE swiftdrop_parcel OWNER TO parcel_user;
  REVOKE CONNECT ON DATABASE swiftdrop_parcel FROM PUBLIC;

  -- ── pricing ──────────────────────────────────────────────────────────────
  CREATE DATABASE swiftdrop_pricing;
  CREATE USER pricing_user WITH ENCRYPTED PASSWORD '${PRICING_DB_PASSWORD}';
  GRANT CONNECT  ON DATABASE swiftdrop_pricing TO pricing_user;
  GRANT CREATE   ON DATABASE swiftdrop_pricing TO pricing_user;
  ALTER DATABASE swiftdrop_pricing OWNER TO pricing_user;
  REVOKE CONNECT ON DATABASE swiftdrop_pricing FROM PUBLIC;

  -- ── delivery ─────────────────────────────────────────────────────────────
  CREATE DATABASE swiftdrop_delivery;
  CREATE USER delivery_user WITH ENCRYPTED PASSWORD '${DELIVERY_DB_PASSWORD}';
  GRANT CONNECT  ON DATABASE swiftdrop_delivery TO delivery_user;
  GRANT CREATE   ON DATABASE swiftdrop_delivery TO delivery_user;
  ALTER DATABASE swiftdrop_delivery OWNER TO delivery_user;
  REVOKE CONNECT ON DATABASE swiftdrop_delivery FROM PUBLIC;

  -- ── tracking ─────────────────────────────────────────────────────────────
  CREATE DATABASE swiftdrop_tracking;
  CREATE USER tracking_user WITH ENCRYPTED PASSWORD '${TRACKING_DB_PASSWORD}';
  GRANT CONNECT  ON DATABASE swiftdrop_tracking TO tracking_user;
  GRANT CREATE   ON DATABASE swiftdrop_tracking TO tracking_user;
  ALTER DATABASE swiftdrop_tracking OWNER TO tracking_user;
  REVOKE CONNECT ON DATABASE swiftdrop_tracking FROM PUBLIC;

  -- ── notification ─────────────────────────────────────────────────────────
  CREATE DATABASE swiftdrop_notification;
  CREATE USER notification_user WITH ENCRYPTED PASSWORD '${NOTIFICATION_DB_PASSWORD}';
  GRANT CONNECT  ON DATABASE swiftdrop_notification TO notification_user;
  GRANT CREATE   ON DATABASE swiftdrop_notification TO notification_user;
  ALTER DATABASE swiftdrop_notification OWNER TO notification_user;
  REVOKE CONNECT ON DATABASE swiftdrop_notification FROM PUBLIC;

EOSQL

