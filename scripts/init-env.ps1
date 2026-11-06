# scripts/init-env.ps1
# Copy .env.example to .env and replace every CHANGE_ME_* placeholder with
# a securely-generated random value. Will NOT overwrite an existing .env
# without explicit confirmation.

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$envExample = Join-Path $repoRoot ".env.example"
$envFile = Join-Path $repoRoot ".env"

if (Test-Path $envFile) {
    $reply = Read-Host ".env already exists. Overwrite? [y/N]"
    if ($reply -notmatch '^[Yy]$') {
        Write-Host "Keeping existing .env. Exiting."
        exit 0
    }
    Remove-Item $envFile -Force
}

Write-Host "Creating $envFile from $envExample ..."
Copy-Item $envExample $envFile

# Helper: generate random hex string (output = 2*bytes hex chars, plain ASCII)
function New-RandomHex {
    param([int]$Bytes = 32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $buf = New-Object byte[] $Bytes
    $rng.GetBytes($buf)
    return [System.BitConverter]::ToString($buf).Replace('-', '').ToLower()
}

# Helper: generate base64 of random bytes (no line breaks)
function New-RandomBase64 {
    param([int]$Bytes = 32)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $buf = New-Object byte[] $Bytes
    $rng.GetBytes($buf)
    return [System.Convert]::ToBase64String($buf)
}

$content = Get-Content $envFile -Raw

# DB passwords and RabbitMQ password
$vars = @("POSTGRES_PASSWORD","AUTH_DB_PASSWORD","PARCEL_DB_PASSWORD","PRICING_DB_PASSWORD",
          "DELIVERY_DB_PASSWORD","TRACKING_DB_PASSWORD","NOTIFICATION_DB_PASSWORD","RABBITMQ_DEFAULT_PASS")
foreach ($var in $vars) {
    $val = New-RandomHex -Bytes 20
    $content = $content -replace "CHANGE_ME_$var", $val
}

# INTERNAL_API_KEY: at least 32 characters
$internalKey = New-RandomHex -Bytes 32
$content = $content -replace "CHANGE_ME_INTERNAL_API_KEY", $internalKey

# WEBHOOK_SECRET_ENCRYPTION_KEY: Base64 of 32 random bytes
$webhookKey = New-RandomBase64 -Bytes 32
$content = $content -replace "CHANGE_ME_WEBHOOK_SECRET_ENCRYPTION_KEY", $webhookKey

# Bootstrap admin password
$adminPass = New-RandomHex -Bytes 16
$content = $content -replace "CHANGE_ME_BOOTSTRAP_ADMIN_PASSWORD", $adminPass

# Demo user password
$demoPass = New-RandomHex -Bytes 12
$content = $content -replace "CHANGE_ME_DEMO_USER_PASSWORD", $demoPass

# Write as UTF-8 without BOM (plain ASCII-safe)
[System.IO.File]::WriteAllText($envFile, $content, [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host ".env created. Next steps:"
Write-Host "  1. Run .\scripts\generate-keys.ps1 to generate JWT RSA keys"
Write-Host "  2. Run: docker compose -f compose.infra.yaml up -d"
Write-Host ""
Write-Host "Bootstrap admin password: $adminPass"
Write-Host "(Change this in .env before sharing with anyone)"

