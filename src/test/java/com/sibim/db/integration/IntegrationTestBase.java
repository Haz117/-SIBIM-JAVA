package com.sibim.db.integration;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.offline.SyncService;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Base class for integration tests that require a real PostgreSQL database.
 *
 * Lifecycle (PER_CLASS so @BeforeAll/@AfterAll are non-static):
 *   @BeforeAll  — starts EmbeddedPostgres, applies Flyway V1, wraps in HikariCP,
 *                 injects the DataSource into DatabaseConfig, and seeds an ADMIN
 *                 session so methods guarded by requireAdmin() pass.
 *   @BeforeEach — truncates all application tables so every test starts clean.
 *   @AfterAll   — logs out, closes HikariCP pool, stops EmbeddedPostgres.
 *
 * With -Dsibim.it.comoApp=true the app code runs the way the installed PCs
 * connect: scripts/sql/rol_app_minimo.sql is applied and DatabaseConfig gets a
 * pool of sibim_app (no DDL, RLS on every table). {@link #dataSource} stays the
 * database owner, for the cleanup and the tests' own assertions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {

    private static final boolean COMO_APP = Boolean.getBoolean("sibim.it.comoApp");
    /** Same as RolAppMinimoIntegrationTest, which applies the script again. */
    static final String CLAVE_APP = "clave-de-prueba";

    private EmbeddedPostgres postgres;
    protected HikariDataSource dataSource;
    /** What the app code uses: {@link #dataSource}, or sibim_app's pool. */
    private HikariDataSource appDataSource;

    @BeforeAll
    void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();

        // Apply V1 migration against the embedded Postgres.
        // baselineOnMigrate + baselineVersion("0") handles the idempotent DO $$
        // block in V1 that is normally used for pre-Flyway installations.
        Flyway.configure()
            .dataSource(postgres.getPostgresDatabase())
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate();

        HikariConfig cfg = new HikariConfig();
        cfg.setDataSource(postgres.getPostgresDatabase());
        cfg.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(cfg);
        appDataSource = dataSource;
        if (COMO_APP) {
            String script = java.nio.file.Files.readString(
                    java.nio.file.Path.of(System.getProperty("basedir", "..")).resolve("scripts/sql/rol_app_minimo.sql"),
                    java.nio.charset.StandardCharsets.UTF_8)
                .replace("clave TEXT := 'CAMBIA-ESTA-CONTRASENA';", "clave TEXT := '" + CLAVE_APP + "';");
            try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
                st.execute(script);
            }
            HikariConfig app = new HikariConfig();
            app.setJdbcUrl(postgres.getJdbcUrl("sibim_app", "postgres"));
            app.setUsername("sibim_app");
            app.setPassword(CLAVE_APP);
            app.setMaximumPoolSize(5);
            appDataSource = new HikariDataSource(app);
        }

        // Point all repository code at the embedded DB for the duration of the
        // test suite. setDataSourceForTest also forces demo=false, offline=false.
        DatabaseConfig.setDataSourceForTest(appDataSource);

        // Seed an ADMIN session. ConteoRepository.findAll() and any other method
        // guarded by requireAdmin() will use SessionManager.isAdmin() at call time,
        // so the session must be set before the first @BeforeEach runs.
        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Admin Test");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);
    }

    @AfterAll
    void stopDatabase() throws Exception {
        SessionManager.logout();
        // Only clear the pool if this test class still owns the active global
        // reference; another integration class may have installed its own.
        DatabaseConfig.clearDataSourceForTest(appDataSource);
        if (appDataSource != null && appDataSource != dataSource && !appDataSource.isClosed()) appDataSource.close();
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        if (postgres != null) {
            postgres.close();
        }
    }

    /**
     * Wipes all application data between tests. Order matters: child tables
     * must come before parent tables to avoid FK violations. CASCADE handles
     * conteo_items via conteos_fisicos, and movements references products, so
     * movements must be truncated before products.
     */
    @BeforeEach
    void truncateAll() throws SQLException {
        // Other unit tests exercise demo/offline modes in the same Surefire
        // JVM. Restore the integration contract before touching the database.
        // A SyncService watcher leaked by an earlier test would otherwise flip
        // DatabaseConfig to offline mode mid-test when it sees a pool close.
        SyncService.stopWatching();
        DatabaseConfig.setDemoMode(false);
        DatabaseConfig.setOfflineMode(false);
        DatabaseConfig.setDataSourceForTest(appDataSource);
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute(
                "TRUNCATE resguardo_items, resguardos, prestamos, actas_entrega_recepcion, " +
                "price_history, conteo_items, conteos_fisicos, movements, products, categories, " +
                "users, audit_log, area_resguardos, configuracion, actualizaciones CASCADE"
            );
        }
        // Re-seed the admin user row so FK constraints on movements and
        // conteos_fisicos (usuario_id → users.id) are satisfied after the TRUNCATE.
        try (Connection c = dataSource.getConnection();
             java.sql.PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (id, username, password, nombre, role) VALUES (?,?,?,?,?)")) {
            ps.setString(1, "test-admin");
            ps.setString(2, "admin-test");
            ps.setString(3, "$2a$10$placeholder");
            ps.setString(4, "Admin Test");
            ps.setString(5, "admin");
            ps.executeUpdate();
        }
    }

    /** Whether the app code runs as sibim_app (-Dsibim.it.comoApp=true). */
    protected static boolean corriendoComoSibimApp() { return COMO_APP; }

    /** Returns a raw connection to the embedded Postgres for direct JDBC assertions. */
    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
