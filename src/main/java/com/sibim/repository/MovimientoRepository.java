package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.LocalDataStore;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.config.AreaCodigos;
import com.sibim.model.Movimiento;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.session.SessionManager;
import com.sibim.util.ProductoUtils;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MovimientoRepository {

    private static final Logger log = LoggerFactory.getLogger(MovimientoRepository.class);

    /** Aggregate stats for the movement list view — mirrors ProductoRepository.InventarioStats. */
    public record MovimientoStats(long total, long entradas, long salidas, long ajustes) {}

    /** Monthly aggregated movements for the trend chart in the Dashboard. */
    public record MonthlyStats(String label, int entradas, int salidas) {}

    /** Monthly patrimonial value for the valor chart — MXN entradas/salidas from movements × precio_compra. */
    public record MonthlyValorStats(String label, BigDecimal valorEntradas, BigDecimal valorSalidas) {}

    private static final String[] MES_ABREV =
        {"Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"};

    public List<MonthlyStats> findMonthlyStats(int months) throws SQLException {
        LocalDate fromMonth = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1);
        LinkedHashMap<String, int[]> byMonth = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate m = LocalDate.now().withDayOfMonth(1).minusMonths(i);
            byMonth.put(m.getYear() + "-" + String.format("%02d", m.getMonthValue()), new int[]{0, 0});
        }

        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Movimiento> movs = local.findMovimientosByDateRange(
                fromMonth, LocalDate.now(), SessionManager.getAccessibleAreas());
            for (Movimiento m : movs) {
                LocalDate d = m.getCreadoEn().toLocalDate();
                int[] arr = byMonth.get(d.getYear() + "-" + String.format("%02d", d.getMonthValue()));
                if (arr == null) continue;
                switch (m.getTipo()) {
                    case ENTRADA -> arr[0] += m.getCantidad();
                    case SALIDA  -> arr[1] += m.getCantidad();
                    default -> {}
                }
            }
        } else {
            Set<String> accessible = SessionManager.getAccessibleAreas();
            StringBuilder sql = new StringBuilder("""
                SELECT TO_CHAR(DATE_TRUNC('month', m.created_at), 'YYYY-MM') AS ym,
                    SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad ELSE 0 END)::int AS entradas,
                    SUM(CASE WHEN m.tipo = 'SALIDA'  THEN m.cantidad ELSE 0 END)::int AS salidas
                FROM movements m
                JOIN products p ON p.id = m.producto_id
                WHERE m.created_at >= ?
                """);
            if (accessible != null) sql.append("AND p.area = ANY(?) ");
            sql.append("GROUP BY DATE_TRUNC('month', m.created_at) ORDER BY ym ASC");
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                ps.setTimestamp(1, Timestamp.valueOf(fromMonth.atStartOfDay()));
                if (accessible != null)
                    ps.setArray(2, conn.createArrayOf("text", accessible.toArray(new String[0])));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int[] arr = byMonth.get(rs.getString("ym"));
                        if (arr != null) { arr[0] = rs.getInt("entradas"); arr[1] = rs.getInt("salidas"); }
                    }
                }
            }
        }

        return byMonth.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("-");
            int monthIdx = Integer.parseInt(parts[1]) - 1;
            return new MonthlyStats(MES_ABREV[monthIdx] + " '" + parts[0].substring(2),
                e.getValue()[0], e.getValue()[1]);
        }).toList();
    }

    public List<MonthlyValorStats> findMonthlyValorStats(int months) throws SQLException {
        LocalDate fromMonth = LocalDate.now().withDayOfMonth(1).minusMonths(months - 1);
        LinkedHashMap<String, BigDecimal[]> byMonth = new LinkedHashMap<>();
        for (int i = months - 1; i >= 0; i--) {
            LocalDate m = LocalDate.now().withDayOfMonth(1).minusMonths(i);
            byMonth.put(m.getYear() + "-" + String.format("%02d", m.getMonthValue()),
                new BigDecimal[]{ BigDecimal.ZERO, BigDecimal.ZERO });
        }

        if (DatabaseConfig.getLocalDataStore() != null) {
            // Demo/offline mode: no price data available — return zero-filled skeleton
            return byMonth.entrySet().stream().map(e -> {
                String[] parts = e.getKey().split("-");
                int monthIdx = Integer.parseInt(parts[1]) - 1;
                return new MonthlyValorStats(MES_ABREV[monthIdx] + " '" + parts[0].substring(2),
                    BigDecimal.ZERO, BigDecimal.ZERO);
            }).toList();
        }

        Set<String> accessible = SessionManager.getAccessibleAreas();
        StringBuilder sql = new StringBuilder("""
            SELECT TO_CHAR(DATE_TRUNC('month', m.created_at), 'YYYY-MM') AS ym,
                SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.cantidad * COALESCE(p.precio_compra, 0) ELSE 0 END) AS valor_entradas,
                SUM(CASE WHEN m.tipo = 'SALIDA'  THEN m.cantidad * COALESCE(p.precio_compra, 0) ELSE 0 END) AS valor_salidas
            FROM movements m
            JOIN products p ON p.id = m.producto_id
            WHERE m.created_at >= ?
            """);
        if (accessible != null) sql.append("AND p.area = ANY(?) ");
        sql.append("GROUP BY DATE_TRUNC('month', m.created_at) ORDER BY ym ASC");

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setTimestamp(1, Timestamp.valueOf(fromMonth.atStartOfDay()));
            if (accessible != null)
                ps.setArray(2, conn.createArrayOf("text", accessible.toArray(new String[0])));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal[] arr = byMonth.get(rs.getString("ym"));
                    if (arr != null) {
                        arr[0] = rs.getBigDecimal("valor_entradas");
                        if (arr[0] == null) arr[0] = BigDecimal.ZERO;
                        arr[1] = rs.getBigDecimal("valor_salidas");
                        if (arr[1] == null) arr[1] = BigDecimal.ZERO;
                    }
                }
            }
        }

        return byMonth.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("-");
            int monthIdx = Integer.parseInt(parts[1]) - 1;
            return new MonthlyValorStats(MES_ABREV[monthIdx] + " '" + parts[0].substring(2),
                e.getValue()[0], e.getValue()[1]);
        }).toList();
    }

    private static final String BASE_SELECT = """
        SELECT m.*, p.nombre AS producto_nombre, c.color AS categoria_color
        FROM movements m
        JOIN products p ON p.id = m.producto_id
        LEFT JOIN categories c ON c.id = p.categoria_id
        """;

    public List<Movimiento> findAll() throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findAllMovimientos(accessible);
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();
        if (accessible != null) {
            sb.append(" WHERE p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" ORDER BY m.created_at DESC");
        List<Movimiento> result = queryDynamic(sb.toString(), params);
        // Keeps OfflineStore's local mirror fresh — see ProductoRepository#findAll.
        OfflineStore.cacheMovimientos(result);
        return result;
    }

    /** Used for authorization checks before deleting a movement — doesn't
     *  apply the caller's own area filter, since the caller needs to know
     *  the product's area precisely to decide whether they're allowed to. */
    public Optional<String> findProductoIdById(String movimientoId) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findProductoIdByMovimientoId(movimientoId);
        String sql = "SELECT producto_id FROM movements WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movimientoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("producto_id")) : Optional.empty();
            }
        }
    }

    /** Returns the estado of a movement without loading the full record —
     *  used by the service to block deletion of PENDIENTE transfers. */
    public Optional<String> findEstadoById(String movimientoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) {
            return DemoDataStore.findAllMovimientos(null).stream()
                .filter(m -> movimientoId.equals(m.getId()))
                .map(Movimiento::getEstado)
                .findFirst();
        }
        if (DatabaseConfig.isOfflineMode()) {
            return OfflineStore.findAllMovimientos(null).stream()
                .filter(m -> movimientoId.equals(m.getId()))
                .map(Movimiento::getEstado)
                .findFirst();
        }
        String sql = "SELECT estado FROM movements WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movimientoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("estado")) : Optional.empty();
            }
        }
    }

    public List<Movimiento> findByProducto(String productoId) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findMovimientosByProducto(productoId, accessible);
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        conditions.add("m.producto_id = ?");
        params.add(productoId);
        if (accessible != null) {
            conditions.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        String sql = BASE_SELECT + " WHERE " + String.join(" AND ", conditions) + " ORDER BY m.created_at DESC";
        return queryDynamic(sql, params);
    }

    /** Fetches movements for multiple products in a single query and groups them by productoId.
     *  Falls back to per-product calls in local/offline mode where ANY(array) isn't available. */
    public Map<String, List<Movimiento>> findByProductoIds(List<String> ids) throws SQLException {
        Map<String, List<Movimiento>> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return result;
        for (String id : ids) result.put(id, new ArrayList<>());
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            for (String id : ids)
                result.put(id, local.findMovimientosByProducto(id, accessible));
            return result;
        }
        List<Object> params = new ArrayList<>();
        params.add(ids.toArray(new String[0]));
        String where = " WHERE m.producto_id = ANY(?)";
        if (accessible != null) {
            where += " AND p.area = ANY(?)";
            params.add(accessible.toArray(new String[0]));
        }
        String sql = BASE_SELECT + where + " ORDER BY m.created_at DESC";
        for (Movimiento m : queryDynamic(sql, params))
            result.computeIfAbsent(m.getProductoId(), k -> new ArrayList<>()).add(m);
        return result;
    }

    public List<Movimiento> findByDateRange(LocalDate desde, LocalDate hasta) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return local.findMovimientosByDateRange(desde, hasta, accessible);
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (accessible != null) {
            conditions.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        if (desde != null) { conditions.add("m.created_at >= ?"); params.add(Timestamp.valueOf(desde.atStartOfDay())); }
        if (hasta != null) { conditions.add("m.created_at <= ?"); params.add(Timestamp.valueOf(hasta.atTime(LocalTime.MAX))); }
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
        sb.append(" ORDER BY m.created_at DESC");
        List<Movimiento> result = queryDynamic(sb.toString(), params);
        // Keeps OfflineStore's local mirror fresh — see ProductoRepository#findAll.
        OfflineStore.cacheMovimientos(result);
        return result;
    }

    // ── Server-side pagination support ───────────────────────────────────────

    /** Builds the shared WHERE clause (with "WHERE" prefix) used by
     *  {@link #findPaginated} and {@link #countFiltrado}.  Conditions and
     *  their bound values are appended to {@code params} in lock-step. */
    private String buildFiltroWhere(LocalDate desde, LocalDate hasta,
                                    String query, String tipo, String categoriaNombre,
                                    List<Object> params, Set<String> accessible) {
        List<String> conditions = new ArrayList<>();

        if (accessible != null) {
            conditions.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        if (desde != null) {
            conditions.add("m.created_at >= ?");
            params.add(Timestamp.valueOf(desde.atStartOfDay()));
        }
        if (hasta != null) {
            conditions.add("m.created_at <= ?");
            params.add(Timestamp.valueOf(hasta.atTime(LocalTime.MAX)));
        }
        if (tipo != null && !"Todos".equals(tipo)) {
            for (TipoMovimiento tm : TipoMovimiento.values()) {
                if (tm.getEtiqueta().equals(tipo)) {
                    conditions.add("m.tipo = ?");
                    params.add(tm.getCodigo());
                    break;
                }
            }
        }
        if (categoriaNombre != null) {
            conditions.add("c.nombre = ?");
            params.add(categoriaNombre);
        }
        if (query != null && !query.isBlank()) {
            conditions.add("(p.nombre ILIKE ? OR m.motivo ILIKE ? OR m.referencia ILIKE ?)");
            String likeVal = "%" + query + "%";
            params.add(likeVal);
            params.add(likeVal);
            params.add(likeVal);
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /** Returns one page of movements matching the given filters, ordered by
     *  created_at DESC (most recent first). */
    public List<Movimiento> findPaginated(LocalDate desde, LocalDate hasta,
                                          String query, String tipo, String categoriaNombre,
                                          int limit, int offset) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Movimiento> all = local.findMovimientosByDateRange(desde, hasta, accessible);
            return applyClientFilters(all, query, tipo, categoriaNombre)
                .stream().skip(offset).limit(limit).toList();
        }
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(desde, hasta, query, tipo, categoriaNombre, params, accessible);
        String sql = BASE_SELECT + where + " ORDER BY m.created_at DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return queryDynamic(sql, params);
    }

    /** Returns the total count of movements matching the given filters — used
     *  to compute the number of pages without loading the full result set. */
    public int countFiltrado(LocalDate desde, LocalDate hasta,
                              String query, String tipo, String categoriaNombre) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Movimiento> all = local.findMovimientosByDateRange(desde, hasta, accessible);
            return applyClientFilters(all, query, tipo, categoriaNombre).size();
        }
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(desde, hasta, query, tipo, categoriaNombre, params, accessible);
        String sql = "SELECT COUNT(*) FROM movements m "
            + "JOIN products p ON p.id = m.producto_id "
            + "LEFT JOIN categories c ON c.id = p.categoria_id"
            + where;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Client-side filter for offline / demo mode fallback.  Category is
     *  skipped because Movimiento doesn't carry the category name in local
     *  store — offline mode can tolerate this. */
    private List<Movimiento> applyClientFilters(List<Movimiento> all,
                                                String query, String tipo,
                                                String categoriaNombre) {
        if (categoriaNombre != null) {
            log.warn("applyClientFilters: categoria filter not supported in offline mode, ignored");
        }
        return all.stream()
            .filter(m -> query == null || query.isBlank()
                || (m.getProductoNombre() != null && m.getProductoNombre().toLowerCase().contains(query.toLowerCase()))
                || (m.getMotivo() != null && m.getMotivo().toLowerCase().contains(query.toLowerCase()))
                || (m.getReferencia() != null && m.getReferencia().toLowerCase().contains(query.toLowerCase())))
            .filter(m -> tipo == null || "Todos".equals(tipo)
                || m.getTipo().getEtiqueta().equals(tipo))
            .toList();
    }

    /** Aggregate stats (total, entradas, salidas, ajustes+transferencias)
     *  for the given date range, scoped to the current user's areas. */
    public MovimientoStats findStats(LocalDate desde, LocalDate hasta) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            List<Movimiento> all = local.findMovimientosByDateRange(desde, hasta, accessible);
            long total    = all.size();
            long entradas = all.stream().filter(m -> m.getTipo() == TipoMovimiento.ENTRADA).count();
            long salidas  = all.stream().filter(m -> m.getTipo() == TipoMovimiento.SALIDA).count();
            long ajustes  = all.stream().filter(m ->
                m.getTipo() == TipoMovimiento.AJUSTE || m.getTipo() == TipoMovimiento.TRANSFERENCIA).count();
            return new MovimientoStats(total, entradas, salidas, ajustes);
        }
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(desde, hasta, null, null, null, params, accessible);
        String sql = """
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE m.tipo = 'entrada') AS entradas,
                COUNT(*) FILTER (WHERE m.tipo = 'salida')  AS salidas,
                COUNT(*) FILTER (WHERE m.tipo IN ('ajuste','transferencia')) AS ajustes
            FROM movements m
            JOIN products p ON p.id = m.producto_id
            LEFT JOIN categories c ON c.id = p.categoria_id
            """ + where;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MovimientoStats(
                        rs.getLong("total"), rs.getLong("entradas"),
                        rs.getLong("salidas"), rs.getLong("ajustes"));
                }
            }
        }
        return new MovimientoStats(0, 0, 0, 0);
    }

    /** Distinct category names present in movements for the given date range,
     *  sorted alphabetically — used to populate the Categoría filter dropdown. */
    public List<String> findDistinctCategorias(LocalDate desde, LocalDate hasta) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) return List.of(); // caller prepends null for "Todas"
        List<Object> params = new ArrayList<>();
        String where = buildFiltroWhere(desde, hasta, null, null, null, params, accessible);
        String sql = """
            SELECT DISTINCT c.nombre
            FROM categories c
            JOIN products p ON c.id = p.categoria_id
            JOIN movements m ON m.producto_id = p.id
            """ + where + " ORDER BY c.nombre";
        List<String> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nombre = rs.getString(1);
                    if (nombre != null) result.add(nombre);
                }
            }
        }
        return result;
    }

    public long countByAnio(int anio) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return local.findAllMovimientos(accessible).stream()
                .filter(m -> m.getCreadoEn() != null && m.getCreadoEn().getYear() == anio)
                .count();
        }
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM movements m JOIN products p ON p.id = m.producto_id"
            + " WHERE EXTRACT(YEAR FROM m.created_at) = ?");
        if (accessible != null) sql.append(" AND p.area = ANY(?)");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, anio);
            if (accessible != null)
                ps.setArray(2, conn.createArrayOf("text", accessible.toArray(new String[0])));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    public List<Movimiento> findToday() throws SQLException {
        return findByDateRange(LocalDate.now(), LocalDate.now());
    }

    public List<Movimiento> findLastNDays(int days) throws SQLException {
        return findByDateRange(LocalDate.now().minusDays(days - 1), LocalDate.now());
    }

    /**
     * Atomically inserts the movement and updates product stock (and, for a
     * transferencia, the product's area) in a single transaction.
     *
     * The caller (MovimientoService) supplies {@code stockAnterior} as a
     * best-effort value for its own pre-flight validation UX, but it is NOT
     * trusted here — two users registering movements on the same product at
     * nearly the same time would otherwise both read the same stale stock,
     * compute the same "new" value independently, and the second UPDATE
     * would silently overwrite the first (a lost update). Instead this locks
     * the product row with {@code SELECT ... FOR UPDATE} and recomputes
     * stockAnterior/stockNuevo from that locked, DB-fresh value, so
     * concurrent registrations serialize correctly instead of racing.
     */
    public Movimiento addMovimientoAtomic(Movimiento m) throws SQLException {
        return addMovimientoAtomic(m, null);
    }

    /**
     * @param expectedStockAnterior when non-null, the DB-fresh stock (read
     *        under the same lock below) must match this value or the whole
     *        movement is rejected instead of applied. AJUSTE's "cantidad" is
     *        an absolute new value, not a delta — unlike ENTRADA/SALIDA, the
     *        lock alone doesn't protect against overwriting a change that
     *        happened after this movement's expected stock was captured
     *        (e.g. a conteo físico session captures stock when the dialog
     *        opens, but the user can take minutes to finish reviewing every
     *        item — if another movement lands on this product meanwhile, a
     *        blind AJUSTE would silently erase its effect). Callers that
     *        aren't reconciling against a possibly-stale snapshot (the
     *        normal "Registrar Movimiento" flow) should keep using the
     *        single-argument overload.
     */
    public Movimiento addMovimientoAtomic(Movimiento m, Integer expectedStockAnterior) throws SQLException {
        if (m.getId() == null) m.setId(UUID.randomUUID().toString());
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            if (m.getCreadoEn() == null) m.setCreadoEn(LocalDateTime.now());
            local.addMovimiento(m, expectedStockAnterior);
            return m;
        }
        return addMovimientoAtomicOnline(m, expectedStockAnterior);
    }

    /** Replay target for SyncService — see ProductoRepository#saveOnline.
     *  Deliberately reused as-is for replay too: re-locking and
     *  recalculating stock against Postgres's CURRENT value at sync time
     *  (not the possibly-stale value captured while offline) is exactly the
     *  correct behavior for a deferred write, the same way it already is for
     *  two concurrent online users. */
    public Movimiento addMovimientoAtomicOnline(Movimiento m, Integer expectedStockAnterior) throws SQLException {
        if (m.getId() == null) m.setId(UUID.randomUUID().toString());
        String lockProducto = "SELECT stock_actual, area, codigo FROM products WHERE id = ? FOR UPDATE";
        String insertMov = """
            INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo,
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at, estado,
                codigo_anterior, codigo_nuevo)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        String updateProducto = "UPDATE products SET stock_actual = ?, area = ?, codigo = ?, updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int stockActual;
                String areaActual;
                String codigoActual;
                try (PreparedStatement ps = conn.prepareStatement(lockProducto)) {
                    ps.setString(1, m.getProductoId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Producto no encontrado: " + m.getProductoId());
                        stockActual = rs.getInt("stock_actual");
                        areaActual = rs.getString("area");
                        codigoActual = rs.getString("codigo");
                    }
                }

                if (expectedStockAnterior != null && stockActual != expectedStockAnterior) {
                    throw new SQLException("El stock cambió desde que se capturó el conteo (esperado "
                        + expectedStockAnterior + ", actual " + stockActual + ") — no se aplicó el ajuste.");
                }

                if (m.getTipo() == TipoMovimiento.SALIDA && m.getCantidad() > stockActual) {
                    throw new SQLException("La cantidad supera el stock disponible (" + stockActual + ")");
                }

                int stockNuevo = ProductoUtils.calcularStockNuevo(m.getTipo().getCodigo(), stockActual, m.getCantidad());
                m.setStockAnterior(stockActual);
                m.setStockNuevo(stockNuevo);

                boolean esTransferencia = m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null;
                String areaNueva = esTransferencia ? m.getAreaDestino() : areaActual;
                if (esTransferencia) m.setAreaOrigen(areaActual);
                // Reasigna el código de nomenclatura por área en cada transferencia —
                // el número que deja libre en el área de origen queda disponible para
                // el siguiente bien nuevo ahí (ver com.sibim.config.AreaCodigos).
                String codigoNuevo = (esTransferencia && AreaCodigos.tienePrefijo(areaNueva))
                    ? siguienteCodigo(conn, areaNueva) : codigoActual;
                // Both códigos stay on the movement: labels, resguardos and actas
                // printed with the old number remain traceable after it's reused.
                if (esTransferencia) {
                    m.setCodigoAnterior(codigoActual);
                    m.setCodigoNuevo(codigoNuevo);
                }

                try (PreparedStatement ps = conn.prepareStatement(insertMov)) {
                    ps.setString(1, m.getId());
                    ps.setString(2, m.getProductoId());
                    ps.setString(3, m.getTipo().getCodigo());
                    ps.setInt(4, m.getCantidad());
                    ps.setInt(5, m.getStockAnterior());
                    ps.setInt(6, m.getStockNuevo());
                    ps.setString(7, m.getAreaOrigen());
                    ps.setString(8, m.getAreaDestino());
                    ps.setString(9, m.getMotivo());
                    ps.setString(10, m.getReferencia());
                    ps.setString(11, m.getUsuarioId());
                    ps.setString(12, m.getUsuarioNombre());
                    ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(14, Movimiento.ESTADO_APROBADO);
                    ps.setString(15, m.getCodigoAnterior());
                    ps.setString(16, m.getCodigoNuevo());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(updateProducto)) {
                    ps.setInt(1, m.getStockNuevo());
                    ps.setString(2, areaNueva);
                    ps.setString(3, codigoNuevo);
                    ps.setString(4, m.getProductoId());
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
        return m;
    }

    private static String siguienteCodigo(Connection conn, String area) throws SQLException {
        return ProductoRepository.siguienteCodigo(conn, area);
    }

    /**
     * Atomically deletes the movement and restores product stock — and, if
     * it was a transferencia, moves the product's area back to where it
     * came from (area_origen), since the transfer's whole effect was
     * relocating it, not changing stock.
     *
     * Reverses this movement's own effect by delta (stock_anterior -
     * stock_nuevo) applied against the product's CURRENT stock, instead of
     * blindly restoring stock_actual to this movement's stock_anterior.
     * Delta reversal composes correctly regardless of insertion order —
     * unlike a snapshot restore, which is only correct when deleting the
     * single most recent movement for the product (deleting an older one
     * would otherwise discard every movement registered after it).
     */
    public void deleteMovimientoAtomic(String movimientoId) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            local.deleteMovimiento(movimientoId);
            return;
        }
        deleteMovimientoAtomicOnline(movimientoId);
    }

    /** Replay target for SyncService — see ProductoRepository#saveOnline.
     *
     *  Only the most recent applied movement of a product may be deleted —
     *  the same rule OfflineStore and DemoDataStore enforce. Stock is
     *  reversed by delta, but área is restored by snapshot (area_origen):
     *  deleting an older transferencia would send the bien back to where it
     *  was before it, even though later transfers have moved it on since.
     *  Pending/rejected movements never touched the bien, so deleting one
     *  leaves stock, área and código as they are. */
    public void deleteMovimientoAtomicOnline(String movimientoId) throws SQLException {
        String getMov = "SELECT producto_id, stock_anterior, stock_nuevo, area_origen, estado, created_at "
            + "FROM movements WHERE id = ?";
        String lockProduct = "SELECT stock_actual FROM products WHERE id = ? FOR UPDATE";
        String newerMov = "SELECT 1 FROM movements WHERE producto_id = ? AND id <> ? AND created_at > ? "
            + "AND COALESCE(estado, 'APROBADO') = 'APROBADO' LIMIT 1";
        String deleteMov = "DELETE FROM movements WHERE id = ?";
        String restoreProducto = "UPDATE products SET stock_actual = ?, area = COALESCE(?, area), " +
            "codigo = COALESCE(?, codigo), updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String productoId;
                int delta;
                String areaOrigen;
                boolean aplicado;
                Timestamp creado;
                try (PreparedStatement ps = conn.prepareStatement(getMov)) {
                    ps.setString(1, movimientoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Movimiento no encontrado: " + movimientoId);
                        productoId = rs.getString("producto_id");
                        String estado = rs.getString("estado");
                        aplicado = estado == null || Movimiento.ESTADO_APROBADO.equals(estado);
                        delta = aplicado ? rs.getInt("stock_anterior") - rs.getInt("stock_nuevo") : 0;
                        areaOrigen = aplicado ? rs.getString("area_origen") : null;
                        creado = rs.getTimestamp("created_at");
                    }
                }
                int currentStock;
                try (PreparedStatement ps = conn.prepareStatement(lockProduct)) {
                    ps.setString(1, productoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        currentStock = rs.next() ? rs.getInt("stock_actual") : 0;
                    }
                }
                if (aplicado && creado != null) {
                    try (PreparedStatement ps = conn.prepareStatement(newerMov)) {
                        ps.setString(1, productoId);
                        ps.setString(2, movimientoId);
                        ps.setTimestamp(3, creado);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) throw new SQLException(
                                "Solo se puede eliminar el movimiento mas reciente de este producto: "
                                + "existen movimientos registrados despues de este.");
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteMov)) {
                    ps.setString(1, movimientoId);
                    ps.executeUpdate();
                }
                // areaOrigen is only set when the deleted movement was a
                // transferencia — recompute a fresh código for that área the
                // same way as everywhere else, since its old número there may
                // have since been claimed by a different bien.
                String codigoRestaurado = (areaOrigen != null && AreaCodigos.tienePrefijo(areaOrigen))
                    ? siguienteCodigo(conn, areaOrigen) : null;
                try (PreparedStatement ps = conn.prepareStatement(restoreProducto)) {
                    ps.setInt(1, currentStock + delta);
                    ps.setString(2, areaOrigen);
                    ps.setString(3, codigoRestaurado);
                    ps.setString(4, productoId);
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

    private List<Movimiento> queryDynamic(String sql, List<Object> params) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, conn, params);
            return executeQuery(ps);
        }
    }

    private static void bindParams(PreparedStatement ps, Connection conn,
                                   List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof String[] arr) ps.setArray(i + 1, conn.createArrayOf("text", arr));
            else ps.setObject(i + 1, p);
        }
    }

    private List<Movimiento> executeQuery(PreparedStatement ps) throws SQLException {
        List<Movimiento> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /**
     * Saves a TRANSFERENCIA as PENDIENTE without modifying product stock or area.
     * The admin must approve it later via {@link #aprobarTransferencia}.
     */
    public Movimiento addMovimientoPendiente(Movimiento m) throws SQLException {
        if (m.getId() == null) m.setId(UUID.randomUUID().toString());
        m.setEstado(Movimiento.ESTADO_PENDIENTE);
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.addMovimientoPendiente(m);
            return m;
        }
        if (DatabaseConfig.isDemoMode()) {
            if (m.getCreadoEn() == null) m.setCreadoEn(LocalDateTime.now());
            DemoDataStore.addMovimientoPendiente(m);
            return m;
        }
        String lockProducto = "SELECT stock_actual, area FROM products WHERE id = ? FOR UPDATE";
        String insertMov = """
            INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo,
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at, estado)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int stockActual; String areaActual;
                try (PreparedStatement ps = conn.prepareStatement(lockProducto)) {
                    ps.setString(1, m.getProductoId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Producto no encontrado: " + m.getProductoId());
                        stockActual = rs.getInt("stock_actual");
                        areaActual  = rs.getString("area");
                    }
                }
                m.setStockAnterior(stockActual);
                m.setStockNuevo(stockActual); // unchanged until approved
                m.setAreaOrigen(areaActual);
                try (PreparedStatement ps = conn.prepareStatement(insertMov)) {
                    ps.setString(1, m.getId());
                    ps.setString(2, m.getProductoId());
                    ps.setString(3, m.getTipo().getCodigo());
                    ps.setInt(4, m.getCantidad());
                    ps.setInt(5, m.getStockAnterior());
                    ps.setInt(6, m.getStockNuevo());
                    ps.setString(7, m.getAreaOrigen());
                    ps.setString(8, m.getAreaDestino());
                    ps.setString(9, m.getMotivo());
                    ps.setString(10, m.getReferencia());
                    ps.setString(11, m.getUsuarioId());
                    ps.setString(12, m.getUsuarioNombre());
                    ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(14, Movimiento.ESTADO_PENDIENTE);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        }
        return m;
    }

    public List<Movimiento> findPendientesTransferencias() throws SQLException {
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findPendientesTransferencias();
        String sql = BASE_SELECT +
            " WHERE m.tipo = 'TRANSFERENCIA' AND m.estado = 'PENDIENTE' ORDER BY m.created_at ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return executeQuery(ps);
        }
    }

    /** Returns {productoId, areaDestino} for a PENDIENTE transfer, or empty if not found. */
    public Optional<String[]> findTransferenciaInfo(String movimientoId) throws SQLException {
        LocalDataStore local = DatabaseConfig.getLocalDataStore();
        if (local != null) {
            return local.findAllMovimientos(null).stream()
                .filter(m -> movimientoId.equals(m.getId()) && m.isPendiente())
                .map(m -> new String[]{ m.getProductoId(), m.getAreaDestino() })
                .findFirst();
        }
        String sql = "SELECT producto_id, area_destino FROM movements WHERE id = ? AND estado = 'PENDIENTE'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movimientoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new String[]{ rs.getString("producto_id"), rs.getString("area_destino") });
            }
        }
    }

    /** Applies a pending transfer in one transaction: moves the bien to
     *  area_destino, gives it the next free código there (same rule as a
     *  direct admin transfer, see addMovimientoAtomicOnline) and marks the
     *  movement APROBADO with both códigos on record. Refuses if the bien is
     *  no longer in the área the request was made from — approving it would
     *  otherwise pull it out of wherever it was moved to in the meantime. */
    public void aprobarTransferencia(String movimientoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) { DemoDataStore.aprobarTransferencia(movimientoId); return; }
        String getMov   = "SELECT producto_id, area_origen, area_destino FROM movements "
            + "WHERE id = ? AND estado = 'PENDIENTE' FOR UPDATE";
        String lockProd = "SELECT area, codigo, stock_actual FROM products WHERE id = ? FOR UPDATE";
        String updProd  = "UPDATE products SET area = ?, codigo = ?, updated_at = NOW() WHERE id = ?";
        String updMov   = "UPDATE movements SET estado = 'APROBADO', stock_anterior = ?, stock_nuevo = ?, "
            + "codigo_anterior = ?, codigo_nuevo = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String productoId, areaOrigen, areaDestino;
                try (PreparedStatement ps = conn.prepareStatement(getMov)) {
                    ps.setString(1, movimientoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Transferencia pendiente no encontrada: " + movimientoId);
                        productoId  = rs.getString("producto_id");
                        areaOrigen  = rs.getString("area_origen");
                        areaDestino = rs.getString("area_destino");
                    }
                }
                String areaActual, codigoActual;
                int stock;
                try (PreparedStatement ps = conn.prepareStatement(lockProd)) {
                    ps.setString(1, productoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Producto no encontrado: " + productoId);
                        areaActual   = rs.getString("area");
                        codigoActual = rs.getString("codigo");
                        stock        = rs.getInt("stock_actual");
                    }
                }
                if (areaOrigen != null && !areaOrigen.equals(areaActual)) {
                    throw new SQLException("El bien ya no está en " + areaOrigen + " (ahora está en "
                        + areaActual + "): rechaza esta solicitud y registra una nueva si sigue siendo necesaria.");
                }
                String codigoNuevo = AreaCodigos.tienePrefijo(areaDestino)
                    ? siguienteCodigo(conn, areaDestino) : codigoActual;
                try (PreparedStatement ps = conn.prepareStatement(updProd)) {
                    ps.setString(1, areaDestino); ps.setString(2, codigoNuevo); ps.setString(3, productoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(updMov)) {
                    ps.setInt(1, stock);
                    ps.setInt(2, stock);
                    ps.setString(3, codigoActual);
                    ps.setString(4, codigoNuevo);
                    ps.setString(5, movimientoId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        }
    }

    public void rechazarTransferencia(String movimientoId) throws SQLException {
        rechazarTransferencia(movimientoId, null);
    }

    public void rechazarTransferencia(String movimientoId, String motivo) throws SQLException {
        if (DatabaseConfig.isDemoMode()) { DemoDataStore.rechazarTransferencia(movimientoId); return; }
        String sql = motivo != null && !motivo.isBlank()
            ? "UPDATE movements SET estado = 'RECHAZADO', motivo = ? WHERE id = ? AND estado = 'PENDIENTE'"
            : "UPDATE movements SET estado = 'RECHAZADO' WHERE id = ? AND estado = 'PENDIENTE'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (motivo != null && !motivo.isBlank()) {
                ps.setString(1, motivo);
                ps.setString(2, movimientoId);
            } else {
                ps.setString(1, movimientoId);
            }
            ps.executeUpdate();
        }
    }

    private Movimiento mapRow(ResultSet rs) throws SQLException {
        Movimiento m = new Movimiento();
        m.setId(rs.getString("id"));
        m.setProductoId(rs.getString("producto_id"));
        m.setProductoNombre(rs.getString("producto_nombre"));
        m.setCategoriaColor(rs.getString("categoria_color"));
        m.setTipo(TipoMovimiento.fromCodigo(rs.getString("tipo")));
        m.setCantidad(rs.getInt("cantidad"));
        m.setStockAnterior(rs.getInt("stock_anterior"));
        m.setStockNuevo(rs.getInt("stock_nuevo"));
        m.setAreaOrigen(rs.getString("area_origen"));
        m.setAreaDestino(rs.getString("area_destino"));
        m.setMotivo(rs.getString("motivo"));
        m.setReferencia(rs.getString("referencia"));
        m.setUsuarioId(rs.getString("usuario_id"));
        m.setUsuarioNombre(rs.getString("usuario_nombre"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) m.setCreadoEn(ts.toLocalDateTime());
        try {
            m.setEstado(rs.getString("estado"));
        } catch (SQLException e) {
            // Column added in V8 migration — absent in queries that pre-date the JOIN
            if (!e.getMessage().contains("estado")) throw e;
        }
        m.setCodigoAnterior(columnaOpcional(rs, "codigo_anterior"));
        m.setCodigoNuevo(columnaOpcional(rs, "codigo_nuevo"));
        return m;
    }

    /** Columns added by V22 — absent from queries with an explicit column list. */
    private static String columnaOpcional(ResultSet rs, String columna) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (columna.equalsIgnoreCase(meta.getColumnLabel(i))) return rs.getString(i);
        }
        return null;
    }
}
