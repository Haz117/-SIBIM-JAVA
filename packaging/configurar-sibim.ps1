#Requires -Version 5.1
<#
.SYNOPSIS
    Configura SIBIM Desktop en una PC nueva.
    Crea %APPDATA%\SIBIM\.env con las credenciales de conexion.

.DESCRIPTION
    Por defecto configura una PC de trabajo con el rol de minimo privilegio
    sibim_app (sin permisos para cambiar el esquema; ver
    scripts\sql\rol_app_minimo.sql, que debe haberse ejecutado antes en la base)
    y DB_MIGRATE=false. Con -Admin configura la PC de administracion, que usa
    el usuario dueno (postgres) y aplica las migraciones de cada version nueva.

.EXAMPLE
    .\configurar-sibim.ps1              # PC de trabajo (sibim_app), modo interactivo
    .\configurar-sibim.ps1 -Admin       # PC de administracion (postgres)
    .\configurar-sibim.ps1 -Silent      # usa variables de entorno SIBIM_DB_PASS y SIBIM_SUPABASE_ANON_KEY
#>
param([switch]$Silent, [switch]$Admin)

$ErrorActionPreference = "Stop"

# ── Valores fijos de la instalacion municipal ─────────────────────────────────
# Sin usuario ni contrasena en la URL: el driver les da prioridad sobre
# DB_USER/DB_PASSWORD, asi que ahi anulaban el usuario configurado.
$PROYECTO    = "zjrzrcfpvkkefsscvzyh"
$DB_URL      = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres"
$DB_USER     = if ($Admin) { "postgres.$PROYECTO" } else { "sibim_app.$PROYECTO" }
$DB_SSL_MODE = "require"
$SUPABASE_URL = "https://$PROYECTO.supabase.co"

# ── Destino ───────────────────────────────────────────────────────────────────
$SibimDir = "$env:APPDATA\SIBIM"
$EnvFile  = "$SibimDir\.env"

Write-Host ""
Write-Host "  SIBIM Desktop - Configuracion inicial" -ForegroundColor Cyan
Write-Host "  ======================================" -ForegroundColor Cyan
Write-Host ""
if ($Admin) {
    Write-Host "  PC de ADMINISTRACION: usuario dueno de la base (aplica migraciones)." -ForegroundColor Yellow
} else {
    Write-Host "  PC de trabajo: usuario sibim_app (requiere haber ejecutado scripts\sql\rol_app_minimo.sql)."
}
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
    if ($Admin) {
        Write-Host "  [1/2] Contrasena del usuario postgres"
        Write-Host "  (Supabase Dashboard -> Settings -> Database -> Database password)" -ForegroundColor DarkGray
    } else {
        Write-Host "  [1/2] Contrasena de sibim_app"
        Write-Host "  (la que se puso en scripts\sql\rol_app_minimo.sql)" -ForegroundColor DarkGray
    }
    Write-Host ""
    $SecurePass = Read-Host "  Contrasena BD" -AsSecureString
    $Password   = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecurePass))
}

# ── Pedir Supabase public/anon key ────────────────────────────────────────────
if ($Silent) {
    $SupabaseKey = $env:SIBIM_SUPABASE_ANON_KEY
    if (-not $SupabaseKey) { throw "Modo -Silent requiere variable de entorno SIBIM_SUPABASE_ANON_KEY" }
} else {
    Write-Host ""
    Write-Host "  [2/2] Supabase anon/public key (para fotos de bienes)"
    Write-Host "  (Supabase Dashboard -> Settings -> API -> anon public)" -ForegroundColor DarkGray
    Write-Host ""
    $SecureKey  = Read-Host "  Anon public key" -AsSecureString
    $SupabaseKey = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
                    [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureKey))
}

# Una clave secreta (sb_secret_... o un JWT con role service_role) da control
# total del proyecto a quien abra el .env: nunca debe llegar a una PC.
$esSecreta = $SupabaseKey.StartsWith("sb_secret_")
$partes = $SupabaseKey.Split(".")
if (-not $esSecreta -and $partes.Length -eq 3) {
    try {
        $b64 = $partes[1].Replace("-", "+").Replace("_", "/")
        $b64 = $b64.PadRight($b64.Length + (4 - $b64.Length % 4) % 4, "=")
        $payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64))
        $esSecreta = $payload -match '"role"\s*:\s*"service_role"'
    } catch { }
}
if ($esSecreta) { throw "Esa es una clave SECRETA de Supabase (service_role). Usa la clave publica 'anon'." }

# ── Escribir .env ─────────────────────────────────────────────────────────────
New-Item -ItemType Directory -Force -Path $SibimDir | Out-Null

$migrar = if ($Admin) { "" } else { "`r`n# Esta PC no cambia el esquema; la PC de administracion aplica las migraciones.`r`nDB_MIGRATE=false" }
$content = @"
# SIBIM Desktop - Configuracion de conexion
# Generado automaticamente por configurar-sibim.ps1
# NO compartir este archivo - contiene credenciales.

# Session Pooler Supabase (IPv4)
DB_URL=$DB_URL
DB_USER=$DB_USER
DB_PASSWORD=$Password
DB_SSL_MODE=$DB_SSL_MODE$migrar

# Supabase Storage (fotos de bienes)
SUPABASE_URL=$SUPABASE_URL
SUPABASE_ANON_KEY=$SupabaseKey
"@

[System.IO.File]::WriteAllText($EnvFile, $content, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "  Configuracion guardada en:" -ForegroundColor Green
Write-Host "  $EnvFile" -ForegroundColor Green
Write-Host ""
Write-Host "  Puedes iniciar SIBIM Desktop ahora." -ForegroundColor Green
Write-Host ""
