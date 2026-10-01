-- ============================================================
-- V30: Versión instalada y errores por computadora
--
-- Con SIBIM repartido en muchas PCs, Patrimonio necesita ver desde la suya
-- qué equipos siguen con una versión vieja y qué errores están teniendo,
-- sin ir físicamente a cada área (el log de cada PC es un archivo local).
-- ============================================================

ALTER TABLE accesos_equipo ADD COLUMN IF NOT EXISTS version TEXT;

CREATE TABLE IF NOT EXISTS errores_equipo (
    id         TEXT PRIMARY KEY,
    equipo     TEXT NOT NULL,
    usuario    TEXT,
    version    TEXT,
    origen     TEXT,
    mensaje    TEXT NOT NULL,
    detalle    TEXT,
    creado_en  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_errores_equipo_fecha ON errores_equipo (creado_en DESC);
