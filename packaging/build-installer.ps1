#Requires -Version 5.1
<#
.SYNOPSIS
    Builds a self-contained Windows app-image (or installer) for SIBIM Desktop.
    Works both locally and in GitHub Actions CI/CD.

.PARAMETER Type
    jpackage output type: app-image (default), exe, or msi.
    exe/msi require WiX Toolset 3.x on PATH.

.PARAMETER Version
    App version string. Defaults to the version in pom.xml.
    In CI the release workflow sets this from the git tag (e.g. 1.2.3).

.PARAMETER ZipOutput
    When set, zips the app-image folder after creation (useful for CD artifact upload).

.EXAMPLE
    .\build-installer.ps1                        # portable app-image, local
    .\build-installer.ps1 -Type exe             # .exe installer (needs WiX 3.x)
    .\build-installer.ps1 -ZipOutput            # app-image + zip for distribution
    .\build-installer.ps1 -Version 1.2.0 -ZipOutput  # version override (used by CI)
#>
param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type      = "app-image",
    [string]$Version   = "",
    [switch]$ZipOutput
)

$ErrorActionPreference = "Stop"

$Root   = Split-Path -Parent $PSScriptRoot
$Target = "$Root\target"
$Out    = "$PSScriptRoot\dist"
$Icon   = "$Root\src\main\resources\img\icon.ico"

# ── Locate Maven ──────────────────────────────────────────────────────────────
# Prefer the bundled copy (local dev); fall back to mvn on PATH (CI / systems
# with Maven installed globally).
$LocalMvn = "$Root\maven-dist\apache-maven-3.9.9\bin\mvn.cmd"
if (Test-Path $LocalMvn) {
    $Maven = $LocalMvn
} else {
    $mvnOnPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnOnPath) { $Maven = $mvnOnPath.Source }
    else { throw "Maven not found. Install Maven or make sure maven-dist/ exists." }
}
Write-Host "Using Maven: $Maven"

# ── Locate jpackage ───────────────────────────────────────────────────────────
# Priority: JAVA_HOME env var (set by actions/setup-java in CI) → known local JDK
# paths → java on PATH.
$JpackagePath = $null

if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) {
    $JpackagePath = "$env:JAVA_HOME\bin\jpackage.exe"
} else {
    $Candidates = @(
        "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot",
        "C:\Program Files\Eclipse Adoptium\jdk-21*",
        "C:\Program Files\Java\jdk-21*"
    ) | ForEach-Object { Resolve-Path $_ -ErrorAction SilentlyContinue } |
        Where-Object { Test-Path "$_\bin\jpackage.exe" } |
        Select-Object -First 1

    if ($Candidates) {
        $JpackagePath = "$Candidates\bin\jpackage.exe"
    } else {
        $javaExe = Get-Command java -ErrorAction SilentlyContinue
        if ($javaExe) {
            $jpackageCandidate = Join-Path (Split-Path (Split-Path $javaExe.Source)) "jpackage.exe"
            if (Test-Path $jpackageCandidate) { $JpackagePath = $jpackageCandidate }
        }
    }
}

if (-not $JpackagePath) { throw "jpackage.exe not found. Install JDK 21+." }
Write-Host "Using jpackage: $JpackagePath"

# ── Resolve version ───────────────────────────────────────────────────────────
if (-not $Version) {
    # Read from pom.xml if not supplied
    [xml]$pom = Get-Content "$Root\pom.xml" -Encoding UTF8
    $Version = $pom.project.version
    Write-Host "Version from pom.xml: $Version"
} else {
    Write-Host "Version (override): $Version"
}

# ── Build fat JAR ─────────────────────────────────────────────────────────────
Write-Host "`n[1/3] Building fat JAR..."
& $Maven -f "$Root\pom.xml" clean package -DskipTests -q
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)" }

$Jar = Get-ChildItem $Target -Filter "sibim-desktop-*.jar" |
       Where-Object { $_.Name -notlike "*original*" } |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1

if (-not $Jar) { throw "Fat JAR not found in $Target" }
Write-Host "Fat JAR: $($Jar.Name)  ($([math]::Round($Jar.Length/1MB, 1)) MB)"

# ── Run jpackage ──────────────────────────────────────────────────────────────
Write-Host "`n[2/3] Creating $Type → $Out"
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$jargs = @(
    "--type",        $Type,
    "--name",        "SIBIM Desktop",
    "--app-version", $Version,
    "--vendor",      "H. Ayuntamiento Municipal",
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

& $JpackagePath @jargs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed (exit $LASTEXITCODE)" }

# ── Optional zip ──────────────────────────────────────────────────────────────
if ($ZipOutput -or $Type -eq "app-image") {
    Write-Host "`n[3/3] Zipping app-image..."
    $AppFolder = "$Out\SIBIM Desktop"
    $ZipFile   = "$Out\SIBIM-Desktop-$Version-win64.zip"
    if (Test-Path $AppFolder) {
        Compress-Archive -Path $AppFolder -DestinationPath $ZipFile -Force -CompressionLevel Optimal
        $sizeMb = [math]::Round((Get-Item $ZipFile).Length / 1MB, 1)
        Write-Host "Archive: $ZipFile  ($sizeMb MB)"
    }
} else {
    Write-Host "`n[3/3] Skipping zip (use -ZipOutput to create a distributable archive)"
}

Write-Host "`nDone. Output: $Out"
Write-Host "  To distribute: share the .zip or run .\build-installer.ps1 -Type exe (needs WiX 3.x)"
