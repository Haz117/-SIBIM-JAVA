package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConteoRepository {

    /** Persists a completed conteo session and all its reviewed items in one
     *  transaction. Doesn't touch stock/movements — the caller
     *  (ConteoFisicoDialog) registers the reconciling Ajuste movements
     *  separately through MovimientoService, same atomic/locked path as any
     *  other stock change. */
    public ConteoFisico guardar(ConteoFisico c) throws SQLException {
        if (c.getId() == null) c.setId(UUID.randomUUID().toString());
        if (c.getCreadoEn() == null) c.setCreadoEn(LocalDateTime.now());
        for (ConteoItem it : c.getItems()) {
            if (it.getId() == null) it.setId(UUID.randomUUID().toString());
            it.setConteoId(c.getId());
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.addConteo(c);
            return c;
        }
        String insertConteo = """
            INSERT INTO conteos_fisicos (id, usuario_id, usuario_nombre, total_contados, total_discrepancias, created_at)
            VALUES (?,?,?,?,?,?)
            """;
        String insertItem = """
            INSERT INTO conteo_items (id, conteo_id, producto_id, producto_nombre, area, stock_sistema, stock_contado, ajustado)
            VALUES (?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(insertConteo)) {
                    ps.setString(1, c.getId());
                    ps.setString(2, c.getUsuarioId());
                    ps.setString(3, c.getUsuarioNombre());
                    ps.setInt(4, c.getTotalContados());
                    ps.setInt(5, c.getTotalDiscrepancias());
                    ps.setTimestamp(6, Timestamp.valueOf(c.getCreadoEn()));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
                    for (ConteoItem it : c.getItems()) {
                        ps.setString(1, it.getId());
                        ps.setString(2, it.getConteoId());
                        ps.setString(3, it.getProductoId());
                        ps.setString(4, it.getProductoNombre());
                        ps.setString(5, it.getArea());
                        ps.setInt(6, it.getStockSistema());
                        ps.setInt(7, it.getStockContado());
                        ps.setBoolean(8, it.isAjustado());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
        return c;
    }

    public List<ConteoFisico> findAll(int limit) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findConteos(limit);
        String sql = "SELECT * FROM conteos_fisicos ORDER BY created_at DESC LIMIT ?";
        List<ConteoFisico> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapConteo(rs));
            }
        }
        return list;
    }

    public List<ConteoItem> findItems(String conteoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findConteoItems(conteoId);
        String sql = "SELECT * FROM conteo_items WHERE conteo_id = ? ORDER BY producto_nombre";
        List<ConteoItem> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conteoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapItem(rs));
            }
        }
        return list;
    }

    private ConteoFisico mapConteo(ResultSet rs) throws SQLException {
        ConteoFisico c = new ConteoFisico();
        c.setId(rs.getString("id"));
        c.setUsuarioId(rs.getString("usuario_id"));
        c.setUsuarioNombre(rs.getString("usuario_nombre"));
        c.setTotalContados(rs.getInt("total_contados"));
        c.setTotalDiscrepancias(rs.getInt("total_discrepancias"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreadoEn(ts.toLocalDateTime());
        return c;
    }

    private ConteoItem mapItem(ResultSet rs) throws SQLException {
        ConteoItem it = new ConteoItem();
        it.setId(rs.getString("id"));
        it.setConteoId(rs.getString("conteo_id"));
        it.setProductoId(rs.getString("producto_id"));
        it.setProductoNombre(rs.getString("producto_nombre"));
        it.setArea(rs.getString("area"));
        it.setStockSistema(rs.getInt("stock_sistema"));
        it.setStockContado(rs.getInt("stock_contado"));
        it.setAjustado(rs.getBoolean("ajustado"));
        return it;
    }
}
