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
-- Aquí se permite solo eso: agregar fotos .jpg y publicar o reemplazar la
-- ficha .pdf de cada bien. Una foto siempre se sube con nombre nuevo, así que
-- nunca se reemplaza: con la clave pública nadie puede sobrescribir las fotos
-- del inventario, borrar archivos ni usar otro bucket.
-- Ejecútalo en el SQL Editor de Supabase; se puede repetir. Hazlo cuando todas
-- las PCs tengan SIBIM 1.0.12 o más nuevo: las versiones anteriores subían
-- las fotos como reemplazo y se las rechazaría.
-- ============================================================================

-- Solo los tipos que manda SIBIM.
UPDATE storage.buckets
   SET allowed_mime_types = ARRAY['image/jpeg', 'application/pdf']
 WHERE id = 'sibim-fotos';

DROP POLICY IF EXISTS sibim_fotos_leer       ON storage.objects;
DROP POLICY IF EXISTS sibim_fotos_subir      ON storage.objects;
DROP POLICY IF EXISTS sibim_fotos_reemplazar ON storage.objects;

-- Reemplazar la ficha (la app la sube con x-upsert) también necesita leer su fila.
CREATE POLICY sibim_fotos_leer ON storage.objects FOR SELECT TO anon
    USING (bucket_id = 'sibim-fotos');

CREATE POLICY sibim_fotos_subir ON storage.objects FOR INSERT TO anon
    WITH CHECK (bucket_id = 'sibim-fotos' AND (
        lower(storage.extension(name)) = 'jpg'
        OR (name LIKE 'fichas/%' AND lower(storage.extension(name)) = 'pdf')));

CREATE POLICY sibim_fotos_reemplazar ON storage.objects FOR UPDATE TO anon
    USING (bucket_id = 'sibim-fotos' AND name LIKE 'fichas/%')
    WITH CHECK (bucket_id = 'sibim-fotos' AND name LIKE 'fichas/%' AND lower(storage.extension(name)) = 'pdf');
