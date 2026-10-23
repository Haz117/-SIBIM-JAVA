-- ============================================================================
-- SIBIM — usuario de base de datos para las PCs de las áreas
-- ============================================================================
--
-- sibim_app (rol_app_minimo.sql) puede leer y escribir cualquier dato, y su
-- contraseña va dentro del instalador: quien la saque de una PC de área podría
-- borrar bienes, cambiar valores o mover bienes de área sin pasar por SIBIM.
--
-- sibim_areas puede lo mismo que hace un área en SIBIM, nada más:
--   * leer (como hoy),
--   * actualizar ubicación, descripción, estado físico y fotos de un bien,
--   * confirmar que recibió un bien transferido (acuse),
--   * cambiar la contraseña de una cuenta que no sea de administrador,
--   * guardar los resguardos por área (PDF) y los folios de sus reportes,
--   * dejar su rastro: bitácora, intentos de inicio de sesión, equipos y errores.
-- No puede dar de alta ni borrar bienes, cambiar su código, área o valores,
-- registrar movimientos, tocar usuarios ni configuración, ni publicar
-- actualizaciones. Patrimonio sigue con sibim_app (o postgres).
--
-- CÓMO USARLO (lo hace packaging\preparar-conexion.ps1 -Areas)
--   1. Ejecuta antes rol_app_minimo.sql (activa la seguridad por filas).
--   2. Cambia la contraseña de la línea marcada abajo (una larga y aleatoria).
--   3. Ejecútalo como el usuario dueño (postgres) en el SQL Editor de Supabase.
--      Se puede volver a ejecutar sin problema: cada vez deja exactamente
--      estos permisos.
--   4. El instalador lleva esta conexión (packaging\sibim.env con
--      DB_USER=sibim_areas.<ref-del-proyecto>); las PCs de Patrimonio usan su
--      propio %APPDATA%\SIBIM\.env con sibim_app.
--   5. Vuelve a ejecutarlo si una versión nueva de SIBIM hace que las áreas
--      escriban en otra tabla (las pruebas de SIBIM lo avisan).
-- ============================================================================

DO $$
DECLARE
    clave TEXT := 'CAMBIA-ESTA-CONTRASENA';   -- ← cambia esto antes de ejecutar
BEGIN
    IF clave = 'CAMBIA-ESTA-CONTRASENA' THEN
        RAISE EXCEPTION 'Cambia la contraseña de sibim_areas en este script antes de ejecutarlo';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sibim_app') THEN
        RAISE EXCEPTION 'Ejecuta primero scripts/sql/rol_app_minimo.sql';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sibim_areas') THEN
        EXECUTE format('CREATE ROLE sibim_areas LOGIN PASSWORD %L', clave);
    ELSE
        EXECUTE format('ALTER ROLE sibim_areas WITH LOGIN PASSWORD %L', clave);
    END IF;
END $$;

-- Cada ejecución parte de cero: lo que se concede abajo es todo lo que tiene.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM sibim_areas;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM sibim_areas;
REVOKE CREATE ON SCHEMA public FROM sibim_areas;
GRANT USAGE ON SCHEMA public TO sibim_areas;

-- Lee todo, como hoy (las áreas ven el inventario a través de SIBIM).
GRANT SELECT ON ALL TABLES IN SCHEMA public TO sibim_areas;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sibim_areas;
-- Las tablas de versiones futuras: solo lectura hasta que se decida otra cosa.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sibim_areas;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sibim_areas;

-- Lo que escribe un área. Las columnas que no aparecen no se pueden cambiar
-- (PostgreSQL rechaza el UPDATE completo, no solo la columna).
GRANT UPDATE (ubicacion, descripcion, estado_fisico, foto_url, factura_url, updated_at) ON products TO sibim_areas;
GRANT INSERT, UPDATE, DELETE ON product_fotos TO sibim_areas;
GRANT UPDATE (recibido_por, recibido_en) ON movements TO sibim_areas;
GRANT UPDATE (password, debe_cambiar_password) ON users TO sibim_areas;
GRANT INSERT, DELETE ON area_resguardos TO sibim_areas;
GRANT INSERT, UPDATE ON folios TO sibim_areas;
GRANT INSERT ON audit_log TO sibim_areas;
GRANT INSERT, UPDATE, DELETE ON login_attempts TO sibim_areas;
GRANT INSERT, UPDATE ON accesos_equipo TO sibim_areas;
GRANT INSERT ON errores_equipo TO sibim_areas;

-- Seguridad por filas (la activó rol_app_minimo.sql en cada tabla): sin una
-- política, sibim_areas no vería ni una fila. Lee todas; escribe en las tablas
-- de arriba; y de los usuarios, solo la contraseña de cuentas que no son de
-- administrador (así no puede quitarle el acceso a Patrimonio).
DO $$
DECLARE
    t TEXT;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tableowner = current_user LOOP
        EXECUTE format('DROP POLICY IF EXISTS sibim_areas_leer ON public.%I', t);
        EXECUTE format('DROP POLICY IF EXISTS sibim_areas_agregar ON public.%I', t);
        EXECUTE format('DROP POLICY IF EXISTS sibim_areas_cambiar ON public.%I', t);
        EXECUTE format('DROP POLICY IF EXISTS sibim_areas_quitar ON public.%I', t);
        EXECUTE format('CREATE POLICY sibim_areas_leer ON public.%I FOR SELECT TO sibim_areas USING (true)', t);
    END LOOP;
    FOREACH t IN ARRAY ARRAY['product_fotos', 'area_resguardos', 'folios', 'audit_log',
                             'login_attempts', 'accesos_equipo', 'errores_equipo'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            EXECUTE format('CREATE POLICY sibim_areas_agregar ON public.%I FOR INSERT TO sibim_areas WITH CHECK (true)', t);
        END IF;
    END LOOP;
    FOREACH t IN ARRAY ARRAY['products', 'product_fotos', 'movements', 'folios',
                             'login_attempts', 'accesos_equipo'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            EXECUTE format('CREATE POLICY sibim_areas_cambiar ON public.%I FOR UPDATE TO sibim_areas USING (true) WITH CHECK (true)', t);
        END IF;
    END LOOP;
    FOREACH t IN ARRAY ARRAY['product_fotos', 'area_resguardos', 'login_attempts'] LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            EXECUTE format('CREATE POLICY sibim_areas_quitar ON public.%I FOR DELETE TO sibim_areas USING (true)', t);
        END IF;
    END LOOP;
END $$;

CREATE POLICY sibim_areas_cambiar ON public.users FOR UPDATE TO sibim_areas
    USING (role <> 'admin') WITH CHECK (role <> 'admin');
