@echo off
setlocal

REM Build app-image first (includes MyNoSQL.exe + runtime files)
call "%~dp0build-app-image.bat"
if errorlevel 1 (
  echo App-image build failed.
  exit /b 1
)

if not exist dist mkdir dist

for /f "delims=" %%V in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "[xml]$p = Get-Content 'pom.xml'; $p.project.version"') do set "APP_VERSION=%%V"
if "%APP_VERSION%"=="" set "APP_VERSION=1.0.0"

set "ZIP_NAME=MyNoSQL-%APP_VERSION%-windows-app.zip"
set "ZIP_PATH=dist\%ZIP_NAME%"

if exist "%ZIP_PATH%" del /q "%ZIP_PATH%"

powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path 'dist-app/MyNoSQL' -DestinationPath '%ZIP_PATH%' -Force"
if errorlevel 1 (
  echo Failed to create ZIP package.
  exit /b 1
)

echo.
echo ZIP package created:
echo   %ZIP_PATH%

endlocal
