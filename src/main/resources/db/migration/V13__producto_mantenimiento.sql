-- ============================================================
-- V13: Tabla dedicada para alertas de mantenimiento preventivo
--
-- Reemplaza el almacenamiento anterior (claves "mant_{productoId}_{index}"
-- dentro de la tabla genérica configuracion), que forzaba un full scan de
-- configuracion para listar alertas próximas y no permitía escrituras
-- atómicas (lectura de contador + dos escrituras separadas).
-- ============================================================

CREATE TABLE IF NOT EXISTS producto_mantenimiento (
    id            TEXT PRIMARY KEY,
    producto_id   TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    descripcion   TEXT NOT NULL,
    fecha         DATE NOT NULL,
    completada    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_producto_mantenimiento_producto ON producto_mantenimiento(producto_id);

-- Sirve tanto getAlertas(productoId) ordenado por fecha como el scan global
-- de getProximasGlobal(days) — ambos filtran por completada = false.
CREATE INDEX IF NOT EXISTS idx_producto_mantenimiento_pendientes
    ON producto_mantenimiento(fecha) WHERE NOT completada;
