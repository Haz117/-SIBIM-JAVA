-- ============================================================
-- V34: (5-nov-2026)
--   1. Un número de inventario que ya se usó no se vuelve a dar.
--   2. Baja definitiva de un bien dado de baja.
--   3. Revisión de sus bienes por cada área (fase 2 del organigrama).
--   4. Categoría "Activos Biológicos".
-- Idempotente: correrla otra vez no cambia nada.
-- ============================================================

-- ── 1. Números usados ───────────────────────────────────────
-- Hasta aquí el siguiente bien de un área tomaba el menor número libre entre
-- los activos: el de un bien dado de baja o transferido se le daba a otro, y
-- las etiquetas, resguardos y actas con ese número quedaban apuntando a dos
-- bienes. Ahora el siguiente es el que sigue al más alto que se haya usado
-- (AreaCodigos.siguienteCodigo). Esta tabla guarda cada código que ha tenido
-- un bien, aunque después cambie (transferencia) o el bien se borre.
CREATE TABLE IF NOT EXISTS codigos_usados (
    codigo       TEXT PRIMARY KEY,      -- en mayúsculas y sin espacios a los lados
    producto_id  TEXT,                  -- el primer bien que lo tuvo (sin llave: el bien puede borrarse)
    usado_en     TIMESTAMP NOT NULL DEFAULT NOW()
);

INSERT INTO codigos_usados (codigo, producto_id)
SELECT DISTINCT ON (UPPER(TRIM(codigo))) UPPER(TRIM(codigo)), id
  FROM products
 WHERE codigo IS NOT NULL AND TRIM(codigo) <> ''
 ORDER BY UPPER(TRIM(codigo)), created_at
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO codigos_usados (codigo, producto_id)
SELECT DISTINCT ON (c) c, producto_id
  FROM (SELECT UPPER(TRIM(codigo_anterior)) AS c, producto_id, created_at FROM movements
        UNION ALL
        SELECT UPPER(TRIM(codigo_nuevo)), producto_id, created_at FROM movements) t
 WHERE c IS NOT NULL AND c <> ''
 ORDER BY c, created_at
ON CONFLICT (codigo) DO NOTHING;

-- Cada código nuevo de un bien queda anotado, venga de la app o de un script.
-- SECURITY DEFINER: anota aunque quien escribe en products no tenga permiso
-- sobre esta tabla.
CREATE OR REPLACE FUNCTION sibim_anotar_codigo_usado() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF NEW.codigo IS NOT NULL AND TRIM(NEW.codigo) <> '' THEN
        INSERT INTO codigos_usados (codigo, producto_id)
        VALUES (UPPER(TRIM(NEW.codigo)), NEW.id)
        ON CONFLICT (codigo) DO NOTHING;
    END IF;
    RETURN NEW;
END $$;

REVOKE ALL ON FUNCTION sibim_anotar_codigo_usado() FROM PUBLIC;

DROP TRIGGER IF EXISTS trg_codigos_usados ON products;
CREATE TRIGGER trg_codigos_usados
    AFTER INSERT OR UPDATE OF codigo ON products
    FOR EACH ROW EXECUTE FUNCTION sibim_anotar_codigo_usado();

-- Row Level Security como las demás tablas. En producción rol_app_minimo.sql
-- (y rol_areas.sql) se aplicaron antes de que existiera esta tabla: sin esto
-- quedaría como la única sin RLS (el Security Advisor de Supabase la marca).
-- Las políticas solo se crean si el rol ya existe; si no, el script del rol
-- las pone al aplicarse. El trigger escribe como dueño: no le afecta RLS.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sibim_app') THEN
        ALTER TABLE codigos_usados ENABLE ROW LEVEL SECURITY;
        DROP POLICY IF EXISTS sibim_app_todo ON codigos_usados;
        CREATE POLICY sibim_app_todo ON codigos_usados FOR ALL TO sibim_app USING (true) WITH CHECK (true);
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sibim_areas') THEN
            DROP POLICY IF EXISTS sibim_areas_leer ON codigos_usados;
            CREATE POLICY sibim_areas_leer ON codigos_usados FOR SELECT TO sibim_areas USING (true);
        END IF;
    END IF;
END $$;

-- ── 2. Baja definitiva ──────────────────────────────────────
-- Dar de baja saca al bien del inventario activo; la baja definitiva es
-- cuando ya se ejecutó su destino final (destrucción, donación, subasta…)
-- con el documento que lo autoriza. Ya no se puede reactivar.
ALTER TABLE products ADD COLUMN IF NOT EXISTS baja_definitiva_fecha     DATE;
ALTER TABLE products ADD COLUMN IF NOT EXISTS baja_definitiva_documento TEXT;

-- ── 3. Revisión del área ────────────────────────────────────
-- El área confirma que el bien sí es suyo (revision_area_nota vacía) o avisa
-- que no lo es o no lo encuentra (con la nota). Al cambiar de área el bien,
-- la revisión se borra: la nueva área tiene que revisarlo.
ALTER TABLE products ADD COLUMN IF NOT EXISTS revisado_area_en   TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS revisado_area_por  TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS revision_area_nota TEXT;

CREATE OR REPLACE FUNCTION sibim_revision_al_cambiar_area() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.area IS DISTINCT FROM OLD.area THEN
        NEW.revisado_area_en   := NULL;
        NEW.revisado_area_por  := NULL;
        NEW.revision_area_nota := NULL;
    END IF;
    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_revision_al_cambiar_area ON products;
CREATE TRIGGER trg_revision_al_cambiar_area
    BEFORE UPDATE OF area ON products
    FOR EACH ROW EXECUTE FUNCTION sibim_revision_al_cambiar_area();

-- ── 4. Activos Biológicos ───────────────────────────────────
-- Animales (perros de la unidad canina, caballos…) y plantas del municipio.
-- Cuenta 1.2.4.8 del Plan de Cuentas del CONAC (partida 57x del COG); cada
-- bien puede llevar su subcuenta en la clave armonizada.
INSERT INTO categories (id, nombre, descripcion, color, codigo_conac)
SELECT 'cat-activos-biologicos', 'Activos Biológicos',
       'Animales y plantas del municipio. Subcuentas CONAC 1.2.4.8: .1 Bovinos (571), .2 Porcinos (572), '
       || '.3 Aves (573), .4 Ovinos y caprinos (574), .5 Peces y acuicultura (575), .6 Equinos (576), '
       || '.7 Especies menores y de zoológico (577), .8 Árboles y plantas (578), .9 Otros (579).',
       '#4D7C0F', '1.2.4.8'
 WHERE NOT EXISTS (SELECT 1 FROM categories
                    WHERE translate(LOWER(nombre), 'áéíóú', 'aeiou') LIKE '%activos biologicos%')
ON CONFLICT DO NOTHING;
