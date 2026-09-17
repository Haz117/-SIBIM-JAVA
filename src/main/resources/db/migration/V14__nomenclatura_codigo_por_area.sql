-- ============================================================
-- V14: Nomenclatura de código patrimonial por área (ej. "TICS/01")
--
-- El código de un bien ahora se asigna según su área (ver
-- com.sibim.config.AreaCodigos) y se reasigna cuando el bien se transfiere
-- a otra área o se reactiva tras una baja — el número que deja libre queda
-- disponible para el siguiente bien nuevo de esa área.
--
-- Un bien dado de baja conserva su código histórico (para auditoría), pero
-- ya no debe "ocupar" ese valor de forma permanente: por eso la unicidad de
-- codigo pasa de ser global a aplicar solo entre bienes activos
-- (fecha_baja IS NULL). Dos bienes distintos pueden compartir el mismo
-- texto de código si uno de los dos ya no está activo.
-- ============================================================

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_codigo_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_products_codigo_activo
    ON products(codigo) WHERE fecha_baja IS NULL;
