# SIBIM — Sistema Integral de Bienes Municipales

Aplicación de escritorio desarrollada en **Java 21 + JavaFX** para la gestión del inventario patrimonial del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.

---

## Características

- **Inventario de bienes** — registro completo con código, área, resguardante, stock, precios y foto
- **Movimientos** — entradas, salidas, ajustes y **transferencias reales entre áreas** (reasignan el bien, no solo restan stock), con historial, candado de concurrencia para evitar pérdida de datos entre usuarios simultáneos, y una vista previa animada "Área A → Área B" al elegir el destino
- **Baja patrimonial** — dar de baja un bien pide motivo y lo saca del inventario activo sin borrar su historial (soft-delete), con vista para consultar y reactivar bajas
- **Conteo físico de inventario** — captura lo contado contra el sistema, reconcilia las diferencias con movimientos de Ajuste auditados, y guarda cada sesión de conteo completa (incluyendo lo que sí coincidió) para revisión posterior
- **Auditoría de cambios** — historial de quién creó/editó/eliminó/dio de baja/reactivó cada bien, categoría o usuario, consultable desde Configuración (solo Admin)
- **Cambio de contraseña obligatorio** — cualquier cuenta con contraseña temporal conocida (cuentas semilla, o un usuario recién creado/restablecido por un Admin) es forzada a definir su propia contraseña en el primer login, antes de poder usar el sistema
- **Alertas** — bienes agotados, existencias bajo mínimo y garantías por vencer
- **Dashboard** — resumen con gráficas de movimientos y distribución por categoría
- **Reportes** — exportación a PDF, Excel y CSV (inventario, movimientos, alertas, distribución por área)
- **Organigrama** — bienes distribuidos por secretaría y dirección municipal, con valor patrimonial y alertas de stock por área, y salto directo al Inventario filtrado por esa área
- **Gestión de usuarios** — roles Admin, Secretario y Dirección con control de acceso por área
- **Pantalla de inicio animada** con progreso de carga y transiciones tipo cross-fade entre splash → login → sistema
- **Notificaciones toast** en tiempo real
- **Recuperación de formularios** — si falla el guardado (BD caída, validación), el diálogo se reabre con los datos ya capturados en vez de perderlos

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| UI | JavaFX 21 + FXML + CSS + AtlantaFX |
| Base de datos | PostgreSQL |
| Conexión BD | HikariCP (pool de conexiones) |
| Reportes PDF | iText 7 |
| Reportes Excel | Apache POI |
| Cifrado | jBCrypt |
| Build | Maven 3.9 (incluido en `/maven-dist`) |

---

## Requisitos

- **Java 21** o superior instalado y en el PATH
  Verificar: `java -version`
- **PostgreSQL** con la base de datos creada (ver sección de configuración)
- Windows 10/11 (el script `iniciar.bat` es para Windows)

---

## Configuración de base de datos

1. Crear la base de datos en PostgreSQL:
   ```sql
   CREATE DATABASE sibim;
   ```
2. Ejecutar el script de esquema (seguro de correr contra producción — no trae datos ni cuentas):
   ```
   src/main/resources/sibim.sql
   ```
3. **Solo para desarrollo/pruebas locales**, opcionalmente ejecutar después los datos de ejemplo (usuarios, categorías y bienes ficticios):
   ```
   src/main/resources/seed_demo.sql
   ```
   ⚠️ **Nunca ejecutes `seed_demo.sql` contra la base de datos de producción real** — crea 3 cuentas con contraseñas conocidas y públicas en este repositorio (`admin123456`, `sec123456`, `dir123456`). Todas quedan marcadas para forzar el cambio de contraseña en su primer login, pero de todas formas no deben usarse como cuentas reales del ayuntamiento.
