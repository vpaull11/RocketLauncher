@echo off
setlocal
cd /d "%~dp0"

echo Building release AAB...
call gradlew.bat :app:bundleRelease %*

if errorlevel 1 (
    echo.
    echo Build FAILED.
    exit /b 1
)

echo.
echo Done. AAB path:
echo   %~dp0app\build\outputs\bundle\release\app-release.aab
if exist "%~dp0keystore.properties" (
    call "%~dp0verify-aab-signing.bat"
    if errorlevel 1 exit /b 1
) else (
    echo.
    echo Note: no keystore.properties — release is debug-signed. For Play, add keystore.properties ^(see keystore.properties.example^).
)
exit /b 0
