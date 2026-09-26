package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.session.SessionManager;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ResguardoRepository {

    private final FolioRepository folioRepo = new FolioRepository();

    /** Resguardos belong to one área (resguardanteArea), unlike Préstamos
     *  which cross two — mirrors ProductoRepository's single-column area
     *  scoping. Returns null for admin (no restriction). */
    private static String scopeCondicion(List<Object> params) {
        java.util.Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible == null) return null;
        params.add(accessible.toArray(new String[0]));
        return "r.resguardante_area = ANY(?)";
    }

    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params) throws SQLException {
        bindParams(ps, conn, params, 1);
    }

    private static void bindParams(PreparedStatement ps, Connection conn, List<Object> params, int startIndex) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            int idx = startIndex + i;
            if (p instanceof String[] arr) ps.setArray(idx, conn.createArrayOf("text", arr));
            else ps.setObject(idx, p);
        }
    }

    public List<Resguardo> findAll() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Resguardo> list = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT r.*, "
            + "(SELECT COUNT(*) FROM resguardo_items i WHERE i.resguardo_id = r.id) AS total_items "
            + "FROM resguardos r"
            + (scope != null ? " WHERE " + scope : "")
            + " ORDER BY r.created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Resguardo findById(String id) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return null;
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "SELECT * FROM resguardos r WHERE r.id = ?" + (scope != null ? " AND " + scope : "");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            bindParams(ps, conn, scopeParams, 2);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Resguardo r = mapRow(rs);
                    r.setItems(findItems(id, conn));
                    return r;
                }
            }
        }
        return null;
    }

    public Resguardo save(Resguardo resguardo) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null)
            throw new IllegalStateException("Resguardos no disponibles en modo offline/demo");
        if (resguardo.getId() == null) resguardo.setId(UUID.randomUUID().toString());
        if (resguardo.getNumero() == null) resguardo.setNumero(nextNumero());

        com.sibim.model.Usuario u = SessionManager.getCurrentUser();
        if (u != null) {
            resguardo.setCreadoPorId(u.getId());
            resguardo.setCreadoPorNombre(u.getNombre());
        }

        String sql = """
            INSERT INTO resguardos
              (id, numero, resguardante_nombre, resguardante_cargo, resguardante_area,
               creado_por_id, creado_por_nombre, observaciones, estado, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,NOW())
            """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, resguardo.getId());
                ps.setString(2, resguardo.getNumero());
                ps.setString(3, resguardo.getResguardanteNombre());
                ps.setString(4, resguardo.getResguardanteCargo());
                ps.setString(5, resguardo.getResguardanteArea());
                ps.setString(6, resguardo.getCreadoPorId());
                ps.setString(7, resguardo.getCreadoPorNombre());
                ps.setString(8, resguardo.getObservaciones());
                ps.setString(9, Resguardo.ESTADO_ACTIVO);
                ps.executeUpdate();
            }
            saveItems(resguardo.getId(), resguardo.getItems(), conn);
            asignarResguardante(conn, resguardo);
            conn.commit();
        }
        resguardo.setCreadoEn(LocalDateTime.now());
        return resguardo;
    }

    /** The signed resguardo is the source of truth for who holds each bien:
     *  products.resguardante follows it instead of being typed separately,
     *  so the column in Bienes and the documents can't disagree. */
    private static void asignarResguardante(Connection conn, Resguardo resguardo) throws SQLException {
        List<String> ids = resguardo.getItems() == null ? List.of() : resguardo.getItems().stream()
            .map(ResguardoItem::getProductoId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE products SET resguardante = ?, updated_at = NOW() WHERE id = ANY(?)")) {
            ps.setString(1, resguardo.getResguardanteNombre());
            ps.setArray(2, conn.createArrayOf("text", ids.toArray(new String[0])));
            ps.executeUpdate();
        }
    }

    public void cancelar(String id) throws SQLException {
        if (!SessionManager.isAdmin())
            throw new SecurityException("Solo el administrador puede cancelar resguardos");
        if (DatabaseConfig.getLocalDataStore() != null) return;
        // The bienes of a cancelled resguardo are left without resguardante —
        // unless another active resguardo still covers them.
        String liberar = """
            UPDATE products p SET resguardante = NULL, updated_at = NOW()
            WHERE p.id IN (SELECT producto_id FROM resguardo_items WHERE resguardo_id = ?)
              AND p.resguardante = (SELECT resguardante_nombre FROM resguardos WHERE id = ?)
              AND NOT EXISTS (SELECT 1 FROM resguardo_items i JOIN resguardos r ON r.id = i.resguardo_id
                              WHERE i.producto_id = p.id AND r.estado = 'ACTIVO' AND r.id <> ?)
            """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(liberar)) {
                    ps.setString(1, id); ps.setString(2, id); ps.setString(3, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE resguardos SET estado = 'CANCELADO' WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /** The active resguardo that covers {@code productoId}, if any. */
    public java.util.Optional<Resguardo> findActivoByProductoId(String productoId) throws SQLException {
        return findByProductoId(productoId).stream()
            .filter(r -> Resguardo.ESTADO_ACTIVO.equals(r.getEstado()))
            .findFirst();
    }

    public List<Resguardo> findByProductoId(String productoId) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return List.of();
        List<Resguardo> list = new ArrayList<>();
        List<Object> scopeParams = new ArrayList<>();
        String scope = scopeCondicion(scopeParams);
        String sql = "SELECT DISTINCT r.* FROM resguardos r"
            + " JOIN resguardo_items i ON i.resguardo_id = r.id"
            + " WHERE i.producto_id = ?"
            + (scope != null ? " AND " + scope : "")
            + " ORDER BY r.created_at DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            bindParams(ps, conn, scopeParams, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public String nextNumero() throws SQLException {
        return folioRepo.next("RSG");
    }

    private List<ResguardoItem> findItems(String resguardoId, Connection conn) throws SQLException {
        List<ResguardoItem> items = new ArrayList<>();
        String sql = "SELECT * FROM resguardo_items WHERE resguardo_id = ? ORDER BY id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, resguardoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) items.add(mapItem(rs));
            }
        }
        return items;
    }

    private void saveItems(String resguardoId, List<ResguardoItem> items, Connection conn) throws SQLException {
        String sql = """
            INSERT INTO resguardo_items
              (id, resguardo_id, producto_id, producto_nombre, producto_codigo,
               area, cantidad, descripcion, numero_serie, valor_unitario)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ResguardoItem item : items) {
                if (item.getId() == null) item.setId(UUID.randomUUID().toString());
                item.setResguardoId(resguardoId);
                ps.setString(1, item.getId());
                ps.setString(2, resguardoId);
                ps.setString(3, item.getProductoId());
                ps.setString(4, item.getProductoNombre());
                ps.setString(5, item.getProductoCodigo());
                ps.setString(6, item.getArea());
                ps.setInt(7, item.getCantidad());
                ps.setString(8, item.getDescripcion());
                ps.setString(9, item.getNumeroSerie());
                if (item.getValorUnitario() != null)
                    ps.setBigDecimal(10, item.getValorUnitario());
                else
                    ps.setNull(10, Types.NUMERIC);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private Resguardo mapRow(ResultSet rs) throws SQLException {
        Resguardo r = new Resguardo();
        r.setId(rs.getString("id"));
        r.setNumero(rs.getString("numero"));
        r.setResguardanteNombre(rs.getString("resguardante_nombre"));
        r.setResguardanteCargo(rs.getString("resguardante_cargo"));
        r.setResguardanteArea(rs.getString("resguardante_area"));
        r.setCreadoPorId(rs.getString("creado_por_id"));
        r.setCreadoPorNombre(rs.getString("creado_por_nombre"));
        r.setObservaciones(rs.getString("observaciones"));
        r.setEstado(rs.getString("estado"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) r.setCreadoEn(ts.toLocalDateTime());
        return r;
    }

    public int countActivos() throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) return 0;
        List<Object> params = new ArrayList<>();
        String scope = scopeCondicion(params);
        String sql = "SELECT COUNT(*) FROM resguardos WHERE estado = 'ACTIVO'"
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

    private ResguardoItem mapItem(ResultSet rs) throws SQLException {
        ResguardoItem i = new ResguardoItem();
        i.setId(rs.getString("id"));
        i.setResguardoId(rs.getString("resguardo_id"));
        i.setProductoId(rs.getString("producto_id"));
        i.setProductoNombre(rs.getString("producto_nombre"));
        i.setProductoCodigo(rs.getString("producto_codigo"));
        i.setArea(rs.getString("area"));
        i.setCantidad(rs.getInt("cantidad"));
        i.setDescripcion(rs.getString("descripcion"));
        i.setNumeroSerie(rs.getString("numero_serie"));
        BigDecimal val = rs.getBigDecimal("valor_unitario");
        if (val != null) i.setValorUnitario(val);
        return i;
    }
}
