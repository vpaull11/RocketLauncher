@echo off
setlocal
cd /d "%~dp0"

set "AAB=%~dp0app\build\outputs\bundle\release\app-release.aab"
if not exist "%AAB%" (
    echo AAB not found: %AAB%
    echo Build first: build-aab.bat
    exit /b 1
)

set "JS=jarsigner"
where jarsigner >nul 2>&1
if errorlevel 1 (
    if defined JAVA_HOME (
        set "JS=%JAVA_HOME%\bin\jarsigner.exe"
    ) else (
        echo jarsigner not found. Set JAVA_HOME or add JDK bin to PATH.
        exit /b 1
    )
)

set "CERTLOG=%TEMP%\rocketlauncher-aab-certs.txt"
"%JS%" -verify -verbose -certs "%AAB%" >"%CERTLOG%" 2>&1
if errorlevel 1 (
    echo jarsigner verify failed.
    type "%CERTLOG%"
    exit /b 1
)

findstr /C:"CN=Android Debug" "%CERTLOG%" >nul
if not errorlevel 1 (
    echo ERROR: AAB is signed with the DEBUG certificate ^(CN=Android Debug^).
    echo For Play, use release keystore and keystore.properties. See keystore.properties.example
    exit /b 1
)

echo OK: AAB signature verified; not debug-signed.
exit /b 0
