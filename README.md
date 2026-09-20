# SIBIM — Sistema Integral de Bienes Municipales

Aplicación de escritorio desarrollada en **Java 21 + JavaFX** para la gestión del inventario patrimonial del **H. Ayuntamiento de Huejutla, Hidalgo**.

---

## Características

- **Inventario de bienes** — registro completo con código, área, resguardante, stock, precios, foto y datos de depreciación; filtros combinables guardables como **presets de acceso rápido** (máx. 10, persistidos en `~/.sibim/presets-bienes.json`); importación masiva desde CSV
- **Depreciación en línea recta (SAT)** — cada bien puede tener fecha de adquisición, vida útil en años y valor residual; el sistema calcula automáticamente el valor depreciado actual y el porcentaje depreciado, visible en el detalle del bien con una barra de progreso codificada por color (verde / ámbar / rojo)
- **Movimientos** — entradas, salidas, ajustes y **transferencias reales entre áreas** (reasignan el bien, no solo restan stock), con historial, candado de concurrencia para evitar pérdida de datos entre usuarios simultáneos, y una vista previa animada "Área A → Área B" al elegir el destino
- **Flujo de aprobación de transferencias** — cuando un usuario no-Admin registra una transferencia, queda en estado **PENDIENTE** (sin mover stock ni área) hasta que un Admin la apruebe o rechace desde el panel "⏳ Pendientes" en Movimientos; el botón muestra un contador en tiempo real y cambia de color cuando hay solicitudes esperando
- **Baja patrimonial** — dar de baja un bien pide motivo y lo saca del inventario activo sin borrar su historial (soft-delete), con vista para consultar y reactivar bajas
- **Conteo físico de inventario** — captura lo contado contra el sistema, reconcilia las diferencias con movimientos de Ajuste auditados, y guarda cada sesión de conteo completa (incluyendo lo que sí coincidió) para revisión posterior; pide confirmación si se intenta cerrar con diferencias sin guardar; el usuario activo se captura en el hilo de UI antes del guardado en segundo plano para evitar lecturas fuera del hilo de JavaFX
- **Auditoría de cambios** — historial de quién creó/editó/eliminó/dio de baja/reactivó cada bien, categoría, usuario, resguardo, préstamo, acta o configuración; filtrable por entidad, tipo de acción y usuario (con accesos rápidos Hoy/Semana/Mes), con tarjetas de resumen (total, inicios de sesión, intentos fallidos, eliminaciones/bajas) y exportable a PDF, Excel y CSV — consultable desde el menú lateral (solo Admin)
- **Centro de notificaciones** — icono de campana en la barra de estado, visible en cualquier pantalla, con el conteo de bienes agotados, stock bajo, garantías por vencer y préstamos vencidos/por vencer; cada elemento navega directo a la pantalla correspondiente
- **Respaldo y restauración manual** — desde Configuración (solo Admin), exporta todas las tablas a un único archivo **cifrado con AES-256-GCM y una contraseña que tú eliges** (necesaria de nuevo para restaurar — no queda ligada a esta PC, para poder restaurar en otro equipo), o restaura la base de datos completa desde uno (reemplaza todo dentro de una sola transacción — si algo falla, no queda a medias). Solo disponible conectado a la base de datos real, no en modo offline/demo
- **Depreciación de activos** — 4 tarjetas (valor compra, valor actual, % promedio, totalmente depreciados); distribución del inventario en 4 rangos de depreciación (0–24 % / 25–49 % / 50–99 % / 100 %+) como barras animadas; columna de visualización con `ProgressBar` codificada por color en la tabla; gráfica de proyección del valor a 10 años; exportable a PDF / Excel / fichas técnicas en lote
- **Cambio de contraseña obligatorio** — cualquier cuenta con contraseña temporal conocida (cuentas semilla, o un usuario recién creado/restablecido por un Admin) es forzada a definir su propia contraseña en el primer login, antes de poder usar el sistema
- **Alertas** — resumen rápido con 3 tarjetas animadas (Agotados / Bajo Stock / Garantías) con barras de proporción; detalle de garantías vencidas vs. próximas; exportable a PDF, Excel y CSV directamente desde la pantalla de alertas (Ctrl+F para filtrar, atajos de teclado en todos los módulos)
- **Dashboard** — 4 mini-tarjetas de estado (Activos / Bajo Stock / Agotados / Vencidos) con `ProgressBar` codificada por color, gráfica de movimientos semanal, gráfica por categoría y barras de distribución de las 5 áreas con más bienes
- **Reportes** — exportación a PDF, Excel y CSV (inventario, movimientos, alertas, distribución por área); el encabezado de todos los documentos PDF y Excel usa el nombre del ayuntamiento configurado en Configuración
- **Organigrama** — 4 tarjetas de resumen (áreas, bienes distribuidos, área con más bienes, valor patrimonial total); barras horizontales animadas con las top-5 áreas; valor patrimonial y alertas de stock por área; salto directo al Inventario filtrado por esa área
- **Gestión de usuarios** — roles Admin, Secretario y Dirección con control de acceso por área; buscador en tiempo real; activar/desactivar cuentas (desactivar bloquea el acceso tanto online como en modo offline); la eliminación de un usuario con bienes/movimientos relacionados ofrece desactivar la cuenta como alternativa a borrar
- **Configuración institucional** — nombre del ayuntamiento, municipio, área responsable y correo de contacto editables desde Configuración (solo Admin); todos los reportes PDF/Excel usan automáticamente estos datos
- **Interfaz animada** — splash con progreso de carga y transiciones cross-fade; animaciones de entrada escalonadas en cada módulo; contadores animados de 0 al valor real; barra de salud con revelado izquierda→derecha; micro-animaciones de hover/press; animación de transferencia con flecha que se estira al disparar y chip de destino que entra desde la derecha con rebote; efecto shake en errores de validación
- **Aviso de inactividad** — alerta al usuario si permanece sin interacción durante un período prolongado
- **Notificaciones toast** en tiempo real
- **Recuperación de formularios** — si falla el guardado (BD caída, validación), el diálogo se reabre con los datos ya capturados en vez de perderlos
- **Accesibilidad** — texto accesible automático para lectores de pantalla en botones de solo-ícono (toma el texto del tooltip); tamaño de texto ajustable (Normal / Grande / Extra grande) y densidad de filas de tabla (Compacto / Normal / Cómodo), ambos en la barra de estado; interruptor para desactivar animaciones en equipos de gama baja (Configuración → Accesibilidad y rendimiento)
- **Columnas de tabla restaurables** — en las tablas con menú de columnas (Auditoría, Categorías, Movimientos, Depreciación, Alertas), un botón junto a "Actualizar" regresa el orden, ancho y visibilidad de las columnas a como estaban originalmente, sin tener que recordar qué se cambió

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| UI | JavaFX 21 + FXML + CSS + AtlantaFX |
| Base de datos principal | PostgreSQL (gestionado por Flyway) |
| Base de datos offline | SQLite 3.46 (`~/.sibim/offline.db`) |
| Conexión BD | HikariCP (pool 10 conexiones, 8 s timeout) |
| Reportes PDF | iText 7 |
| Reportes Excel | Apache POI |
| Cifrado de contraseñas | BCrypt (at.favre.lib, factor 12) |
| Cifrado de datos en reposo | AES-256-GCM — `offline.db` cifrada con clave derivada de `MachineGuid` |
| Serialización backup | Jackson (JSON + módulo java.time) |
| Build | Maven 3.9 (incluido en `/maven-dist`) |
| Tests | JUnit 5 + Mockito + EmbeddedPostgres (701 tests en la última ejecución) |

