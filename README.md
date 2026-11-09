# MiGestor

MVP web no-code para pequeños negocios construido con Java 17, Maven, Jakarta Servlets/JSP, Tomcat 10.1 y MySQL/MariaDB. Cada registro queda aislado por negocio.

## Funciones implementadas

- Registro, inicio/cierre de sesión y contraseñas BCrypt.
- Panel principal fijo con métricas y alertas actuales.
- Módulos configurables de tipo **Tabla** y **Panel**.
- ID automático e ineditable para cada fila.
- Columnas de texto, número, fecha, sí/no, lista, relación, dato heredado y fórmula.
- Campos obligatorios, valores predeterminados y umbrales de existencia baja.
- Relaciones entre tablas y valores heredados a partir de esas relaciones.
- Fórmulas aritméticas seguras con referencias como `{12} * {18}`.
- Paneles de registro con campos visibles, obligatorios, orden y valores predeterminados configurables.
- Paneles de existencias para sumar, restar o establecer una cantidad sin eliminar el artículo.
- Operaciones de existencias transaccionales, protección contra doble envío e historial de movimientos.
- Ventas completas de varios productos: cantidades, precio unitario editable, forma de pago, total calculado en el servidor, descuento de existencias e historial en una sola transacción.
- Devoluciones parciales o completas de ventas: reponen existencias, registran el importe y conservan el historial sin alterar la venta original.
- Caja y gastos: turnos con fondo inicial, ingresos y gastos manuales, ventas y devoluciones registradas automáticamente, cierre con efectivo contado y diferencia.
- Códigos de barras únicos y fotos JPEG/PNG para los productos del catálogo de Ventas. Búsqueda exacta por código en una venta, compatible con entrada manual o lector USB; cámara opcional en navegadores compatibles.
- Flujos configurables para crear una fila o ajustar existencias cuando se crea un registro, una venta completa o una devolución.
- Búsqueda, filtros, orden, vistas personales guardadas y ocultamiento local de columnas.
- Importación transaccional de CSV/XLSX y exportación a CSV/XLSX.
- Exportación ZIP de las tablas del negocio, ventas, devoluciones, Caja, códigos y fotos de productos para el propietario. Para recuperar toda la instalación, conserva también un respaldo SQL de MySQL.
- Reportes de conteo, totales, promedios, mínimos, máximos y distribuciones agrupadas. Los reportes guardados como gráfica dibujan barras o círculos; la línea se usa al agrupar por una columna Fecha. Cada gráfica incluye las cifras en una tabla desplegable.
- Reglas de alerta sobre números, fechas y valores, con avisos internos programables cada 15 minutos, cada hora o cada día. Los avisos se pueden marcar como vistos y dejan de mostrarse cuando ya no se cumple la condición.
- Usuarios con roles Propietario, Gerente y Empleado; permisos por módulo para ver, crear, editar, eliminar y configurar.
- Historial de creación, edición, eliminación, importación y ajustes de existencias.
- Edición y eliminación de módulos; una Tabla vinculada queda protegida hasta eliminar o cambiar sus Paneles.

## Lo que esta versión no hace

- Los avisos programados aparecen solo dentro de MiGestor. No se envían por correo ni SMS; el servidor Tomcat y MySQL deben estar encendidos para revisarlos. La frecuencia es aproximada, con comprobaciones del servidor cada minuto.
- No hay recuperación de contraseña, invitaciones por correo, archivos adjuntos generales ni edición simultánea en tiempo real. Las fotos son solo para productos del catálogo de Ventas.
- Los reportes y gráficas se consultan dentro de MiGestor; no generan PDF ni se envían automáticamente.
- La forma de pago o reembolso se registra, pero MiGestor no cobra tarjetas, devuelve dinero por un proveedor de pagos ni emite facturas. Las ventas y devoluciones guardan su propio historial; solo generan filas en una tabla personalizada si configuras un flujo para hacerlo.
- Caja es un registro interno: no verifica saldos bancarios ni confirma que un pago o reembolso externo se haya realizado.
- Los flujos no reaccionan a ediciones, importaciones, ajustes de existencias ni eliminaciones. No envían mensajes ni llaman servicios externos.
- Antes de exponerla a Internet deben añadirse protección CSRF, HTTPS, límites de intentos, gestión externa de secretos y una política de sesión más estricta.

