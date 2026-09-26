package com.sibim.db.integration;

import com.sibim.db.MigrationRunner;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** scripts/sql/rol_app_minimo.sql against a real Postgres: the app can do
 *  everything it needs with sibim_app, and nothing beyond that. */
class RolAppMinimoIntegrationTest extends IntegrationTestBase {

    private static final String CLAVE = "clave-de-prueba";
    private String url;

    @BeforeAll
    void aplicarScript() throws Exception {
        Path base = Path.of(System.getProperty("basedir", ".."));
        String script = Files.readString(base.resolve("scripts/sql/rol_app_minimo.sql"), StandardCharsets.UTF_8)
            .replace("clave TEXT := 'CAMBIA-ESTA-CONTRASENA';", "clave TEXT := '" + CLAVE + "';");
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            url = c.getMetaData().getURL();
            // Supabase's REST roles, with the grants Supabase gives them by default.
            st.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') "
                + "THEN CREATE ROLE anon NOLOGIN; END IF; END $$");
            st.execute("GRANT ALL ON ALL TABLES IN SCHEMA public TO anon");
            st.execute(script);
        }
    }

    private Connection comoApp() throws SQLException {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(url);
        ds.setUser("sibim_app");
        ds.setPassword(CLAVE);
        return ds.getConnection();
    }

    private static String sqlState(Executable accion) {
        SQLException ex = assertThrows(SQLException.class, accion::run);
        return ex.getSQLState();
    }

    @FunctionalInterface
    private interface Executable { void run() throws Exception; }

    @Test
    void laBitacoraSoloCrece() throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO audit_log (id, entidad, entidad_id, accion, usuario_nombre) "
                + "VALUES ('a-1', 'producto', 'p', 'crear', 'App')");
            assertEquals("42501", sqlState(() -> st.execute("DELETE FROM audit_log")));
            assertEquals("42501", sqlState(() -> st.execute("UPDATE audit_log SET detalle = 'x'")));
            assertEquals("42501", sqlState(() -> st.execute("TRUNCATE audit_log")));
        }
    }

    @Test
    void puedeTrabajarConLosDatos_peroNoCambiarElEsquema() throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO categories (id, nombre, color) VALUES ('c-app', 'Cat App', '#000000')");
            st.execute("INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad) "
                + "VALUES ('p-app', 'Bien', 'APP-1', 'c-app', 'Área', 'pieza')");
            st.execute("UPDATE products SET nombre = 'Bien 2' WHERE id = 'p-app'");
            st.execute("DELETE FROM products WHERE id = 'p-app'");
            st.execute("INSERT INTO login_attempts (username) VALUES ('alguien')");

            assertEquals("42501", sqlState(() -> st.execute("CREATE TABLE intrusa (id INT)")));
            assertEquals("42501", sqlState(() -> st.execute("DROP TABLE movements")));
            assertEquals("42501", sqlState(() -> st.execute("ALTER TABLE products ADD COLUMN x INT")));
            assertEquals("42501", sqlState(() -> st.execute(
                "DELETE FROM flyway_schema_history")));
        }
    }

    @Test
    void borrarUnUsuarioConBitacora_sigueFuncionando() throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement()) {
            st.execute("INSERT INTO users (id, username, password, nombre, role) "
                + "VALUES ('u-borrar', 'borrar', 'x', 'Borrar', 'direccion')");
            st.execute("INSERT INTO audit_log (id, entidad, entidad_id, accion, usuario_id, usuario_nombre) "
                + "VALUES ('a-u', 'sesion', 'u-borrar', 'login', 'u-borrar', 'Borrar')");
            st.execute("DELETE FROM users WHERE id = 'u-borrar'");   // ON DELETE SET NULL on audit_log
            try (ResultSet rs = st.executeQuery("SELECT usuario_id FROM audit_log WHERE id = 'a-u'")) {
                assertTrue(rs.next());
                assertNull(rs.getString(1), "la entrada se conserva, sin el vínculo al usuario");
            }
        }
    }

    @Test
    void conDbMigrateFalse_arrancaValidandoSinPermisosDeDdl() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(url);
        ds.setUser("sibim_app");
        ds.setPassword(CLAVE);
        Flyway flyway = Flyway.configure().dataSource(ds)
            .baselineOnMigrate(true).baselineVersion("0").load();
        assertDoesNotThrow(() -> MigrationRunner.run(flyway, false, false));
    }

    @Test
    void laApiRestDeSupabaseYaNoVeLasTablas() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT has_table_privilege('anon', 'users', 'SELECT')")) {
            assertTrue(rs.next());
            assertFalse(rs.getBoolean(1));
        }
    }
}
