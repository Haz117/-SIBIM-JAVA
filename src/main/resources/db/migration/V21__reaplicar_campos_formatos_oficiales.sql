-- V21: re-aplica los campos de formatos oficiales (mismo contenido que V19).
--
-- En bases donde el historial de Flyway ya tenía una V19 distinta, el
-- flyway.repair() del arranque reescribió la descripción/checksum de V19 sin
-- ejecutar su SQL: el historial dice "campos formatos oficiales" pero las
-- columnas nunca se crearon y guardar un bien falla ("column ... does not
-- exist"). Esta migración es idempotente: en bases donde V19 sí corrió no
-- hace nada; en las demás crea las columnas que faltan.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS clave_armonizada       TEXT,
    ADD COLUMN IF NOT EXISTS color                  TEXT,
    ADD COLUMN IF NOT EXISTS no_motor               TEXT,
    ADD COLUMN IF NOT EXISTS tipo_bien              TEXT,
    ADD COLUMN IF NOT EXISTS no_tarjeta_circulacion TEXT,
    ADD COLUMN IF NOT EXISTS no_poliza_seguro       TEXT;
