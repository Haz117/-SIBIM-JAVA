package com.sibim.db.integration;

import com.sibim.db.MigrationRunner;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MigrationRunner against a real (embedded) Postgres. Standalone — it does not
 * extend IntegrationTestBase, so it never touches DatabaseConfig's global state.
 *
 * Covers the production incident these guard against: a checksum mismatch on an
 * applied migration must NOT be silently "repaired" (which marks it as run
 * without running its SQL), and V21 must recreate the V19 columns on a database
 * whose history claims V19 ran but whose table never got them.
 */
class MigrationRunnerIntegrationTest {

    private static final String[] V19_COLUMNS = {
        "clave_armonizada", "color", "no_motor", "tipo_bien",
        "no_tarjeta_circulacion", "no_poliza_seguro"
    };

    private static EmbeddedPostgres postgres;
    private Flyway flyway;

    @BeforeAll
    static void startPostgres() throws Exception {
        postgres = EmbeddedPostgres.start();
    }

    @AfterAll
    static void stopPostgres() throws Exception {
        if (postgres != null) postgres.close();
    }

    @BeforeEach
    void freshDatabase() {
        flyway = Flyway.configure()
            .dataSource(postgres.getPostgresDatabase())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .cleanDisabled(false)
            .load();
        flyway.clean();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private long scalar(String sql) throws SQLException {
        try (Connection c = postgres.getPostgresDatabase().getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = postgres.getPostgresDatabase().getConnection();
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private long v19ColumnCount() throws SQLException {
        String in = "'" + String.join("','", V19_COLUMNS) + "'";
        return scalar("SELECT count(*) FROM information_schema.columns "
            + "WHERE table_name = 'products' AND column_name IN (" + in + ")");
    }

    private long checksumOfV19() throws SQLException {
        return scalar("SELECT checksum FROM flyway_schema_history WHERE version = '19'");
    }

    /** Leaves V21 as the next pending migration. Later versions go too: a
     *  missing V21 below an applied V22 would be an out-of-order gap, which
     *  Flyway rejects — not the production state being reproduced. */
    private void borrarHistorialDesdeV21() throws SQLException {
        exec("DELETE FROM flyway_schema_history WHERE installed_rank >= "
            + "(SELECT installed_rank FROM flyway_schema_history WHERE version = '21')");
    }

    // ── DB_MIGRATE=false (rol sin permisos de DDL) ──────────────────────────

    @Test
    void sinMigrar_conEsquemaAlDia_soloValida() throws Exception {
        MigrationRunner.run(flyway, false);
        long aplicadas = scalar("SELECT count(*) FROM flyway_schema_history");

        assertDoesNotThrow(() -> MigrationRunner.run(flyway, false, false));

        assertEquals(aplicadas, scalar("SELECT count(*) FROM flyway_schema_history"));
    }

    @Test
    void sinMigrar_conMigracionesPendientes_fallaSinTocarElEsquema() throws Exception {
        MigrationRunner.run(flyway, false);
        exec("DELETE FROM flyway_schema_history WHERE version = (SELECT max(version::int)::text "
            + "FROM flyway_schema_history WHERE version ~ '^[0-9]+$')");
        long aplicadas = scalar("SELECT count(*) FROM flyway_schema_history");

        assertThrows(MigrationRunner.MigracionesPendientesException.class,
            () -> MigrationRunner.run(flyway, false, false));

        assertEquals(aplicadas, scalar("SELECT count(*) FROM flyway_schema_history"),
            "no debe aplicar nada");
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void migracionCompleta_aplicaTodoYCreaLasColumnasDeV19() throws Exception {
        MigrationRunner.run(flyway, false);

        assertEquals(V19_COLUMNS.length, v19ColumnCount());
        assertEquals(0, scalar("SELECT count(*) FROM flyway_schema_history WHERE NOT success"));
        assertTrue(scalar("SELECT max(installed_rank) FROM flyway_schema_history") >= 21,
            "Debe llegar al menos a V21");
    }

    @Test
    void checksumAlterado_sinAutoRepair_falla_yNoReescribeElHistorial() throws Exception {
        MigrationRunner.run(flyway, false);
        exec("UPDATE flyway_schema_history SET checksum = 12345 WHERE version = '19'");

        assertThrows(FlywayException.class, () -> MigrationRunner.run(flyway, false));

        assertEquals(12345, checksumOfV19(), "El historial no debe reescribirse en silencio");
    }

    @Test
    void checksumAlterado_conAutoRepair_realineaElHistorial_yMigra() throws Exception {
        MigrationRunner.run(flyway, false);
        exec("UPDATE flyway_schema_history SET checksum = 12345 WHERE version = '19'");

        assertDoesNotThrow(() -> MigrationRunner.run(flyway, true));

        assertNotEquals(12345, checksumOfV19(), "repair() debe restaurar el checksum del archivo");
    }

    @Test
    void v21_recreaLasColumnasCuandoV19SeMarcoAplicadaSinEjecutarse() throws Exception {
        MigrationRunner.run(flyway, false);
        // Estado real de producción: historial con V19 "aplicada", columnas ausentes, V21 pendiente.
        for (String col : V19_COLUMNS) exec("ALTER TABLE products DROP COLUMN " + col);
        borrarHistorialDesdeV21();
        assertEquals(0, v19ColumnCount(), "Precondición: las columnas no existen");

        MigrationRunner.run(flyway, false);

        assertEquals(V19_COLUMNS.length, v19ColumnCount());
    }

    @Test
    void v21_esIdempotente_siLasColumnasYaExisten() throws Exception {
        MigrationRunner.run(flyway, false);
        borrarHistorialDesdeV21();

        assertDoesNotThrow(() -> MigrationRunner.run(flyway, false),
            "ADD COLUMN IF NOT EXISTS no debe fallar cuando V19 sí había corrido");

        assertEquals(V19_COLUMNS.length, v19ColumnCount());
    }
}