4. Crear el archivo `.env` en la raíz del proyecto con las credenciales (puedes partir de `.env.example`, que trae todas las variables documentadas):
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/sibim
   DB_USER=tu_usuario
   DB_PASSWORD=tu_contraseña
   ```
   ⚠️ Si `DB_PASSWORD` no está configurada, la app se conecta sin contraseña y lo advierte en consola — nunca dejes esto así en un despliegue real; solo es aceptable en desarrollo local con PostgreSQL configurado en modo `trust`.

   Opcionalmente, también puede definirse:
   ```env
   IMG_DIR=\\servidor\sibim\imagenes
   ```
   ⚠️ **Importante en un despliegue con varias PCs**: las fotos de los bienes se guardan como archivo en disco, y la ruta se persiste en la base de datos compartida. Si `IMG_DIR` no se configura, cada PC guarda las fotos en su propia carpeta de usuario (`%USERPROFILE%\.sibim\imagenes`) — y una foto subida desde una PC aparecerá rota al verla desde cualquier otra. Para que las fotos se vean igual en todas las computadoras del ayuntamiento, `IMG_DIR` debe apuntar a una ruta de red (UNC o unidad mapeada) accesible **con la misma ruta** desde cada PC que use el sistema.
5. Crea las cuentas reales del ayuntamiento desde Configuración (solo Admin) una vez levantado el sistema — cualquier cuenta que crees o restablezcas ahí queda forzada a cambiar su contraseña en el primer login.

---

## Cómo ejecutar

### Opción 1 — Script de inicio (recomendado)

Doble clic en **`iniciar.bat`**

El script compila automáticamente si detecta cambios y lanza la aplicación.

### Opción 2 — Maven directo

```bash
mvn package -q
java -jar target/sibim-desktop-1.0.0.jar
```

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

- **Qué sí funciona sin conexión**: Bienes, Movimientos y Categorías — crear, editar, registrar entradas/salidas/ajustes/transferencias — todo se guarda en un archivo local en esa PC (`%USERPROFILE%\.sibim\offline.db`). Un usuario que ya haya iniciado sesión antes en esa PC estando conectado también puede seguir entrando sin conexión.
- **Qué necesita conexión**: crear/editar usuarios, conteos físicos, auditoría, y el cambio de contraseña obligatorio (se pospone hasta el siguiente login ya conectado).
- **Sincronización**: en cuanto la app detecta que la base de datos real volvió a estar disponible (revisa cada minuto), sube automáticamente todo lo capturado offline, en el mismo orden en que se hizo. Un aviso confirma cuántos cambios se sincronizaron. Si algún cambio ya no se puede aplicar tal cual (por ejemplo, alguien eliminó ese mismo bien desde otra PC mientras esta estaba offline), queda marcado para revisión manual en vez de perderse o bloquear el resto.
- La barra de estado muestra "Modo offline · N pendientes" mientras haya cambios sin subir.

Este modo offline es distinto del **modo demo** (datos ficticios que se pierden al cerrar la app, `DEMO_MODE=true` en `.env`) — ver `.env.example`. El modo demo es solo para desarrollo local sin PostgreSQL; nunca debe activarse en una instalación real.

---

## Estructura del proyecto

```
SIBIM-Java/
├── src/
│   └── main/
│       ├── java/com/sibim/
│       │   ├── controller/    # Controladores JavaFX por módulo
│       │   │   └── dialogs/   # Formularios de diálogo extraídos (alta/edición)
│       │   ├── model/         # Entidades del dominio
│       │   ├── repository/    # Acceso a base de datos
│       │   ├── service/       # Lógica de negocio
│       │   ├── util/          # Utilidades (notificaciones, diálogos, animaciones)
│       │   ├── session/       # Manejo de sesión de usuario
│       │   └── config/        # Configuración de áreas y BD
│       └── resources/
│           ├── fxml/          # Vistas de la interfaz
│           ├── css/           # Hoja de estilos del sistema de diseño
│           ├── sibim.sql      # Esquema de base de datos (seguro para producción)
│           └── seed_demo.sql  # Datos de ejemplo (solo desarrollo, nunca producción)
├── maven-dist/                 # Maven embebido para ejecución sin instalar
├── iniciar.bat                 # Script de inicio para Windows
└── pom.xml                     # Configuración de dependencias
```

---

## Módulos

| Módulo | Descripción |
|---|---|
| Dashboard | Tarjetas resumen, gráfica de movimientos semanal, gráfica por categoría, barra de salud del inventario |
| Inventario | CRUD completo de bienes con búsqueda, filtros por estado/área/categoría, paginación, baja patrimonial con motivo, conteo físico y vista de bajas |
| Movimientos | Registro de entradas/salidas/ajustes/transferencias (con reasignación real de área) con vista previa del stock resultante |
| Alertas | Tres secciones: agotados, bajo stock y garantías próximas a vencer |
| Categorías | Gestión de clasificaciones con selector de color e ícono predefinidos (paleta de swatches, no hex/RGBA a mano) |
| Organigrama | Vista de bienes distribuidos por estructura organizacional del Ayuntamiento, con resumen de áreas/bienes, valor patrimonial y alertas de stock por área, y acceso directo al Inventario filtrado |
| Reportes | Exportación multi-formato con selector de período |
| Configuración | Perfil de usuario, gestión de cuentas, historial de auditoría e historial de conteos físicos (solo Admin) |

---

## Roles de usuario

| Rol | Permisos |
|---|---|
| **Admin** | Acceso completo: gestión de usuarios, todas las áreas, reportes globales |
| **Secretario** | Acceso a su secretaría y a las direcciones que dependen de ella, sin gestión de usuarios |
| **Dirección** | Acceso solo a su área asignada, sin gestión de usuarios |

---

## Respaldo de la base de datos

El proyecto no incluye ningún mecanismo automatizado de respaldo — es responsabilidad de quien administre el servidor PostgreSQL. Como mínimo, en producción se recomienda:

```bash
# Respaldo diario (ejemplo)
pg_dump -U tu_usuario -d sibim -F c -f sibim_$(date +%Y%m%d).dump

# Restauración
pg_restore -U tu_usuario -d sibim --clean sibim_20260101.dump
```

`src/main/resources/sibim.sql` no usa una herramienta de migraciones (Flyway/Liquibase), pero sí es seguro volver a ejecutarlo contra una base ya existente: usa `CREATE TABLE IF NOT EXISTS` para las tablas y un bloque `DO $$ ... ALTER TABLE IF NOT EXISTS` al final para agregar columnas nuevas sin tocar los datos existentes, y una tabla `schema_version` registra qué versión del script se aplicó por última vez a cada base de datos. Para un cambio de esquema futuro: sube el número en el `INSERT INTO schema_version` y agrega el `ALTER TABLE` correspondiente al bloque `DO $$` — así el mismo script sigue siendo el único que hay que volver a correr en cada instalación.

---

## Licencia

Proyecto desarrollado para uso interno del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.
Todos los derechos reservados.
