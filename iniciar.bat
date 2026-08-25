@echo off
title SIBIM - Sistema Integral de Bienes Municipales
echo.
echo  ================================================================
echo   SIBIM Desktop - Sistema Integral de Bienes Municipales
echo  ================================================================
echo.

REM Verificar Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java no encontrado. Instala Java 21 o superior.
    pause
    exit /b 1
)

echo Compilando (actualiza automaticamente si hay cambios)...
call maven-dist\apache-maven-3.9.9\bin\mvn.cmd package -q
if %errorlevel% neq 0 (
    echo [ERROR] Error al compilar. Revisa los logs.
    pause
    exit /b 1
)

echo Iniciando SIBIM Desktop...
echo.
for %%J in (target\sibim-desktop-*.jar) do set "SIBIM_JAR=%%J"
if not defined SIBIM_JAR (
    echo [ERROR] No se encontro el JAR generado en target.
    pause
    exit /b 1
)
java -jar "%SIBIM_JAR%"
pause
    
