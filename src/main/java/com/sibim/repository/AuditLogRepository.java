package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.AuditLog;
import com.sibim.session.SessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
            escribir(entrada(entidad, entidadId, entidadNombre, accion, detalle));
        } catch (Exception e) {
            log.warn("No se pudo registrar auditoría: {}", e.getMessage());
        }
    }

    /** Same as {@link #log}, without making the caller wait for the database:
     *  who and when are taken now, the write happens in the background. For
     *  entries written from the JavaFX thread right before the session ends
     *  (automatic logout) — with the connection just lost, waiting for it there
     *  froze the window until the pool's timeout. */
    public void logEnFondo(String entidad, String entidadId, String entidadNombre, String accion, String detalle) {
        AuditLog a = entrada(entidad, entidadId, entidadNombre, accion, detalle);
        com.sibim.util.AppExecutor.submit(() -> {
            try {
                escribir(a);
            } catch (Exception e) {
                log.warn("No se pudo registrar auditoría: {}", e.getMessage());
            }
        });
    }

    private static AuditLog entrada(String entidad, String entidadId, String entidadNombre, String accion, String detalle) {
        AuditLog a = new AuditLog();
        a.setId(UUID.randomUUID().toString());
        a.setEntidad(entidad);
        a.setEntidadId(entidadId);
        a.setEntidadNombre(entidadNombre);
        a.setAccion(accion);
        a.setDetalle(detalle);
        com.sibim.model.Usuario yo = SessionManager.getCurrentUser();
        if (yo != null) {
            a.setUsuarioId(yo.getId());
            String nombre = yo.getNombre();
            a.setUsuarioNombre(nombre == null || nombre.isBlank() ? "Sistema" : nombre);
        } else {
            a.setUsuarioNombre("Sistema");
        }
        // System events such as failed logins and backups do not always
        // belong to a persisted entity. The schema migration permits null
        // entity IDs for those events instead of rejecting the audit row.
        a.setCreadoEn(LocalDateTime.now());
        return a;
    }

    private void escribir(AuditLog a) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.logAudit(a);
            return;
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.addAuditLog(a);
            return;
        }
        // Connected, the entry is dated by the server: the bitácora of every
        // PC shares one clock, whatever time each PC thinks it is.
        insertar(a, null);
    }

    /** An entry with the date it already has — one made offline keeps the
     *  moment it happened, not the moment it reached the server. */
    public void logOnline(AuditLog a) throws java.sql.SQLException {
        insertar(a, a.getCreadoEn());
    }

    /** @param fecha the entry's date, or null for the server's current time */
    private void insertar(AuditLog a, LocalDateTime fecha) throws SQLException {
        // usuario_id only if that user still exists (the column references
        // users): looked up in the same statement instead of a query before it.
        String sql = """
            INSERT INTO audit_log (id, entidad, entidad_id, entidad_nombre, accion, detalle,
                usuario_id, usuario_nombre, created_at)
            VALUES (?,?,?,?,?,?, (SELECT id FROM users WHERE id = ?), ?, COALESCE(?, clock_timestamp()))
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
            ps.setString(7, a.getUsuarioId());
            ps.setString(8, a.getUsuarioNombre());
            if (fecha != null) ps.setTimestamp(9, Timestamp.valueOf(fecha));
            else ps.setNull(9, Types.TIMESTAMP);
            ps.executeUpdate();
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

    /** What the Auditoría screen filters by; null or blank is no filter.
     *  @param entidadId one bien's (or resguardo's…) whole history («Ver todo lo de SSPCR/19») */
    public record Filtro(String busqueda, String entidad, String accion, String usuario,
                         LocalDate desde, LocalDate hasta, String entidadId) {
        public static Filtro ninguno() { return new Filtro(null, null, null, null, null, null, null); }
        /** The same without the acción: what the acción chips count. */
        public Filtro sinAccion() { return new Filtro(busqueda, entidad, null, usuario, desde, hasta, entidadId); }
    }

    public List<AuditLog> findPaginated(int limit, int offset,
            String busqueda, String entidad, String accion, String usuarioNombre,
            LocalDate desde, LocalDate hasta) throws SQLException {
        return findPaginated(limit, offset, new Filtro(busqueda, entidad, accion, usuarioNombre, desde, hasta, null));
    }

    public List<AuditLog> findPaginated(int limit, int offset, Filtro f) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return applyClientFilters(findAll(500), f).stream().skip(offset).limit(limit).toList();
        List<Object> params = new ArrayList<>();
        String where = buildAuditWhere(f, params);
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
            LocalDate desde, LocalDate hasta) throws SQLException {
        return countFiltrado(new Filtro(busqueda, entidad, accion, usuarioNombre, desde, hasta, null));
    }

    public int countFiltrado(Filtro f) throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return applyClientFilters(findAll(500), f).size();
        List<Object> params = new ArrayList<>();
        String where = buildAuditWhere(f, params);
        String sql = "SELECT COUNT(*) FROM audit_log" + where;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** How many entries of each acción the filter has, ignoring its own acción, the most first:
     *  the chips over the table (19-nov-2026: four cards said «TOTAL 3794», «162», «25», «479»
     *  and none of them filtered). */
    public java.util.LinkedHashMap<String, Integer> contarPorAccion(Filtro f) throws SQLException {
        requireAdmin();
        Filtro sin = f.sinAccion();
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode()) {
            java.util.Map<String, Integer> c = new java.util.HashMap<>();
            for (AuditLog a : applyClientFilters(findAll(500), sin))
                if (a.getAccion() != null) c.merge(a.getAccion(), 1, Integer::sum);
            c.entrySet().stream().sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> out.put(e.getKey(), e.getValue()));
            return out;
        }
        List<Object> params = new ArrayList<>();
        String where = buildAuditWhere(sin, params);
        String sql = "SELECT accion, COUNT(*) FROM audit_log" + (where.isEmpty() ? " WHERE" : where + " AND")
            + " accion IS NOT NULL GROUP BY accion ORDER BY 2 DESC, 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
            }
        }
        return out;
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

    /** Every distinct "entidad" in the log, for the Entidad filter. */
    public List<String> findDistinctEntidades() throws SQLException {
        requireAdmin();
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode())
            return findAll(500).stream().map(AuditLog::getEntidad)
                .filter(e -> e != null && !e.isBlank()).distinct().sorted().toList();
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT entidad FROM audit_log WHERE entidad IS NOT NULL ORDER BY entidad";
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
    private static List<AuditLog> applyClientFilters(List<AuditLog> all, Filtro f) {
        return all.stream()
            .filter(a -> vacio(f.busqueda())
                || containsIgnoreCase(a.getEntidadNombre(), f.busqueda())
                || containsIgnoreCase(a.getDetalle(), f.busqueda())
                || containsIgnoreCase(a.getAccion(), f.busqueda()))
            .filter(a -> vacio(f.entidad()) || f.entidad().equals(a.getEntidad()))
            .filter(a -> vacio(f.accion()) || f.accion().equals(a.getAccion()))
            .filter(a -> vacio(f.usuario()) || containsIgnoreCase(a.getUsuarioNombre(), f.usuario()))
            .filter(a -> f.desde() == null || (a.getCreadoEn() != null && !a.getCreadoEn().toLocalDate().isBefore(f.desde())))
            .filter(a -> f.hasta() == null || (a.getCreadoEn() != null && !a.getCreadoEn().toLocalDate().isAfter(f.hasta())))
            .filter(a -> vacio(f.entidadId()) || f.entidadId().equals(a.getEntidadId()))
            .toList();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static String buildAuditWhere(Filtro f, List<Object> params) {
        List<String> conds = new ArrayList<>();
        if (!vacio(f.busqueda())) {
            String like = Like.contiene(f.busqueda());
            conds.add("(entidad_nombre ILIKE ? OR detalle ILIKE ? OR accion ILIKE ?)");
            params.add(like); params.add(like); params.add(like);
        }
        if (!vacio(f.entidad())) { conds.add("entidad = ?"); params.add(f.entidad()); }
        if (!vacio(f.accion())) { conds.add("accion = ?"); params.add(f.accion()); }
        if (!vacio(f.usuario())) { conds.add("usuario_nombre ILIKE ?"); params.add(Like.contiene(f.usuario())); }
        if (f.desde() != null) { conds.add("created_at >= ?"); params.add(Timestamp.valueOf(f.desde().atStartOfDay())); }
        if (f.hasta() != null) { conds.add("created_at <= ?"); params.add(Timestamp.valueOf(f.hasta().atTime(LocalTime.MAX))); }
        if (!vacio(f.entidadId())) { conds.add("entidad_id = ?"); params.add(f.entidadId()); }
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
