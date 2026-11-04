@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Reset-Defaults.ps1"
if errorlevel 1 (echo Reset failed. Read the error above.)
pause
