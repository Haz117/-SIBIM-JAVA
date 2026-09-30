-- V24: áreas del municipio y su prefijo de código en una tabla editable
-- (Configuración → Áreas y prefijos) en lugar de estar escritas en el código
-- (com.sibim.config.Areas / AreaCodigos). La semilla es exactamente la lista
-- que tenía el código (AreaCatalog.PREDETERMINADO), así que nada cambia al
-- migrar.
--
-- El nombre es la llave: products.area, resguardos, users.area… guardan el
-- nombre como texto, por eso la app no permite renombrar un área, solo
-- agregarla, cambiar de quién depende o cambiar su prefijo.
CREATE TABLE IF NOT EXISTS areas (
    nombre          TEXT PRIMARY KEY,
    grupo           TEXT NOT NULL CHECK (grupo IN ('PRESIDENCIA', 'SECRETARIA', 'DIRECCION', 'AUTONOMO')),
    padre           TEXT REFERENCES areas(nombre),
    prefijo         TEXT NOT NULL UNIQUE CHECK (prefijo ~ '^[A-Z0-9]{1,8}$'),
    orden           INTEGER NOT NULL DEFAULT 0,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((grupo = 'DIRECCION') = (padre IS NOT NULL))
);

INSERT INTO areas (nombre, grupo, padre, prefijo, orden) VALUES
    ('Despacho de Presidencia', 'PRESIDENCIA', NULL, 'PRES', 1),
    ('Dirección Jurídica', 'DIRECCION', 'Despacho de Presidencia', 'DJ', 2),
    ('Comunicación Social y Marketing Digital', 'DIRECCION', 'Despacho de Presidencia', 'DCS', 3),
    ('Dirección de Gobierno', 'DIRECCION', 'Despacho de Presidencia', 'DGOB', 4),
    ('Dirección de Logística y Eventos', 'DIRECCION', 'Despacho de Presidencia', 'DLE', 5),
    ('Instancia Municipal de la Mujer', 'DIRECCION', 'Despacho de Presidencia', 'IMM', 6),
    ('Instancia Municipal de la Juventud', 'DIRECCION', 'Despacho de Presidencia', 'IMJ', 7),
    ('SIPINNA', 'DIRECCION', 'Despacho de Presidencia', 'SIPI', 8),
    ('Secretaría General Municipal', 'SECRETARIA', NULL, 'SGM', 9),
    ('Archivo Municipal', 'DIRECCION', 'Secretaría General Municipal', 'ARCH', 10),
    ('Dirección de Conciliación Municipal', 'DIRECCION', 'Secretaría General Municipal', 'DCM', 11),
    ('Oficialía del Registro del Estado Familiar', 'DIRECCION', 'Secretaría General Municipal', 'OREF', 12),
    ('Recursos Materiales y Patrimonio', 'DIRECCION', 'Secretaría General Municipal', 'RMP', 13),
    ('Reglamentos, Comercio y Espectáculos', 'DIRECCION', 'Secretaría General Municipal', 'RCE', 14),
    ('Tesorería Municipal', 'SECRETARIA', NULL, 'TES', 15),
    ('Dirección de Catastro', 'DIRECCION', 'Tesorería Municipal', 'DCAT', 16),
    ('Tesorería — Administración', 'DIRECCION', 'Tesorería Municipal', 'TESA', 17),
    ('Tesorería — Egresos', 'DIRECCION', 'Tesorería Municipal', 'TESE', 18),
    ('Tesorería — Ingresos', 'DIRECCION', 'Tesorería Municipal', 'TESI', 19),
    ('Tesorería — Recursos Humanos y Nómina', 'DIRECCION', 'Tesorería Municipal', 'TESRH', 20),
    ('Secretaría de Obras Públicas', 'SECRETARIA', NULL, 'SOP', 21),
    ('Dirección de Desarrollo Urbano', 'DIRECCION', 'Secretaría de Obras Públicas', 'DDU', 22),
    ('Dirección de Medio Ambiente', 'DIRECCION', 'Secretaría de Obras Públicas', 'DMA', 23),
    ('Servicios Municipales', 'DIRECCION', 'Secretaría de Obras Públicas', 'SERM', 24),
    ('Servicios Públicos y Limpias', 'DIRECCION', 'Secretaría de Obras Públicas', 'SPL', 25),
    ('Secretaría de Planeación', 'SECRETARIA', NULL, 'SPLAN', 26),
    ('Dirección de Tecnologías de la Información', 'DIRECCION', 'Secretaría de Planeación', 'TICS', 27),
    ('Secretaría de Desarrollo Económico y Turismo', 'SECRETARIA', NULL, 'SDET', 28),
    ('Secretaría de Bienestar Social', 'SECRETARIA', NULL, 'SBS', 29),
    ('Atención al Migrante', 'DIRECCION', 'Secretaría de Bienestar Social', 'ATM', 30),
    ('Dirección de Cultura', 'DIRECCION', 'Secretaría de Bienestar Social', 'DCUL', 31),
    ('Dirección de Educación', 'DIRECCION', 'Secretaría de Bienestar Social', 'DEDU', 32),
    ('Dirección de Salud', 'DIRECCION', 'Secretaría de Bienestar Social', 'DSAL', 33),
    ('Dirección del Deporte', 'DIRECCION', 'Secretaría de Bienestar Social', 'DDEP', 34),
    ('Junta de Reclutamiento', 'DIRECCION', 'Secretaría de Bienestar Social', 'JREC', 35),
    ('Programas Sociales', 'DIRECCION', 'Secretaría de Bienestar Social', 'PSOC', 36),
    ('Secretaría de Pueblos Indígenas', 'SECRETARIA', NULL, 'SPI', 37),
    ('Contraloría Municipal', 'AUTONOMO', NULL, 'CONT', 38),
    ('Control Canino', 'AUTONOMO', NULL, 'CCAN', 39),
    ('Coordinación de Bibliotecas', 'AUTONOMO', NULL, 'CBIB', 40),
    ('Oficialía Mayor de la Asamblea', 'AUTONOMO', NULL, 'OMA', 41),
    ('Parque Municipal', 'AUTONOMO', NULL, 'PARQ', 42),
    ('Protección Civil y Bomberos', 'AUTONOMO', NULL, 'PCB', 43),
    ('Unidad de Transparencia', 'AUTONOMO', NULL, 'UT', 44)
ON CONFLICT (nombre) DO NOTHING;
