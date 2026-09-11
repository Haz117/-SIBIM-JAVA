-- ============================================================
-- V12: Resguardos formales, actas de entrega-recepción y préstamos temporales
-- ============================================================

-- Resguardos formales de bienes (el resguardante firma haber recibido los bienes)
CREATE TABLE IF NOT EXISTS resguardos (
    id                  TEXT PRIMARY KEY,
    numero              TEXT UNIQUE NOT NULL,
    resguardante_nombre TEXT NOT NULL,
    resguardante_cargo  TEXT,
    resguardante_area   TEXT,
    creado_por_id       TEXT REFERENCES users(id) ON DELETE SET NULL,
    creado_por_nombre   TEXT,
    observaciones       TEXT,
    estado              TEXT NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO','CANCELADO')),
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS resguardo_items (
    id              TEXT PRIMARY KEY,
    resguardo_id    TEXT NOT NULL REFERENCES resguardos(id) ON DELETE CASCADE,
    producto_id     TEXT REFERENCES products(id) ON DELETE SET NULL,
    producto_nombre TEXT NOT NULL,
    producto_codigo TEXT,
    area            TEXT,
    cantidad        INTEGER NOT NULL DEFAULT 1,
    descripcion     TEXT,
    numero_serie    TEXT,
    valor_unitario  NUMERIC(12,2)
);

-- Actas de entrega-recepción (cambio de administración)
CREATE TABLE IF NOT EXISTS actas_entrega_recepcion (
    id                TEXT PRIMARY KEY,
    numero            TEXT UNIQUE NOT NULL,
    admin_saliente    TEXT NOT NULL,
    cargo_saliente    TEXT,
    admin_entrante    TEXT NOT NULL,
    cargo_entrante    TEXT,
    fecha_entrega     DATE NOT NULL,
    observaciones     TEXT,
    total_bienes      INTEGER NOT NULL DEFAULT 0,
    valor_total       NUMERIC(18,2) NOT NULL DEFAULT 0,
    creado_por_id     TEXT REFERENCES users(id) ON DELETE SET NULL,
    creado_por_nombre TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW()
);

-- Préstamos temporales entre áreas
CREATE TABLE IF NOT EXISTS prestamos (
    id                        TEXT PRIMARY KEY,
    numero                    TEXT UNIQUE NOT NULL,
    producto_id               TEXT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    producto_nombre           TEXT NOT NULL,
    producto_codigo           TEXT,
    area_origen               TEXT NOT NULL,
    area_destino              TEXT NOT NULL,
    responsable_nombre        TEXT NOT NULL,
    responsable_cargo         TEXT,
    motivo                    TEXT,
    fecha_prestamo            DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_devolucion_prevista DATE NOT NULL,
    fecha_devolucion_real     DATE,
    estado                    TEXT NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO','DEVUELTO','VENCIDO')),
    creado_por_id             TEXT REFERENCES users(id) ON DELETE SET NULL,
    creado_por_nombre         TEXT,
    created_at                TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_resguardos_estado   ON resguardos(estado);
CREATE INDEX IF NOT EXISTS idx_resguardos_area     ON resguardos(resguardante_area);
CREATE INDEX IF NOT EXISTS idx_resguardos_created  ON resguardos(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_resguardo_items_rid ON resguardo_items(resguardo_id);
CREATE INDEX IF NOT EXISTS idx_actas_fecha         ON actas_entrega_recepcion(fecha_entrega DESC);
CREATE INDEX IF NOT EXISTS idx_prestamos_estado    ON prestamos(estado);
CREATE INDEX IF NOT EXISTS idx_prestamos_producto  ON prestamos(producto_id);
CREATE INDEX IF NOT EXISTS idx_prestamos_fecha_dev ON prestamos(fecha_devolucion_prevista);
