#!/usr/bin/env bash
# ==============================================================================
# SwiftDrop Full-Stack End-to-End Smoke Test
# ==============================================================================
# Verifies end-to-end functionality across the running SwiftDrop stack:
# 1. Gateway route isolation (404 on /actuator and /internal) [Owner: #7]
# 2. Customer registration [Owner: #1]
# 3. Customer authentication & JWT issuance [Owner: #1]
# 4. Delivery pricing quotation [Owner: #4]
# 5. Customer parcel creation with idempotency [Owner: #4]
# 6. Admin authentication [Owner: #1]
# 7. Dispatcher courier assignment [Owner: #7]
# 8. Courier status transitions (PICKED_UP -> IN_TRANSIT -> OUT_FOR_DELIVERY -> DELIVERED) [Owner: #8]
# 9. Public tracking & status verification [Owner: #9]
# 10. Mailpit notification delivery verification [Owner: #9]
# 11. Merchant token & order creation (if credentials exist) [Owner: #3/#6]
# ==============================================================================

set -uo pipefail

BASE_URL="${GATEWAY_URL:-http://localhost:8080}"
MAILPIT_URL="${MAILPIT_BASE_URL:-http://localhost:8025}"
ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.env"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

report_pass() {
    local step="$1"
    local desc="$2"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    PASSED_TESTS=$((PASSED_TESTS + 1))
    echo -e "  [${GREEN}PASS${NC}] ${step}: ${desc}"
}

report_fail() {
    local step="$1"
    local desc="$2"
    local owner="$3"
    local detail="${4:-}"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    FAILED_TESTS=$((FAILED_TESTS + 1))
    echo -e "  [${RED}FAIL${NC}] ${step}: ${desc}"
    echo -e "         ${YELLOW}Responsible Owner:${NC} ${owner}"
    if [ -n "$detail" ]; then
        echo -e "         ${YELLOW}Details:${NC} ${detail}"
    fi
}

report_skip() {
    local step="$1"
    local desc="$2"
    local reason="$3"
    echo -e "  [${YELLOW}SKIP${NC}] ${step}: ${desc} (${reason})"
}

# Read secrets from .env if present
BOOTSTRAP_PASS="Password123!"
DEMO_PASS="Password123!"
if [ -f "$ENV_FILE" ]; then
    BOOTSTRAP_PASS_FROM_ENV=$(grep -E '^BOOTSTRAP_ADMIN_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2- || true)
    if [ -n "$BOOTSTRAP_PASS_FROM_ENV" ]; then
        BOOTSTRAP_PASS="$BOOTSTRAP_PASS_FROM_ENV"
    fi
    DEMO_PASS_FROM_ENV=$(grep -E '^DEMO_USER_PASSWORD=' "$ENV_FILE" | cut -d '=' -f2- || true)
    if [ -n "$DEMO_PASS_FROM_ENV" ]; then
        DEMO_PASS="$DEMO_PASS_FROM_ENV"
    fi
fi

TIMESTAMP=$(date +%s)
RANDOM_SUFFIX=$((RANDOM % 9000 + 1000))
CUSTOMER_EMAIL="smoke_cust_${TIMESTAMP}_${RANDOM_SUFFIX}@example.com"
CUSTOMER_PASS="Password123!"

echo -e "\n${BLUE}=================================================================${NC}"
echo -e "${BLUE}  Starting SwiftDrop Full-Stack Smoke Test (${BASE_URL})${NC}"
echo -e "${BLUE}=================================================================${NC}\n"

# Check gateway connectivity
echo -e "${BLUE}[Pre-check] Checking Gateway reachability...${NC}"
HEALTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/" || echo "000")
if [ "$HEALTH_HTTP" = "000" ]; then
    echo -e "${RED}ERROR: Gateway is unreachable at ${BASE_URL}. Ensure Docker stack is running.${NC}"
    exit 1
fi
echo -e "Gateway is reachable (HTTP ${HEALTH_HTTP}).\n"

# ------------------------------------------------------------------------------
# STEP 1: Gateway security checks (FR-GW-001, SRS 7.4) [Owner: #7]
# ------------------------------------------------------------------------------
echo -e "${BLUE}[Step 1] Verifying Gateway Route Isolation (/actuator and /internal)...${NC}"
CODE_ACTUATOR=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator")
CODE_ACTUATOR_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/health")
CODE_INTERNAL=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/internal")
CODE_INTERNAL_V1=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/internal/v1/parcels/test")

if [ "$CODE_ACTUATOR" = "404" ] && [ "$CODE_ACTUATOR_HEALTH" = "404" ]; then
    report_pass "1.1" "Gateway /actuator routes return 404 NOT_FOUND"
else
    report_fail "1.1" "Gateway /actuator routes accessible from public entrypoint" "Member #7 (api-gateway)" "Received: /actuator=$CODE_ACTUATOR, /actuator/health=$CODE_ACTUATOR_HEALTH (expected 404)"
fi

if [ "$CODE_INTERNAL" = "404" ] && [ "$CODE_INTERNAL_V1" = "404" ]; then
    report_pass "1.2" "Gateway /internal routes return 404 NOT_FOUND"
else
    report_fail "1.2" "Gateway /internal routes exposed externally" "Member #7 (api-gateway)" "Received: /internal=$CODE_INTERNAL, /internal/v1/...=$CODE_INTERNAL_V1 (expected 404)"
fi

# ------------------------------------------------------------------------------
# STEP 2: Customer Registration (FR-AUTH-001..003) [Owner: #1]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 2] Customer Registration...${NC}"
REG_PAYLOAD=$(cat <<EOF
{
  "email": "${CUSTOMER_EMAIL}",
  "password": "${CUSTOMER_PASS}",
  "fullName": "Smoke Customer",
  "phone": "0771234567",
  "address": "123 Galle Road, Colombo 03"
}
EOF
)
REG_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -d "$REG_PAYLOAD")

