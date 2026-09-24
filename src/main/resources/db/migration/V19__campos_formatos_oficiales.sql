-- V19: campos para formatos oficiales (ANEXO V.4, V.6 Parque Vehicular, Resguardo)
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS clave_armonizada      TEXT,
    ADD COLUMN IF NOT EXISTS color                 TEXT,
    ADD COLUMN IF NOT EXISTS no_motor              TEXT,
    ADD COLUMN IF NOT EXISTS tipo_bien             TEXT,
    ADD COLUMN IF NOT EXISTS no_tarjeta_circulacion TEXT,
    ADD COLUMN IF NOT EXISTS no_poliza_seguro       TEXT;
