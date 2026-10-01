-- ============================================================
-- V26: Recepción de transferencias
--
-- Una transferencia aprobada (o hecha directo por Patrimonio) mueve el bien
-- al área de destino, pero esa área no se enteraba: el bien solo aparecía en
-- su lista. Ahora queda "por recibir" hasta que alguien del área de destino
-- confirma que lo tiene físicamente; se guarda quién y cuándo.
--
-- Las transferencias aprobadas antes de esta versión se dan por recibidas,
-- para que no aparezcan de golpe como pendientes de recibir.
-- ============================================================

ALTER TABLE movements
    ADD COLUMN IF NOT EXISTS recibido_por TEXT,
    ADD COLUMN IF NOT EXISTS recibido_en  TIMESTAMP;

UPDATE movements
   SET recibido_en  = created_at,
       recibido_por = 'Registrada antes de la confirmación de recepción'
 WHERE tipo = 'transferencia'
   AND estado = 'APROBADO'
   AND recibido_en IS NULL;

CREATE INDEX IF NOT EXISTS idx_movements_por_recibir
    ON movements (area_destino)
 WHERE tipo = 'transferencia' AND estado = 'APROBADO' AND recibido_en IS NULL;
