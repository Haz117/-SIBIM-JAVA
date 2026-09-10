CREATE TABLE IF NOT EXISTS filtros_guardados (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(100) NOT NULL,
    seccion VARCHAR(50) NOT NULL DEFAULT 'bienes',
    filtro_json TEXT NOT NULL,
    usuario_id UUID,
    created_at TIMESTAMP DEFAULT NOW()
);
