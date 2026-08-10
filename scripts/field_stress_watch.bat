@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
  echo [ERROR] No se encontro adb en:
  echo %ADB%
  exit /b 1
)

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value 2^>nul ^| find "="') do set "DT=%%I"
if not defined DT set "DT=unknown"
set "STAMP=%DT:~0,8%_%DT:~8,6%"
set "OUT=field_stress_%STAMP%.log"

echo =============================================================
echo Afora 3.3.5B - Long-running Tracking Stress Test
echo Evidencia: %OUT%
echo.
echo Mantenga esta ventana abierta durante la prueba.
echo Ctrl+C detiene la captura de logs, no el tracking de la app.
echo =============================================================
echo.

"%ADB%" devices

echo.
echo Limpiando buffer logcat para iniciar evidencia limpia...
"%ADB%" logcat -c

echo Iniciando captura...
echo.

"%ADB%" logcat -v time ^| findstr /I /C:"FieldReadiness" /C:"PREFLIGHT_" /C:"TrackingRecovery" /C:"WATCHDOG_" /C:"TripTelemetry" /C:"TELEMETRY_" /C:"CloudSyncIntegrity" /C:"SYNC_" /C:"DataIntegrity" /C:"DATA_AUDIT" /C:"FirestorePermission" /C:"FIRESTORE_PERMISSION" > "%OUT%"

echo.
echo Captura finalizada.
echo Archivo: %CD%\%OUT%
endlocal
