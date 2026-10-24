-- ============================================================================
-- SIBIM — corrige la clave CONAC de "Equipo de Cómputo y de Tecnologías de la
-- Información"
-- ============================================================================
--
-- La carga del inventario del 12-oct-2026 le puso 1.2.4.1.5.515 a esa
-- categoría y a sus bienes. La cuenta del Plan de Cuentas del CONAC es
-- 1.2.4.1.3, y así la escriben los Excel del municipio (1.2.4.1.3.515):
-- 1.2.4.1.5.515 no aparece en ninguno. Esa clave sale en el Anexo V.4.
--
-- Ejecútalo en el SQL Editor de Supabase. Va en una sola transacción y deja
-- una entrada en la Auditoría. Correrlo otra vez no cambia nada.
-- ============================================================================

BEGIN;

UPDATE categories
   SET codigo_conac = '1.2.4.1.3.515'
 WHERE codigo_conac = '1.2.4.1.5.515';

-- updated_at: una ventana de edición que estuviera abierta con el valor viejo
-- avisa que el bien cambió en vez de regresarlo al guardar.
UPDATE products
   SET clave_armonizada = '1.2.4.1.3.515', updated_at = NOW()
 WHERE clave_armonizada = '1.2.4.1.5.515';

INSERT INTO audit_log (id, entidad, entidad_id, entidad_nombre, accion, detalle, usuario_nombre)
SELECT gen_random_uuid()::text, 'categoria', c.id, c.nombre, 'actualizar',
       'Clave CONAC corregida: 1.2.4.1.5.515 → 1.2.4.1.3.515, en la categoría y en sus '
         || (SELECT count(*) FROM products p WHERE p.categoria_id = c.id) || ' bienes',
       'Patrimonio (corrección con scripts/sql/corregir_conac_computo.sql)'
  FROM categories c
 WHERE c.codigo_conac = '1.2.4.1.3.515'
   AND NOT EXISTS (SELECT 1 FROM audit_log a
                    WHERE a.entidad_id = c.id AND a.detalle LIKE 'Clave CONAC corregida: 1.2.4.1.5.515%');

COMMIT;

-- Comprobación: quedan_mal debe salir 0 y corregidos 503 (o más, si después
-- se registraron otros equipos de cómputo).
SELECT (SELECT count(*) FROM products   WHERE clave_armonizada = '1.2.4.1.5.515') AS quedan_mal,
       (SELECT count(*) FROM categories WHERE codigo_conac     = '1.2.4.1.5.515') AS categorias_mal,
       (SELECT count(*) FROM products   WHERE clave_armonizada = '1.2.4.1.3.515') AS corregidos;
