-- ============================================================
-- V28: Solicitudes de préstamo y de resguardo
--
-- Solo Patrimonio (administrador) registra préstamos y resguardos. Las áreas
-- los piden desde aquí: la solicitud queda PENDIENTE hasta que Patrimonio la
-- aprueba (y entonces se crea el documento, cuyo folio se anota en
-- "documento") o la rechaza con un motivo.
-- ============================================================

CREATE TABLE IF NOT EXISTS solicitudes (
    id                TEXT PRIMARY KEY,
    tipo              TEXT NOT NULL CHECK (tipo IN ('prestamo', 'resguardo')),
    producto_id       TEXT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    producto_nombre   TEXT NOT NULL,
    producto_codigo   TEXT,
    area              TEXT NOT NULL,
    solicitante       TEXT,
    area_destino      TEXT,
    responsable       TEXT NOT NULL,
    cargo             TEXT,
    motivo            TEXT,
    fecha_devolucion  DATE,
    estado            TEXT NOT NULL DEFAULT 'PENDIENTE'
                      CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    respuesta         TEXT,
    documento         TEXT,
    resuelto_por      TEXT,
    resuelto_en       TIMESTAMP,
    creado_en         TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_solicitudes_estado ON solicitudes (estado, creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_solicitudes_area   ON solicitudes (area);
