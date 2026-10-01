-- ============================================================
-- V27: Actualizaciones del sistema
--
-- Patrimonio publica el instalador de una versión nueva desde su PC
-- (Configuración › Publicar actualización) y las demás PCs lo descargan de
-- aquí al arrancar. Vive en la base y no en GitHub porque el instalador
-- lleva la configuración de conexión, y el repositorio es público.
--
-- El instalador (~110 MB) se guarda en partes de 5 MB: así ni subirlo ni
-- bajarlo necesita tenerlo entero en memoria.
-- ============================================================

CREATE TABLE IF NOT EXISTS actualizaciones (
    version        TEXT PRIMARY KEY,
    archivo        TEXT NOT NULL,
    tamano         BIGINT NOT NULL,
    sha256         TEXT NOT NULL,
    notas          TEXT,
    partes         INTEGER NOT NULL,
    publicado_por  TEXT,
    publicado_en   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS actualizacion_partes (
    version  TEXT NOT NULL REFERENCES actualizaciones(version) ON DELETE CASCADE,
    n        INTEGER NOT NULL,
    datos    BYTEA NOT NULL,
    PRIMARY KEY (version, n)
);
