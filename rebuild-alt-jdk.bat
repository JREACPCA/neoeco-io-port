@echo off
REM ===========================================================================
REM  Rebuild using the already-downloaded Oracle JDK 21.0.2    ASCII ONLY
REM
REM  try-alt-jdk.bat downloaded and extracted it fine, but looked for
REM  javac.exe at the wrong depth, so it stopped. This script just uses the
REM  extracted JDK directly - no re-download.
REM
REM  Actual JDK home: .\jdk21-0-2\jdk-21.0.2
REM ===========================================================================

setlocal
cd /d "%~dp0"
title Rebuild with Oracle JDK 21.0.2

set "ALTJDK=%~dp0jdk21-0-2\jdk-21.0.2"

if not exist "%ALTJDK%\bin\javac.exe" (
    echo [ERROR] JDK not found at:
    echo         %ALTJDK%
    echo.
    echo Looking for any javac.exe under jdk21-0-2 to figure out the real path ...
    for /f "delims=" %%F in ('dir /s /b "%~dp0jdk21-0-2\javac.exe" 2^>nul') do echo   found: %%F
    echo.
    pause
    exit /b 1
)

echo Using JDK: %ALTJDK%
"%ALTJDK%\bin\java" -version 2>&1
echo.

call "%~dp0build-clean.bat" "%ALTJDK%"
set "RC=%ERRORLEVEL%"

echo.
echo ============================================================
if "%RC%"=="0" (
    echo   [SUCCESS] Oracle JDK 21.0.2 fixed it.
    echo             The mod jar should be in build\libs\.
    dir /b "%~dp0build\libs\*.jar" 2>nul
) else (
    echo   [STILL FAILING] exit code %RC%
    echo.
    echo   The JDK build was not the cause. Next step: capture the real javac
    echo   error with debug logging:
    echo       cd ..\stock-mdk-test
    echo       run-debug.bat
)
echo ============================================================
echo.
pause
endlocal
exit /b %RC%
