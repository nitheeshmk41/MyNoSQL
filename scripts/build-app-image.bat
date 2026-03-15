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

call mvnw.cmd -q clean package
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

if exist dist-app rmdir /s /q dist-app
mkdir dist-app

"%JPACKAGE_EXE%" ^
  --type app-image ^
  --name MyNoSQL ^
  --input target ^
  --main-jar %MAIN_JAR% ^
  --main-class com.mynosql.Main ^
  --dest dist-app ^
  --win-console

if errorlevel 1 (
  echo App-image packaging failed.
  exit /b 1
)

echo.
echo App-image build complete.
echo Run from:
echo   dist-app\MyNoSQL\MyNoSQL.exe

endlocal
