#Requires -Version 5.1
<#
.SYNOPSIS
    Deja listo el instalador para que cada PC entre solo con usuario y contrasena.

.DESCRIPTION
    Se corre UNA vez, en la PC de administracion, antes de generar el instalador:

      1. Genera la contrasena del rol sibim_app (o conserva la que ya tenia
         packaging\sibim.env) y escribe packaging\sibim.env, que el instalador
         lleva dentro (packaging\build-installer.ps1). git lo ignora.
      2. Copia al portapapeles scripts\sql\rol_app_minimo.sql con esa
         contrasena y abre el SQL Editor de Supabase: pegar y "Run".
      3. Comprueba que sibim_app entra, que solo puede trabajar con datos y
         cuantas areas ya tienen su cuenta.
      4. Si se pide, genera el instalador.

    La contrasena nunca se muestra en pantalla ni se guarda en otro archivo.
    Con ella, una PC recien instalada se conecta sola: solo se escribe el
    usuario y la contrasena de la cuenta del area. Una PC que ya tenga su
    propio %APPDATA%\SIBIM\.env sigue usando ese (como la de administracion).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File packaging\preparar-conexion.ps1
#>
$ErrorActionPreference = "Stop"

$Root      = Split-Path -Parent $PSScriptRoot
$EnvFile   = "$PSScriptRoot\sibim.env"
$SqlFile   = "$Root\scripts\sql\rol_app_minimo.sql"
$Proyecto  = "zjrzrcfpvkkefsscvzyh"
$DbUrl     = "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:5432/postgres"
$DbUser    = "sibim_app.$Proyecto"
$SqlEditor = "https://supabase.com/dashboard/project/$Proyecto/sql/new"
$Utf8      = New-Object System.Text.UTF8Encoding($false)

function Leer-Env([string]$ruta) {
    $valores = @{}
    foreach ($linea in [IO.File]::ReadAllLines($ruta, $Utf8)) {
        if ($linea -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $valores[$Matches[1]] = $Matches[2].Trim() }
    }
    return $valores
}

Write-Host ""
Write-Host "  SIBIM - conexion para el instalador" -ForegroundColor Cyan
Write-Host "  ===================================" -ForegroundColor Cyan
Write-Host ""

# -- 1. Contrasena de sibim_app ------------------------------------------------
$anterior = if (Test-Path $EnvFile) { Leer-Env $EnvFile } else { @{} }
$Clave = $anterior["DB_PASSWORD"]
if ($Clave) {
    Write-Host "  packaging\sibim.env ya tiene una contrasena para sibim_app."
    $nueva = Read-Host "  Generar una NUEVA? Las PCs instaladas con la anterior dejarian de conectarse [s/N]"
    if ($nueva -in @("s", "S")) { $Clave = $null }
}
if (-not $Clave) {
    $alfabeto = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    $bytes = New-Object byte[] 40
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $Clave = -join ($bytes | ForEach-Object { $alfabeto[$_ % $alfabeto.Length] })
    Write-Host "  Contrasena nueva generada (40 caracteres, no se muestra)." -ForegroundColor Green
}

# -- Clave publica (anon) de Supabase, para fotos y la ficha del QR -------------
function Parece-Clave([string]$k) {
    return $k -match '^[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}$'
}

