-- ============================================================
-- V31: Firma de las actualizaciones
--
-- Todas las PCs pueden escribir en la base (cada una tiene su .env), así que
-- el SHA-256 de V27 solo prueba que el instalador llegó completo, no quién lo
-- publicó: con el .env de cualquier PC se podía subir un programa que todas
-- las demás ofrecerían instalar. Ahora cada instalador lleva la firma de TI
-- (Ed25519 sobre la versión y el SHA-256, ver FirmaActualizaciones) y SIBIM
-- solo ofrece e instala los que la traen válida. Los publicados antes de
-- esta versión no tienen firma: se ignoran.
-- ============================================================

ALTER TABLE actualizaciones ADD COLUMN IF NOT EXISTS firma TEXT;
