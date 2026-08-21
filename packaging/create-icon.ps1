<#
.SYNOPSIS
    Genera packaging\icon.ico con múltiples resoluciones a partir de los PNG del proyecto.
    Requiere .NET Framework (incluido en Windows 7+). Sin dependencias externas.

.PARAMETER OutputPath
    Ruta del archivo .ico a generar. Por defecto: packaging\icon.ico
#>
param(
    [string]$OutputPath = "$PSScriptRoot\icon.ico"
)

Add-Type -AssemblyName System.Drawing

$projectRoot = Split-Path -Parent $PSScriptRoot
$imgDir = Join-Path $projectRoot "src\main\resources\img"

$sizeEntries = @(
    [pscustomobject]@{ Size = 16;  File = "icon-16.png"  },
    [pscustomobject]@{ Size = 32;  File = "icon-32.png"  },
    [pscustomobject]@{ Size = 48;  File = "icon-48.png"  },
    [pscustomobject]@{ Size = 64;  File = "icon-64.png"  },
    [pscustomobject]@{ Size = 128; File = "icon-128.png" },
    [pscustomobject]@{ Size = 256; File = "icon-256.png" }
)

$pngChunks = [System.Collections.ArrayList]::new()
$sizes     = [System.Collections.ArrayList]::new()

foreach ($entry in $sizeEntries) {
    $filePath = Join-Path $imgDir $entry.File
    if (-not (Test-Path $filePath)) {
        $filePath = Join-Path $imgDir "icon-256.png"
    }
    if (-not (Test-Path $filePath)) {
        Write-Warning "Fuente no encontrada para ${$entry.Size}px — saltando."
        continue
    }

    $bmp = New-Object System.Drawing.Bitmap $filePath

    if ($bmp.Width -ne $entry.Size -or $bmp.Height -ne $entry.Size) {
        $resized = New-Object System.Drawing.Bitmap $entry.Size, $entry.Size
        $g = [System.Drawing.Graphics]::FromImage($resized)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode    = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.DrawImage($bmp, 0, 0, $entry.Size, $entry.Size)
        $g.Dispose()
        $bmp.Dispose()
        $bmp = $resized
    }

    # Codificar como PNG (ICO soporta PNG embebido desde Windows Vista)
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $bytes = $ms.ToArray()
    $ms.Dispose()

    [void]$pngChunks.Add($bytes)
    [void]$sizes.Add($entry.Size)
}

if ($sizes.Count -eq 0) {
    Write-Error "No se encontraron imagenes PNG en $imgDir"
    exit 1
}

# Escribir ICO (ICONDIR + ICONDIRENTRYs + datos PNG)
$count      = $sizes.Count
$dataOffset = 6 + 16 * $count   # ICONDIR(6) + ICONDIRENTRY(16) * n

$fs     = [System.IO.File]::OpenWrite($OutputPath)
$writer = New-Object System.IO.BinaryWriter($fs)

# ICONDIR
$writer.Write([uint16]0)      # Reserved
$writer.Write([uint16]1)      # Type: ICO
$writer.Write([uint16]$count)

# ICONDIRENTRY por cada imagen
$offset = $dataOffset
for ($i = 0; $i -lt $count; $i++) {
    $sz = $sizes[$i]
    if ($sz -eq 256) {
        $writer.Write([byte]0); $writer.Write([byte]0)
    } else {
        $writer.Write([byte]$sz); $writer.Write([byte]$sz)
    }
    $writer.Write([byte]0)         # ColorCount
    $writer.Write([byte]0)         # Reserved
    $writer.Write([uint16]1)       # Planes
    $writer.Write([uint16]32)      # BitCount
    $writer.Write([uint32]$pngChunks[$i].Length)
    $writer.Write([uint32]$offset)
    $offset += $pngChunks[$i].Length
}

# Datos de imagen
for ($i = 0; $i -lt $count; $i++) {
    $writer.Write($pngChunks[$i])
}

$writer.Close()
$fs.Close()

Write-Host "Icono generado: $OutputPath  ($($sizes -join ', ')px)" -ForegroundColor Green
