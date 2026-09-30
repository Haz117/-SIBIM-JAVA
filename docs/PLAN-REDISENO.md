# Plan de rediseño — SIBIM Desktop

Estado: **propuesta**, sin empezar. Este documento fija el rumbo para los cambios de arquitectura que no caben en un arreglo puntual. Cada fase deja la app funcionando y con los tests en verde; se puede detener entre fases sin dejar nada a medias.

Tres objetivos:

1. **Una sola versión de cada operación.** Hoy la misma regla de negocio está escrita hasta tres veces.
2. **Dependencias inyectadas.** Hoy cada pantalla fabrica sus propios servicios y todo cuelga de estáticos globales.
3. **Una API intermedia** entre las PCs y la base de datos. Hoy cada PC entra directo a PostgreSQL.

---

## 1. Dónde estamos (medido el 2026-09-26)

| Síntoma | Dato | Consecuencia |
|---|---|---|
| Cada consulta existe en 3 versiones: SQL (`*Repository`), demo (`DemoDataStore`) y offline (`OfflineStore`) | 50 ramas `isDemoMode()` y 83 ramas `getLocalDataStore()` dentro de los repositorios; `DemoDataStore` + `OfflineStore` suman ~2 400 líneas | Las versiones se desalinean. Ejemplo real, ya corregido: el Dashboard contaba "agotados" con `getStats()`, que excluye los bienes vencidos, y el menú lateral con `findAgotados()`, que los incluye, así que los números no cuadraban. |
| Regla de negocio repartida | `siguienteCodigo`, stock y transferencias se validan en el servicio, en el repositorio SQL y otra vez en el almacén offline | Un arreglo en un lugar no llega a los otros dos. |
| Sin inyección de dependencias | 78 `new XService()` / `new XRepository()` dentro de controladores; singletons `ReporteService.getInstance()` (11 usos) y `MainController.getInstance()` (30 usos) | No se puede probar un controlador sin base de datos o sin modo demo global, y los tests se pisan entre sí por el estado estático (ver la memoria de tests con Splash/Sync). |
| Acceso directo a la BD desde cada PC | 123 llamadas a `DatabaseConfig.getConnection()`; cada `.env` trae credenciales de PostgreSQL | Quien abra el `.env` tiene la base (mitigado, no resuelto, con `rol_app_minimo.sql`). Las migraciones dependen de qué PC abra primero. La sincronización offline resuelve conflictos fila por fila en el cliente. |

---

## 2. A dónde vamos

```
 JavaFX (controladores)                    ← solo presentación
        │  recibe servicios por constructor
        ▼
 Servicios de dominio (ProductoService…)   ← ÚNICA versión de cada regla
        │  usa puertos (interfaces)
        ▼
 Puertos: ProductoStore, MovimientoStore…  ← una interfaz por agregado
   ├── ApiStore      (HTTP → API SIBIM)       en línea
   ├── SqliteStore   (offline.db)             sin conexión
   └── MemoryStore   (demo y tests)           demo / pruebas
```

### 2.1 Una sola versión de cada operación

- Las reglas (validar stock, asignar código `PREF/NN`, aprobar transferencias, calcular alertas) viven **solo** en los servicios de dominio.
- Los almacenes (`*Store`) solo guardan y leen: nada de `if (demo)` ni `if (offline)` adentro.
- El modo lo decide **un** lugar al arrancar (la composición, §2.2), que elige qué almacén conectar.
- Las consultas derivadas ("alertas", "agotados", "stats") se definen una vez sobre el mismo filtro. Mientras sigan existiendo en SQL, se prueban con un test de contrato que corre la misma batería contra los tres almacenes.

### 2.2 Dependencias inyectadas

- Constructor con sus dependencias en cada servicio y controlador. Sin framework: una clase `AppContext` arma el grafo al arrancar (unas 60 líneas), y el `FXMLLoader` recibe `setControllerFactory(ctx::controllerFor)`.
- Desaparecen `ReporteService.getInstance()` y `MainController.getInstance()`. La navegación pasa a un `Navigator` inyectado.
- `DatabaseConfig` deja de ser estático. Su estado (demo, offline, pool) pasa a un objeto `Conexion` que se inyecta, y así los tests ya no comparten estado global.

