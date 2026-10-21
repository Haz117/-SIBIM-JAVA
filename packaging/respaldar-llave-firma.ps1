#Requires -Version 5.1
<#
.SYNOPSIS
    Respalda (o restaura) la llave que firma las actualizaciones de SIBIM.

.DESCRIPTION
    La llave privada vive solo en esta PC (%APPDATA%\SIBIM\firma-actualizaciones.key),
    sellada para esta cuenta de Windows: no se puede copiar tal cual. Si esta PC se
    descompone sin respaldo, ninguna PC vuelve a recibir actualizaciones solas y la
    siguiente version hay que instalarla a mano en cada una.

    -Respaldar escribe la llave cifrada con una contrasena que eliges (minimo 12
    caracteres; se pide dos veces y no se muestra). Guarda el archivo en una USB y
    la contrasena en otro lugar: el archivo solo no sirve sin ella.

    -Restaurar la vuelve a sellar en la PC donde se corra (por ejemplo, la nueva PC
    de administracion), despues de comprobar que es la llave de SIBIM.

    Compila SIBIM antes de correr (tarda un minuto).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File packaging\respaldar-llave-firma.ps1 -Respaldar E:\sibim-llave-firma.respaldo
    powershell -ExecutionPolicy Bypass -File packaging\respaldar-llave-firma.ps1 -Restaurar E:\sibim-llave-firma.respaldo
#>
param(
    [string]$Respaldar,
    [string]$Restaurar,
    [switch]$Reemplazar
)
$ErrorActionPreference = "Stop"

if (-not $Respaldar -and -not $Restaurar) { throw "Indica -Respaldar <archivo> o -Restaurar <archivo>." }
if ($Respaldar -and $Restaurar) { throw "Usa solo uno: -Respaldar o -Restaurar." }

$Root   = Split-Path $PSScriptRoot -Parent
$Target = Join-Path $Root "target"
$Maven  = Join-Path $Root "maven-dist\apache-maven-3.9.9\bin\mvn.cmd"

function Buscar-Jar {
    Get-ChildItem $Target -Filter "sibim-desktop-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*original*" -and $_.Name -notlike "*shaded*" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

# Siempre con el codigo actual: un JAR viejo en target\ puede no conocer estos comandos.
Write-Host "Compilando SIBIM..."
& $Maven -f "$Root\pom.xml" --no-transfer-progress package -DskipTests -q
if ($LASTEXITCODE -ne 0) { throw "No se pudo compilar (exit $LASTEXITCODE)" }
$Jar = Buscar-Jar
if (-not $Jar) { throw "No se encontro el JAR en $Target" }

$java = (Get-Command java -ErrorAction SilentlyContinue).Source
if (-not $java) { throw "No se encontro java. Instala Java 21." }

if ($Respaldar) {
    & $java -cp $Jar.FullName com.sibim.service.FirmaActualizaciones respaldar $Respaldar
} else {
    $argumentos = @("restaurar", $Restaurar)
    if ($Reemplazar) { $argumentos += "--reemplazar" }
    & $java -cp $Jar.FullName com.sibim.service.FirmaActualizaciones @argumentos
}
exit $LASTEXITCODE
