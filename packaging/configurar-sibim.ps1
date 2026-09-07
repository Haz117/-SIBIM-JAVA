#Requires -Version 5.1
<#
.SYNOPSIS
    Configura SIBIM Desktop en una PC nueva.
    Crea %APPDATA%\SIBIM\.env con las credenciales de conexión.

.EXAMPLE
    .\configurar-sibim.ps1              # modo interactivo (pide contraseña)
    .\configurar-sibim.ps1 -Silent      # usa los valores hardcodeados abajo
#>
param([switch]$Silent)

$ErrorActionPreference = "Stop"

# ── Valores fijos de la instalación municipal ─────────────────────────────────
$DB_URL         = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres?user=postgres.zjrzrcfpvkkefsscvzyh&password={PASSWORD}"
$DB_USER        = "postgres.zjrzrcfpvkkefsscvzyh"
$DB_SSL_MODE    = "require"
$SUPABASE_URL   = "https://zjrzrcfpvkkefsscvzyh.supabase.co"
$SUPABASE_KEY   = $env:SIBIM_SUPABASE_KEY   # Pasar como variable de entorno — NO hardcodear aquí

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

# ── Pedir contraseña ──────────────────────────────────────────────────────────
if ($Silent) {
    # En modo silencioso la contraseña debe pasarse como variable de entorno SIBIM_DB_PASS
    $Password = $env:SIBIM_DB_PASS
    if (-not $Password) { throw "Modo -Silent requiere variable de entorno SIBIM_DB_PASS" }
} else {
    Write-Host "  Ingresa la contrasena de la base de datos Supabase"
    Write-Host "  (Supabase Dashboard -> Settings -> Database -> Database password)" -ForegroundColor DarkGray
    Write-Host ""
    $SecurePass = Read-Host "  Contrasena" -AsSecureString
    $Password   = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePass))
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
SUPABASE_SERVICE_KEY=$SUPABASE_KEY
"@

[System.IO.File]::WriteAllText($EnvFile, $content, [System.Text.Encoding]::UTF8)

Write-Host ""
Write-Host "  Configuracion guardada en:" -ForegroundColor Green
Write-Host "  $EnvFile" -ForegroundColor Green
Write-Host ""
Write-Host "  Puedes iniciar SIBIM Desktop ahora." -ForegroundColor Green
Write-Host ""
