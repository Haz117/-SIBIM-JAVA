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
    (Supabase usa PostgreSQL 15/17). Lo busca primero en tools\pgsql\bin (una
    copia portátil, que deja scripts\instalar_pg_dump.ps1), luego en el PATH y
    en C:\Program Files\PostgreSQL.

.PARAMETER Destino
    Carpeta de los respaldos. Por defecto %APPDATA%\SIBIM\respaldos.

.PARAMETER DiasRetencion
    Respaldos más viejos que esto se borran. Por defecto 30.

.PARAMETER Registrar
    Registra una tarea programada de Windows que ejecuta este script todos los
    días a la -Hora indicada (por defecto 23:00) con la cuenta actual, y hace
    un primer respaldo en ese momento para comprobar que funciona.

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
    Write-Host "Primer respaldo, para comprobar que funciona..."
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
$portatil = Join-Path (Split-Path -Parent $PSScriptRoot) "tools\pgsql\bin\pg_dump.exe"
$pgDump = if (Test-Path $portatil) { $portatil } else { (Get-Command pg_dump -ErrorAction SilentlyContinue).Source }
if (-not $pgDump) {
    $pgDump = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\pg_dump.exe" -ErrorAction SilentlyContinue |
        Sort-Object { [int]($_.Directory.Parent.Name -replace '\D', '') } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $pgDump) { throw "No encontré pg_dump. Córrelo antes: scripts\instalar_pg_dump.ps1" }

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
if ($Destino.StartsWith($env:APPDATA)) {
    Write-Host "Copia de vez en cuando la carpeta $Destino a una USB o a otra PC: si esta PC se descompone, los respaldos se van con ella."
}

# ── Retención: solo archivos sibim-*.dump de esta carpeta ────────────────────
Get-ChildItem $Destino -Filter "sibim-*.dump" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$DiasRetencion) } |
    ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "Borrado por antigüedad: $($_.Name)" }
