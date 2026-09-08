#Requires -Version 5.1
<#
.SYNOPSIS
    Configura SIBIM Desktop en una PC nueva.
    Crea %APPDATA%\SIBIM\.env con las credenciales de conexion.

.EXAMPLE
    .\configurar-sibim.ps1              # modo interactivo (pide contrasena y clave)
    .\configurar-sibim.ps1 -Silent      # usa variables de entorno SIBIM_DB_PASS y SIBIM_SUPABASE_KEY
#>
param([switch]$Silent)

$ErrorActionPreference = "Stop"

# ── Valores fijos de la instalacion municipal ─────────────────────────────────
$DB_URL      = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?user=postgres.zjrzrcfpvkkefsscvzyh&password={PASSWORD}"
$DB_USER     = "postgres.zjrzrcfpvkkefsscvzyh"
$DB_SSL_MODE = "require"
$SUPABASE_URL = "https://zjrzrcfpvkkefsscvzyh.supabase.co"

# ── Destino ───────────────────────────────────────────────────────────────────
$SibimDir = "$env:APPDATA\SIBIM"
$EnvFile  = "$SibimDir\.env"

Write-Host ""
Write-Host "  SIBIM Desktop — Configuracion inicial" -ForegroundColor Cyan
Write-Host "  ======================================" -ForegroundColor Cyan
Write-Host ""

if (Test-Path $EnvFile) {
    Write-Host "  Ya existe una configuracion en $EnvFile" -ForegroundColor Yellow
    $resp = if ($Silent) { "n" } else { Read-Host "  Sobreescribir? [s/N]" }
    if ($resp -notin @("s","S")) { Write-Host "  Cancelado."; exit 0 }
}

# ── Pedir contrasena de BD ────────────────────────────────────────────────────
if ($Silent) {
    $Password = $env:SIBIM_DB_PASS
    if (-not $Password) { throw "Modo -Silent requiere variable de entorno SIBIM_DB_PASS" }
} else {
    Write-Host "  [1/2] Contrasena de la base de datos"
    Write-Host "  (Supabase Dashboard -> Settings -> Database -> Database password)" -ForegroundColor DarkGray
    Write-Host ""
    $SecurePass = Read-Host "  Contrasena BD" -AsSecureString
    $Password   = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePass))
}

# ── Pedir Supabase Service Key ────────────────────────────────────────────────
if ($Silent) {
    $SupabaseKey = $env:SIBIM_SUPABASE_KEY
    if (-not $SupabaseKey) { throw "Modo -Silent requiere variable de entorno SIBIM_SUPABASE_KEY" }
} else {
    Write-Host ""
    Write-Host "  [2/2] Supabase Service Key (para fotos de bienes)"
    Write-Host "  (Supabase Dashboard -> Settings -> API -> service_role -> Reveal)" -ForegroundColor DarkGray
    Write-Host ""
    $SecureKey  = Read-Host "  Service Key" -AsSecureString
    $SupabaseKey = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureKey))
}

# ── Escribir .env ─────────────────────────────────────────────────────────────
New-Item -ItemType Directory -Force -Path $SibimDir | Out-Null

$content = @"
# SIBIM Desktop — Configuracion de conexion
# Generado automaticamente por configurar-sibim.ps1
# NO compartir este archivo — contiene credenciales.

# Session Pooler Supabase (IPv4)
DB_URL=$($DB_URL -replace '\{PASSWORD\}', $Password)
DB_USER=$DB_USER
DB_PASSWORD=$Password
DB_SSL_MODE=$DB_SSL_MODE

# Supabase Storage (fotos de bienes)
SUPABASE_URL=$SUPABASE_URL
SUPABASE_SERVICE_KEY=$SupabaseKey
"@

[System.IO.File]::WriteAllText($EnvFile, $content, [System.Text.Encoding]::UTF8)

Write-Host ""
Write-Host "  Configuracion guardada en:" -ForegroundColor Green
Write-Host "  $EnvFile" -ForegroundColor Green
Write-Host ""
Write-Host "  Puedes iniciar SIBIM Desktop ahora." -ForegroundColor Green
Write-Host ""
