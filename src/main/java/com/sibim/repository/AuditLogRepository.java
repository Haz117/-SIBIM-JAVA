package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.AuditLog;
import com.sibim.session.SessionManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditLogRepository {

    /** Records one audit entry for the CURRENT session's user. Failures here
     *  are logged but never thrown — a broken audit write must not block the
     *  actual operation being audited (e.g. saving a producto). */
    public void log(String entidad, String entidadId, String entidadNombre, String accion, String detalle) {
        try {
            AuditLog a = new AuditLog();
            a.setId(UUID.randomUUID().toString());
            a.setEntidad(entidad);
            a.setEntidadId(entidadId);
            a.setEntidadNombre(entidadNombre);
            a.setAccion(accion);
            a.setDetalle(detalle);
            if (SessionManager.getCurrentUser() != null) {
                a.setUsuarioId(SessionManager.getCurrentUser().getId());
                a.setUsuarioNombre(SessionManager.getCurrentUser().getNombre());
            } else {
                a.setUsuarioNombre("Sistema");
            }
            a.setCreadoEn(java.time.LocalDateTime.now());

            if (DatabaseConfig.isDemoMode()) {
                DemoDataStore.addAuditLog(a);
                return;
            }
            String sql = """
                INSERT INTO audit_log (id, entidad, entidad_id, entidad_nombre, accion, detalle,
                    usuario_id, usuario_nombre, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """;
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, a.getId());
                ps.setString(2, a.getEntidad());
                ps.setString(3, a.getEntidadId());
                ps.setString(4, a.getEntidadNombre());
                ps.setString(5, a.getAccion());
                ps.setString(6, a.getDetalle());
                ps.setString(7, a.getUsuarioId());
                ps.setString(8, a.getUsuarioNombre());
                ps.setTimestamp(9, Timestamp.valueOf(a.getCreadoEn()));
                ps.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("No se pudo registrar auditoria: " + e.getMessage());
        }
    }

    public List<AuditLog> findAll(int limit) throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findAuditLog(limit);
        String sql = "SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ?";
        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private AuditLog mapRow(ResultSet rs) throws SQLException {
        AuditLog a = new AuditLog();
        a.setId(rs.getString("id"));
        a.setEntidad(rs.getString("entidad"));
        a.setEntidadId(rs.getString("entidad_id"));
        a.setEntidadNombre(rs.getString("entidad_nombre"));
        a.setAccion(rs.getString("accion"));
        a.setDetalle(rs.getString("detalle"));
        a.setUsuarioId(rs.getString("usuario_id"));
        a.setUsuarioNombre(rs.getString("usuario_nombre"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) a.setCreadoEn(ts.toLocalDateTime());
        return a;
    }
}