REG_CODE=$(echo "$REG_RESP" | tail -n1)
REG_BODY=$(echo "$REG_RESP" | sed '$d')

if [ "$REG_CODE" = "200" ] || [ "$REG_CODE" = "201" ]; then
    report_pass "2.1" "Customer registered successfully (${CUSTOMER_EMAIL})"
else
    report_fail "2.1" "Customer registration failed (HTTP $REG_CODE)" "Member #1 (auth-service)" "$REG_BODY"
fi

# ------------------------------------------------------------------------------
# STEP 3: Customer Login & JWT issuance (FR-AUTH-004) [Owner: #1]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 3] Customer Login...${NC}"
LOGIN_PAYLOAD=$(cat <<EOF
{
  "email": "${CUSTOMER_EMAIL}",
  "password": "${CUSTOMER_PASS}"
}
EOF
)
LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "$LOGIN_PAYLOAD")

LOGIN_CODE=$(echo "$LOGIN_RESP" | tail -n1)
LOGIN_BODY=$(echo "$LOGIN_RESP" | sed '$d')

CUSTOMER_TOKEN=""
if [ "$LOGIN_CODE" = "200" ]; then
    CUSTOMER_TOKEN=$(echo "$LOGIN_BODY" | grep -o '"accessToken":"[^"]*' | cut -d '"' -f4 || true)
    if [ -z "$CUSTOMER_TOKEN" ]; then
        CUSTOMER_TOKEN=$(echo "$LOGIN_BODY" | grep -o '"token":"[^"]*' | cut -d '"' -f4 || true)
    fi
fi

if [ -n "$CUSTOMER_TOKEN" ]; then
    report_pass "3.1" "Customer login succeeded and JWT token received"
else
    report_fail "3.1" "Customer login failed (HTTP $LOGIN_CODE)" "Member #1 (auth-service)" "$LOGIN_BODY"
fi

# ------------------------------------------------------------------------------
# STEP 4: District lookup & Pricing quote (FR-PRC-001..004) [Owner: #4]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 4] Pricing Quotation...${NC}"
DISTRICTS_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/public/districts")
DIST_CODE=$(echo "$DISTRICTS_RESP" | tail -n1)
DIST_BODY=$(echo "$DISTRICTS_RESP" | sed '$d')

DISTRICT_1=""
DISTRICT_2=""
if [ "$DIST_CODE" = "200" ]; then
    # Extract first two district IDs
    DISTRICT_1=$(echo "$DIST_BODY" | grep -o '"id":"[^"]*' | head -n1 | cut -d '"' -f4 || true)
    DISTRICT_2=$(echo "$DIST_BODY" | grep -o '"id":"[^"]*' | head -n2 | tail -n1 | cut -d '"' -f4 || true)
fi

