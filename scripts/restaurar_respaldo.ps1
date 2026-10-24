#Requires -Version 5.1
<#
.SYNOPSIS
    Revisa un respaldo de SIBIM (scripts\respaldo_diario.ps1), lo restaura en
    una base, o regresa a Supabase Storage las fotos que falten.

.DESCRIPTION
    Sin -Restaurar ni -Fotos solo revisa: cuántos bienes, resguardos, fotos...
    trae el respaldo y cuántas de sus fotos están en la carpeta de respaldos.
    No se conecta a nada.

    -Restaurar REEMPLAZA todo lo de la base (la de SIBIM en esta PC, o la de
    -Conexion) con el respaldo. Pide escribir RESTAURAR. Va en una sola
    transacción: si algo falla, la base queda exactamente como estaba. Al
    terminar compara, tabla por tabla, los registros de la base con los del
    respaldo. Necesita la cuenta dueña de las tablas (postgres en Supabase),
    no sibim_app ni sibim_areas.

    Antes de restaurar: cierra SIBIM en TODAS las PCs. Una PC que siga abierta
    puede reenviar después cambios que tenía guardados sin conexión.

    -Fotos sube a Storage las fotos y facturas del respaldo que ya no estén
    ahí, desde la carpeta "fotos" de los respaldos. Nunca reemplaza una que sí
    está. Pide escribir SUBIR.

.PARAMETER Respaldo
    El archivo .dump. Por defecto, el más reciente de -Carpeta.

.PARAMETER Carpeta
    Donde están los respaldos y su carpeta "fotos". Por defecto
    %APPDATA%\SIBIM\respaldos (o la USB donde se copiaron).

.PARAMETER Conexion
    Un .env con DB_URL, DB_USER, DB_PASSWORD (y SUPABASE_URL y
    SUPABASE_ANON_KEY para -Fotos) de la base de destino. Por defecto el de
    SIBIM en esta PC.

.EXAMPLE
    .\scripts\restaurar_respaldo.ps1
    .\scripts\restaurar_respaldo.ps1 -Restaurar
    .\scripts\restaurar_respaldo.ps1 -Restaurar -Respaldo E:\SIBIM-respaldos\sibim-20261024-1400.dump
    .\scripts\restaurar_respaldo.ps1 -Fotos -Carpeta E:\SIBIM-respaldos
