@echo off
setlocal
cd /d "%~dp0"

rem Instalacion compartida: importa database\schema.sql en tu propia base,
rem configura DB_URL, DB_USER y DB_PASSWORD, y define CATALINA_HOME con la
rem ruta de Tomcat 10.1 antes de ejecutar este archivo. No usa datos locales.
if not defined CATALINA_HOME (
  echo ERROR: Define CATALINA_HOME con la ruta de tu Tomcat 10.1.
  exit /b 1
)
if not defined CATALINA_BASE set "CATALINA_BASE=%CATALINA_HOME%"
if not exist "%CATALINA_HOME%\bin\startup.bat" (
  echo ERROR: No se encontro startup.bat en "%CATALINA_HOME%\bin".
  exit /b 1
)
if not exist "%CATALINA_BASE%\webapps" (
  echo ERROR: No se encontro webapps en "%CATALINA_BASE%".
  exit /b 1
)
if not defined DB_URL (
  echo ERROR: Define DB_URL para conectar tu propia base de datos.
  exit /b 1
)
if not defined DB_USER (
  echo ERROR: Define DB_USER para conectar tu propia base de datos.
  exit /b 1
)
if not defined DB_PASSWORD (
  echo ERROR: Define DB_PASSWORD para conectar tu propia base de datos.
  exit /b 1
)

rem Deten el Tomcat que ya use estos puertos antes de reemplazar el WAR.
powershell.exe -NoProfile -Command ^
  "$busy = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in @(8080,8005) });" ^
  "if ($busy.Count -gt 0) { Write-Host 'ERROR: Los puertos 8080 u 8005 ya estan ocupados. Deten el otro Tomcat e intenta de nuevo.'; exit 1 }"
if errorlevel 1 exit /b 1

call "%~dp0mvnw.cmd" clean package
if errorlevel 1 exit /b 1

copy /Y "%~dp0target\migestor.war" "%CATALINA_BASE%\webapps\migestor.war" >nul
if errorlevel 1 (
  echo ERROR: No se pudo copiar migestor.war a "%CATALINA_BASE%\webapps".
  exit /b 1
)

call "%CATALINA_HOME%\bin\startup.bat"
if errorlevel 1 exit /b 1

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
  "Write-Host 'ERROR: MiGestor no respondio en 60 segundos. Revisa los registros de Tomcat.'; exit 1"
if errorlevel 1 exit /b 1

echo MiGestor esta listo: http://localhost:8080/migestor/
start "" "http://localhost:8080/migestor/"
exit /b 0
