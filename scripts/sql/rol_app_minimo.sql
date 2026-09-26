-- ============================================================================
-- SIBIM — rol de base de datos con el mínimo privilegio para las PCs
-- ============================================================================
--
-- Hoy cada PC se conecta con el usuario dueño de la base (postgres): quien
-- abra el .env de cualquier PC puede borrar tablas, cambiarse a admin o editar
-- la bitácora. Con este rol, las PCs solo pueden leer y escribir datos:
--   * sin DDL (no pueden crear, alterar ni borrar tablas),
--   * la bitácora (audit_log) solo crece: no se puede modificar ni borrar,
--   * el historial de migraciones solo se lee.
-- Además quita el acceso de la API REST de Supabase (roles anon y
-- authenticated) a las tablas de SIBIM, que la app no usa.
--
-- CÓMO USARLO
--   1. Cambia la contraseña de la línea marcada abajo (una larga y aleatoria).
--   2. Ejecútalo UNA vez como el usuario dueño (postgres), en el SQL Editor de
--      Supabase o con psql. Se puede volver a ejecutar sin problema.
--   3. En el .env de cada PC (%APPDATA%\SIBIM\.env):
--        DB_USER=sibim_app          (con el pooler de Supabase: sibim_app.<ref-del-proyecto>)
--        DB_PASSWORD=<la contraseña del paso 1>
--        DB_MIGRATE=false
--   4. Deja UNA PC de administración con el usuario postgres y sin
--      DB_MIGRATE=false: al instalar una versión nueva de SIBIM, se abre
--      primero ahí para que aplique las migraciones; las demás PCs solo
--      verifican que el esquema esté al día.
--   5. Vuelve a ejecutar este script después de cada versión que agregue
--      tablas (las nuevas ya quedan cubiertas por los permisos por defecto,
--      pero así se reaplican las restricciones de audit_log y flyway).
-- ============================================================================

DO $$
DECLARE
    clave TEXT := 'CAMBIA-ESTA-CONTRASENA';   -- ← cambia esto antes de ejecutar
BEGIN
    IF clave = 'CAMBIA-ESTA-CONTRASENA' THEN
        RAISE EXCEPTION 'Cambia la contraseña de sibim_app en este script antes de ejecutarlo';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sibim_app') THEN
        EXECUTE format('CREATE ROLE sibim_app LOGIN PASSWORD %L', clave);
    ELSE
        EXECUTE format('ALTER ROLE sibim_app WITH LOGIN PASSWORD %L', clave);
    END IF;
END $$;

-- Solo datos: leer y escribir filas, usar secuencias.
GRANT USAGE ON SCHEMA public TO sibim_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sibim_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sibim_app;

-- Sin DDL. (En PostgreSQL < 15 el rol PUBLIC puede crear objetos en public.)
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM sibim_app;

-- La bitácora solo crece. (Los ON DELETE SET NULL que la tocan al borrar un
-- usuario corren con los permisos del dueño de la tabla, así que siguen
-- funcionando.)
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM sibim_app;

-- El historial de migraciones solo se lee (validación al arrancar).
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM sibim_app;

-- Las tablas que creen migraciones futuras (ejecutadas por este mismo
-- usuario dueño) quedan accesibles para sibim_app sin volver a correr esto.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sibim_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sibim_app;

-- Supabase publica el esquema public en su API REST para los roles anon y
-- authenticated; con la clave pública del proyecto cualquiera podría leer o
-- escribir las tablas (incluidos los hashes de contraseñas). SIBIM no usa esa
-- API: se les quita todo acceso aquí. (No existen fuera de Supabase.)
DO $$
DECLARE
    r TEXT;
BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', r);
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', r);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM %I', r);
        END IF;
    END LOOP;
END $$;
