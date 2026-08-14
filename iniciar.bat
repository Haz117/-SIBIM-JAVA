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
java -jar target\sibim-desktop-1.0.0.jar
pause
    
