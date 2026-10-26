-- ============================================================
-- V32: vuelve a dejar el código único solo entre bienes activos (V14)
--
-- V14 quitó la unicidad global de products.codigo para que el número de un
-- bien dado de baja pueda reutilizarse (AreaCodigos: el siguiente bien del
-- área toma el menor número libre entre los activos). En la base de
-- producción la restricción products_codigo_key volvió a aparecer después
-- de V14 (el historial de Flyway dice que V14 sí corrió), y con ella la
-- primera alta, transferencia o reactivación que tomara el número de un
-- bien dado de baja fallaba con "llave duplicada".
--
-- Idempotente: en una base donde V14 quedó bien no cambia nada.
-- ============================================================

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_codigo_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_products_codigo_activo
    ON products(codigo) WHERE fecha_baja IS NULL;

-- V1 solo creaba este índice cuando agregaba la columna estado; en una base
-- que ya la tenía (la de producción) nunca se creó.
CREATE INDEX IF NOT EXISTS idx_movements_estado
    ON movements(estado) WHERE estado = 'PENDIENTE';
