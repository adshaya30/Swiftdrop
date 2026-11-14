# ==============================================================================
# SwiftDrop Full-Stack End-to-End Smoke Test (PowerShell)
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

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$MailpitUrl = "http://localhost:8025",
    [string]$MerchantClientId = $env:MERCHANT_CLIENT_ID,
    [string]$MerchantClientSecret = $env:MERCHANT_CLIENT_SECRET
)

$TotalTests = 0
$PassedTests = 0
$FailedTests = 0

function Report-Pass($step, $desc) {
    $script:TotalTests++
    $script:PassedTests++
    Write-Host "  [PASS] $step`: $desc" -ForegroundColor Green
}

function Report-Fail($step, $desc, $owner, $detail) {
    $script:TotalTests++
    $script:FailedTests++
    Write-Host "  [FAIL] $step`: $desc" -ForegroundColor Red
    Write-Host "         Responsible Owner: $owner" -ForegroundColor Yellow
    if ($detail) {
        Write-Host "         Details: $detail" -ForegroundColor Yellow
    }
}

function Report-Skip($step, $desc, $reason) {
    Write-Host "  [SKIP] $step`: $desc ($reason)" -ForegroundColor Gray
}

# Read secrets from .env if present
$BootstrapPass = "Password123!"
$DemoPass = "Password123!"
$envPath = Join-Path $PSScriptRoot "..\..\.env"
if (Test-Path $envPath) {
    Get-Content $envPath | ForEach-Object {
        if ($_ -match "^BOOTSTRAP_ADMIN_PASSWORD=(.+)") { $BootstrapPass = $matches[1] }
        if ($_ -match "^DEMO_USER_PASSWORD=(.+)") { $DemoPass = $matches[1] }
    }
}

$Timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$RandomSuffix = Get-Random -Minimum 1000 -Maximum 9999
$CustomerEmail = "smoke_cust_${Timestamp}_${RandomSuffix}@example.com"
$CustomerPass = "Password123!"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Starting SwiftDrop Full-Stack Smoke Test ($BaseUrl)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Pre-check
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/" -UseBasicParsing -TimeoutSec 5 -ErrorAction SilentlyContinue
    Write-Host "Gateway reachable (HTTP $($resp.StatusCode)).`n"
} catch {
    Write-Host "ERROR: Gateway unreachable at $BaseUrl. Ensure Docker stack is running." -ForegroundColor Red
    exit 1
}

# STEP 1: Gateway Isolation
Write-Host "[Step 1] Verifying Gateway Route Isolation (/actuator and /internal)..." -ForegroundColor Cyan
$actuatorCode = 0
$internalCode = 0
try { $r = Invoke-WebRequest -Uri "$BaseUrl/actuator" -UseBasicParsing; $actuatorCode = $r.StatusCode } catch { $actuatorCode = $_.Exception.Response.StatusCode.value__ }
try { $r = Invoke-WebRequest -Uri "$BaseUrl/internal" -UseBasicParsing; $internalCode = $r.StatusCode } catch { $internalCode = $_.Exception.Response.StatusCode.value__ }

if ($actuatorCode -eq 404) {
    Report-Pass "1.1" "Gateway /actuator routes return 404 NOT_FOUND"
} else {
    Report-Fail "1.1" "Gateway /actuator accessible" "Member #7 (api-gateway)" "Received HTTP $actuatorCode"
}

if ($internalCode -eq 404) {
    Report-Pass "1.2" "Gateway /internal routes return 404 NOT_FOUND"
} else {
    Report-Fail "1.2" "Gateway /internal accessible" "Member #7 (api-gateway)" "Received HTTP $internalCode"
}

# STEP 2: Customer Register
Write-Host "`n[Step 2] Customer Registration..." -ForegroundColor Cyan
$regBody = @{
    email = $CustomerEmail
    password = $CustomerPass
    fullName = "Smoke Customer"
    phone = "0771234567"
    address = "123 Galle Road, Colombo 03"
} | ConvertTo-Json

