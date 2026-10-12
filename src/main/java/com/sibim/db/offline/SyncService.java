package com.sibim.db.offline;

import com.sibim.controller.MainController;
import com.sibim.controller.dialogs.ConflictResolutionDialog;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.MigrationRunner;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final long MIN_POLL_SECONDS = 60;
    private static final long MAX_POLL_SECONDS = 300;
    private static volatile long currentPollDelay = MIN_POLL_SECONDS;

    /** While connected, one failed attempt to get a connection is not "no
     *  server": every connection of the pool being busy for a few seconds (a
     *  big report, a backup) fails the same way, and going offline for that
     *  left the whole app offline until a full sync pass brought it back. The
     *  attempt is repeated after {@link #REINTENTO_SEGUNDOS}; only the second
     *  failure in a row switches to offline mode. */
    private static final int  FALLOS_PARA_OFFLINE = 2;
    private static final long REINTENTO_SEGUNDOS  = 15;
    private static volatile int fallosSeguidos;

    /** One pass at a time: the scheduled poll and "Sincronizar ahora" would
     *  otherwise replay the same queued rows twice, side by side. */
    private static final java.util.concurrent.locks.ReentrantLock PASADA =
        new java.util.concurrent.locks.ReentrantLock();

    static final String STATUS_PENDING   = "PENDING";
    static final String STATUS_SYNCED    = "SYNCED";
    static final String STATUS_FAILED    = "FAILED";
    static final String STATUS_CONFLICT  = "CONFLICT";
    static final String STATUS_DISCARDED = "DISCARDED";

    /** A row that keeps failing for MAX_RETRY_ATTEMPTS sync attempts is discarded
     *  instead of being requeued forever — otherwise a failure that never
     *  qualifies as "permanent" under {@link #isPermanentFailure} (e.g. a
     *  movimiento that no longer fits the server's current stock) would retry
     *  on every connectivity tick indefinitely, and since countPending() counts
     *  FAILED rows, the app would never be allowed back into modo online even
     *  with the server fully reachable. */
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private static ScheduledExecutorService executor;

    private SyncService() {}

    public static synchronized void startWatching() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sibim-sync");
            t.setDaemon(true);
            return t;
        });
        scheduleNextTick();
        log.info("SyncService: vigilando reconexión con backoff adaptativo ({}s–{}s)",
            MIN_POLL_SECONDS, MAX_POLL_SECONDS);
    }

    private static synchronized void scheduleNextTick() {
        if (executor == null || executor.isShutdown()) return;
        executor.schedule(SyncService::tickAndReschedule, currentPollDelay, TimeUnit.SECONDS);
    }

    private static void tickAndReschedule() {
        try {
            boolean wasOffline = DatabaseConfig.isOfflineMode();
            tick();
            boolean stillOffline = DatabaseConfig.isOfflineMode();
            // Back off when we're offline and couldn't reconnect; reset on success
            if (stillOffline && wasOffline) {
                currentPollDelay = Math.min(currentPollDelay * 2, MAX_POLL_SECONDS);
                log.debug("SyncService: sin conexión — próximo intento en {}s", currentPollDelay);
            } else {
                currentPollDelay = fallosSeguidos > 0 ? REINTENTO_SEGUNDOS : MIN_POLL_SECONDS;
            }
        } finally {
            scheduleNextTick();
        }
    }

    public static synchronized void stopWatching() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** Manual trigger (e.g. a "Sincronizar ahora" button) — resets backoff
     *  so the next scheduled tick returns to the baseline interval. */
    public static void syncNow() {
        currentPollDelay = MIN_POLL_SECONDS;
        tick();
    }

    /** Runs on every poll regardless of which mode we're currently in — this
     *  is what actually watches for connectivity in BOTH directions:
     *  offline→online (replay the outbox, as before) and, just as
     *  important, online→offline. That second direction didn't exist before
     *  this fix: "modo offline" was only ever entered once, from the splash
     *  screen's initial connection attempt — if the connection dropped
     *  later, mid-session, nothing detected it, and every screen just threw
     *  raw SQLExceptions until the app was restarted. */
    private static void tick() {
        PASADA.lock();
        try {
            DatabaseConfig.comoSincronizacion(SyncService::tickSinAtajo);
        } finally {
            PASADA.unlock();
        }
    }

    private static void tickSinAtajo() {
        if (DatabaseConfig.isDemoMode()) return;
        if (DatabaseConfig.isOfflineMode()) {
            if (postgresReachable()) syncPendingChanges();
            if (!DatabaseConfig.isOfflineMode()) refrescarAreas();
            return;
        }
        if (postgresReachable()) {
            fallosSeguidos = 0;
            refrescarAreas();
        } else if (debePasarAOffline(++fallosSeguidos)) {
            fallosSeguidos = 0;
            enterOfflineMode();
        } else {
            log.warn("SyncService: no se obtuvo conexión con la base de datos; se reintenta en {} s "
                + "antes de pasar a modo offline.", REINTENTO_SEGUNDOS);
        }
    }

    static boolean debePasarAOffline(int fallosConsecutivos) {
        return fallosConsecutivos >= FALLOS_PARA_OFFLINE;
    }

    /** Areas added or edited on another PC show up here within one poll; photos
     *  and facturas left on this PC (offline, failed upload) are shared. */
    private static void refrescarAreas() {
        try { new com.sibim.service.AreaService().refrescarSiCambio(); }
        catch (RuntimeException e) { log.debug("Revisión de áreas omitida: {}", e.getMessage()); }
        try { new com.sibim.service.FotosPendientesService().subirPendientes(); }
        catch (RuntimeException e) { log.debug("Revisión de fotos pendientes omitida: {}", e.getMessage()); }
        olvidarBienesQueElServidorYaNoTiene();
    }

    private static final long LIMPIEZA_DE_LA_COPIA_CADA_MS = 10 * 60_000L;
    private static volatile long ultimaLimpiezaDeLaCopia;

    /** While connected, at start and every ten minutes: the copy this PC keeps
     *  for working offline drops the bienes that no longer exist on the server
     *  (see {@link OfflineStore#olvidarBienesAusentes}). */
    private static void olvidarBienesQueElServidorYaNoTiene() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaLimpiezaDeLaCopia < LIMPIEZA_DE_LA_COPIA_CADA_MS) return;
        try {
            int olvidados = OfflineStore.olvidarBienesAusentes(new ProductoRepository().idsEnServidor());
            ultimaLimpiezaDeLaCopia = ahora;
            if (olvidados > 0)
                log.info("SyncService: {} bien(es) que el servidor ya no tiene se quitaron de la copia local", olvidados);
        } catch (SQLException | RuntimeException e) {
            log.debug("Limpieza de la copia local omitida: {}", e.getMessage());
        }
    }

    /** Mid-session connectivity loss while online — the counterpart to the
     *  splash screen's boot-time fallback. Deliberately does NOT call
     *  DatabaseConfig.close(): re-running init() on the next getConnection()
     *  can throw HikariCP's (unchecked) PoolInitializationException if the
     *  DB is still down, which postgresReachable() doesn't catch — that
     *  would silently kill this scheduled task for good (an uncaught
     *  exception cancels all future runs of a ScheduledExecutorService
     *  task). Leaving the existing (dead) pool in place is both simpler and
     *  safer: HikariCP already reports a clean, checked SQLException when
     *  it can't hand out a connection. */
    private static void enterOfflineMode() {
        log.warn("SyncService: se perdió la conexión con la base de datos — entrando a modo offline.");
        DatabaseConfig.setOfflineMode(true);
        Platform.runLater(() -> {
            MainController mc = MainController.getInstance();
            var scene = mc != null ? mc.getContentAreaScene() : null;
            if (scene != null) NotificacionUtil.advertencia(scene,
                "Se perdió la conexión con el servidor. Trabajando en modo offline — tus cambios se "
                + "guardan localmente y se sincronizarán automáticamente en cuanto vuelva la conexión.");
            if (mc != null) mc.refreshCurrentViewAfterSync();
        });
    }

    /** Set once the user has been told why the app stays offline, so the
     *  notice isn't repeated on every poll. */
    private static volatile String motivoEsquemaAvisado;

    /** Before replaying anything or going back online, the database must be
     *  migrated (or validated) for this build — the same check as startup.
     *  A PC that started without a connection never ran it, and one that
     *  can't migrate (DB_MIGRATE=false) would otherwise go online against an
     *  outdated schema a minute after the splash sent it offline for that. */
    private static boolean esquemaListo() {
        try {
            MigrationRunner.asegurarEsquema();
            motivoEsquemaAvisado = null;
            return true;
        } catch (Exception e) {
            String motivo = MigrationRunner.motivoEsquema(e);
            if (motivo == null) {
                log.warn("SyncService: no se pudo verificar el esquema de la base de datos: {}", e.getMessage());
                return false;
            }
            if (!motivo.equals(motivoEsquemaAvisado)) {
                motivoEsquemaAvisado = motivo;
                log.error("SyncService: se sigue en modo offline — {}", motivo);
                Platform.runLater(() -> {
                    MainController mc = MainController.getInstance();
                    var scene = mc != null ? mc.getContentAreaScene() : null;
                    if (scene != null) NotificacionUtil.advertencia(scene,
                        "Hay conexión, pero la base de datos necesita una actualización que esta computadora "
                        + "no puede aplicar. Sigues en modo offline; avisa al administrador del sistema.");
                });
            }
            return false;
        }
    }

    private static void syncPendingChanges() {
        if (!esquemaListo()) return;
        requeueFailedChanges();
        int pendingBefore = countPending();
        if (pendingBefore > 0) {
            Platform.runLater(() -> {
                MainController mc = MainController.getInstance();
                var scene = mc != null ? mc.getContentAreaScene() : null;
                if (scene != null) NotificacionUtil.info(scene,
                    "Conexión restablecida — sincronizando " + pendingBefore + " cambio(s) pendiente(s)...");
            });
        }

        log.info("SyncService: conexión recuperada, sincronizando cambios pendientes...");
        AtomicInteger synced = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<ConflictoInfo> conflicts = new ArrayList<>();
        try {
            syncCategorias(synced, failed);
            List<ConflictoInfo> nuevos = syncProductos(synced, failed);
            conflicts.addAll(nuevos);
            conflicts.addAll(conflictosSinResolver(nuevos));
            syncMovimientos(synced, failed);
            syncConteos(synced, failed);
            syncDocumentos(synced, failed);
            syncAuditLog(synced, failed);
        } catch (Exception e) {
            log.error("SyncService: fallo inesperado durante la sincronización", e);
            Platform.runLater(() -> {
                MainController mc = MainController.getInstance();
                var scene = mc != null ? mc.getContentAreaScene() : null;
                if (scene != null) NotificacionUtil.advertencia(scene,
                    "No se pudo completar la sincronización — se reintentará automáticamente.");
            });
            return;
        }
        purgeSyncedRows();

        int pending = countPending();
        // Only switch back to online mode if everything was resolved (no PENDING/FAILED
        // rows AND no unresolved CONFLICT rows). countConflictRows() — not just this
        // pass's `conflicts` list — matters here: a conflict flagged on an earlier tick
        // is already CONFLICT status by now, so it's invisible to syncProductos()'s
        // "WHERE status = PENDING" query and would never show up in `conflicts` again;
        // without this check we'd flip back online and abandon it unresolved.
        boolean conflictsPending = !conflicts.isEmpty() || countConflictRows() > 0;
        if (pending == 0 && !conflictsPending) {
            DatabaseConfig.setOfflineMode(false);
            // Next offline stint (if any) should re-read from SQLite instead
            // of replaying whatever was in memory from before this reconnect.
            OfflineStore.invalidateCache();
            log.info("SyncService: sincronización completa ({} cambio(s) aplicados). Volviendo a modo online.", synced.get());
        } else if (conflictsPending) {
            log.warn("SyncService: hay conflicto(s) sin resolver que requieren decisión del usuario.");
        } else {
            log.warn("SyncService: quedaron {} cambio(s) sin poder sincronizar — requieren revisión manual.", pending);
        }
        notifyUi(synced.get(), failed.get(), conflicts, pending);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean MOSTRANDO_CONFLICTOS =
        new java.util.concurrent.atomic.AtomicBoolean();

    private static boolean postgresReachable() {
        try (Connection ignored = DatabaseConfig.getConnection()) {
            return true;
        } catch (Exception e) {
            // Broad on purpose: a dead/misconfigured pool can surface as an
            // unchecked HikariCP exception too, and this must never let one
            // escape — an uncaught exception here would silently cancel all
            // future runs of the scheduled watcher (ScheduledExecutorService
            // semantics), leaving the app stuck in whatever mode it's in.
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
            // The poll keeps running while the dialog waits for an answer:
            // never a second dialog for the same conflicts on top of it.
            if (!conflicts.isEmpty() && MOSTRANDO_CONFLICTOS.compareAndSet(false, true)) {
                try { ConflictResolutionDialog.show(conflicts); }
                finally { MOSTRANDO_CONFLICTOS.set(false); }
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
                                String descripcion, String color, String icono, String codigoConac, int retryCount) {}

    static void syncCategorias(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<CategoryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM category_outbox WHERE status = '" + STATUS_PENDING + "' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new CategoryRow(rs.getInt("id"), rs.getString("operacion"), rs.getString("categoria_id"),
                    rs.getString("nombre"), rs.getString("descripcion"), rs.getString("color"), rs.getString("icono"),
                    rs.getString("codigo_conac"), rs.getInt("retry_count")));
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
                    c.setCodigoConac(r.codigoConac());
                    repo.saveOnline(c);
                }
                markOutbox("category_outbox", r.id(), STATUS_SYNCED, null);
                writeAuditEntry("categoria", r.categoriaId(), r.nombre(),
                    r.operacion().toLowerCase(), "Replicado desde modo offline");
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar categoría {} ({})", r.categoriaId(), r.operacion(), ex);
                markOutbox("category_outbox", r.id(),
                    resolveFailureStatus(ex, r.retryCount(), "categoria", r.categoriaId(), r.nombre()),
                    ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    // ─────────────────────────────── Productos ─────────────────────────────

    private record ProductRow(int id, String operacion, String productoId, String nombre, String codigo,
                               String descripcion, String categoriaId, String precioCompra, String precioVenta,
                               int stockActual, int stockMinimo, int stockMaximo, String unidad, String proveedor,
                               String fechaVencimiento, String fotoUrl, String facturaUrl,
                               String numeroSerie, String marca, String modelo,
                               String ubicacion, String area,
                               String resguardante, String motivoBaja, String serverSnapshotAt,
                               boolean etiquetado, String fotosUrls,
                               Producto extras, int retryCount) {}

    static List<ConflictoInfo> syncProductos(AtomicInteger synced, AtomicInteger failed)
            throws SQLException {
        return replicarBienes(filasDeBienes(STATUS_PENDING), synced, failed);
    }

    private static List<ProductRow> filasDeBienes(String status) throws SQLException {
        List<ProductRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM product_outbox WHERE status = '" + status + "' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Producto extras = new Producto();
                ProductoExtras.leer(rs, extras);
                rows.add(new ProductRow(rs.getInt("id"), rs.getString("operacion"), rs.getString("producto_id"),
                    rs.getString("nombre"), rs.getString("codigo"), rs.getString("descripcion"),
                    rs.getString("categoria_id"), rs.getString("precio_compra"), rs.getString("precio_venta"),
                    rs.getInt("stock_actual"), rs.getInt("stock_minimo"), rs.getInt("stock_maximo"),
                    rs.getString("unidad"), rs.getString("proveedor"), rs.getString("fecha_vencimiento"),
                    rs.getString("foto_url"), rs.getString("factura_url"),
                    rs.getString("numero_serie"), rs.getString("marca"), rs.getString("modelo"),
                    rs.getString("ubicacion"), rs.getString("area"),
                    rs.getString("resguardante"), rs.getString("motivo_baja"),
                    rs.getString("server_snapshot_at"),
                    rs.getInt("etiquetado") != 0, rs.getString("fotos_urls"),
                    extras, rs.getInt("retry_count")));
            }
        }
        return rows;
    }

    /** Conflicts flagged on an earlier pass that nobody decided (the app was
     *  closed, or crashed, with the dialog open). Only PENDING rows are
     *  replayed, so these never came up again: the PC stayed offline because
     *  of them with no way to resolve them. They are shown again on every pass
     *  until someone decides. */
    static List<ConflictoInfo> conflictosSinResolver(List<ConflictoInfo> yaIncluidos) throws SQLException {
        java.util.Set<Integer> incluidos = new java.util.HashSet<>();
        for (ConflictoInfo c : yaIncluidos) incluidos.add(c.outboxId());
        List<ConflictoInfo> pendientes = new ArrayList<>();
        for (ProductRow r : filasDeBienes(STATUS_CONFLICT)) {
            if (incluidos.contains(r.id())) continue;
            pendientes.add(new ConflictoInfo(r.id(), productFromRow(r), fetchServerProduct(r.productoId()), r.operacion()));
        }
        return pendientes;
    }

    private static List<ConflictoInfo> replicarBienes(List<ProductRow> rows,
            AtomicInteger synced, AtomicInteger failed) {
        List<ConflictoInfo> conflicts = new ArrayList<>();
        ProductoRepository repo = new ProductoRepository();
        // Once one queued change of a bien is applied, the server's updated_at
        // moves to "now" — a later queued change of the same bien must be
        // compared against that, not against the baseline both were queued
        // with, or every bien edited twice offline would conflict with itself.
        Map<String, LocalDateTime> baselineTrasReplicar = new HashMap<>();
        for (ProductRow r : rows) {
            try {
                // Conflict detection: if the server's updated_at is newer than the snapshot
                // this PC had when it made the offline change, another user modified the
                // same product in the meantime — don't blindly overwrite. Applies to SAVE,
                // BAJA, and REACTIVAR (all three now store a non-null serverSnapshotAt).
                String snapshot = baselineTrasReplicar.containsKey(r.productoId())
                    ? baselineTrasReplicar.get(r.productoId()).toString() : r.serverSnapshotAt();
                if (snapshot != null) {
                    LocalDateTime serverUpdatedAt = fetchServerUpdatedAt(r.productoId());
                    if (serverUpdatedAt != null) {
                        try {
                            LocalDateTime snapshotAt = LocalDateTime.parse(snapshot);
                            if (serverUpdatedAt.isAfter(snapshotAt)) {
                                log.warn("SyncService: CONFLICTO bien '{}' [{}] op={} — servidor modificado en {}, snapshot local: {}",
                                    r.nombre(), r.productoId(), r.operacion(), serverUpdatedAt, snapshotAt);
                                Producto offlineVersion = productFromRow(r);
                                Producto serverVersion  = fetchServerProduct(r.productoId());
                                markOutbox("product_outbox", r.id(), STATUS_CONFLICT,
                                    "Conflicto en " + r.operacion() + ": el bien fue modificado en el servidor ("
                                    + serverUpdatedAt + ") mientras el equipo estuvo sin conexión.");
                                conflicts.add(new ConflictoInfo(r.id(), offlineVersion, serverVersion, r.operacion()));
                                continue;
                            }
                        } catch (Exception parseEx) {
                            log.debug("SyncService: no se pudo parsear server_snapshot_at '{}'", snapshot);
                        }
                    }
                }
                switch (r.operacion()) {
                    case "BAJA" -> repo.darDeBajaOnline(r.productoId(), r.motivoBaja());
                    case "REACTIVAR" -> repo.reactivarOnline(r.productoId());
                    default -> guardarReplicado(repo, productFromRow(r));
                }
                LocalDateTime nuevoBaseline = fetchServerUpdatedAt(r.productoId());
                if (nuevoBaseline != null) {
                    baselineTrasReplicar.put(r.productoId(), nuevoBaseline);
                    actualizarBaselinePendiente(r.productoId(), r.id(), nuevoBaseline);
                }
                markOutbox("product_outbox", r.id(), STATUS_SYNCED, null);
                writeAuditEntry("producto", r.productoId(), r.nombre(),
                    r.operacion().toLowerCase(), "Replicado desde modo offline");
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar producto {} ({})", r.productoId(), r.operacion(), ex);
                markOutbox("product_outbox", r.id(),
                    resolveFailureStatus(ex, r.retryCount(), "producto", r.productoId(), r.nombre()),
                    ex.getMessage());
                failed.incrementAndGet();
            }
        }
        return conflicts;
    }

    /** Persists the new baseline on this bien's later queued rows too, so an
     *  interrupted pass doesn't flag them as conflicts on the next one. */
    private static void actualizarBaselinePendiente(String productoId, int despuesDeId, LocalDateTime baseline) {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "UPDATE product_outbox SET server_snapshot_at = ? WHERE producto_id = ? AND id > ? AND status = ?")) {
            ps.setString(1, baseline.toString());
            ps.setString(2, productoId);
            ps.setInt(3, despuesDeId);
            ps.setString(4, STATUS_PENDING);
            OfflineStore.escribir(ps);
        } catch (SQLException e) {
            log.warn("SyncService: no se pudo actualizar la referencia de conflicto del bien {}", productoId, e);
        }
    }

    private static Producto productFromRow(ProductRow r) {
        Producto p = new Producto();
        p.setId(r.productoId());
        p.setNombre(r.nombre());
        p.setCodigo(r.codigo());
        p.setDescripcion(r.descripcion());
        p.setCategoriaId(r.categoriaId());
        p.setPrecioCompra(r.precioCompra() != null ? new BigDecimal(r.precioCompra()) : BigDecimal.ZERO);
        p.setPrecioVenta(r.precioVenta() != null ? new BigDecimal(r.precioVenta()) : BigDecimal.ZERO);
        p.setStockActual(r.stockActual());
        p.setStockMinimo(r.stockMinimo());
        p.setStockMaximo(r.stockMaximo());
        p.setUnidad(UnidadMedida.fromCodigo(r.unidad()));
        p.setProveedor(r.proveedor());
        if (r.fechaVencimiento() != null) p.setFechaVencimiento(LocalDate.parse(r.fechaVencimiento()));
        p.setFotoUrl(r.fotoUrl());
        p.setFacturaUrl(r.facturaUrl());
        p.setNumeroSerie(r.numeroSerie());
        p.setMarca(r.marca());
        p.setModelo(r.modelo());
        p.setUbicacion(r.ubicacion());
        p.setArea(r.area());
        p.setResguardante(r.resguardante());
        p.setEtiquetado(r.etiquetado());
        if (r.fotosUrls() != null && !r.fotosUrls().isBlank())
            p.setFotosUrls(new ArrayList<>(Arrays.asList(r.fotosUrls().split("\\|\\|"))));
        ProductoExtras.completarConServidor(p, r.extras());   // p has none of them yet: copies them all
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

    /** Saves a bien captured offline. Its código was picked from this PC's
     *  copy of the inventory, so another PC may have used that número in the
     *  meantime: instead of throwing the bien away (what a duplicate código
     *  used to do), a new one takes the next free número on the server, and
     *  an edited one keeps the código the server already has for it. */
    static void guardarReplicado(ProductoRepository repo, Producto offline) throws SQLException {
        java.util.Optional<Producto> enServidor = repo.findByIdOnline(offline.getId());
        Producto p = conDatosDelServidor(repo, offline);
        try {
            repo.saveOnline(p);
        } catch (SQLException e) {
            if (!"23505".equals(e.getSQLState())) throw e;
            String anterior = p.getCodigo();
            if (enServidor.isPresent()) p.setCodigo(enServidor.get().getCodigo());
            else if (com.sibim.config.AreaCodigos.tienePrefijo(p.getArea()))
                p.setCodigo(repo.siguienteCodigoOnline(p.getArea()));
            else throw e;
            if (java.util.Objects.equals(anterior, p.getCodigo())) throw e;
            repo.saveOnline(p);
            try { OfflineStore.updateProductoCodigo(p.getId(), p.getCodigo()); }
            catch (SQLException local) { log.debug("No se actualizó el código en la copia local: {}", local.getMessage()); }
            writeAuditEntry("producto", p.getId(), p.getNombre(), "codigo_reasignado",
                "Capturado sin conexión como " + anterior + "; ese código ya estaba en uso y quedó como " + p.getCodigo());
        }
    }

    /** saveOnline writes the whole row: fields this offline change doesn't
     *  carry (queued by an older SIBIM, or from a mirror not refreshed since)
     *  keep the server's value instead of being wiped. */
    private static Producto conDatosDelServidor(ProductoRepository repo, Producto offline) throws SQLException {
        ProductoExtras.completarConServidor(offline, repo.findByIdOnline(offline.getId()).orElse(null));
        return offline;
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
                    p.setEtiquetado(rs.getBoolean("etiquetado"));
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
     * @param operacion      the outbox operation ("SAVE", "BAJA", "REACTIVAR") — determines
     *                       which repository method to call when applying the offline version
     */
    public static void resolveConflicto(int outboxId, Producto versionOffline, String operacion) {
        // The app is still in offline mode while a conflict waits, and the
        // dialog calls this from its own thread: without the sync pass
        // getConnection() refused, "usar mi versión" always failed and the
        // same conflict came back on the next poll.
        DatabaseConfig.comoSincronizacion(() -> resolverConflicto(outboxId, versionOffline, operacion));
    }

    private static void resolverConflicto(int outboxId, Producto versionOffline, String operacion) {
        if (versionOffline != null) {
            try {
                ProductoRepository repo = new ProductoRepository();
                if ("BAJA".equals(operacion)) {
                    repo.darDeBajaOnline(versionOffline.getId(), versionOffline.getMotivoBaja());
                } else if ("REACTIVAR".equals(operacion)) {
                    repo.reactivarOnline(versionOffline.getId());
                } else {
                    // Like the batch replay: a código another PC took meanwhile
                    // is replaced, not a reason to throw the offline edit away.
                    guardarReplicado(repo, versionOffline);
                }
                markOutbox("product_outbox", outboxId, STATUS_SYNCED, null);
                log.info("SyncService: conflicto {} ({}) resuelto — versión offline aplicada", outboxId, operacion);
            } catch (Exception e) {
                markOutbox("product_outbox", outboxId, STATUS_FAILED, e.getMessage());
                log.error("SyncService: fallo aplicando versión offline para conflicto {} ({})", outboxId, operacion, e);
            }
        } else {
            markOutbox("product_outbox", outboxId, STATUS_DISCARDED, "Conservado: versión del servidor");
            log.info("SyncService: conflicto {} descartado — conservando versión del servidor", outboxId);
        }
        // If there are no more actionable rows, switch back to online mode
        if (!DatabaseConfig.isOfflineMode()) return;
        if (countPending() == 0 && countConflictRows() == 0) {
            DatabaseConfig.setOfflineMode(false);
            OfflineStore.invalidateCache();
            Platform.runLater(() -> {
                MainController mc = MainController.getInstance();
                if (mc != null) mc.refreshCurrentViewAfterSync();
            });
        }
    }

    // ─────────────────────────────── Movimientos ───────────────────────────

    private record MovementRow(int id, String operacion, String movimientoId, String productoId, String tipo,
                                int cantidad, String motivo, String referencia, String areaDestino,
                                String usuarioId, String usuarioNombre, String estado, int retryCount) {}

    static void syncMovimientos(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<MovementRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM movement_outbox WHERE status = '" + STATUS_PENDING + "' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new MovementRow(rs.getInt("id"), rs.getString("operacion"), rs.getString("movimiento_id"),
                    rs.getString("producto_id"), rs.getString("tipo"), rs.getInt("cantidad"), rs.getString("motivo"),
                    rs.getString("referencia"), rs.getString("area_destino"), rs.getString("usuario_id"),
                    rs.getString("usuario_nombre"), rs.getString("estado"), rs.getInt("retry_count")));
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
                    if (TipoMovimiento.TRANSFERENCIA == m.getTipo()
                            && Movimiento.ESTADO_PENDIENTE.equals(r.estado())) {
                        repo.addMovimientoPendiente(m);
                    } else {
                        repo.addMovimientoAtomicOnline(m, null);
                    }
                }
                markOutbox("movement_outbox", r.id(), STATUS_SYNCED, null);
                writeAuditEntry("movimiento", r.movimientoId(), r.productoId(),
                    r.operacion().toLowerCase(), "Replicado desde modo offline");
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar movimiento {} ({})", r.movimientoId(), r.operacion(), ex);
                markOutbox("movement_outbox", r.id(),
                    resolveFailureStatus(ex, r.retryCount(), "movimiento", r.movimientoId(), r.productoId()),
                    ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    // ─────────────────────────────── Conteos ───────────────────────────────

    private record ConteoRow(int id, String conteoId, String usuarioId, String usuarioNombre,
                              int totalContados, int totalDiscrepancias, String createdAt, int retryCount) {}

    private record ConteoItemRow(String itemId, String productoId, String productoNombre,
                                  String productoCodigo, String area, int stockSistema, int stockContado,
                                  boolean ajustado, String estadoConteo, String nota) {}

    static void syncConteos(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<ConteoRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM conteo_outbox WHERE status = '" + STATUS_PENDING + "' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new ConteoRow(rs.getInt("id"), rs.getString("conteo_id"), rs.getString("usuario_id"),
                    rs.getString("usuario_nombre"), rs.getInt("total_contados"), rs.getInt("total_discrepancias"),
                    rs.getString("created_at"), rs.getInt("retry_count")));
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
                try { c.setCreadoEn(LocalDateTime.parse(r.createdAt())); } catch (Exception ex) { log.warn("createdAt inválido en ConteoFisico {}: {}", r.id(), r.createdAt()); }
                List<ConteoItem> items = new ArrayList<>();
                for (ConteoItemRow ir : itemRows) {
                    ConteoItem it = new ConteoItem();
                    it.setId(ir.itemId());
                    it.setConteoId(r.conteoId());
                    it.setProductoId(ir.productoId());
                    it.setProductoNombre(ir.productoNombre());
                    it.setProductoCodigo(ir.productoCodigo());
                    it.setArea(ir.area());
                    it.setStockSistema(ir.stockSistema());
                    it.setStockContado(ir.stockContado());
                    it.setAjustado(ir.ajustado());
                    it.setEstadoConteo(ir.estadoConteo());
                    it.setNota(ir.nota());
                    items.add(it);
                }
                c.setItems(items);
                repo.guardarOnline(c);
                markOutbox("conteo_outbox", r.id(), STATUS_SYNCED, null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar conteo {}", r.conteoId(), ex);
                markOutbox("conteo_outbox", r.id(),
                    resolveFailureStatus(ex, r.retryCount(), "conteo", r.conteoId(), r.usuarioNombre()),
                    ex.getMessage());
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
                        rs.getString("producto_nombre"), rs.getString("producto_codigo"),
                        rs.getString("area"),
                        rs.getInt("stock_sistema"), rs.getInt("stock_contado"),
                        rs.getInt("ajustado") != 0,
                        rs.getString("estado_conteo"), rs.getString("nota")));
                }
            }
        }
        return list;
    }

    // ─────────────────────────────── AuditLog ──────────────────────────────

    private record AuditRow(int id, String auditId, String entidad, String entidadId, String entidadNombre,
                             String accion, String detalle, String usuarioId, String usuarioNombre,
                             String createdAt, int retryCount) {}

    static void syncAuditLog(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        List<AuditRow> rows = new ArrayList<>();
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT * FROM audit_log_outbox WHERE status = '" + STATUS_PENDING + "' ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new AuditRow(rs.getInt("id"), rs.getString("audit_id"), rs.getString("entidad"),
                    rs.getString("entidad_id"), rs.getString("entidad_nombre"), rs.getString("accion"),
                    rs.getString("detalle"), rs.getString("usuario_id"), rs.getString("usuario_nombre"),
                    rs.getString("created_at"), rs.getInt("retry_count")));
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
                try { a.setCreadoEn(LocalDateTime.parse(r.createdAt())); } catch (Exception ex) { log.warn("createdAt inválido en AuditLog {}: {}", r.id(), r.createdAt()); }
                repo.logOnline(a);
                markOutbox("audit_log_outbox", r.id(), STATUS_SYNCED, null);
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar audit entry {}", r.auditId(), ex);
                markOutbox("audit_log_outbox", r.id(),
                    resolveFailureStatus(ex, r.retryCount(), "audit_log", r.auditId(), r.entidadNombre()),
                    ex.getMessage());
                failed.incrementAndGet();
            }
        }
    }

    // ─────────────────────────────── Documentos ────────────────────────────

    /** Resguardos, préstamos, comodatos and actas made or changed offline
     *  (see OfflineDocs), in the order they were made. A document created
     *  offline gets its real folio here; the copy on this PC is updated so
     *  it shows that folio from now on. */
    static void syncDocumentos(AtomicInteger synced, AtomicInteger failed) throws SQLException {
        var resguardos = new com.sibim.repository.ResguardoRepository();
        var prestamos  = new com.sibim.repository.PrestamoRepository();
        var comodatos  = new com.sibim.repository.ComodatoRepository();
        var actas      = new com.sibim.repository.ActaRepository();
        var areaResguardos = new com.sibim.repository.AreaResguardoRepository();
        for (OfflineDocs.Pendiente r : OfflineDocs.pendientes()) {
            try {
                String clave = r.tipo() + ":" + r.operacion();
                switch (clave) {
                    case "resguardo:CREAR" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.model.Resguardo.class);
                        exigirResguardoLibre(d);
                        OfflineDocs.guardar(r.tipo(), d.getId(), resguardos.saveOnline(d));
                    }
                    case "resguardo:CANCELAR" -> resguardos.cancelarOnline(r.docId());
                    case "prestamo:CREAR" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.model.Prestamo.class);
                        exigirBienLibre(d.getProductoId(), d.getProductoNombre(), d.getId());
                        OfflineDocs.guardar(r.tipo(), d.getId(), prestamos.saveOnline(d));
                    }
                    case "prestamo:DEVOLVER" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.model.Prestamo.class);
                        prestamos.devolverOnline(r.docId(), d.getFechaDevolucionReal());
                    }
                    case "comodato:CREAR" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.model.Comodato.class);
                        exigirBienLibre(d.getProductoId(), d.getProductoNombre(), d.getId());
                        OfflineDocs.guardar(r.tipo(), d.getId(), comodatos.saveOnline(d));
                    }
                    case "comodato:CONCLUIR" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.model.Comodato.class);
                        comodatos.concluirOnline(r.docId(), d.getFechaDevolucionReal());
                    }
                    case "comodato:RESCINDIR" -> comodatos.rescindirOnline(r.docId());
                    case "acta:CREAR" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.model.ActaEntregaRecepcion.class);
                        OfflineDocs.guardar(r.tipo(), d.getId(), actas.saveOnline(d));
                    }
                    case "area_resguardo:CREAR" -> {
                        var d = OfflineDocs.leer(r.json(), com.sibim.repository.AreaResguardoRepository.Local.class);
                        areaResguardos.saveOnline(d);
                    }
                    case "area_resguardo:ELIMINAR" -> areaResguardos.deleteOnline(r.docId());
                    default -> throw new SQLException("Operación desconocida en la cola de documentos: " + clave);
                }
                markOutbox("doc_outbox", r.id(), STATUS_SYNCED, null);
                writeAuditEntry(r.tipo(), r.docId(), r.docId(), r.operacion().toLowerCase(), "Replicado desde modo offline");
                synced.incrementAndGet();
            } catch (Exception ex) {
                log.error("SyncService: no se pudo sincronizar {} {} ({})", r.tipo(), r.docId(), r.operacion(), ex);
                markOutbox("doc_outbox", r.id(),
                    resolveFailureStatus(ex, r.reintentos(), r.tipo(), r.docId(), r.docId()), ex.getMessage());
                // It will never reach the server: stop showing it on this PC
                // (the reason stays in the discarded changes, for the admin).
                if (ex instanceof DocumentoEnConflicto) {
                    try { OfflineDocs.borrar(r.tipo(), r.docId()); }
                    catch (SQLException e) { log.warn("SyncService: no se pudo quitar {} {} de la copia local", r.tipo(), r.docId(), e); }
                }
                failed.incrementAndGet();
            }
        }
    }

    /** A document this PC made offline that the server no longer allows. */
    static final class DocumentoEnConflicto extends Exception {
        DocumentoEnConflicto(String mensaje) { super(mensaje); }
    }

    private static final String MIENTRAS = " (se registró en otra PC mientras esta estaba sin conexión)";

    /** Offline, the PC only checked its own copy before making the document;
     *  meanwhile another PC may have lent the bien, given it in comodato, put
     *  it in a resguardo or dado de baja. Same rules as ResguardoService,
     *  PrestamoService and ComodatoService, checked again on the server. */
    private static void exigirResguardoLibre(com.sibim.model.Resguardo d) throws SQLException, DocumentoEnConflicto {
        for (com.sibim.model.ResguardoItem it : d.getItems()) {
            if (it.getProductoId() == null) continue;
            String otro = primero("SELECT r.numero FROM resguardo_items i JOIN resguardos r ON r.id = i.resguardo_id "
                + "WHERE i.producto_id = ? AND r.estado = 'ACTIVO' AND r.id <> ? LIMIT 1", it.getProductoId(), d.getId());
            if (otro != null)
                throw new DocumentoEnConflicto("El bien " + nombre(it.getProductoNombre(), it.getProductoId())
                    + " ya está en el resguardo vigente " + otro + MIENTRAS);
        }
    }

    /** For a préstamo or comodato: the bien still exists, isn't de baja and
     *  isn't already lent or in comodato ({@code docId} excluded, so a retry
     *  after a half-finished sync doesn't find itself). */
    private static void exigirBienLibre(String productoId, String productoNombre, String docId)
            throws SQLException, DocumentoEnConflicto {
        String bien = nombre(productoNombre, productoId);
        String estado = primero("SELECT CASE WHEN fecha_baja IS NOT NULL THEN 'baja' "
            + "WHEN stock_actual <= 0 THEN 'sin' ELSE 'ok' END FROM products WHERE id = ?", productoId);
        if (estado == null) throw new DocumentoEnConflicto("El bien " + bien + " ya no existe en el servidor");
        if (estado.equals("baja")) throw new DocumentoEnConflicto("El bien " + bien + " fue dado de baja" + MIENTRAS);
        if (estado.equals("sin")) throw new DocumentoEnConflicto("El bien " + bien + " tiene cantidad 0" + MIENTRAS);
        String prestamo = primero("SELECT numero FROM prestamos WHERE producto_id = ? "
            + "AND estado IN ('ACTIVO','VENCIDO') AND id <> ? LIMIT 1", productoId, docId);
        if (prestamo != null)
            throw new DocumentoEnConflicto("El bien " + bien + " ya tiene el préstamo activo " + prestamo + MIENTRAS);
        String comodato = primero("SELECT numero FROM comodatos WHERE producto_id = ? "
            + "AND estado IN ('VIGENTE','VENCIDO') AND id <> ? LIMIT 1", productoId, docId);
        if (comodato != null)
            throw new DocumentoEnConflicto("El bien " + bien + " ya tiene el comodato vigente " + comodato + MIENTRAS);
    }

    private static String primero(String sql, String... parametros) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) ps.setString(i + 1, parametros[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static String nombre(String nombre, String id) {
        return "\"" + (nombre != null && !nombre.isBlank() ? nombre : id) + "\"";
    }

    // ─────────────────────────────── Outbox bookkeeping ────────────────────

    /** True for failures that will never succeed on retry (FK violations, duplicate keys,
     *  "not found" responses).  Such rows are marked DISCARDED immediately so
     *  requeueFailedChanges() doesn't loop on them forever. */
    private static boolean isPermanentFailure(Exception ex) {
        if (ex instanceof DocumentoEnConflicto) return true;
        if (ex instanceof SQLException sqle) {
            String state = sqle.getSQLState();
            // 23xxx = integrity constraint violation (FK, unique, not-null…)
            if (state != null && state.startsWith("23")) return true;
        }
        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        return msg.contains("no encontrado") || msg.contains("not found")
            || msg.contains("violates foreign key") || msg.contains("duplicate key");
    }

    /** Decides whether a failed row should be retried again (FAILED, requeued on the
     *  next tick) or given up on (DISCARDED). Permanent failures discard immediately;
     *  anything else discards once it has exhausted MAX_RETRY_ATTEMPTS — and, only in
     *  that second case, leaves an audit trail so the change isn't silently dropped
     *  (a permanent failure already has its own descriptive {@code error} column). */
    private static String resolveFailureStatus(Exception ex, int retryCountBeforeThisFailure,
                                                String entidad, String entidadId, String entidadNombre) {
        if (isPermanentFailure(ex)) return STATUS_DISCARDED;
        int attempts = retryCountBeforeThisFailure + 1;
        if (attempts >= MAX_RETRY_ATTEMPTS) {
            log.error("SyncService: {} {} descartado tras {} intento(s) fallidos: {}",
                entidad, entidadId, attempts, ex.getMessage());
            writeAuditEntry(entidad, entidadId, entidadNombre, "sync_descartado",
                "Se agotaron " + attempts + " intento(s) de sincronización — requiere revisión manual: "
                    + ex.getMessage());
            return STATUS_DISCARDED;
        }
        return STATUS_FAILED;
    }

    private static void writeAuditEntry(String entidad, String entidadId, String entidadNombre,
                                         String accion, String detalle) {
        try {
            AuditLog a = new AuditLog();
            a.setId(UUID.randomUUID().toString());
            a.setEntidad(entidad);
            a.setEntidadId(entidadId);
            a.setEntidadNombre(entidadNombre != null ? entidadNombre : entidadId);
            a.setAccion("offline_sync:" + accion);
            a.setDetalle(detalle);
            a.setCreadoEn(LocalDateTime.now());
            a.setUsuarioNombre("Sistema (offline sync)");
            new AuditLogRepository().logOnline(a);
        } catch (Exception e) {
            log.warn("SyncService: no se pudo escribir audit entry para {} {}", entidad, entidadId, e);
        }
    }

    private static void markOutbox(String table, int rowId, String status, String error) {
        String sql = STATUS_FAILED.equals(status)
            ? "UPDATE " + table + " SET status = ?, error = ?, retry_count = retry_count + 1 WHERE id = ?"
            : "UPDATE " + table + " SET status = ?, error = ? WHERE id = ?";
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, error);
            ps.setInt(3, rowId);
            OfflineStore.escribir(ps);
        } catch (SQLException e) {
            log.error("SyncService: no se pudo actualizar el estado de la cola local ({}#{})", table, rowId, e);
        }
    }

    static int countPending() {
        int total = 0;
        for (String table : new String[]{
                "category_outbox", "product_outbox", "movement_outbox",
                "conteo_outbox", "audit_log_outbox", "doc_outbox"}) {
            try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                    "SELECT COUNT(*) FROM " + table + " WHERE status IN ('" + STATUS_PENDING + "', '" + STATUS_FAILED + "')");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total += rs.getInt(1);
            } catch (SQLException e) {
                log.error("SyncService: no se pudo contar pendientes en {}", table, e);
            }
        }
        return total;
    }

    /** Retries transient failures on the next successful connectivity check. */
    static void requeueFailedChanges() {
        for (String table : new String[]{
                "category_outbox", "product_outbox", "movement_outbox",
                "conteo_outbox", "audit_log_outbox", "doc_outbox"}) {
            try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                    "UPDATE " + table + " SET status = ? WHERE status = ?")) {
                ps.setString(1, STATUS_PENDING);
                ps.setString(2, STATUS_FAILED);
                OfflineStore.escribir(ps);
            } catch (SQLException e) {
                log.error("SyncService: no se pudieron reencolar fallos en {}", table, e);
            }
        }
    }

    /** Deletes SYNCED rows so the outbox tables don't grow without bound over the app's
     *  lifetime — once a row is SYNCED its change already landed in Postgres, so the local
     *  copy has no further use (unlike FAILED/DISCARDED/CONFLICT, which stay around for
     *  requeueFailedChanges(), manual review, or resolveConflicto() respectively). */
    private static void purgeSyncedRows() {
        for (String table : new String[]{
                "category_outbox", "product_outbox", "movement_outbox",
                "conteo_outbox", "audit_log_outbox", "doc_outbox"}) {
            try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                    "DELETE FROM " + table + " WHERE status = ?")) {
                ps.setString(1, STATUS_SYNCED);
                OfflineStore.escribir(ps);
            } catch (SQLException e) {
                log.error("SyncService: no se pudo purgar filas SYNCED en {}", table, e);
            }
        }
        // conteo_items_outbox has no status column of its own — its rows are detail lines
        // for a conteo_outbox header, so once that header is gone (just purged above) the
        // matching items are orphaned dead weight.
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "DELETE FROM conteo_items_outbox WHERE conteo_id NOT IN (SELECT conteo_id FROM conteo_outbox)")) {
            OfflineStore.escribir(ps);
        } catch (SQLException e) {
            log.error("SyncService: no se pudo purgar conteo_items_outbox huérfanos", e);
        }
    }

    private static int countConflictRows() {
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                "SELECT COUNT(*) FROM product_outbox WHERE status = '" + STATUS_CONFLICT + "'");
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
            OfflineStore.sharedConnection();
        } catch (SQLException e) {
            return 0;
        }
        return countPending();
    }

    // ─────────────── Outbox error review (DISCARDED rows) ─────────────────

    /** One row from any outbox table that ended up permanently discarded. */
    public record OutboxEntry(
        String table,
        String tableLabel,
        int    rowId,
        String operacion,
        String entityLabel,
        String error,
        String createdAt
    ) {}

    /** All DISCARDED rows across every outbox table, newest first.
     *  Returns an empty list when no offline store exists. */
    public static List<OutboxEntry> getDiscarded() {
        try {
            OfflineStore.sharedConnection();
        } catch (SQLException e) {
            return List.of();
        }
        List<OutboxEntry> result = new ArrayList<>();
        queryDiscarded("category_outbox",  "Categoría",  "categoria_id", "nombre",          result);
        queryDiscarded("product_outbox",   "Bien",        "producto_id",  "nombre",          result);
        queryDiscarded("movement_outbox",  "Movimiento",  "movimiento_id","tipo",            result);
        queryDiscarded("conteo_outbox",    "Conteo físico","conteo_id",   "usuario_nombre",  result);
        queryDiscarded("audit_log_outbox", "Auditoría",   "audit_id",     "entidad_nombre",  result);
        queryDiscarded("doc_outbox",       "Documento",   "doc_id",       "tipo",            result);
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return result;
    }

    private static void queryDiscarded(String table, String tableLabel,
                                       String idCol, String nameCol,
                                       List<OutboxEntry> out) {
        // Conteos and audit entries are only ever added, so their outboxes have no "operacion"
        // column; selecting it failed and a discarded one was counted but never listed.
        boolean soloAltas = table.equals("conteo_outbox") || table.equals("audit_log_outbox");
        String sql = "SELECT id, " + (soloAltas ? "'ADD' AS operacion" : "operacion") + ", " + idCol + ", " + nameCol
            + ", error, created_at FROM " + table + " WHERE status = 'DISCARDED' ORDER BY id DESC";
        try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(nameCol);
                String id   = rs.getString(idCol);
                String label = (name != null && !name.isBlank()) ? name : id;
                out.add(new OutboxEntry(table, tableLabel, rs.getInt("id"),
                    rs.getString("operacion"), label,
                    rs.getString("error"), rs.getString("created_at")));
            }
        } catch (SQLException e) {
            log.error("SyncService: no se pudieron leer descartados de {}", table, e);
        }
    }

    /** Count of DISCARDED rows across all outbox tables. Returns 0 when no offline store exists. */
    public static int countDiscarded() {
        try {
            OfflineStore.sharedConnection();
        } catch (SQLException e) {
            return 0;
        }
        int total = 0;
        for (String table : new String[]{
                "category_outbox", "product_outbox", "movement_outbox",
                "conteo_outbox", "audit_log_outbox", "doc_outbox"}) {
            try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                    "SELECT COUNT(*) FROM " + table + " WHERE status = 'DISCARDED'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total += rs.getInt(1);
            } catch (SQLException e) {
                log.error("SyncService: no se pudo contar descartados en {}", table, e);
            }
        }
        return total;
    }

    /** Permanently deletes all DISCARDED rows from every outbox table. */
    public static void clearDiscarded() {
        try {
            OfflineStore.sharedConnection();
        } catch (SQLException e) {
            return;
        }
        for (String table : new String[]{
                "category_outbox", "product_outbox", "movement_outbox",
                "conteo_outbox", "audit_log_outbox", "doc_outbox"}) {
            try (PreparedStatement ps = OfflineStore.sharedConnection().prepareStatement(
                    "DELETE FROM " + table + " WHERE status = 'DISCARDED'")) {
                OfflineStore.escribir(ps);
            } catch (SQLException e) {
                log.error("SyncService: no se pudieron limpiar descartados de {}", table, e);
            }
        }
    }
}
