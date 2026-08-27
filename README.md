# SIBIM — Sistema Integral de Bienes Municipales

Aplicación de escritorio desarrollada en **Java 21 + JavaFX** para la gestión del inventario patrimonial del **H. Ayuntamiento de Huejutla, Hidalgo**.

---

## Características

- **Inventario de bienes** — registro completo con código, área, resguardante, stock, precios, foto y datos de depreciación; filtros combinables guardables como **presets de acceso rápido** (máx. 10, persistidos en `~/.sibim/presets-bienes.json`); importación masiva desde CSV
- **Depreciación en línea recta (SAT)** — cada bien puede tener fecha de adquisición, vida útil en años y valor residual; el sistema calcula automáticamente el valor depreciado actual y el porcentaje depreciado, visible en el detalle del bien con una barra de progreso codificada por color (verde / ámbar / rojo)
- **Movimientos** — entradas, salidas, ajustes y **transferencias reales entre áreas** (reasignan el bien, no solo restan stock), con historial, candado de concurrencia para evitar pérdida de datos entre usuarios simultáneos, y una vista previa animada "Área A → Área B" al elegir el destino
- **Flujo de aprobación de transferencias** — cuando un usuario no-Admin registra una transferencia, queda en estado **PENDIENTE** (sin mover stock ni área) hasta que un Admin la apruebe o rechace desde el panel "⏳ Pendientes" en Movimientos; el botón muestra un contador en tiempo real y cambia de color cuando hay solicitudes esperando
- **Baja patrimonial** — dar de baja un bien pide motivo y lo saca del inventario activo sin borrar su historial (soft-delete), con vista para consultar y reactivar bajas
- **Conteo físico de inventario** — captura lo contado contra el sistema, reconcilia las diferencias con movimientos de Ajuste auditados, y guarda cada sesión de conteo completa (incluyendo lo que sí coincidió) para revisión posterior; pide confirmación si se intenta cerrar con diferencias sin guardar
- **Auditoría de cambios** — historial de quién creó/editó/eliminó/dio de baja/reactivó cada bien, categoría o usuario, consultable desde Configuración (solo Admin)
- **Respaldo y restauración manual** — desde Configuración (solo Admin), exporta todas las tablas a un único archivo JSON, o restaura la base de datos completa desde uno (reemplaza todo dentro de una sola transacción — si algo falla, no queda a medias). Solo disponible conectado a la base de datos real, no en modo offline/demo
- **Depreciación de activos** — página dedicada con el valor total de compra, valor actual en libros y % promedio depreciado de los bienes con datos completos, una gráfica de proyección del valor a 10 años, y el detalle por bien; exportable a PDF/Excel
- **Cambio de contraseña obligatorio** — cualquier cuenta con contraseña temporal conocida (cuentas semilla, o un usuario recién creado/restablecido por un Admin) es forzada a definir su propia contraseña en el primer login, antes de poder usar el sistema
- **Alertas** — bienes agotados, existencias bajo mínimo y garantías por vencer; exportables a PDF y Excel directamente desde la pantalla de alertas (Ctrl+F para filtrar, atajos de teclado en todos los módulos)
- **Dashboard** — resumen con gráficas de movimientos y distribución por categoría
- **Reportes** — exportación a PDF, Excel y CSV (inventario, movimientos, alertas, distribución por área)
- **Organigrama** — bienes distribuidos por secretaría y dirección municipal, con valor patrimonial y alertas de stock por área, y salto directo al Inventario filtrado por esa área
- **Gestión de usuarios** — roles Admin, Secretario y Dirección con control de acceso por área; buscador en tiempo real por nombre, usuario, cargo y área
- **Interfaz animada** — splash con progreso de carga y transiciones cross-fade; animaciones de entrada escalonadas en cada módulo; contadores animados de 0 al valor real; barra de salud con revelado izquierda→derecha; micro-animaciones de hover/press; animación de transferencia con flecha que se estira al disparar y chip de destino que entra desde la derecha con rebote; efecto shake en errores de validación
- **Aviso de inactividad** — alerta al usuario si permanece sin interacción durante un período prolongado
- **Notificaciones toast** en tiempo real
- **Recuperación de formularios** — si falla el guardado (BD caída, validación), el diálogo se reabre con los datos ya capturados en vez de perderlos

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
| Serialización backup | Jackson (JSON + módulo java.time) |
| Build | Maven 3.9 (incluido en `/maven-dist`) |
| Tests | JUnit 5 + Mockito + EmbeddedPostgres (291 tests) |

---

## Seguridad

El sistema implementa múltiples capas de defensa:

