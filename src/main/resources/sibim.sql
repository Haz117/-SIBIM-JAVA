-- ============================================================
-- SIBIM Desktop - Schema SQL (compatible con el schema de Supabase)
-- Ejecuta este script en tu PostgreSQL local si no tienes Supabase
--
-- Este script es seguro de volver a correr contra una base de datos
-- existente (CREATE TABLE IF NOT EXISTS + el bloque DO $$ ... ALTER TABLE
-- IF NOT EXISTS al final agrega columnas nuevas sin tocar las existentes).
-- No hay una herramienta de migraciones (Flyway/Liquibase) — schema_version
-- abajo es solo un registro simple de qué versión de este script se aplicó
-- por última vez a esta base de datos, para poder auditarlo a mano.
-- ============================================================

CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER PRIMARY KEY,
    applied_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    descripcion TEXT
);
INSERT INTO schema_version (version, descripcion) VALUES
    (1, 'Esquema inicial: users, categories, products, movements, audit_log, conteos')
ON CONFLICT (version) DO NOTHING;

CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY,
    username    TEXT UNIQUE NOT NULL,
    password    TEXT NOT NULL,
    nombre      TEXT NOT NULL,
    cargo       TEXT,
    role        TEXT NOT NULL DEFAULT 'direccion' CHECK (role IN ('admin','secretario','direccion')),
    area        TEXT,
    -- Forces the "cambiar contraseña" dialog on next login — set TRUE for
    -- any account whose password is a known default (seed accounts, admin
    -- resets), so a temporary/known password can never be the one actually
    -- protecting the account past first use.
    debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS categories (
    id          TEXT PRIMARY KEY,
    nombre      TEXT UNIQUE NOT NULL,
    descripcion TEXT,
    color       TEXT NOT NULL DEFAULT '#3B82F6',
    icono       TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS products (
    id                  TEXT PRIMARY KEY,
    nombre              TEXT NOT NULL,
    codigo              TEXT UNIQUE NOT NULL,
    descripcion         TEXT,
    categoria_id        TEXT NOT NULL REFERENCES categories(id),
    precio_compra       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (precio_compra >= 0),
    precio_venta        NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (precio_venta >= 0),
    stock_actual        INTEGER NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
    stock_minimo        INTEGER NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
    stock_maximo        INTEGER NOT NULL DEFAULT 100 CHECK (stock_maximo >= 0),
    unidad              TEXT NOT NULL DEFAULT 'pieza',
    proveedor           TEXT,
    fecha_vencimiento   DATE,
    foto_url            TEXT,
    ubicacion           TEXT,
    area                TEXT NOT NULL,
    -- Persona física responsable del resguardo del bien (distinto del área) —
    -- requerido en auditorías patrimoniales municipales mexicanas.
    resguardante        TEXT,
    -- Soft-delete: a formal "baja patrimonial" (decommission) sets these
    -- instead of a physical DELETE, so movement history stays intact for
    -- audits. NULL fecha_baja = still active inventory.
    fecha_baja          DATE,
    motivo_baja         TEXT,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS movements (
    id              TEXT PRIMARY KEY,
    producto_id     TEXT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    tipo            TEXT NOT NULL CHECK (tipo IN ('entrada','salida','ajuste','transferencia')),
    cantidad        INTEGER NOT NULL CHECK (cantidad >= 0),
    stock_anterior  INTEGER NOT NULL CHECK (stock_anterior >= 0),
    stock_nuevo     INTEGER NOT NULL CHECK (stock_nuevo >= 0),
    -- Only populated for tipo='transferencia': the area the product moved
    -- FROM and TO. Needed so deleting a transfer movement can put the
    -- product back in its original area, not just restore its stock count.
    area_origen     TEXT,
    area_destino    TEXT,
    motivo          TEXT,
    referencia      TEXT,
    usuario_id      TEXT REFERENCES users(id),
    usuario_nombre  TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Generic change audit trail — covers entities that don't already have their
-- own history (products/categories/users edits, altas, bajas). Movements
-- keep being their own audit trail for stock changes (movements table); this
-- is for "who changed what" on everything else.
CREATE TABLE IF NOT EXISTS audit_log (
    id              TEXT PRIMARY KEY,
    entidad         TEXT NOT NULL,   -- 'producto' | 'categoria' | 'usuario'
    entidad_id      TEXT NOT NULL,
    entidad_nombre  TEXT,
    accion          TEXT NOT NULL,   -- 'crear' | 'actualizar' | 'eliminar' | 'baja' | 'reactivar'
    detalle         TEXT,
    usuario_id      TEXT REFERENCES users(id),
    usuario_nombre  TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Physical inventory count sessions (toma de inventario) — persisted as
-- their own entity (not just the resulting Ajuste movements) so a completed
-- conteo can be reviewed later: exactly what was reviewed, what matched, and
-- what didn't, even for items that turned out with no discrepancy.
CREATE TABLE IF NOT EXISTS conteos_fisicos (
    id                    TEXT PRIMARY KEY,
    usuario_id            TEXT REFERENCES users(id),
    usuario_nombre        TEXT NOT NULL,
    total_contados        INTEGER NOT NULL DEFAULT 0,
    total_discrepancias   INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS conteo_items (
    id              TEXT PRIMARY KEY,
    conteo_id       TEXT NOT NULL REFERENCES conteos_fisicos(id) ON DELETE CASCADE,
    producto_id     TEXT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    producto_nombre TEXT NOT NULL,
    area            TEXT,
    stock_sistema   INTEGER NOT NULL,
    stock_contado   INTEGER NOT NULL,
    ajustado        BOOLEAN NOT NULL DEFAULT FALSE
);

-- Indices
CREATE INDEX IF NOT EXISTS idx_products_area        ON products(area);
CREATE INDEX IF NOT EXISTS idx_products_categoria   ON products(categoria_id);
CREATE INDEX IF NOT EXISTS idx_products_codigo      ON products(codigo);
CREATE INDEX IF NOT EXISTS idx_products_vencimiento ON products(fecha_vencimiento);
CREATE INDEX IF NOT EXISTS idx_products_area_created ON products(area, created_at);
CREATE INDEX IF NOT EXISTS idx_movements_producto   ON movements(producto_id);
CREATE INDEX IF NOT EXISTS idx_movements_created    ON movements(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_movements_usuario    ON movements(usuario_id);
CREATE INDEX IF NOT EXISTS idx_audit_entidad        ON audit_log(entidad, entidad_id);
CREATE INDEX IF NOT EXISTS idx_audit_created        ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_conteo_items_conteo  ON conteo_items(conteo_id);
CREATE INDEX IF NOT EXISTS idx_conteos_created       ON conteos_fisicos(created_at DESC);

-- ─── MIGRACIÓN IDEMPOTENTE PARA BASES DE DATOS YA EXISTENTES ─────────────
-- No hay herramienta de migraciones (Flyway/Liquibase) en este proyecto —
-- CREATE TABLE IF NOT EXISTS no aplica cambios a una BD ya creada. Estos
-- bloques agregan los CHECK de integridad nuevos a una instalación existente
-- si aún no los tiene; son seguros de volver a ejecutar (no fallan si el
-- constraint ya existe). Vuelve a correr este script completo contra la BD
-- de producción para aplicar estos cambios.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'users_role_check') THEN
        ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('admin','secretario','direccion'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_precio_compra_check') THEN
        ALTER TABLE products ADD CONSTRAINT products_precio_compra_check CHECK (precio_compra >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_precio_venta_check') THEN
        ALTER TABLE products ADD CONSTRAINT products_precio_venta_check CHECK (precio_venta >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_stock_actual_check') THEN
        ALTER TABLE products ADD CONSTRAINT products_stock_actual_check CHECK (stock_actual >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_stock_minimo_check') THEN
        ALTER TABLE products ADD CONSTRAINT products_stock_minimo_check CHECK (stock_minimo >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'products_stock_maximo_check') THEN
        ALTER TABLE products ADD CONSTRAINT products_stock_maximo_check CHECK (stock_maximo >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'movements_cantidad_check') THEN
        ALTER TABLE movements ADD CONSTRAINT movements_cantidad_check CHECK (cantidad >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'movements_stock_anterior_check') THEN
        ALTER TABLE movements ADD CONSTRAINT movements_stock_anterior_check CHECK (stock_anterior >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'movements_stock_nuevo_check') THEN
        ALTER TABLE movements ADD CONSTRAINT movements_stock_nuevo_check CHECK (stock_nuevo >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'movements' AND column_name = 'area_origen') THEN
        ALTER TABLE movements ADD COLUMN area_origen TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'movements' AND column_name = 'area_destino') THEN
        ALTER TABLE movements ADD COLUMN area_destino TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'products' AND column_name = 'fecha_baja') THEN
        ALTER TABLE products ADD COLUMN fecha_baja DATE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'products' AND column_name = 'motivo_baja') THEN
        ALTER TABLE products ADD COLUMN motivo_baja TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'products' AND column_name = 'resguardante') THEN
        ALTER TABLE products ADD COLUMN resguardante TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'users' AND column_name = 'debe_cambiar_password') THEN
        ALTER TABLE users ADD COLUMN debe_cambiar_password BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- ─── DATOS DE EJEMPLO ───────────────────────────────────────────────
-- Los datos de prueba (usuarios, categorías, productos de muestra) NO viven
-- en este archivo — este script es el esquema real, seguro de correr contra
-- una base de datos de producción. Los datos de ejemplo están en
-- seed_demo.sql, que es opcional y NUNCA debe ejecutarse contra producción.
