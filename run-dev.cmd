@echo off
setlocal

rem Usa siempre el Tomcat 10 incluido en el proyecto. El Tomcat 8 de XAMPP
rem no puede ejecutar esta aplicacion Jakarta.
set "CATALINA_HOME=%~dp0tools\apache-tomcat-10.1.61"
set "CATALINA_BASE=%CATALINA_HOME%"

if not exist "%CATALINA_HOME%\bin\startup.bat" (
  echo ERROR: No se encontro el Tomcat 10 incluido en "%CATALINA_HOME%".
  exit /b 1
)

rem La variable JAVA_HOME global de esta PC tiene una barra final que impide
rem iniciar Tomcat. Para desarrollo usamos el JDK 21 ya instalado.
if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
  set "JRE_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
)

rem Detecta conflictos ANTES de compilar o copiar el WAR. El puerto 8005
rem tambien debe quedar libre para que Tomcat pueda iniciarse.
powershell.exe -NoProfile -Command ^
  "$http = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue);" ^
  "if ($http.Count -gt 0) {" ^
  "  foreach ($listener in $http) {" ^
  "    $owner = Get-CimInstance Win32_Process -Filter ('ProcessId=' + $listener.OwningProcess) -ErrorAction SilentlyContinue;" ^
  "    if ($owner.CommandLine -and $owner.CommandLine.IndexOf($env:CATALINA_HOME, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) { exit 0 }" ^
  "  };" ^
  "  Write-Host ('ERROR: El puerto 8080 esta ocupado por otro programa (PID ' + $http[0].OwningProcess + ').');" ^
  "  Write-Host 'Si XAMPP tiene Tomcat encendido, deten Tomcat en XAMPP y vuelve a intentar.'; exit 1" ^
  "};" ^
  "$shutdown = @(Get-NetTCPConnection -LocalPort 8005 -State Listen -ErrorAction SilentlyContinue);" ^
  "if ($shutdown.Count -gt 0) { Write-Host 'ERROR: El puerto 8005 tambien esta ocupado. Deten el otro Tomcat antes de continuar.'; exit 1 };" ^
  "exit 2"
set "PORT_STATUS=%ERRORLEVEL%"
if not "%PORT_STATUS%"=="0" if not "%PORT_STATUS%"=="2" exit /b 1

rem En esta instalación local, inicia la base recuperada con sus datos propios.
rem En proyectos nuevos sin local-db se sigue usando MySQL de XAMPP.
if exist "%~dp0local-db\my.ini" (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-local-db.ps1"
  if errorlevel 1 exit /b 1
)

call "%~dp0mvnw.cmd" clean package
if errorlevel 1 exit /b 1

rem Si el Tomcat del proyecto ya corre, detenerlo antes de reemplazar el WAR.
rem Copiarlo en caliente podia anunciar "listo" justo antes de un 404 temporal.
if not "%PORT_STATUS%"=="0" goto copy_war
echo Reiniciando el Tomcat 10 del proyecto...
call "%CATALINA_HOME%\bin\shutdown.bat"
if errorlevel 1 exit /b 1
powershell.exe -NoProfile -Command ^
  "$deadline = (Get-Date).AddSeconds(30);" ^
  "while ((Get-Date) -lt $deadline) {" ^
  "  if (-not (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)) { exit 0 };" ^
  "  Start-Sleep -Milliseconds 500" ^
  "};" ^
  "Write-Host 'ERROR: Tomcat 10 no libero el puerto 8080 en 30 segundos.'; exit 1"
if errorlevel 1 exit /b 1

:copy_war

copy /Y "%~dp0target\migestor.war" "%CATALINA_HOME%\webapps\migestor.war" >nul
if errorlevel 1 (
  echo ERROR: No fue posible copiar el WAR a Tomcat.
  exit /b 1
)

call "%CATALINA_HOME%\bin\startup.bat"
if errorlevel 1 exit /b 1

rem startup.bat termina antes de que la aplicacion este lista. Solo anunciamos
rem la URL cuando responde el proyecto, no una pagina 404 de otro Tomcat.
powershell.exe -NoProfile -Command ^
  "$url = 'http://127.0.0.1:8080/migestor/';" ^
  "$deadline = (Get-Date).AddSeconds(60);" ^
  "do {" ^
  "  try {" ^
  "    $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3;" ^
  "    if ($response.StatusCode -eq 200 -and $response.Content -like '*MiGestor*') { exit 0 }" ^
  "  } catch {};" ^
  "  Start-Sleep -Seconds 2" ^
  "} while ((Get-Date) -lt $deadline);" ^
  "Write-Host 'ERROR: Tomcat no entrego MiGestor en 60 segundos.';" ^
  "Write-Host ('Revisa los registros en ' + $env:CATALINA_HOME + '\logs'); exit 1"
if errorlevel 1 exit /b 1

echo.
echo MiGestor esta listo: http://localhost:8080/migestor/
exit /b 0
