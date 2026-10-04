@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\laptop-agent.ps1" -Action Menu
if errorlevel 1 pause