---

## Seguridad

El sistema implementa múltiples capas de defensa:

| Área | Mecanismo |
|---|---|
| Contraseñas | BCrypt (factor 12) — nunca se almacena texto plano |
| Intentos de login | Bloqueo tras 5 fallos en 15 min; mensaje con minutos restantes |
| Sesión activa | Timeout de inactividad a los 30 min con countdown UI; cierre automático o manual |
| Credenciales offline | Caché local expira a los **30 días** — requiere conexión periódica al servidor para renovar; el estado `activo` se sincroniza en cada login online — una cuenta desactivada no puede entrar ni en modo offline |
| Autorización | Guards en capa de servicio/repositorio: `SecurityException` si el rol no tiene permiso (no solo en UI) |
| Control de acceso | Admin ve todo; Secretario ve su secretaría y direcciones dependientes; Dirección ve solo su área. Los préstamos son visibles si el área accesible coincide con el área de origen **o** destino (una transferencia debe verse desde ambos lados); los resguardos se acotan por su área única; las actas de entrega-recepción son documentos de todo el ayuntamiento y no se acotan por área (no tienen un área propia — son un corte de administración completa) |
| Cifrado en tránsito | Configurable via `DB_SSL_MODE` en `.env`; la app emite advertencia en log si la BD es remota y SSL no está en modo `require` |
| Auditoría | Toda creación/edición/baja/reactivación de bienes, categorías, usuarios, resguardos, préstamos, actas y configuración queda en `audit_log` con usuario y timestamp, incluyendo intentos de inicio de sesión fallidos |
| Backup | Solo Admin puede ejecutar respaldo/restauración; el archivo debe estar cifrado con AES-256-GCM y una contraseña elegida al momento; los respaldos JSON en claro se rechazan |
| Supabase Storage | La app usa únicamente la clave **anon/public** de Supabase (nunca `service_role`) para subir fotos de bienes — la app corre en las PCs del ayuntamiento, así que cualquier clave embebida ahí debe asumirse extraíble; el bucket debe tener políticas RLS que permitan solo las operaciones necesarias |

