# Carrousel App

App Android para clientes de Carrousel, con su servidor y un panel web para el personal.

- **Clientes (APK):** entran con su correo y un código de 6 dígitos, sin contraseña. Ven el cupón de cumpleaños de cada festejado (ellos o sus hijos) con QR y código, obtienen cupones de ofertas y consultan parques y eventos.
- **Personal (panel web):** el cajero escribe el código del cliente y confirma el canje. El parque del canje sale de su cuenta y cada cupón se canjea una sola vez en toda la red. Administración configura el regalo de cumpleaños, las ofertas, los eventos, los parques y las cuentas.

Es un proyecto independiente, con su propia base `carrousel_app`. No usa ni modifica CarrouselCumple. Las funciones se tomaron como referencia de la app UNO PLUS, sin usar su código, su marca ni sus servidores.

## Estructura

| Carpeta | Contenido |
| --- | --- |
| `server/app` | Lógica en PHP: cuentas y códigos (`Customers`), cupones (`Coupons`), parques, ofertas y eventos (`Content`) y personal (`Staff`). |
| `server/public` | `api.php` (app), `staff-api.php` (panel) y `admin/` (panel web). |
| `server/database/schema.sql` | Esquema de la base. Se puede aplicar varias veces sin problema. |
| `server/tests/api.php` | Prueba de punta a punta por HTTP (35 casos) contra `carrousel_app_test`. |
| `android/` | Proyecto de Android Studio (Kotlin + Jetpack Compose). |

Apache solo publica `api.php`, `staff-api.php` y `admin/`. El resto (`server/`, `android/`, configuración) responde 403.

## Servidor (XAMPP)

Ya quedó instalado en esta PC: dependencias, usuario de MariaDB `carrousel_app` (solo con permisos sobre `carrousel_app` y `carrousel_app_test`), `server/config/local.php` y el esquema. En otra PC:

```powershell
cd C:\xampp\htdocs\CarrouselApp\server
composer install --no-dev
Copy-Item config\local.php.example config\local.php   # completa base de datos y SMTP
C:\xampp\php\php.exe bin\install.php                  # genera app_key y crea el esquema
```

Crea la primera cuenta de administración. La contraseña se genera y se muestra una sola vez:

```powershell
C:\xampp\php\php.exe bin\create-staff.php "Tu nombre" tu-correo@carrousel.com.gt admin
```

Abre el panel en <http://localhost/CarrouselApp/admin/> y en este orden:

1. **Parques:** agrega los parques con dirección, horario y coordenadas (opcional, para "Cómo llegar").
2. **Personal:** asigna un parque a tu cuenta para poder canjear y crea los cajeros de cada parque.
3. **Regalo de cumpleaños:** escribe el regalo y sus condiciones. Mientras esté vacío no se emiten cupones de cumpleaños.
4. **Ofertas** y **Eventos:** opcionales.

**Correo:** en `local.php` de esta PC está `dev_outbox => true`. Sin SMTP, el código de acceso se escribe en `server\storage\dev-outbox.json` en lugar de enviarse, para que puedas probar. En producción configura `smtp_*` y deja `dev_outbox` en `false`.

**Pruebas:** `C:\xampp\php\php.exe server\tests\api.php` (vacía y usa solo `carrousel_app_test`).

## App Android

**Compilar sin Android Studio:** esta PC ya tiene JDK 17 (Temurin), Android SDK 35 (`%LOCALAPPDATA%\Android\Sdk`) y el wrapper de Gradle. Doble clic en `android\COMPILAR_APK.bat`. El APK queda en `android\app\build\outputs\apk\debug\app-debug.apk`.

**Con Android Studio** (para editar, usar el emulador o firmar):

