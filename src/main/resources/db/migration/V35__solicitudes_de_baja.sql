-- ============================================================
-- V35: (5-nov-2026) Solicitudes de baja por SIBIM.
--
-- Un área pide la baja de un bien desde Bienes: queda una solicitud en
-- espera, a Patrimonio (administrador) le llega el aviso, ve el bien y decide
-- si lo da de baja o si no procede (con el motivo, que el área ve). El
-- formato firmado se sigue entregando en papel.
-- Idempotente: correrla otra vez no cambia nada.
-- ============================================================

-- El tipo ahora también puede ser 'baja'. Se quita la regla anterior sin
-- depender de su nombre.
DO $$
DECLARE
    c TEXT;
BEGIN
    FOR c IN SELECT conname FROM pg_constraint
              WHERE conrelid = 'solicitudes'::regclass AND contype = 'c'
                AND pg_get_constraintdef(oid) LIKE '%tipo%' LOOP
        EXECUTE format('ALTER TABLE solicitudes DROP CONSTRAINT %I', c);
    END LOOP;
END $$;

ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_tipo_check
    CHECK (tipo IN ('prestamo', 'resguardo', 'baja'));

-- Una sola solicitud de baja en espera por bien, aunque dos personas del área
-- la pidan al mismo tiempo.
CREATE UNIQUE INDEX IF NOT EXISTS uq_solicitudes_baja_en_espera
    ON solicitudes (producto_id) WHERE tipo = 'baja' AND estado = 'PENDIENTE';

-- El área entrega el formato firmado en Recursos Materiales y Patrimonio; sin
-- él no se da de baja. Patrimonio anota cuándo lo recibió y con qué folio
-- (o se anota solo al dar de baja el bien, con el folio que pide la baja).
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS formato_folio        TEXT;
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS formato_recibido_en  TIMESTAMP;
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS formato_recibido_por TEXT;
