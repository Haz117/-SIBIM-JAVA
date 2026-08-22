package com.sibim.db.offline;

import com.sibim.controller.MainController;
import com.sibim.controller.dialogs.ConflictResolutionDialog;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.AuditLog;
import com.sibim.model.Categoria;
import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.CategoriaRepository;
import com.sibim.repository.ConteoRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Watches for the real Postgres connection coming back while the app is in
 * modo offline, and — once it does — replays everything queued in
 * OfflineStore's outbox tables against it, in dependency order (categories,
 * then products, then movements, then conteos, then audit log), using each
 * repository's *Online(...) method directly (bypassing the isOfflineMode()/
 * isDemoMode() branch), exactly as described in the offline-mode plan.
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
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<ConflictoInfo> conflicts = new ArrayList<>();
        try {
            syncCategorias(synced, failed);
            conflicts.addAll(syncProductos(synced, failed));
            syncMovimientos(synced, failed);
            syncConteos(synced, failed);
            syncAuditLog(synced, failed);
        } catch (Exception e) {
            log.error("SyncService: fallo inesperado durante la sincronización", e);
            return;
        }

        int pending = countPending();
        // Only switch back to online mode if everything was resolved
        // (no PENDING rows AND no unresolved CONFLICT rows).
        if (pending == 0 && conflicts.isEmpty()) {
            DatabaseConfig.setOfflineMode(false);
            log.info("SyncService: sincronización completa ({} cambio(s) aplicados). Volviendo a modo online.", synced.get());
        } else if (!conflicts.isEmpty()) {
            log.warn("SyncService: {} conflicto(s) requieren resolución del usuario.", conflicts.size());
        } else {
            log.warn("SyncService: quedaron {} cambio(s) sin poder sincronizar — requieren revisión manual.", pending);
        }
        notifyUi(synced.get(), failed.get(), conflicts, pending);
    }

    private static boolean postgresReachable() {
        try (Connection ignored = DatabaseConfig.getConnection()) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void notifyUi(int synced, int failed, List<ConflictoInfo> conflicts, int stillPending) {
        Platform.runLater(() -> {
            MainController mc = MainController.getInstance();
            var scene = mc != null ? mc.getContentAreaScene() : null;
            if (synced > 0) {
                String msg = "Se sincronizaron " + synced + " cambio(s) con el servidor.";
                if (scene != null) NotificacionUtil.exito(scene, msg);
            }
            if (!conflicts.isEmpty()) {
                ConflictResolutionDialog.show(conflicts);
            }
            if (failed > 0 && scene != null) {
                NotificacionUtil.advertencia(scene,
                    failed + " cambio(s) no se pudieron sincronizar y requieren revisión manual.");
            }
            if (stillPending == 0 && conflicts.isEmpty() && mc != null) {
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

    private static List<ConflictoInfo> syncProductos(AtomicInteger synced, AtomicInteger failed)
            throws SQLException {
        List<ConflictoInfo> conflicts = new ArrayList<>();
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
                                Producto offlineVersion = productFromRow(r);
                                Producto serverVersion  = fetchServerProduct(r.productoId());
                                markOutbox("product_outbox", r.id(), "CONFLICT",
                                    "Conflicto: el bien fue modificado en el servidor (" + serverUpdatedAt
                                    + ") mientras el equipo estuvo sin conexión. Vuelve a editarlo y guardar.");
                                conflicts.add(new ConflictoInfo(r.id(), offlineVersion, serverVersion));
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
                    default -> repo.saveOnline(productFromRow(r));
                }
                markOutbox("product_outbox", r.id(), "SYNCED", null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar producto {} ({})", r.productoId(), r.operacion(), ex);
                markOutbox("product_outbox", r.id(), "FAILED", ex.getMessage());
                failed.incrementAndGet();
            }
        }
        return conflicts;
    }

    private static Producto productFromRow(ProductRow r) {
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
        if (r.fechaVencimiento() != null) p.setFechaVencimiento(LocalDate.parse(r.fechaVencimiento()));
        p.setFotoUrl(r.fotoUrl());
        p.setUbicacion(r.ubicacion());
        p.setArea(r.area());
        p.setResguardante(r.resguardante());
        return p;
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

    private static Producto fetchServerProduct(String productoId) {
        String sql = "SELECT p.*, c.nombre AS categoria_nombre, c.color AS categoria_color "
                   + "FROM products p LEFT JOIN categories c ON c.id = p.categoria_id WHERE p.id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Producto p = new Producto();
                    p.setId(rs.getString("id"));
                    p.setNombre(rs.getString("nombre"));
                    p.setCodigo(rs.getString("codigo"));
                    p.setDescripcion(rs.getString("descripcion"));
                    p.setCategoriaId(rs.getString("categoria_id"));
                    p.setPrecioCompra(rs.getBigDecimal("precio_compra"));
                    p.setPrecioVenta(rs.getBigDecimal("precio_venta"));
                    p.setStockActual(rs.getInt("stock_actual"));
                    p.setStockMinimo(rs.getInt("stock_minimo"));
                    p.setStockMaximo(rs.getInt("stock_maximo"));
                    p.setUnidad(UnidadMedida.fromCodigo(rs.getString("unidad")));
                    p.setProveedor(rs.getString("proveedor"));
                    p.setUbicacion(rs.getString("ubicacion"));
                    p.setArea(rs.getString("area"));
                    p.setResguardante(rs.getString("resguardante"));
                    return p;
                }
            }
        } catch (SQLException e) {
            log.warn("SyncService: no se pudo obtener versión del servidor para producto {}", productoId, e);
        }
        return null;
    }

    /**
     * Called from {@link ConflictResolutionDialog} after the user decides what to do.
     *
     * @param versionOffline non-null → apply this version to Postgres; null → discard
     *                       (keep whatever is currently on the server)
     */
    public static void resolveConflicto(int outboxId, Producto versionOffline) {
        if (versionOffline != null) {
            try {
                new ProductoRepository().saveOnline(versionOffline);
                markOutbox("product_outbox", outboxId, "SYNCED", null);
                log.info("SyncService: conflicto {} resuelto — versión offline aplicada", outboxId);
            } catch (Exception e) {
                markOutbox("product_outbox", outboxId, "FAILED", e.getMessage());
                log.error("SyncService: fallo aplicando versión offline para conflicto {}", outboxId, e);
            }
        } else {
            markOutbox("product_outbox", outboxId, "DISCARDED", "Conservado: versión del servidor");
            log.info("SyncService: conflicto {} descartado — conservando versión del servidor", outboxId);
        }
        // If there are no more actionable rows, switch back to online mode
        if (!DatabaseConfig.isOfflineMode()) return;
        if (countPending() == 0 && countConflictRows() == 0) {
            DatabaseConfig.setOfflineMode(false);
            Platform.runLater(() -> {
                MainController mc = MainController.getInstance();
                if (mc != null) mc.refreshCurrentViewAfterSync();
            });
        }
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

    // ─────────────────────────────── Conteos ───────────────────────────────

    private record ConteoRow(int id, String conteoId, String usuarioId, String usuarioNombre,
                              int totalContados, int totalDiscrepancias, String createdAt) {}

    private record ConteoItemRow(String itemId, String productoId, String productoNombre,
                                  String area, int stockSistema, int stockContado, boolean ajustado) {}

    private static void syncConteos(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<ConteoRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM conteo_outbox WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new ConteoRow(rs.getInt("id"), rs.getString("conteo_id"), rs.getString("usuario_id"),
                    rs.getString("usuario_nombre"), rs.getInt("total_contados"), rs.getInt("total_discrepancias"),
                    rs.getString("created_at")));
            }
        }
        ConteoRepository repo = new ConteoRepository();
        for (ConteoRow r : rows) {
            try {
                List<ConteoItemRow> itemRows = fetchConteoItems(r.conteoId());
                ConteoFisico c = new ConteoFisico();
                c.setId(r.conteoId());
                c.setUsuarioId(r.usuarioId());
                c.setUsuarioNombre(r.usuarioNombre());
                c.setTotalContados(r.totalContados());
                c.setTotalDiscrepancias(r.totalDiscrepancias());
                try { c.setCreadoEn(LocalDateTime.parse(r.createdAt())); } catch (Exception ignored) {}
                List<ConteoItem> items = new ArrayList<>();
                for (ConteoItemRow ir : itemRows) {
                    ConteoItem it = new ConteoItem();
                    it.setId(ir.itemId());
                    it.setConteoId(r.conteoId());
                    it.setProductoId(ir.productoId());
                    it.setProductoNombre(ir.productoNombre());
                    it.setArea(ir.area());
                    it.setStockSistema(ir.stockSistema());
                    it.setStockContado(ir.stockContado());
                    it.setAjustado(ir.ajustado());
                    items.add(it);
                }
                c.setItems(items);
                repo.guardarOnline(c);
                markOutbox("conteo_outbox", r.id(), "SYNCED", null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar conteo {}", r.conteoId(), ex);
                markOutbox("conteo_outbox", r.id(), "FAILED", ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    private static List<ConteoItemRow> fetchConteoItems(String conteoId) throws SQLException {
        List<ConteoItemRow> list = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM conteo_items_outbox WHERE conteo_id = ?")) {
            ps.setString(1, conteoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ConteoItemRow(rs.getString("item_id"), rs.getString("producto_id"),
                        rs.getString("producto_nombre"), rs.getString("area"),
                        rs.getInt("stock_sistema"), rs.getInt("stock_contado"),
                        rs.getInt("ajustado") != 0));
                }
            }
        }
        return list;
    }

    // ─────────────────────────────── AuditLog ──────────────────────────────

    private record AuditRow(int id, String auditId, String entidad, String entidadId, String entidadNombre,
                             String accion, String detalle, String usuarioId, String usuarioNombre,
                             String createdAt) {}

    private static void syncAuditLog(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<AuditRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM audit_log_outbox WHERE status = 'PENDING' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new AuditRow(rs.getInt("id"), rs.getString("audit_id"), rs.getString("entidad"),
                    rs.getString("entidad_id"), rs.getString("entidad_nombre"), rs.getString("accion"),
                    rs.getString("detalle"), rs.getString("usuario_id"), rs.getString("usuario_nombre"),
                    rs.getString("created_at")));
            }
        }
        AuditLogRepository repo = new AuditLogRepository();
        for (AuditRow r : rows) {
            try {
                AuditLog a = new AuditLog();
                a.setId(r.auditId());
                a.setEntidad(r.entidad());
                a.setEntidadId(r.entidadId());
                a.setEntidadNombre(r.entidadNombre());
                a.setAccion(r.accion());
                a.setDetalle(r.detalle());
                a.setUsuarioId(r.usuarioId());
                a.setUsuarioNombre(r.usuarioNombre());
                try { a.setCreadoEn(LocalDateTime.parse(r.createdAt())); } catch (Exception ignored) {}
                repo.logOnline(a);
                markOutbox("audit_log_outbox", r.id(), "SYNCED", null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar audit entry {}", r.auditId(), ex);
                markOutbox("audit_log_outbox", r.id(), "FAILED", ex.getMessage());
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
        for (String table : new String[]{
                "category_outbox", "product_outbox", "movement_outbox",
                "conteo_outbox", "audit_log_outbox"}) {
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

    private static int countConflictRows() {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT COUNT(*) FROM product_outbox WHERE status = 'CONFLICT'");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            log.error("SyncService: no se pudo contar conflictos", e);
        }
        return 0;
    }

    /** Total pending across all outbox tables — used by the status bar while
     *  offline ("Modo offline · N pendientes"). Returns 0 if the offline store
     *  hasn't been touched yet (nothing queued). */
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
