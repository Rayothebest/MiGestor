@echo off
setlocal

if "%CATALINA_HOME%"=="" if exist "%~dp0tools\apache-tomcat-10.1.61\bin\shutdown.bat" set "CATALINA_HOME=%~dp0tools\apache-tomcat-10.1.61"

if "%CATALINA_HOME%"=="" (
  echo ERROR: No se encontro Tomcat 10.1. Define CATALINA_HOME.
  exit /b 1
)

if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
  set "JRE_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
)

call "%CATALINA_HOME%\bin\shutdown.bat"
endlocal
