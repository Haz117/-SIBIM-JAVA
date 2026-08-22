#Requires -Version 5.1
<#
.SYNOPSIS
    Builds a self-contained Windows app-image (or installer) for SIBIM Desktop.
    Works both locally and in GitHub Actions CI/CD.

.PARAMETER Type
    jpackage output type: app-image (default), exe, or msi.
    exe/msi require WiX Toolset 3.x — install with: choco install wixtoolset

.PARAMETER Version
    App version string. Defaults to the version in pom.xml.
    In CI the release workflow sets this from the git tag (e.g. 1.2.3).

.PARAMETER ZipOutput
    When set, zips the app-image folder after creation (useful for CD artifact upload).

.PARAMETER SkipTests
    Skip running tests before packaging (default: false).

.EXAMPLE
    .\build-installer.ps1                              # portable app-image, local
    .\build-installer.ps1 -Type exe                   # .exe installer (needs WiX 3.x)
    .\build-installer.ps1 -Type exe -SkipTests        # skip tests for quick iteration
    .\build-installer.ps1 -ZipOutput                  # app-image + zip for distribution
    .\build-installer.ps1 -Version 1.2.0 -ZipOutput   # version override (used by CI)
#>
param(
    [ValidateSet("app-image", "exe", "msi")]
    [string]$Type      = "app-image",
    [string]$Version   = "",
    [switch]$ZipOutput,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

$Root   = Split-Path -Parent $PSScriptRoot
$Target = "$Root\target"
$Out    = "$PSScriptRoot\dist"
$Icon   = "$Root\src\main\resources\img\icon.ico"

# ── Locate Maven ──────────────────────────────────────────────────────────────
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
            $candidate = Join-Path (Split-Path (Split-Path $javaExe.Source)) "jpackage.exe"
            if (Test-Path $candidate) { $JpackagePath = $candidate }
        }
    }
}

if (-not $JpackagePath) { throw "jpackage.exe not found. Install JDK 21+." }
Write-Host "Using jpackage: $JpackagePath"

# ── Verify WiX when needed ────────────────────────────────────────────────────
if ($Type -ne "app-image") {
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    if (-not $candle) {
        Write-Host ""
        Write-Host "[ERROR] WiX Toolset 3.x not found on PATH." -ForegroundColor Red
        Write-Host "        jpackage --type $Type requires WiX 3.x (candle.exe / light.exe)." -ForegroundColor Red
        Write-Host ""
        Write-Host "  Install options:" -ForegroundColor Yellow
        Write-Host "    1. choco install wixtoolset         (recommended if you have Chocolatey)" -ForegroundColor Yellow
        Write-Host "    2. Download WiX 3.14 from https://github.com/wixtoolset/wix3/releases" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  After installing, open a new terminal so PATH is refreshed, then re-run." -ForegroundColor Yellow
        Write-Host ""

        $installNow = Read-Host "Install WiX now via Chocolatey? [y/N]"
        if ($installNow -eq 'y' -or $installNow -eq 'Y') {
            if (-not (Get-Command choco -ErrorAction SilentlyContinue)) {
                throw "Chocolatey not found. Install it from https://chocolatey.org/install then re-run."
            }
            Write-Host "Installing WiX Toolset via Chocolatey..."
            choco install wixtoolset --yes --no-progress
            if ($LASTEXITCODE -ne 0) { throw "WiX installation failed." }
            # Refresh PATH in current session
            $env:PATH = [System.Environment]::GetEnvironmentVariable("PATH", "Machine") + ";" + $env:PATH
            Write-Host "WiX installed." -ForegroundColor Green
        } else {
            throw "WiX Toolset required for -Type $Type. Aborting."
        }
    } else {
        Write-Host "Using WiX: $($candle.Source)"
    }
}

# ── Resolve version ───────────────────────────────────────────────────────────
if (-not $Version) {
    [xml]$pom = Get-Content "$Root\pom.xml" -Encoding UTF8
    $Version = $pom.project.version
    Write-Host "Version from pom.xml: $Version"
} else {
    Write-Host "Version (override): $Version"
}

$steps = if ($SkipTests) { 2 } else { 3 }
$step  = 1

# ── Run tests ─────────────────────────────────────────────────────────────────
if (-not $SkipTests) {
    Write-Host "`n[$step/$steps] Running tests..."
    & $Maven -f "$Root\pom.xml" --no-transfer-progress test -q
    if ($LASTEXITCODE -ne 0) { throw "Tests failed (exit $LASTEXITCODE). Fix failures or pass -SkipTests." }
    Write-Host "Tests passed." -ForegroundColor Green
    $step++
}

# ── Build fat JAR ─────────────────────────────────────────────────────────────
Write-Host "`n[$step/$steps] Building fat JAR..."
& $Maven -f "$Root\pom.xml" --no-transfer-progress package -DskipTests -q
if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)" }
$step++

$Jar = Get-ChildItem $Target -Filter "sibim-desktop-*.jar" |
       Where-Object { $_.Name -notlike "*original*" } |
       Sort-Object LastWriteTime -Descending |
       Select-Object -First 1

if (-not $Jar) { throw "Fat JAR not found in $Target" }
Write-Host "Fat JAR: $($Jar.Name)  ($([math]::Round($Jar.Length/1MB, 1)) MB)"

# ── Run jpackage ──────────────────────────────────────────────────────────────
Write-Host "`n[$step/$steps] Creating $Type → $Out"
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
    $AppFolder = "$Out\SIBIM Desktop"
    $ZipFile   = "$Out\SIBIM-Desktop-$Version-win64-portable.zip"
    if (Test-Path $AppFolder) {
        if (Test-Path $ZipFile) { Remove-Item $ZipFile -Force }
        Compress-Archive -Path $AppFolder -DestinationPath $ZipFile -CompressionLevel Optimal
        $sizeMb = [math]::Round((Get-Item $ZipFile).Length / 1MB, 1)
        Write-Host "Portable zip: $ZipFile  ($sizeMb MB)"
    }
} else {
    # Rename exe to consistent artifact name (no spaces)
    $exeFile = Get-ChildItem $Out -Filter "SIBIM Desktop-*.exe" | Select-Object -First 1
    if ($exeFile) {
        $cleanName = "$Out\SIBIM-Desktop-$Version-win64-setup.exe"
        Rename-Item $exeFile.FullName $cleanName
        $sizeMb = [math]::Round((Get-Item $cleanName).Length / 1MB, 1)
        Write-Host "Installer: $cleanName  ($sizeMb MB)"
    }
}

Write-Host ""
Write-Host "Done. Output: $Out" -ForegroundColor Green
