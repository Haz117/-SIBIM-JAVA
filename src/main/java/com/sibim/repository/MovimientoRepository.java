package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.LocalDataStore;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Movimiento;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.session.SessionManager;
import com.sibim.util.ProductoUtils;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MovimientoRepository {

    private static final Logger log = LoggerFactory.getLogger(MovimientoRepository.class);

    /** Aggregate stats for the movement list view — mirrors ProductoRepository.InventarioStats. */
    public record MovimientoStats(long total, long entradas, long salidas, long ajustes) {}

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
        if (hasta != null) { conditions.add("m.created_at <= ?"); params.add(Timestamp.valueOf(hasta.atTime(23, 59, 59))); }
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
            params.add(Timestamp.valueOf(hasta.atTime(23, 59, 59)));
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
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr) ps.setArray(i + 1, conn.createArrayOf("text", arr));
                else ps.setObject(i + 1, p);
            }
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
        StringBuilder sb = new StringBuilder("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE m.tipo = 'entrada') AS entradas,
                COUNT(*) FILTER (WHERE m.tipo = 'salida')  AS salidas,
                COUNT(*) FILTER (WHERE m.tipo IN ('ajuste','transferencia')) AS ajustes
            FROM movements m
            JOIN products p ON p.id = m.producto_id
            LEFT JOIN categories c ON c.id = p.categoria_id
            """);
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
            params.add(Timestamp.valueOf(hasta.atTime(23, 59, 59)));
        }
        if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr) ps.setArray(i + 1, conn.createArrayOf("text", arr));
                else ps.setObject(i + 1, p);
            }
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
        StringBuilder sb = new StringBuilder("""
            SELECT DISTINCT c.nombre
            FROM categories c
            JOIN products p ON c.id = p.categoria_id
            JOIN movements m ON m.producto_id = p.id
            """);
        List<String> conditions = new ArrayList<>();
        if (desde != null) {
            conditions.add("m.created_at >= ?");
            params.add(Timestamp.valueOf(desde.atStartOfDay()));
        }
        if (hasta != null) {
            conditions.add("m.created_at <= ?");
            params.add(Timestamp.valueOf(hasta.atTime(23, 59, 59)));
        }
        if (accessible != null) {
            conditions.add("p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        if (!conditions.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", conditions));
        sb.append(" ORDER BY c.nombre");
        List<String> result = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr) ps.setArray(i + 1, conn.createArrayOf("text", arr));
                else ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String nombre = rs.getString(1);
                    if (nombre != null) result.add(nombre);
                }
            }
        }
        return result;
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
            if (m.getCreadoEn() == null) m.setCreadoEn(java.time.LocalDateTime.now());
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
        String lockProducto = "SELECT stock_actual, area FROM products WHERE id = ? FOR UPDATE";
        String insertMov = """
            INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo,
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at, estado)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        String updateProducto = "UPDATE products SET stock_actual = ?, area = ?, updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int stockActual;
                String areaActual;
                try (PreparedStatement ps = conn.prepareStatement(lockProducto)) {
                    ps.setString(1, m.getProductoId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Producto no encontrado: " + m.getProductoId());
                        stockActual = rs.getInt("stock_actual");
                        areaActual = rs.getString("area");
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
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(updateProducto)) {
                    ps.setInt(1, m.getStockNuevo());
                    ps.setString(2, areaNueva);
                    ps.setString(3, m.getProductoId());
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

    /** Replay target for SyncService — see ProductoRepository#saveOnline. */
    public void deleteMovimientoAtomicOnline(String movimientoId) throws SQLException {
        String getMov = "SELECT producto_id, stock_anterior, stock_nuevo, area_origen FROM movements WHERE id = ?";
        String lockProduct = "SELECT stock_actual FROM products WHERE id = ? FOR UPDATE";
        String deleteMov = "DELETE FROM movements WHERE id = ?";
        String restoreProducto = "UPDATE products SET stock_actual = ?, area = COALESCE(?, area), updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String productoId;
                int delta;
                String areaOrigen;
                try (PreparedStatement ps = conn.prepareStatement(getMov)) {
                    ps.setString(1, movimientoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Movimiento no encontrado: " + movimientoId);
                        productoId = rs.getString("producto_id");
                        delta = rs.getInt("stock_anterior") - rs.getInt("stock_nuevo");
                        areaOrigen = rs.getString("area_origen");
                    }
                }
                int currentStock;
                try (PreparedStatement ps = conn.prepareStatement(lockProduct)) {
                    ps.setString(1, productoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        currentStock = rs.next() ? rs.getInt("stock_actual") : 0;
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteMov)) {
                    ps.setString(1, movimientoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(restoreProducto)) {
                    ps.setInt(1, currentStock + delta);
                    ps.setString(2, areaOrigen);
                    ps.setString(3, productoId);
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
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String[] arr) {
                    ps.setArray(i + 1, conn.createArrayOf("text", arr));
                } else {
                    ps.setObject(i + 1, p);
                }
            }
            return executeQuery(ps);
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

    /** Applies a pending transfer: updates product area → area_destino, marks movement APROBADO. */
    public void aprobarTransferencia(String movimientoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) { DemoDataStore.aprobarTransferencia(movimientoId); return; }
        String getMov   = "SELECT producto_id, area_destino FROM movements WHERE id = ? AND estado = 'PENDIENTE'";
        String lockProd = "SELECT area FROM products WHERE id = ? FOR UPDATE";
        String updProd  = "UPDATE products SET area = ?, updated_at = NOW() WHERE id = ?";
        String updMov   = "UPDATE movements SET estado = 'APROBADO' WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String productoId, areaDestino;
                try (PreparedStatement ps = conn.prepareStatement(getMov)) {
                    ps.setString(1, movimientoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Transferencia pendiente no encontrada: " + movimientoId);
                        productoId  = rs.getString("producto_id");
                        areaDestino = rs.getString("area_destino");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(lockProd)) {
                    ps.setString(1, productoId); ps.executeQuery();
                }
                try (PreparedStatement ps = conn.prepareStatement(updProd)) {
                    ps.setString(1, areaDestino); ps.setString(2, productoId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(updMov)) {
                    ps.setString(1, movimientoId); ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) { conn.rollback(); throw e; }
            finally { conn.setAutoCommit(true); }
        }
    }

    public void rechazarTransferencia(String movimientoId) throws SQLException {
        if (DatabaseConfig.isDemoMode()) { DemoDataStore.rechazarTransferencia(movimientoId); return; }
        String sql = "UPDATE movements SET estado = 'RECHAZADO' WHERE id = ? AND estado = 'PENDIENTE'";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movimientoId); ps.executeUpdate();
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
        try { m.setEstado(rs.getString("estado")); } catch (SQLException ignored) {}
        return m;
    }
}
