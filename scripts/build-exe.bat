@echo off
setlocal

REM Resolve jpackage from PATH first, then fallback to JAVA_HOME\bin
set "JPACKAGE_EXE="
for /f "delims=" %%I in ('where jpackage 2^>nul') do (
  if not defined JPACKAGE_EXE set "JPACKAGE_EXE=%%I"
)

if not defined JPACKAGE_EXE (
  if defined JAVA_HOME (
    for %%I in ("%JAVA_HOME%") do (
      if exist "%%~fI\bin\jpackage.exe" set "JPACKAGE_EXE=%%~fI\bin\jpackage.exe"
    )
  )
)

if not defined JPACKAGE_EXE (
  echo jpackage was not found.
  echo Install JDK 17+ and ensure either:
  echo   1. jpackage is on PATH, or
  echo   2. JAVA_HOME points to a JDK root directory.
  exit /b 1
)

REM EXE packaging on Windows requires WiX Toolset (candle.exe and light.exe)
where candle.exe >nul 2>nul
if errorlevel 1 (
  echo WiX Toolset was not found ^(missing candle.exe^).
  echo Install WiX 3.x and add it to PATH.
  echo Example ^(Admin CMD^): choco install wixtoolset --no-progress -y
  echo No-admin fallback: scripts\build-app-image.bat
  echo Then open a new terminal and re-run this script.
  exit /b 1
)

where light.exe >nul 2>nul
if errorlevel 1 (
  echo WiX Toolset was not found ^(missing light.exe^).
  echo Install WiX 3.x and add it to PATH.
  echo Example ^(Admin CMD^): choco install wixtoolset --no-progress -y
  echo No-admin fallback: scripts\build-app-image.bat
  echo Then open a new terminal and re-run this script.
  exit /b 1
)

call mvnw.cmd -q package
if errorlevel 1 (
  echo Maven build failed.
  exit /b 1
)

set "MAIN_JAR="
for %%F in (target\mynosql-*.jar) do (
  echo %%~nxF | findstr /I /B "original-" >nul
  if errorlevel 1 (
    set "MAIN_JAR=%%~nxF"
    goto :jarFound
  )
)

:jarFound
if "%MAIN_JAR%"=="" (
  echo Could not find packaged JAR in target\
  exit /b 1
)

if exist dist rmdir /s /q dist
if exist dist (
  echo Could not remove dist\
  echo Close any installer or file explorer window using dist and try again.
  exit /b 1
)
mkdir dist

"%JPACKAGE_EXE%" ^
  --type exe ^
  --name MyNoSQL ^
  --input target ^
  --main-jar %MAIN_JAR% ^
  --main-class com.mynosql.Main ^
  --dest dist ^
  --win-console

if errorlevel 1 (
  echo EXE packaging failed.
  exit /b 1
)

echo.
echo EXE build complete.
echo Installer created in dist\

endlocal
