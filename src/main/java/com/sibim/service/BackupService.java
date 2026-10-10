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
        "areas", "users", "categories", "configuracion", "folios", "filtros_guardados",
        "products", "product_fotos", "price_history", "producto_mantenimiento",
        "movements", "audit_log", "conteos_fisicos", "conteo_items",
        "resguardos", "resguardo_items", "prestamos", "comodatos",
        "actas_entrega_recepcion", "area_resguardos");

    /** Tables deliberately left out: migration bookkeeping, transient login
     *  counters, the published installers (~110 MB each, regenerable) and the
     *  areas' requests (the préstamo or resguardo they led to is what counts)
     *  and the log of which PC each account signed in on. */
    public static final Set<String> TABLAS_EXCLUIDAS = Set.of(
        "flyway_schema_history", "schema_version", "login_attempts",
        "actualizaciones", "actualizacion_partes", "solicitudes", "accesos_equipo", "errores_equipo");

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
        // Compact: the whole dump is held in memory to be encrypted, and
        // indenting it only made it larger (nobody reads an encrypted file).
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

    /** Replaces every row of every table {@code origen} contains (except the
     *  audit trail, which is only added to); tables an older backup doesn't
     *  have are left as they are. Runs inside a single
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
                apartarLoQueNoVaEnElRespaldo(conn);
                // Only tables the backup contains are replaced; the audit trail
                // is only added to.
                for (int i = TABLAS.size() - 1; i >= 0; i--) {
                    String tabla = TABLAS.get(i);
                    if (tablas.containsKey(tabla) && !TABLA_AUDITORIA.equals(tabla)) borrarTabla(conn, tabla);
                }
                for (String tabla : TABLAS) {
                    List<Map<String, Object>> filas = tablas.get(tabla);
                    if (filas != null) insertarFilas(conn, tabla, filas, TABLA_AUDITORIA.equals(tabla));
                }
                reponerLoApartado(conn);
                conn.commit();
                ProductosEnMemoria.invalidar();
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

    /** Tables left out of the backup that hang off one the restore empties
     *  (ON DELETE CASCADE): table → {its FK column, the table it points at}.
     *  Their rows are set aside and put back for the bienes/cuentas that
     *  still exist afterwards; without this a restore silently wiped the
     *  áreas' requests and the list of PCs each account has signed in on. */
    private static final Map<String, String[]> DEPENDIENTES_EXCLUIDAS = Map.of(
        "solicitudes",    new String[]{"producto_id", "products"},
        "accesos_equipo", new String[]{"user_id", "users"});

    private void apartarLoQueNoVaEnElRespaldo(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (String tabla : DEPENDIENTES_EXCLUIDAS.keySet())
                st.execute("CREATE TEMP TABLE _resp_" + tabla + " ON COMMIT DROP AS SELECT * FROM " + tabla);
            // Deleting users sets audit_log.usuario_id to NULL (V20) on every
            // entry the bitácora keeps: remember who each one belonged to.
            st.execute("CREATE TEMP TABLE _resp_audit_usuario ON COMMIT DROP AS "
                + "SELECT id, usuario_id FROM audit_log WHERE usuario_id IS NOT NULL");
        }
    }

    private void reponerLoApartado(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            for (Map.Entry<String, String[]> e : DEPENDIENTES_EXCLUIDAS.entrySet()) {
                String tabla = e.getKey(), columna = e.getValue()[0], padre = e.getValue()[1];
                st.executeUpdate("INSERT INTO " + tabla + " SELECT t.* FROM _resp_" + tabla + " t "
                    + "WHERE EXISTS (SELECT 1 FROM " + padre + " p WHERE p.id = t." + columna + ") "
                    + "ON CONFLICT DO NOTHING");
            }
        }
        // The app's own database role may not UPDATE the bitácora
        // (rol_app_minimo.sql): then the names stay, only the link is lost.
        java.sql.Savepoint antes = conn.setSavepoint();
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE audit_log a SET usuario_id = t.usuario_id FROM _resp_audit_usuario t "
                + "WHERE a.id = t.id AND a.usuario_id IS NULL "
                + "AND EXISTS (SELECT 1 FROM users u WHERE u.id = t.usuario_id)");
        } catch (SQLException e) {
            conn.rollback(antes);
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
     *  since. Those are left as they are — unless they hang off a table the
     *  restore replaces: deleting products fails while a comodato points at
     *  it (RESTRICT) and silently takes fotos, historial de precios and
     *  mantenimiento with it (CASCADE). If such a table has data, the restore
     *  is refused instead of losing it. */
    private void rechazarSiBorraDatosNoRespaldados(Connection conn,
            Map<String, List<Map<String, Object>>> tablas) throws SQLException, IOException {
        List<String> seBorrarian = new ArrayList<>();
        for (String tabla : TABLAS) {
            if (tablas.containsKey(tabla) || TABLA_AUDITORIA.equals(tabla)) continue;
            if (tablasReferenciadas(conn, tabla).stream().noneMatch(tablas::containsKey)) continue;
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

    /** Tables {@code tabla} has a foreign key to. */
    private static Set<String> tablasReferenciadas(Connection conn, String tabla) throws SQLException {
        Set<String> referenciadas = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT confrelid::regclass::text FROM pg_constraint "
                + "WHERE contype = 'f' AND conrelid = ?::regclass")) {
            ps.setString(1, tabla);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) referenciadas.add(rs.getString(1));
            }
        }
        return referenciadas;
    }

    /** Row order for tables that reference themselves, so a restore inserts
     *  every parent before its children (areas.padre → areas.nombre). */
    private static final Map<String, String> ORDEN = Map.of(
        "areas", " ORDER BY (grupo = 'DIRECCION'), orden");

    private List<Map<String, Object>> leerTabla(Connection conn, String tabla) throws SQLException {
        List<Map<String, Object>> filas = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + tabla + ORDEN.getOrDefault(tabla, ""))) {
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
                    // bytea (e.g. area_resguardos.pdf): PostgreSQL's hex text
                    // form, which the untyped insert on restore turns back
                    // into the exact bytes.
                    else if (valor instanceof byte[] b) valor = "\\x" + java.util.HexFormat.of().formatHex(b);
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