#>
param(
    [string]$Respaldo,
    [string]$Carpeta = (Join-Path $env:APPDATA "SIBIM\respaldos"),
    [switch]$Restaurar,
    [switch]$Fotos,
    [string]$Conexion
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
. (Join-Path $PSScriptRoot "comun.ps1")

if (-not $Respaldo) {
    $Respaldo = Get-ChildItem $Carpeta -Filter "sibim-*.dump" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime | Select-Object -Last 1 -ExpandProperty FullName
    if (-not $Respaldo) { throw "No hay respaldos sibim-*.dump en $Carpeta" }
}
if (-not (Test-Path $Respaldo)) { throw "No existe $Respaldo" }
$Respaldo = (Resolve-Path $Respaldo).Path
$pgRestore = Buscar-Programa "pg_restore"
$carpetaFotos = Join-Path (Split-Path $Respaldo) "fotos"

# Registros de cada tabla en el respaldo, contando las filas de cada COPY.
function Contar-Filas([string]$respaldo) {
    $cuenta = [ordered]@{}
    $tabla = $null
    foreach ($linea in (& $pgRestore --data-only --file=- $respaldo)) {
        if ($tabla) {
            if ($linea -eq '\.') { $tabla = $null } else { $cuenta[$tabla]++ }
        } elseif ($linea -match '^COPY public\.(\w+) ') {
            $tabla = $Matches[1]
            $cuenta[$tabla] = 0
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "pg_restore no pudo leer $respaldo (código $LASTEXITCODE)" }
    return $cuenta
}

$nombres = [ordered]@{
    products = "Bienes"; resguardos = "Resguardos"; resguardo_items = "Partidas de resguardos"
    product_fotos = "Fotos"; areas = "Áreas"; categories = "Categorías"; users = "Usuarios"
    movements = "Movimientos"; prestamos = "Préstamos"; comodatos = "Comodatos"
    actas_entrega_recepcion = "Actas"; area_resguardos = "Resguardos firmados por área"
    audit_log = "Bitácora"
}

$filas = Contar-Filas $Respaldo
$archivo = Get-Item $Respaldo
Write-Host ("Respaldo: {0} ({1:N1} MB, del {2:dd/MM/yyyy HH:mm})" -f $archivo.Name, ($archivo.Length / 1MB), $archivo.LastWriteTime)
foreach ($t in $nombres.Keys) {
    if ($filas.Contains($t)) { Write-Host ("  {0,-30} {1,8:N0}" -f $nombres[$t], $filas[$t]) }
}
$otras = @($filas.Keys | Where-Object { -not $nombres.Contains($_) })
Write-Host ("  ...y {0} tablas más con {1:N0} registros" -f $otras.Count, (($otras | ForEach-Object { $filas[$_] } | Measure-Object -Sum).Sum))

$archivos = Archivos-De-Storage $Respaldo
$enCarpeta = @($archivos | Where-Object { Test-Path (Ruta-Local $carpetaFotos $_.Nombre) }).Count
Write-Host ("Fotos y facturas: {0:N0}; respaldadas en {1}: {2:N0}" -f $archivos.Count, $carpetaFotos, $enCarpeta)

if (-not $Restaurar -and -not $Fotos) {
    Write-Host ""
    Write-Host "Solo se revisó el respaldo. Para cargarlo en la base: -Restaurar; para regresar fotos a Storage: -Fotos."
    exit 0
}

$con = Leer-Conexion $Conexion

# ── Restaurar la base ────────────────────────────────────────────────────────
if ($Restaurar) {
    $psql = Buscar-Programa "psql"
    $env:PGPASSWORD = $con.Contrasena
    $env:PGSSLMODE  = $con.Ssl
    $lista = Join-Path $env:TEMP ("sibim-restaurar-{0}.list" -f [guid]::NewGuid())
    try {
        function Consulta([string]$sql) {
            $r = & $psql --host $con.Host --port $con.Puerto --username $con.Usuario --dbname $con.Base `
                --no-psqlrc --tuples-only --no-align --quiet --command $sql
            if ($LASTEXITCODE -ne 0) { throw "No se pudo consultar la base $($con.Host)/$($con.Base) (código $LASTEXITCODE)." }
            return $r
        }

        # Los roles que nombran las políticas del respaldo deben existir: si
        # falta uno, la restauración se detendría a la mitad (y se desharía).
        $esquema = & $pgRestore --schema-only --file=- $Respaldo
        $roles = @{}
        foreach ($m in [regex]::Matches(($esquema -join "`n"), '(?m)^CREATE POLICY .*? TO ((?:\w+)(?:, \w+)*)')) {
            foreach ($r in ($m.Groups[1].Value -split ', ')) { if ($r -ne 'public') { $roles[$r] = $true } }
        }
        if ($roles.Count -gt 0) {
            $existen = @(Consulta ("SELECT rolname FROM pg_roles WHERE rolname IN ('{0}')" -f (($roles.Keys | Sort-Object) -join "','")))
            $faltan = @($roles.Keys | Where-Object { $existen -notcontains $_ } | Sort-Object)
            if ($faltan.Count -gt 0) {
                Write-Host "A la base de destino le faltan roles que usa el respaldo: $($faltan -join ', ')."
                Write-Host "Créalos primero (pasa en una base nueva; Supabase ya los tiene):"
                $faltan | ForEach-Object { Write-Host "  CREATE ROLE $_ NOLOGIN;" }
                exit 1
            }
        }

        $hoy = Consulta "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = 'products'"
        $bienesHoy = if ($hoy -eq '1') { "tiene $(Consulta 'SELECT count(*) FROM public.products') bienes" } else { "no tiene la tabla de bienes" }
        Write-Host ""
        Write-Host "Se va a REEMPLAZAR todo lo de la base $($con.Host)/$($con.Base) (cuenta $($con.Usuario))."
        Write-Host "Hoy $bienesHoy; el respaldo trae $($filas['products'])."
        Write-Host "Cierra SIBIM en TODAS las PCs antes de seguir."
        if ((Read-Host "Escribe RESTAURAR para seguir") -cne "RESTAURAR") { Write-Host "No se hizo nada."; exit 1 }

        # Todo menos el esquema public: ya existe (en Supabase con sus permisos)
        # y crearlo o borrarlo detendría la restauración.
        & $pgRestore --list $Respaldo | Where-Object { $_ -notmatch ' SCHEMA - public | COMMENT - SCHEMA public ' } |
            Set-Content -Path $lista -Encoding ASCII
        & $pgRestore --host $con.Host --port $con.Puerto --username $con.Usuario --dbname $con.Base `
            --use-list=$lista --clean --if-exists --no-owner --no-privileges --single-transaction --exit-on-error $Respaldo
        if ($LASTEXITCODE -ne 0) {
            Write-Host "La restauración se detuvo (código $LASTEXITCODE) y se deshizo: la base quedó como estaba. La causa es el error de arriba."
            exit 1
        }

        # Comprobación: los registros de cada tabla, contra los del respaldo.
        $sql = ($filas.Keys | ForEach-Object { "SELECT '$_', count(*) FROM public.""$_""" }) -join " UNION ALL "
        $diferentes = 0
        foreach ($renglon in (Consulta $sql)) {
            $t, $n = $renglon -split '\|'
            if ([int64]$n -ne $filas[$t]) {
                $diferentes++
                Write-Warning "$t tiene $n registros; el respaldo trae $($filas[$t])."
            }
        }
        if ($diferentes -gt 0) { Write-Host "Restaurado, pero $diferentes tablas no cuadran (ver arriba)."; exit 1 }
        Write-Host "Restaurado: las $($filas.Count) tablas tienen los mismos registros que el respaldo."
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        Remove-Item $lista -ErrorAction SilentlyContinue
    }
}

# ── Regresar fotos a Storage ─────────────────────────────────────────────────
if ($Fotos) {
    if (-not $con.SupabaseUrl) { throw "El .env ($($con.Origen)) no tiene SUPABASE_URL." }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $publica = "$($con.SupabaseUrl)/storage/v1/object/public/sibim-fotos"
    Write-Host "Revisando cuáles faltan en Storage..."
    $faltan = New-Object System.Collections.Generic.List[object]
    foreach ($a in $archivos) {
        try {
            Invoke-WebRequest -Uri "$publica/$($a.Ruta)" -Method Head -UseBasicParsing -TimeoutSec 30 | Out-Null
        } catch {
            $codigo = Codigo-Http $_
            if ($codigo -eq 400 -or $codigo -eq 404) { $faltan.Add($a) }
            else { throw "No se pudo revisar $($a.Nombre) en Storage: $($_.Exception.Message)" }
        }
    }
    $sinCopia = @($faltan | Where-Object { -not (Test-Path (Ruta-Local $carpetaFotos $_.Nombre)) })
    Write-Host ("Faltan en Storage: {0:N0} de {1:N0}; de esas, {2:N0} no están en {3}." -f $faltan.Count, $archivos.Count, $sinCopia.Count, $carpetaFotos)
    $subir = @($faltan | Where-Object { Test-Path (Ruta-Local $carpetaFotos $_.Nombre) })
    if ($subir.Count -eq 0) { Write-Host "No hay fotos que subir."; exit 0 }
    if (-not $con.AnonKey) {
        throw "Para subirlas falta SUPABASE_ANON_KEY en $($con.Origen) (corre packaging\limpiar-env.ps1, o pasa con -Conexion un .env que la tenga)."
    }
    if ((Read-Host "Escribe SUBIR para subir $($subir.Count) a $($con.SupabaseUrl)") -cne "SUBIR") { Write-Host "No se hizo nada."; exit 1 }

    $encabezados = @{ apikey = $con.AnonKey; Authorization = "Bearer $($con.AnonKey)" }
    $subidas = 0; $fallidas = 0
    foreach ($a in $subir) {
        $local = Ruta-Local $carpetaFotos $a.Nombre
        $tipo = switch ([IO.Path]::GetExtension($local).ToLower()) {
            ".png" { "image/png" } ".pdf" { "application/pdf" } default { "image/jpeg" }
        }
        try {
            # POST sin x-upsert: nunca reemplaza lo que ya esté.
            Invoke-WebRequest -Uri "$($con.SupabaseUrl)/storage/v1/object/sibim-fotos/$($a.Ruta)" -Method Post `
                -Headers $encabezados -InFile $local -ContentType $tipo -UseBasicParsing -TimeoutSec 90 | Out-Null
            $subidas++
        } catch {
            if ((Codigo-Http $_) -eq 409) { continue }   # ya estaba
            $fallidas++
            if ($fallidas -le 3) { Write-Warning "No se pudo subir $($a.Nombre): $($_.Exception.Message)" }
        }
    }
    Write-Host "Fotos subidas: $subidas; no se pudieron subir: $fallidas."
    if ($fallidas -gt 0) { exit 1 }
}
exit 0
