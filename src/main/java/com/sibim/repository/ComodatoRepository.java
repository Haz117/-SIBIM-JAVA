package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Comodato;
import com.sibim.session.SessionManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ComodatoRepository {

    /** Comodatos involve an external entity — no internal area restriction applies
     *  to the comodato record itself; however we still scope by producto_id's
     *  area ownership so a user can only create/see comodatos for goods they have
     *  access to, matching PrestamoRepository's convention. */
    private static String scopeCondicion(List<Object> params) {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible == null) return null;
        // Join to products table to check area ownership
        String[] areas = accessible.toArray(new String[0]);
        params.add(areas);
        return "c.producto_id IN (SELECT id FROM products WHERE area = ANY(?))";
    }

    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String[] arr) ps.setArray(i + 1, conn.createArrayOf("text", arr));
            else ps.setObject(i + 1, p);
        }
    }

    public List<Comodato> findAll() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Comodato> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT c.* FROM comodatos c"
            + (scope != null ? " WHERE " + scope : "")
            + " ORDER BY c.created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Comodato> findVigentes() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Comodato> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT c.* FROM comodatos c WHERE c.estado = 'VIGENTE'"
            + (scope != null ? " AND " + scope : "")
            + " ORDER BY c.fecha_fin ASC NULLS LAST";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Comodato> findVencidos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Comodato> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT c.* FROM comodatos c WHERE c.estado = 'VENCIDO'"
            + (scope != null ? " AND " + scope : "")
            + " ORDER BY c.fecha_fin ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Comodato save(Comodato c) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null)
            throw new IllegalStateException("Comodatos no disponibles en modo offline/demo");
        if (c.getId() == null) c.setId(UUID.randomUUID().toString());
        if (c.getNumero() == null) c.setNumero(nextNumero());

        com.sibim.model.Usuario u = SessionManager.getCurrentUser();
        if (u != null) {
            c.setCreadoPorId(u.getId());
            c.setCreadoPorNombre(u.getNombre());
        }

        String sql = """
            INSERT INTO comodatos
              (id, numero, producto_id, producto_nombre, producto_codigo,
               entidad_receptora, contacto_nombre, contacto_cargo, domicilio,
               motivo, condiciones, fecha_inicio, fecha_fin, estado,
               creado_por_id, creado_por_nombre, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'VIGENTE',?,?,NOW(),NOW())
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getNumero());
            ps.setString(3, c.getProductoId());
            ps.setString(4, c.getProductoNombre());
            ps.setString(5, c.getProductoCodigo());
            ps.setString(6, c.getEntidadReceptora());
            ps.setString(7, c.getContactoNombre());
            ps.setString(8, c.getContactoCargo());
            ps.setString(9, c.getDomicilio());
            ps.setString(10, c.getMotivo());
            ps.setString(11, c.getCondiciones());
            ps.setDate(12, Date.valueOf(c.getFechaInicio() != null ? c.getFechaInicio() : LocalDate.now()));
            if (c.getFechaFin() != null) ps.setDate(13, Date.valueOf(c.getFechaFin()));
            else ps.setNull(13, Types.DATE);
            ps.setString(14, c.getCreadoPorId());
            ps.setString(15, c.getCreadoPorNombre());
            ps.executeUpdate();
        }
        c.setEstado(Comodato.ESTADO_VIGENTE);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    public void concluir(String id, LocalDate fechaReal) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return;
        String sql = "UPDATE comodatos SET estado = 'CONCLUIDO', fecha_devolucion_real = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fechaReal != null ? fechaReal : LocalDate.now()));
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public void rescindir(String id) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return;
        String sql = "UPDATE comodatos SET estado = 'RESCINDIDO', updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public int updateVencidos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return 0;
        String sql = """
            UPDATE comodatos SET estado = 'VENCIDO', updated_at = NOW()
            WHERE estado = 'VIGENTE' AND fecha_fin IS NOT NULL AND fecha_fin < CURRENT_DATE
              AND fecha_devolucion_real IS NULL
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    public String nextNumero() throws SQLException {
        int year = LocalDate.now().getYear();
        String sql = "SELECT COALESCE(MAX(CAST(NULLIF(REGEXP_REPLACE(numero, '^CDT-" + year + "-', ''), numero) AS INT)), 0) FROM comodatos WHERE numero LIKE 'CDT-" + year + "-%'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return String.format("CDT-%d-%04d", year, rs.getInt(1) + 1);
        }
    }

    private Comodato mapRow(ResultSet rs) throws SQLException {
        Comodato c = new Comodato();
        c.setId(rs.getString("id"));
        c.setNumero(rs.getString("numero"));
        c.setProductoId(rs.getString("producto_id"));
        c.setProductoNombre(rs.getString("producto_nombre"));
        c.setProductoCodigo(rs.getString("producto_codigo"));
        c.setEntidadReceptora(rs.getString("entidad_receptora"));
        c.setContactoNombre(rs.getString("contacto_nombre"));
        c.setContactoCargo(rs.getString("contacto_cargo"));
        c.setDomicilio(rs.getString("domicilio"));
        c.setMotivo(rs.getString("motivo"));
        c.setCondiciones(rs.getString("condiciones"));
        Date fi = rs.getDate("fecha_inicio");
        if (fi != null) c.setFechaInicio(fi.toLocalDate());
        Date ff = rs.getDate("fecha_fin");
        if (ff != null) c.setFechaFin(ff.toLocalDate());
        Date fdr = rs.getDate("fecha_devolucion_real");
        if (fdr != null) c.setFechaDevolucionReal(fdr.toLocalDate());
        c.setEstado(rs.getString("estado"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) c.setCreatedAt(ts.toLocalDateTime());
        Timestamp upd = rs.getTimestamp("updated_at");
        if (upd != null) c.setUpdatedAt(upd.toLocalDateTime());
        c.setCreadoPorId(rs.getString("creado_por_id"));
        c.setCreadoPorNombre(rs.getString("creado_por_nombre"));
        return c;
    }
}
