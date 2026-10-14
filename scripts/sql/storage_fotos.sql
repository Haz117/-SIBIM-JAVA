-- ============================================================================
-- SIBIM — permisos de Supabase Storage para las fotos y la ficha del QR
-- ============================================================================
--
-- Las PCs suben al bucket público sibim-fotos con la clave pública (anon),
-- que va dentro de cada instalador:
--   * fotos y facturas:  <id>_<...>.jpg     (image/jpeg)
--   * ficha del QR:      fichas/<id>.pdf    (application/pdf; se reemplaza
--                                           cada vez que cambia el bien)
-- Sin políticas, Storage rechaza toda subida con esa clave: las fotos se
-- quedan en la PC que las tomó y la ficha del QR no se publica.
--
-- Aquí se permite solo eso: subir y reemplazar .jpg y .pdf en ese bucket.
-- Con la clave pública nadie puede borrar archivos ni usar otro bucket.
-- Ejecútalo una vez en el SQL Editor de Supabase; se puede repetir.
-- ============================================================================

-- Solo los tipos que manda SIBIM.
UPDATE storage.buckets
   SET allowed_mime_types = ARRAY['image/jpeg', 'application/pdf']
 WHERE id = 'sibim-fotos';

DROP POLICY IF EXISTS sibim_fotos_leer       ON storage.objects;
DROP POLICY IF EXISTS sibim_fotos_subir      ON storage.objects;
DROP POLICY IF EXISTS sibim_fotos_reemplazar ON storage.objects;

-- Reemplazar un archivo (la app sube con x-upsert) también necesita leer su fila.
CREATE POLICY sibim_fotos_leer ON storage.objects FOR SELECT TO anon
    USING (bucket_id = 'sibim-fotos');

CREATE POLICY sibim_fotos_subir ON storage.objects FOR INSERT TO anon
    WITH CHECK (bucket_id = 'sibim-fotos' AND lower(storage.extension(name)) IN ('jpg', 'pdf'));

CREATE POLICY sibim_fotos_reemplazar ON storage.objects FOR UPDATE TO anon
    USING (bucket_id = 'sibim-fotos')
    WITH CHECK (bucket_id = 'sibim-fotos' AND lower(storage.extension(name)) IN ('jpg', 'pdf'));
