-- ============================================================
-- V18: campos del inventario físico MLA
--   products    → estado_fisico (BUENO/REGULAR/MALO/DEFICIENTE)
--   products    → numero_factura (número de documento de compra)
--   categories  → codigo_conac   (clasificación contable CONAC)
-- ============================================================

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS estado_fisico  VARCHAR(15),
    ADD COLUMN IF NOT EXISTS numero_factura TEXT;

ALTER TABLE categories
    ADD COLUMN IF NOT EXISTS codigo_conac   VARCHAR(20);
