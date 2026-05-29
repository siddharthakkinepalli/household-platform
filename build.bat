@echo off
cd /d "%~dp0"

set APK=android\build\outputs\apk\debug\android-arm64-v8a-debug.apk

echo === Building Household Platform ===
if exist "%APK%" del "%APK%"

call "%~dp0gradlew.bat" :android:assembleDebug

if not exist "%APK%" (
    echo [FAIL] Build failed - APK not produced.
    exit /b 1
)
echo [OK] Build done.

set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe

echo === Checking for connected emulator ===
set TARGET_EMULATOR=
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    echo %%A | findstr /i "emulator" >nul
    if not errorlevel 1 (
        if "%%B"=="device" (
            set TARGET_EMULATOR=%%A
            goto install
        )
    )
)

echo [WARN] No emulator found. Looking for any connected device...
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        set TARGET_EMULATOR=%%A
        goto install
    )
)

echo [FAIL] No device or emulator connected.
goto done

:install
echo [INFO] Deploying to %TARGET_EMULATOR%...
"%ADB%" -s %TARGET_EMULATOR% install -r "%APK%"
if %errorlevel% equ 0 (
    echo [OK] Deployment successful.
) else (
    echo [FAIL] Deployment failed.
)

:done
