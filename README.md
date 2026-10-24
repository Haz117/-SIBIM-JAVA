# SIBIM — Sistema Integral de Bienes Municipales

Aplicación de escritorio desarrollada en **Java 21 + JavaFX** para la gestión del inventario patrimonial del **H. Ayuntamiento de Ixmiquilpan, Hidalgo** (Presidencia Municipal de Ixmiquilpan).

---

## Características

- **Inventario de bienes** — registro completo con código, área, resguardante, stock, precios, foto y datos de depreciación; filtros combinables guardables como **presets de acceso rápido** (máx. 10, persistidos en `~/.sibim/presets-bienes.json`); importación masiva desde CSV. El autocompletado de marca, modelo, proveedor y ubicación sugiere los valores de tus áreas, los más usados primero; **Ctrl+C** (o clic derecho › Copiar código) copia el código de las filas seleccionadas; en la búsqueda, `%` y `_` se buscan tal cual
- **Nomenclatura de códigos `PREFIJO/NN`** — cada bien activo lleva el prefijo de su área y el número libre más bajo (`SGM/07`). Al transferirse toma el siguiente número libre del área de destino; al darse de baja conserva su código (para el acta). En ambos casos su número queda libre y lo toma el siguiente bien nuevo del área. `scripts/sql/revisar_nomenclatura.sql` revisa en la base real, en modo de solo lectura, que se cumpla
- **Depreciación en línea recta (SAT)** — cada bien puede tener fecha de adquisición, vida útil en años y valor residual; el sistema calcula automáticamente el valor depreciado actual y el porcentaje depreciado, visible en el detalle del bien con una barra de progreso codificada por color (verde / ámbar / rojo)
- **Movimientos** — entradas, salidas, ajustes y **transferencias reales entre áreas** (reasignan el bien, no solo restan stock), con historial, candado de concurrencia para evitar pérdida de datos entre usuarios simultáneos, y una vista previa animada "Área A → Área B" al elegir el destino. El **stock y el área de un bien solo cambian con movimientos**: el formulario de edición los muestra en solo lectura, la acción masiva "Transferir" registra transferencias y guardar una edición nunca pisa un movimiento registrado mientras el formulario estaba abierto (si otro usuario modificó el bien, pide volver a abrirlo). Cada transferencia guarda el código patrimonial anterior y el nuevo. Solo el Admin puede eliminar un movimiento, y solo el más reciente de cada bien; los demás usan **Revertir**, que deja ambos registros
- **Transferencias con acuse de recibo** — Patrimonio registra la transferencia y se aplica al instante (área y código nuevos); nadie la aprueba. El área que recibe ve el bien en "Pendientes de tu área" y confirma cuando ya lo tiene físicamente: es un acuse que no detiene nada. El botón "Pendientes" de Movimientos solo aparece si quedan solicitudes de transferencia de antes de que las áreas dejaran de enviarlas
- **Préstamos, resguardos y comodatos solo de Patrimonio** — solo Patrimonio (Admin) los registra; las áreas ya no envían solicitudes por SIBIM (las piden directamente a Patrimonio). Las solicitudes enviadas antes de ese cambio siguen en la campana del Admin y en Préstamos/Resguardos → "Solicitudes", donde se aprueban o rechazan
- **Equipos por cuenta** — una cuenta solo puede entrar sin internet en una PC donde ya entró con conexión en los últimos 30 días. Cada ingreso con conexión se anota por computadora, y Configuración → Usuarios → "Equipos por cuenta" (solo Admin) muestra qué cuentas ya están listas para trabajar sin internet, cuáles tienen el acceso vencido y cuáles no han entrado en ninguna PC. La lista incluye la versión de SIBIM instalada en cada equipo
- **Errores de equipos** — los errores que SIBIM tiene en cualquier computadora se envían a la base (máximo 25 por sesión, sin repetir) y el Admin los ve en Configuración → "Errores de equipos" con el equipo, la cuenta y la versión; se conservan 90 días
- **Respaldo automático** — en Configuración → "Respaldo automático" el Admin define una contraseña y esa computadora guarda cada día un respaldo cifrado en `%APPDATA%\SIBIM\respaldos` (se conservan 30). La contraseña queda sellada con Windows DPAPI en esa PC; para restaurar se escribe la misma contraseña en cualquier equipo
- **Baja patrimonial con solicitud** — solo el administrador (Patrimonio) da de baja un bien, y solo con la **solicitud de baja firmada** por el área: la ventana de baja exige su oficio/folio (queda junto al motivo, en la auditoría y en el acta) y permite generar el formato. Secretarías y direcciones ven "Solicitar baja", que genera el formato para firmarlo y entregarlo. La baja saca el bien del inventario activo sin borrar su historial (soft-delete) y genera el acta de baja
- **Pantalla de Bajas** (Ctrl+Alt+B) — todas las bajas de tus áreas con motivo, folio de la solicitud, destino, dictamen, número de acta y valor; tarjetas (total, este año, valor dado de baja, sin solicitud registrada), filtros por texto/área/año, exportación PDF/Excel/CSV, volver a generar el acta, ficha técnica y reactivar (solo administrador)
- **Ficha y etiquetas del bien** — en la barra inferior de Bienes: **Ficha** (ficha técnica en PDF; con varios seleccionados, todas en un PDF) y **Etiqueta ▾** (etiqueta de inventario con datos y QR, o solo QR), además de **QR**
- **El QR descarga la ficha del bien** — con el almacenamiento de fotos configurado (`SUPABASE_ANON_KEY`), el QR de un bien es un enlace a su ficha en PDF: se publica al generar el QR o las etiquetas QR y se actualiza al guardar el bien. Esa copia no lleva montos, historial ni firmas, porque cualquiera frente a la etiqueta puede abrirla. Sin almacenamiento, y en la etiqueta oficial (su QR es muy chico para un enlace), el QR lleva lo que la etiqueta no imprime: resguardante, ubicación, estado físico, adquisición, factura, garantía y próxima revisión. El lector de SIBIM sigue obteniendo el código en ambos casos
- **Abrir, imprimir y guardar documentos** — cada PDF/Excel generado muestra Guardar como / Imprimir / Abrir / Carpeta / Copiar ruta; si Java no logra abrir el archivo en la PC se usa el mismo mecanismo que el Explorador de Windows. "Imprimir" abre el visor para elegir impresora (Ctrl+P)
- **Conteo físico de inventario** — verificación bien por bien: un clic en la fila (o en "Visto") marca el bien que se tiene a la vista, o se escanea su etiqueta con la pistola lectora sin abrir nada, y se anota lo que no coincide (mal estado, en otra área, faltante, o una cantidad distinta). Las pestañas **Todos / Pendientes / Vistos / Con incidencia** muestran cuántos hay de cada uno; una fila dice "Pendiente" hasta que alguien la revisa y solo toma color cuando ya se vio (verde) o tiene una incidencia (ámbar). La ventana ocupa el ancho de la pantalla y se puede agrandar. Al finalizar se reconcilian las diferencias con movimientos de Ajuste auditados y se guarda la sesión completa; un bien que nadie marcó queda como "sin revisar", no como encontrado, y el reporte lo dice. El borrador se guarda en un archivo de la PC y recuerda lo ya visto
- **Auditoría de cambios** — historial de quién creó/editó/eliminó/dio de baja/reactivó cada bien, categoría, usuario, resguardo, préstamo, acta o configuración; filtrable por entidad, acción, usuario y fechas (accesos rápidos Hoy / 7 días / Este mes) en una sola barra, con las entidades y acciones que la bitácora realmente tiene y en palabras ("Bienes", "Inicio de sesión", "Baja"). Los filtros duran mientras SIBIM esté abierto y "Limpiar filtros" aparece cuando hay alguno; una respuesta que llega tarde nunca muestra los registros de otro filtro. Tarjetas de resumen (total, inicios de sesión, intentos fallidos, eliminaciones/bajas) y exportación a PDF, Excel y CSV — consultable desde el menú lateral (solo Admin)
- **Centro de notificaciones** — icono de campana en la barra de estado, visible en cualquier pantalla, con las garantías por vencer, los préstamos vencidos o por vencer, los bienes por recibir y las solicitudes por atender; cada elemento navega directo a la pantalla correspondiente. Su número es la suma de los contadores de Alertas y Préstamos del menú lateral, y el aviso del Dashboard usa las mismas listas que la pantalla de Alertas, así que las tres cifras cuadran
- **Respaldo y restauración manual** — desde Configuración (solo Admin), exporta todas las tablas a un único archivo **cifrado con AES-256-GCM y una contraseña que tú eliges** (necesaria de nuevo para restaurar — no queda ligada a esta PC, para poder restaurar en otro equipo), o restaura la base de datos completa desde uno (reemplaza todo dentro de una sola transacción — si algo falla, no queda a medias). La bitácora de auditoría nunca se borra al restaurar (solo se le agregan las entradas del respaldo que falten). Un respaldo de una versión anterior deja intactas las tablas que no incluye; si alguna de ellas depende de los bienes y tiene datos (fotos, comodatos, historial de precios, mantenimiento), se rechaza en vez de borrarlas. Solo disponible conectado a la base de datos real, no en modo offline/demo
- **Depreciación de activos** — 4 tarjetas (valor compra, valor actual, % promedio, totalmente depreciados); distribución del inventario en 4 rangos de depreciación (0–24 % / 25–49 % / 50–99 % / 100 %+) como barras animadas; columna de visualización con `ProgressBar` codificada por color en la tabla; gráfica de proyección del valor a 10 años; exportable a PDF / Excel / fichas técnicas en lote
- **Cambio de contraseña obligatorio** — cualquier cuenta con contraseña temporal conocida (cuentas semilla, o un usuario recién creado/restablecido por un Admin) es forzada a definir su propia contraseña en el primer login, antes de poder usar el sistema
- **Alertas y pendientes** — garantías vencidas o por vencer, mantenimientos próximos, comodatos vencidos y pendientes patrimoniales (bienes sin resguardante o sin etiqueta física); exportable a PDF, Excel y CSV. SIBIM es un inventario patrimonial, no de tienda: no existen los estados "agotado" ni "bajo stock", ni mínimos de existencia ni reposiciones
- **Inicio** — tarjetas de resumen con `ProgressBar` codificada por color, gráfica de movimientos semanal, gráfica por categoría y barras de distribución de las 5 áreas con más bienes; para las áreas, "Pendientes de tu área" (bienes sin foto, ubicación o estado físico, y bienes por recibir)
- **Menú lateral por grupos** — Inventario (Inicio, Organigrama, Bienes, Categorías), Operación (Movimientos, Alertas, Conteo físico), Reportes (Reportes, Depreciación), Control patrimonial (Resguardos, Préstamos, Comodatos, Actas, Bajas) y Sistema (Configuración, Auditoría). Un grupo sin nada que abrir para la cuenta (Control patrimonial, para un área) no se muestra
- **Reportes** — exportación a PDF, Excel y CSV (inventario, movimientos, alertas, distribución por área); el encabezado de todos los documentos PDF y Excel usa el nombre del ayuntamiento configurado en Configuración
- **Organigrama** — 4 tarjetas de resumen (áreas, bienes distribuidos, área con más bienes, valor patrimonial total); barras horizontales animadas con las top-5 áreas; **tres modos de vista**: árbol jerárquico por secretaría/dirección, tabla de resumen y tarjetas por área (toggle en la barra de herramientas). El árbol muestra los primeros 5 bienes de cada área y "ver todos" abre la lista completa (antes armaba una fila oculta por cada bien del inventario en cada búsqueda); bienes, resguardos, préstamos y comodatos se leen en paralelo; las **tarjetas de área** muestran valor patrimonial, cantidad total y bienes con garantía vencida — con borde de acento ámbar (1–2 alertas) o rojo (3 o más); clic en el botón de área abre un diálogo con tabs de **Bienes**, **Préstamos activos**, **Comodatos** y **Actas de entrega**, con exportación a PDF; salto directo al Inventario filtrado por esa área
- **Gestión de usuarios** — roles Admin, Secretario y Dirección con control de acceso por área; buscador en tiempo real; activar/desactivar cuentas (desactivar bloquea el acceso tanto online como en modo offline); la eliminación de un usuario con bienes/movimientos relacionados ofrece desactivar la cuenta como alternativa a borrar
- **Cuentas por área** — Configuración › Usuarios › "Cuentas por área" crea de un clic una cuenta compartida por cada área del organigrama (Patrimonio como administrador, Presidencia y secretarías como secretario, direcciones y organismos como dirección) y genera un PDF con usuarios y contraseñas. Los usuarios salen del nombre del área (`catastro`, `seguridad.c2`); si dos áreas se abreviarían igual o una cuenta personal ya tiene ese nombre, se agrega el prefijo del área (`catastro.dcat`). Las cuentas que ya existen no se tocan. El PDF es temporal y las contraseñas no se pueden volver a ver: SIBIM pide guardarlo antes de abrirlo
- **Quién usa una cuenta compartida** — en cada inicio de sesión de una cuenta de área se confirma el nombre de la persona (ya viene escrito el último que se usó en esa PC); todo lo que haga queda a nombre de "Persona · Área" y la bitácora registra el cambio de persona
- **Configuración institucional** — nombre del ayuntamiento, municipio, área responsable y correo de contacto editables desde Configuración (solo Admin); todos los reportes PDF/Excel usan automáticamente estos datos
- **Reportes y avisos programados** — a partir de las 06:00, la primera PC abierta con sesión de administrador genera los reportes programados (según la frecuencia configurada) y envía los avisos diarios de préstamos vencidos y el resumen de los lunes. Cada tarea se "reclama" en la base de datos, así que aunque haya varias PCs abiertas se hace una sola vez al día
- **Áreas y prefijos editables** — el organigrama del municipio y el prefijo con que se numeran los bienes de cada área (p. ej. `TICS/01`) viven en la tabla `areas` y se editan en Configuración → Áreas y prefijos (solo Admin): agregar un área, cambiar de quién depende o su prefijo. El nombre de un área no se puede cambiar porque los bienes y resguardos lo guardan. Sin conexión se usa la última copia descargada en la PC
- **Formatos oficiales en guinda institucional** — los PDF y Excel (resguardos, actas, préstamos, comodatos, reportes) usan el guinda del ayuntamiento y el logo municipal: el configurado en Configuración (la imagen se guarda en la base, así que todas las PCs imprimen el mismo logo) o, si no hay, `src/main/resources/img/logo-municipio.png` incluido en la app. La interfaz usa la misma paleta institucional: guinda (#A2232D), dorado y verde institucional (#235B4E); el verde, el ámbar y el rojo brillantes se reservan para estados (hecho, cuidado, error)
- **Ventanas con la identidad del sistema** — en Windows 11 la barra de título y el borde de cada ventana de SIBIM son guinda con texto blanco (atributos de ventana de DWM), sin perder lo nativo: ajustar a media pantalla, maximizar, redimensionar. En Windows 10 la barra queda como la del sistema. El selector de fecha también lleva encabezado guinda
- **Interfaz animada** — splash con progreso de carga y transiciones cross-fade; animaciones de entrada escalonadas en cada módulo; contadores animados de 0 al valor real; barra de salud con revelado izquierda→derecha; micro-animaciones de hover/press; animación de transferencia con flecha que se estira al disparar y chip de destino que entra desde la derecha con rebote; efecto shake en errores de validación
- **Aviso de inactividad** — alerta al usuario si permanece sin interacción durante un período prolongado
- **Notificaciones** — avisos (toast) que se quedan mientras el puntero está encima; los avisos que no pueden pasar desapercibidos (el PDF de contraseñas, cambios sin sincronizar, base que necesita actualizarse) son una ventana que espera a que alguien la cierre
- **Recuperación de formularios** — si falla el guardado (BD caída, validación), el diálogo se reabre con los datos ya capturados en vez de perderlos
- **Accesibilidad** — texto accesible automático para lectores de pantalla en botones de solo-ícono (toma el texto del tooltip); **tamaño de texto** (Normal / Grande / Extra grande) que agranda todo el texto de la app, menú, barra de estado, diálogos y avisos incluidos (una hoja de estilos generada al arrancar escala cada tamaño del CSS; los botones conservan el suyo para no cortarse) y densidad de filas de tabla (Compacto / Normal / Cómodo), ambos en la barra de estado; las **animaciones** siguen el ajuste "Mostrar animaciones" de Windows y se apagan en Configuración → Accesibilidad y rendimiento, lo que cubre también avisos, cambios de pantalla, tutorial y menú lateral; cada "?" de ayuda se abre con clic o con Tab + Enter; los selectores de fecha dicen "dd/mm/aaaa"
- **Tutorial (F2)** — un paso por módulo, solo los que la cuenta puede abrir: encabezado con el ícono, "Paso N de M", título y atajo de teclado; una barra de avance; los puntos de cada paso numerados; navega al módulo que describe. Las cuentas compartidas tienen un paso sobre quién usa la cuenta
- **Un solo menú "Exportar"** — todas las pantallas exportan desde el mismo menú (PDF / Excel / CSV según la pantalla); en Bienes y Movimientos incluye además "Solo los seleccionados"
- **Menú lateral adaptable** — en ventanas angostas (menos de 1360 px) el menú lateral se compacta solo a iconos y se vuelve a expandir al agrandar la ventana
- **Columnas de tabla restaurables** — en las tablas con menú de columnas (Auditoría, Categorías, Movimientos, Depreciación, Alertas), un botón junto a "Actualizar" regresa el orden, ancho y visibilidad de las columnas a como estaban originalmente, sin tener que recordar qué se cambió

---

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| UI | JavaFX 21 + FXML + CSS + AtlantaFX (PrimerLight) |
| Base de datos principal | PostgreSQL (gestionado por Flyway) |
| Base de datos offline | SQLite 3.46 (`~/.sibim/offline.db`) |
| Conexión BD | HikariCP (pool de hasta 6 conexiones, 8 s timeout) |
| Reportes PDF | iText 7 |
| Reportes Excel | Apache POI |
| Cifrado de contraseñas | BCrypt (at.favre.lib, factor 12) |
| Cifrado de datos en reposo | AES-256-GCM — `offline.db.enc` cifrada con una clave aleatoria sellada con DPAPI de Windows para la cuenta del usuario |
| Serialización backup | Jackson (JSON + módulo java.time) |
| Build | Maven 3.9 (incluido en `/maven-dist`) |
| Windows | JNA (DPAPI para sellar claves; colores de la barra de título vía DWM) |
| Tests | JUnit 5 + Mockito + EmbeddedPostgres + TestFX (1132 tests en la última ejecución) |

---

## Seguridad

El sistema implementa múltiples capas de defensa:

| Área | Mecanismo |
|---|---|
| Contraseñas | BCrypt (factor 12) — nunca se almacena texto plano |
| Intentos de login | Bloqueo tras 5 fallos en 15 min, con mensaje de minutos restantes. El contador vive en el servidor (tabla `login_attempts`, reloj del servidor), así que el bloqueo aplica desde cualquier PC; además hay un contador local para el modo offline |
| Sesión activa | Timeout de inactividad a los 30 min con countdown UI; cierre automático o manual. Si un Admin desactiva o elimina la cuenta, o le cambia el rol o el área, la sesión abierta se cierra en menos de un minuto |
| Credenciales offline | Caché local expira a los **30 días** — requiere conexión periódica al servidor para renovar; el estado `activo` se sincroniza en cada login online — una cuenta desactivada no puede entrar ni en modo offline |
| Autorización | Guards en capa de servicio/repositorio: `SecurityException` si el rol no tiene permiso (no solo en UI). Como cada PC se conecta directo a PostgreSQL, estos guards no protegen contra quien tenga las credenciales de la base: por eso las PCs deben usar el rol `sibim_app` (ver [Rol de base de datos con mínimo privilegio](#rol-de-base-de-datos-con-mínimo-privilegio)) |
| Control de acceso | Admin ve todo; Secretario ve su secretaría y direcciones dependientes; Dirección ve solo su área. Los préstamos son visibles si el área accesible coincide con el área de origen **o** destino (una transferencia debe verse desde ambos lados); los resguardos se acotan por su área única; las actas de entrega-recepción son documentos de todo el ayuntamiento y no se acotan por área (no tienen un área propia — son un corte de administración completa) | Solo el administrador da de alta, da de baja, reactiva, registra movimientos y gestiona resguardos, préstamos y comodatos (las áreas solo los consultan e imprimen); Secretaría y Dirección solo actualizan datos de sus bienes (ver Roles de usuario).
| Cifrado en tránsito | Con una base remota la app exige `DB_SSL_MODE=require` o más estricto y no arranca sin él ni sin `DB_PASSWORD` (`DB_SSL_BYPASS=true` solo para desarrollo). `require` cifra la conexión pero no comprueba el certificado del servidor; `verify-full` con el certificado de Supabase lo haría |
| Auditoría | Toda creación/edición/baja/reactivación de bienes, categorías, usuarios, resguardos, préstamos, actas y configuración queda en `audit_log` con usuario y timestamp, incluyendo intentos de inicio de sesión fallidos |
| Backup | Solo Admin puede ejecutar respaldo/restauración; el archivo debe estar cifrado con AES-256-GCM y una contraseña elegida al momento; los respaldos JSON en claro se rechazan |
| Supabase Storage | La app usa únicamente la clave **anon/public** de Supabase (`SUPABASE_ANON_KEY`, nunca `service_role`) para subir fotos de bienes — la app corre en las PCs del ayuntamiento, así que cualquier clave embebida ahí debe asumirse extraíble; el bucket debe tener políticas RLS que permitan solo las operaciones necesarias. Si en `SUPABASE_ANON_KEY` hay una clave secreta, la app no la usa, y si el `.env` contiene una clave de servicio (`SUPABASE_SERVICE_KEY`, etc.) lo registra como error en el log |

> **Cifrado en reposo**: el almacén persistente `offline.db.enc` está cifrado con **AES-256-GCM** usando una clave aleatoria que se guarda en `offline.key` **sellada con DPAPI** para la cuenta de Windows del usuario: otra cuenta de la misma PC no puede abrirla. (Las versiones anteriores derivaban la clave del nombre de usuario y el `MachineGuid`, datos que cualquier cuenta del equipo puede leer; al abrir un almacén de esas versiones se vuelve a cifrar con la clave nueva.) Si un administrador restablece la contraseña de Windows del usuario, DPAPI ya no puede abrir la clave: el almacén ilegible se aparta como `offline.db.enc.ilegible-*` y se reconstruye desde el servidor (se pierden los cambios offline que no se hubieran sincronizado). Durante la ejecución se usa temporalmente un archivo de trabajo SQLite en claro.

---

## Requisitos

- **Para usar SIBIM**: Windows 10/11 y el instalador (`SIBIM-Desktop-<versión>-win64-setup.exe`), que trae su propio Java y se instala sin permisos de administrador. Ver [Despliegue en producción](#despliegue-en-producción-varias-pcs-del-ayuntamiento)
- **Para desarrollar**: Java 21 o superior en el PATH (`java -version`) y PostgreSQL con la base creada (ver la sección siguiente); Maven va incluido en `maven-dist/`

---

## Configuración de base de datos

El esquema ya no se aplica a mano: **Flyway lo crea/actualiza automáticamente la primera vez que la app logra conectarse** a la base de datos (ver `src/main/resources/db/migration/`). Las migraciones actuales van de `V1` a `V31` y cubren el esquema inicial, índices de rendimiento, campos de activos, configuración institucional, resguardos, conteos físicos, email, historial de precios, mantenimiento, préstamos, actas, comodatos, dictámenes de baja, nomenclatura de código por área, campos de formatos oficiales (V19, re-aplicados de forma idempotente en V21 para bases donde su SQL no llegó a ejecutarse), eventos de auditoría del sistema (login fallido, respaldos) que no siempre tienen una entidad de negocio asociada, los códigos anterior/nuevo de cada transferencia (V22) el contador de intentos de inicio de sesión compartido entre PCs (V23) y la tabla de áreas del municipio con su prefijo de código (V24, sembrada con las 44 áreas de Ixmiquilpan) y los PDF firmados de los resguardos por área guardados en la propia base para que cualquier PC los abra (V25), la confirmación de recepción de las transferencias (V26), las actualizaciones publicadas en la base (V27) y su firma (V31), las solicitudes de las áreas (V28), el registro de los equipos en que entra cada cuenta (V29) y la versión instalada y los errores de cada equipo (V30). Solo hace falta preparar la base vacía y las credenciales antes de arrancar:

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

   **Fotos de los bienes (Supabase Storage)** — con `SUPABASE_URL` y `SUPABASE_ANON_KEY` (la clave **pública** del proyecto) en el `.env`, las fotos y facturas se suben al bucket público `sibim-fotos` y todas las PCs las ven en la tabla, el detalle y los formatos PDF. Si falta `SUPABASE_ANON_KEY`, la foto queda como archivo de esa PC (SIBIM lo avisa al guardar) y ninguna otra computadora la ve; al agregar la clave, las fotos pendientes de esa PC se suben solas. El bucket necesita las políticas de `scripts/sql/storage_fotos.sql` (ejecútalo una vez en el SQL Editor de Supabase): sin ellas, la clave pública no puede subir nada. La carpeta compartida `IMG_DIR` de abajo es la alternativa para instalaciones sin Supabase.

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

El script compila (sin correr las pruebas) y lanza la aplicación con `java -jar`. Ojo: usa el `.env` de `%APPDATA%\SIBIM\` si existe, así que en una PC con SIBIM instalado se conecta a la base real. Para probar sin tocarla, arráncalo con `DEMO_MODE=true` desde una carpeta sin `.env` y con `APPDATA` apuntando a una carpeta vacía.

### Opción 2 — Maven directo

```bash
# Desde la raíz del proyecto (usa el Maven embebido):
maven-dist/apache-maven-3.9.9/bin/mvn javafx:run
```

---

## Despliegue en producción (varias PCs del ayuntamiento)

Las PCs del ayuntamiento usan el **instalador**, nunca el código fuente:

1. **Conexión que lleva el instalador** — en la PC de administración, `powershell -ExecutionPolicy Bypass -File packaging\preparar-conexion.ps1` genera `packaging\sibim.env` (git lo ignora) con el usuario de mínimo privilegio `sibim_app` (ver [Rol de base de datos con mínimo privilegio](#rol-de-base-de-datos-con-mínimo-privilegio)).
2. **Generar el instalador** — sube la versión en `pom.xml` (Windows no instala la misma versión encima y "Publicar actualización" exige una mayor), corre las pruebas (`mvn clean test`) y luego:
   ```powershell
   powershell -ExecutionPolicy Bypass -File packaging\build-installer.ps1 -Type exe -SkipTests
   ```
   Sale `packaging\dist\SIBIM-Desktop-<versión>-win64-setup.exe` con `sibim.env` adentro y su `.firma`. La firma la pone la llave de TI, que solo existe en la PC de administración (`%APPDATA%\SIBIM`); el `.firma` lleva el sha256 del instalador. WiX 3.14 portátil va en `tools\wix314`.
3. **Instalar** — el instalador es por usuario (no pide permisos de administrador), trae su propio Java y se conecta solo como `sibim_app`. Una PC que tenga su propio `%APPDATA%\SIBIM\.env` usa ese; en las PCs de trabajo bórralo para que use el del instalador. La PC de administración conserva el suyo (usuario `postgres`) y es la primera en abrir cada versión nueva, para aplicar las migraciones.
4. **Actualizar las demás PCs** — desde la PC de administración, Configuración › Publicar actualización sube el instalador firmado a la base; cada PC lo ofrece al arrancar y solo instala los que lleven la firma de TI.
5. **Dejar la PC de administración sin secretos de más** — `powershell -ExecutionPolicy Bypass -File packaging\limpiar-env.ps1` quita del `.env` la clave de servicio de Supabase y las credenciales dentro de `DB_URL`, sin mostrarlas, y deja un respaldo junto al archivo. Después rota la clave en Supabase y borra el respaldo.

`produccion.bat` y el app-image (`build-installer.ps1` sin `-Type`) siguen sirviendo para una PC sin instalador, con su propio `.env`.

---

## Modo offline

Si una PC no logra conectar a la base de datos real al arrancar (red caída, servidor apagado, etc.), el sistema **no pierde el trabajo**: entra en modo offline automáticamente.

- **Qué sí funciona sin conexión**: Bienes, Movimientos y Categorías — crear, editar, registrar entradas/salidas/ajustes/transferencias — todo se guarda en un archivo local en esa PC (`%USERPROFILE%\.sibim\offline.db`). Un usuario que ya haya iniciado sesión antes en esa PC estando conectado puede seguir entrando sin conexión **hasta 30 días** después de su último login online; pasado ese plazo, la app exige reconexión para renovar el caché de credenciales.
- **Qué necesita conexión**: crear/editar usuarios, conteos físicos, auditoría, préstamos, comodatos, resguardos, actas, resguardos por área, alertas de mantenimiento, guardar la configuración, aprobar o rechazar transferencias pendientes (dependen de dónde está el bien en el servidor), **dar de baja** un bien y **cambiar su resguardante** (sin conexión no se puede comprobar si está prestado o si un resguardo firmado lo define), y el cambio de contraseña obligatorio (se pospone hasta el siguiente login ya conectado). Esas acciones lo avisan al instante en lugar de quedarse esperando al servidor.
- **Configuración y logo sin conexión**: los formatos PDF/Excel usan la última configuración descargada en esa PC (nombre del ayuntamiento, municipio, logo); la contraseña SMTP nunca se copia a la PC.
- **Fotos tomadas sin conexión**: se guardan en la PC y se suben solas al almacenamiento compartido en cuanto vuelve la conexión, para que las demás PCs las vean.
- **Sincronización**: en cuanto la app detecta que la base de datos real volvió a estar disponible (revisa cada minuto), sube automáticamente todo lo capturado offline — bienes, movimientos, categorías, conteos físicos, documentos (resguardos, préstamos, comodatos, actas) y entradas de auditoría — en el mismo orden en que se hizo. Un aviso confirma cuántos cambios se sincronizaron. Un resguardo, préstamo o comodato hecho sin conexión se vuelve a validar contra el servidor: si mientras tanto otra PC puso el bien en resguardo, lo prestó, lo dio en comodato o lo dio de baja, no se sube (no quedan dos documentos vigentes del mismo bien) y el motivo aparece en **Configuración → Errores offline**.
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
│   │   │   ├── util/              # NotificacionUtil, DialogUtil (badges, dialogs), AnimationUtils, EmptyStateUtil, FormatUtils
│   │   │   ├── session/           # SessionManager (usuario activo, áreas accesibles)
│   │   │   └── config/            # Áreas del organigrama y configuración de BD
│   │   └── resources/
│   │       ├── fxml/              # 18 vistas de la interfaz
│   │       ├── css/               # styles.css importa css/partes/00–21 (paleta institucional guinda/dorado/verde);
│   │       │                      # EscalaTexto genera al arrancar la hoja de "Texto: Grande / Extra"
│   │       ├── db/migration/      # Migraciones Flyway V1–V31, se aplican solas al arrancar
│   │       ├── offline.sql        # Esquema del almacén SQLite offline
│   │       └── seed_demo.sql      # Datos de ejemplo (solo desarrollo, nunca producción)
│   └── test/java/com/sibim/
│       ├── controller/            # Tests de lógica de filtros y navegación
│       ├── db/integration/        # Tests contra EmbeddedPostgres real
│       ├── db/offline/            # Tests del almacén offline (caducidad, outbox)
│       ├── model/                 # Tests de entidades
│       ├── repository/            # Tests de autorización de repositorios
│       ├── service/               # Tests unitarios + autorización + exports 
│       ├── session/               # Tests de SessionManager
│       └── util/                  # Tests de utilidades
├── packaging/
│   ├── build-installer.ps1        # Genera el instalador .exe/.msi firmado con jpackage
│   ├── preparar-conexion.ps1      # Escribe sibim.env (sibim_app) para el instalador
│   └── limpiar-env.ps1            # Quita secretos del .env de una PC sin mostrarlos
├── scripts/sql/
│   ├── rol_app_minimo.sql         # Rol sibim_app: sin DDL, bitácora solo crece, RLS
│   ├── rol_areas.sql              # Rol sibim_areas: lo que hace un área, nada más
│   ├── storage_fotos.sql          # Políticas del bucket: fotos solo se agregan, fichas se reemplazan
│   └── revisar_nomenclatura.sql   # Revisión de solo lectura de códigos, transferencias y bajas
├── maven-dist/                    # Maven embebido (no requiere Maven instalado)
├── iniciar.bat                    # Arranque para desarrollo (compila y ejecuta)
├── produccion.bat                 # Arranque para producción (solo ejecuta el JAR)
└── pom.xml                        # Dependencias y configuración de build
```

---

## Módulos

| Módulo | Descripción |
|---|---|
| Inicio | 4 mini-tarjetas de estado con `ProgressBar` animada; gráfica de movimientos semanal; gráfica por categoría; barras de distribución top-5 áreas; contadores animados; "Pendientes de tu área" para las áreas |
| Inventario | Búsqueda, filtros combinables, presets guardables, importación CSV masiva (Admin), paginación, conteo físico (Admin), baja con solicitud firmada (Admin) o "Solicitar baja" (áreas), botones Ficha / Etiqueta / QR, fotos que se comparten entre PCs y panel de depreciación en el detalle |
| Movimientos | Entradas / salidas / ajustes / transferencias, registradas solo por el administrador y aplicadas al instante (el área que recibe una transferencia solo da el acuse de recibo); eliminar un movimiento deshace su efecto (una transferencia regresa el bien a su área con un código de esa área). Las solicitudes de transferencia antiguas que queden se resuelven desde "Pendientes", que solo aparece si hay alguna |
| Conteo físico | Pestañas Todos / Pendientes / Vistos / Con incidencia; clic en la fila para marcarla vista; escaneo con pistola; borrador en la PC; ajustes auditados al finalizar (solo Admin) |
| Bajas | Pantalla dedicada: bajas con folio de solicitud, dictamen, destino y acta; filtros, exportación, acta, ficha y reactivación (Admin) |
| Alertas | Garantías vencidas/por vencer, mantenimientos próximos, comodatos vencidos y pendientes patrimoniales (sin resguardante / sin etiqueta); búsqueda en tiempo real (Ctrl+F); export a PDF, Excel y CSV |
| Categorías | Gestión de clasificaciones con selector de color e ícono predefinidos (paleta de swatches) |
| Organigrama | 4 tarjetas de resumen; barras animadas top-5 áreas; vista árbol / tabla / tarjetas (toggle); tarjetas con borde de acento por nivel de alertas; diálogo por área con tabs Bienes / Préstamos / Comodatos / Actas; acceso directo al Inventario filtrado por área |
| Reportes | Exportación multi-formato con selector de período (PDF, Excel, CSV); encabezado institucional configurable |
| Depreciación | Tarjetas de valor compra/actual/% promedio/totalmente depreciados; distribución en 4 rangos como barras animadas; columna visual `ProgressBar` en la tabla; gráfica de proyección a 10 años; export PDF/Excel/fichas en lote |
| Auditoría | Registro de acciones de todo el sistema (bienes, movimientos, usuarios, resguardos, préstamos, actas, configuración, inicios de sesión y quién usa cada cuenta compartida); filtros en una barra por entidad/acción/usuario/fechas, en palabras y con lo que la bitácora tiene; acciones como píldoras; tarjetas de resumen; export PDF/Excel/CSV (solo Admin) |
| Configuración | Datos institucionales editables (nombre, municipio, responsable, correo); áreas y prefijos de código; gestión de cuentas con activar/desactivar y cuentas por área de un clic; historial de auditoría; conteos físicos; respaldo/restauración cifrado (solo Admin); publicar actualización; accesibilidad (animaciones) |

---

## Roles de usuario

| Rol | Permisos |
|---|---|
| **Admin (Patrimonio)** | Hace todo: alta, baja y reactivación de bienes, movimientos (y revertirlos), conteo físico, importación, código patrimonial y valores contables, usuarios, áreas y prefijos, configuración, respaldos, auditoría y reportes globales |
| **Secretario** | Ve su secretaría y las direcciones que dependen de ella. De los bienes asignados ahí solo **actualiza cuatro cosas**: ubicación, descripción, estado físico y fotos. No da de alta ni de baja (genera la **solicitud de baja**), no registra movimientos, y no cambia el código, el resguardante ni los valores contables. Ve los importes |
| **Dirección** | Ve solo su dirección. Mismas reglas que Secretario, pero sin ver importes en pesos (tampoco en los formatos que imprime, como la solicitud y el dictamen de baja) |

Estas reglas se aplican en los servicios (no solo ocultando botones), así que ninguna pantalla ni atajo puede saltárselas, tampoco al resolver un conflicto sin conexión: si un área elige "usar mi versión", solo se aplican los cuatro campos que puede editar.

Las cuentas de las áreas suelen ser **compartidas** (ver "Cuentas por área"): su contraseña la cambia Patrimonio, no quien la usa, y cada inicio de sesión confirma quién está usando la cuenta.

---

## Rol de base de datos con mínimo privilegio

Cada PC se conecta directo a PostgreSQL con las credenciales de su `.env`. Si esas credenciales son las del dueño de la base (`postgres`), cualquiera que abra el archivo puede borrar tablas, darse rol de Admin o editar la bitácora, y los permisos por rol de la app no lo impiden.

**Forma corta (instalador que se conecta solo):** en la PC de administración corre `powershell -ExecutionPolicy Bypass -File packaging\preparar-conexion.ps1`. Genera la contraseña de `sibim_app`, escribe `packaging\sibim.env` (git lo ignora), copia el SQL de abajo con esa contraseña al portapapeles y abre el SQL Editor de Supabase para pegarlo; luego comprueba la conexión y genera el instalador. Una PC instalada con él solo pide el usuario y la contraseña de la cuenta de su área. Una PC que ya tenga su propio `%APPDATA%\SIBIM\.env` sigue usando ese: bórralo para que use el del instalador (salvo en la PC de administración).

Paso a paso, para que las PCs solo puedan leer y escribir datos:

1. Edita la contraseña en `scripts/sql/rol_app_minimo.sql` y ejecútalo **una vez** como `postgres` (SQL Editor de Supabase o `psql`). Crea el rol `sibim_app`:
   - sin permisos de DDL (no puede crear, alterar ni borrar tablas);
   - la bitácora `audit_log` solo acepta inserciones (ni `UPDATE` ni `DELETE`);
   - el historial de Flyway es de solo lectura.

   Además quita a los roles `anon`/`authenticated` de Supabase el acceso por la API REST a las tablas de SIBIM, que la app no usa.
2. En cada PC de trabajo ejecuta `packaging\configurar-sibim.ps1` (sin parámetros): escribe `%APPDATA%\SIBIM\.env` con `DB_USER=sibim_app.<ref-del-proyecto>`, la contraseña del paso 1 y `DB_MIGRATE=false`. Con `DB_MIGRATE=false` la app no intenta migrar el esquema al arrancar; solo verifica que esté al día.

   Si editas el `.env` a mano, **quita `user=` y `password=` del `DB_URL`**: el driver de PostgreSQL los prefería sobre `DB_USER`/`DB_PASSWORD`, así que con ellos ahí la PC seguía entrando como `postgres` aunque `DB_USER` dijera otra cosa. La versión actual los ignora y lo avisa en el log, pero conviene dejar la URL limpia.
3. Deja **una** PC de administración con el usuario `postgres` (`configurar-sibim.ps1 -Admin`). Al instalar una versión nueva, ábrela primero ahí para que aplique las migraciones; las actualizaciones también se publican desde esa PC (con `sibim_app` no se puede escribir en `actualizaciones`). Si una PC con `DB_MIGRATE=false` arranca antes, trabaja en modo offline, avisa que la base necesita actualizarse y no vuelve a modo en línea hasta que el esquema esté al día. Lo mismo aplica a cualquier PC que arranque sin conexión: al reconectarse verifica o aplica las migraciones antes de sincronizar.
4. Vuelve a ejecutar el script después de cada versión que agregue tablas.

`src/test/java/com/sibim/db/integration/RolAppMinimoIntegrationTest.java` verifica el script contra un PostgreSQL real.

### Usuario de las áreas (`sibim_areas`)

`sibim_app` puede leer y escribir cualquier dato, y su contraseña va en el instalador. `scripts/sql/rol_areas.sql` crea `sibim_areas`, que puede lo mismo que hace un área en SIBIM y nada más: lee todo; actualiza ubicación, descripción, estado físico y fotos de un bien; confirma la recepción de una transferencia; cambia la contraseña de cuentas que no son de administrador; guarda resguardos por área, folios y su rastro (bitácora, intentos de acceso, equipos, errores). No puede dar de alta, borrar ni revaluar bienes, moverlos de área, registrar movimientos ni tocar usuarios o configuración. Cuando la sesión es de un área, la app guarda un bien escribiendo solo esas cuatro columnas (también al sincronizar lo capturado sin conexión).

Como todas las PCs reciben el mismo instalador por actualización, el instalador lleva `sibim_areas` y las PCs de Patrimonio usan su propio `%APPDATA%\SIBIM\.env` con `sibim_app`:

1. En la PC de administración: `packaging\preparar-conexion.ps1 -Areas` (sube antes la versión del `pom.xml` si la actual ya se repartió). Guarda la conexión de `sibim_app` en `packaging\conexion-patrimonio.env` (git lo ignora), pone `rol_areas.sql` con su contraseña en el portapapeles para pegarlo en Supabase, comprueba la conexión y genera el instalador.
2. En cada PC de Patrimonio, **antes** de publicar: `packaging\configurar-sibim.ps1 -Archivo <USB>\conexion-patrimonio.env`.
3. Publica la actualización. Si un administrador entra desde una PC de área, SIBIM le avisa que ahí solo puede consultar.

`RolAreasIntegrationTest` ejecuta los flujos de un área con `sibim_areas` y comprueba lo que no puede hacer.

---

## Respaldo de la base de datos

Desde **Configuración → Respaldo y restauración** (solo Admin, y solo conectado a la base de datos real — no funciona en modo offline/demo) se puede exportar toda la base a un único archivo cifrado, o restaurar la base completa desde uno: la restauración corre dentro de una sola transacción, así que si algo falla no deja la base a medias. Es una herramienta manual pensada para respaldos puntuales antes de un cambio importante, no un reemplazo de un respaldo automatizado. El respaldo incluye las solicitudes de las áreas; el registro de en qué equipos entró cada cuenta no se respalda, pero se conserva al restaurar. El respaldo cubre la base de datos: las fotos y facturas de los bienes son archivos en Supabase Storage y no van en él (el respaldo guarda sus direcciones; el respaldo automático diario de abajo sí las guarda). Se toma como una foto de un mismo instante, aunque otras PCs sigan trabajando, y se escribe cifrado directo al archivo.

**Respaldo automático diario** (en la PC de administración; `pg_dump` de la misma versión mayor que el servidor o más nueva). Activo desde el 23-oct-2026 en la PC de administración, todos los días a las 14:00. Guarda la base y, en la subcarpeta `fotos`, las fotos y facturas de los bienes: cada día baja de Storage solo las nuevas (una foto nunca se reemplaza) y ninguna se borra por antigüedad. Los instaladores publicados con "Publicar actualización" no se respaldan (se rehacen desde el código):

```powershell
# pg_dump, pg_restore y psql portátiles en tools\pgsql\bin (sin instalar PostgreSQL; git lo ignora)
.\scripts\instalar_pg_dump.ps1

# Respaldo ahora (lee la conexión de %APPDATA%\SIBIM\.env; guarda en %APPDATA%\SIBIM\respaldos, conserva 30 días)
.\scripts\respaldo_diario.ps1

# Registrar la tarea programada de Windows y hacer un primer respaldo para comprobarla;
# -CopiaEn copia además lo nuevo a una USB o carpeta de red cuando está conectada
.\scripts\respaldo_diario.ps1 -Registrar -Hora 14:00 -CopiaEn E:\SIBIM-respaldos
```

**Restaurar** (`scripts\restaurar_respaldo.ps1`). Sin opciones solo revisa el respaldo más reciente: cuántos bienes, resguardos y fotos trae, sin conectarse a nada. `-Restaurar` reemplaza la base con el respaldo, en una sola transacción (si algo falla, la base queda como estaba), y al final compara los registros de cada tabla con los del respaldo; pide escribir RESTAURAR y necesita la cuenta dueña de las tablas (postgres). Antes, cierra SIBIM en todas las PCs. `-Fotos` sube a Storage las fotos del respaldo que ya no estén ahí, sin reemplazar ninguna:

```powershell
.\scripts\restaurar_respaldo.ps1                       # revisar
.\scripts\restaurar_respaldo.ps1 -Restaurar            # el más reciente, en la base del .env de esta PC
.\scripts\restaurar_respaldo.ps1 -Restaurar -Respaldo E:\SIBIM-respaldos\sibim-20261024-1400.dump
.\scripts\restaurar_respaldo.ps1 -Fotos -Carpeta E:\SIBIM-respaldos
# -Conexion <archivo .env> restaura en otra base (una de prueba, o un servidor nuevo)
```

El respaldo incluye los hashes de contraseña y todo el inventario: guárdalo en una carpeta a la que solo tenga acceso quien administra el sistema y copia la carpeta fuera de esa PC (`-CopiaEn`).

El esquema se gestiona con **Flyway** (`src/main/resources/db/migration/`), aplicado automáticamente en cada arranque — no hace falta correr nada a mano. Para un cambio de esquema futuro: agrega un archivo nuevo `V32__descripcion.sql` (numeración consecutiva a partir de V31) a esa carpeta con el `ALTER TABLE`/`CREATE TABLE IF NOT EXISTS` correspondiente; Flyway se encarga de aplicarlo una sola vez por base de datos y de no volver a tocarlo. No edites migraciones ya publicadas — Flyway rechaza cualquier migración aplicada si su contenido cambia. El arranque **no** ejecuta `flyway.repair()` por su cuenta (reescribiría el historial sin correr el SQL y escondería choques de numeración): si una migración aplicada cambió a propósito, agrega `FLYWAY_AUTO_REPAIR=true` al `.env` una sola vez, reinicia y quítalo (ver `.env.example`).

---

## Tests

```bash
# Correr todos los tests (1132 en total; los de interfaz abren ventanas unos minutos)
maven-dist/apache-maven-3.9.9/bin/mvn.cmd test

# Solo tests de una clase
maven-dist/apache-maven-3.9.9/bin/mvn.cmd test -Dtest=AuthServiceTest
```

Los tests de integración (`db/integration/`) levantan una instancia efímera de PostgreSQL con EmbeddedPostgres — no requieren ninguna instalación externa. Los tests de autorización verifican que los guards de seguridad lanzan `SecurityException` para roles sin permiso, independientemente de si hay BD disponible.

**Los tests nunca tocan la instalación real**, aunque la app esté instalada en la misma PC: Maven los corre con `APPDATA` y `user.home` apuntando a `target/` (no ven `%APPDATA%\SIBIM\.env` ni `~/.sibim`) y con las preferencias de Java en memoria (no escriben en el registro de Windows). Además, `DatabaseConfig` se niega a conectarse a una base remota mientras corre bajo tests. Antes de este aislamiento, una corrida de `mvn test` restauró un respaldo de prueba sobre la base de producción.

---

## Licencia

Proyecto desarrollado para uso interno del **H. Ayuntamiento de Ixmiquilpan, Hidalgo**.
Todos los derechos reservados.
