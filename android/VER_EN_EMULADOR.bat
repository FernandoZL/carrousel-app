@echo off
rem Abre el celular virtual, instala el APK de prueba y abre la app. XAMPP (Apache y MySQL) debe estar encendido.
setlocal
set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo Primero compila con COMPILAR_APK.bat
  pause
  exit /b 1
)
start "" "%SDK%\emulator\emulator.exe" -avd Carrousel_Pixel7 -no-snapshot-save
echo Encendiendo el celular virtual...
"%SDK%\platform-tools\adb.exe" wait-for-device
:esperar
for /f %%b in ('"%SDK%\platform-tools\adb.exe" shell getprop sys.boot_completed 2^>nul') do if "%%b"=="1" goto listo
timeout /t 3 /nobreak >nul
goto esperar
:listo
"%SDK%\platform-tools\adb.exe" install -r "%APK%"
"%SDK%\platform-tools\adb.exe" shell am start -n com.carrousel.clientes.debug/com.carrousel.clientes.MainActivity
echo.
echo Listo. El codigo de acceso aparece en server\storage\dev-outbox.json
pause
