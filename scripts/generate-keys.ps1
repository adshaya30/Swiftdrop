# scripts/generate-keys.ps1
# DEP-013: Generate RSA 2048 JWT key pair using Docker alpine/openssl.
# Produces:
#   infrastructure\keys\swiftdrop_private.pem  (PKCS#8 PEM, never committed)
#   infrastructure\keys\swiftdrop_public.pem   (PEM, never committed)
# Note: Uses docker to avoid requiring a host OpenSSL install.
# Outputs plain ASCII PEM files (no UTF-16, no BOM).

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$keysDir = Join-Path $repoRoot "infrastructure\keys"
$privateKey = Join-Path $keysDir "swiftdrop_private.pem"
$publicKey = Join-Path $keysDir "swiftdrop_public.pem"

if (Test-Path $privateKey) {
    Write-Host "Keys already exist at $keysDir - delete them first if you want to regenerate."
    exit 0
}

if (-not (Test-Path $keysDir)) {
    New-Item -ItemType Directory -Force -Path $keysDir | Out-Null
}

Write-Host "Generating RSA 2048 key pair in $keysDir ..."

# Convert Windows path to Docker-friendly path
$keysDirDocker = $keysDir -replace '\\', '/' -replace '^([A-Za-z]):', '/$1'

# Generate PKCS#8 private key
docker run --rm `
    -v "${keysDirDocker}:/keys" `
    alpine/openssl `
    genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /keys/swiftdrop_private.pem

if ($LASTEXITCODE -ne 0) { throw "Failed to generate private key" }

# Derive public key
docker run --rm `
    -v "${keysDirDocker}:/keys" `
    alpine/openssl `
    rsa -pubout `
    -in /keys/swiftdrop_private.pem `
    -out /keys/swiftdrop_public.pem

if ($LASTEXITCODE -ne 0) { throw "Failed to derive public key" }

Write-Host "Done. Keys written to $keysDir"
Write-Host "  Private: $privateKey"
Write-Host "  Public:  $publicKey"

