@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

:: build.bat  --  Household Platform (com.jugaad.home)
::
:: Usage:
::   build.bat              -> debug APK
::   build.bat release      -> release APK  (requires android\keystore.properties)
::   build.bat debug -i     -> debug APK, then install to connected device
::   build.bat release -i   -> release APK, then install
::   build.bat clean        -> clean build outputs only

set BUILD_TYPE=debug
set DO_INSTALL=0

:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="debug"   set BUILD_TYPE=debug   & shift & goto parse_args
if /i "%~1"=="release" set BUILD_TYPE=release & shift & goto parse_args
if /i "%~1"=="-i"      set DO_INSTALL=1       & shift & goto parse_args
if /i "%~1"=="clean"   goto do_clean
echo [WARN] Unknown argument "%~1" -- ignoring.
shift
goto parse_args
:args_done

:: Release pre-flight: keystore must exist
if /i "%BUILD_TYPE%"=="release" (
    if not exist "android\keystore.properties" (
        echo.
        echo [ERROR] android\keystore.properties not found.
        echo         Create it with storeFile / storePassword / keyAlias / keyPassword.
        exit /b 1
    )
)

:: Build
echo.
echo === Household Platform ^| %BUILD_TYPE% build ===
echo.

if /i "%BUILD_TYPE%"=="release" (
    set GRADLE_TASK=:android:assembleRelease
    set APK_PATH=android\build\outputs\apk\release\android-release.apk
) else (
    set GRADLE_TASK=:android:assembleDebug
    set APK_PATH=android\build\outputs\apk\debug\android-debug.apk
)

call gradlew.bat %GRADLE_TASK% --stacktrace
if errorlevel 1 (
    echo.
    echo [FAIL] Gradle build failed. Check errors above.
    exit /b 1
)

:: Report APK
echo.
if exist "%APK_PATH%" (
    for %%F in ("%APK_PATH%") do set /a APK_SIZE_KB=%%~zF / 1024
    echo [OK] APK ready: %APK_PATH%
    echo      Size: !APK_SIZE_KB! KB
) else (
    echo [WARN] Build succeeded but APK not found at: %APK_PATH%
    set DO_INSTALL=0
)

:: Optional install via adb
if "%DO_INSTALL%"=="1" (
    set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
    if not exist "!ADB!" (
        echo.
        echo [WARN] adb not found at !ADB! -- skipping install.
        goto done
    )
    echo.
    echo [INFO] Installing to connected device...
    "!ADB!" install -r "%APK_PATH%"
    if errorlevel 1 (
        echo [FAIL] adb install failed.
        exit /b 1
    )
    echo [OK] Installed com.jugaad.home
)

goto done

:do_clean
echo.
echo [INFO] Cleaning build outputs...
call gradlew.bat :android:clean
if errorlevel 1 (
    echo [FAIL] Clean failed.
    exit /b 1
)
echo [OK] Clean complete.
goto done

:done
echo.
exit /b 0
