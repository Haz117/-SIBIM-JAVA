-- ============================================================
-- V17: Comité de Bajas con Dictamen
--
-- Amplía la baja patrimonial para registrar el tipo de destino del bien,
-- el dictamen formal del comité, el número de acta y la fecha del dictamen.
-- Todos los campos son opcionales (NULL) para mantener compatibilidad con
-- las bajas simples existentes que sólo tienen motivo_baja.
-- ============================================================

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS tipo_destino_baja VARCHAR(30)
        CHECK (tipo_destino_baja IN ('DESTRUCCION','DONACION','SUBASTA','TRANSFERENCIA_ENTE','OTRO')),
    ADD COLUMN IF NOT EXISTS dictamen_baja      TEXT,
    ADD COLUMN IF NOT EXISTS numero_acta_baja   VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fecha_dictamen     DATE;
