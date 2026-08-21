@echo off
title SIBIM - Sistema Integral de Bienes Municipales
echo.
echo  ================================================================
echo   SIBIM Desktop - Sistema Integral de Bienes Municipales
echo  ================================================================
echo.
REM Lanzador de PRODUCCION: solo ejecuta el .jar ya compilado.
REM No usa Maven ni requiere el codigo fuente en esta maquina.
REM
REM Para desplegar en una PC nueva del ayuntamiento, copia a esa PC
REM UNICAMENTE estos 3 elementos (nunca el codigo fuente completo):
REM   1. Este archivo (produccion.bat)
REM   2. El archivo sibim-desktop-1.0.0.jar (generado con
REM      "mvn package" una sola vez, en una maquina de desarrollo —
REM      el shade plugin lo deja en target\ con este nombre, ya
REM      autocontenido con todas las dependencias)
REM   3. El archivo .env con las credenciales reales de esa instalacion
REM
REM Estructura esperada junto a este .bat:
REM   .\sibim-desktop-1.0.0.jar
REM   .\.env

REM Verificar Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java no encontrado. Instala Java 21 o superior.
    pause
    exit /b 1
)

if not exist "sibim-desktop-1.0.0.jar" (
    echo [ERROR] No se encontro sibim-desktop-1.0.0.jar junto a este script.
    echo Copia aqui el .jar generado con "mvn package" en la maquina de desarrollo
    echo (queda en target\sibim-desktop-1.0.0.jar).
    pause
    exit /b 1
)

echo Iniciando SIBIM Desktop...
echo.
java -jar sibim-desktop-1.0.0.jar
pause