> **Cifrado en reposo**: el almacén persistente `offline.db.enc` está cifrado con **AES-256-GCM**. Durante la ejecución se usa temporalmente un archivo de trabajo SQLite y el proceso debe proteger el perfil de Windows y evitar copias de seguridad de ese archivo. La clave se deriva del `MachineGuid` de Windows y es estable ante renombres de equipo.

---

## Requisitos

- **Java 21** o superior instalado y en el PATH
  Verificar: `java -version`
- **PostgreSQL** con la base de datos creada (ver sección de configuración)
- Windows 10/11 (el script `iniciar.bat` es para Windows)

---

## Configuración de base de datos

El esquema ya no se aplica a mano: **Flyway lo crea/actualiza automáticamente la primera vez que la app logra conectarse** a la base de datos (ver `src/main/resources/db/migration/`). Las migraciones actuales van de `V1` a `V18` y cubren el esquema inicial, índices de rendimiento, campos de activos, configuración institucional, resguardos, conteos físicos, email, historial de precios, mantenimiento, préstamos, actas, comodatos, dictámenes de baja y nomenclatura de código por área. Solo hace falta preparar la base vacía y las credenciales antes de arrancar:

1. Crear la base de datos en PostgreSQL (vacía — no hace falta correr ningún script de esquema):
   ```sql
   CREATE DATABASE sibim;
   ```
