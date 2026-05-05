@echo off
setlocal
cd /d "%~dp0"

echo Running automated Play Internal upload for com.jugaad.home...
echo Default behavior: bumps patch version, builds release AAB, uploads to Play Internal.
C:/Projects/.venv/Scripts/python.exe scripts\publish_play_internal.py %*

if errorlevel 1 (
  echo.
  echo Publish failed.
  exit /b 1
)

echo.
echo Publish completed.
exit /b 0
