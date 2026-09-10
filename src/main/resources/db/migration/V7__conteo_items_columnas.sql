-- Añade columnas de condición y código a conteo_items (agregadas después del despliegue inicial)
ALTER TABLE conteo_items ADD COLUMN IF NOT EXISTS estado_conteo TEXT DEFAULT 'ENCONTRADO';
ALTER TABLE conteo_items ADD COLUMN IF NOT EXISTS nota           TEXT;
ALTER TABLE conteo_items ADD COLUMN IF NOT EXISTS producto_codigo TEXT;
