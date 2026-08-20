# SIBIM — Sistema Integral de Bienes Municipales

Aplicación de escritorio desarrollada en **Java 21 + JavaFX** para la gestión del inventario patrimonial del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.

---

## Características

- **Inventario de bienes** — registro completo con código, área, resguardante, stock, precios y foto
- **Movimientos** — entradas, salidas, ajustes y **transferencias reales entre áreas** (reasignan el bien, no solo restan stock), con historial, candado de concurrencia para evitar pérdida de datos entre usuarios simultáneos, y una vista previa animada "Área A → Área B" al elegir el destino
- **Flujo de aprobación de transferencias** — cuando un usuario no-Admin registra una transferencia, queda en estado **PENDIENTE** (sin mover stock ni área) hasta que un Admin la apruebe o rechace desde el panel "⏳ Pendientes" en Movimientos; el botón muestra un contador en tiempo real y cambia de color cuando hay solicitudes esperando
- **Baja patrimonial** — dar de baja un bien pide motivo y lo saca del inventario activo sin borrar su historial (soft-delete), con vista para consultar y reactivar bajas
- **Conteo físico de inventario** — captura lo contado contra el sistema, reconcilia las diferencias con movimientos de Ajuste auditados, y guarda cada sesión de conteo completa (incluyendo lo que sí coincidió) para revisión posterior
- **Auditoría de cambios** — historial de quién creó/editó/eliminó/dio de baja/reactivó cada bien, categoría o usuario, consultable desde Configuración (solo Admin)
- **Cambio de contraseña obligatorio** — cualquier cuenta con contraseña temporal conocida (cuentas semilla, o un usuario recién creado/restablecido por un Admin) es forzada a definir su propia contraseña en el primer login, antes de poder usar el sistema
- **Alertas** — bienes agotados, existencias bajo mínimo y garantías por vencer
- **Dashboard** — resumen con gráficas de movimientos y distribución por categoría
- **Reportes** — exportación a PDF, Excel y CSV (inventario, movimientos, alertas, distribución por área)
- **Organigrama** — bienes distribuidos por secretaría y dirección municipal, con valor patrimonial y alertas de stock por área, y salto directo al Inventario filtrado por esa área
- **Gestión de usuarios** — roles Admin, Secretario y Dirección con control de acceso por área
- **Interfaz animada** — splash con progreso de carga y transiciones cross-fade; animaciones de entrada escalonadas en cada módulo (stat cards, gráficas, banners, grillas de reportes, perfil); contadores animados de 0 al valor real en todas las pantallas; barra de salud con revelado izquierda→derecha; micro-animaciones de hover/press en tarjetas, botones de navegación y acciones rápidas; efecto shake en errores de validación de formularios
- **Aviso de inactividad** — alerta al usuario si permanece sin interacción durante un período prolongado
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
3. **Si estás actualizando una instalación existente**, aplica las migraciones incrementales en orden:
   ```
   sql/migrations/002_movimiento_estado.sql   -- Agrega columna `estado` a movements (flujo de aprobación)
   ```
   Las migraciones usan `IF NOT EXISTS` — son seguras de correr más de una vez.
4. **Solo para desarrollo/pruebas locales**, opcionalmente ejecutar después los datos de ejemplo (usuarios, categorías y bienes ficticios):
   ```
   src/main/resources/seed_demo.sql
   ```
   ⚠️ **Nunca ejecutes `seed_demo.sql` contra la base de datos de producción real** — crea 3 cuentas con contraseñas conocidas y públicas en este repositorio (`admin123456`, `sec123456`, `dir123456`). Todas quedan marcadas para forzar el cambio de contraseña en su primer login, pero de todas formas no deben usarse como cuentas reales del ayuntamiento.
5. Crear el archivo `.env` con las credenciales. En **producción (Windows)** colócalo en `%APPDATA%\SIBIM\.env`; en desarrollo puedes colocarlo en la raíz del proyecto:
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/sibim
   DB_USER=tu_usuario
   DB_PASSWORD=tu_contraseña
   # "require" para producción/Supabase, "prefer" para desarrollo local
   DB_SSL_MODE=prefer
   ```
   ⚠️ Si `DB_PASSWORD` no está configurada, la app se conecta sin contraseña y lo advierte en consola — nunca dejes esto así en un despliegue real; solo es aceptable en desarrollo local con PostgreSQL configurado en modo `trust`.
6. Crea las cuentas reales del ayuntamiento desde Configuración (solo Admin) una vez levantado el sistema — cualquier cuenta que crees o restablezcas ahí queda forzada a cambiar su contraseña en el primer login.

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
├── sql/
│   └── migrations/            # Migraciones incrementales para instalaciones existentes
├── docs/                      # Documentación técnica (CI/CD, arquitectura)
├── maven-dist/                 # Maven embebido para ejecución sin instalar
├── iniciar.bat                 # Script de inicio para Windows
└── pom.xml                     # Configuración de dependencias
```

---

## Módulos

| Módulo | Descripción |
|---|---|
| Dashboard | Tarjetas resumen, gráfica de movimientos semanal, gráfica por categoría, barra de salud del inventario, animaciones de entrada y contadores animados |
| Inventario | CRUD completo de bienes con búsqueda, filtros por estado/área/categoría, paginación, baja patrimonial con motivo, conteo físico y vista de bajas |
| Movimientos | Registro de entradas/salidas/ajustes/transferencias; flujo de aprobación para transferencias de usuarios no-Admin (quedan como PENDIENTE hasta que un Admin las autorice o rechace desde el panel "⏳ Pendientes") |
| Alertas | Tres secciones: agotados, bajo stock y garantías próximas a vencer |
| Categorías | Gestión de clasificaciones con selector de color e ícono predefinidos (paleta de swatches, no hex/RGBA a mano) |
| Organigrama | Vista de bienes distribuidos por estructura organizacional del Ayuntamiento, con resumen de áreas/bienes, valor patrimonial y alertas de stock por área, y acceso directo al Inventario filtrado |
| Reportes | Exportación multi-formato con selector de período |
| Configuración | Perfil de usuario, gestión de cuentas, historial de auditoría e historial de conteos físicos (solo Admin) |

---

## Roles de usuario

| Rol | Permisos |
|---|---|
| **Admin** | Acceso completo: gestión de usuarios, todas las áreas, reportes globales, aprobación/rechazo de transferencias pendientes |
| **Secretario** | Acceso a su secretaría y a las direcciones que dependen de ella; las transferencias que registre quedan en PENDIENTE hasta aprobación |
| **Dirección** | Acceso solo a su área asignada; las transferencias que registre quedan en PENDIENTE hasta aprobación |

---

## Licencia

Proyecto desarrollado para uso interno del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.
Todos los derechos reservados.
