<#
.SYNOPSIS
    Genera el SQL para insertar el primer usuario administrador en la base de datos de producción.

.DESCRIPTION
    1. Pide el nombre completo, username y contraseña del admin.
    2. Genera el hash BCrypt (usando el JAR compilado del proyecto).
    3. Muestra el INSERT listo para pegar en psql o pgAdmin.

    Ejecutar DESPUÉS de haber compilado el proyecto ('mvn package').

.EXAMPLE
    .\packaging\crear-admin.ps1
#>

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

# ── Localizar el JAR compilado ────────────────────────────────────────────────
$jar = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter "sibim-desktop-*.jar" |
    Where-Object { $_.Name -notlike "*-sources*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $jar) {
    Write-Error "No se encontro el JAR en target\. Ejecuta 'mvn package' primero."
    exit 1
}

# ── Localizar java ────────────────────────────────────────────────────────────
$java = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidate) { $java = $candidate }
}
if (-not $java) {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { $java = $cmd.Source }
}
if (-not $java) {
    Write-Error "No se encontro java.exe. Configura JAVA_HOME o agrega Java al PATH."
    exit 1
}

# ── Recopilar datos del admin ─────────────────────────────────────────────────
Write-Host ""
Write-Host "=== Crear primer administrador de SIBIM ===" -ForegroundColor Cyan
Write-Host ""

$nombre   = Read-Host "Nombre completo del administrador"
$cargo    = Read-Host "Cargo                             (ej: Jefe de Patrimonio)"
$username = Read-Host "Nombre de usuario (para login)    (ej: admin.patrimonio)"

Write-Host ""
$pass1 = Read-Host "Contraseña inicial" -AsSecureString
$pass2 = Read-Host "Confirma contraseña" -AsSecureString

$p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pass1))
$p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pass2))

if ($p1 -ne $p2) {
    Write-Error "Las contraseñas no coinciden."
    exit 1
}
if ($p1.Length -lt 8) {
    Write-Error "La contraseña debe tener al menos 8 caracteres."
    exit 1
}

# ── Generar hash BCrypt usando el JAR ────────────────────────────────────────
Write-Host "`nGenerando hash BCrypt (puede tardar ~1 segundo)..." -ForegroundColor DarkGray
$hash = & $java -cp $jar.FullName com.sibim.util.HashUtil $p1 2>&1
if ($LASTEXITCODE -ne 0 -or -not $hash -or $hash -notlike '$2a$*') {
    Write-Error "No se pudo generar el hash. Salida: $hash"
    exit 1
}

# ── Generar UUID ──────────────────────────────────────────────────────────────
$id = [System.Guid]::NewGuid().ToString()

# ── Mostrar SQL ───────────────────────────────────────────────────────────────
$sql = @"

-- ============================================================
-- Pega este bloque en psql o pgAdmin DESPUÉS de ejecutar sibim.sql
-- La contraseña está hasheada con BCrypt — el sistema pedirá
-- cambiarla en el primer inicio de sesión (debe_cambiar_password = TRUE).
-- ============================================================

INSERT INTO users (id, username, password, nombre, cargo, role, area, debe_cambiar_password)
VALUES (
    '$id',
    '$($username.ToLower())',
    '$hash',
    '$nombre',
    '$cargo',
    'admin',
    NULL,
    TRUE
)
ON CONFLICT (username) DO NOTHING;

-- ============================================================
"@

Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "  SQL generado — copia y pega en psql o pgAdmin:" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green
Write-Host $sql
Write-Host "======================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Pasos siguientes:" -ForegroundColor Yellow
Write-Host "  1. Ejecuta sibim.sql contra tu base de datos de produccion."
Write-Host "  2. Ejecuta el INSERT de arriba."
Write-Host "  3. Configura .env con tus credenciales de PostgreSQL."
Write-Host "  4. Inicia SIBIM — el sistema pedira cambiar la contraseña en el primer login."
Write-Host ""
