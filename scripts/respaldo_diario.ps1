#Requires -Version 5.1
<#
.SYNOPSIS
    Respaldo completo de SIBIM: la base con pg_dump (con retención de N días)
    y las fotos y facturas de los bienes.

.DESCRIPTION
    Lee la conexión del mismo .env que usa SIBIM (%APPDATA%\SIBIM\.env, o el
    .env de la carpeta del proyecto) y guarda un respaldo en formato custom de
    pg_dump (se restaura con scripts\restaurar_respaldo.ps1). Borra solo los
    respaldos de esa misma carpeta con más de -DiasRetencion días.

    Las fotos y facturas no están en la base (la base guarda su dirección):
    después del respaldo baja de Supabase Storage las que aún no estén en la
    subcarpeta "fotos". Como en Storage una foto nunca se reemplaza, cada día
    solo baja las nuevas, y ninguna se borra por antigüedad.

    Los instaladores publicados con "Publicar actualización" (unos 110 MB cada
    uno) no se respaldan: se rehacen desde el código, y harían pesar cada
    respaldo más que todo el inventario.

    El respaldo incluye los hashes de contraseña de los usuarios y todo el
    inventario: guárdalo en una carpeta a la que solo tenga acceso quien
    administra el sistema (y de preferencia en un disco cifrado con BitLocker),
    y copia periódicamente la carpeta fuera de esta PC (-CopiaEn).

    Requiere pg_dump de la misma versión mayor que el servidor o más nueva
    (Supabase usa PostgreSQL 15/17). Lo busca primero en tools\pgsql\bin (una
    copia portátil, que deja scripts\instalar_pg_dump.ps1), luego en el PATH y
    en C:\Program Files\PostgreSQL.

.PARAMETER Destino
    Carpeta de los respaldos. Por defecto %APPDATA%\SIBIM\respaldos.

.PARAMETER DiasRetencion
    Respaldos más viejos que esto se borran. Por defecto 30.

.PARAMETER SinFotos
    Solo la base, sin bajar fotos.

.PARAMETER CopiaEn
    Otra carpeta (una USB, una carpeta de red) a la que se copia lo nuevo de
    -Destino al terminar: los respaldos y las fotos. Si ese día no está
    conectada, se avisa y no pasa nada más. Ahí también se borran los
    respaldos de más de -DiasRetencion días; las fotos, nunca.

.PARAMETER UnoPorDia
    Si ya hay un respaldo de hoy en -Destino, no hace nada. Lo usa la tarea
    programada, que se dispara más de una vez al día.

.PARAMETER Registrar
    Registra una tarea programada de Windows que ejecuta este script todos los
    días a la -Hora indicada (por defecto 11:00) y también 10 minutos después
    de iniciar sesión, con -UnoPorDia, la cuenta actual y los mismos -Destino,
    -DiasRetencion, -SinFotos y -CopiaEn, y hace un primer respaldo en ese
    momento para comprobar que funciona. El disparo al iniciar sesión es el que
    cuenta en una PC que se apaga antes de la hora: Windows no siempre recupera
    la ejecución perdida al volver a encenderla (con 14:00 nunca corrió).

.EXAMPLE
    .\scripts\respaldo_diario.ps1
    .\scripts\respaldo_diario.ps1 -Registrar -Hora 11:00 -CopiaEn E:\SIBIM-respaldos
    .\scripts\restaurar_respaldo.ps1            # revisa el respaldo más reciente
