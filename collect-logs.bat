@echo off
:: ============================================================
:: collect-logs.bat — Сбор ADB-логов RocketLauncher
:: ============================================================
:: Запустите этот файл при подключённом устройстве/эмуляторе.
:: Логи сохраняются в папку logs\ рядом со скриптом.
:: ============================================================

set ADB=G:\Android\android-sdk\platform-tools\adb.exe
set PKG=com.rocketlauncher
set LOGS_DIR=%~dp0logs
set TIMESTAMP=%DATE:~6,4%-%DATE:~3,2%-%DATE:~0,2%_%TIME:~0,2%-%TIME:~3,2%-%TIME:~6,2%
set TIMESTAMP=%TIMESTAMP: =0%

if not exist "%LOGS_DIR%" mkdir "%LOGS_DIR%"

echo ============================================
echo  RocketLauncher — сбор диагностических логов
echo ============================================
echo.

:: Проверяем ADB
"%ADB%" version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] ADB не найден: %ADB%
    echo Проверьте путь к Android SDK в этом файле.
    pause
    exit /b 1
)

:: Проверяем устройство
"%ADB%" devices
echo.

:: ============================================
:: 1. Полный logcat с тегами приложения
:: ============================================
echo [1/4] Сохранение logcat (последние 5000 строк)...
"%ADB%" logcat -d -v time *:W com.rocketlauncher:V RocketWS:V MsgFgService:V ChatVM:V MessageRepo:V AuthRepo:V -t 5000 > "%LOGS_DIR%\logcat_%TIMESTAMP%.txt" 2>&1
echo     Сохранено: logs\logcat_%TIMESTAMP%.txt

:: ============================================
:: 2. Буфер крашей (crash buffer)
:: ============================================
echo [2/4] Сохранение crash buffer...
"%ADB%" logcat -d -b crash -v time > "%LOGS_DIR%\crash_%TIMESTAMP%.txt" 2>&1
echo     Сохранено: logs\crash_%TIMESTAMP%.txt

:: ============================================
:: 3. Лог ANR из /data/anr (если есть root/доступ)
:: ============================================
echo [3/4] Попытка получить ANR traces...
"%ADB%" shell "ls /data/anr/ 2>/dev/null | head -5" > "%LOGS_DIR%\anr_list_%TIMESTAMP%.txt" 2>&1
"%ADB%" shell "cat /data/anr/anr_* 2>/dev/null | tail -200" >> "%LOGS_DIR%\anr_list_%TIMESTAMP%.txt" 2>&1
echo     Сохранено: logs\anr_list_%TIMESTAMP%.txt (может быть пустым без root)

:: ============================================
:: 4. Crash log файл из private storage приложения
:: ============================================
echo [4/4] Извлечение crash_log.txt из app storage...
"%ADB%" shell "run-as %PKG% cat files/crash_log.txt 2>/dev/null" > "%LOGS_DIR%\app_crash_log_%TIMESTAMP%.txt" 2>&1
if "%ERRORLEVEL%"=="0" (
    echo     Сохранено: logs\app_crash_log_%TIMESTAMP%.txt
) else (
    echo     [!] Не удалось извлечь (нужен debug-build или root^)
)

echo.
echo ============================================
echo  Готово! Логи в папке: %LOGS_DIR%
echo.
echo  Что смотреть в первую очередь:
echo   - crash_%TIMESTAMP%.txt  — необработанные исключения
echo   - app_crash_log_%TIMESTAMP%.txt — лог ошибок из приложения
echo   - logcat_%TIMESTAMP%.txt — ищите строки: E/RealtimeWS, E/MsgFgService, FATAL
echo ============================================
echo.

:: Открываем папку с логами
start "" "%LOGS_DIR%"
pause