## Requisitos

- JDK 17 o posterior.
- Tomcat 10.1. No uses Tomcat 8.5 de XAMPP: no es compatible con `jakarta.servlet`.
- MySQL 8 o MariaDB 10.4 o posterior. XAMPP es opcional.
- Maven no es obligatorio: el proyecto incluye Maven Wrapper.

## Instalar desde GitHub en otra computadora

El repositorio contiene el código, el esquema SQL y un script opcional para generar un modelo de demostración. **No incluye cuentas, registros de la base, contraseñas, respaldos, la base local ni Tomcat**. Cada compañero debe crear su propia base y cuenta. La carpeta `tools/` y los datos de `local-db/` del equipo original están excluidos de Git.

1. Clona el repositorio o descarga su ZIP. Instala JDK 17 o posterior, MySQL/MariaDB y **Tomcat 10.1**; Tomcat 8.5 de XAMPP no ejecuta esta aplicación Jakarta.
2. Inicia MySQL/MariaDB e importa `database/schema.sql` desde phpMyAdmin. El archivo crea la base `gestor_no_code` y todas las tablas de la versión actual. **En una instalación nueva no ejecutes además las migraciones.**
3. Configura la conexión de Tomcat a la base. Los valores de ejemplo en `src/main/resources/application.properties` usan `root` sin contraseña para pruebas locales; en tu equipo utiliza tu usuario y contraseña. Puedes definir `DB_URL`, `DB_USER`, `DB_PASSWORD` y `DB_POOL_SIZE` como variables de entorno del proceso Tomcat, sin guardar contraseñas en Git. La URL debe apuntar a `gestor_no_code` en tu servidor MySQL.
4. En la carpeta que contiene `pom.xml`, ejecuta `mvnw.cmd clean package` en Windows (o `./mvnw clean package` en macOS/Linux). La primera ejecución necesita Internet para descargar Maven y las dependencias.
5. Copia `target/migestor.war` a la carpeta `webapps` de **Tomcat 10.1** e inicia ese Tomcat. Abre [http://localhost:8080/migestor/](http://localhost:8080/migestor/) y registra la primera cuenta del negocio.

Si Tomcat usa otro puerto, cambia `8080` en la dirección. El navegador por sí solo no inicia los servidores. Para volver a desplegar cambios, recompila, reemplaza el WAR y reinicia Tomcat. `run-dev.cmd` y `abrir-migestor.cmd` esperan Tomcat en `tools/apache-tomcat-10.1.61`; en una copia recién descargada esa carpeta no existe.

### Lanzador para compañeros en Windows

`ejecutar-en-tomcat.cmd` compila, despliega y abre MiGestor con un Tomcat 10.1 instalado por ti. No usa la base ni los respaldos del equipo original. Antes de ejecutarlo, importa `database/schema.sql`, crea un usuario de MySQL con contraseña y acceso a `gestor_no_code`, y define las variables de entorno en una terminal PowerShell. Sustituye las rutas y el usuario por los de tu computadora:

```powershell
$env:JAVA_HOME = 'C:\ruta\al\jdk-17'
$env:CATALINA_HOME = 'C:\ruta\al\apache-tomcat-10.1'
$env:DB_URL = 'jdbc:mysql://localhost:3306/gestor_no_code?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8'
$env:DB_USER = 'migestor'
$env:DB_PASSWORD = Read-Host 'Contraseña de MySQL'
.\ejecutar-en-tomcat.cmd
```

El lanzador necesita una contraseña de base no vacía y los puertos 8080 y 8005 libres. Para volver a ejecutarlo, detén primero ese Tomcat con `bin/shutdown.bat` de tu instalación. Las variables definidas así solo duran en esa terminal; no guardes su contraseña en archivos del repositorio. También puedes definirlas con `set "NOMBRE=valor"` en CMD antes de llamar al archivo. En VS Code, tras definir las variables para el proceso de VS Code, puedes usar **Terminal > Run Task > MiGestor: desplegar en Tomcat externo**.

## Base de datos

Para una instalación nueva, inicia MySQL y ejecuta [database/schema.sql](database/schema.sql) desde phpMyAdmin.

Si ya tenías la base de una versión anterior, ejecuta una sola vez y en orden:

1. `database/migrations/002_module_types.sql`
2. `database/migrations/003_panel_actions.sql`
3. `database/migrations/004_business_tools.sql`
4. `database/migrations/005_complete_sales.sql`
5. `database/migrations/006_workflows.sql`
6. `database/migrations/007_sales_returns.sql`
7. `database/migrations/008_cash_register.sql`
8. `database/migrations/009_product_assets.sql`
9. `database/migrations/010_scheduled_alerts.sql`

En el equipo de desarrollo original hay una base recuperada en `local-db/data`. `run-dev.cmd` y `abrir-migestor.cmd` la inician automáticamente si el puerto 3306 está libre. No inicies MySQL por separado desde XAMPP en ese equipo: ocuparía el mismo puerto. `local-db/` y `backups/` contienen datos privados y están excluidos de Git. **Quienes clonen el repositorio deben seguir la instalación nueva anterior.**

La conexión predeterminada es:

```text
jdbc:mysql://localhost:3306/gestor_no_code
usuario: root
contraseña: vacía
```

Se puede cambiar en `src/main/resources/application.properties` o con `DB_URL`, `DB_USER`, `DB_PASSWORD` y `DB_POOL_SIZE`.

## Ejecutar en el equipo de desarrollo original desde Visual Studio Code

1. En esta computadora, deja apagados **MySQL** y **Tomcat** en el panel de XAMPP. El proyecto inicia la base recuperada y el Tomcat 10 incluido.
2. Haz doble clic en `abrir-migestor.cmd` desde la carpeta del proyecto. El archivo inicia la base, compila, inicia Tomcat, espera a que MiGestor responda y abre el navegador.

Debes ejecutar `abrir-migestor.cmd` nuevamente despues de reiniciar Windows. Escribir la direccion en el navegador no inicia el servidor por si solo. Si aparece un error de puerto 8080, detén **Tomcat** en el panel de XAMPP y vuelve a abrir el archivo. Si aparece un error de puerto 3306, detén **MySQL** en XAMPP para que el proyecto pueda iniciar `local-db/data`.

Si prefieres iniciarlo desde VS Code, abre la carpeta que contiene `pom.xml` y usa **Terminal > Run Task > MiGestor: desplegar en el equipo original**. Si VS Code lo solicita, instala **Extension Pack for Java** y **Maven for Java**. Cuando la tarea indique `MiGestor esta listo`, abre [http://localhost:8080/migestor/](http://localhost:8080/migestor/). Cada nuevo despliegue reinicia el Tomcat del proyecto y tendrás que iniciar sesión otra vez.

## Ejecutar desde IntelliJ IDEA

Ese equipo tiene Tomcat 10.1.61 instalado en `tools/apache-tomcat-10.1.61`. Para detenerlo usa la tarea **MiGestor: detener Tomcat 10**. Esa carpeta no se publica en GitHub.

## Alertas programadas y gráficas

En **Alertas**, un propietario o gerente crea una regla sobre una columna de una tabla. En cada tarjeta puede elegir **Sin programación**, **Cada 15 minutos**, **Cada hora** o **Una vez al día**, y usar **Comprobar ahora** para probarla. Las coincidencias generan avisos en la misma página. Cada combinación de regla y fila conserva un único aviso; marcarlo como visto no lo duplica en la siguiente revisión. Si el dato deja de coincidir, el aviso se resuelve; si vuelve a coincidir, aparece otra vez como nuevo. Pausar la regla o quitar la programación retira sus avisos activos. Los avisos solo se muestran a usuarios con permiso para ver la tabla. El proceso de fondo se inicia con Tomcat y revisa las reglas aproximadamente cada minuto; no se ejecuta con el servidor apagado.

En **Reportes**, selecciona una tabla y pulsa **Guardar reporte**. Para dibujar datos, elige **Gráfica**, el cálculo, una columna para agrupar y una columna numérica para suma, promedio, mínimo o máximo. El conteo no necesita columna numérica. Las gráficas de barras y circulares muestran hasta once grupos y reúnen el resto en **Otros**; las de línea requieren una columna **Fecha** y muestran hasta doce fechas. Si los valores de una circular son negativos, se muestra como barras. La tabla **Ver cifras de la gráfica** permite consultar los valores exactos mostrados. Las gráficas se recalculan con los datos actuales cada vez que abres Reportes.

## Ventas completas

1. En **Ventas**, el propietario o gerente elige una tabla de productos y las columnas de nombre, existencias y precio. Las dos últimas deben ser numéricas.
2. Quien registre ventas necesita permiso para ver la tabla y para editarla, o para usar un panel vinculado que descuente la misma columna de existencias.
3. Agrega uno o más productos, indica cantidades y elige la forma de pago. El precio viene del catálogo; solo el propietario o gerente puede cambiarlo para una venta. El total en pantalla es una vista previa; el servidor lo calcula de nuevo al guardar.
4. Si falta existencia en cualquier producto, no se guarda la venta ni se modifica ningún artículo. El historial conserva el nombre, precio y existencias de cada renglón aunque el producto cambie después.

La sección **Ventas completas** usa un registro propio. No añade filas a `[DEMO] Ventas` ni mezcla sus importes con los reportes guardados de esa tabla.

### Códigos y fotos de productos

El propietario o gerente abre **Códigos y fotos** desde el menú o Ventas. La pantalla usa la tabla configurada como catálogo de Ventas: cada fila puede tener un código de hasta 64 caracteres ASCII sin espacios y una foto JPEG o PNG de hasta 4 MB. Los códigos se normalizan a mayúsculas y son únicos dentro de esa tabla. Dejar el campo vacío quita el código; también se puede cambiar o quitar la foto. Las fotos se reducen a un máximo de 1200 píxeles por lado, se convierten a JPEG y se guardan en MySQL. Ver una foto requiere permiso para ver la tabla del producto.

En **Nueva venta**, escribe el código exacto o usa un lector USB que escriba en el campo y pulse Enter. El producto se agrega a la venta; si ya está seleccionado, su cantidad aumenta en una unidad. La cámara aparece solo si el navegador dispone de `BarcodeDetector` y permite acceso a la cámara; pide permiso al usarla. La entrada manual o el lector USB siguen disponibles. Los códigos y fotos no reemplazan los datos de la tabla ni cambian sus existencias por sí solos. Al cambiar la tabla configurada para Ventas, los datos anteriores permanecen vinculados a sus filas originales y la pantalla administra la nueva tabla. El ZIP del propietario incluye `productos/codigos.csv` y las fotos en `productos/fotos/`.

La cuenta local de demostración tiene el código interno `DEMO-AGUA-600` y una foto de ejemplo en «Agua mineral 600 ml» para probar la búsqueda y la vista de productos sin registrar una venta. El archivo de origen está en `scripts/demo-agua-mineral-600ml.png`; es una imagen generada y no representa una marca comercial ni un producto real.

### Devoluciones

Un propietario o gerente abre el detalle de una venta y elige las cantidades que se devolvieron. Puede registrar varias devoluciones parciales hasta completar lo vendido. MiGestor calcula el importe desde el precio original, repone cada producto en la columna de existencias que usó esa venta y guarda el movimiento, la persona y la nota. Si falta el producto o su columna original, si la cantidad supera lo vendido o si falla un flujo vinculado, no se guarda ninguna parte de la devolución. El total de la venta original permanece intacto; el detalle muestra el importe devuelto y el neto registrado. El método de reembolso es solo un dato interno: el dinero debe entregarse fuera de MiGestor.

## Caja y gastos

Un propietario o gerente abre **Caja** (`/app/cash`) e indica el fondo inicial en efectivo. Durante el turno puede registrar ingresos o gastos manuales con categoría, importe, método y nota. Cada venta completa y devolución registrada en **Ventas** crea además un movimiento de Caja dentro de la misma transacción; si falla el guardado, no queda ninguna de las dos operaciones a medias.

El **efectivo esperado** es el fondo inicial más ventas e ingresos manuales en efectivo, menos devoluciones y gastos manuales en efectivo. Tarjeta, transferencia y otros métodos aparecen en el historial, pero no cambian ese cálculo. Al cerrar, se anota el efectivo contado y se guarda la diferencia; un turno cerrado no se recalcula con operaciones posteriores. Solo puede haber un turno abierto por negocio.

También se permiten operaciones sin turno abierto: se etiquetan **Fuera de turno** y no entran en cierres futuros. La migración 008 conserva las ventas y devoluciones anteriores a Caja de esta misma forma. El ZIP del propietario incluye `caja/turnos.csv` y `caja/movimientos.csv`. Los registros de la tabla personalizada `[DEMO] Ventas` siguen siendo independientes de las ventas completas y no crean movimientos de Caja.

## Flujos

Un propietario o gerente abre **Flujos** (`/app/workflows`) para crear, editar, pausar, activar o eliminar reglas de su negocio. Cada regla tiene un disparador y una acción. Al guardar una fila desde una Tabla o un Panel de registro, al completar una venta o al registrar una devolución, la acción se ejecuta dentro de la misma transacción: si falla, tampoco se guarda el evento que la inició.

| Disparador | Acción | Configuración |
| --- | --- | --- |
| Fila nueva en una Tabla, creada directamente o desde un Panel de registro | Ajustar existencias | Elige la columna **Relación** que apunta al producto, la columna **Número** con la cantidad, la tabla de productos, su columna **Número** de existencias y si deseas sumar o restar. Puedes exigir que otra columna del registro nuevo tenga un valor exacto. |
| Fila nueva en una Tabla, creada directamente o desde un Panel de registro | Crear fila en otra Tabla | Asigna las columnas de destino desde columnas del registro original o desde valores fijos. |
| Venta completa registrada en **Ventas** | Crear fila en una Tabla | Asigna las columnas de destino desde el ID de la venta, su total, la forma de pago o valores fijos. La forma de pago se guarda con los códigos `CASH`, `CARD`, `TRANSFER` y `OTHER`. |
| Devolución registrada en una venta | Crear fila en una Tabla | Asigna columnas de destino desde el ID de la devolución, el ID de la venta, el importe devuelto, el método de reembolso o valores fijos. Las existencias ya se reponen en la operación original. |

Por ejemplo, un flujo sobre `[DEMO] Compras` puede sumar `Cantidad` a `Existencias` del `Producto` relacionado solo cuando la fila nueva tenga `Estado = Recibida`. El cambio de estado posterior no dispara ese flujo; para ese caso, crea la fila con `Recibida` cuando llega la mercancía. Las reglas nuevas pueden permanecer pausadas hasta que compruebes su configuración.

Los flujos creados por una regla no disparan otras reglas. Tampoco se ejecutan por una importación CSV/XLSX, un ajuste desde un Panel de existencias o la edición de una fila. Una venta ya descuenta existencias y una devolución ya las repone, por lo que esos disparadores solo permiten **Crear fila**. Las reglas se aplican a nuevos eventos; no procesan registros, ventas ni devoluciones anteriores.

## Modelo demostrativo

La cuenta local usada durante el desarrollo contiene un recorrido llamado **[DEMO] 00 - Guía de recorrido**. El modelo “Tienda La Esquina” incluye seis tablas, seis paneles, relaciones, lookups, fórmulas, vistas, reportes, alertas, movimientos de inventario y ejemplos de permisos.

Para sembrar el mismo modelo en otra cuenta propietaria, con Tomcat y MySQL activos, usa `scripts/seed-demo.ps1`. El script no guarda contraseñas, se detiene si ya existe contenido `[DEMO]` y no elimina módulos anteriores:

```powershell
.\scripts\seed-demo.ps1 `
  -OwnerEmail 'propietario@ejemplo.com' `
  -OwnerPassword '<contraseña actual>' `
  -ManagerPassword '<contraseña temporal del gerente demo>' `
  -CashierPassword '<contraseña temporal del empleado demo>'
```

Los dos usuarios creados por el sembrador quedan inactivos intencionalmente. El propietario puede revisar sus roles y permisos antes de activarlos. El antiguo panel `[DEMO] Caja - Registrar venta` sigue siendo un ejemplo independiente; usa **Ventas completas** para guardar una venta y descontar existencias en una sola operación.

En la base local quedó configurada la regla `[DEMO] Al recibir compra, sumar existencias`: toma `Producto` y `Cantidad` de `[DEMO] Compras` cuando `Estado = Recibida` y suma a `Existencias` del producto. Está **pausada** para que no cambie inventario hasta que el propietario la revise y active. El sembrador no crea esta regla en otras cuentas.

## Ejecutar desde IntelliJ IDEA

1. Abre esta carpeta como proyecto Maven y selecciona JDK 17 o posterior.
2. Si tu edición de IntelliJ admite servidores de aplicaciones, en **Settings > Build, Execution, Deployment > Application Servers** agrega tu instalación de Tomcat 10.1. En el equipo original está en `tools/apache-tomcat-10.1.61`.
3. Crea una configuración **Tomcat Server > Local**.
4. En **Deployment**, agrega el artefacto WAR exploded y usa el contexto `/migestor`.
5. En una instalación nueva, inicia tu MySQL/MariaDB con el esquema importado y configura la conexión como se indica arriba. En el equipo original, ejecuta primero `scripts/start-local-db.ps1` para iniciar la base recuperada. Luego inicia Tomcat y abre [http://localhost:8080/migestor/](http://localhost:8080/migestor/). Si tu edición de IntelliJ no ofrece esa configuración, compila el WAR y despliégalo manualmente como se indica en **Instalar desde GitHub en otra computadora**.

## Compilar y probar

En Windows:

```bat
mvnw.cmd clean verify
```

El WAR se genera en `target/migestor.war`.

## Estructura

```text
database/schema.sql                  Esquema completo para instalaciones nuevas
database/migrations                  Migraciones incrementales
database/verify_004.sql              Comprobaciones de integridad de la migración
scripts/seed-demo.ps1                Modelo demostrativo reproducible y verificable
scripts/start-local-db.ps1           Arranque de la base recuperada de esta computadora
local-db/data                         Datos activos locales; no publicar ni incluir en Git
src/main/java/com/gestor/model       Modelos del dominio
src/main/java/com/gestor/repository  SQL con aislamiento por negocio
src/main/java/com/gestor/service     Fórmulas, operaciones, reportes y transferencias
src/main/java/com/gestor/servlet     Rutas HTTP
src/main/webapp/WEB-INF/views        Pantallas JSP protegidas
src/test                             Pruebas automáticas
```

## Seguridad e integridad

- Consultas preparadas y comprobación de `business_id` en cada recurso.
- Permisos comprobados en servidor; ocultar un botón no concede acceso.
- Contraseñas BCrypt.
- JSP dentro de `WEB-INF`.
- Ajustes, importaciones, ventas completas, devoluciones, Caja y flujos dentro de transacciones.
- Token único para impedir que una venta, devolución o movimiento manual de Caja se procese dos veces.
- Fórmulas de hojas de cálculo neutralizadas al exportar CSV/XLSX.
