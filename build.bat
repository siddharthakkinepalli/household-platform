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
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" (
        echo [INFO] Installing on %%A...
        "%ADB%" install -r "%APK%"
        echo [OK] Done.
        goto done
    )
)
echo [INFO] No device connected.

:done