2. Crear el archivo `.env` con las credenciales (puedes partir de `.env.example`, que trae todas las variables documentadas). En **producción (Windows)** colócalo en `%APPDATA%\SIBIM\.env`; en desarrollo puedes colocarlo en la raíz del proyecto:
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/sibim
   DB_USER=tu_usuario
   DB_PASSWORD=tu_contraseña
   # "require" para producción/Supabase, "prefer" para desarrollo local
   DB_SSL_MODE=prefer
   ```
   ⚠️ Si `DB_PASSWORD` no está configurada, la app se conecta sin contraseña y lo advierte en consola — nunca dejes esto así en un despliegue real; solo es aceptable en desarrollo local con PostgreSQL configurado en modo `trust`.

   Opcionalmente, también puede definirse:
   ```env
   IMG_DIR=\\servidor\sibim\imagenes
   ```

   **Configuración de la carpeta compartida de imágenes (despliegue multiPC)**

   Las fotos de bienes se guardan como archivos en disco y su **ruta queda en la base de datos**. Si dos PCs tienen rutas distintas para la misma carpeta, las fotos aparecerán rotas en una de ellas. Sigue este procedimiento antes de poner en producción:

   1. **Crea la carpeta en el servidor** (o en cualquier PC que permanezca encendida):
      ```
      \\servidor\sibim\imagenes          ← comparte esta carpeta con permisos Lectura+Escritura
      ```
   2. **Verifica desde cada PC del ayuntamiento** que la ruta UNC es accesible:
      ```
      # En CMD de cada PC:
      dir \\servidor\sibim\imagenes
      ```
      Si falla: revisa que el recurso compartido exista, el firewall permita SMB (puerto 445) y la cuenta de Windows tenga acceso.
   3. **Configura `IMG_DIR` en el `.env` de cada PC** usando exactamente la misma cadena UNC:
      ```env
      IMG_DIR=\\servidor\sibim\imagenes
      ```
      > ⚠️ No uses letras de unidad mapeada (p. ej. `Z:\imagenes`) — el mapeo puede diferir entre PCs o no estar disponible al arrancar el servicio. La ruta UNC (`\\servidor\...`) es siempre inequívoca.
   4. **Prueba antes de go-live**: desde dos PCs distintas, sube la foto de un bien y comprueba que la otra PC la ve correctamente en la pantalla de detalle del bien.
3. Inicia SIBIM una vez (ver "Cómo ejecutar" abajo) — Flyway aplica el esquema completo (`V1__schema_inicial.sql`) contra la base vacía en ese primer arranque. **Si estás actualizando una instalación existente**, simplemente vuelve a iniciar la app con la versión nueva: Flyway detecta y aplica solo las migraciones que falten (son idempotentes, seguras de correr más de una vez).
4. **Solo para desarrollo/pruebas locales**, y solo después del paso 3 (las tablas ya deben existir), opcionalmente carga los datos de ejemplo (usuarios, categorías y bienes ficticios):
   ```
   src/main/resources/seed_demo.sql
   ```
   ⚠️ **Nunca ejecutes `seed_demo.sql` contra la base de datos de producción real** — crea 3 cuentas con contraseñas conocidas y públicas en este repositorio (`admin123456`, `sec123456`, `dir123456`). Todas quedan marcadas para forzar el cambio de contraseña en su primer login, pero de todas formas no deben usarse como cuentas reales del ayuntamiento.
5. Crea las cuentas reales del ayuntamiento desde Configuración (solo Admin) una vez levantado el sistema — cualquier cuenta que crees o restablezcas ahí queda forzada a cambiar su contraseña en el primer login.

---

## Cómo ejecutar

### Opción 1 — Script de inicio (recomendado)

Doble clic en **`iniciar.bat`**

El script compila automáticamente si detecta cambios y lanza la aplicación.

### Opción 2 — Maven directo

```bash
# Desde la raíz del proyecto (usa el Maven embebido):
maven-dist/apache-maven-3.9.9/bin/mvn javafx:run
```

> **Nota:** El fat JAR generado por `mvn package` no es directamente ejecutable con `java -jar` en JavaFX 21 (el runtime nativo de JavaFX no puede empaquetarse en un shade JAR). Para distribución usa el instalador (ver sección siguiente).

---

## Despliegue en producción (varias PCs del ayuntamiento)

`iniciar.bat` es para **desarrollo**: recompila con Maven en cada arranque, lo cual requiere tener el código fuente completo y acceso a Maven Central en esa máquina. Para las PCs reales del ayuntamiento **no** se debe copiar el repositorio completo — en su lugar:

1. En una máquina de desarrollo (con Maven e internet), compila una sola vez:
   ```bash
   mvn package -q
   ```
   Esto genera el JAR de compilación, pero para distribuir JavaFX en Windows usa el app-image de `packaging/build-installer.ps1` o el workflow de release; así se incluyen correctamente los runtimes nativos de JavaFX.
2. Copia a cada PC del ayuntamiento **únicamente** la carpeta `SIBIM Desktop` generada, `produccion.bat` y un `.env` con las credenciales reales de esa instalación.
3. Ejecuta `produccion.bat`; el app-image incluye el runtime de Java y no requiere Maven, código fuente ni Java instalado.

Así el código fuente y las credenciales de producción no quedan expuestos en cada estación de trabajo, y una PC no puede quedar corriendo una versión distinta a las demás por una recompilación local accidental.

---

## Modo offline

Si una PC no logra conectar a la base de datos real al arrancar (red caída, servidor apagado, etc.), el sistema **no pierde el trabajo**: entra en modo offline automáticamente.

- **Qué sí funciona sin conexión**: Bienes, Movimientos y Categorías — crear, editar, registrar entradas/salidas/ajustes/transferencias — todo se guarda en un archivo local en esa PC (`%USERPROFILE%\.sibim\offline.db`). Un usuario que ya haya iniciado sesión antes en esa PC estando conectado puede seguir entrando sin conexión **hasta 30 días** después de su último login online; pasado ese plazo, la app exige reconexión para renovar el caché de credenciales.
- **Qué necesita conexión**: crear/editar usuarios, conteos físicos, auditoría, y el cambio de contraseña obligatorio (se pospone hasta el siguiente login ya conectado).
- **Sincronización**: en cuanto la app detecta que la base de datos real volvió a estar disponible (revisa cada minuto), sube automáticamente todo lo capturado offline — bienes, movimientos, categorías, conteos físicos y entradas de auditoría — en el mismo orden en que se hizo. Un aviso confirma cuántos cambios se sincronizaron.
- **Resolución de conflictos**: si un bien fue editado en otro equipo mientras esta PC estaba offline, el sistema lo detecta comparando fechas de modificación y muestra un **diálogo de resolución** con ambas versiones lado a lado (campo por campo, con los valores que difieren resaltados en amarillo). El usuario elige para cada bien si conservar la versión del servidor o aplicar la suya, antes de que se escriba cualquier cambio. La detección aplica también a operaciones de **Baja** y **Reactivación**: si un bien fue dado de baja o reactivado offline pero el servidor lo modificó en el ínterin, también se muestra el conflicto. Al resolver a favor de la versión offline, el sistema llama la operación correcta (baja, reactivación o guardado de campos) según el tipo de cambio pendiente.
- **Fallos permanentes en sincronización**: violaciones de clave foránea, claves únicas y registros no encontrados en el servidor se marcan como descartados en lugar de reintentar indefinidamente — evita que el ciclo de reintento de 60 s se trabe en filas que nunca podrán sincronizarse.
- La barra de estado muestra "Modo offline · N pendientes" mientras haya cambios sin subir.

Este modo offline es distinto del **modo demo** (datos ficticios que se pierden al cerrar la app, `DEMO_MODE=true` en `.env`) — ver `.env.example`. El modo demo es solo para desarrollo local sin PostgreSQL; nunca debe activarse en una instalación real.

---

## Estructura del proyecto

```
SIBIM-Java/
├── src/
│   ├── main/
│   │   ├── java/com/sibim/
│   │   │   ├── controller/        # Controladores JavaFX por módulo
│   │   │   │   ├── dialogs/       # Formularios de diálogo (alta/edición/detalle)
│   │   │   │   ├── *ColumnSetup   # Configuración de columnas de tabla
│   │   │   │   ├── *ChipsManager  # Lógica de chips de filtro activos
│   │   │   │   ├── *Exporter      # Acciones de exportación
│   │   │   │   ├── *Builder       # Constructores de secciones de UI
│   │   │   │   └── *Dialog        # Diálogos auxiliares de un solo método
│   │   │   ├── model/             # Entidades del dominio + enums
│   │   │   ├── repository/        # Acceso a base de datos (PreparedStatements)
│   │   │   ├── service/           # Lógica de negocio; subclases de ReporteService
│   │   │   │   │                  # por tipo de export (Depreciación, Bajas, Dashboard, Auditoría)
│   │   │   ├── db/
│   │   │   │   └── offline/       # OfflineStore (SQLite), SyncService, outbox
│   │   │   ├── util/              # Notificaciones, diálogos, animaciones, formato
│   │   │   ├── session/           # SessionManager (usuario activo, áreas accesibles)
│   │   │   └── config/            # Áreas del organigrama y configuración de BD
│   │   └── resources/
│   │       ├── fxml/              # 16 vistas de la interfaz
│   │       ├── css/               # Design System (tema indigo/purple, 0 inline styles)
│   │       ├── db/migration/      # Migraciones Flyway V1–V18, se aplican solas al arrancar
│   │       ├── offline.sql        # Esquema del almacén SQLite offline
│   │       └── seed_demo.sql      # Datos de ejemplo (solo desarrollo, nunca producción)
│   └── test/java/com/sibim/
│       ├── controller/            # Tests de lógica de filtros y navegación
│       ├── db/integration/        # Tests contra EmbeddedPostgres real
│       ├── db/offline/            # Tests del almacén offline (caducidad, outbox)
│       ├── model/                 # Tests de entidades
│       ├── repository/            # Tests de autorización de repositorios
│       ├── service/               # Tests unitarios + autorización + exports (701 tests total)
│       ├── session/               # Tests de SessionManager
│       └── util/                  # Tests de utilidades
├── packaging/
│   └── build-installer.ps1        # Genera instalador .exe/.msi con jpackage
├── maven-dist/                    # Maven embebido (no requiere Maven instalado)
├── iniciar.bat                    # Arranque para desarrollo (compila y ejecuta)
├── produccion.bat                 # Arranque para producción (solo ejecuta el JAR)
└── pom.xml                        # Dependencias y configuración de build
```

---

## Módulos

| Módulo | Descripción |
|---|---|
| Dashboard | 4 mini-tarjetas de estado con `ProgressBar` animada; gráfica de movimientos semanal; gráfica por categoría; barras de distribución top-5 áreas; contadores animados |
| Inventario | CRUD completo con búsqueda, filtros combinables, presets guardables, importación CSV masiva, paginación, baja patrimonial con motivo, conteo físico, vista de bajas y panel de depreciación en el detalle |
| Movimientos | Entradas / salidas / ajustes / transferencias; flujo de aprobación para transferencias de usuarios no-Admin (PENDIENTE hasta que un Admin las autorice o rechace desde el panel "⏳ Pendientes") |
| Alertas | 3 tarjetas resumen animadas (Agotados / Bajo Stock / Garantías) con proporciones relativas y desglose vencidas/próximas; búsqueda en tiempo real (Ctrl+F); export a PDF, Excel y CSV; reposición de stock con guardia por rol |
| Categorías | Gestión de clasificaciones con selector de color e ícono predefinidos (paleta de swatches) |
| Organigrama | 4 tarjetas (áreas, bienes, top área, valor patrimonial); barras animadas top-5 áreas; alertas de stock por área; acceso directo al Inventario filtrado por área |
| Reportes | Exportación multi-formato con selector de período (PDF, Excel, CSV); encabezado institucional configurable |
| Depreciación | Tarjetas de valor compra/actual/% promedio/totalmente depreciados; distribución en 4 rangos como barras animadas; columna visual `ProgressBar` en la tabla; gráfica de proyección a 10 años; export PDF/Excel/fichas en lote |
| Auditoría | Registro de acciones de todo el sistema (bienes, movimientos, usuarios, resguardos, préstamos, actas, configuración, inicios de sesión); filtros por entidad/acción/usuario con presets de fecha; tarjetas de resumen; export PDF/Excel/CSV (solo Admin) |
| Configuración | Datos institucionales editables (nombre, municipio, responsable, correo); gestión de cuentas con activar/desactivar; historial de auditoría; conteos físicos; respaldo/restauración cifrado (solo Admin); interruptor de animaciones |

---

## Roles de usuario

| Rol | Permisos |
|---|---|
| **Admin** | Acceso completo: gestión de usuarios, todas las áreas, reportes globales, aprobación/rechazo de transferencias pendientes |
| **Secretario** | Acceso a su secretaría y a las direcciones que dependen de ella; las transferencias que registre quedan en PENDIENTE hasta aprobación |
| **Dirección** | Acceso solo a su área asignada; las transferencias que registre quedan en PENDIENTE hasta aprobación |

---

## Respaldo de la base de datos

Desde **Configuración → Respaldo y restauración** (solo Admin, y solo conectado a la base de datos real — no funciona en modo offline/demo) se puede exportar toda la base a un único archivo JSON, o restaurar la base completa desde uno: la restauración corre dentro de una sola transacción, así que si algo falla no deja la base a medias. Es una herramienta manual pensada para respaldos puntuales antes de un cambio importante, no un reemplazo de un respaldo automatizado real — para eso, sigue siendo responsabilidad de quien administre el servidor PostgreSQL. Como mínimo, en producción se recomienda:

```bash
# Respaldo diario (ejemplo)
pg_dump -U tu_usuario -d sibim -F c -f sibim_$(date +%Y%m%d).dump

