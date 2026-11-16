#!/usr/bin/env bash
# SwiftDrop merchant API demo (DEP-015, Flow 4.3, AT-14). The web UI has no create-order screen (D-23), so this
# script shows the merchant side end to end against the running stack:
#   token -> create order -> idempotent replay -> duplicate reference (409) -> invalid district (400)
#   -> list orders -> cancel.
#
# Usage:
#   CLIENT_ID=sdc_... CLIENT_SECRET=sds_... ./tools/postman/merchant-demo.sh
# Optional: BASE_URL (default http://localhost:8080).
# Needs only bash, curl, grep and sed (all included in Git Bash on Windows).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
: "${CLIENT_ID:?Set CLIENT_ID to a merchant client_id (sdc_...) from the API credentials page}"
: "${CLIENT_SECRET:?Set CLIENT_SECRET to the matching client_secret (sds_...)}"

RUN="$(date +%s)"
KEY="demo-${RUN}"
REFERENCE="DEMO-${RUN}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILURES=0

# call METHOD PATH [curl args...]: prints the status, keeps body and headers in $WORK.
call() {
  local method="$1" path="$2"
  shift 2
  curl -sS -X "$method" "${BASE_URL}${path}" -o "$WORK/body" -D "$WORK/headers" -w '%{http_code}' "$@"
}

expect() {
  local step="$1" want="$2" got="$3"
  if [ "$got" = "$want" ]; then
    echo "PASS  $step (HTTP $got)"
  else
    echo "FAIL  $step: expected HTTP $want, got $got"
    sed 's/^/      /' "$WORK/body"; echo
    FAILURES=$((FAILURES + 1))
  fi
}

json_value() { # json_value FIELD: first string value of FIELD in the last body
  sed -n "s/.*\"$1\":\"\\([^\"]*\\)\".*/\\1/p" "$WORK/body" | head -n 1
}

order_body() { # order_body REFERENCE DROPOFF_DISTRICT
  cat <<JSON
{"merchantReference":"$1",
 "pickup":{"contactName":"Demo Store","contactPhone":"0771234567","addressLine1":"12 Galle Road",
           "city":"Colombo 03","districtCode":"COLOMBO","postalCode":"00300"},
 "dropoff":{"contactName":"Kamala Silva","contactPhone":"0712345678","contactEmail":"kamala@example.com",
            "addressLine1":"45 Peradeniya Road","city":"Kandy","districtCode":"$2"},
 "parcel":{"weightKg":1.5,"size":"SMALL","description":"Demo order"}}
JSON
}

echo "SwiftDrop merchant demo against ${BASE_URL}"
echo

# 1. Client-credentials token (FR-AUTH-019): form-encoded.
status=$(call POST /api/v1/oauth/token \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" \
  --data-urlencode "scope=orders:read orders:write")
expect "1. get access token" 200 "$status"
TOKEN="$(json_value access_token)"
if [ -z "$TOKEN" ]; then
  echo "No access token: check CLIENT_ID, CLIENT_SECRET and that the merchant is APPROVED." >&2
  exit 1
fi
AUTH=(-H "Authorization: Bearer ${TOKEN}")
JSON=(-H "Content-Type: application/json")

# 2. Create an order with an Idempotency-Key (FR-PAR-007).
status=$(call POST /api/v1/merchant/orders "${AUTH[@]}" "${JSON[@]}" -H "Idempotency-Key: ${KEY}" \
  --data "$(order_body "$REFERENCE" KANDY)")
expect "2. create order ${REFERENCE}" 201 "$status"
ORDER_ID="$(json_value id)"
echo "      tracking number: $(json_value trackingNumber)  total: $(sed -n 's/.*"total":\([0-9.]*\).*/\1/p' "$WORK/body")"
echo "      tracking URL:    $(json_value trackingUrl)"

# 3. Replay with the same key and body: the original 201, no second order (FR-PAR-006).
status=$(call POST /api/v1/merchant/orders "${AUTH[@]}" "${JSON[@]}" -H "Idempotency-Key: ${KEY}" \
  --data "$(order_body "$REFERENCE" KANDY)")
expect "3. replay with the same Idempotency-Key" 201 "$status"
if grep -qi '^Idempotent-Replay: true' "$WORK/headers" && [ "$(json_value id)" = "$ORDER_ID" ]; then
  echo "PASS  3. response carries Idempotent-Replay: true and the same order id"
else
  echo "FAIL  3. expected the Idempotent-Replay: true header and the same order id"
  FAILURES=$((FAILURES + 1))
fi

# 4. Same merchantReference with a new key: 409 DUPLICATE_MERCHANT_REFERENCE (BR-22).
status=$(call POST /api/v1/merchant/orders "${AUTH[@]}" "${JSON[@]}" -H "Idempotency-Key: ${KEY}-dup" \
  --data "$(order_body "$REFERENCE" KANDY)")
expect "4. duplicate merchantReference" 409 "$status"
echo "      code: $(json_value code)"

# 5. Unknown district: 400 VALIDATION_ERROR from pricing-service (FR-PRC-004).
status=$(call POST /api/v1/merchant/orders "${AUTH[@]}" "${JSON[@]}" \
  --data "$(order_body "${REFERENCE}-X" ATLANTIS)")
expect "5. invalid district" 400 "$status"
echo "      code: $(json_value code)"

# 6. List orders filtered by the reference (FR-PAR-008).
status=$(call GET "/api/v1/merchant/orders?merchantReference=${REFERENCE}" "${AUTH[@]}")
expect "6. list orders" 200 "$status"
echo "      totalItems: $(sed -n 's/.*"totalItems":\([0-9]*\).*/\1/p' "$WORK/body")"

# 7. Cancel the order while CREATED (BR-07).
status=$(call POST "/api/v1/merchant/orders/${ORDER_ID}/cancel" "${AUTH[@]}" "${JSON[@]}" \
  --data '{"reason":"Demo cancellation"}')
expect "7. cancel order" 200 "$status"
echo "      status: $(json_value status)"

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "All steps passed."
else
  echo "${FAILURES} step(s) failed."
  exit 1
fi
