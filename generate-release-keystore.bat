@echo off
REM Creates release-upload.keystore in repo root (gitignored: *.keystore).
REM Then copy keystore.properties.example to keystore.properties and fill passwords and keyAlias.
setlocal
cd /d "%~dp0"

set "OUT=%~dp0release-upload.keystore"
if exist "%OUT%" (
    echo File already exists: %OUT%
    echo Delete or rename it first if you want a new keystore.
    exit /b 1
)

where keytool >nul 2>&1
if errorlevel 1 (
    if defined JAVA_HOME (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    )
)

echo Creating keystore: %OUT%
echo You will be prompted for keystore password and key password ^(remember them for keystore.properties^).
keytool -genkeypair -v -keystore "%OUT%" -alias release -keyalg RSA -keysize 2048 -validity 10000
if errorlevel 1 exit /b 1

echo.
echo Next: copy keystore.properties.example to keystore.properties, set storeFile=release-upload.keystore, same passwords and alias ^(release^).
exit /b 0
