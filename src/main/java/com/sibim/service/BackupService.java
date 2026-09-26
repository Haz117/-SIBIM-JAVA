package com.sibim.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sibim.db.DatabaseConfig;
import com.sibim.repository.AuditLogRepository;
import com.sibim.session.SessionManager;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Whole-database backup/restore, driven purely over JDBC (no pg_dump/psql
 *  available or bundled) — for each table, a plain {@code SELECT *} is read
 *  generically via {@link ResultSetMetaData} into a list of column→value
 *  maps, so this works for any column added to the schema later without
 *  per-table Java mapping code. Only usable against the real Postgres
 *  connection (see {@link #requireOnlineMode()}) — offline/demo mode has no
 *  single source of truth to dump. */
public class BackupService {

    /** Every data table, parents before children (per the FKs in the
     *  migrations). Restore deletes in the reverse of this order, then
     *  re-inserts in this order, all inside one transaction.
     *
     *  A table missing here is not just "not backed up": restore deletes
     *  products, so a child with ON DELETE CASCADE (fotos, historial de
     *  precios, mantenimiento) would be wiped without being put back, and one
     *  with RESTRICT (comodatos) would make every restore fail.
     *  BackupServiceTablasIntegrationTest fails when a migration adds a table
     *  that is in neither this list nor {@link #TABLAS_EXCLUIDAS}. */
    public static final List<String> TABLAS = List.of(
        "users", "categories", "configuracion", "folios", "filtros_guardados",
        "products", "product_fotos", "price_history", "producto_mantenimiento",
        "movements", "audit_log", "conteos_fisicos", "conteo_items",
        "resguardos", "resguardo_items", "prestamos", "comodatos",
        "actas_entrega_recepcion", "area_resguardos");

    /** Tables deliberately left out: migration bookkeeping and transient
     *  login counters. */
    public static final Set<String> TABLAS_EXCLUIDAS = Set.of(
        "flyway_schema_history", "schema_version", "login_attempts");

    /** The audit trail is append-only: a restore never deletes it, it only
     *  adds back the entries the backup has that the database lost — so the
     *  restore itself (and whatever happened before it) stays on record. */
    private static final String TABLA_AUDITORIA = "audit_log";

    /** 2 = every table in {@link #TABLAS}; 1 = the first 11 only. */
    private static final int BACKUP_VERSION = 2;

    private final ObjectMapper mapper;

    public BackupService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        // Amounts are NUMERIC(12,2): read them back as BigDecimal, not double.
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }

    /** Dumps every table into {@code destino}, encrypted with {@code password}
     *  (AES-256-GCM, see {@link BackupEncryption}) — the dump includes every
     *  user's bcrypt password hash (see {@code TABLAS}), so an unencrypted
     *  backup sitting on a USB drive or in an email would hand out everyone's
     *  hash bundle to whoever finds it. */
    public void backup(File destino, char[] password) throws SQLException, IOException {
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
        byte[] json = mapper.writeValueAsBytes(raiz);
        byte[] encrypted = BackupEncryption.encrypt(json, password);
        java.nio.file.Files.write(destino.toPath(), encrypted);
        new AuditLogRepository().log("backup", destino.getName(), destino.getName(), "crear",
            "Respaldo completo generado");
    }

    /** Replaces every row in every table (except the audit trail, which is
     *  only added to) with what's in {@code origen}. Runs inside a single
     *  transaction — any failure rolls back completely, never leaving the
     *  database half-restored.
    *  @throws BackupEncryption.WrongPasswordException if {@code password}
    *  doesn't match the one used to create an encrypted backup. Plain JSON
    *  backups are rejected because they contain password hashes and complete
    *  municipal inventory data. */
    @SuppressWarnings("unchecked")
    public void restore(File origen, char[] password) throws SQLException, IOException, BackupEncryption.WrongPasswordException {
        requireOnlineMode();
        byte[] raw = java.nio.file.Files.readAllBytes(origen.toPath());
        if (!BackupEncryption.isEncrypted(raw)) {
            throw new IOException("El respaldo no está cifrado con AES-256-GCM y no puede restaurarse");
        }
        byte[] json = BackupEncryption.decrypt(raw, password);
        Map<String, Object> raiz = mapper.readValue(json, Map.class);
        Object tablasObj = raiz.get("tablas");
        if (!(tablasObj instanceof Map)) throw new IOException("Archivo de respaldo inválido: falta 'tablas'");
        Map<String, List<Map<String, Object>>> tablas = (Map<String, List<Map<String, Object>>>) tablasObj;

        try (Connection conn = DatabaseConfig.getConnection()) {
            validarTablas(conn, tablas);
            conn.setAutoCommit(false);
            try {
                rechazarSiBorraDatosNoRespaldados(conn, tablas);
                for (int i = TABLAS.size() - 1; i >= 0; i--) {
                    if (!TABLA_AUDITORIA.equals(TABLAS.get(i))) borrarTabla(conn, TABLAS.get(i));
                }
                for (String tabla : TABLAS) {
                    List<Map<String, Object>> filas = tablas.get(tabla);
                    if (filas != null) insertarFilas(conn, tabla, filas, TABLA_AUDITORIA.equals(tabla));
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            new AuditLogRepository().log("backup", origen.getName(), origen.getName(), "restaurar",
                "Restauración completa aplicada");
        }
    }

    private void requireOnlineMode() throws SQLException {
        if (!SessionManager.isAdmin())
            throw new SecurityException("Solo el administrador puede realizar respaldos y restauraciones");
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            throw new SQLException(
                "Respaldo/restauración solo disponible conectado a la base de datos principal "
                + "(no en modo offline ni demostración)");
    }

    /** A backup made by an older version doesn't contain the tables added
     *  since. Restoring it would still empty them (products can't be deleted
     *  while their comodatos/fotos exist), so if any of them has data now the
     *  restore is refused instead of silently losing it. */
    private void rechazarSiBorraDatosNoRespaldados(Connection conn,
            Map<String, List<Map<String, Object>>> tablas) throws SQLException, IOException {
        List<String> seBorrarian = new ArrayList<>();
        for (String tabla : TABLAS) {
            if (tablas.containsKey(tabla) || TABLA_AUDITORIA.equals(tabla)) continue;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT EXISTS (SELECT 1 FROM " + tabla + ")")) {
                if (rs.next() && rs.getBoolean(1)) seBorrarian.add(tabla);
            }
        }
        if (!seBorrarian.isEmpty()) {
            throw new IOException("Este respaldo es de una versión anterior y no incluye "
                + String.join(", ", seBorrarian) + ". Restaurarlo borraría esos datos sin reponerlos, "
                + "así que no se aplicó. Genera un respaldo nuevo o restaura con pg_restore.");
        }
    }

    private List<Map<String, Object>> leerTabla(Connection conn, String tabla) throws SQLException {
        List<Map<String, Object>> filas = new ArrayList<>();
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
                    if (valor instanceof Timestamp ts) valor = ts.toLocalDateTime();
                    else if (valor instanceof Date d) valor = d.toLocalDate();
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

    private void insertarFilas(Connection conn, String tabla, List<Map<String, Object>> filas,
                               boolean soloFaltantes) throws SQLException {
        if (filas.isEmpty()) return;
        List<String> columnas = List.copyOf(filas.get(0).keySet());
        String placeholders = String.join(",", columnas.stream().map(c -> "?").toList());
        String sql = "INSERT INTO " + tabla + " (" + String.join(",", columnas) + ") VALUES (" + placeholders + ")"
            + (soloFaltantes ? " ON CONFLICT DO NOTHING" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map<String, Object> fila : filas) {
                int i = 1;
                for (String col : columnas) {
                    Object valor = fila.get(col);
                    // JSON gives back plain strings for dates, timestamps and
                    // UUIDs. Sent untyped, Postgres converts each one to its
                    // column's type (a text column keeps it as text), instead
                    // of guessing from the shape of the string.
                    if (valor instanceof String s) ps.setObject(i++, s, Types.OTHER);
                    else ps.setObject(i++, valor);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void validarTablas(Connection conn, Map<String, List<Map<String, Object>>> tablas) throws SQLException, IOException {
        if (!tablas.keySet().stream().allMatch(TABLAS::contains))
            throw new IOException("El respaldo contiene tablas no permitidas");
        for (Map.Entry<String, List<Map<String, Object>>> entry : tablas.entrySet()) {
            if (entry.getValue() == null) continue;
            Set<String> columnasPermitidas;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM " + entry.getKey() + " LIMIT 0")) {
                ResultSetMetaData meta = rs.getMetaData();
                columnasPermitidas = new HashSet<>();
                for (int i = 1; i <= meta.getColumnCount(); i++)
                    columnasPermitidas.add(meta.getColumnLabel(i));
            }
            for (Map<String, Object> fila : entry.getValue()) {
                if (fila == null || fila.keySet().stream().anyMatch(c -> !columnasPermitidas.contains(c)))
                    throw new IOException("El respaldo contiene columnas no permitidas en " + entry.getKey());
            }
        }
    }
}