| Área | Mecanismo |
|---|---|
| Contraseñas | BCrypt (factor 12) — nunca se almacena texto plano |
| Intentos de login | Bloqueo tras 5 fallos en 15 min; mensaje con minutos restantes |
| Sesión activa | Timeout de inactividad a los 30 min con countdown UI; cierre automático o manual |
| Credenciales offline | Caché local expira a los **30 días** — requiere conexión periódica al servidor para renovar |
| Autorización | Guards en capa de servicio/repositorio: `SecurityException` si el rol no tiene permiso (no solo en UI) |
| Control de acceso | Admin ve todo; Secretario ve su secretaría y direcciones dependientes; Dirección ve solo su área |
| Cifrado en tránsito | Configurable via `DB_SSL_MODE` en `.env`; la app emite advertencia en log si la BD es remota y SSL no está en modo `require` |
| Auditoría | Toda creación/edición/baja/reactivación de bienes, categorías y usuarios queda en `audit_log` con usuario y timestamp |
| Backup | Solo Admin puede ejecutar respaldo/restauración; verificación de tablas y columnas permitidas antes de restaurar |

> **Limitación conocida**: el archivo SQLite de modo offline (`offline.db`) no está cifrado. Los hashes BCrypt que contiene son robustos, pero los datos de inventario son legibles por quien tenga acceso físico al sistema de archivos. Si en el futuro se manejan datos personales sujetos a regulación, se puede migrar a SQLCipher.

---

## Requisitos

- **Java 21** o superior instalado y en el PATH
  Verificar: `java -version`
- **PostgreSQL** con la base de datos creada (ver sección de configuración)
- Windows 10/11 (el script `iniciar.bat` es para Windows)

---

## Configuración de base de datos

El esquema ya no se aplica a mano: **Flyway lo crea/actualiza automáticamente la primera vez que la app logra conectarse** a la base de datos (ver `src/main/resources/db/migration/`). Solo hace falta preparar la base vacía y las credenciales antes de arrancar:

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
   ⚠️ **Importante en un despliegue con varias PCs**: las fotos de los bienes se guardan como archivo en disco, y la ruta se persiste en la base de datos compartida. Si `IMG_DIR` no se configura, cada PC guarda las fotos en su propia carpeta local — y una foto subida desde una PC aparecerá rota al verla desde cualquier otra. Para que las fotos se vean igual en todas las computadoras del ayuntamiento, `IMG_DIR` debe apuntar a una ruta de red (UNC o unidad mapeada) accesible **con la misma ruta** desde cada PC que use el sistema.
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
   Esto genera `target/sibim-desktop-1.0.0.jar` (el shade plugin lo deja autocontenido, con todas las dependencias empaquetadas — no hay nada más que instalar).
2. Copia a cada PC del ayuntamiento **únicamente**:
   - `sibim-desktop-1.0.0.jar`
   - `produccion.bat` (en la raíz del repo)
   - un `.env` con las credenciales reales de esa instalación (nunca el mismo `.env` de desarrollo)
3. Ejecuta `produccion.bat` — solo requiere Java 21 instalado, no Maven ni el código fuente.

Así el código fuente y las credenciales de producción no quedan expuestos en cada estación de trabajo, y una PC no puede quedar corriendo una versión distinta a las demás por una recompilación local accidental.

---

## Modo offline

Si una PC no logra conectar a la base de datos real al arrancar (red caída, servidor apagado, etc.), el sistema **no pierde el trabajo**: entra en modo offline automáticamente.

- **Qué sí funciona sin conexión**: Bienes, Movimientos y Categorías — crear, editar, registrar entradas/salidas/ajustes/transferencias — todo se guarda en un archivo local en esa PC (`%USERPROFILE%\.sibim\offline.db`). Un usuario que ya haya iniciado sesión antes en esa PC estando conectado puede seguir entrando sin conexión **hasta 30 días** después de su último login online; pasado ese plazo, la app exige reconexión para renovar el caché de credenciales.
- **Qué necesita conexión**: crear/editar usuarios, conteos físicos, auditoría, y el cambio de contraseña obligatorio (se pospone hasta el siguiente login ya conectado).
- **Sincronización**: en cuanto la app detecta que la base de datos real volvió a estar disponible (revisa cada minuto), sube automáticamente todo lo capturado offline — bienes, movimientos, categorías, conteos físicos y entradas de auditoría — en el mismo orden en que se hizo. Un aviso confirma cuántos cambios se sincronizaron.
- **Resolución de conflictos**: si un bien fue editado en otro equipo mientras esta PC estaba offline, el sistema lo detecta comparando fechas de modificación y muestra un **diálogo de resolución** con ambas versiones lado a lado (campo por campo, con los valores que difieren resaltados en amarillo). El usuario elige para cada bien si conservar la versión del servidor o aplicar la suya, antes de que se escriba cualquier cambio.
- La barra de estado muestra "Modo offline · N pendientes" mientras haya cambios sin subir.

Este modo offline es distinto del **modo demo** (datos ficticios que se pierden al cerrar la app, `DEMO_MODE=true` en `.env`) — ver `.env.example`. El modo demo es solo para desarrollo local sin PostgreSQL; nunca debe activarse en una instalación real.

---

## Estructura del proyecto

