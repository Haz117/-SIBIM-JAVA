# SIBIM — Sistema Integral de Bienes Municipales

Aplicación de escritorio desarrollada en **Java 21 + JavaFX** para la gestión del inventario patrimonial del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.

---

## Características

- **Inventario de bienes** — registro completo con código, área, resguardante, stock, precios y foto
- **Movimientos** — entradas, salidas, ajustes y **transferencias reales entre áreas** (reasignan el bien, no solo restan stock), con historial y candado de concurrencia para evitar pérdida de datos entre usuarios simultáneos
- **Baja patrimonial** — dar de baja un bien pide motivo y lo saca del inventario activo sin borrar su historial (soft-delete), con vista para consultar y reactivar bajas
- **Conteo físico de inventario** — captura lo contado contra el sistema y reconcilia las diferencias con movimientos de Ajuste auditados
- **Alertas** — bienes agotados, existencias bajo mínimo y garantías por vencer
- **Dashboard** — resumen con gráficas de movimientos y distribución por categoría
- **Reportes** — exportación a PDF, Excel y CSV (inventario, movimientos, alertas, distribución por área)
- **Organigrama** — bienes distribuidos por secretaría y dirección municipal
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
2. Ejecutar el script de esquema ubicado en:
   ```
   src/main/resources/sibim.sql
   ```
3. Crear el archivo `.env` en la raíz del proyecto con las credenciales:
   ```env
   DB_URL=jdbc:postgresql://localhost:5432/sibim
   DB_USER=tu_usuario
   DB_PASSWORD=tu_contraseña
   ```

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
│           └── sibim.sql      # Esquema de base de datos
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
| Organigrama | Vista de bienes distribuidos por estructura organizacional del Ayuntamiento, con resumen de áreas/bienes |
| Reportes | Exportación multi-formato con selector de período |
| Configuración | Perfil de usuario y gestión de cuentas (solo Admin) |

---

## Roles de usuario

| Rol | Permisos |
|---|---|
| **Admin** | Acceso completo: gestión de usuarios, todas las áreas, reportes globales |
| **Secretario** | Acceso a su secretaría y a las direcciones que dependen de ella, sin gestión de usuarios |
| **Dirección** | Acceso solo a su área asignada, sin gestión de usuarios |

---

## Licencia

Proyecto desarrollado para uso interno del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.
Todos los derechos reservados.
