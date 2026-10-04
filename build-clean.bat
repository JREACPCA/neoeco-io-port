@echo off
REM ===========================================================================
REM  Neo ECO IO Port - CLEAN NeoForm build            ASCII ONLY
REM
REM  Symptom being tested:
REM    "Node action for recompile failed" on a *stock* NeoForge project too,
REM    always on net/minecraft/world/ticks/package-info.java line 1.
REM
REM  Hypothesis:
REM    The very first run crashed inside the "transformSources" stage with an
REM    AccessDeniedException (temp dir not writable). That may have left a
REM    TRUNCATED intermediate artifact in the NeoForm cache. Every later run
REM    shows "REUSE Used cache of transformSources", i.e. it keeps replaying the
REM    damaged artifact, which then breaks "recompile".
REM
REM  What this script does:
REM    * copies the existing .gradle-home to .gradle-home-fresh
REM      (so neoform-runtime and every other Maven artifact is reused -> no
REM       network needed, which matters because maven.neoforged.net is flaky)
REM    * deletes ONLY caches\neoformruntime (the stage outputs)
REM    * rebuilds, forcing the whole NeoForm pipeline to run from scratch
REM
REM  Cost: one full pipeline run (~5-15 min: decompile etc. cannot be reused).
REM
REM  Usage:
REM     build-clean.bat                       use bundled jdk21
REM     build-clean.bat "D:\path\to\jdk21"    use a different JDK 21
REM ===========================================================================

setlocal
cd /d "%~dp0"
title Neo ECO IO Port - CLEAN NeoForm build

set "JAVA_HOME=%~dp0jdk21"
if not "%~1"=="" set "JAVA_HOME=%~1"

set "SRC_HOME=%~dp0.gradle-home"
set "GRADLE_USER_HOME=%~dp0.gradle-home-fresh"
set "NEWTMP=%~dp0build\tmp\javatmp-fresh"
if not exist "%NEWTMP%" mkdir "%NEWTMP%"
set "TEMP=%NEWTMP%"
set "TMP=%NEWTMP%"

echo ============================================================
echo   CLEAN NeoForm build
echo   JAVA_HOME        = %JAVA_HOME%
echo   GRADLE_USER_HOME = %GRADLE_USER_HOME%
echo   TEMP             = %TEMP%
echo ============================================================
echo.

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERROR] No javac.exe under "%JAVA_HOME%".
    echo         Pass a valid JDK 21 path as the first argument, or keep the bundled jdk21.
    goto :fail
)
"%JAVA_HOME%\bin\java" -version 2>&1
echo.

REM --- 1) rebuild a fresh gradle home from the existing one ------------------
if exist "%GRADLE_USER_HOME%" (
    echo [1/3] Removing previous .gradle-home-fresh ...
    rmdir /s /q "%GRADLE_USER_HOME%" 2>nul
)

if exist "%SRC_HOME%" (
    echo [1/3] Copying .gradle-home -^> .gradle-home-fresh ...
    echo       ^(this reuses neoform-runtime so no network is required^)
    xcopy "%SRC_HOME%" "%GRADLE_USER_HOME%\" /E /I /H /Y /Q >nul
    if errorlevel 1 (
        echo       [WARN] xcopy reported an error; continuing anyway.
    )
) else (
    echo [1/3] No .gradle-home found; a fresh one will be created.
    echo       NOTE: this requires network access to maven.neoforged.net
    echo             for net.neoforged:neoform-runtime.
    mkdir "%GRADLE_USER_HOME%" 2>nul
)

REM --- 2) purge ONLY the NeoForm stage outputs -------------------------------
set "NF=%GRADLE_USER_HOME%\caches\neoformruntime"
if exist "%NF%" (
    echo [2/3] Purging NeoForm stage cache: caches\neoformruntime
    rmdir /s /q "%NF%" 2>nul
) else (
    echo [2/3] No caches\neoformruntime to purge.
)

if exist "%GRADLE_USER_HOME%\caches\modules-2\files-2.1\net.neoforged\neoform-runtime" (
    echo       neoform-runtime is present in the reused cache - good.
) else (
    echo       [WARN] neoform-runtime NOT found in the cache.
    echo              This run will need maven.neoforged.net to be reachable.
)

REM --- 3) build -------------------------------------------------------------
echo.
echo [3/3] Building (full NeoForm pipeline, expect 5-15 minutes) ...
echo       Log: build-log-clean.txt
echo.

call "%~dp0gradle-8.10.2\bin\gradle.bat" --no-daemon --console=plain --stacktrace build > "%~dp0build-log-clean.txt" 2>&1
set "RC=%ERRORLEVEL%"

type "%~dp0build-log-clean.txt"

echo.
echo ============================================================
if "%RC%"=="0" (
    echo   [OK] CLEAN build SUCCEEDED
    echo.
    echo   This means the earlier failures were caused by a damaged NeoForm
    echo   cache, not by the mod source. The jar is in build\libs\.
    dir /b "%~dp0build\libs\*.jar" 2>nul
) else (
    echo   [FAILED] exit code %RC%
    echo.
    echo   Look for the line "*** Started working on recompile" in the log.
    echo   * If it FAILED again the same way, the NeoForm cache was NOT the
    echo     cause and the problem is the local JDK / environment.
    echo   * If it now fails in a different place, send the new log.
    echo   Log: build-log-clean.txt
)
echo ============================================================
echo.

:fail
echo Press any key to close ...
pause >nul
endlocal
exit /b %RC%
