@echo off
rem Compila el APK de prueba (debug) con el JDK 17 y el Android SDK instalados en esta PC.
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo.
  echo No se pudo compilar. Revisa los mensajes de arriba.
) else (
  echo.
  echo APK listo: %~dp0app\build\outputs\apk\debug\app-debug.apk
  explorer "%~dp0app\build\outputs\apk\debug"
)
pause