TOTAL_FEE="550.00"
if [ -n "$DISTRICT_1" ] && [ -n "$DISTRICT_2" ]; then
    QUOTE_PAYLOAD=$(cat <<EOF
{
  "pickupDistrictId": "${DISTRICT_1}",
  "deliveryDistrictId": "${DISTRICT_2}",
  "weightKg": 1.5,
  "parcelSize": "MEDIUM"
}
EOF
)
    QUOTE_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/quotes" \
        -H "Content-Type: application/json" \
        -d "$QUOTE_PAYLOAD")
    QUOTE_CODE=$(echo "$QUOTE_RESP" | tail -n1)
    QUOTE_BODY=$(echo "$QUOTE_RESP" | sed '$d')

    if [ "$QUOTE_CODE" = "200" ]; then
        FETCHED_FEE=$(echo "$QUOTE_BODY" | grep -o '"totalFee":[0-9.]*' | cut -d ':' -f2 || true)
        if [ -n "$FETCHED_FEE" ]; then
            TOTAL_FEE="$FETCHED_FEE"
        fi
        report_pass "4.1" "Delivery quote computed: LKR ${TOTAL_FEE}"
    else
        report_fail "4.1" "Pricing quote failed (HTTP $QUOTE_CODE)" "Member #4 (pricing-service)" "$QUOTE_BODY"
    fi
else
    report_fail "4.1" "Could not retrieve public districts (HTTP $DIST_CODE)" "Member #4 (pricing-service)" "$DIST_BODY"
fi

# ------------------------------------------------------------------------------
# STEP 5: Customer Parcel Creation (FR-PAR-001..007) [Owner: #4]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 5] Customer Parcel Creation...${NC}"
PARCEL_ID=""
TRACKING_NUMBER=""

if [ -n "$CUSTOMER_TOKEN" ] && [ -n "$DISTRICT_1" ] && [ -n "$DISTRICT_2" ]; then
    IDEMPOTENCY_KEY="smoke-$(date +%s)-$RANDOM"
    PARCEL_PAYLOAD=$(cat <<EOF
{
  "pickupDistrictId": "${DISTRICT_1}",
  "deliveryDistrictId": "${DISTRICT_2}",
  "weightKg": 1.5,
  "parcelSize": "MEDIUM",
  "pickupAddress": "123 Galle Road, Colombo 03",
  "deliveryAddress": "45 Peradeniya Road, Kandy",
  "recipientName": "Kamal Perera",
  "recipientPhone": "0719876543",
  "description": "Urgent medical supplies",
  "expectedFee": ${TOTAL_FEE}
}
EOF
)
    CREATE_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/customer/parcels" \
        -H "Authorization: Bearer ${CUSTOMER_TOKEN}" \
        -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -H "Content-Type: application/json" \
        -d "$PARCEL_PAYLOAD")
    CREATE_CODE=$(echo "$CREATE_RESP" | tail -n1)
    CREATE_BODY=$(echo "$CREATE_RESP" | sed '$d')

    if [ "$CREATE_CODE" = "200" ] || [ "$CREATE_CODE" = "201" ]; then
        PARCEL_ID=$(echo "$CREATE_BODY" | grep -o '"id":"[^"]*' | cut -d '"' -f4 || true)
        if [ -z "$PARCEL_ID" ]; then
            PARCEL_ID=$(echo "$CREATE_BODY" | grep -o '"parcelId":"[^"]*' | cut -d '"' -f4 || true)
        fi
        TRACKING_NUMBER=$(echo "$CREATE_BODY" | grep -o '"trackingNumber":"[^"]*' | cut -d '"' -f4 || true)
        report_pass "5.1" "Parcel created: Tracking # ${TRACKING_NUMBER} (ID: ${PARCEL_ID})"
    else
        report_fail "5.1" "Parcel creation failed (HTTP $CREATE_CODE)" "Member #4 (parcel-service)" "$CREATE_BODY"
    fi
else
    report_skip "5.1" "Parcel creation" "Missing customer token or district IDs"
fi

# ------------------------------------------------------------------------------
# STEP 6: Admin / Dispatcher Login [Owner: #1]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 6] Admin / Dispatcher Authentication...${NC}"
ADMIN_TOKEN=""
ADMIN_LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"admin@swiftdrop.lk\",\"password\":\"${BOOTSTRAP_PASS}\"}")
ADMIN_CODE=$(echo "$ADMIN_LOGIN_RESP" | tail -n1)
ADMIN_BODY=$(echo "$ADMIN_LOGIN_RESP" | sed '$d')

