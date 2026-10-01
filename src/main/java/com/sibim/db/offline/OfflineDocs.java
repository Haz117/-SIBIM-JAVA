package com.sibim.db.offline;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sibim.db.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Offline copy of the documents that live only on the server — resguardos,
 * préstamos, comodatos, actas — so they can be read, created and printed
 * without a connection. Each one is kept whole as JSON in {@code doc_cache}
 * (inside the same encrypted offline.db as the bienes mirror): these
 * documents are few and always used whole, so a column per field — what the
 * bienes mirror needed — would only be more code to keep in step with the
 * model. Changes made offline wait in {@code doc_outbox} until SyncService
 * replays them through the repositories (see {@link #pendientes()}).
 *
 * Online reads refresh the copy through {@link #guardarTodos}; like the rest
 * of the offline mirror that is best-effort and never breaks the real read.
 */
public final class OfflineDocs {

    private static final Logger log = LoggerFactory.getLogger(OfflineDocs.class);

    public static final String RESGUARDO = "resguardo";
    public static final String PRESTAMO  = "prestamo";
    public static final String COMODATO  = "comodato";
    public static final String ACTA      = "acta";
    public static final String AREA_RESGUARDO     = "area_resguardo";
    /** The PDF of an área resguardo, kept apart from its row: it's only
     *  copied once someone opens it with a connection (they can weigh MB). */
    public static final String AREA_RESGUARDO_PDF = "area_resguardo_pdf";

    /** Fields, not getters: the models have derived getters (días restantes,
     *  vencido…) that aren't state and some of them need other fields set. */
    static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    public record Pendiente(int id, String tipo, String operacion, String docId, String json, int reintentos) {}

    private OfflineDocs() {}

    /** Offline, and not in demo mode (demo keeps its own in-memory data). */
    public static boolean activo() {
        return DatabaseConfig.isOfflineMode() && !DatabaseConfig.isDemoMode();
    }

    static void crearTablas(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS doc_cache (tipo TEXT NOT NULL, id TEXT NOT NULL, "
                + "json TEXT NOT NULL, PRIMARY KEY (tipo, id))");
            st.execute("CREATE TABLE IF NOT EXISTS doc_outbox (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "tipo TEXT NOT NULL, operacion TEXT NOT NULL, doc_id TEXT NOT NULL, json TEXT, "
                + "created_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', error TEXT, "
                + "retry_count INTEGER NOT NULL DEFAULT 0)");
        }
    }

    // ── Copy ───────────────────────────────────────────────────────────────

    /** Refreshes the copy after an online read. Never throws. */
    public static <T> void guardarTodos(String tipo, List<T> docs, Function<T, String> id) {
        if (docs == null || docs.isEmpty() || DatabaseConfig.isDemoMode()) return;
        try {
            Connection db = OfflineStore.sharedConnection();
            synchronized (OfflineStore.class) {
                boolean auto = db.getAutoCommit();
                db.setAutoCommit(false);
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO doc_cache (tipo, id, json) VALUES (?,?,?) "
                        + "ON CONFLICT(tipo, id) DO UPDATE SET json = excluded.json")) {
                    for (T d : docs) {
                        ps.setString(1, tipo);
                        ps.setString(2, id.apply(d));
                        ps.setString(3, JSON.writeValueAsString(d));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                    db.commit();
                } catch (Exception e) {
                    db.rollback();
                    throw e;
                } finally {
                    db.setAutoCommit(auto);
                }
            }
        } catch (Exception e) {
            log.warn("OfflineDocs: no se pudo refrescar la copia local de {}", tipo, e);
        }
    }

    public static <T> void guardar(String tipo, String id, T doc) throws SQLException {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "INSERT INTO doc_cache (tipo, id, json) VALUES (?,?,?) "
                + "ON CONFLICT(tipo, id) DO UPDATE SET json = excluded.json")) {
            ps.setString(1, tipo);
            ps.setString(2, id);
            ps.setString(3, escribir(doc));
            ps.executeUpdate();
        }
    }

    public static <T> List<T> todos(String tipo, Class<T> clase) throws SQLException {
        List<T> lista = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT json FROM doc_cache WHERE tipo = ?")) {
            ps.setString(1, tipo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    T d = leer(rs.getString(1), clase);
                    if (d != null) lista.add(d);
                }
            }
        }
        return lista;
    }

    public static <T> Optional<T> uno(String tipo, String id, Class<T> clase) throws SQLException {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT json FROM doc_cache WHERE tipo = ? AND id = ?")) {
            ps.setString(1, tipo);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(leer(rs.getString(1), clase)) : Optional.empty();
            }
        }
    }

    // ── Queue ──────────────────────────────────────────────────────────────

    /** Stores the document in the copy and queues the change, atomically. */
    public static <T> void registrar(String tipo, String operacion, String id, T doc) throws SQLException {
        Connection db = OfflineStore.sharedConnection();
        synchronized (OfflineStore.class) {
            boolean auto = db.getAutoCommit();
            db.setAutoCommit(false);
            try {
                guardar(tipo, id, doc);
                try (PreparedStatement ps = db.prepareStatement(
                        "INSERT INTO doc_outbox (tipo, operacion, doc_id, json, created_at) VALUES (?,?,?,?,?)")) {
                    ps.setString(1, tipo);
                    ps.setString(2, operacion);
                    ps.setString(3, id);
                    ps.setString(4, escribir(doc));
                    ps.setString(5, LocalDateTime.now().toString());
                    ps.executeUpdate();
                }
                db.commit();
            } catch (SQLException e) {
                db.rollback();
                throw e;
            } finally {
                db.setAutoCommit(auto);
            }
        }
    }

    public static void borrar(String tipo, String id) throws SQLException {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "DELETE FROM doc_cache WHERE tipo = ? AND id = ?")) {
            ps.setString(1, tipo);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public static List<Pendiente> pendientes() throws SQLException {
        List<Pendiente> lista = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT id, tipo, operacion, doc_id, json, retry_count FROM doc_outbox "
                + "WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) lista.add(new Pendiente(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getInt(6)));
        }
        return lista;
    }

    /** True if the document was created offline and hasn't reached the server yet. */
    public static boolean creadoSinSincronizar(String tipo, String id) throws SQLException {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT 1 FROM doc_outbox WHERE tipo = ? AND doc_id = ? AND operacion = 'CREAR' "
                + "AND status IN ('PENDING','FAILED')")) {
            ps.setString(1, tipo);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public static <T> T leer(String json, Class<T> clase) {
        try {
            return JSON.readValue(json, clase);
        } catch (Exception e) {
            log.warn("OfflineDocs: documento local ilegible ({})", clase.getSimpleName(), e);
            return null;
        }
    }

    private static String escribir(Object doc) throws SQLException {
        try {
            return JSON.writeValueAsString(doc);
        } catch (Exception e) {
            throw new SQLException("No se pudo guardar el documento en la PC: " + e.getMessage(), e);
        }
    }

    /** Folio shown until the server assigns the real one on sync. */
    public static String folioProvisional(String prefijo, String id) {
        return prefijo + "-PROV-" + id.substring(0, 6).toUpperCase();
    }
}
