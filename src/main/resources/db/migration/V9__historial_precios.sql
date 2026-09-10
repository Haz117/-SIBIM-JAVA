CREATE TABLE IF NOT EXISTS price_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    producto_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    campo TEXT NOT NULL,
    valor_anterior NUMERIC(15,2),
    valor_nuevo NUMERIC(15,2) NOT NULL,
    usuario_id UUID,
    usuario_nombre TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_price_history_producto ON price_history(producto_id, created_at DESC);
