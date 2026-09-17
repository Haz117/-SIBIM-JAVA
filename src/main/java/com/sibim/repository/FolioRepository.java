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

        if (DatabaseConfig.isDemoMode() || DatabaseConfig.getLocalDataStore() != null) {
            String key = prefijo + "-" + year;
            AtomicInteger counter = DEMO_COUNTERS.computeIfAbsent(key, k -> new AtomicInteger(0));
            int numero = counter.incrementAndGet();
            return prefijo + "-" + year + "-" + String.format("%06d", numero);
        }

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, prefijo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int numero = rs.getInt(1);
                return prefijo + "-" + year + "-" + String.format("%06d", numero);
            }
        }
    }
}
