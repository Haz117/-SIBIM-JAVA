package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.LocalDataStore;
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Producto> all = local.findAllProductos(SessionManager.getAccessibleAreas());
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
        List<Producto> result = queryDynamic(sb.toString(), params);
        // Keeps OfflineStore's local mirror fresh with real data while
        // connected, so a later mid-session disconnect (see SyncService)
        // falls back to what was actually on screen instead of an empty
        // inventory. Best-effort — never allowed to affect this read.
        OfflineStore.cacheProductos(result);
        return result;
    }

    // ── Server-side filtering & pagination ───────────────────────────────────

    /** Aggregate stats for the Bienes screen stat cards — one round-trip. */
    public record InventarioStats(long total, java.math.BigDecimal valorTotal, long alertas, long sinEtiquetar) {}

    /** SQL expression that mirrors ProductoUtils.computeEstado logic. */
    private static final String ESTADO_SQL =
        "CASE WHEN p.fecha_vencimiento IS NOT NULL AND p.fecha_vencimiento < CURRENT_DATE THEN 'vencido' " +
        "WHEN p.stock_actual = 0 THEN 'agotado' " +
        "WHEN p.stock_actual <= p.stock_minimo THEN 'bajo_stock' " +
        "ELSE 'activo' END";

    /** Builds a WHERE clause (with leading space) and populates {@code params}
     *  for all server-side filter queries. */
    private static String buildFiltroWhere(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            boolean incluirBaja, boolean soloSinEtiquetar, List<Object> params,
            LocalDate desdeReg, LocalDate hastaReg) {
        List<String> conds = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null) {
            conds.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        if (!incluirBaja) conds.add("p.fecha_baja IS NULL");
        if (soloSinEtiquetar) conds.add("p.etiquetado = FALSE");
        if (busqueda != null && !busqueda.isBlank()) {
            String like = "%" + busqueda.toLowerCase() + "%";
            conds.add("(LOWER(p.nombre) LIKE ? OR LOWER(p.codigo) LIKE ? OR LOWER(p.proveedor) LIKE ? OR LOWER(p.ubicacion) LIKE ? OR LOWER(p.resguardante) LIKE ? OR LOWER(COALESCE(p.marca,'')) LIKE ? OR LOWER(COALESCE(p.modelo,'')) LIKE ? OR LOWER(COALESCE(p.numero_serie,'')) LIKE ?)");
            params.add(like); params.add(like); params.add(like); params.add(like); params.add(like);
            params.add(like); params.add(like); params.add(like);
        }
        if (categoriaId != null) { conds.add("p.categoria_id = ?"); params.add(categoriaId); }
        if (area != null) { conds.add("p.area = ?"); params.add(area); }
        if (resguardante != null) { conds.add("p.resguardante = ?"); params.add(resguardante); }
        if (estado != null) {
            conds.add("(" + ESTADO_SQL + ") = ?");
            params.add(estado.getCodigo());
        }
        if (desdeReg != null) { conds.add("p.creado_en >= ?"); params.add(java.sql.Timestamp.valueOf(desdeReg.atStartOfDay())); }
        if (hastaReg != null) { conds.add("p.creado_en < ?"); params.add(java.sql.Timestamp.valueOf(hastaReg.plusDays(1).atStartOfDay())); }
        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
    }

    /** Returns one page of products matching the given filters (LIMIT/OFFSET). */
    public List<Producto> findPaginated(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            boolean incluirBaja, int limit, int offset,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return findPaginated(busqueda, categoriaId, area, resguardante, estado, incluirBaja, false, limit, offset, desdeReg, hastaReg);
    }

    public List<Producto> findPaginated(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            boolean incluirBaja, boolean soloSinEtiquetar, int limit, int offset,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Producto> all = findAll(incluirBaja);
            return applyClientFilters(all, busqueda, categoriaId, area, resguardante, estado, soloSinEtiquetar, desdeReg, hastaReg)
                .stream().skip(offset).limit(limit).toList();
        }
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(busqueda, categoriaId, area, resguardante, estado, incluirBaja, soloSinEtiquetar, params, desdeReg, hastaReg);
        String sql = BASE_SELECT + where + " ORDER BY p.nombre LIMIT ? OFFSET ?";
        params.add(limit); params.add(offset);
        return queryDynamic(sql, params);
    }

    /** Returns the COUNT(*) of products matching the given filters — for pagination. */
    public int countFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            boolean incluirBaja, LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        return countFiltrado(busqueda, categoriaId, area, resguardante, estado, incluirBaja, false, desdeReg, hastaReg);
    }

    public int countFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            boolean incluirBaja, boolean soloSinEtiquetar, LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Producto> all = findAll(incluirBaja);
            return applyClientFilters(all, busqueda, categoriaId, area, resguardante, estado, soloSinEtiquetar, desdeReg, hastaReg).size();
        }
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(busqueda, categoriaId, area, resguardante, estado, incluirBaja, soloSinEtiquetar, params, desdeReg, hastaReg);
        String sql = "SELECT COUNT(*) FROM products p" + where;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = buildStatement(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** Returns ALL products matching the given filters — for export and conteo físico
     *  (no LIMIT/OFFSET, always excludes bienes dados de baja). */
    public List<Producto> findAllFiltrado(String busqueda, String categoriaId, String area,
            String resguardante, com.sibim.model.enums.EstadoProducto estado,
            LocalDate desdeReg, LocalDate hastaReg) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return applyClientFilters(local.findAllProductos(SessionManager.getAccessibleAreas()),
                busqueda, categoriaId, area, resguardante, estado, false, desdeReg, hastaReg);
        }
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(busqueda, categoriaId, area, resguardante, estado, false, false, params, desdeReg, hastaReg);
        return queryDynamic(BASE_SELECT + where + " ORDER BY p.nombre", params);
    }

    /** Marks the given product IDs as etiquetado = {@code valor} in one round-trip. */
    public void marcarEtiquetado(List<String> ids, boolean valor) throws SQLException {
        if (ids == null || ids.isEmpty()) return;
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            for (String id : ids) {
                local.findProductoById(id).ifPresent(p -> { p.setEtiquetado(valor); local.saveProducto(p); });
            }
            return;
        }
        String sql = "UPDATE products SET etiquetado = ?, updated_at = NOW() WHERE id = ANY(?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, valor);
            ps.setArray(2, conn.createArrayOf("text", ids.toArray(new String[0])));
            ps.executeUpdate();
        }
    }

    /** Returns distinct non-blank resguardante values (for the dropdown). */
    public List<String> findDistinctResguardantes() throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return local.findAllProductos(SessionManager.getAccessibleAreas()).stream()
                .map(Producto::getResguardante).filter(r -> r != null && !r.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        StringBuilder sb = new StringBuilder(
            "SELECT DISTINCT resguardante FROM products WHERE fecha_baja IS NULL AND resguardante IS NOT NULL AND resguardante <> ''");
        Set<String> accessible = SessionManager.getAccessibleAreas();
        List<Object> params = new ArrayList<>();
        if (accessible != null) { sb.append(" AND area = ANY(?)"); params.add(accessible.toArray(new String[0])); }
        sb.append(" ORDER BY 1");
        List<String> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = buildStatement(conn, sb.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    /** Aggregate stats for the Bienes stat cards — one round-trip, full inventory. */
    public InventarioStats findStats() throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Producto> all = findAll(false);
            long total = all.size();
            java.math.BigDecimal valor = all.stream()
                .map(p -> { java.math.BigDecimal v = p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
                            return v.multiply(java.math.BigDecimal.valueOf(p.getStockActual())); })
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            long alertas = all.stream()
                .filter(p -> p.getEstado() != com.sibim.model.enums.EstadoProducto.ACTIVO).count();
            long sinEtiq = all.stream().filter(p -> !p.isEtiquetado()).count();
            return new InventarioStats(total, valor, alertas, sinEtiq);
        }
        String sql = """
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(precio_venta * stock_actual), 0) AS valor_total,
                   COUNT(*) FILTER (WHERE %s IN ('agotado','bajo_stock','vencido')) AS alertas,
                   COUNT(*) FILTER (WHERE etiquetado = FALSE) AS sin_etiquetar
            FROM products p
            WHERE fecha_baja IS NULL
            """.formatted(ESTADO_SQL);
        Set<String> accessible = SessionManager.getAccessibleAreas();
        List<Object> params = new ArrayList<>();
        if (accessible != null) {
            sql += " AND p.area = ANY(?)";
            params.add(accessible.toArray(new String[0]));
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = buildStatement(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return new InventarioStats(rs.getLong("total"),
                    rs.getBigDecimal("valor_total"), rs.getLong("alertas"),
                    rs.getLong("sin_etiquetar"));
            }
        }
        return new InventarioStats(0, java.math.BigDecimal.ZERO, 0, 0);
    }

    /** In-memory filter for offline/demo mode — mirrors {@link #buildFiltroWhere}. */
    private static List<Producto> applyClientFilters(List<Producto> all, String busqueda,
            String categoriaId, String area, String resguardante,
            com.sibim.model.enums.EstadoProducto estado, boolean soloSinEtiquetar,
            LocalDate desdeReg, LocalDate hastaReg) {
        java.util.stream.Stream<Producto> stream = all.stream()
            .filter(p -> busqueda == null || busqueda.isBlank()
                || p.getNombre().toLowerCase().contains(busqueda.toLowerCase())
                || p.getCodigo().toLowerCase().contains(busqueda.toLowerCase())
                || (p.getProveedor() != null && p.getProveedor().toLowerCase().contains(busqueda.toLowerCase()))
                || (p.getUbicacion() != null && p.getUbicacion().toLowerCase().contains(busqueda.toLowerCase()))
                || (p.getResguardante() != null && p.getResguardante().toLowerCase().contains(busqueda.toLowerCase())))
            .filter(p -> categoriaId == null || categoriaId.equals(p.getCategoriaId()))
            .filter(p -> area == null || area.equals(p.getArea()))
            .filter(p -> resguardante == null || resguardante.equals(p.getResguardante()))
            .filter(p -> estado == null || p.getEstado() == estado)
            .filter(p -> !soloSinEtiquetar || !p.isEtiquetado());
        if (desdeReg != null) stream = stream.filter(p -> p.getCreadoEn() != null && !p.getCreadoEn().toLocalDate().isBefore(desdeReg));
        if (hastaReg != null) stream = stream.filter(p -> p.getCreadoEn() != null && !p.getCreadoEn().toLocalDate().isAfter(hastaReg));
        return stream
            .sorted(java.util.Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** Builds a PreparedStatement with heterogeneous param types (String[], Object). */
    private static PreparedStatement buildStatement(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            Object v = params.get(i);
            if (v instanceof String[] arr) ps.setArray(i + 1, conn.createArrayOf("text", arr));
            else ps.setObject(i + 1, v);
        }
        return ps;
    }

    // ── Existing methods ─────────────────────────────────────────────────────

    public List<Producto> findByDateRange(LocalDate desde, LocalDate hasta) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Producto> all = local.findAllProductos(SessionManager.getAccessibleAreas());
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findAllProductos(Set.of(area));
        String sql = BASE_SELECT + " WHERE p.area = ? ORDER BY p.nombre";
        return query(sql, area);
    }

    public Optional<Producto> findById(String id) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findProductoById(id);
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return local.findProductoByCodigo(codigo)
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            if (p.getCreadoEn() == null) p.setCreadoEn(java.time.LocalDateTime.now());
            p.setActualizadoEn(java.time.LocalDateTime.now());
            local.saveProducto(p);
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
                fecha_vencimiento, foto_url, factura_url, numero_serie, marca, modelo, ubicacion, area, resguardante,
                fecha_adquisicion, vida_util_anios, valor_residual, etiquetado,
                created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                factura_url = EXCLUDED.factura_url,
                numero_serie = EXCLUDED.numero_serie,
                marca = EXCLUDED.marca,
                modelo = EXCLUDED.modelo,
                ubicacion = EXCLUDED.ubicacion,
                area = EXCLUDED.area,
                resguardante = EXCLUDED.resguardante,
                fecha_adquisicion = EXCLUDED.fecha_adquisicion,
                vida_util_anios = EXCLUDED.vida_util_anios,
                valor_residual = EXCLUDED.valor_residual,
                etiquetado = EXCLUDED.etiquetado,
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
            ps.setString(15, p.getFacturaUrl());
            ps.setString(16, p.getNumeroSerie());
            ps.setString(17, p.getMarca());
            ps.setString(18, p.getModelo());
            ps.setString(19, p.getUbicacion());
            ps.setString(20, p.getArea());
            ps.setString(21, p.getResguardante());
            ps.setObject(22, p.getFechaAdquisicion());
            ps.setObject(23, p.getVidaUtilAnios());
            ps.setBigDecimal(24, p.getValorResidual() != null ? p.getValorResidual() : BigDecimal.ZERO);
            ps.setBoolean(25, p.isEtiquetado());
            ps.setTimestamp(26, p.getCreadoEn() != null ? Timestamp.valueOf(p.getCreadoEn()) : Timestamp.valueOf(now));
            ps.setTimestamp(27, Timestamp.valueOf(now));
            ps.executeUpdate();
        }
        return p;
    }

    public void updateStock(String productoId, int newStock) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            local.updateProductoStock(productoId, newStock);
            return;
        }
        String sql = "UPDATE products SET stock_actual = ?, updated_at = NOW() WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, productoId);
            if (ps.executeUpdate() == 0)
                throw new SQLException("El bien ya no existe en la base de datos (id=" + productoId + ")");
        }
    }

    /** Formal baja patrimonial (decommission) — soft-delete: the record and
     *  its movement history stay in the database, it's just excluded from
     *  the active inventory (see {@link #findAll()}). This is what the UI's
     *  "Dar de baja" action should call, not {@link #delete}. */
    public void darDeBaja(String id, String motivo) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            local.darDeBajaProducto(id, motivo);
            return;
        }
        darDeBajaOnline(id, motivo);
    }

    /** Replay target for SyncService — see {@link #saveOnline}. */
    public void darDeBajaOnline(String id, String motivo) throws SQLException {
        String sql = "UPDATE products SET fecha_baja = CURRENT_DATE, motivo_baja = ?, updated_at = NOW() WHERE id = ? AND fecha_baja IS NULL";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, motivo);
            ps.setString(2, id);
            if (ps.executeUpdate() == 0)
                throw new SQLException("El bien no existe o ya estaba dado de baja (id=" + id + ")");
        }
    }

    /** Reverses a baja patrimonial — puts the bien back into active
     *  inventory. Kept separate from {@link #save} so it can't accidentally
     *  be triggered by an unrelated edit. */
    public void reactivar(String id) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            local.reactivarProducto(id);
            return;
        }
        reactivarOnline(id);
    }

    /** Replay target for SyncService — see {@link #saveOnline}. */
    public void reactivarOnline(String id) throws SQLException {
        String sql = "UPDATE products SET fecha_baja = NULL, motivo_baja = NULL, updated_at = NOW() WHERE id = ? AND fecha_baja IS NOT NULL";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            if (ps.executeUpdate() == 0)
                throw new SQLException("El bien no existe o no estaba dado de baja (id=" + id + ")");
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.existsByCodigo(codigo, excludeId);
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

    /** Active bienes grouped by area — used for the Reportes area distribution chart.
     *  Returns a LinkedHashMap ordered descending by count (up to {@code limit} entries). */
    public LinkedHashMap<String, Long> countByArea(int limit) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            var all = local.findAllProductos(SessionManager.getAccessibleAreas()).stream()
                .filter(p -> !p.isDadoDeBaja()).toList();
            LinkedHashMap<String, Long> result = new LinkedHashMap<>();
            all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    p -> p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área",
                    java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> result.put(e.getKey(), e.getValue()));
            return result;
        }
        StringBuilder sb = new StringBuilder(
            "SELECT COALESCE(NULLIF(area,''), 'Sin área') AS area_label, COUNT(*) AS cnt " +
            "FROM products p WHERE p.fecha_baja IS NULL");
        List<Object> params = new ArrayList<>();
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (accessible != null && !accessible.isEmpty()) {
            sb.append(" AND p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" GROUP BY area_label ORDER BY cnt DESC LIMIT ").append(limit);
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = buildStatement(conn, sb.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.put(rs.getString("area_label"), rs.getLong("cnt"));
        }
        return result;
    }

    /** Bienes with stock_actual = 0 (agotados) — filtered in SQL. */
    public List<Producto> findAgotados() throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return local.findAllProductos(SessionManager.getAccessibleAreas()).stream()
                .filter(p -> !p.isDadoDeBaja() && p.getStockActual() == 0)
                .toList();
        }
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return local.findAllProductos(SessionManager.getAccessibleAreas()).stream()
                .filter(p -> !p.isDadoDeBaja()
                    && p.getStockActual() > 0
                    && p.getStockActual() <= p.getStockMinimo())
                .sorted(Comparator.comparingInt(Producto::getStockActual))
                .toList();
        }
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
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            LocalDate limite = LocalDate.now().plusDays(dias);
            return local.findAllProductos(SessionManager.getAccessibleAreas()).stream()
                .filter(p -> !p.isDadoDeBaja()
                    && p.getFechaVencimiento() != null
                    && !p.getFechaVencimiento().isAfter(limite))
                .sorted(Comparator.comparing(Producto::getFechaVencimiento))
                .toList();
        }
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
        p.setFacturaUrl(rs.getString("factura_url"));
        p.setNumeroSerie(rs.getString("numero_serie"));
        p.setMarca(rs.getString("marca"));
        p.setModelo(rs.getString("modelo"));
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
        p.setEtiquetado(rs.getBoolean("etiquetado"));
        return p;
    }

    /** Returns the ordered list of photo URLs for a given product. */
    public List<String> findFotos(String productoId) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null) {
            // offline: fotos stored in Producto.fotosUrls (already loaded)
            return new java.util.ArrayList<>();
        }
        if (DatabaseConfig.isDemoMode()) return new java.util.ArrayList<>();
        List<String> fotos = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT foto_url FROM product_fotos WHERE producto_id = ? ORDER BY orden")) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) fotos.add(rs.getString(1));
            }
        }
        return fotos;
    }

    /** Replaces all photos for a product (deletes then re-inserts in order). */
    public void saveFotos(String productoId, List<String> fotos) throws SQLException {
        if (DatabaseConfig.getLocalDataStore() != null || DatabaseConfig.isDemoMode()) return;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveFotosInTx(conn, productoId, fotos);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private static void saveFotosInTx(Connection conn, String productoId, List<String> fotos) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM product_fotos WHERE producto_id = ?")) {
            del.setString(1, productoId);
            del.executeUpdate();
        }
        if (fotos == null || fotos.isEmpty()) return;
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO product_fotos (id, producto_id, foto_url, orden) VALUES (?,?,?,?)")) {
            for (int i = 0; i < fotos.size(); i++) {
                ins.setString(1, java.util.UUID.randomUUID().toString());
                ins.setString(2, productoId);
                ins.setString(3, fotos.get(i));
                ins.setInt(4, i);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }
}
