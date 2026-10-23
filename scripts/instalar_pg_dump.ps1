#Requires -Version 5.1
<#
.SYNOPSIS
    Deja pg_dump y pg_restore en tools\pgsql\bin, sin instalar PostgreSQL.

.DESCRIPTION
    Descarga los binarios oficiales de PostgreSQL para Windows (EnterpriseDB)
    y extrae solo pg_dump, pg_restore, psql y las librerias que usan (unos
    20 MB de los 340 del zip). scripts\respaldo_diario.ps1 los busca ahi.
    pg_dump respalda servidores de su version o anteriores: con la 18 sirve
    para el PostgreSQL 15/17 de Supabase.

.PARAMETER Zip
    Un zip ya descargado (postgresql-<version>-windows-x64-binaries.zip), en
    vez de descargarlo.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\instalar_pg_dump.ps1
#>
param(
    [string]$Version = "18.6",
    [string]$Zip
)

$ErrorActionPreference = "Stop"
$Root    = Split-Path -Parent $PSScriptRoot
$Destino = Join-Path $Root "tools\pgsql\bin"

if (-not $Zip) {
    $Zip = Join-Path $env:TEMP "postgresql-$Version-windows-x64-binaries.zip"
    $url = "https://get.enterprisedb.com/postgresql/postgresql-$Version-1-windows-x64-binaries.zip"
    Write-Host "Descargando $url (unos 340 MB)..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $url -OutFile $Zip -UseBasicParsing
}

# The programs and every DLL they may load; not the GUI (wx*), ICU, ecpg or XML ones.
$programas = @("pg_dump.exe", "pg_restore.exe", "psql.exe")
$fuera = '^(wx|icu|libecpg|libpgtypes|libxml|libxslt|testplug)'

Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$archivo = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Zip).Path)
try {
    $n = 0
    foreach ($e in $archivo.Entries) {
        if ($e.FullName -notmatch '^pgsql/bin/([^/]+)$') { continue }
        $nombre = $Matches[1]
        $sirve = ($programas -contains $nombre) -or ($nombre -like "*.dll" -and $nombre -notmatch $fuera)
        if (-not $sirve) { continue }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $Destino $nombre), $true)
        $n++
    }
} finally {
    $archivo.Dispose()
}

$version = & (Join-Path $Destino "pg_dump.exe") --version
if ($LASTEXITCODE -ne 0) { throw "pg_dump no arranco (falta alguna libreria)" }
Write-Host "Listo: $n archivos en $Destino ($version)"
