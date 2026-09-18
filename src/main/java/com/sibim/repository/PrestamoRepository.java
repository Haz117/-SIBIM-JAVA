package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Prestamo;
import com.sibim.session.SessionManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PrestamoRepository {

    private final FolioRepository folioRepo = new FolioRepository();

    /** A préstamo moves a bien FROM one área TO another, unlike Producto's
     *  single-area ownership — restricting visibility to only areaOrigen
     *  would hide it from the destination área's own staff (and vice versa),
     *  breaking the legitimate case of both sides needing to see an active
     *  transfer. Returns null for admin (no restriction), matching
     *  SessionManager.getAccessibleAreas()'s own convention. */
    private static String scopeCondicion(List<Object> params) {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible == null) return null;
        String[] areas = accessible.toArray(new String[0]);
        params.add(areas);
        params.add(areas);
        return "(area_origen = ANY(?) OR area_destino = ANY(?))";
    }

    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params) throws SQLException {
        bindParams(ps, conn, params, 1);
    }

    /** Same as {@link #bindParams(PreparedStatement, Connection, List)} but starting at an
     *  arbitrary placeholder index — for statements that bind explicit columns first and
     *  append the área scope condition's params afterward. */
    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params, int startIndex) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            int idx = startIndex + i;
            if (p instanceof String[] arr) ps.setArray(idx, conn.createArrayOf("text", arr));
            else ps.setObject(idx, p);
        }
    }

    public List<Prestamo> findAll() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Prestamo> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT * FROM prestamos" + (scope != null ? " WHERE " + scope : "")
            + " ORDER BY created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Prestamo> findActivos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Prestamo> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT * FROM prestamos WHERE estado IN ('ACTIVO','VENCIDO')"
            + (scope != null ? " AND " + scope : "") + " ORDER BY fecha_devolucion_prevista ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Prestamo findById(String id) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return null;
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "SELECT * FROM prestamos WHERE id = ?" + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            bindParams(ps, conn, scopeParams, 2);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /** True when {@code productoId} already has an open préstamo (ACTIVO or VENCIDO) —
     *  used to stop the same bien from being lent out to two áreas at once. */
    public boolean existeActivoPorProducto(String productoId) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return false;
        String sql = "SELECT 1 FROM prestamos WHERE producto_id = ? AND estado IN ('ACTIVO','VENCIDO') LIMIT 1";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Prestamo save(Prestamo prestamo) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null)
            throw new IllegalStateException("Préstamos no disponibles en modo offline/demo");
        if (prestamo.getId() == null) prestamo.setId(UUID.randomUUID().toString());
        if (prestamo.getNumero() == null) prestamo.setNumero(nextNumero());

        com.sibim.model.Usuario u = SessionManager.getCurrentUser();
        if (u != null) {
            prestamo.setCreadoPorId(u.getId());
            prestamo.setCreadoPorNombre(u.getNombre());
        }

        String sql = """
            INSERT INTO prestamos
              (id, numero, producto_id, producto_nombre, producto_codigo,
               area_origen, area_destino, responsable_nombre, responsable_cargo,
               motivo, fecha_prestamo, fecha_devolucion_prevista, estado,
               creado_por_id, creado_por_nombre, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVO',?,?,NOW())
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prestamo.getId());
            ps.setString(2, prestamo.getNumero());
            ps.setString(3, prestamo.getProductoId());
            ps.setString(4, prestamo.getProductoNombre());
            ps.setString(5, prestamo.getProductoCodigo());
            ps.setString(6, prestamo.getAreaOrigen());
            ps.setString(7, prestamo.getAreaDestino());
            ps.setString(8, prestamo.getResponsableNombre());
            ps.setString(9, prestamo.getResponsableCargo());
            ps.setString(10, prestamo.getMotivo());
            ps.setDate(11, Date.valueOf(prestamo.getFechaPrestamo() != null
                ? prestamo.getFechaPrestamo() : LocalDate.now()));
            ps.setDate(12, Date.valueOf(prestamo.getFechaDevolucionPrevista()));
            ps.setString(13, prestamo.getCreadoPorId());
            ps.setString(14, prestamo.getCreadoPorNombre());
            ps.executeUpdate();
        }
        prestamo.setCreadoEn(LocalDateTime.now());
        return prestamo;
    }

    public void devolver(String id, LocalDate fechaDevolucionReal) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return;
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "UPDATE prestamos SET estado = 'DEVUELTO', fecha_devolucion_real = ? "
            + "WHERE id = ? AND estado IN ('ACTIVO','VENCIDO')"
            + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fechaDevolucionReal));
            ps.setString(2, id);
            bindParams(ps, conn, scopeParams, 3);
            if (ps.executeUpdate() == 0)
                throw new SQLException("El préstamo no existe, ya fue devuelto, o no tienes acceso a su área (id=" + id + ")");
        }
    }

    public int updateVencidos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return 0;
        String sql = """
            UPDATE prestamos SET estado = 'VENCIDO'
            WHERE estado = 'ACTIVO' AND fecha_devolucion_prevista < CURRENT_DATE
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    public List<Prestamo> findVencidos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Prestamo> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT * FROM prestamos WHERE estado = 'VENCIDO'"
            + (scope != null ? " AND " + scope : "") + " ORDER BY fecha_devolucion_prevista ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Prestamo> findProximosAVencer(int days) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Prestamo> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        params.add(days);
        String scope = scopeCondicion(params);
        String sql = "SELECT * FROM prestamos"
            + " WHERE estado = 'ACTIVO' AND fecha_devolucion_prevista BETWEEN CURRENT_DATE AND CURRENT_DATE + ?"
            + (scope != null ? " AND " + scope : "")
            + " ORDER BY fecha_devolucion_prevista ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countVencidos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return 0;
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT COUNT(*) FROM prestamos WHERE estado = 'VENCIDO'"
            + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public List<Prestamo> findByProductoId(String productoId) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Prestamo> list = new ArrayList<>();
        String sql = "SELECT * FROM prestamos WHERE producto_id = ? ORDER BY created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public String nextNumero() throws SQLException {
        return folioRepo.next("PRS");
    }

    private Prestamo mapRow(ResultSet rs) throws SQLException {
        Prestamo p = new Prestamo();
        p.setId(rs.getString("id"));
        p.setNumero(rs.getString("numero"));
        p.setProductoId(rs.getString("producto_id"));
        p.setProductoNombre(rs.getString("producto_nombre"));
        p.setProductoCodigo(rs.getString("producto_codigo"));
        p.setAreaOrigen(rs.getString("area_origen"));
        p.setAreaDestino(rs.getString("area_destino"));
        p.setResponsableNombre(rs.getString("responsable_nombre"));
        p.setResponsableCargo(rs.getString("responsable_cargo"));
        p.setMotivo(rs.getString("motivo"));
        Date fp = rs.getDate("fecha_prestamo");
        if (fp != null) p.setFechaPrestamo(fp.toLocalDate());
        Date fdp = rs.getDate("fecha_devolucion_prevista");
        if (fdp != null) p.setFechaDevolucionPrevista(fdp.toLocalDate());
        Date fdr = rs.getDate("fecha_devolucion_real");
        if (fdr != null) p.setFechaDevolucionReal(fdr.toLocalDate());
        p.setEstado(rs.getString("estado"));
        p.setCreadoPorId(rs.getString("creado_por_id"));
        p.setCreadoPorNombre(rs.getString("creado_por_nombre"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) p.setCreadoEn(ts.toLocalDateTime());
        return p;
    }
}
