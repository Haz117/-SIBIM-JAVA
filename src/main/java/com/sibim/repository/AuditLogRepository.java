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
                String nombre = SessionManager.getCurrentUser().getNombre();
                a.setUsuarioNombre(nombre == null || nombre.isBlank() ? "Sistema" : nombre);
            } else {
                a.setUsuarioNombre("Sistema");
            }
            // System events such as failed logins and backups do not always
            // belong to a persisted entity. The schema migration permits null
            // entity IDs for those events instead of rejecting the audit row.
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
            if (a.getEntidadId() == null || a.getEntidadId().isBlank()) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, a.getEntidadId());
            ps.setString(4, a.getEntidadNombre());
            ps.setString(5, a.getAccion());
            ps.setString(6, a.getDetalle());
            String usuarioId = a.getUsuarioId();
            if (usuarioId != null && !usuarioExiste(conn, usuarioId)) usuarioId = null;
            ps.setString(7, usuarioId);
            ps.setString(8, a.getUsuarioNombre());
            ps.setTimestamp(9, Timestamp.valueOf(a.getCreadoEn()));
            ps.executeUpdate();
        }
    }

    private static boolean usuarioExiste(Connection conn, String usuarioId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            ps.setString(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<AuditLog> findAll(int limit) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findAuditLog(limit);
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

    /** Full audit trail for one entity (e.g. entidad="producto") — used by the
     *  bien's "cadena de custodia" timeline, admin-only like the rest of this class. */
    public List<AuditLog> findByEntidadId(String entidad, String entidadId) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return findAll(500).stream()
                .filter(a -> entidad.equals(a.getEntidad()) && entidadId.equals(a.getEntidadId()))
                .toList();
        String sql = "SELECT * FROM audit_log WHERE entidad = ? AND entidad_id = ? ORDER BY created_at DESC";
        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entidad);
            ps.setString(2, entidadId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<AuditLog> findPaginated(int limit, int offset,
            String busqueda, String entidad, String accion, String usuarioNombre,
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return applyClientFilters(findAll(500), busqueda, entidad, accion, usuarioNombre, desde, hasta)
                .stream().skip(offset).limit(limit).toList();
        List<Object> params = new ArrayList<>();
        String where = buildAuditWhere(busqueda, entidad, accion, usuarioNombre, desde, hasta, params);
        String sql = "SELECT * FROM audit_log" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        List<AuditLog> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countFiltrado(String busqueda, String entidad, String accion, String usuarioNombre,
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return applyClientFilters(findAll(500), busqueda, entidad, accion, usuarioNombre, desde, hasta).size();
        List<Object> params = new ArrayList<>();
        String where = buildAuditWhere(busqueda, entidad, accion, usuarioNombre, desde, hasta, params);
        String sql = "SELECT COUNT(*) FROM audit_log" + where;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Every distinct "accion" value ever logged — feeds the Acción filter
     *  dropdown so it only ever offers choices that actually exist, instead
     *  of a hardcoded list that drifts from whatever callers pass to log(). */
    public List<String> findDistinctAcciones() throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return findAll(500).stream().map(AuditLog::getAccion)
                .filter(a -> a != null && !a.isBlank()).distinct().sorted().toList();
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT accion FROM audit_log WHERE accion IS NOT NULL ORDER BY accion";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    /** Every distinct "usuario_nombre" that has ever logged an action —
     *  feeds the Usuario filter as a dropdown (a free-text field could
     *  never match on a typo, and users who no longer exist would be
     *  impossible to filter by by name if this instead read the live
     *  users table). */
    public List<String> findDistinctUsuarios() throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return findAll(500).stream().map(AuditLog::getUsuarioNombre)
                .filter(u -> u != null && !u.isBlank()).distinct().sorted().toList();
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT usuario_nombre FROM audit_log WHERE usuario_nombre IS NOT NULL ORDER BY usuario_nombre";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    /** Applies the same filters as {@link #buildAuditWhere} client-side —
     *  used for demo/offline mode, which has no SQL WHERE clause to build
     *  since the data isn't coming from a real query. Before this, demo
     *  mode's findPaginated/countFiltrado ignored every filter argument
     *  entirely and always returned the full unfiltered list, silently
     *  making every filter on the Auditoría screen a no-op whenever the
     *  app runs in demo mode. */
    private static List<AuditLog> applyClientFilters(List<AuditLog> all, String busqueda, String entidad,
            String accion, String usuarioNombre, java.time.LocalDate desde, java.time.LocalDate hasta) {
        return all.stream()
            .filter(a -> busqueda == null || busqueda.isBlank()
                || containsIgnoreCase(a.getEntidadNombre(), busqueda)
                || containsIgnoreCase(a.getDetalle(), busqueda)
                || containsIgnoreCase(a.getAccion(), busqueda))
            .filter(a -> entidad == null || entidad.isBlank() || entidad.equals(a.getEntidad()))
            .filter(a -> accion == null || accion.isBlank() || accion.equals(a.getAccion()))
            .filter(a -> usuarioNombre == null || usuarioNombre.isBlank()
                || containsIgnoreCase(a.getUsuarioNombre(), usuarioNombre))
            .filter(a -> desde == null || (a.getCreadoEn() != null && !a.getCreadoEn().toLocalDate().isBefore(desde)))
            .filter(a -> hasta == null || (a.getCreadoEn() != null && !a.getCreadoEn().toLocalDate().isAfter(hasta)))
            .toList();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    public record AuditStats(int total, int logins, int loginsFallidos, int eliminaciones) {}

    /** Summary counts for the stat cards, honoring the same filters as the
     *  table below them — Auditoría was the only list screen with no summary
     *  section at all. */
    public AuditStats getStats(String busqueda, String entidad, String accion, String usuarioNombre,
            java.time.LocalDate desde, java.time.LocalDate hasta) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode()) {
            List<AuditLog> filtered = applyClientFilters(findAll(500), busqueda, entidad, accion, usuarioNombre, desde, hasta);
            int logins = (int) filtered.stream().filter(a -> "login".equals(a.getAccion())).count();
            int fallidos = (int) filtered.stream().filter(a -> "login_fallido".equals(a.getAccion())).count();
            int elim = (int) filtered.stream()
                .filter(a -> "eliminar".equals(a.getAccion()) || "baja".equals(a.getAccion())).count();
            return new AuditStats(filtered.size(), logins, fallidos, elim);
        }
        List<Object> params = new ArrayList<>();
        String where = buildAuditWhere(busqueda, entidad, accion, usuarioNombre, desde, hasta, params);
        String sql = "SELECT COUNT(*) AS total, "
            + "COUNT(*) FILTER (WHERE accion = 'login') AS logins, "
            + "COUNT(*) FILTER (WHERE accion = 'login_fallido') AS logins_fallidos, "
            + "COUNT(*) FILTER (WHERE accion IN ('eliminar','baja')) AS eliminaciones "
            + "FROM audit_log" + where;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next())
                    return new AuditStats(rs.getInt("total"), rs.getInt("logins"),
                        rs.getInt("logins_fallidos"), rs.getInt("eliminaciones"));
                return new AuditStats(0, 0, 0, 0);
            }
        }
    }

    private static String buildAuditWhere(String busqueda, String entidad, String accion, String usuarioNombre,
            java.time.LocalDate desde, java.time.LocalDate hasta, List<Object> params) {
        List<String> conds = new ArrayList<>();
        if (busqueda != null && !busqueda.isBlank()) {
            String like = "%" + busqueda + "%";
            conds.add("(entidad_nombre ILIKE ? OR detalle ILIKE ? OR accion ILIKE ?)");
            params.add(like); params.add(like); params.add(like);
        }
        if (entidad != null && !entidad.isBlank()) { conds.add("entidad = ?"); params.add(entidad); }
        if (accion != null && !accion.isBlank()) { conds.add("accion = ?"); params.add(accion); }
        if (usuarioNombre != null && !usuarioNombre.isBlank()) {
            conds.add("usuario_nombre ILIKE ?"); params.add("%" + usuarioNombre + "%");
        }
        if (desde != null) { conds.add("created_at >= ?"); params.add(java.sql.Timestamp.valueOf(desde.atStartOfDay())); }
        if (hasta != null) { conds.add("created_at <= ?"); params.add(java.sql.Timestamp.valueOf(hasta.atTime(java.time.LocalTime.MAX))); }
        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
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
