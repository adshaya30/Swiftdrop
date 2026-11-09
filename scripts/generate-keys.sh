#!/usr/bin/env bash
# scripts/generate-keys.sh
# DEP-013: Generate RSA 2048 JWT key pair using Docker alpine/openssl.
# Produces:
#   infrastructure/keys/swiftdrop_private.pem  (PKCS#8 PEM, never committed)
#   infrastructure/keys/swiftdrop_public.pem   (PEM, never committed)
set -euo pipefail

KEYS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/infrastructure/keys"
PRIVATE_KEY="$KEYS_DIR/swiftdrop_private.pem"
PUBLIC_KEY="$KEYS_DIR/swiftdrop_public.pem"

if [ -f "$PRIVATE_KEY" ]; then
  echo "Keys already exist at $KEYS_DIR – delete them first if you want to regenerate."
  exit 0
fi

echo "Generating RSA 2048 key pair in $KEYS_DIR ..."

# Generate PKCS#8 private key
docker run --rm \
  -v "$KEYS_DIR:/keys" \
  alpine/openssl \
  genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /keys/swiftdrop_private.pem

# Derive the public key
docker run --rm \
  -v "$KEYS_DIR:/keys" \
  alpine/openssl \
  rsa -pubout \
  -in /keys/swiftdrop_private.pem \
  -out /keys/swiftdrop_public.pem

chmod 600 "$PRIVATE_KEY"
chmod 644 "$PUBLIC_KEY"

echo "Done. Keys written to $KEYS_DIR"
echo "  Private: $PRIVATE_KEY"
echo "  Public:  $PUBLIC_KEY"

