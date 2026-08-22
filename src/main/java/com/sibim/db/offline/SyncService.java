package com.sibim.db.offline;

import com.sibim.controller.MainController;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Categoria;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.CategoriaRepository;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watches for the real Postgres connection coming back while the app is in
 * modo offline, and — once it does — replays everything queued in
 * OfflineStore's outbox tables against it, in dependency order (categories,
 * then products, then movements), using each repository's *Online(...)
 * method directly (bypassing the isOfflineMode()/isDemoMode() branch),
 * exactly as described in the offline-mode plan.
 */
public final class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final long POLL_SECONDS = 60;

    private static ScheduledExecutorService executor;

    private SyncService() {}

    public static synchronized void startWatching() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sibim-sync");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(SyncService::tick, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
        log.info("SyncService: vigilando reconexión cada {}s", POLL_SECONDS);
    }

    public static synchronized void stopWatching() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Manual trigger (e.g. a "Sincronizar ahora" button) — same logic the
     *  scheduled poll runs, just on demand. */
    public static void syncNow() {
        tick();
    }

    private static void tick() {
        if (!DatabaseConfig.isOfflineMode()) return;
        if (!postgresReachable()) return;

        log.info("SyncService: conexión recuperada, sincronizando cambios pendientes...");
        AtomicInteger synced     = new AtomicInteger();
        AtomicInteger failed     = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        try {
            syncCategorias(synced, failed);
            syncProductos(synced, failed, conflicted);
            syncMovimientos(synced, failed);
        } catch (Exception e) {
            log.error("SyncService: fallo inesperado durante la sincronización", e);
            return;
        }

        int pending = countPending();
        if (pending == 0) {
            DatabaseConfig.setOfflineMode(false);
            log.info("SyncService: sincronización completa ({} cambio(s) aplicados). Volviendo a modo online.", synced.get());
        } else {
            log.warn("SyncService: quedaron {} cambio(s) sin poder sincronizar — requieren revisión manual.", pending);
        }
        notifyUi(synced.get(), failed.get(), conflicted.get(), pending);
    }

    private static boolean postgresReachable() {
        try (Connection ignored = DatabaseConfig.getConnection()) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void notifyUi(int synced, int failed, int conflicted, int stillPending) {
        Platform.runLater(() -> {
            MainController mc = MainController.getInstance();
            var scene = mc != null ? mc.getContentAreaScene() : null;
            if (synced > 0) {
                String msg = "Se sincronizaron " + synced + " cambio(s) con el servidor.";
                if (scene != null) NotificacionUtil.exito(scene, msg);
            }
            if (conflicted > 0 && scene != null) {
                NotificacionUtil.advertencia(scene,
                    conflicted + " bien(es) no se sincronizaron: otro equipo los modificó "
                    + "mientras estabas sin conexión. Revísalos en Bienes y vuelve a guardar.");
            }
            if (failed > 0 && scene != null) {
                NotificacionUtil.advertencia(scene,
                    failed + " cambio(s) no se pudieron sincronizar y requieren revisión manual.");
            }
            if (stillPending == 0 && mc != null) {
                mc.refreshCurrentViewAfterSync();
            }
        });
    }

    // ─────────────────────────────── Categorías ───────────────────────────

    private record CategoryRow(int id, String operacion, String categoriaId, String nombre,
                                String descripcion, String color, String icono) {}

    private static void syncCategorias(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<CategoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM category_outbox WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new CategoryRow(rs.getInt("id"), rs.getString("operacion"), rs.getString("categoria_id"),
                    rs.getString("nombre"), rs.getString("descripcion"), rs.getString("color"), rs.getString("icono")));
            }
        }
        CategoriaRepository repo = new CategoriaRepository();
        for (CategoryRow r : rows) {
            try {
                if ("DELETE".equals(r.operacion())) {
                    repo.deleteOnline(r.categoriaId());
                } else {
                    Categoria c = new Categoria();
                    c.setId(r.categoriaId());
                    c.setNombre(r.nombre());
                    c.setDescripcion(r.descripcion());
                    c.setColor(r.color());
                    c.setIcono(r.icono());
                    repo.saveOnline(c);
                }
                markOutbox("category_outbox", r.id(), "SYNCED", null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar categoría {} ({})", r.categoriaId(), r.operacion(), ex);
                markOutbox("category_outbox", r.id(), "FAILED", ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    // ─────────────────────────────── Productos ─────────────────────────────

    private record ProductRow(int id, String operacion, String productoId, String nombre, String codigo,
                               String descripcion, String categoriaId, String precioCompra, String precioVenta,
                               int stockActual, int stockMinimo, int stockMaximo, String unidad, String proveedor,
                               String fechaVencimiento, String fotoUrl, String ubicacion, String area,
                               String resguardante, String motivoBaja, String serverSnapshotAt) {}

    private static void syncProductos(AtomicInteger synced, AtomicInteger failed, AtomicInteger conflicted)
            throws SQLException {
        List<ProductRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM product_outbox WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new ProductRow(rs.getInt("id"), rs.getString("operacion"), rs.getString("producto_id"),
                    rs.getString("nombre"), rs.getString("codigo"), rs.getString("descripcion"),
                    rs.getString("categoria_id"), rs.getString("precio_compra"), rs.getString("precio_venta"),
                    rs.getInt("stock_actual"), rs.getInt("stock_minimo"), rs.getInt("stock_maximo"),
                    rs.getString("unidad"), rs.getString("proveedor"), rs.getString("fecha_vencimiento"),
                    rs.getString("foto_url"), rs.getString("ubicacion"), rs.getString("area"),
                    rs.getString("resguardante"), rs.getString("motivo_baja"),
                    rs.getString("server_snapshot_at")));
            }
        }
        ProductoRepository repo = new ProductoRepository();
        for (ProductRow r : rows) {
            try {
                // Conflict detection for SAVE operations: if the server's updated_at is
                // newer than the snapshot this PC had when it made the offline change,
                // another user modified the same product in the meantime — don't overwrite.
                if ("SAVE".equals(r.operacion()) && r.serverSnapshotAt() != null) {
                    LocalDateTime serverUpdatedAt = fetchServerUpdatedAt(r.productoId());
                    if (serverUpdatedAt != null) {
                        try {
                            LocalDateTime snapshotAt = LocalDateTime.parse(r.serverSnapshotAt());
                            if (serverUpdatedAt.isAfter(snapshotAt)) {
                                log.warn("SyncService: CONFLICTO bien '{}' [{}] — servidor modificado en {}, snapshot local: {}",
                                    r.nombre(), r.productoId(), serverUpdatedAt, snapshotAt);
                                markOutbox("product_outbox", r.id(), "CONFLICT",
                                    "Conflicto: el bien fue modificado en el servidor (" + serverUpdatedAt
                                    + ") mientras el equipo estuvo sin conexión. Vuelve a editarlo y guardar.");
                                conflicted.incrementAndGet();
                                continue;
                            }
                        } catch (Exception parseEx) {
                            log.debug("SyncService: no se pudo parsear server_snapshot_at '{}'", r.serverSnapshotAt());
                        }
                    }
                }
                switch (r.operacion()) {
                    case "BAJA" -> repo.darDeBajaOnline(r.productoId(), r.motivoBaja());
                    case "REACTIVAR" -> repo.reactivarOnline(r.productoId());
                    default -> {
                        Producto p = new Producto();
                        p.setId(r.productoId());
                        p.setNombre(r.nombre());
                        p.setCodigo(r.codigo());
                        p.setDescripcion(r.descripcion());
                        p.setCategoriaId(r.categoriaId());
                        p.setPrecioCompra(r.precioCompra() != null ? new java.math.BigDecimal(r.precioCompra()) : java.math.BigDecimal.ZERO);
                        p.setPrecioVenta(r.precioVenta() != null ? new java.math.BigDecimal(r.precioVenta()) : java.math.BigDecimal.ZERO);
                        p.setStockActual(r.stockActual());
                        p.setStockMinimo(r.stockMinimo());
                        p.setStockMaximo(r.stockMaximo());
                        p.setUnidad(UnidadMedida.fromCodigo(r.unidad()));
                        p.setProveedor(r.proveedor());
                        if (r.fechaVencimiento() != null) p.setFechaVencimiento(java.time.LocalDate.parse(r.fechaVencimiento()));
                        p.setFotoUrl(r.fotoUrl());
                        p.setUbicacion(r.ubicacion());
                        p.setArea(r.area());
                        p.setResguardante(r.resguardante());
                        repo.saveOnline(p);
                    }
                }
                markOutbox("product_outbox", r.id(), "SYNCED", null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar producto {} ({})", r.productoId(), r.operacion(), ex);
                markOutbox("product_outbox", r.id(), "FAILED", ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    private static LocalDateTime fetchServerUpdatedAt(String productoId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT updated_at FROM products WHERE id = ?")) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("updated_at");
                    return ts != null ? ts.toLocalDateTime() : null;
                }
            }
        } catch (SQLException e) {
            log.warn("SyncService: no se pudo consultar updated_at del producto {}", productoId, e);
        }
        return null;
    }

    // ─────────────────────────────── Movimientos ───────────────────────────

    private record MovementRow(int id, String operacion, String movimientoId, String productoId, String tipo,
                                int cantidad, String motivo, String referencia, String areaDestino,
                                String usuarioId, String usuarioNombre) {}

    private static void syncMovimientos(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<MovementRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM movement_outbox WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new MovementRow(rs.getInt("id"), rs.getString("operacion"), rs.getString("movimiento_id"),
                    rs.getString("producto_id"), rs.getString("tipo"), rs.getInt("cantidad"), rs.getString("motivo"),
                    rs.getString("referencia"), rs.getString("area_destino"), rs.getString("usuario_id"),
                    rs.getString("usuario_nombre")));
            }
        }
        MovimientoRepository repo = new MovimientoRepository();
        for (MovementRow r : rows) {
            try {
                if ("DELETE".equals(r.operacion())) {
                    repo.deleteMovimientoAtomicOnline(r.movimientoId());
                } else {
                    Movimiento m = new Movimiento();
                    m.setId(r.movimientoId());
                    m.setProductoId(r.productoId());
                    m.setTipo(TipoMovimiento.fromCodigo(r.tipo()));
                    m.setCantidad(r.cantidad());
                    m.setMotivo(r.motivo());
                    m.setReferencia(r.referencia());
                    m.setAreaDestino(r.areaDestino());
                    m.setUsuarioId(r.usuarioId());
                    m.setUsuarioNombre(r.usuarioNombre());
                    // No expectedStockAnterior on replay: an AJUSTE's offline
                    // snapshot is necessarily stale by the time we're back
                    // online (the server may have moved on independently
                    // while this PC was offline) — recomputing fresh against
                    // Postgres's current stock, the same as any other
                    // movement, is correct here.
                    repo.addMovimientoAtomicOnline(m, null);
                }
                markOutbox("movement_outbox", r.id(), "SYNCED", null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar movimiento {} ({})", r.movimientoId(), r.operacion(), ex);
                markOutbox("movement_outbox", r.id(), "FAILED", ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    // ─────────────────────────────── Outbox bookkeeping ────────────────────

    private static void markOutbox(String table, int rowId, String status, String error) {
        String sql = "UPDATE " + table + " SET status = ?, error = ? WHERE id = ?";
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, error);
            ps.setInt(3, rowId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("SyncService: no se pudo actualizar el estado de la cola local ({}#{})", table, rowId, e);
        }
    }

    private static int countPending() {
        int total = 0;
        for (String table : new String[]{"category_outbox", "product_outbox", "movement_outbox"}) {
            try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                    "SELECT COUNT(*) FROM " + table + " WHERE status = 'PENDING'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total += rs.getInt(1);
            } catch (SQLException e) {
                log.error("SyncService: no se pudo contar pendientes en {}", table, e);
            }
        }
        return total;
    }

    /** Total pending across all three outbox tables — used by the status
     *  bar while offline ("Modo offline · N pendientes"). Returns 0 if the
     *  offline store hasn't been touched yet (nothing queued). */
    public static int pendingCount() {
        if (!DatabaseConfig.isOfflineMode()) return 0;
        try {
            OfflineStore.sharedConnection(); // ensures schema/connection exist
        } catch (SQLException e) {
            return 0;
        }
        return countPending();
    }
}
