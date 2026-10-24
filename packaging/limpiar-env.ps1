#Requires -Version 5.1
<#
.SYNOPSIS
    Limpia el .env de una PC de SIBIM sin mostrar ningun secreto.

.DESCRIPTION
    1. Guarda una copia (.env.respaldo-AAAAMMDD-HHMMSS) junto al original.
    2. Quita las claves de servicio de Supabase (SUPABASE_SERVICE_KEY,
       SUPABASE_SERVICE_ROLE_KEY, SUPABASE_SECRET_KEY): SIBIM no las usa y dan
       control total del proyecto a quien abra el archivo. Despues de correrlo,
       rota esa clave en Supabase (Project Settings -> API Keys).
    3. Saca user= y password= de DB_URL: el driver los preferia a DB_USER y
       DB_PASSWORD. Si DB_USER o DB_PASSWORD no estaban, se escriben con esos
       valores; si ya estaban, se conservan los suyos.
    4. Si falta SUPABASE_ANON_KEY, la copia de la configuracion del instalador
       (packaging\sibim.env, o la de SIBIM instalado): sin ella la PC no sube
       fotos ni publica la ficha del QR. Es la llave publica que ya lleva cada
       instalador; solo se copia si es del mismo proyecto (SUPABASE_URL) y no
       es una clave secreta.
    Solo muestra los NOMBRES de lo que cambio.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File packaging\limpiar-env.ps1
    powershell -ExecutionPolicy Bypass -File packaging\limpiar-env.ps1 -Ruta C:\ruta\.env
    powershell -ExecutionPolicy Bypass -File packaging\limpiar-env.ps1 -Llave E:\sibim.env
#>
param(
    [string]$Ruta = "$env:APPDATA\SIBIM\.env",
    # De donde copiar SUPABASE_ANON_KEY si falta.
    [string]$Llave
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path $Ruta)) { throw "No existe $Ruta" }
$utf8 = New-Object System.Text.UTF8Encoding($false)
$lineas = [System.Collections.Generic.List[string]]([IO.File]::ReadAllLines($Ruta, $utf8))
$cambios = @()

function Valor([string]$clave) {
    foreach ($l in $lineas) { if ($l -match "^\s*$clave\s*=\s*(.*)$") { return $Matches[1].Trim() } }
    return $null
}

# -- Claves de servicio --------------------------------------------------------
foreach ($clave in 'SUPABASE_SERVICE_KEY', 'SUPABASE_SERVICE_ROLE_KEY', 'SUPABASE_SECRET_KEY') {
    $antes = $lineas.Count
    $lineas.RemoveAll([Predicate[string]]{ param($l) $l -match "^\s*$clave\s*=" }) | Out-Null
    if ($lineas.Count -lt $antes) { $cambios += "quitada $clave" }
}

# -- user= / password= dentro de DB_URL ----------------------------------------
for ($i = 0; $i -lt $lineas.Count; $i++) {
    if ($lineas[$i] -notmatch '^\s*DB_URL\s*=\s*(.*)$') { continue }
    $url = $Matches[1].Trim()
    if ($url -notmatch '\?') { break }
    $base, $query = $url.Split('?', 2)
    $quedan = @(); $usuario = $null; $clave = $null
    # Decoded like DatabaseConfig.separarCredenciales (URLDecoder: '+' is a space,
    # keys in any case), so the PC keeps connecting with exactly the same values.
    foreach ($par in $query.Split('&')) {
        if ($par -match '(?i)^user=(.*)$') { $usuario = [Uri]::UnescapeDataString($Matches[1].Replace('+', ' ')) }
        elseif ($par -match '(?i)^password=(.*)$') { $clave = [Uri]::UnescapeDataString($Matches[1].Replace('+', ' ')) }
        elseif ($par) { $quedan += $par }
    }
    if (-not $usuario -and -not $clave) { break }
    $lineas[$i] = 'DB_URL=' + $base + $(if ($quedan.Count) { '?' + ($quedan -join '&') } else { '' })
    $cambios += "DB_URL sin user=/password="
    if ($usuario -and -not (Valor 'DB_USER')) { $lineas.Insert($i + 1, "DB_USER=$usuario"); $cambios += "agregado DB_USER (venia en DB_URL)" }
    if ($clave -and -not (Valor 'DB_PASSWORD')) { $lineas.Insert($i + 1, "DB_PASSWORD=$clave"); $cambios += "agregado DB_PASSWORD (venia en DB_URL)" }
    break
}

# -- Llave publica de Storage --------------------------------------------------
# Como SupabaseStorage.esClaveSecreta: "sb_secret_..." o un JWT de service_role.
function Es-Secreta([string]$k) {
    if ($k.StartsWith('sb_secret_')) { return $true }
    $partes = $k.Split('.')
    if ($partes.Count -ne 3) { return $false }
    $p = $partes[1].Replace('-', '+').Replace('_', '/')
    while ($p.Length % 4) { $p += '=' }
    try { return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) -match '"role"\s*:\s*"service_role"' }
    catch { return $false }
}

$url = Valor 'SUPABASE_URL'
if (-not (Valor 'SUPABASE_ANON_KEY') -and $url) {
    $fuentes = if ($Llave) { @($Llave) } else { @("$PSScriptRoot\sibim.env", "$env:ProgramFiles\SIBIM Desktop\app\sibim.env") }
    $copiada = $false
    foreach ($f in $fuentes) {
        if (-not (Test-Path $f)) { continue }
        $otra = @{}
        foreach ($l in [IO.File]::ReadAllLines($f, $utf8)) { if ($l -match '^\s*([A-Z_]+)\s*=\s*(.*)$') { $otra[$Matches[1]] = $Matches[2].Trim() } }
        if (-not $otra['SUPABASE_ANON_KEY']) { continue }
        if (([string]$otra['SUPABASE_URL']).TrimEnd('/') -ne $url.TrimEnd('/')) {
            Write-Host "  $f es de otro proyecto de Supabase: no se toma su llave." -ForegroundColor Yellow; continue
        }
        if (Es-Secreta $otra['SUPABASE_ANON_KEY']) {
            Write-Host "  La SUPABASE_ANON_KEY de $f es una clave secreta: no se copia." -ForegroundColor Yellow; continue
        }
        $lineas.Add("SUPABASE_ANON_KEY=" + $otra['SUPABASE_ANON_KEY'])
        $cambios += "agregada SUPABASE_ANON_KEY (de $f)"
        $copiada = $true
        break
    }
    if (-not $copiada) {
        Write-Host "  Falta SUPABASE_ANON_KEY y no la encontre en: $($fuentes -join ', ')." -ForegroundColor Yellow
        Write-Host "  Sin ella esta PC no sube fotos. Pasa con -Llave el sibim.env del instalador." -ForegroundColor Yellow
    }
}

if (-not $cambios) { Write-Host "  Nada que limpiar en $Ruta." -ForegroundColor Green; exit 0 }

$respaldo = "$Ruta.respaldo-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
Copy-Item $Ruta $respaldo
[IO.File]::WriteAllText($Ruta, (($lineas -join "`r`n") + "`r`n"), $utf8)
Write-Host ""
Write-Host "  Respaldo: $respaldo" -ForegroundColor DarkGray
foreach ($c in $cambios) { Write-Host "  - $c" -ForegroundColor Green }
Write-Host ""
if ($cambios -match 'SERVICE|SECRET') {
    Write-Host "  Ahora rota la clave de servicio en Supabase (Project Settings -> API Keys)." -ForegroundColor Yellow
    Write-Host "  Cuando todo funcione, borra el respaldo: todavia la contiene." -ForegroundColor Yellow
}
