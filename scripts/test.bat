@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0test.ps1"
exit /b %ERRORLEVEL%
