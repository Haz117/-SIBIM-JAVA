package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.db.offline.OfflineStore;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
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

public class MovimientoRepository {

    private static final String BASE_SELECT = """
        SELECT m.*, p.nombre AS producto_nombre, c.color AS categoria_color
        FROM movements m
        JOIN products p ON p.id = m.producto_id
        LEFT JOIN categories c ON c.id = p.categoria_id
        """;

    public List<Movimiento> findAll() throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findAllMovimientos(accessible);
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findAllMovimientos(accessible);
        StringBuilder sb = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();
        if (accessible != null) {
            sb.append(" WHERE p.area = ANY(?)");
            params.add(accessible.toArray(new String[0]));
        }
        sb.append(" ORDER BY m.created_at DESC");
        return queryDynamic(sb.toString(), params);
    }

    /** Used for authorization checks before deleting a movement — doesn't
     *  apply the caller's own area filter, since the caller needs to know
     *  the product's area precisely to decide whether they're allowed to. */
    public Optional<String> findProductoIdById(String movimientoId) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findProductoIdByMovimientoId(movimientoId);
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findProductoIdByMovimientoId(movimientoId);
        String sql = "SELECT producto_id FROM movements WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movimientoId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString("producto_id")) : Optional.empty();
            }
        }
    }

    public List<Movimiento> findByProducto(String productoId) throws SQLException {
        Set<String> accessible = SessionManager.getAccessibleAreas();
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findMovimientosByProducto(productoId, accessible);
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findMovimientosByProducto(productoId, accessible);
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
        if (DatabaseConfig.isOfflineMode()) return OfflineStore.findMovimientosByDateRange(desde, hasta, accessible);
        if (DatabaseConfig.isDemoMode()) return DemoDataStore.findMovimientosByDateRange(desde, hasta, accessible);
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
        return queryDynamic(sb.toString(), params);
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
        if (DatabaseConfig.isOfflineMode()) {
            if (m.getCreadoEn() == null) m.setCreadoEn(java.time.LocalDateTime.now());
            OfflineStore.addMovimiento(m, expectedStockAnterior);
            return m;
        }
        if (DatabaseConfig.isDemoMode()) {
            if (m.getCreadoEn() == null) m.setCreadoEn(java.time.LocalDateTime.now());
            DemoDataStore.addMovimiento(m, expectedStockAnterior);
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
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
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
     * Restoring stock this way (setting stock_actual back to this
     * movement's stock_anterior) is only correct if this is the MOST
     * RECENT movement for the product — otherwise it would discard the
     * effect of movements registered after it. That is enforced here,
     * under the product row lock, so a concurrent insert can't race past
     * the check.
     */
    public void deleteMovimientoAtomic(String movimientoId) throws SQLException {
        if (DatabaseConfig.isOfflineMode()) {
            OfflineStore.deleteMovimiento(movimientoId);
            return;
        }
        if (DatabaseConfig.isDemoMode()) {
            DemoDataStore.deleteMovimiento(movimientoId);
            return;
        }
        deleteMovimientoAtomicOnline(movimientoId);
    }

    /** Replay target for SyncService — see ProductoRepository#saveOnline. */
    public void deleteMovimientoAtomicOnline(String movimientoId) throws SQLException {
        String getMov = "SELECT producto_id, stock_anterior, area_origen FROM movements WHERE id = ? FOR UPDATE";
        String lockProducto = "SELECT id FROM products WHERE id = ? FOR UPDATE";
        String getLatestMov = "SELECT id FROM movements WHERE producto_id = ? ORDER BY created_at DESC, id DESC LIMIT 1";
        String deleteMov = "DELETE FROM movements WHERE id = ?";
        String restoreProducto = "UPDATE products SET stock_actual = ?, area = COALESCE(?, area), updated_at = NOW() WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String productoId;
                int stockAnterior;
                String areaOrigen;
                try (PreparedStatement ps = conn.prepareStatement(getMov)) {
                    ps.setString(1, movimientoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Movimiento no encontrado: " + movimientoId);
                        productoId = rs.getString("producto_id");
                        stockAnterior = rs.getInt("stock_anterior");
                        areaOrigen = rs.getString("area_origen");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(lockProducto)) {
                    ps.setString(1, productoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Producto no encontrado: " + productoId);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(getLatestMov)) {
                    ps.setString(1, productoId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && !movimientoId.equals(rs.getString("id"))) {
                            throw new SQLException(
                                "Solo se puede eliminar el movimiento mas reciente de este producto: "
                                + "existen movimientos registrados despues de este.");
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteMov)) {
                    ps.setString(1, movimientoId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(restoreProducto)) {
                    ps.setInt(1, stockAnterior);
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
        return m;
    }
}
