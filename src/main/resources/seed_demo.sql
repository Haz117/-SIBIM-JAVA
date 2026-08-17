-- ============================================================
-- SIBIM Desktop — Datos de EJEMPLO (opcional, solo para desarrollo/demo)
--
-- ⚠ NO ejecutes este archivo contra una base de datos de producción real.
-- Crea usuarios con contraseñas conocidas y públicas (documentadas abajo) y
-- productos ficticios. Está pensado únicamente para levantar el sistema
-- localmente y probarlo antes de cargar los datos reales del municipio.
--
-- Los tres usuarios de ejemplo tienen debe_cambiar_password = TRUE, así que
-- el sistema los obliga a definir una contraseña propia en su primer login
-- — pero de todas formas no los uses como cuentas reales: crea usuarios
-- nuevos desde Configuración una vez que el sistema esté en producción y
-- elimina o desactiva estos tres.
--
-- Ejecuta primero sibim.sql (esquema), luego este archivo si quieres datos
-- de prueba.
-- ============================================================

-- Administrador de ejemplo (password: admin123456)
INSERT INTO users (id, username, password, nombre, cargo, role, area, debe_cambiar_password) VALUES
('u-admin', 'superusuario',
 '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj1o.FxRzFNS',
 'Administrador del Sistema', 'Superusuario', 'admin', NULL, TRUE)
ON CONFLICT (id) DO NOTHING;

-- Secretario de ejemplo (password: sec123456)
INSERT INTO users (id, username, password, nombre, cargo, role, area, debe_cambiar_password) VALUES
('u-sec-1', 'secretario.demo',
 '$2a$12$RfQKHBQQlnBqnUjefY0n9.5Z2hxPzG4A1k2ZOBqFr9QQZJPBDhQBu',
 'Secretario de Ejemplo', 'Secretario General Municipal',
 'secretario', 'Secretaria General Municipal', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Dirección de ejemplo (password: dir123456)
INSERT INTO users (id, username, password, nombre, cargo, role, area, debe_cambiar_password) VALUES
('u-dir-1', 'direccion.demo',
 '$2a$12$n8TWbDFZ2F/Q3VdTHxuvTO.7OP7f/bJYKMXPJRb5LTkmHlKxhALiu',
 'Director de Ejemplo', 'Director de Recursos Humanos',
 'direccion', 'Direccion de Recursos Humanos', TRUE)
ON CONFLICT (id) DO NOTHING;

-- Categorías de ejemplo
INSERT INTO categories (id, nombre, descripcion, color, icono) VALUES
('cat-mob', 'Mobiliario',         'Escritorios, sillas, archiveros',        '#8B5CF6', 'Armchair'),
('cat-veh', 'Vehiculos',          'Flota vehicular municipal',               '#3B82F6', 'Car'),
('cat-comp','Equipo de Computo',  'Laptops, computadoras, perifericos',      '#10B981', 'Laptop'),
('cat-ofi', 'Equipo de Oficina',  'Impresoras, copiadoras',                  '#F59E0B', 'Printer'),
('cat-maq', 'Herramientas y Maquinaria', 'Maquinaria pesada y herramientas', '#EF4444', 'Wrench'),
('cat-av',  'Equipo Audiovisual', 'Camaras, proyectores, pantallas',         '#EC4899', 'VideoCamera')
ON CONFLICT (id) DO NOTHING;

-- Bienes de ejemplo
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