### 2.3 API intermedia

- Un servicio HTTP pequeño (Java 21 + Javalin o Spring Boot, a decidir) al lado de la base de datos. Es el **único** que tiene credenciales de PostgreSQL y el único que corre migraciones Flyway.
- Las PCs se autentican con usuario y contraseña contra la API y reciben un token de sesión. El bloqueo por intentos (V23) y los permisos por área se aplican en el servidor.
- La API expone operaciones de negocio, no tablas: `POST /movimientos`, `POST /transferencias/{id}/aprobar`, `GET /alertas`… La sincronización offline envía la cola de operaciones y la API resuelve los conflictos con el mismo servicio de dominio.
- El código de dominio (§2.1) es un módulo Maven compartido: la API y el cliente usan **el mismo** `ProductoService`.

---

## 3. Fases

Cada fase es un PR (o varios pequeños) que no cambia lo que ve el usuario.

| Fase | Qué se hace | Cómo se sabe que terminó |
|---|---|---|
| **0. Red de seguridad** | Tests de contrato por almacén: la misma batería (alta, movimiento, transferencia, alertas, stats) contra SQL, demo y offline. | Los tres pasan la misma batería; cualquier desalineación actual queda documentada como test fallido y se corrige. |
| **1. Composición** | `AppContext` + `controllerFactory`; los controladores reciben servicios por constructor; se elimina `ReporteService.getInstance()`. | 0 `new XService()` en controladores; los tests de controladores usan servicios falsos sin `setDemoMode`. |
| **2. Puertos** | Interfaces `*Store` por agregado (Producto, Movimiento, Resguardo, Préstamo, Comodato, Acta, Usuario, Área, Auditoría); los repositorios SQL, demo y offline las implementan tal cual. | Los servicios dependen solo de interfaces. |
| **3. Una versión** | Sacar las ramas `isDemoMode()` / `getLocalDataStore()` de los repositorios: cada almacén implementa su versión y la regla sube al servicio. Empezar por Movimientos (es la más duplicada) y seguir con Productos. | 0 ramas de modo dentro de `repository/`; el test de contrato sigue verde. |
| **4. Estado global fuera** | `DatabaseConfig` y `SessionManager` pasan a ser objetos inyectados. | La suite corre en paralelo sin fallas intermitentes. |
| **5. API** | Módulo `sibim-dominio` compartido + servicio `sibim-api`. `ApiStore` en el cliente detrás de un interruptor (`MODO_DATOS=api`), convive con el acceso directo mientras se prueba en una PC. | Una PC opera un día completo solo por la API; las credenciales de PostgreSQL salen de los `.env` de las PCs. |
| **6. Retiro** | Quitar el acceso JDBC directo del cliente y el `rol_app_minimo.sql` para PCs. | El cliente ya no tiene el driver de PostgreSQL. |

---

## 4. Reglas mientras dure el rediseño

- **Nada de "big bang".** Si una fase no cabe en una semana, se parte.
- **Nueva funcionalidad = nueva forma.** Lo nuevo se escribe ya con inyección y puertos, aunque conviva con código viejo.
- **Sin cambiar datos.** Ninguna fase modifica el esquema salvo la API (fase 5), que no necesita tablas nuevas.
- **Offline no se rompe.** Cada fase se prueba también arrancando sin conexión y sincronizando al volver.
- **Los números cuadran por construcción.** Toda cifra que aparezca en más de una pantalla (alertas, pendientes, totales) sale de un solo método del servicio; si hace falta un conteo rápido, ese mismo método ofrece `contar…()`, no una segunda consulta escrita aparte.

## 5. Decisiones pendientes

- Framework de la API: Javalin (ligero, poco código) o Spring Boot (más convención, más peso).
- Dónde corre la API: el mismo servidor que la base o un servicio administrado.
- Si la fase 5 justifica cambiar la sincronización offline a una cola de operaciones en el servidor, en lugar de la comparación fila por fila actual.
