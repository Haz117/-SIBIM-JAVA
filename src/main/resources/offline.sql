-- ============================================================
-- SIBIM Desktop - Esquema del almacén local offline (SQLite)
--
-- Espejo simplificado de sibim.sql, usado únicamente cuando la app no
-- logra conectar a Postgres al arrancar. Vive en un archivo separado
-- (%USERPROFILE%\.sibim\offline.db), uno por PC.
--
-- Solo cubre las 3 entidades que participan en la sincronización
-- automática (productos, movimientos, categorías) más una caché de
-- solo-lectura de usuarios para poder iniciar sesión sin Postgres.
-- Ver com.sibim.db.offline.OfflineStore y com.sibim.db.offline.SyncService.
-- ============================================================

CREATE TABLE IF NOT EXISTS categories (
    id          TEXT PRIMARY KEY,
    nombre      TEXT UNIQUE NOT NULL,
    descripcion TEXT,
    color       TEXT NOT NULL DEFAULT '#3B82F6',
    icono       TEXT,
    created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    id                TEXT PRIMARY KEY,
    nombre            TEXT NOT NULL,
    codigo            TEXT UNIQUE NOT NULL,
    descripcion       TEXT,
    categoria_id      TEXT NOT NULL,
    precio_compra     REAL NOT NULL DEFAULT 0,
    precio_venta      REAL NOT NULL DEFAULT 0,
    stock_actual      INTEGER NOT NULL DEFAULT 0,
    stock_minimo      INTEGER NOT NULL DEFAULT 0,
    stock_maximo      INTEGER NOT NULL DEFAULT 100,
    unidad            TEXT NOT NULL DEFAULT 'pieza',
    proveedor         TEXT,
    fecha_vencimiento TEXT,
    foto_url          TEXT,
    ubicacion         TEXT,
    area              TEXT NOT NULL,
    resguardante      TEXT,
    fecha_baja        TEXT,
    motivo_baja       TEXT,
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS movements (
    id             TEXT PRIMARY KEY,
    producto_id    TEXT NOT NULL,
    tipo           TEXT NOT NULL,
    cantidad       INTEGER NOT NULL,
    stock_anterior INTEGER NOT NULL,
    stock_nuevo    INTEGER NOT NULL,
    area_origen    TEXT,
    area_destino   TEXT,
    motivo         TEXT,
    referencia     TEXT,
    usuario_id     TEXT,
    usuario_nombre TEXT NOT NULL,
    created_at     TEXT NOT NULL
);

-- Solo-lectura desde el lado offline: se llena/actualiza cada vez que la
-- app SÍ logra conectar a Postgres (ver AuthService), para que un usuario
-- ya conocido pueda seguir iniciando sesión si luego se pierde la conexión.
CREATE TABLE IF NOT EXISTS users_cache (
    id                     TEXT PRIMARY KEY,
    username               TEXT UNIQUE NOT NULL,
    password_hash          TEXT NOT NULL,
    nombre                 TEXT NOT NULL,
    rol                    TEXT NOT NULL,
    area                   TEXT,
    debe_cambiar_password  INTEGER NOT NULL DEFAULT 0
);

-- ───────────────────────────── Outbox ─────────────────────────────
-- Una fila por cada escritura hecha mientras la app estuvo offline.
-- SyncService las reproduce en orden (created_at) contra Postgres real
-- cuando vuelve la conexión, usando los métodos *Online(...) de cada
-- repository (la misma lógica de validación/locking que ya existe).

CREATE TABLE IF NOT EXISTS product_outbox (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    operacion          TEXT NOT NULL, -- SAVE | BAJA | REACTIVAR
    producto_id        TEXT NOT NULL,
    nombre             TEXT,
    codigo             TEXT,
    descripcion        TEXT,
    categoria_id       TEXT,
    precio_compra      REAL,
    precio_venta       REAL,
    stock_actual       INTEGER,
    stock_minimo       INTEGER,
    stock_maximo       INTEGER,
    unidad             TEXT,
    proveedor          TEXT,
    fecha_vencimiento  TEXT,
    foto_url           TEXT,
    ubicacion          TEXT,
    area               TEXT,
    resguardante       TEXT,
    motivo_baja        TEXT,
    created_at         TEXT NOT NULL,
    status             TEXT NOT NULL DEFAULT 'PENDING', -- PENDING | SYNCED | FAILED | CONFLICT
    error              TEXT,
    -- updated_at the product had on the server BEFORE this offline edit was made.
    -- SyncService compares it against the server's current updated_at at sync time:
    -- if the server was touched after this snapshot, another user edited it while
    -- this PC was offline and the change is marked CONFLICT instead of being applied.
    server_snapshot_at TEXT
);

CREATE TABLE IF NOT EXISTS movement_outbox (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    operacion      TEXT NOT NULL, -- ADD | DELETE
    movimiento_id  TEXT NOT NULL,
    producto_id    TEXT,
    tipo           TEXT,
    cantidad       INTEGER,
    motivo         TEXT,
    referencia     TEXT,
    area_destino   TEXT,
    usuario_id     TEXT,
    usuario_nombre TEXT,
    created_at     TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'PENDING',
    error          TEXT
);

CREATE TABLE IF NOT EXISTS category_outbox (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    operacion   TEXT NOT NULL, -- SAVE | DELETE
    categoria_id TEXT NOT NULL,
    nombre      TEXT,
    descripcion TEXT,
    color       TEXT,
    icono       TEXT,
    created_at  TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'PENDING',
    error       TEXT
);

CREATE INDEX IF NOT EXISTS idx_offline_products_area     ON products(area);
CREATE INDEX IF NOT EXISTS idx_offline_movements_producto ON movements(producto_id);
CREATE INDEX IF NOT EXISTS idx_offline_product_outbox_status  ON product_outbox(status);
CREATE INDEX IF NOT EXISTS idx_offline_movement_outbox_status ON movement_outbox(status);
CREATE INDEX IF NOT EXISTS idx_offline_category_outbox_status ON category_outbox(status);