1. Instala [Android Studio](https://developer.android.com/studio). Trae el JDK y el SDK.
2. **File → Open** y elige la carpeta `C:\xampp\htdocs\CarrouselApp\android`. Acepta sincronizar Gradle (descarga Gradle 8.11.1 y las librerías). Si ofrece actualizar el plugin de Android, puedes aceptar.
3. Crea un emulador (**Device Manager**) y pulsa **Run ▶**. En el emulador la app llega a tu XAMPP por `http://10.0.2.2/CarrouselApp/api.php`.
4. Entra con cualquier correo y copia el código de `server\storage\dev-outbox.json`.

**Celular real:** en `android/gradle.properties` cambia `carrouselApiUrlDebug` por la IP de tu PC (por ejemplo `http://192.168.1.50/CarrouselApp/api.php`), agrega esa IP a `allowed_hosts` en `server/config/local.php` y permite Apache en el Firewall de Windows.

**Generar el APK:** **Build → Generate Signed App Bundle / APK → APK**. Crea una llave nueva y guárdala bien: sin ella no podrás publicar actualizaciones. Antes del APK de producción, pon el dominio HTTPS real en `carrouselApiUrlRelease`. La versión release solo acepta HTTPS.

## Diseño

App y panel usan el diseño del Ecosistema Carrousel, el mismo de CarrouselCumple (`ecosystem-ui.css`): azul `#213a8f`, fondo `#eef2f7`, franja con degradado azul, violeta, rosa, naranja y amarillo, esquinas de 9 y 14 px y modo oscuro. En la app los colores están en `android/.../ui/Theme.kt` y en el panel en `server/public/admin/admin.css`. El logo oficial está en `res/drawable-nodpi/logo_carrousel.png` y `admin/logo-carrousel.png`, y el ícono de la app sale de ese logo (`mipmap-xxxhdpi/ic_launcher_foreground.png`).

## Publicar en el servidor (portal.carrousel-apps.com)

El servidor ya tiene XAMPP con PHP 8.2 y un túnel de Cloudflare con HTTPS, como CarrouselCumple. La app queda en `https://portal.carrousel-apps.com/CarrouselApp/` y el APK final (`assembleRelease`) ya apunta ahí.

En el servidor, en CMD como administrador:

1. Copia la carpeta a `C:\xampp\htdocs\CarrouselApp`, con `git clone` del repositorio o copiándola, **sin** `server\config\local.php`, `server\storage` ni `android\keystore.properties`.
2. Crea el usuario de MariaDB con una contraseña nueva (no la de PC TEST):
   ```
   C:\xampp\mysql\bin\mysql.exe -u root -p -e "CREATE USER 'carrousel_app'@'localhost' IDENTIFIED BY 'CONTRASEÑA-NUEVA'; GRANT ALL ON carrousel_app.* TO 'carrousel_app'@'localhost';"
   ```
3. Configura e instala:
   ```
   cd C:\xampp\htdocs\CarrouselApp\server
   composer install --no-dev
   copy config\local.php.example config\local.php
   notepad config\local.php
   C:\xampp\php\php.exe bin\install.php
   C:\xampp\php\php.exe bin\create-staff.php "Tu nombre" tu-correo admin
   ```
   En `local.php`: `db_pass`, `'allowed_hosts'=>['portal.carrousel-apps.com']`, los datos `smtp_*` y `mail_from`, y `dev_outbox` en `false`.
4. Comprueba que <https://portal.carrousel-apps.com/CarrouselApp/api.php?action=parks> responda `{"ok":true,...}` y que `https://portal.carrousel-apps.com/CarrouselApp/server/` responda 403.

**Detrás de Cloudflare:** `trusted_proxies` (por defecto `127.0.0.1`) es la IP desde la que se conecta el túnel. Solo desde ahí se aceptan `CF-Connecting-IP`, para el límite de intentos por cliente, y `X-Forwarded-Proto`, para la cookie segura del panel. Si el túnel corre en otra máquina, pon su IP.

**APK final:** `android\app\build\outputs\apk\release\app-release.apk`, firmado con la llave de `Documentos\Carrousel-firma-app` (respáldala; sin ella no hay actualizaciones). Para generarlo: `gradlew.bat assembleRelease`. Antes de cada versión nueva, sube `versionCode` en `android/app/build.gradle.kts`.

## Reglas

- **Cuenta:** un correo verificado con código. Puede tener hasta 8 festejados. La fecha de nacimiento no se edita desde la app, porque el cupón depende de ella; si hay un error, se corrige en la base.
- **Cupón de cumpleaños:** uno por festejado y año, con código aleatorio de 6 caracteres (A-Z, 0-9) único. Vale de *días antes* a *días después* del cumpleaños (por defecto 15 y 14, ambos incluidos). Copia el regalo y la vigencia al emitirse: cambiar la configuración no modifica los ya emitidos. El 29 de febrero se celebra el 28 en años no bisiestos.
- **Cupón de oferta:** uno por cuenta, vigente hasta el fin de la oferta.
- **Canje:** atómico y único. El servidor rechaza un cupón anulado, ya canjeado, vencido o que aún no es vigente. Administración puede anular un cupón sin canjear indicando el motivo. Todo queda en la tabla `audit`.
- **Seguridad:** el código de acceso vence en 10 minutos y admite 5 intentos. Los intentos tienen límite por IP y por correo. El servidor guarda solo el hash del código y el HMAC del token de la app; el token vence a los 90 días y se revoca al cerrar sesión. El panel usa una sesión con cookie HttpOnly y token CSRF.

## Pendiente

- Lector de QR en el panel (hoy el cajero escribe el código).
- Notificaciones push, puntos y premios: se definirán con negocio.
