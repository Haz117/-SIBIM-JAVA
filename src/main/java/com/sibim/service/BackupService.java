package com.sibim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sibim.db.DatabaseConfig;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Whole-database backup/restore, driven purely over JDBC (no pg_dump/psql
 *  available or bundled) — for each table, a plain {@code SELECT *} is read
 *  generically via {@link ResultSetMetaData} into a list of column→value
 *  maps, so this works for any column added to the schema later without
 *  per-table Java mapping code. Only usable against the real Postgres
 *  connection (see {@link #requireOnlineMode()}) — offline/demo mode has no
 *  single source of truth to dump. */
public class BackupService {

    /** Insert order (parents before children, per the FKs in sibim.sql).
     *  Restore deletes in the reverse of this order, then re-inserts in
     *  this order, all inside one transaction. */
    private static final List<String> TABLAS = List.of(
        "users", "categories", "products", "movements",
        "audit_log", "conteos_fisicos", "conteo_items");

    private static final int BACKUP_VERSION = 1;

    private final ObjectMapper mapper;

    public BackupService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /** Dumps every table into {@code destino} as a single JSON file. */
    public void backup(File destino) throws SQLException, IOException {
        requireOnlineMode();
        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("version", BACKUP_VERSION);
        raiz.put("exportadoEn", LocalDateTime.now());
        Map<String, List<Map<String, Object>>> tablas = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection()) {
            for (String tabla : TABLAS) {
                tablas.put(tabla, leerTabla(conn, tabla));
            }
        }
        raiz.put("tablas", tablas);
        mapper.writeValue(destino, raiz);
    }

    /** Replaces every row in every table with what's in {@code origen}.
     *  Runs inside a single transaction — any failure rolls back completely,
     *  never leaving the database half-restored. */
    @SuppressWarnings("unchecked")
    public void restore(File origen) throws SQLException, IOException {
        requireOnlineMode();
        Map<String, Object> raiz = mapper.readValue(origen, Map.class);
        Object tablasObj = raiz.get("tablas");
        if (!(tablasObj instanceof Map)) throw new IOException("Archivo de respaldo inválido: falta 'tablas'");
        Map<String, List<Map<String, Object>>> tablas = (Map<String, List<Map<String, Object>>>) tablasObj;

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (int i = TABLAS.size() - 1; i >= 0; i--) {
                    borrarTabla(conn, TABLAS.get(i));
                }
                for (String tabla : TABLAS) {
                    List<Map<String, Object>> filas = tablas.get(tabla);
                    if (filas != null) insertarFilas(conn, tabla, filas);
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private void requireOnlineMode() throws SQLException {
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            throw new SQLException(
                "Respaldo/restauración solo disponible conectado a la base de datos principal "
                + "(no en modo offline ni demostración)");
    }

    private List<Map<String, Object>> leerTabla(Connection conn, String tabla) throws SQLException {
        List<Map<String, Object>> filas = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + tabla)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> fila = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    Object valor = rs.getObject(i);
                    // Timestamps come back as java.sql.Timestamp/Date, which
                    // Jackson+JavaTimeModule doesn't handle directly — convert
                    // to the java.time equivalent so round-tripping is exact.
                    if (valor instanceof java.sql.Timestamp ts) valor = ts.toLocalDateTime();
                    else if (valor instanceof java.sql.Date d) valor = d.toLocalDate();
                    fila.put(meta.getColumnLabel(i), valor);
                }
                filas.add(fila);
            }
        }
        return filas;
    }

    private void borrarTabla(Connection conn, String tabla) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM " + tabla);
        }
    }

    private void insertarFilas(Connection conn, String tabla, List<Map<String, Object>> filas) throws SQLException {
        if (filas.isEmpty()) return;
        List<String> columnas = List.copyOf(filas.get(0).keySet());
        String placeholders = String.join(",", columnas.stream().map(c -> "?").toList());
        String sql = "INSERT INTO " + tabla + " (" + String.join(",", columnas) + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> fila : filas) {
                int i = 1;
                for (String col : columnas) {
                    Object valor = fila.get(col);
                    if (valor instanceof String s && looksLikeDateTime(s)) {
                        ps.setObject(i++, java.time.LocalDateTime.parse(s));
                    } else if (valor instanceof String s && looksLikeDate(s)) {
                        ps.setObject(i++, java.time.LocalDate.parse(s));
                    } else {
                        ps.setObject(i++, valor);
                    }
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // Jackson serializes LocalDateTime/LocalDate as ISO strings; on the way
    // back in they're plain Strings from the generic Map<String,Object>
    // deserialization, so re-parse them into java.time types the JDBC driver
    // can bind to a TIMESTAMPTZ/DATE column correctly.
    private boolean looksLikeDateTime(String s) {
        return s.length() >= 19 && s.charAt(4) == '-' && s.charAt(7) == '-' && s.charAt(10) == 'T';
    }

    private boolean looksLikeDate(String s) {
        return s.length() == 10 && s.charAt(4) == '-' && s.charAt(7) == '-';
    }
}
