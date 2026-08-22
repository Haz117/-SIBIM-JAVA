package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.AuditLog;
import com.sibim.session.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);

    // Mirrors CategoriaRepository/UsuarioRepository's pattern: the audit
    // trail spans every area, not just the caller's own, so unlike
    // ProductoRepository/MovimientoRepository there's no per-area filter
    // that would naturally scope it down — only an explicit admin-only
    // check does. Until this was added, findAll() had no protection of its
    // own; ConfiguracionController only reached it from a button hidden for
    // non-admins, which is UI-only gating, not real access control.
    private void requireAdmin() {
        if (!SessionManager.isAdmin()) {
            throw new SecurityException("Solo el administrador puede consultar la auditoría");
        }
    }

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

            if (DatabaseConfig.isOfflineMode()) {
                OfflineStore.logAudit(a);
                return;
            }
            if (DatabaseConfig.isDemoMode()) {
                DemoDataStore.addAuditLog(a);
                return;
            }
            logOnline(a);
        } catch (Exception e) {
            log.warn("No se pudo registrar auditoría: {}", e.getMessage());
        }
    }

    public void logOnline(AuditLog a) throws java.sql.SQLException {
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
    }

    public List<AuditLog> findAll(int limit) throws SQLException {
        requireAdmin();
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
