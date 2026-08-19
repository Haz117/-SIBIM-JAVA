#Requires -Version 5.1
<#
.SYNOPSIS
    Builds a self-contained Windows app-image (or installer) for SIBIM Desktop.

.PARAMETER Type
    jpackage output type: app-image (default), exe, or msi.
    exe/msi require WiX Toolset 3.x to be installed and on PATH.

.PARAMETER Version
    App version string (default: 1.0.0).

.EXAMPLE
    .\build-installer.ps1                    # portable app folder
    .\build-installer.ps1 -Type exe          # .exe installer (needs WiX)

.NOTES
    Requirements:
    - JDK 21 with jpackage (path auto-detected below)
    - Maven wrapper in ..\maven-dist\
    - WiX Toolset 3.x only if using -Type exe or msi
#>
param(
    [ValidateSet("app-image","exe","msi")]
    [string]$Type    = "app-image",
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"

$Root     = Split-Path -Parent $PSScriptRoot
$Maven    = "$Root\maven-dist\apache-maven-3.9.9\bin\mvn.cmd"
$Target   = "$Root\target"
$Out      = "$PSScriptRoot\dist"
$Icon     = "$Root\src\main\resources\img\icon.ico"

# Locate jpackage
$JdkCandidates = @(
    "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot",
    $env:JAVA_HOME,
    (& where.exe java 2>$null | Select-Object -First 1 | ForEach-Object { Split-Path (Split-Path $_) })
) | Where-Object { $_ -and (Test-Path "$_\bin\jpackage.exe") }

if (-not $JdkCandidates) {
    throw "jpackage.exe not found. Make sure JDK 14+ is installed."
}
$Jpackage = "$($JdkCandidates[0])\bin\jpackage.exe"
Write-Host "Using jpackage: $Jpackage"

# 1. Build fat JAR
Write-Host "`n[1/2] Building fat JAR (mvn clean package)..."
& $Maven -f "$Root\pom.xml" clean package -DskipTests -q
if (!$?) { throw "Maven build failed" }

# 2. Find the shaded JAR
$Jar = Get-ChildItem $Target -Filter "sibim-desktop-*.jar" |
       Where-Object { $_.Name -notlike "*original*" } |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1

if (!$Jar) { throw "Fat JAR not found in $Target after build." }
Write-Host "Fat JAR: $($Jar.Name)  ($([math]::Round($Jar.Length/1MB, 1)) MB)"

New-Item -ItemType Directory -Force -Path $Out | Out-Null

# 3. Run jpackage
Write-Host "`n[2/2] Packaging ($Type) -> $Out"

$jargs = @(
    "--type",        $Type,
    "--name",        "SIBIM Desktop",
    "--app-version", $Version,
    "--vendor",      "Municipio",
    "--description", "Sistema Integral de Bienes Municipales",
    "--input",       $Target,
    "--main-jar",    $Jar.Name,
    "--main-class",  "com.sibim.Main",
    "--dest",        $Out,
    "--java-options", "-Xmx512m",
    "--java-options", "-Dfile.encoding=UTF-8"
)

if (Test-Path $Icon) { $jargs += "--icon", $Icon }

if ($Type -ne "app-image") {
    $jargs += "--win-menu", "--win-shortcut", "--win-dir-chooser"
}

& $Jpackage @jargs
if (!$?) { throw "jpackage failed. See output above for details." }

Write-Host "`nDone. Output: $Out"
if ($Type -eq "app-image") {
    Write-Host "To distribute: zip the 'SIBIM Desktop' folder inside $Out"
    Write-Host "For an installer: run  .\build-installer.ps1 -Type exe"
}
