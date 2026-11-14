#!/usr/bin/env bash
# scripts/init-env.sh
# Copy .env.example to .env and replace every CHANGE_ME_* placeholder with
# a securely-generated random value. Will NOT overwrite an existing .env
# without explicit confirmation.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_EXAMPLE="$REPO_ROOT/.env.example"
ENV_FILE="$REPO_ROOT/.env"

if [ -f "$ENV_FILE" ]; then
  read -r -p ".env already exists. Overwrite? [y/N] " reply
  case "$reply" in
    [Yy]) rm "$ENV_FILE" ;;
    *) echo "Keeping existing .env. Exiting."; exit 0 ;;
  esac
fi

echo "Creating $ENV_FILE from $ENV_EXAMPLE ..."
cp "$ENV_EXAMPLE" "$ENV_FILE"

# Helper: generate random hex string of given byte count (output = 2*bytes hex chars)
rand_hex() {
  local bytes="${1:-32}"
  if command -v openssl &>/dev/null; then
    openssl rand -hex "$bytes"
  else
    cat /dev/urandom | tr -dc 'a-f0-9' | head -c $((bytes * 2))
  fi
}

# Helper: generate base64 of given bytes
rand_b64() {
  local bytes="${1:-32}"
  if command -v openssl &>/dev/null; then
    openssl rand -base64 "$bytes"
  else
    cat /dev/urandom | base64 | head -c 44
  fi
}

# Helper: generate a password satisfying PasswordPolicy (min 12 chars, upper, lower, digit, symbol; safe for .env/compose)
rand_pass() {
  local upper="ABCDEFGHJKLMNPQRSTUVWXYZ"
  local lower="abcdefghijkmnopqrstuvwxyz"
  local digits="23456789"
  local symbols="!@*-_=+"
  local u l d s x
  if command -v openssl &>/dev/null; then
    u=$(openssl rand 64 | LC_ALL=C tr -dc "$upper" | head -c 2)
    l=$(openssl rand 64 | LC_ALL=C tr -dc "$lower" | head -c 4)
    d=$(openssl rand 64 | LC_ALL=C tr -dc "$digits" | head -c 4)
    s=$(openssl rand 64 | LC_ALL=C tr -dc "$symbols" | head -c 2)
    x=$(openssl rand 64 | LC_ALL=C tr -dc "${upper}${lower}${digits}" | head -c 4)
  else
    u=$(LC_ALL=C tr -dc "$upper" < /dev/urandom | head -c 2)
    l=$(LC_ALL=C tr -dc "$lower" < /dev/urandom | head -c 4)
    d=$(LC_ALL=C tr -dc "$digits" < /dev/urandom | head -c 4)
    s=$(LC_ALL=C tr -dc "$symbols" < /dev/urandom | head -c 2)
    x=$(LC_ALL=C tr -dc "${upper}${lower}${digits}" < /dev/urandom | head -c 4)
  fi
  echo "${u}${l}${d}${s}${x}"
}

# Replace each CHANGE_ME_* placeholder with a random value
sed_inplace() {
  if sed --version 2>/dev/null | grep -q GNU; then
    sed -i "$@"
  else
    sed -i '' "$@"  # macOS BSD sed
  fi
}

# DB passwords and RabbitMQ password
for var in POSTGRES_PASSWORD AUTH_DB_PASSWORD PARCEL_DB_PASSWORD PRICING_DB_PASSWORD \
           DELIVERY_DB_PASSWORD TRACKING_DB_PASSWORD NOTIFICATION_DB_PASSWORD \
           RABBITMQ_DEFAULT_PASS; do
  val=$(rand_hex 20)
  sed_inplace "s|CHANGE_ME_${var}|${val}|g" "$ENV_FILE"
done

# INTERNAL_API_KEY: at least 32 characters
INTERNAL_API_KEY_VAL=$(rand_hex 32)
sed_inplace "s|CHANGE_ME_INTERNAL_API_KEY|${INTERNAL_API_KEY_VAL}|g" "$ENV_FILE"

# WEBHOOK_SECRET_ENCRYPTION_KEY: Base64 of 32 random bytes
WEBHOOK_KEY_VAL=$(rand_b64 32 | tr -d '\n')
sed_inplace "s|CHANGE_ME_WEBHOOK_SECRET_ENCRYPTION_KEY|${WEBHOOK_KEY_VAL}|g" "$ENV_FILE"

# Bootstrap admin password (must satisfy PasswordPolicy: uppercase, lowercase, digit, symbol)
ADMIN_PASS=$(rand_pass)
sed_inplace "s|CHANGE_ME_BOOTSTRAP_ADMIN_PASSWORD|${ADMIN_PASS}|g" "$ENV_FILE"

# Demo user password (must satisfy PasswordPolicy)
DEMO_PASS=$(rand_pass)
sed_inplace "s|CHANGE_ME_DEMO_USER_PASSWORD|${DEMO_PASS}|g" "$ENV_FILE"

echo ""
echo ".env created. Next steps:"
echo "  1. Run ./scripts/generate-keys.sh to generate JWT RSA keys"
echo "  2. Run: docker compose -f compose.infra.yaml up -d"
echo ""
echo "Bootstrap admin password: $ADMIN_PASS"
echo "(Change this in .env before sharing with anyone)"

