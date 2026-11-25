
@echo off
set "ROOT=%~dp0"

start "PickMe Backend" powershell -NoExit -Command "Set-Location '%ROOT%backend'; .\gradlew.bat bootRun"

start "PickMe AI" powershell -NoExit -Command "$env:PYTHONUTF8='1'; Set-Location '%ROOT%ai-server'; ..\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --port 8000"

start "PickMe Frontend" powershell -NoExit -Command "Set-Location '%ROOT%frontend'; npm.cmd run dev"
