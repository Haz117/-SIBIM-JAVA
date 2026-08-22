package com.sibim.db.integration;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the Flyway V1 migration (V1__schema_inicial.sql) creates the
 * expected tables, columns, and constraints in the embedded Postgres database.
 *
 * All assertions go through information_schema so they are independent of any
 * application code — if migration fails, tests here catch it before any
 * repository test even runs.
 */
class FlywayMigrationTest extends IntegrationTestBase {

    // -----------------------------------------------------------------------
    // Table existence
    // -----------------------------------------------------------------------

    @Test
    void allExpectedTablesExist() throws SQLException {
        String[] expectedTables = {
            "users", "categories", "products", "movements",
            "audit_log", "conteos_fisicos", "conteo_items"
        };
        try (Connection c = getConnection()) {
            for (String table : expectedTables) {
                assertTrue(tableExists(c, table),
                    "Expected table '" + table + "' to exist after V1 migration");
            }
        }
    }

    @Test
    void flywayHistoryTableExists() throws SQLException {
        // Flyway creates flyway_schema_history when it manages migrations.
        // V1 also creates schema_version as an application-level audit table.
        try (Connection c = getConnection()) {
            assertTrue(tableExists(c, "flyway_schema_history"),
                "flyway_schema_history must exist — Flyway did not complete migration");
            assertTrue(tableExists(c, "schema_version"),
                "schema_version table (application audit) must exist after V1");
        }
    }

    // -----------------------------------------------------------------------
    // Column existence — depreciation columns added in V1
    // -----------------------------------------------------------------------

    @Test
    void productsHasDeprecationColumns() throws SQLException {
        try (Connection c = getConnection()) {
            assertTrue(columnExists(c, "products", "fecha_adquisicion"),
                "products.fecha_adquisicion must exist");
            assertTrue(columnExists(c, "products", "vida_util_anios"),
                "products.vida_util_anios must exist");
            assertTrue(columnExists(c, "products", "valor_residual"),
                "products.valor_residual must exist");
        }
    }

    @Test
    void movementsHasEstadoColumn() throws SQLException {
        try (Connection c = getConnection()) {
            assertTrue(columnExists(c, "movements", "estado"),
                "movements.estado column must exist (added in V1 idempotent block)");
        }
    }

    // -----------------------------------------------------------------------
    // Constraint verification — NOT NULL on required products columns
    // -----------------------------------------------------------------------

    /**
     * Verifies that nombre, codigo, and area are NOT NULL in products by
     * attempting to insert a row with NULL values and expecting a SQLException.
     * A category row is inserted first to satisfy the FK constraint.
     */
    @Test
    void productsHasRequiredConstraints() throws SQLException {
        // Seed a category so the FK is satisfied on valid inserts.
        try (Connection c = getConnection()) {
            c.createStatement().execute(
                "INSERT INTO categories (id, nombre, color) VALUES ('cat-constraint-test', 'Test Cat', '#000000')"
            );
        }

        // nombre NOT NULL
        assertThrows(SQLException.class, () -> {
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO products (id, nombre, codigo, categoria_id, area) " +
                     "VALUES ('p1', NULL, 'COD-001', 'cat-constraint-test', 'Area X')")) {
                ps.executeUpdate();
            }
        }, "Inserting NULL nombre must violate NOT NULL constraint");

        // codigo NOT NULL
        assertThrows(SQLException.class, () -> {
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO products (id, nombre, codigo, categoria_id, area) " +
                     "VALUES ('p2', 'Producto', NULL, 'cat-constraint-test', 'Area X')")) {
                ps.executeUpdate();
            }
        }, "Inserting NULL codigo must violate NOT NULL constraint");

        // area NOT NULL
        assertThrows(SQLException.class, () -> {
            try (Connection c = getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO products (id, nombre, codigo, categoria_id, area) " +
                     "VALUES ('p3', 'Producto', 'COD-003', 'cat-constraint-test', NULL)")) {
                ps.executeUpdate();
            }
        }, "Inserting NULL area must violate NOT NULL constraint");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private boolean tableExists(Connection c, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean columnExists(Connection c, String tableName, String columnName) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.columns " +
                     "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
