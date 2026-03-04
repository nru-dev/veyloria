@echo off
setlocal

if not exist logs mkdir logs
if not exist logs\veyloria mkdir logs\veyloria
if not exist data mkdir data
if not exist config mkdir config

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Start-Process -FilePath '.\\gradlew.bat' -ArgumentList 'runServer','--no-daemon' -WorkingDirectory '%~dp0' -PassThru -RedirectStandardOutput 'logs\\veyloria\\server.out.log' -RedirectStandardError 'logs\\veyloria\\server.err.log'; Set-Content -Path 'run\\veyloria-server.pid' -Value $p.Id"

echo Veyloria server started. Logs: logs\veyloria\
endlocal
