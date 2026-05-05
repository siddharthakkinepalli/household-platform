@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0run_phone_smoke.ps1" -InstallFirst %*
endlocal
