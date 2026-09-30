package com.sibim.db.offline;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** offline.db files created before 2026-09 have a global UNIQUE on
 *  products.codigo; the server (V14) only requires it among active bienes. */
class OfflineCodigoUnicoMigrationTest {

    private static final String DDL_VIEJO = """
        CREATE TABLE products (
            id          TEXT PRIMARY KEY,
            nombre      TEXT NOT NULL,
            codigo      TEXT UNIQUE NOT NULL,
            area        TEXT,
            fecha_baja  TEXT
        )""";

    private static void insertar(Connection c, String id, String codigo, String baja) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("INSERT INTO products (id, nombre, codigo, area, fecha_baja) VALUES ('" + id
                + "', 'x', '" + codigo + "', 'A', " + (baja == null ? "NULL" : "'" + baja + "'") + ")");
        }
    }

    @Test
    void reconstruyeLaTabla_conservaLosDatos_yPermiteReusarElCodigoDeUnaBaja() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement st = c.createStatement()) { st.execute(DDL_VIEJO); }
            insertar(c, "1", "TICS/01", "2026-01-01");
            assertThrows(SQLException.class, () -> insertar(c, "2", "TICS/01", null), "antes: único global");

            OfflineStore.codigoUnicoSoloEntreActivos(c);

            insertar(c, "2", "TICS/01", null);   // el número de la baja se reutiliza
            assertThrows(SQLException.class, () -> insertar(c, "3", "TICS/01", null),
                "dos bienes ACTIVOS no pueden compartir código");
            try (Statement st = c.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM products")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "la fila original sigue ahí");
            }

            OfflineStore.codigoUnicoSoloEntreActivos(c);   // idempotente
        }
    }
}
