package com.sibim.repository;

import com.sibim.db.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FolioRepository {

    private static final ConcurrentHashMap<String, AtomicInteger> DEMO_COUNTERS = new ConcurrentHashMap<>();

    private static final String UPSERT_SQL =
        "INSERT INTO folios (prefijo, anio, ultimo) " +
        "VALUES (?, EXTRACT(YEAR FROM CURRENT_DATE)::SMALLINT, 1) " +
        "ON CONFLICT (prefijo, anio) DO UPDATE " +
        "SET ultimo = folios.ultimo + 1 " +
        "RETURNING ultimo";

    public String next(String prefijo) throws SQLException {
        int year = LocalDate.now().getYear();

        // The sync pass runs while the app is still "offline": a document made
        // offline must get its real folio from the server then, not from this
        // in-memory counter (which restarts at 1 and collided with real folios).
        boolean local = DatabaseConfig.isDemoMode()
            || (DatabaseConfig.getLocalDataStore() != null && !DatabaseConfig.sincronizando());
        if (local) {
            String key = prefijo + "-" + year;
            AtomicInteger counter = DEMO_COUNTERS.computeIfAbsent(key, k -> new AtomicInteger(0));
            int numero = counter.incrementAndGet();
            return prefijo + "-" + year + "-" + String.format("%06d", numero);
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            return next(conn, prefijo);
        }
    }

    /** The next folio, taken on the caller's connection — inside its
     *  transaction, so a write that fails gives the number back. */
    public String next(Connection conn, String prefijo) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, prefijo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return prefijo + "-" + LocalDate.now().getYear() + "-" + String.format("%06d", rs.getInt(1));
            }
        }
    }

    @FunctionalInterface
    public interface Escritura { void con(Connection conn, String folio) throws SQLException; }

    /** Saves a document and takes its folio in ONE transaction. Taken before
     *  it, a save that failed left a hole in the consecutive numbering that
     *  an audit then asks about. A folio the document already has is kept; a
     *  provisional one (made offline) is replaced.
     *  @return the folio the document was saved with */
    public String guardarConFolio(String prefijo, String folioActual, Escritura escritura) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String folio = folioActual == null || folioActual.contains("-PROV-") ? next(conn, prefijo) : folioActual;
                escritura.con(conn, folio);
                conn.commit();
                return folio;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
