@echo off
setlocal

:: Get root directory of the project
set PROJECT_ROOT=%~dp0
cd /d "%PROJECT_ROOT%"

echo ============================================================
echo   Household Platform - Running All Unit Tests
echo ============================================================

:: Check if gradlew exists
if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found in %PROJECT_ROOT%
    exit /b 1
)

:: Run unit tests for all modules
echo [INFO] Executing Gradle test tasks...
call gradlew.bat :libs:household-core:testDebugUnitTest :modules:expenses:testDebugUnitTest :android:testDebugUnitTest --continue

set EXIT_CODE=%errorlevel%

echo.
if %EXIT_CODE% equ 0 (
    echo [OK] SUCCESS: All unit tests passed!
) else (
    echo [FAIL] ERROR: One or more unit tests failed. Check individual module reports.
)

:: Option to keep window open if run by double-clicking
if "%1" neq "/silent" (
    echo.
    echo Press any key to exit...
    pause > nul
)

exit /b %EXIT_CODE%
