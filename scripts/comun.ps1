#Requires -Version 5.1
# Lo que comparten respaldo_diario.ps1 y restaurar_respaldo.ps1. Se carga con
#   . (Join-Path $PSScriptRoot "comun.ps1")

$RaizSibim = Split-Path -Parent $PSScriptRoot

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

# La conexión de un .env: la de -Archivo, o la de SIBIM en esta PC
# (%APPDATA%\SIBIM\.env, y si no tiene DB_URL, el .env del proyecto).
function Leer-Conexion([string]$Archivo) {
    if ($Archivo) {
        if (-not (Test-Path $Archivo)) { throw "No existe $Archivo" }
        $cfg = Leer-Env $Archivo
        $origen = $Archivo
    } else {
        $origen = Join-Path $env:APPDATA "SIBIM\.env"
        $cfg = Leer-Env $origen
        if (-not $cfg.ContainsKey("DB_URL")) {
            $origen = Join-Path $RaizSibim ".env"
            $cfg = Leer-Env $origen
        }
    }
    if (-not $cfg.ContainsKey("DB_URL")) { throw "No encontré DB_URL en $origen." }
    if ($cfg["DB_URL"] -notmatch '^jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?]+)') {
        throw "DB_URL no tiene el formato jdbc:postgresql://host:puerto/base"
    }
    return @{
        Host        = $Matches[1]
        Puerto      = if ($Matches[2]) { $Matches[2] } else { "5432" }
        Base        = $Matches[3]
        Usuario     = $cfg["DB_USER"]
        Contrasena  = $cfg["DB_PASSWORD"]
        Ssl         = if ($cfg["DB_SSL_MODE"]) { $cfg["DB_SSL_MODE"] } else { "require" }
        SupabaseUrl = if ($cfg["SUPABASE_URL"]) { $cfg["SUPABASE_URL"].Trim().TrimEnd('/') } else { $null }
        AnonKey     = $cfg["SUPABASE_ANON_KEY"]
        Origen      = $origen
    }
}

# ── Programas de PostgreSQL ──────────────────────────────────────────────────
# tools\pgsql\bin (la copia portátil de scripts\instalar_pg_dump.ps1), el PATH
# o C:\Program Files\PostgreSQL, el más nuevo.
function Buscar-Programa([string]$nombre) {
    $portatil = Join-Path $RaizSibim "tools\pgsql\bin\$nombre.exe"
    if (Test-Path $portatil) { return $portatil }
    $enPath = (Get-Command $nombre -ErrorAction SilentlyContinue).Source
    if ($enPath) { return $enPath }
    $instalado = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\$nombre.exe" -ErrorAction SilentlyContinue |
        Sort-Object { [int]($_.Directory.Parent.Name -replace '\D', '') } -Descending |
        Select-Object -First 1 -ExpandProperty FullName
    if ($instalado) { return $instalado }
    throw "No encontré $nombre. Córrelo antes: scripts\instalar_pg_dump.ps1"
}

# ── Fotos y facturas ─────────────────────────────────────────────────────────
# La base guarda la dirección de cada foto y factura de un bien; el archivo
# vive en Supabase Storage (bucket sibim-fotos) y ningún respaldo de la base
# lo trae. Devuelve los archivos a los que apunta un respaldo, cada uno con su
# nombre en el bucket tal como va en la dirección (Ruta) y ya decodificado
# (Nombre). Las fichas PDF (fichas/<id>.pdf) no: se vuelven a generar del bien.
function Archivos-De-Storage([string]$respaldo) {
    $pgRestore = Buscar-Programa "pg_restore"
    $texto = & $pgRestore --data-only --table=products --table=product_fotos --file=- $respaldo
    if ($LASTEXITCODE -ne 0) { throw "pg_restore no pudo leer $respaldo (código $LASTEXITCODE)" }
    $vistos = @{}
    $archivos = New-Object System.Collections.Generic.List[object]
    foreach ($m in [regex]::Matches(($texto -join "`n"), '/storage/v1/object/public/sibim-fotos/([^\s\\]+)')) {
        $ruta = $m.Groups[1].Value
        $nombre = [Uri]::UnescapeDataString($ruta)
        if ($nombre -like 'fichas/*' -or $vistos.ContainsKey($nombre)) { continue }
        # Un nombre que saldría de la carpeta de respaldos no se toca.
        if ($nombre -match '\.\.|[:*?"<>|]' -or $nombre.StartsWith('/')) {
            Write-Warning "Nombre de archivo raro en la base, se omite: $nombre"
            continue
        }
        $vistos[$nombre] = $true
        $archivos.Add([pscustomobject]@{ Nombre = $nombre; Ruta = $ruta })
    }
    return ,$archivos
}

# Dónde queda el archivo de Storage $nombre dentro de la carpeta de fotos.
function Ruta-Local([string]$carpetaFotos, [string]$nombre) {
    return Join-Path $carpetaFotos ($nombre -replace '/', '\')
}

# El código HTTP de una excepción de Invoke-WebRequest (0 si no hubo respuesta).
function Codigo-Http($err) {
    $resp = $err.Exception.Response
    if ($resp -and $resp.StatusCode) { return [int]$resp.StatusCode }
    return 0
}
