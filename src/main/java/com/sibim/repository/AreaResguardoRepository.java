package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class AreaResguardoRepository {

    public record AreaResguardo(String id, String area, String pdfUrl, String descripcion, LocalDate fecha, LocalDateTime creadoEn) {}

    /** Returns the most recent resguardo for each area. */
    public List<AreaResguardo> findByArea(String area) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null || DatabaseConfig.isDemoMode())
            return new ArrayList<>();
        List<AreaResguardo> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT * FROM area_resguardos WHERE area = ? ORDER BY created_at DESC")) {
            ps.setString(1, area);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        }
        return result;
    }

    public void save(String area, String pdfUrl, String descripcion, LocalDate fecha) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null || DatabaseConfig.isDemoMode()) return;
        String sql = "INSERT INTO area_resguardos (id, area, pdf_url, descripcion, fecha) VALUES (?,?,?,?,?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, area);
            ps.setString(3, pdfUrl);
            ps.setString(4, descripcion);
            ps.setObject(5, fecha);
            ps.executeUpdate();
        }
    }

    public void delete(String id) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null || DatabaseConfig.isDemoMode()) return;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM area_resguardos WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private AreaResguardo mapRow(ResultSet rs) throws SQLException {
        LocalDate fecha = rs.getDate("fecha") != null ? rs.getDate("fecha").toLocalDate() : null;
        Timestamp ca = rs.getTimestamp("created_at");
        LocalDateTime creadoEn = ca != null ? ca.toLocalDateTime() : null;
        return new AreaResguardo(rs.getString("id"), rs.getString("area"),
            rs.getString("pdf_url"), rs.getString("descripcion"), fecha, creadoEn);
    }
}
