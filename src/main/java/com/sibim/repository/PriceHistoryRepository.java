package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PriceHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryRepository.class);

    public record PriceHistoryEntry(
        String campo,
        BigDecimal valorAnterior,
        BigDecimal valorNuevo,
        String usuarioNombre,
        LocalDateTime creadoEn
    ) {}

    public void save(String productoId, String campo, BigDecimal anterior, BigDecimal nuevo,
                     String usuarioId, String usuarioNombre) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return;
        String sql = """
            INSERT INTO price_history (producto_id, campo, valor_anterior, valor_nuevo, usuario_id, usuario_nombre)
            VALUES (?::uuid, ?, ?, ?, ?::uuid, ?)
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            ps.setString(2, campo);
            if (anterior != null) ps.setBigDecimal(3, anterior); else ps.setNull(3, Types.NUMERIC);
            ps.setBigDecimal(4, nuevo);
            if (usuarioId != null) ps.setString(5, usuarioId); else ps.setNull(5, Types.OTHER);
            ps.setString(6, usuarioNombre);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("No se pudo guardar historial de precio para producto {}: {}", productoId, e.getMessage());
        }
    }

    public List<PriceHistoryEntry> findByProducto(String productoId) {
        if (DatabaseConfig.isDemoMode() || DatabaseConfig.isOfflineMode()) return List.of();
        String sql = """
            SELECT campo, valor_anterior, valor_nuevo, usuario_nombre, created_at
            FROM price_history
            WHERE producto_id = ?::uuid
            ORDER BY created_at DESC
            LIMIT 20
            """;
        List<PriceHistoryEntry> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal ant    = rs.getBigDecimal("valor_anterior");
                    BigDecimal nuevo  = rs.getBigDecimal("valor_nuevo");
                    String usuario    = rs.getString("usuario_nombre");
                    Timestamp ts      = rs.getTimestamp("created_at");
                    LocalDateTime dt  = ts != null ? ts.toLocalDateTime() : null;
                    list.add(new PriceHistoryEntry(rs.getString("campo"), ant, nuevo, usuario, dt));
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar historial de precios para producto {}: {}", productoId, e.getMessage());
        }
        return list;
    }
}