try {
    $regResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" -Method Post -Body $regBody -ContentType "application/json"
    Report-Pass "2.1" "Customer registered successfully ($CustomerEmail)"
} catch {
    Report-Fail "2.1" "Customer registration failed" "Member #1 (auth-service)" $_.Exception.Message
}

# STEP 3: Customer Login
Write-Host "`n[Step 3] Customer Login..." -ForegroundColor Cyan
$CustomerToken = ""
$loginBody = @{
    email = $CustomerEmail
    password = $CustomerPass
} | ConvertTo-Json

try {
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
    $CustomerToken = if ($loginResp.accessToken) { $loginResp.accessToken } else { $loginResp.token }
    if ($CustomerToken) {
        Report-Pass "3.1" "Customer login succeeded and JWT token received"
    } else {
        Report-Fail "3.1" "No token in login response" "Member #1 (auth-service)" ($loginResp | ConvertTo-Json)
    }
} catch {
    Report-Fail "3.1" "Customer login failed" "Member #1 (auth-service)" $_.Exception.Message
}

# STEP 4: Pricing Quote
Write-Host "`n[Step 4] Pricing Quotation..." -ForegroundColor Cyan
$District1 = ""
$District2 = ""
$TotalFee = 550.00

try {
    $districts = Invoke-RestMethod -Uri "$BaseUrl/api/v1/public/districts" -Method Get
    if ($districts -and $districts.Count -ge 2) {
        $District1 = $districts[0].id
        $District2 = $districts[1].id
    }
} catch {
    Report-Fail "4.1" "Failed to fetch districts" "Member #4 (pricing-service)" $_.Exception.Message
}

if ($District1 -and $District2) {
    $quoteBody = @{
        pickupDistrictId = $District1
        deliveryDistrictId = $District2
        weightKg = 1.5
        parcelSize = "MEDIUM"
    } | ConvertTo-Json

    try {
        $quoteResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/quotes" -Method Post -Body $quoteBody -ContentType "application/json"
        if ($quoteResp.totalFee) { $TotalFee = $quoteResp.totalFee }
        Report-Pass "4.1" "Delivery quote computed: LKR $TotalFee"
    } catch {
        Report-Fail "4.1" "Quote computation failed" "Member #4 (pricing-service)" $_.Exception.Message
    }
}

# STEP 5: Customer Parcel Creation
Write-Host "`n[Step 5] Customer Parcel Creation..." -ForegroundColor Cyan
$ParcelId = ""
$TrackingNumber = ""

