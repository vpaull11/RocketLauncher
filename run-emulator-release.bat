@echo off
REM Use ASCII only in this file - UTF-8 Cyrillic breaks cmd.exe batch parser.
REM Release APK: app\build\outputs\apk\release\app-release.apk
REM Signing: without keystore.properties, release uses the debug keystore (installable on device/emulator).
REM For a release key: copy keystore.properties.example to keystore.properties, add release.keystore, fill passwords.
setlocal enabledelayedexpansion

set "JAVA_HOME=G:\Android\openjdk\jdk-17.0.12"
set "ANDROID_HOME=G:\Android\android-sdk"
set "PATH=%ANDROID_HOME%\platform-tools;%JAVA_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo [1/3] Building release APK...
echo Stopping Gradle daemons...
call gradlew.bat --stop >nul 2>&1
timeout /t 2 /nobreak >nul
call gradlew.bat assembleRelease
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

echo.
echo [2/3] Installing on emulator...
adb kill-server >nul 2>&1
adb start-server >nul 2>&1
timeout /t 2 /nobreak >nul
for /f "tokens=1" %%d in ('adb devices ^| findstr "emulator.*device"') do (
    adb -s %%d install -r app\build\outputs\apk\release\app-release.apk
    if errorlevel 1 (
        echo Install failed.
        pause
        exit /b 1
    )
    set "DEVICE=%%d"
    goto :installed
)
echo Error: start an emulator with adb, then run this script again.
pause
exit /b 1

:installed
echo.
echo [3/3] Launching app...
adb -s !DEVICE! shell am start -n com.rocketlauncher/.presentation.MainActivity

echo.
echo Done.
pause