# Restauración
pg_restore -U tu_usuario -d sibim --clean sibim_20260101.dump
```

El esquema se gestiona con **Flyway** (`src/main/resources/db/migration/`), aplicado automáticamente en cada arranque — no hace falta correr nada a mano. Para un cambio de esquema futuro: agrega un archivo nuevo `V19__descripcion.sql` (numeración consecutiva a partir de V18) a esa carpeta con el `ALTER TABLE`/`CREATE TABLE IF NOT EXISTS` correspondiente; Flyway se encarga de aplicarlo una sola vez por base de datos y de no volver a tocarlo. No edites migraciones ya publicadas — Flyway rechaza cualquier migración aplicada si su contenido cambia.

---

## Tests

```bash
# Correr todos los tests (701 en total)
maven-dist/apache-maven-3.9.9/bin/mvn.cmd test

# Solo tests de una clase
maven-dist/apache-maven-3.9.9/bin/mvn.cmd test -Dtest=AuthServiceTest
```

Los tests de integración (`db/integration/`) levantan una instancia efímera de PostgreSQL con EmbeddedPostgres — no requieren ninguna instalación externa. Los tests de autorización verifican que los guards de seguridad lanzan `SecurityException` para roles sin permiso, independientemente de si hay BD disponible.

---

## Licencia

Proyecto desarrollado para uso interno del **H. Ayuntamiento de Huejutla, Hidalgo**.
Todos los derechos reservados.
