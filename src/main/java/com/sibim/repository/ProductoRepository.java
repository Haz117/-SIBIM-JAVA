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
import java.util.LinkedHashMap;

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
                fecha_vencimiento, foto_url, ubicacion, area, resguardante,
                fecha_adquisicion, vida_util_anios, valor_residual,
                created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                fecha_adquisicion = EXCLUDED.fecha_adquisicion,
                vida_util_anios = EXCLUDED.vida_util_anios,
                valor_residual = EXCLUDED.valor_residual,
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
            ps.setObject(18, p.getFechaAdquisicion());
            ps.setObject(19, p.getVidaUtilAnios());
            ps.setBigDecimal(20, p.getValorResidual() != null ? p.getValorResidual() : BigDecimal.ZERO);
            ps.setTimestamp(21, p.getCreadoEn() != null ? Timestamp.valueOf(p.getCreadoEn()) : Timestamp.valueOf(now));
            ps.setTimestamp(22, Timestamp.valueOf(now));
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
        String sql = excludeId != null
            ? "SELECT 1 FROM products WHERE LOWER(codigo) = LOWER(?) AND id != ?"
            : "SELECT 1 FROM products WHERE LOWER(codigo) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            if (excludeId != null) ps.setString(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Aggregate stats for the dashboard — one round-trip, no full table load. */
    public record ProductoStats(long total, long activos, long bajoStock,
                                long agotados, long vencidos,
                                BigDecimal valorTotal, long categorias) {}

    /** Category name + total inventory value — used by the dashboard pie chart. */
    public record CategoriaValor(String nombre, BigDecimal valor) {}

    /** Returns aggregate product stats for the current user's visible areas. */
    public ProductoStats getStats() throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            List<Producto> all = findAll();
            LocalDate hoy = LocalDate.now();
            long venc = 0, agot = 0, bajo = 0;
            BigDecimal valor = BigDecimal.ZERO;
            Set<String> catSet = new HashSet<>();
            for (Producto p : all) {
                boolean esVenc = p.getFechaVencimiento() != null && p.getFechaVencimiento().isBefore(hoy);
                if (esVenc)                                             venc++;
                else if (p.getStockActual() == 0)                      agot++;
                else if (p.getStockActual() <= p.getStockMinimo())     bajo++;
                valor = valor.add(p.getValorTotal());
                if (p.getCategoriaId() != null) catSet.add(p.getCategoriaId());
            }
            return new ProductoStats(all.size(), all.size() - venc - agot - bajo, bajo, agot, venc, valor, catSet.size());
        }
        StringBuilder sb = new StringBuilder("""
            SELECT
              COUNT(*) AS total,
              COUNT(*) FILTER (WHERE stock_actual > stock_minimo
                AND (fecha_vencimiento IS NULL OR fecha_vencimiento >= CURRENT_DATE)) AS activos,
              COUNT(*) FILTER (WHERE stock_actual > 0 AND stock_actual <= stock_minimo
                AND (fecha_vencimiento IS NULL OR fecha_vencimiento >= CURRENT_DATE)) AS bajo_stock,
              COUNT(*) FILTER (WHERE stock_actual = 0
                AND (fecha_vencimiento IS NULL OR fecha_vencimiento >= CURRENT_DATE)) AS agotados,
              COUNT(*) FILTER (WHERE fecha_vencimiento IS NOT NULL AND fecha_vencimiento < CURRENT_DATE) AS vencidos,
              COALESCE(SUM(precio_venta * stock_actual), 0) AS valor_total,
              COUNT(DISTINCT categoria_id) AS categorias
            FROM products p
            WHERE p.fecha_baja IS NULL
            """);
        List<Object> params = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr)
                    ps.setArray(i + 1, conn.createArrayOf("text", arr));
                else
                    ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ProductoStats(
                        rs.getLong("total"), rs.getLong("activos"),
                        rs.getLong("bajo_stock"), rs.getLong("agotados"),
                        rs.getLong("vencidos"),
                        rs.getBigDecimal("valor_total"), rs.getLong("categorias"));
                }
            }
        }
        return new ProductoStats(0, 0, 0, 0, 0, BigDecimal.ZERO, 0);
    }

    /** Returns inventory value grouped by category, descending — for the pie chart. */
    public List<CategoriaValor> getValorPorCategoria() throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            List<Producto> all = findAll();
            Map<String, BigDecimal> map = new LinkedHashMap<>();
            for (Producto p : all) {
                String cat = p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "Sin categoría";
                map.merge(cat, p.getValorTotal(), BigDecimal::add);
            }
            return map.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoriaValor(e.getKey(), e.getValue()))
                .toList();
        }
        StringBuilder sb = new StringBuilder("""
            SELECT c.nombre, COALESCE(SUM(p.precio_venta * p.stock_actual), 0) AS valor
            FROM products p
            LEFT JOIN categories c ON c.id = p.categoria_id
            WHERE p.fecha_baja IS NULL
            """);
        List<Object> params = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" GROUP BY c.nombre HAVING SUM(p.precio_venta * p.stock_actual) > 0 ORDER BY valor DESC");
        List<CategoriaValor> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr)
                    ps.setArray(i + 1, conn.createArrayOf("text", arr));
                else
                    ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    result.add(new CategoriaValor(rs.getString("nombre"), rs.getBigDecimal("valor")));
            }
        }
        return result;
    }

    /** COUNT(*) of active bienes visible to the current user — no full load. */
    public long countAll() throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            return DemoDataStore.findAllProductos(SessionManager.getAccessibleAreas())
                .stream().filter(p -> !p.isDadoDeBaja()).count();
        }
        StringBuilder sb = new StringBuilder(
            "SELECT COUNT(*) FROM products p WHERE p.fecha_baja IS NULL");
        List<Object> params = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr)
                    ps.setArray(i + 1, conn.createArrayOf("text", arr));
                else
                    ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /** Bienes with stock_actual = 0 (agotados) — filtered in SQL. */
    public List<Producto> findAgotados() throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            return DemoDataStore.findAllProductos(SessionManager.getAccessibleAreas())
                .stream().filter(p -> !p.isDadoDeBaja() && p.getStockActual() == 0).toList();
        }
        StringBuilder sb = new StringBuilder(BASE_SELECT
            + " WHERE p.fecha_baja IS NULL AND p.stock_actual = 0");
        List<Object> params = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" ORDER BY p.nombre");
        return queryDynamic(sb.toString(), params);
    }

    /** Bienes whose stock is above zero but at or below their minimum threshold. */
    public List<Producto> findBajoStock() throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            return DemoDataStore.findAllProductos(SessionManager.getAccessibleAreas())
                .stream().filter(p -> !p.isDadoDeBaja()
                    && p.getStockActual() > 0
                    && p.getStockActual() <= p.getStockMinimo()).toList();
        }
        StringBuilder sb = new StringBuilder(BASE_SELECT
            + " WHERE p.fecha_baja IS NULL"
            + " AND p.stock_actual > 0 AND p.stock_actual <= p.stock_minimo");
        List<Object> params = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" ORDER BY p.stock_actual ASC");
        return queryDynamic(sb.toString(), params);
    }

    /** Bienes expiring within the next {@code dias} days (inclusive of already-expired). */
    public List<Producto> findVencidosProximos(int dias) throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            LocalDate limite = LocalDate.now().plusDays(dias);
            return DemoDataStore.findAllProductos(SessionManager.getAccessibleAreas())
                .stream().filter(p -> !p.isDadoDeBaja()
                    && p.getFechaVencimiento() != null
                    && !p.getFechaVencimiento().isAfter(limite))
                .sorted(Comparator.comparing(Producto::getFechaVencimiento))
                .toList();
        }
        StringBuilder sb = new StringBuilder(BASE_SELECT
            + " WHERE p.fecha_baja IS NULL"
            + " AND p.fecha_vencimiento IS NOT NULL"
            + " AND p.fecha_vencimiento <= ?");
        List<Object> params = new ArrayList<>();
        params.add(java.sql.Date.valueOf(LocalDate.now().plusDays(dias)));
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" ORDER BY p.fecha_vencimiento ASC");
        return queryDynamic(sb.toString(), params);
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
        p.setFechaAdquisicion(rs.getDate("fecha_adquisicion") != null ? rs.getDate("fecha_adquisicion").toLocalDate() : null);
        p.setVidaUtilAnios(rs.getObject("vida_util_anios", Integer.class));
        p.setValorResidual(rs.getBigDecimal("valor_residual") != null ? rs.getBigDecimal("valor_residual") : BigDecimal.ZERO);
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
