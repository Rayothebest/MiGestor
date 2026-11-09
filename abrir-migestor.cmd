@echo off
call "%~dp0run-dev.cmd"
if errorlevel 1 (
  echo.
  echo MiGestor no pudo iniciarse. Lee el error anterior para corregirlo.
  pause
  exit /b 1
)

start "" "http://localhost:8080/migestor/"
