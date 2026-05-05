@echo off
setlocal
cd /d "%~dp0backend"
echo Starting Household Platform Backend on http://127.0.0.1:5000
C:\Projects\.venv\Scripts\python.exe main.py
endlocal
