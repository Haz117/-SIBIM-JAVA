@echo off
title SIBIM - Sistema Integral de Bienes Municipales
echo.
echo  ================================================================
echo   SIBIM Desktop - Sistema Integral de Bienes Municipales
echo  ================================================================
echo.
REM Lanzador de PRODUCCION: ejecuta el app-image generado por jpackage.
REM No usa Maven, Java ni requiere el codigo fuente en esta maquina.
REM
REM Para desplegar en una PC nueva del ayuntamiento, copia a esa PC
REM UNICAMENTE estos 3 elementos (nunca el codigo fuente completo):
REM   1. Este archivo (produccion.bat)
REM   2. La carpeta "SIBIM Desktop" generada por jpackage
REM   3. El archivo .env con las credenciales reales de esa instalacion
REM
REM Estructura esperada junto a este .bat:
REM   .\SIBIM Desktop\SIBIM Desktop.exe
REM   .\.env

REM El app-image trae su propio runtime de Java: no se revisa que Java este
REM instalado (antes se exigia y bloqueaba PCs que no lo necesitan).
set "SIBIM_EXE=SIBIM Desktop\SIBIM Desktop.exe"
if not exist "%SIBIM_EXE%" (
    echo [ERROR] No se encontro el app-image de SIBIM Desktop.
    echo Copia junto a este script la carpeta "SIBIM Desktop" generada por jpackage.
    pause
    exit /b 1
)

echo Iniciando SIBIM Desktop...
start "SIBIM Desktop" "%SIBIM_EXE%"