$Anon = $anterior["SUPABASE_ANON_KEY"]
if ($Anon -and -not (Parece-Clave $Anon)) {
    Write-Host "  La clave publica (anon) guardada no es valida; hay que pegarla de nuevo." -ForegroundColor Yellow
    $Anon = $null
} elseif ($Anon) {
    $otra = Read-Host "  Ya hay clave publica (anon) de Supabase. Cambiarla? [s/N]"
    if ($otra -in @("s", "S")) { $Anon = $null }
}
while (-not $Anon) {
    Write-Host ""
    Write-Host "  Pega la clave publica 'anon' de Supabase (para las fotos de los bienes)."
    Write-Host "  Supabase -> Project Settings -> API Keys -> Legacy -> anon public (empieza con eyJ)" -ForegroundColor DarkGray
    Write-Host "  Pegala con CLIC DERECHO. Enter vacio = sin fotos en linea (se quedan en cada PC)." -ForegroundColor DarkGray
    # The anon key is public (it ships in every installer): shown while pasted,
    # so a paste that didn't take is seen at once.
    $Anon = (Read-Host "  Clave anon") -replace '[\x00-\x20\x7F]', ''
    if (-not $Anon) { break }
    if (-not (Parece-Clave $Anon)) {
        Write-Host "  Eso no parece la clave anon (debe ser larga, con dos puntos, y empezar con eyJ). Otra vez." -ForegroundColor Red
        $Anon = $null
    }
}
if ($Anon) {
    # Una clave secreta (sb_secret_... o un JWT con role service_role) da
    # control total del proyecto a quien abra el instalador.
    $secreta = $Anon.StartsWith("sb_secret_")
    $partes = $Anon.Split(".")
    if (-not $secreta -and $partes.Length -eq 3) {
        $b64 = $partes[1].Replace("-", "+").Replace("_", "/")
        $b64 = $b64.PadRight($b64.Length + (4 - $b64.Length % 4) % 4, "=")
        $payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b64))
        $secreta = $payload -match '"role"\s*:\s*"service_role"'
        if ($payload -match '"ref"\s*:\s*"([a-z0-9]+)"' -and $Matches[1] -ne $Proyecto) {
            throw "Esa clave es de otro proyecto de Supabase ($($Matches[1])), no de $Proyecto."
        }
    }
    if ($secreta) { throw "Esa es una clave SECRETA (service_role). Usa la clave publica 'anon'. No se escribio nada." }
}

$lineaAnon = if ($Anon) { "SUPABASE_ANON_KEY=$Anon" } else { "# SUPABASE_ANON_KEY=  (falta: las fotos se quedan en cada PC)" }
$contenido = @"
# SIBIM Desktop - conexion que lleva el instalador (packaging\build-installer.ps1).
# Generado por packaging\preparar-conexion.ps1. Rol de minimo privilegio
# sibim_app (scripts\sql\rol_app_minimo.sql). NO subir a git ni compartir.
DB_URL=$DbUrl
DB_USER=$DbUser
DB_PASSWORD=$Clave
DB_SSL_MODE=require
# Las PCs no cambian el esquema; la PC de administracion aplica las migraciones.
DB_MIGRATE=false

# Supabase Storage (fotos de bienes y ficha del QR)
SUPABASE_URL=https://$Proyecto.supabase.co
$lineaAnon
"@
[IO.File]::WriteAllText($EnvFile, $contenido, $Utf8)
Write-Host "  Escrito packaging\sibim.env" -ForegroundColor Green

# -- 2. Crear o actualizar el rol en Supabase ----------------------------------
$sql = [IO.File]::ReadAllText($SqlFile, $Utf8)
$marca = "clave TEXT := 'CAMBIA-ESTA-CONTRASENA';"
if (-not $sql.Contains($marca)) { throw "No encontre la linea de la contrasena en scripts\sql\rol_app_minimo.sql" }
Set-Clipboard -Value $sql.Replace($marca, "clave TEXT := '$Clave';")
Write-Host ""
Write-Host "  El SQL del rol sibim_app ya esta en el portapapeles." -ForegroundColor Yellow
Write-Host "  Se abre el SQL Editor de Supabase: pega (Ctrl+V) y da clic en Run."
Write-Host "  Debe terminar con 'Success. No rows returned'."
Start-Process $SqlEditor
Read-Host "  Cuando lo hayas ejecutado, presiona Enter"
Set-Clipboard -Value " "   # que la contrasena no se quede en el portapapeles

