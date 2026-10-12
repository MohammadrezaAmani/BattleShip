@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0package-windows.ps1"
exit /b %ERRORLEVEL%