```
SIBIM-Java/
├── src/
│   ├── main/
│   │   ├── java/com/sibim/
│   │   │   ├── controller/    # Controladores JavaFX por módulo
│   │   │   │   └── dialogs/   # Formularios de diálogo extraídos (alta/edición)
│   │   │   ├── model/         # Entidades del dominio + enums
│   │   │   ├── repository/    # Acceso a base de datos (PreparedStatements)
│   │   │   ├── service/       # Lógica de negocio y validaciones
│   │   │   ├── db/
│   │   │   │   └── offline/   # OfflineStore (SQLite), SyncService, outbox
│   │   │   ├── util/          # Notificaciones, diálogos, animaciones, formato
│   │   │   ├── session/       # SessionManager (usuario activo, áreas accesibles)
│   │   │   └── config/        # Áreas del organigrama y configuración de BD
│   │   └── resources/
│   │       ├── fxml/          # 12 vistas de la interfaz
│   │       ├── css/           # Design System v2.3 (tema indigo/purple)
│   │       ├── db/migration/  # Migraciones Flyway — se aplican solas al arrancar
│   │       ├── offline.sql    # Esquema del almacén SQLite offline
│   │       └── seed_demo.sql  # Datos de ejemplo (solo desarrollo, nunca producción)
│   └── test/java/com/sibim/
│       ├── controller/        # Tests de lógica de filtros y navegación
│       ├── db/integration/    # 5 clases contra EmbeddedPostgres real
│       ├── db/offline/        # Tests del almacén offline (caducidad, outbox)
│       ├── model/             # Tests de entidades
│       ├── repository/        # Tests de autorización de repositorios
│       ├── service/           # Tests unitarios + autorización de servicios + exports (291 tests total)
│       ├── session/           # Tests de SessionManager
│       └── util/              # Tests de utilidades
├── packaging/
│   └── build-installer.ps1    # Genera instalador .exe/.msi con jpackage
├── maven-dist/                # Maven embebido (no requiere Maven instalado)
├── iniciar.bat                # Arranque para desarrollo (compila y ejecuta)
├── produccion.bat             # Arranque para producción (solo ejecuta el JAR)
└── pom.xml                    # Dependencias y configuración de build
```

---

## Módulos

| Módulo | Descripción |
|---|---|
| Dashboard | Tarjetas resumen, gráfica de movimientos semanal, gráfica por categoría, barra de salud del inventario, animaciones de entrada y contadores animados |
| Inventario | CRUD completo de bienes con búsqueda, filtros por estado/área/categoría/resguardante, presets de filtro guardables, importación CSV masiva, paginación, baja patrimonial con motivo, conteo físico, vista de bajas y panel de depreciación en el detalle |
| Movimientos | Registro de entradas/salidas/ajustes/transferencias; flujo de aprobación para transferencias de usuarios no-Admin (quedan como PENDIENTE hasta que un Admin las autorice o rechace desde el panel "⏳ Pendientes") |
| Alertas | Tres secciones: agotados, bajo stock y garantías próximas a vencer; búsqueda en tiempo real (Ctrl+F), export a PDF y Excel desde la cabecera, botones de reposición de stock guardados por rol |
| Categorías | Gestión de clasificaciones con selector de color e ícono predefinidos (paleta de swatches, no hex/RGBA a mano) |
| Organigrama | Vista de bienes distribuidos por estructura organizacional del Ayuntamiento, con resumen de áreas/bienes, valor patrimonial y alertas de stock por área, y acceso directo al Inventario filtrado |
| Reportes | Exportación multi-formato con selector de período (PDF, Excel, CSV) |
| Depreciación | Valor de compra vs. valor actual en libros, % promedio depreciado, gráfica de proyección a 10 años y detalle por bien; exportable a PDF/Excel |
| Configuración | Perfil de usuario, gestión de cuentas con buscador en tiempo real, historial de auditoría, historial de conteos físicos y respaldo/restauración de la base de datos (solo Admin) |

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

El esquema se gestiona con **Flyway** (`src/main/resources/db/migration/`), aplicado automáticamente en cada arranque — no hace falta correr nada a mano. Para un cambio de esquema futuro: agrega un archivo nuevo `V2__descripcion.sql` (numeración consecutiva) a esa carpeta con el `ALTER TABLE`/`CREATE TABLE IF NOT EXISTS` correspondiente; Flyway se encarga de aplicarlo una sola vez por base de datos y de no volver a tocarlo. No edites `V1__schema_inicial.sql` una vez publicado — Flyway rechaza una migración ya aplicada si su contenido cambia.

---

## Tests

```bash
# Correr todos los tests (291 en total)
maven-dist/apache-maven-3.9.9/bin/mvn.cmd test

# Solo tests de una clase
maven-dist/apache-maven-3.9.9/bin/mvn.cmd test -Dtest=AuthServiceTest
```

Los tests de integración (`db/integration/`) levantan una instancia efímera de PostgreSQL con EmbeddedPostgres — no requieren ninguna instalación externa. Los tests de autorización verifican que los guards de seguridad lanzan `SecurityException` para roles sin permiso, independientemente de si hay BD disponible.

---

## Licencia

Proyecto desarrollado para uso interno del **H. Ayuntamiento de Huejutla, Hidalgo**.
Todos los derechos reservados.