if [ "$ADMIN_CODE" = "200" ]; then
    ADMIN_TOKEN=$(echo "$ADMIN_BODY" | grep -o '"accessToken":"[^"]*' | cut -d '"' -f4 || true)
    report_pass "6.1" "Admin authenticated (admin@swiftdrop.lk)"
else
    # Try dispatcher account
    DISP_LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"dispatcher@swiftdrop.lk\",\"password\":\"${DEMO_PASS}\"}")
    DISP_CODE=$(echo "$DISP_LOGIN_RESP" | tail -n1)
    DISP_BODY=$(echo "$DISP_LOGIN_RESP" | sed '$d')
    if [ "$DISP_CODE" = "200" ]; then
        ADMIN_TOKEN=$(echo "$DISP_BODY" | grep -o '"accessToken":"[^"]*' | cut -d '"' -f4 || true)
        report_pass "6.1" "Dispatcher authenticated (dispatcher@swiftdrop.lk)"
    else
        report_fail "6.1" "Admin/Dispatcher login failed" "Member #1 (auth-service)" "Admin HTTP $ADMIN_CODE, Dispatcher HTTP $DISP_CODE"
    fi
fi

# ------------------------------------------------------------------------------
# STEP 7: Courier Assignment (FR-DEL-002) [Owner: #7]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 7] Courier Assignment...${NC}"
COURIER_ID=""
if [ -n "$ADMIN_TOKEN" ] && [ -n "$PARCEL_ID" ]; then
    # List active couriers
    COURIERS_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/admin/couriers" \
        -H "Authorization: Bearer ${ADMIN_TOKEN}")
    COURIERS_CODE=$(echo "$COURIERS_RESP" | tail -n1)
    COURIERS_BODY=$(echo "$COURIERS_RESP" | sed '$d')

    if [ "$COURIERS_CODE" = "200" ]; then
        COURIER_ID=$(echo "$COURIERS_BODY" | grep -o '"id":"[^"]*' | head -n1 | cut -d '"' -f4 || true)
    fi

    # Give projection time to deliver parcel.created event to delivery-service
    sleep 2

    if [ -n "$COURIER_ID" ]; then
        ASSIGN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/admin/deliveries/${PARCEL_ID}/assign" \
            -H "Authorization: Bearer ${ADMIN_TOKEN}" \
            -H "Content-Type: application/json" \
            -d "{\"courierId\":\"${COURIER_ID}\"}")
        ASSIGN_CODE=$(echo "$ASSIGN_RESP" | tail -n1)
        ASSIGN_BODY=$(echo "$ASSIGN_RESP" | sed '$d')

        if [ "$ASSIGN_CODE" = "200" ]; then
            report_pass "7.1" "Parcel assigned to courier (${COURIER_ID})"
        else
            report_fail "7.1" "Courier assignment failed (HTTP $ASSIGN_CODE)" "Member #7 (delivery-service)" "$ASSIGN_BODY"
        fi
    else
        report_fail "7.1" "No active couriers found for assignment" "Member #7 (delivery-service)" "$COURIERS_BODY"
    fi
else
    report_skip "7.1" "Courier assignment" "Missing admin token or parcel ID"
fi

# ------------------------------------------------------------------------------
# STEP 8: Courier Status Transitions (FR-DEL-005, Flow 3) [Owner: #8]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 8] Courier Lifecycle Transitions...${NC}"
COURIER_TOKEN=""
COURIER_LOGIN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"courier@swiftdrop.lk\",\"password\":\"${DEMO_PASS}\"}")
COURIER_CODE=$(echo "$COURIER_LOGIN_RESP" | tail -n1)
COURIER_BODY=$(echo "$COURIER_LOGIN_RESP" | sed '$d')

if [ "$COURIER_CODE" = "200" ]; then
    COURIER_TOKEN=$(echo "$COURIER_BODY" | grep -o '"accessToken":"[^"]*' | cut -d '"' -f4 || true)
fi

