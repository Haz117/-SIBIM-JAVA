-- Índices para consultas de alerta (findAgotados, findBajoStock)
-- Antes: full table scan en cada carga de la página de Alertas
CREATE INDEX IF NOT EXISTS idx_products_stock_actual
    ON products(stock_actual)
    WHERE fecha_baja IS NULL;

-- Índice compuesto tipo + estado para transferencias pendientes
-- Antes: full scan de movements en findPendientesTransferencias()
CREATE INDEX IF NOT EXISTS idx_movements_tipo_estado
    ON movements(tipo, estado);

-- Índice parcial para filtrar movimientos pendientes (refuerza el anterior)
CREATE INDEX IF NOT EXISTS idx_movements_pendiente_tipo
    ON movements(created_at DESC)
    WHERE estado = 'PENDIENTE';

-- Índice compuesto en products para búsquedas nombre+area frecuentes
CREATE INDEX IF NOT EXISTS idx_products_nombre_area
    ON products(nombre, area)
    WHERE fecha_baja IS NULL;
