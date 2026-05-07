@echo off
chcp 65001 >nul
setlocal

set "ROOT=%~dp0"
set "BOOTSTRAP=%ROOT%policy-report-demo\scripts\bootstrap.ps1"

if not exist "%BOOTSTRAP%" (
  set "BOOTSTRAP=%ROOT%scripts\bootstrap.ps1"
)

if not exist "%BOOTSTRAP%" (
  echo bootstrap.ps1 was not found.
  echo Please keep this file beside the policy-report-demo folder.
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%BOOTSTRAP%" %*
set "CODE=%ERRORLEVEL%"
if not "%CODE%"=="0" (
  echo.
  echo Startup failed. Please check the log above.
  pause
)
exit /b %CODE%