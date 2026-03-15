@echo off
setlocal

set "WRAPPER_DIR=%~dp0.mvn\wrapper"
set "MAVEN_VERSION=3.9.9"
set "MAVEN_HOME=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%"
set "MAVEN_EXE=%MAVEN_HOME%\bin\mvn.cmd"
set "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip"
set "DIST_ZIP=%WRAPPER_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip"

if not exist "%MAVEN_EXE%" (
  echo Maven %MAVEN_VERSION% not found locally. Downloading...
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"

  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DIST_URL%' -OutFile '%DIST_ZIP%'"
  if errorlevel 1 (
    echo Failed to download Maven distribution.
    exit /b 1
  )

  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%DIST_ZIP%' -DestinationPath '%WRAPPER_DIR%' -Force"
  if errorlevel 1 (
    echo Failed to extract Maven distribution.
    exit /b 1
  )
)

call "%MAVEN_EXE%" %*
exit /b %errorlevel%
