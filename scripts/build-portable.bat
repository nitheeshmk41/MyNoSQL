@echo off
setlocal

REM Build a self-contained runnable JAR with dependencies
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

echo.
echo Build complete.
echo Share this file:
echo   target\%MAIN_JAR%
echo.
echo Run it with:
echo   java -jar %MAIN_JAR%
echo   java -jar %MAIN_JAR% --shell

endlocal
