-- V5: soft-delete para usuarios + tabla de configuración del sistema
ALTER TABLE users ADD COLUMN IF NOT EXISTS activo BOOLEAN DEFAULT TRUE NOT NULL;

CREATE TABLE IF NOT EXISTS configuracion (
    clave       TEXT PRIMARY KEY,
    valor       TEXT,
    descripcion TEXT
);

INSERT INTO configuracion (clave, valor, descripcion) VALUES
    ('nombre_ayuntamiento', 'H. Ayuntamiento de Ixmiquilpan', 'Nombre del H. Ayuntamiento'),
    ('municipio',           'Ixmiquilpan, Hidalgo',           'Municipio y estado'),
    ('responsable',         'Dirección de Bienes Patrimoniales', 'Área responsable del sistema'),
    ('correo_contacto',     '',                               'Correo de contacto institucional')
ON CONFLICT (clave) DO NOTHING;
