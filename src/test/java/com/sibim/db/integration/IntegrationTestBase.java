package com.sibim.db.integration;

import com.sibim.db.DatabaseConfig;
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
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTestBase {

    private EmbeddedPostgres postgres;
    protected HikariDataSource dataSource;

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

        // Point all repository code at the embedded DB for the duration of the
        // test suite. setDataSourceForTest also forces demo=false, offline=false.
        DatabaseConfig.setDataSourceForTest(dataSource);

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
        // DatabaseConfig.close() nils the internal reference; the pool itself
        // is owned by our local field and closed below.
        DatabaseConfig.close();
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
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
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute(
                "TRUNCATE conteo_items, conteos_fisicos, movements, products, categories, users, audit_log CASCADE"
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

    /** Returns a raw connection to the embedded Postgres for direct JDBC assertions. */
    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