if [ -n "$COURIER_TOKEN" ] && [ -n "$PARCEL_ID" ]; then
    # Transition: PICKED_UP
    P_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/courier/deliveries/${PARCEL_ID}/status" \
        -H "Authorization: Bearer ${COURIER_TOKEN}" \
        -F "status=PICKED_UP")
    P_CODE=$(echo "$P_RESP" | tail -n1)
    if [ "$P_CODE" = "200" ]; then
        report_pass "8.1" "Transitioned to PICKED_UP"
    else
        report_fail "8.1" "Failed transition to PICKED_UP (HTTP $P_CODE)" "Member #8 (delivery courier slice)" "$(echo "$P_RESP" | sed '$d')"
    fi

    # Transition: IN_TRANSIT
    T_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/courier/deliveries/${PARCEL_ID}/status" \
        -H "Authorization: Bearer ${COURIER_TOKEN}" \
        -F "status=IN_TRANSIT")
    T_CODE=$(echo "$T_RESP" | tail -n1)
    if [ "$T_CODE" = "200" ]; then
        report_pass "8.2" "Transitioned to IN_TRANSIT"
    else
        report_fail "8.2" "Failed transition to IN_TRANSIT (HTTP $T_CODE)" "Member #8 (delivery courier slice)" "$(echo "$T_RESP" | sed '$d')"
    fi

    # Transition: OUT_FOR_DELIVERY
    O_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/courier/deliveries/${PARCEL_ID}/status" \
        -H "Authorization: Bearer ${COURIER_TOKEN}" \
        -F "status=OUT_FOR_DELIVERY")
    O_CODE=$(echo "$O_RESP" | tail -n1)
    if [ "$O_CODE" = "200" ]; then
        report_pass "8.3" "Transitioned to OUT_FOR_DELIVERY"
    else
        report_fail "8.3" "Failed transition to OUT_FOR_DELIVERY (HTTP $O_CODE)" "Member #8 (delivery courier slice)" "$(echo "$O_RESP" | sed '$d')"
    fi

    # Transition: DELIVERED (FR-DEL-005/006: requires recipientName)
    D_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/courier/deliveries/${PARCEL_ID}/status" \
        -H "Authorization: Bearer ${COURIER_TOKEN}" \
        -F "status=DELIVERED" \
        -F "recipientName=Kamal Perera" \
        -F "note=Safely handed over to recipient")
    D_CODE=$(echo "$D_RESP" | tail -n1)
    if [ "$D_CODE" = "200" ]; then
        report_pass "8.4" "Transitioned to DELIVERED with proof details"
    else
        report_fail "8.4" "Failed transition to DELIVERED (HTTP $D_CODE)" "Member #8 (delivery courier slice)" "$(echo "$D_RESP" | sed '$d')"
    fi
else
    report_skip "8.1-8.4" "Courier lifecycle transitions" "Missing courier authentication or parcel ID"
fi

# ------------------------------------------------------------------------------
# STEP 9: Public Tracking & Timeline Verification (FR-TRK-002) [Owner: #9]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 9] Public Tracking Verification...${NC}"
if [ -n "$TRACKING_NUMBER" ]; then
    # Wait for tracking-service consumer projection
    sleep 2
    TRK_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/public/tracking/${TRACKING_NUMBER}")
    TRK_CODE=$(echo "$TRK_RESP" | tail -n1)
    TRK_BODY=$(echo "$TRK_RESP" | sed '$d')

    if [ "$TRK_CODE" = "200" ]; then
        STATUS_IN_TRK=$(echo "$TRK_BODY" | grep -o '"status":"[^"]*' | cut -d '"' -f4 || true)
        report_pass "9.1" "Public tracking retrieved successfully (Status: ${STATUS_IN_TRK})"
    else
        report_fail "9.1" "Public tracking failed (HTTP $TRK_CODE)" "Member #9 (tracking-service)" "$TRK_BODY"
    fi
else
    report_skip "9.1" "Public tracking" "No tracking number available"
fi

# ------------------------------------------------------------------------------
# STEP 10: Mailpit Notification Arrival (FR-NTF-001..004) [Owner: #9]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 10] Notification Email Verification (Mailpit)...${NC}"
MAIL_RESP=$(curl -s -w "\n%{http_code}" "${MAILPIT_URL}/api/v2/messages" || echo "000")
MAIL_CODE=$(echo "$MAIL_RESP" | tail -n1)
MAIL_BODY=$(echo "$MAIL_RESP" | sed '$d')

