@echo off
setlocal
cd /d "%~dp0"

echo Running emulator workflow automation...
powershell -ExecutionPolicy Bypass -File "%~dp0run_emulator_workflows.ps1" %*

if errorlevel 1 (
  echo.
  echo Emulator workflow test FAILED.
  exit /b 1
)

echo.
echo Emulator workflow test PASSED.
exit /b 0
