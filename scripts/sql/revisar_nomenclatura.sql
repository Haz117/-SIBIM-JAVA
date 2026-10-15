-- ============================================================================
-- SIBIM — revisión de la nomenclatura de códigos en la base real (SOLO LECTURA)
-- ============================================================================
--
-- Cada bien activo debe llevar el código PREFIJO/NN de su área (tabla areas),
-- sin repetirse entre los activos. Al transferir, el bien toma el siguiente
-- número libre del área de destino y deja libre el suyo en la de origen; al
-- darlo de baja conserva su código (para el acta) y ese número queda libre
-- para el siguiente bien nuevo del área.
--
-- Ejecútalo en el SQL Editor de Supabase. Solo hace SELECT: no cambia nada.
-- Las consultas 1 a 6 deben salir vacías; la 7 es el resumen por área.
-- ============================================================================

-- 1. Bienes activos cuyo código no es PREFIJO/NN de su propia área.
SELECT p.codigo, p.nombre, p.area, a.prefijo AS prefijo_esperado
  FROM products p
  JOIN areas a ON a.nombre = p.area
 WHERE p.fecha_baja IS NULL
   AND p.codigo !~ ('^' || a.prefijo || '/[0-9]+$')
 ORDER BY p.area, p.codigo;

-- 2. Bienes activos en un área que no existe en el organigrama.
SELECT p.codigo, p.nombre, p.area
  FROM products p
 WHERE p.fecha_baja IS NULL
   AND NOT EXISTS (SELECT 1 FROM areas a WHERE a.nombre = p.area);

-- 3. Códigos repetidos entre bienes activos (sin distinguir mayúsculas).
SELECT upper(codigo) AS codigo, count(*) AS veces, string_agg(nombre, ' | ') AS bienes
  FROM products
 WHERE fecha_baja IS NULL
 GROUP BY upper(codigo)
HAVING count(*) > 1;

-- 4. Bienes que no quedaron en el área de su última transferencia, o con un
--    código que no es del prefijo de esa área. (El área solo cambia con una
--    transferencia; el código sí puede corregirse a mano después, por eso
--    se compara con el prefijo y no con el código que registró el movimiento.)
WITH ultima AS (
    SELECT DISTINCT ON (m.producto_id) m.*
      FROM movements m
     WHERE m.tipo = 'transferencia' AND m.estado = 'APROBADO'
     ORDER BY m.producto_id, m.created_at DESC
)
SELECT u.created_at, p.nombre, u.area_destino, p.area AS area_actual,
       u.codigo_anterior, u.codigo_nuevo, p.codigo AS codigo_actual
  FROM ultima u
  JOIN products p ON p.id = u.producto_id
  JOIN areas a ON a.nombre = u.area_destino
 WHERE p.fecha_baja IS NULL
   AND (p.area <> u.area_destino OR p.codigo !~ ('^' || a.prefijo || '/[0-9]+$'));

-- 5. Transferencias sin el código anterior y el nuevo registrados
--    (las hechas antes de V22 pueden no tenerlos; las nuevas siempre).
SELECT m.created_at, p.nombre, m.area_origen, m.area_destino
  FROM movements m
  JOIN products p ON p.id = m.producto_id
 WHERE m.tipo = 'transferencia' AND m.estado = 'APROBADO'
   AND (m.codigo_anterior IS NULL OR m.codigo_nuevo IS NULL)
 ORDER BY m.created_at DESC;

-- 6. Bajas sin motivo o con fecha futura.
SELECT codigo, nombre, area, fecha_baja, motivo_baja
  FROM products
 WHERE fecha_baja IS NOT NULL
   AND (motivo_baja IS NULL OR btrim(motivo_baja) = '' OR fecha_baja > CURRENT_DATE);

-- 7. Resumen por área: activos, dados de baja, número más alto en uso y
--    huecos (números libres por bajas o transferencias; el próximo bien
--    nuevo del área toma el más bajo de ellos).
SELECT a.prefijo, a.nombre,
       count(p.id) FILTER (WHERE p.fecha_baja IS NULL)     AS activos,
       count(p.id) FILTER (WHERE p.fecha_baja IS NOT NULL) AS de_baja,
       max(CASE WHEN p.fecha_baja IS NULL AND p.codigo ~ ('^' || a.prefijo || '/[0-9]+$')
                THEN split_part(p.codigo, '/', 2)::int END)  AS numero_mas_alto,
       max(CASE WHEN p.fecha_baja IS NULL AND p.codigo ~ ('^' || a.prefijo || '/[0-9]+$')
                THEN split_part(p.codigo, '/', 2)::int END)
         - count(p.id) FILTER (WHERE p.fecha_baja IS NULL) AS huecos
  FROM areas a
  LEFT JOIN products p ON p.area = a.nombre
 GROUP BY a.prefijo, a.nombre
 ORDER BY a.prefijo;