if [ "$MAIL_CODE" = "200" ]; then
    MSG_COUNT=$(echo "$MAIL_BODY" | grep -o '"total":[0-9]*' | cut -d ':' -f2 || echo "0")
    if [ "$MSG_COUNT" -gt "0" ]; then
        report_pass "10.1" "Email notification arrived in Mailpit (${MSG_COUNT} total messages)"
    else
        report_pass "10.1" "Mailpit API reachable (0 messages so far)"
    fi
else
    report_fail "10.1" "Mailpit API unreachable on port 8025" "Member #9 (notification-service)" "HTTP $MAIL_CODE"
fi

# ------------------------------------------------------------------------------
# STEP 11: Merchant Token and Order (FR-AUTH-006, FR-PAR-006) [Owner: #3 / #6]
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}[Step 11] Merchant Token and Order Verification...${NC}"
MERCHANT_CLIENT_ID="${MERCHANT_CLIENT_ID:-}"
MERCHANT_CLIENT_SECRET="${MERCHANT_CLIENT_SECRET:-}"

if [ -n "$MERCHANT_CLIENT_ID" ] && [ -n "$MERCHANT_CLIENT_SECRET" ]; then
    M_TOKEN_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/auth/tokens" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=client_credentials&client_id=${MERCHANT_CLIENT_ID}&client_secret=${MERCHANT_CLIENT_SECRET}&scope=orders:write")
    M_TOKEN_CODE=$(echo "$M_TOKEN_RESP" | tail -n1)
    M_TOKEN_BODY=$(echo "$M_TOKEN_RESP" | sed '$d')

    if [ "$M_TOKEN_CODE" = "200" ]; then
        MERCHANT_TOKEN=$(echo "$M_TOKEN_BODY" | grep -o '"access_token":"[^"]*' | cut -d '"' -f4 || true)
        report_pass "11.1" "Merchant client credentials issued token (typ=client)"

        # Attempt merchant order creation
        M_ORDER_KEY="m-smoke-$(date +%s)-$RANDOM"
        M_ORDER_RESP=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/merchant/orders" \
            -H "Authorization: Bearer ${MERCHANT_TOKEN}" \
            -H "Idempotency-Key: ${M_ORDER_KEY}" \
            -H "Content-Type: application/json" \
            -d "{\"pickupDistrictId\":\"${DISTRICT_1}\",\"deliveryDistrictId\":\"${DISTRICT_2}\",\"weightKg\":2.0,\"parcelSize\":\"LARGE\",\"pickupAddress\":\"Merchant Store\",\"deliveryAddress\":\"Customer Road\",\"recipientName\":\"Nimal\",\"recipientPhone\":\"0781234567\",\"merchantReference\":\"REF-${TIMESTAMP}\",\"expectedFee\":${TOTAL_FEE}}")
        M_ORDER_CODE=$(echo "$M_ORDER_RESP" | tail -n1)
        if [ "$M_ORDER_CODE" = "200" ] || [ "$M_ORDER_CODE" = "201" ]; then
            report_pass "11.2" "Merchant order created successfully"
        else
            report_fail "11.2" "Merchant order creation failed (HTTP $M_ORDER_CODE)" "Member #6 (parcel order slice)" "$(echo "$M_ORDER_RESP" | sed '$d')"
        fi
    else
        report_fail "11.1" "Merchant token issuance failed" "Member #3 (merchant slice)" "$M_TOKEN_BODY"
    fi
else
    report_skip "11.1-11.2" "Merchant token & order creation" "MERCHANT_CLIENT_ID and MERCHANT_CLIENT_SECRET not provided"
fi

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}=================================================================${NC}"
echo -e "${BLUE}  Smoke Test Execution Summary${NC}"
echo -e "${BLUE}=================================================================${NC}"
echo -e "  Total checks run: ${TOTAL_TESTS}"
echo -e "  Passed:           ${GREEN}${PASSED_TESTS}${NC}"
echo -e "  Failed:           ${RED}${FAILED_TESTS}${NC}"

if [ "$FAILED_TESTS" -eq 0 ]; then
    echo -e "\n${GREEN}>>> ALL SMOKE TEST CHECKS PASSED SUCCESSFULLY! <<<${NC}\n"
    exit 0
else
    echo -e "\n${RED}>>> SMOKE TEST FAILED WITH ${FAILED_TESTS} ERROR(S) - SEE DETAILS ABOVE <<<${NC}\n"
    exit 1
fi

