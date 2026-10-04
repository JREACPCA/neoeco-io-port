@echo off
REM ===========================================================================
REM  Neo ECO IO Port - Build Launcher  (ASCII ONLY - do not add non-ASCII here)
REM
REM  Why this file is ASCII-only:
REM    cmd.exe parses .bat files using the system OEM code page (CP936 on
REM    Chinese Windows). Non-ASCII characters written as UTF-8 get mangled into
REM    garbage commands and you see errors like:
REM       'xxx' is not recognized as an internal or external command
REM    So all localized output lives in build.ps1, which handles UTF-8 fine.
REM
REM  Real logic: build.ps1 (same folder)
REM ===========================================================================

setlocal
cd /d "%~dp0"
title Neo ECO IO Port - Build

where powershell.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] powershell.exe not found. This launcher requires Windows PowerShell.
    pause
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
    echo.
    echo Build failed with exit code %RC%.
    echo See build-log.txt for the full log.
    pause
)

endlocal
exit /b %RC%
