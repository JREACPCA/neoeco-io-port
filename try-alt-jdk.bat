@echo off
REM ===========================================================================
REM  Try a different JDK 21, then rebuild        ASCII ONLY
REM
REM  Symptom: NeoForm "recompile" fails on net/minecraft/world/ticks/
REM  package-info.java line 1, with a "null" diagnostic. It also fails on a
REM  stock, unmodified NeoForge project -> the local JDK build is the prime
REM  suspect (NeoFormRuntime 2.0.31 is from 2024; the bundled JDK is 21.0.12.1
REM  from 2026).
REM
REM  This script:
REM    1. downloads Oracle JDK 21.0.2 (compiles/runs GetAltJdk.java)
REM    2. rebuilds using that JDK, via build-clean.bat
REM
REM  Just double-click it. Nothing else to type.
REM ===========================================================================

setlocal
cd /d "%~dp0"
title Try a different JDK 21 - Neo ECO IO Port

set "JAVAC=%~dp0jdk21\bin\java.exe"

echo ============================================================
echo   Step 1/2 : download an alternative JDK 21 (Oracle 21.0.2)
echo ============================================================
echo.

if not exist "%JAVAC%" (
    echo [ERROR] Bundled JDK not found: %JAVAC%
    echo         Expected the folder "jdk21" next to this script.
    goto :fail
)

"%JAVAC%" "%~dp0GetAltJdk.java"
if errorlevel 1 (
    echo.
    echo [ERROR] Could not download/extract the alternative JDK.
    echo         Check proxy.txt if your network needs a proxy.
    goto :fail
)

REM Locate the extracted JDK robustly: the zip has a top-level folder, so the
REM result can be jdk21-0-2\jdk-21.0.2\ or similar. Search instead of guessing.
set "ALTJDK="
for /f "delims=" %%F in ('dir /s /b "%~dp0jdk21-0-2\javac.exe" 2^>nul') do (
    if not defined ALTJDK (
        for %%D in ("%%F\..\..") do set "ALTJDK=%%~fD"
    )
)
if not defined ALTJDK (
    for /f "delims=" %%F in ('dir /s /b "%~dp0jdk21-ms\javac.exe" 2^>nul') do (
        if not defined ALTJDK (
            for %%D in ("%%F\..\..") do set "ALTJDK=%%~fD"
        )
    )
)

if not defined ALTJDK (
    echo.
    echo [ERROR] Could not locate javac.exe under jdk21-0-2 or jdk21-ms.
    echo         Run this to see the real layout:
    echo             dir /s /b jdk21-0-2\javac.exe
    goto :fail
)

echo.
echo ============================================================
echo   Step 2/2 : rebuild with  %ALTJDK%
echo ============================================================
echo.
echo This runs the FULL NeoForm pipeline (5-15 min) using fresh caches.
echo.

call "%~dp0build-clean.bat" "%ALTJDK%"
set "RC=%ERRORLEVEL%"

echo.
echo ============================================================
if "%RC%"=="0" (
    echo   [SUCCESS] The different JDK fixed it.
    echo             The jar should be in build\libs\.
) else (
    echo   [STILL FAILING] exit code %RC%
    echo.
    echo   The JDK was not the cause. Next diagnostic:
    echo       cd ..\stock-mdk-test
    echo       run-debug.bat
    echo   which captures the real javac error message behind the "null".
)
echo ============================================================
echo.
pause
endlocal
exit /b %RC%

:fail
echo.
pause
endlocal
exit /b 1