#>
param(
    [string]$Destino = (Join-Path $env:APPDATA "SIBIM\respaldos"),
    [int]$DiasRetencion = 30,
    [switch]$SinFotos,
    [string]$CopiaEn,
    [switch]$UnoPorDia,
    [switch]$Registrar,
    [string]$Hora = "11:00"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"   # la barra de Invoke-WebRequest lo hace 10 veces más lento
. (Join-Path $PSScriptRoot "comun.ps1")

if ($Registrar) {
    $argumentos = "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Destino `"$Destino`" -DiasRetencion $DiasRetencion -UnoPorDia"
    if ($SinFotos) { $argumentos += " -SinFotos" }
    if ($CopiaEn)  { $argumentos += " -CopiaEn `"$CopiaEn`"" }
    $accion = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argumentos
    $alIniciarSesion = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
    $alIniciarSesion.Delay = "PT10M"
    $disparadores = @((New-ScheduledTaskTrigger -Daily -At $Hora), $alIniciarSesion)
    $ajustes = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName "SIBIM - respaldo diario" -Action $accion -Trigger $disparadores `
        -Settings $ajustes -Description "pg_dump de la base de SIBIM y sus fotos (scripts\respaldo_diario.ps1)" -Force | Out-Null
    Write-Host "Tarea 'SIBIM - respaldo diario' registrada: todos los días a las $Hora y al iniciar sesión (una vez al día)."
    Write-Host "Primer respaldo, para comprobar que funciona..."
} elseif ($UnoPorDia -and (Test-Path $Destino) -and
          (Get-ChildItem $Destino -Filter ("sibim-{0}-*.dump" -f (Get-Date -Format "yyyyMMdd")))) {
    Write-Host "Ya hay un respaldo de hoy en $Destino."
    exit 0
}

$con = Leer-Conexion
$pgDump = Buscar-Programa "pg_dump"

New-Item -ItemType Directory -Force -Path $Destino | Out-Null
# Con segundos: dos respaldos en el mismo minuto (antes y después de una corrección)
# se pisaban, y se perdía el de antes.
$archivo = Join-Path $Destino ("sibim-{0}.dump" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$temporal = "$archivo.parcial"

# La contraseña solo vive en el entorno de este proceso.
$env:PGPASSWORD = $con.Contrasena
$env:PGSSLMODE  = $con.Ssl
try {
    & $pgDump --host $con.Host --port $con.Puerto --username $con.Usuario --dbname $con.Base `
        --format custom --no-owner --no-privileges --schema public `
        --exclude-table-data=actualizacion_partes --exclude-table-data=actualizaciones --file $temporal
    if ($LASTEXITCODE -ne 0) { throw "pg_dump terminó con código $LASTEXITCODE" }
    Move-Item $temporal $archivo -Force
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    if (Test-Path $temporal) { Remove-Item $temporal -Force }
}

$tamano = [math]::Round((Get-Item $archivo).Length / 1MB, 2)
Write-Host "Respaldo guardado: $archivo ($tamano MB)"
if ($Destino.StartsWith($env:APPDATA) -and -not $CopiaEn) {
    Write-Host "Copia de vez en cuando la carpeta $Destino a una USB o a otra PC (o usa -CopiaEn): si esta PC se descompone, los respaldos se van con ella."
}

# ── Retención: solo archivos sibim-*.dump de esta carpeta ────────────────────
function Borrar-Viejos([string]$carpeta) {
    Get-ChildItem $carpeta -Filter "sibim-*.dump" |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$DiasRetencion) } |
        ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "Borrado por antigüedad: $($_.Name)" }
}
Borrar-Viejos $Destino

# ── Fotos y facturas ─────────────────────────────────────────────────────────
$fotosFallidas = 0
if (-not $SinFotos) {
    $carpetaFotos = Join-Path $Destino "fotos"
    $archivos = Archivos-De-Storage $archivo
    $faltan = @($archivos | Where-Object {
        $local = Ruta-Local $carpetaFotos $_.Nombre
        -not ((Test-Path $local) -and (Get-Item $local).Length -gt 0)
    })
    if ($faltan.Count -gt 0 -and -not $con.SupabaseUrl) {
        Write-Warning "Faltan $($faltan.Count) fotos por respaldar, pero el .env no tiene SUPABASE_URL."
        $fotosFallidas = $faltan.Count
    } elseif ($faltan.Count -gt 0) {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Write-Host "Bajando $($faltan.Count) fotos nuevas de $($archivos.Count)..."
        $nuevas = 0
        $noEstan = New-Object System.Collections.Generic.List[string]
        foreach ($a in $faltan) {
            $local = Ruta-Local $carpetaFotos $a.Nombre
            New-Item -ItemType Directory -Force -Path (Split-Path $local) | Out-Null
            try {
                Invoke-WebRequest -Uri "$($con.SupabaseUrl)/storage/v1/object/public/sibim-fotos/$($a.Ruta)" `
                    -OutFile "$local.parcial" -UseBasicParsing -TimeoutSec 90
                Move-Item "$local.parcial" $local -Force
                $nuevas++
            } catch {
                Remove-Item "$local.parcial" -ErrorAction SilentlyContinue
                $codigo = Codigo-Http $_
                # 400/404: Storage no lo tiene (la subida nunca llegó): no hay qué respaldar.
                if ($codigo -eq 400 -or $codigo -eq 404) { $noEstan.Add($a.Nombre) }
                else {
                    $fotosFallidas++
                    if ($fotosFallidas -le 3) { Write-Warning "No se pudo bajar $($a.Nombre): $($_.Exception.Message)" }
                }
            }
        }
        Write-Host "Fotos: $nuevas nuevas; $($archivos.Count - $faltan.Count) ya estaban respaldadas."
        $listaNoEstan = Join-Path $carpetaFotos "no-estan-en-storage.txt"
        if ($noEstan.Count -gt 0) {
            Set-Content -Path $listaNoEstan -Value $noEstan -Encoding UTF8
            Write-Warning "$($noEstan.Count) fotos de la base no están en Storage (lista en $listaNoEstan)."
        }
        if ($fotosFallidas -gt 0) { Write-Warning "$fotosFallidas fotos no se pudieron bajar; se reintentan en el próximo respaldo." }
    } else {
        Write-Host "Fotos: las $($archivos.Count) ya estaban respaldadas."
    }
}

# ── Copia fuera de esta PC ───────────────────────────────────────────────────
$copiaFallida = $false
if ($CopiaEn) {
    # E:\ de una USB o \\servidor\carpeta de una de red.
    if (-not (Test-Path ([IO.Path]::GetPathRoot($CopiaEn)))) {
        Write-Warning "No está conectada la unidad de ${CopiaEn}: hoy no se copió el respaldo fuera de esta PC."
        $copiaFallida = $true
    } else {
        New-Item -ItemType Directory -Force -Path $CopiaEn | Out-Null
        # /E sin /MIR: nunca borra en la copia lo que falte aquí (si esta carpeta
        # se perdiera, la copia no se vacía con ella). /XO: solo lo nuevo.
        robocopy $Destino $CopiaEn /E /XO /XF *.parcial /R:2 /W:5 /NP /NFL /NDL /NJH /NJS | Out-Null
        if ($LASTEXITCODE -ge 8) {
            Write-Warning "robocopy no pudo copiar a $CopiaEn (código $LASTEXITCODE)."
            $copiaFallida = $true
        } else {
            Borrar-Viejos $CopiaEn
            Write-Host "Copiado a $CopiaEn"
        }
    }
}

# Código 1 si algo quedó sin respaldar, para que el Programador de tareas lo
# muestre ("Último resultado"); la base ya quedó guardada de todos modos.
if ($fotosFallidas -gt 0 -or $copiaFallida) { exit 1 }
exit 0
