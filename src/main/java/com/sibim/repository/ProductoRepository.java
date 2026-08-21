package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.session.SessionManager;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class ProductoRepository {

    private static final String BASE_SELECT = """
        SELECT p.*, c.nombre AS categoria_nombre, c.color AS categoria_color
        FROM products p
        LEFT JOIN categories c ON c.id = p.categoria_id
        """;

    /** Active inventory only (excludes bienes dados de baja) — this is what
     *  every screen/report should use by default. */
    public List<Producto> findAll() throws SQLException {
        return findAll(false);
    }

    /** @param incluirBaja true to also include bienes formally decommissioned
     *  (soft-deleted) — used only by the "dados de baja" history view. */
    public List<Producto> findAll(boolean incluirBaja) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            List<Producto> all = OfflineStore.findAllProductos(SessionManager.getAccessibleAreas());
            return incluirBaja ? all : all.stream().filter(p -> !p.isDadoDeBaja()).toList();
        }
        if (DatabaseConfig.isDemoMode()) {
            List<Producto> all = DemoDataStore.findAllProductos(SessionManager.getAccessibleAreas());
            return incluirBaja ? all : all.stream().filter(p -> !p.isDadoDeBaja()).toList();
        }
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null) {
            conditions.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        if (!incluirBaja) conditions.add("p.fecha_baja IS NULL");
        if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
        sb.append(" ORDER BY p.nombre");
        return queryDynamic(sb.toString(), params);
    }

    public List<Producto> findByDateRange(LocalDate desde, LocalDate hasta) throws SQLException {
        if (DatabaseConfig.isOfflineMode() || DatabaseConfig.isDemoMode()) {
            List<Producto> all = DatabaseConfig.isOfflineMode()
                ? OfflineStore.findAllProductos(SessionManager.getAccessibleAreas())
                : DemoDataStore.findAllProductos(SessionManager.getAccessibleAreas());
            return all.stream()
                .filter(p -> !p.isDadoDeBaja())
                .filter(p -> {
                    if (p.getCreadoEn() == null) return true;
                    LocalDate fecha = p.getCreadoEn().toLocalDate();
                    return (desde == null || !fecha.isBefore(desde))
                        && (hasta == null || !fecha.isAfter(hasta));
                })
                .toList();
        }
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        conditions.add("p.fecha_baja IS NULL");
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            conditions.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        if (desde != null) { conditions.add("p.created_at >= ?"); params.add(Timestamp.valueOf(desde.atStartOfDay())); }
        if (hasta != null) { conditions.add("p.created_at <= ?"); params.add(Timestamp.valueOf(hasta.atTime(23, 59, 59))); }
        if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
        sb.append(" ORDER BY p.nombre");
        return queryDynamic(sb.toString(), params);
    }

    public List<Producto> findByArea(String area) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findAllProductos(Set.of(area));
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findAllProductos(Set.of(area));
        String sql = BASE_SELECT + " WHERE p.area = ? ORDER BY p.nombre";
        return query(sql, area);
    }

    public Optional<Producto> findById(String id) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findProductoById(id);
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findProductoById(id);
        String sql = BASE_SELECT + " WHERE p.id = ?";
        List<Producto> results = query(sql, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** Unlike {@link #existsByCodigo}, which intentionally checks
     *  uniqueness across ALL areas (codes are globally unique), this is a
     *  lookup meant to hand back a product's full details — so it respects
     *  the caller's area scope the same way {@link #findAll} does, instead
     *  of exposing another area's product to a lookup/barcode-scan flow. */
    public Optional<Producto> findByCodigo(String codigo) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (DatabaseConfig.isOfflineMode()) {
            return OfflineStore.findProductoByCodigo(codigo)
                .filter(p -> accessible == null || accessible.contains(p.getArea()));
        }
        if (DatabaseConfig.isDemoMode()) {
            return DemoDataStore.findProductoByCodigo(codigo)
                .filter(p -> accessible == null || accessible.contains(p.getArea()));
        }
        List<Object> params = new ArrayList<>();
        params.add(codigo);
        StringBuilder sb = new StringBuilder(BASE_SELECT).append(" WHERE LOWER(p.codigo) = LOWER(?)");
        if (accessible != null) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        List<Producto> results = queryDynamic(sb.toString(), params);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Producto save(Producto p) throws SQLException {
        if (p.getId() == null) p.setId(UUID.randomUUID().toString());
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.saveProducto(p);
            return p;
        }
        if (DatabaseConfig.isDemoMode()) {
            if (p.getCreadoEn() == null) p.setCreadoEn(java.time.LocalDateTime.now());
            p.setActualizadoEn(java.time.LocalDateTime.now());
            DemoDataStore.saveProducto(p);
            return p;
        }
        return saveOnline(p);
    }

    /** The real-Postgres half of {@link #save}, callable directly — used by
     *  SyncService to replay a queued offline product write once the
     *  connection to Postgres comes back, so replay goes through the exact
     *  same upsert (and its ON CONFLICT semantics) as a normal online save. */
    public Producto saveOnline(Producto p) throws SQLException {
        String sql = """
            INSERT INTO products (id, nombre, codigo, descripcion, categoria_id, precio_compra,
                precio_venta, stock_actual, stock_minimo, stock_maximo, unidad, proveedor,
                fecha_vencimiento, foto_url, ubicacion, area, resguardante, created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (id) DO UPDATE SET
                nombre = EXCLUDED.nombre,
                codigo = EXCLUDED.codigo,
                descripcion = EXCLUDED.descripcion,
                categoria_id = EXCLUDED.categoria_id,
                precio_compra = EXCLUDED.precio_compra,
                precio_venta = EXCLUDED.precio_venta,
                stock_actual = EXCLUDED.stock_actual,
                stock_minimo = EXCLUDED.stock_minimo,
                stock_maximo = EXCLUDED.stock_maximo,
                unidad = EXCLUDED.unidad,
                proveedor = EXCLUDED.proveedor,
                fecha_vencimiento = EXCLUDED.fecha_vencimiento,
                foto_url = EXCLUDED.foto_url,
                ubicacion = EXCLUDED.ubicacion,
                area = EXCLUDED.area,
                resguardante = EXCLUDED.resguardante,
                updated_at = NOW()
            """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            ps.setString(1, p.getId());
            ps.setString(2, p.getNombre());
            ps.setString(3, p.getCodigo());
            ps.setString(4, p.getDescripcion());
            ps.setString(5, p.getCategoriaId());
            ps.setBigDecimal(6, p.getPrecioCompra());
            ps.setBigDecimal(7, p.getPrecioVenta());
            ps.setInt(8, p.getStockActual());
            ps.setInt(9, p.getStockMinimo());
            ps.setInt(10, p.getStockMaximo());
            ps.setString(11, p.getUnidad() != null ? p.getUnidad().getCodigo() : "pieza");
            ps.setString(12, p.getProveedor());
            ps.setObject(13, p.getFechaVencimiento());
            ps.setString(14, p.getFotoUrl());
            ps.setString(15, p.getUbicacion());
            ps.setString(16, p.getArea());
            ps.setString(17, p.getResguardante());
            ps.setTimestamp(18, p.getCreadoEn() != null ? Timestamp.valueOf(p.getCreadoEn()) : Timestamp.valueOf(now));
            ps.setTimestamp(19, Timestamp.valueOf(now));
            ps.executeUpdate();
        }
        return p;
    }

    public void updateStock(String productoId, int newStock) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.updateProductoStock(productoId, newStock);
            return;
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.updateProductoStock(productoId, newStock);
            return;
        }
        String sql = "UPDATE products SET stock_actual = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, productoId);
            ps.executeUpdate();
        }
    }

    /** Formal baja patrimonial (decommission) — soft-delete: the record and
     *  its movement history stay in the database, it's just excluded from
     *  the active inventory (see {@link #findAll()}). This is what the UI's
     *  "Dar de baja" action should call, not {@link #delete}. */
    public void darDeBaja(String id, String motivo) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.darDeBajaProducto(id, motivo);
            return;
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.darDeBajaProducto(id, motivo);
            return;
        }
        darDeBajaOnline(id, motivo);
    }

    /** Replay target for SyncService — see {@link #saveOnline}. */
    public void darDeBajaOnline(String id, String motivo) throws SQLException {
        String sql = "UPDATE products SET fecha_baja = CURRENT_DATE, motivo_baja = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, motivo);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    /** Reverses a baja patrimonial — puts the bien back into active
     *  inventory. Kept separate from {@link #save} so it can't accidentally
     *  be triggered by an unrelated edit. */
    public void reactivar(String id) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.reactivarProducto(id);
            return;
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.reactivarProducto(id);
            return;
        }
        reactivarOnline(id);
    }

    /** Replay target for SyncService — see {@link #saveOnline}. */
    public void reactivarOnline(String id) throws SQLException {
        String sql = "UPDATE products SET fecha_baja = NULL, motivo_baja = NULL, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public void delete(String id) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            // A hard delete isn't queueable in this version's offline scope
            // (product_outbox only supports SAVE/BAJA/REACTIVAR) — "Dar de
            // baja" (darDeBaja, a soft-delete that DOES queue) is the normal
            // path the UI uses anyway; this is the rarely-used hard-delete
            // action, which genuinely needs a live connection.
            throw new SQLException("Esta acción requiere conexión a internet. Usa \"Dar de baja\" para "
                + "quitar el bien del inventario activo mientras estás sin conexión.");
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.deleteProducto(id);
            return;
        }
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    public boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.existsByCodigo(codigo, excludeId);
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.existsByCodigo(codigo, excludeId);
        String sql = "SELECT 1 FROM products WHERE LOWER(codigo) = LOWER(?) AND id != ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ps.setString(2, excludeId != null ? excludeId : "");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // --- Internal helpers ---

    private List<Producto> query(String sql, String param) throws SQLException {
        List<Producto> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private List<Producto> queryDynamic(String sql, List<Object> params) throws SQLException {
        List<Producto> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr) {
                    ps.setArray(i + 1, conn.createArrayOf("text", arr));
                } else {
                    ps.setObject(i + 1, p);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Producto mapRow(ResultSet rs) throws SQLException {
        Producto p = new Producto();
        p.setId(rs.getString("id"));
        p.setNombre(rs.getString("nombre"));
        p.setCodigo(rs.getString("codigo"));
        p.setDescripcion(rs.getString("descripcion"));
        p.setCategoriaId(rs.getString("categoria_id"));
        p.setCategoriaNombre(rs.getString("categoria_nombre"));
        p.setCategoriaColor(rs.getString("categoria_color"));
        p.setPrecioCompra(rs.getBigDecimal("precio_compra"));
        p.setPrecioVenta(rs.getBigDecimal("precio_venta"));
        p.setStockActual(rs.getInt("stock_actual"));
        p.setStockMinimo(rs.getInt("stock_minimo"));
        p.setStockMaximo(rs.getInt("stock_maximo"));
        p.setUnidad(UnidadMedida.fromCodigo(rs.getString("unidad")));
        p.setProveedor(rs.getString("proveedor"));
        java.sql.Date fv = rs.getDate("fecha_vencimiento");
        if (fv != null) p.setFechaVencimiento(fv.toLocalDate());
        p.setFotoUrl(rs.getString("foto_url"));
        p.setUbicacion(rs.getString("ubicacion"));
        p.setArea(rs.getString("area"));
        p.setResguardante(rs.getString("resguardante"));
        Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) p.setCreadoEn(ca.toLocalDateTime());
        Timestamp ua = rs.getTimestamp("updated_at");
        if (ua != null) p.setActualizadoEn(ua.toLocalDateTime());
        java.sql.Date fb = rs.getDate("fecha_baja");
        if (fb != null) p.setFechaBaja(fb.toLocalDate());
        p.setMotivoBaja(rs.getString("motivo_baja"));
        return p;
    }
}