# -- 3. Comprobar la conexion como sibim_app -----------------------------------
[xml]$pom = Get-Content "$Root\pom.xml" -Encoding UTF8
$Jar = "$Root\target\sibim-desktop-$($pom.project.version).jar"
$JShell = @("$env:JAVA_HOME\bin\jshell.exe", "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\jshell.exe") |
    Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $JShell) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { $c = Join-Path (Split-Path $java.Source) "jshell.exe"; if (Test-Path $c) { $JShell = $c } }
}
if (-not (Test-Path $Jar) -or -not $JShell) {
    Write-Host "  [AVISO] No pude comprobar la conexion (falta $Jar o jshell). Corre 'mvn package' y repite." -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "  Comprobando la conexion como sibim_app..."
    $prueba = Join-Path $env:TEMP "sibim-prueba-conexion.jsh"
    @'
var p = new java.util.Properties();
p.setProperty("user", System.getenv("SIBIM_PRUEBA_USER"));
p.setProperty("password", System.getenv("SIBIM_PRUEBA_PASS"));
p.setProperty("sslmode", "require");
p.setProperty("connectTimeout", "15");
try (var c = java.sql.DriverManager.getConnection(System.getenv("SIBIM_PRUEBA_URL"), p); var st = c.createStatement()) {
    java.util.function.Function<String, String> q = sql -> {
        try (var rs = st.executeQuery(sql)) { rs.next(); return rs.getString(1); }
        catch (Exception e) { return "ERROR " + e.getMessage(); }
    };
    System.out.println("    Entra como:                     " + q.apply("SELECT current_user"));
    System.out.println("    Bienes que ve:                  " + q.apply("SELECT count(*) FROM products"));
    System.out.println("    Puede registrar bienes:         " + q.apply("SELECT has_table_privilege(current_user, 'products', 'INSERT')"));
    System.out.println("    Puede borrar tablas/esquema:    " + q.apply("SELECT has_schema_privilege(current_user, 'public', 'CREATE')"));
    System.out.println("    Puede publicar actualizaciones: " + q.apply("SELECT has_table_privilege(current_user, 'actualizaciones', 'INSERT')"));
    System.out.println("    Cuentas de area creadas:        " + q.apply("SELECT count(*) FROM users WHERE cargo LIKE 'Cuenta compartida del %rea' AND activo"));
    System.out.println("    Areas sin cuenta:               " + q.apply("SELECT count(*) FROM areas a WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.area = a.nombre AND u.activo)"));
    System.out.println("    OK");
} catch (Exception e) {
    System.out.println("    NO ENTRA: " + e.getMessage());
}
/exit
'@ | Set-Content -Path $prueba -Encoding ASCII
    try {
        $env:SIBIM_PRUEBA_URL = $DbUrl; $env:SIBIM_PRUEBA_USER = $DbUser; $env:SIBIM_PRUEBA_PASS = $Clave
        & $JShell --class-path $Jar $prueba
    } finally {
        Remove-Item Env:SIBIM_PRUEBA_PASS, Env:SIBIM_PRUEBA_USER, Env:SIBIM_PRUEBA_URL -ErrorAction SilentlyContinue
        Remove-Item $prueba -ErrorAction SilentlyContinue
    }
    Write-Host ""
    Write-Host "  Esperado: entra como sibim_app, puede registrar bienes = t, borrar tablas = f, publicar = f." -ForegroundColor DarkGray
    Write-Host "  Si 'Areas sin cuenta' no es 0: entra como administrador -> Configuracion -> Usuarios ->" -ForegroundColor DarkGray
    Write-Host "  'Cuentas por area' (crea las que falten y genera el PDF con las contrasenas)." -ForegroundColor DarkGray
}

# -- 4. Instalador -------------------------------------------------------------
Write-Host ""
$generar = Read-Host "  Generar el instalador ahora? [S/n]"
if ($generar -notin @("n", "N")) {
    & "$PSScriptRoot\build-installer.ps1" -Type exe -SkipTests
}
