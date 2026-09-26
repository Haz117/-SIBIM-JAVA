-- V22: una transferencia reasigna el código patrimonial del bien según el
-- área destino (ver com.sibim.config.AreaCodigos), y el número que deja libre
-- se reutiliza para otro bien. Guardar ambos códigos en el movimiento deja
-- rastro: etiquetas, resguardos y actas impresas con el código anterior
-- siguen siendo rastreables aunque ese número ya lo tenga otro bien.
ALTER TABLE movements ADD COLUMN IF NOT EXISTS codigo_anterior TEXT;
ALTER TABLE movements ADD COLUMN IF NOT EXISTS codigo_nuevo    TEXT;
