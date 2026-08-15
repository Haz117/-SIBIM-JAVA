-- ============================================================
-- SIBIM Desktop - Schema SQL (compatible con el schema de Supabase)
-- Ejecuta este script en tu PostgreSQL local si no tienes Supabase
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          TEXT PRIMARY KEY,
    username    TEXT UNIQUE NOT NULL,
    password    TEXT NOT NULL,
    nombre      TEXT NOT NULL,
    cargo       TEXT,
    role        TEXT NOT NULL DEFAULT 'direccion',
    area        TEXT,
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
    precio_compra       NUMERIC(12,2) NOT NULL DEFAULT 0,
    precio_venta        NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock_actual        INTEGER NOT NULL DEFAULT 0,
    stock_minimo        INTEGER NOT NULL DEFAULT 0,
    stock_maximo        INTEGER NOT NULL DEFAULT 100,
    unidad              TEXT NOT NULL DEFAULT 'pieza',
    proveedor           TEXT,
    fecha_vencimiento   DATE,
    foto_url            TEXT,
    ubicacion           TEXT,
    area                TEXT NOT NULL,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS movements (
    id              TEXT PRIMARY KEY,
    producto_id     TEXT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    tipo            TEXT NOT NULL CHECK (tipo IN ('entrada','salida','ajuste','transferencia')),
    cantidad        INTEGER NOT NULL,
    stock_anterior  INTEGER NOT NULL,
    stock_nuevo     INTEGER NOT NULL,
    motivo          TEXT,
    referencia      TEXT,
    usuario_id      TEXT REFERENCES users(id),
    usuario_nombre  TEXT NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Indices
CREATE INDEX IF NOT EXISTS idx_products_area        ON products(area);
CREATE INDEX IF NOT EXISTS idx_products_categoria   ON products(categoria_id);
CREATE INDEX IF NOT EXISTS idx_products_codigo      ON products(codigo);
CREATE INDEX IF NOT EXISTS idx_products_vencimiento ON products(fecha_vencimiento);
CREATE INDEX IF NOT EXISTS idx_movements_producto   ON movements(producto_id);
CREATE INDEX IF NOT EXISTS idx_movements_created    ON movements(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_movements_usuario    ON movements(usuario_id);

-- ─── DATOS INICIALES ───────────────────────────────────────────────

-- Admin user (password: admin123456)
INSERT INTO users (id, username, password, nombre, cargo, role, area) VALUES
('u-admin', 'superusuario',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj1o.FxRzFNS',
 'Administrador del Sistema', 'Superusuario', 'admin', NULL)
ON CONFLICT (id) DO NOTHING;

-- Secretario (password: sec123456)
INSERT INTO users (id, username, password, nombre, cargo, role, area) VALUES
('u-sec-1', 'jm.zuniga',
 '$2a$12$RfQKHBQQlnBqnUjefY0n9.5Z2hxPzG4A1k2ZOBqFr9QQZJPBDhQBu',
 'Jose Manuel Zuniga Guerrero', 'Secretario General Municipal',
 'secretario', 'Secretaria General Municipal')
ON CONFLICT (id) DO NOTHING;

-- Direccion (password: dir123456)
INSERT INTO users (id, username, password, nombre, cargo, role, area) VALUES
('u-dir-1', 'rh.garcia',
 '$2a$12$n8TWbDFZ2F/Q3VdTHxuvTO.7OP7f/bJYKMXPJRb5LTkmHlKxhALiu',
 'Maria Garcia Lopez', 'Director de Recursos Humanos',
 'direccion', 'Direccion de Recursos Humanos')
ON CONFLICT (id) DO NOTHING;

-- Categorias
INSERT INTO categories (id, nombre, descripcion, color, icono) VALUES
('cat-mob', 'Mobiliario',         'Escritorios, sillas, archiveros',        '#8B5CF6', 'Armchair'),
('cat-veh', 'Vehiculos',          'Flota vehicular municipal',               '#3B82F6', 'Car'),
('cat-comp','Equipo de Computo',  'Laptops, computadoras, perifericos',      '#10B981', 'Laptop'),
('cat-ofi', 'Equipo de Oficina',  'Impresoras, copiadoras',                  '#F59E0B', 'Printer'),
('cat-maq', 'Herramientas y Maquinaria', 'Maquinaria pesada y herramientas', '#EF4444', 'Wrench'),
('cat-av',  'Equipo Audiovisual', 'Camaras, proyectores, pantallas',         '#EC4899', 'VideoCamera')
ON CONFLICT (id) DO NOTHING;

-- Productos de muestra
INSERT INTO products (id, nombre, codigo, categoria_id, precio_compra, precio_venta,
    stock_actual, stock_minimo, stock_maximo, unidad, area) VALUES
('p-01', 'Escritorio ejecutivo de madera', 'MB-001', 'cat-mob',  4500, 5000, 18, 5,  30, 'pieza', 'Secretaria General Municipal'),
('p-02', 'Silla ejecutiva ergonomica',     'MB-002', 'cat-mob',  3200, 3800,  6, 8,  40, 'pieza', 'Direccion de Recursos Humanos'),
('p-03', 'Archivero metalico 4 gavetas',   'MB-003', 'cat-mob',  2800, 3200,  0, 3,  20, 'pieza', 'Secretaria General Municipal'),
('p-04', 'Camioneta pick-up Ford Ranger',  'VH-001', 'cat-veh', 380000,420000, 1, 1,  5, 'unidad','Secretaria de Obras Publicas y Desarrollo Urbano'),
('p-05', 'Laptop Dell Latitude 5440',      'EC-001', 'cat-comp', 22000,25000, 12, 5,  30, 'equipo','Secretaria de Planeacion y Evaluacion'),
('p-06', 'Impresora multifuncional Epson', 'EO-001', 'cat-ofi',  8500, 9500,  2, 2,  10, 'equipo','Secretaria General Municipal'),
('p-07', 'Retroexcavadora CAT 420',        'HM-001', 'cat-maq',850000,900000, 1, 1,   3, 'unidad','Secretaria de Obras Publicas y Desarrollo Urbano'),
('p-08', 'Camara de videovigilancia PTZ',  'AV-001', 'cat-av',   4800, 5500, 22, 5,  50, 'pieza', 'Secretaria de Seguridad Publica Municipal')
ON CONFLICT (id) DO NOTHING;
