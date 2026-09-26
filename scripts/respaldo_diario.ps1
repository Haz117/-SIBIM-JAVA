#Requires -Version 5.1
<#
.SYNOPSIS
    Respaldo completo de la base de SIBIM con pg_dump, con retención de N días.

.DESCRIPTION
    Lee la conexión del mismo .env que usa SIBIM (%APPDATA%\SIBIM\.env, o el
    .env de la carpeta del proyecto) y guarda un respaldo en formato custom de
    pg_dump (se restaura con pg_restore). Borra solo los respaldos de esa misma
    carpeta con más de -DiasRetencion días.

    El respaldo incluye los hashes de contraseña de los usuarios y todo el
    inventario: guárdalo en una carpeta a la que solo tenga acceso quien
    administra el sistema (y de preferencia en un disco cifrado con BitLocker),
    y copia periódicamente la carpeta fuera de esta PC.

    Requiere pg_dump de la misma versión mayor que el servidor o más nueva
    (Supabase usa PostgreSQL 15/17). Si no está instalado: instalador de
    PostgreSQL de EDB para Windows, marcando solo "Command Line Tools".

.PARAMETER Destino
    Carpeta de los respaldos. Por defecto %APPDATA%\SIBIM\respaldos.

.PARAMETER DiasRetencion
    Respaldos más viejos que esto se borran. Por defecto 30.

.PARAMETER Registrar
    En vez de respaldar, registra una tarea programada de Windows que ejecuta
    este script todos los días a la -Hora indicada (por defecto 23:00) con la
    cuenta actual.

.EXAMPLE
    .\scripts\respaldo_diario.ps1
    .\scripts\respaldo_diario.ps1 -Registrar -Hora 22:30
    pg_restore --clean --if-exists -d "<cadena de conexión>" sibim-20260926-2300.dump
#>
param(
    [string]$Destino = (Join-Path $env:APPDATA "SIBIM\respaldos"),
    [int]$DiasRetencion = 30,
    [switch]$Registrar,
    [string]$Hora = "23:00"
)

$ErrorActionPreference = "Stop"

if ($Registrar) {
    $accion = New-ScheduledTaskAction -Execute "powershell.exe" `
        -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Destino `"$Destino`" -DiasRetencion $DiasRetencion"
    $disparador = New-ScheduledTaskTrigger -Daily -At $Hora
    $ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName "SIBIM - respaldo diario" -Action $accion -Trigger $disparador `
        -Settings $ajustes -Description "pg_dump de la base de SIBIM (scripts\respaldo_diario.ps1)" -Force | Out-Null
    Write-Host "Tarea 'SIBIM - respaldo diario' registrada: todos los días a las $Hora."
    exit 0
}

# ── Conexión (mismo orden que DatabaseConfig) ────────────────────────────────
function Leer-Env([string]$ruta) {
    $vars = @{}
    if (Test-Path $ruta) {
        foreach ($linea in Get-Content $ruta -Encoding UTF8) {
            if ($linea -match '^\s*([A-Z_]+)\s*=\s*(.*)$' -and -not $linea.TrimStart().StartsWith('#')) {
                $vars[$Matches[1]] = $Matches[2].Trim()
            }
        }
    }
    return $vars
}

$envProd = Leer-Env (Join-Path $env:APPDATA "SIBIM\.env")
$envDev  = Leer-Env (Join-Path (Split-Path -Parent $PSScriptRoot) ".env")
$cfg = if ($envProd.ContainsKey("DB_URL")) { $envProd } else { $envDev }
if (-not $cfg.ContainsKey("DB_URL")) { throw "No encontré DB_URL en %APPDATA%\SIBIM\.env ni en el .env del proyecto." }

if ($cfg["DB_URL"] -notmatch '^jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)') {
    throw "DB_URL no tiene el formato jdbc:postgresql://host:puerto/base"
}
$pgHost = $Matches[1]
$pgPort = if ($Matches[2]) { $Matches[2] } else { "5432" }
$pgDb   = $Matches[3]

# ── pg_dump ──────────────────────────────────────────────────────────────────
$pgDump = (Get-Command pg_dump -ErrorAction SilentlyContinue).Source
if (-not $pgDump) {
    $pgDump = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\pg_dump.exe" -ErrorAction SilentlyContinue |
        Sort-Object { [int]($_.Directory.Parent.Name -replace '\D', '') } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $pgDump) { throw "No encontré pg_dump. Instala las 'Command Line Tools' de PostgreSQL (instalador de EDB)." }

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$archivo = Join-Path $Destino ("sibim-{0}.dump" -f (Get-Date -Format "yyyyMMdd-HHmm"))
$temporal = "$archivo.parcial"

# La contraseña solo vive en el entorno de este proceso.
$env:PGPASSWORD = $cfg["DB_PASSWORD"]
$env:PGSSLMODE  = if ($cfg["DB_SSL_MODE"]) { $cfg["DB_SSL_MODE"] } else { "require" }
try {
    & $pgDump --host $pgHost --port $pgPort --username $cfg["DB_USER"] --dbname $pgDb `
        --format custom --no-owner --no-privileges --schema public --file $temporal
    if ($LASTEXITCODE -ne 0) { throw "pg_dump terminó con código $LASTEXITCODE" }
    Move-Item $temporal $archivo -Force
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if (Test-Path $temporal) { Remove-Item $temporal -Force }
}

$tamano = [math]::Round((Get-Item $archivo).Length / 1MB, 2)
Write-Host "Respaldo guardado: $archivo ($tamano MB)"

# ── Retención: solo archivos sibim-*.dump de esta carpeta ────────────────────
Get-ChildItem $Destino -Filter "sibim-*.dump" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$DiasRetencion) } |
    ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "Borrado por antigüedad: $($_.Name)" }
