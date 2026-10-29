-- V33: folio fiscal (UUID) del CFDI de la factura de un bien, aparte del número
-- de factura (serie y folio). El 28 y 29-oct-2026 se capturó el UUID en "número
-- de factura" de 51 vehículos y se perdió el folio que tenían. Columna nueva y
-- opcional: las versiones anteriores la ignoran y no la tocan al guardar.
ALTER TABLE products ADD COLUMN IF NOT EXISTS folio_fiscal TEXT;
