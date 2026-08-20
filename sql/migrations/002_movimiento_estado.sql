-- Migration 002: Add estado column to movements for transfer approval workflow
-- Safe to run multiple times (IF NOT EXISTS).
ALTER TABLE movements
    ADD COLUMN IF NOT EXISTS estado VARCHAR(20) NOT NULL DEFAULT 'APROBADO';

CREATE INDEX IF NOT EXISTS idx_movements_estado
    ON movements(estado)
    WHERE estado = 'PENDIENTE';
