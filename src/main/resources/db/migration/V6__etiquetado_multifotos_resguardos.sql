-- Feature: etiquetado de bienes
ALTER TABLE products ADD COLUMN IF NOT EXISTS etiquetado BOOLEAN DEFAULT FALSE NOT NULL;

-- Feature: múltiples fotos por bien
CREATE TABLE IF NOT EXISTS product_fotos (
    id          TEXT PRIMARY KEY,
    producto_id TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    foto_url    TEXT NOT NULL,
    orden       INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Migrar foto_url existente a product_fotos
INSERT INTO product_fotos (id, producto_id, foto_url, orden, created_at)
SELECT gen_random_uuid()::TEXT, id, foto_url, 0, NOW()
FROM products WHERE foto_url IS NOT NULL AND foto_url <> ''
ON CONFLICT DO NOTHING;

-- Feature: PDF de resguardo por área
CREATE TABLE IF NOT EXISTS area_resguardos (
    id          TEXT PRIMARY KEY,
    area        TEXT NOT NULL,
    pdf_url     TEXT NOT NULL,
    descripcion TEXT,
    fecha       DATE,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_fotos_producto ON product_fotos(producto_id);
CREATE INDEX IF NOT EXISTS idx_area_resguardos_area   ON area_resguardos(area);
