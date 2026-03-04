@echo off
setlocal

if not exist run\veyloria-server.pid (
  echo No saved server PID found.
  exit /b 0
)

set /p SERVER_PID=<run\veyloria-server.pid
powershell -NoProfile -ExecutionPolicy Bypass -Command "Stop-Process -Id %SERVER_PID% -Force -ErrorAction SilentlyContinue"
del /f /q run\veyloria-server.pid >nul 2>nul
echo Veyloria server stop signal sent.
endlocal