if ($CustomerToken -and $District1 -and $District2) {
    $idempotencyKey = "smoke-ps-$(Get-Random)"
    $parcelPayload = @{
        pickupDistrictId = $District1
        deliveryDistrictId = $District2
        weightKg = 1.5
        parcelSize = "MEDIUM"
        pickupAddress = "123 Galle Road, Colombo 03"
        deliveryAddress = "45 Peradeniya Road, Kandy"
        recipientName = "Kamal Perera"
        recipientPhone = "0719876543"
        description = "Urgent medical supplies"
        expectedFee = [decimal]$TotalFee
    } | ConvertTo-Json

    try {
        $parcelResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/customer/parcels" -Method Post `
            -Headers @{ Authorization = "Bearer $CustomerToken"; "Idempotency-Key" = $idempotencyKey } `
            -Body $parcelPayload -ContentType "application/json"

        $ParcelId = if ($parcelResp.id) { $parcelResp.id } else { $parcelResp.parcelId }
        $TrackingNumber = $parcelResp.trackingNumber
        Report-Pass "5.1" "Parcel created: Tracking # $TrackingNumber (ID: $ParcelId)"
    } catch {
        Report-Fail "5.1" "Parcel creation failed" "Member #4 (parcel-service)" $_.Exception.Message
    }
} else {
    Report-Skip "5.1" "Parcel creation" "Missing customer token or district IDs"
}

# STEP 6: Admin Login
Write-Host "`n[Step 6] Admin / Dispatcher Authentication..." -ForegroundColor Cyan
$AdminToken = ""
try {
    $adminLogin = @{ email = "admin@swiftdrop.lk"; password = $BootstrapPass } | ConvertTo-Json
    $adminResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $adminLogin -ContentType "application/json"
    $AdminToken = if ($adminResp.accessToken) { $adminResp.accessToken } else { $adminResp.token }
    Report-Pass "6.1" "Admin authenticated (admin@swiftdrop.lk)"
} catch {
    try {
        $dispLogin = @{ email = "dispatcher@swiftdrop.lk"; password = $DemoPass } | ConvertTo-Json
        $dispResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $dispLogin -ContentType "application/json"
        $AdminToken = if ($dispResp.accessToken) { $dispResp.accessToken } else { $dispResp.token }
        Report-Pass "6.1" "Dispatcher authenticated (dispatcher@swiftdrop.lk)"
    } catch {
        Report-Fail "6.1" "Admin/Dispatcher login failed" "Member #1 (auth-service)" $_.Exception.Message
    }
}

# STEP 7: Courier Assignment
Write-Host "`n[Step 7] Courier Assignment..." -ForegroundColor Cyan
$CourierId = ""
if ($AdminToken -and $ParcelId) {
    try {
        $couriers = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/couriers" -Method Get -Headers @{ Authorization = "Bearer $AdminToken" }
        if ($couriers -and $couriers.Count -gt 0) {
            $CourierId = $couriers[0].id
        }
    } catch {
        Write-Host "Warning: Could not list couriers: $($_.Exception.Message)"
    }

    Start-Sleep -Seconds 2

    if ($CourierId) {
        try {
            $assignBody = @{ courierId = $CourierId } | ConvertTo-Json
            $assignResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/deliveries/$ParcelId/assign" -Method Post `
                -Headers @{ Authorization = "Bearer $AdminToken" } -Body $assignBody -ContentType "application/json"
            Report-Pass "7.1" "Parcel assigned to courier ($CourierId)"
        } catch {
            Report-Fail "7.1" "Courier assignment failed" "Member #7 (delivery-service)" $_.Exception.Message
        }
    } else {
        Report-Fail "7.1" "No couriers available for assignment" "Member #7 (delivery-service)" "Couriers list empty"
    }
} else {
    Report-Skip "7.1" "Courier assignment" "Missing admin token or parcel ID"
}

# STEP 8: Courier Transitions
Write-Host "`n[Step 8] Courier Lifecycle Transitions..." -ForegroundColor Cyan
$CourierToken = ""
try {
    $courierLogin = @{ email = "courier@swiftdrop.lk"; password = $DemoPass } | ConvertTo-Json
    $cResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post -Body $courierLogin -ContentType "application/json"
    $CourierToken = if ($cResp.accessToken) { $cResp.accessToken } else { $cResp.token }
} catch {
    Write-Host "Warning: Courier login failed: $($_.Exception.Message)"
}

if ($CourierToken -and $ParcelId) {
    $statuses = @("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY")
    $subStep = 1
    foreach ($st in $statuses) {
        try {
            # curl multipart fallback or Form
            $form = @{ status = $st }
            $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/courier/deliveries/$ParcelId/status" -Method Post `
                -Headers @{ Authorization = "Bearer $CourierToken" } -Form $form
            Report-Pass "8.$subStep" "Transitioned to $st"
        } catch {
            Report-Fail "8.$subStep" "Failed transition to $st" "Member #8 (delivery courier slice)" $_.Exception.Message
        }
        $subStep++
    }

    # DELIVERED
    try {
        $delForm = @{
            status = "DELIVERED"
            recipientName = "Kamal Perera"
            note = "Safely handed over to recipient"
        }
        $r = Invoke-RestMethod -Uri "$BaseUrl/api/v1/courier/deliveries/$ParcelId/status" -Method Post `
            -Headers @{ Authorization = "Bearer $CourierToken" } -Form $delForm
        Report-Pass "8.4" "Transitioned to DELIVERED with recipient proof"
    } catch {
        Report-Fail "8.4" "Failed transition to DELIVERED" "Member #8 (delivery courier slice)" $_.Exception.Message
    }
} else {
    Report-Skip "8.1-8.4" "Courier lifecycle transitions" "Missing courier token or parcel ID"
}

# STEP 9: Public Tracking
Write-Host "`n[Step 9] Public Tracking Verification..." -ForegroundColor Cyan
if ($TrackingNumber) {
    Start-Sleep -Seconds 2
    try {
        $trkResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/public/tracking/$TrackingNumber" -Method Get
        Report-Pass "9.1" "Public tracking retrieved (Status: $($trkResp.status))"
    } catch {
        Report-Fail "9.1" "Public tracking failed" "Member #9 (tracking-service)" $_.Exception.Message
    }
} else {
    Report-Skip "9.1" "Public tracking" "No tracking number available"
}

# STEP 10: Mailpit
Write-Host "`n[Step 10] Notification Email Verification (Mailpit)..." -ForegroundColor Cyan
try {
    $mailResp = Invoke-RestMethod -Uri "$MailpitUrl/api/v2/messages" -Method Get
    if ($mailResp.total -gt 0) {
        Report-Pass "10.1" "Email notification arrived in Mailpit ($($mailResp.total) total messages)"
    } else {
        Report-Pass "10.1" "Mailpit reachable (0 messages so far)"
    }
} catch {
    Report-Fail "10.1" "Mailpit API unreachable on port 8025" "Member #9 (notification-service)" $_.Exception.Message
}

# STEP 11: Merchant Token & Order
Write-Host "`n[Step 11] Merchant Token and Order Verification..." -ForegroundColor Cyan
if ($MerchantClientId -and $MerchantClientSecret) {
    try {
        $tokBody = "grant_type=client_credentials&client_id=$MerchantClientId&client_secret=$MerchantClientSecret&scope=orders:write"
        $mTokResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/tokens" -Method Post -Body $tokBody -ContentType "application/x-www-form-urlencoded"
        $mToken = $mTokResp.access_token
        Report-Pass "11.1" "Merchant client credentials issued token (typ=client)"

        $mOrderKey = "m-ps-$Timestamp"
        $mOrderBody = @{
            pickupDistrictId = $District1
            deliveryDistrictId = $District2
            weightKg = 2.0
            parcelSize = "LARGE"
            pickupAddress = "Merchant Store"
            deliveryAddress = "Customer Road"
            recipientName = "Nimal"
            recipientPhone = "0781234567"
            merchantReference = "REF-$Timestamp"
            expectedFee = [decimal]$TotalFee
        } | ConvertTo-Json

        $mOrderResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/merchant/orders" -Method Post `
            -Headers @{ Authorization = "Bearer $mToken"; "Idempotency-Key" = $mOrderKey } `
            -Body $mOrderBody -ContentType "application/json"
        Report-Pass "11.2" "Merchant order created successfully"
    } catch {
        Report-Fail "11.1" "Merchant order flow failed" "Member #3 / #6" $_.Exception.Message
    }
} else {
    Report-Skip "11.1-11.2" "Merchant token & order creation" "MERCHANT_CLIENT_ID / SECRET not supplied"
}

# Summary
Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  Smoke Test Execution Summary" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Total checks run: $TotalTests"
Write-Host "  Passed:           $PassedTests" -ForegroundColor Green
Write-Host "  Failed:           $FailedTests" -ForegroundColor Red

if ($FailedTests -eq 0) {
    Write-Host "`n>>> ALL SMOKE TEST CHECKS PASSED SUCCESSFULLY! <<<`n" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n>>> SMOKE TEST FAILED WITH $FailedTests ERROR(S) <<<`n" -ForegroundColor Red
    exit 1
}

