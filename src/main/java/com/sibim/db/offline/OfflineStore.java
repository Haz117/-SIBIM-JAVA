package com.sibim.db.offline;

import com.sibim.model.AuditLog;
import com.sibim.model.Categoria;
import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.util.ProductoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistent local store for "modo offline" — used instead of the purely
 * in-memory {@link com.sibim.db.DemoDataStore} when the real DB was
 * reachable at some point but currently isn't. Backed by a SQLite file at
 * {@code %USERPROFILE%\.sibim\offline.db} (schema: offline.sql), so data
 * survives app restarts and can be replayed against Postgres once the
 * connection comes back — see {@link SyncService}.
 *
 * Scope: only products, movements and categories participate in the
 * automatic sync (see the offline-mode plan). Working copies of these three
 * are kept in memory (loaded from SQLite on first use) so the existing
 * filtering logic — deliberately copied from DemoDataStore rather than
 * reinvented — can run the same way; every write is immediately persisted
 * to SQLite AND appended to the matching outbox table.
 */
public final class OfflineStore {

    private static final Logger log = LoggerFactory.getLogger(OfflineStore.class);

    private OfflineStore() {}

    private static Connection conn;
    private static boolean loaded = false;

    private static final List<Producto> PRODUCTOS = new ArrayList<>();
    private static final List<Categoria> CATEGORIAS = new ArrayList<>();
    private static final List<Movimiento> MOVIMIENTOS = new ArrayList<>();
    private static final Map<String, Producto> PRODUCTOS_MAP = new HashMap<>();

    /** Guards the read-recompute-write sequence in addMovimiento — mirrors
     *  DemoDataStore's STOCK_LOCK for the same reason (this store, too, can
     *  be hit from more than one background Thread at once). */
    private static final Object LOCK = new Object();

    // ───────────────────────── Connection / schema ─────────────────────────

    private static synchronized Connection conn() throws SQLException {
        if (conn == null) {
            try {
                Path dbDir = Path.of(System.getProperty("user.home"), ".sibim");
                Files.createDirectories(dbDir);

                Path legacyDb = dbDir.resolve("offline.db");
                Path encFile  = dbDir.resolve("offline.db.enc");
                Path workFile = dbDir.resolve("offline.db.work");

                javax.crypto.SecretKey key =
                    OfflineEncryption.keyFrom(OfflineKeyManager.deriveKey());

                // Migrate plaintext legacy DB on first run after encryption was introduced
                if (Files.exists(legacyDb) && !OfflineEncryption.isEncrypted(legacyDb)) {
                    log.info("offline.db: migrando a almacenamiento cifrado...");
                    OfflineEncryption.encryptFrom(legacyDb, encFile, key);
                    log.info("offline.db: migración completada");
                }

                // Stale work file from a previous crash — discard it; enc is authoritative
                if (Files.exists(encFile)) {
                    Files.deleteIfExists(workFile);
                }

                // Decrypt enc → work, with automatic one-time migration from the old
                // hostname-based key (SIBIM-v1) to the stable MachineGuid-based key (SIBIM-v2)
                if (Files.exists(encFile)) {
                    if (!OfflineEncryption.tryDecryptTo(encFile, workFile, key)) {
                        log.warn("offline.db: clave actual no coincide — probando clave legacy "
                            + "(¿se renombró la computadora?)...");
                        javax.crypto.SecretKey legacyKey =
                            OfflineEncryption.keyFrom(OfflineKeyManager.deriveLegacyKey());
                        OfflineEncryption.decryptTo(encFile, workFile, legacyKey);
                        log.info("offline.db: re-cifrando con clave estable (MachineGuid)...");
                        OfflineEncryption.encryptFrom(workFile, encFile, key);
                        OfflineEncryption.decryptTo(encFile, workFile, key);
                        log.info("offline.db: migración de clave completada");
                    }
                }

                conn = DriverManager.getConnection("jdbc:sqlite:" + workFile);
                conn.setAutoCommit(true);
                // Skip runSchema() on existing DBs — CREATE TABLE IF NOT EXISTS statements
                // executed via executeBatch() on a non-empty DB trigger a SQLite JDBC GC
                // quirk on JDK 21 where native statement handles get finalized mid-batch.
                // runOfflineMigrations() is fully idempotent and handles schema evolution.
                if (!schemaExists(conn)) {
                    runSchema(conn);
                }
                runOfflineMigrations(conn);

                // Re-encrypt on JVM shutdown so the plaintext work file doesn't linger
                final Path fEnc = encFile, fWork = workFile;
                final javax.crypto.SecretKey fKey = key;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        if (conn != null && !conn.isClosed()) conn.close();
                    } catch (Exception ignored) {}
                    try {
                        OfflineEncryption.encryptFrom(fWork, fEnc, fKey);
                    } catch (IOException e) {
                        log.error("Error al cifrar offline.db al cerrar", e);
                    }
                }, "offline-db-encrypt-on-shutdown"));

            } catch (IOException e) {
                throw new SQLException("No se pudo abrir el almacén offline local", e);
            }
        }
        return conn;
    }

    private static boolean schemaExists(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='products'");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** Idempotent column/table additions for existing offline.db files that pre-date schema changes.
     *  SQLite doesn't support IF NOT EXISTS in ALTER TABLE, so we swallow the duplicate-column
     *  error: on a fresh DB these succeed; on an old one they're silent no-ops.
     *  New tables use CREATE TABLE IF NOT EXISTS — always idempotent. */
    private static void runOfflineMigrations(Connection c) {
        // M1 (2025): server_snapshot_at for offline conflict detection
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE product_outbox ADD COLUMN server_snapshot_at TEXT");
        } catch (SQLException ignored) {}
        // M1b (2026): preserve pending transfer state in the local mirror.
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE movements ADD COLUMN estado TEXT NOT NULL DEFAULT 'APROBADO'");
        } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE movement_outbox ADD COLUMN estado TEXT NOT NULL DEFAULT 'APROBADO'");
        } catch (SQLException ignored) {}
        // M2 (2025): conteo físico offline outbox
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS conteo_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conteo_id TEXT NOT NULL, usuario_id TEXT, usuario_nombre TEXT NOT NULL,
                    total_contados INTEGER NOT NULL DEFAULT 0, total_discrepancias INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'PENDING', error TEXT)""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS conteo_items_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, conteo_id TEXT NOT NULL,
                    item_id TEXT NOT NULL, producto_id TEXT NOT NULL, producto_nombre TEXT NOT NULL,
                    area TEXT, stock_sistema INTEGER NOT NULL, stock_contado INTEGER NOT NULL,
                    ajustado INTEGER NOT NULL DEFAULT 0)""");
        } catch (SQLException ignored) {}
        // M3 (2025): audit log offline outbox
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS audit_log_outbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, audit_id TEXT NOT NULL,
                    entidad TEXT NOT NULL, entidad_id TEXT NOT NULL, entidad_nombre TEXT,
                    accion TEXT NOT NULL, detalle TEXT, usuario_id TEXT,
                    usuario_nombre TEXT NOT NULL, created_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING', error TEXT)""");
        } catch (SQLException ignored) {}
        // M4 (2026): cached_at para caducidad del caché de credenciales offline (30 días).
        // Columna nullable — filas previas quedan con NULL, que findCachedUserByUsername
        // trata como "expirado", forzando re-autenticación online la primera vez.
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE users_cache ADD COLUMN cached_at TEXT");
        } catch (SQLException ignored) {}
        // M5: factura_url para foto de factura
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE products ADD COLUMN factura_url TEXT");
        } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) {
            st.execute("ALTER TABLE product_outbox ADD COLUMN factura_url TEXT");
        } catch (SQLException ignored) {}
        // M6: numero_serie, marca, modelo
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN numero_serie TEXT"); } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN marca TEXT"); } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE products ADD COLUMN modelo TEXT"); } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN numero_serie TEXT"); } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN marca TEXT"); } catch (SQLException ignored) {}
        try (Statement st = c.createStatement()) { st.execute("ALTER TABLE product_outbox ADD COLUMN modelo TEXT"); } catch (SQLException ignored) {}
    }

    private static void runSchema(Connection c) throws SQLException {
        try (InputStream in = OfflineStore.class.getResourceAsStream("/offline.sql")) {
            if (in == null) throw new SQLException("No se encontró offline.sql en el classpath");
            String sql;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                sql = r.lines().collect(Collectors.joining("\n"));
            }
            // Use addBatch/executeBatch instead of per-statement execute() to keep a
            // strong reference to the Statement throughout, preventing the JIT from
            // prematurely clearing it for GC while the loop is still running — a
            // known SQLite JDBC quirk on JDK 21 with concurrent GC.
            try (Statement st = c.createStatement()) {
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty()) st.addBatch(trimmed);
                }
                st.executeBatch();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static synchronized void ensureLoaded() throws SQLException {
        if (loaded) return;
        CATEGORIAS.clear();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM categories")) {
            while (rs.next()) CATEGORIAS.add(mapCategoria(rs));
        }
        PRODUCTOS.clear();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM products")) {
            while (rs.next()) PRODUCTOS.add(mapProducto(rs));
        }
        PRODUCTOS_MAP.clear();
        PRODUCTOS.forEach(p -> PRODUCTOS_MAP.put(p.getId(), p));
        MOVIMIENTOS.clear();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM movements ORDER BY created_at DESC")) {
            while (rs.next()) MOVIMIENTOS.add(mapMovimiento(rs));
        }
        recomputeCategoriaCounts();
        loaded = true;
    }

    /** Forces the next offline read to reload from SQLite instead of reusing
     *  the in-memory snapshot — called when leaving offline mode (see
     *  SyncService) so that a *second* offline stint later in the same run
     *  picks up whatever was cached (see cacheProductos/cacheCategorias/
     *  cacheMovimientos below) while the app was back online in between,
     *  instead of replaying the stale in-memory copy from the first stint. */
    public static synchronized void invalidateCache() {
        loaded = false;
    }

    // ─────────────── Write-through cache from ONLINE reads (bug fix) ───────
    // Historically only users_cache existed (see cacheUser below) — going
    // offline mid-session with real data already on screen showed an EMPTY
    // inventory, because the SQLite mirror tables were only ever written to
    // by offline WRITES, never refreshed by ordinary online reads. These
    // three extend the same "cache what you saw while connected" pattern to
    // products/categories/movements, called from the repositories'
    // online-mode branches after every successful list query. Best-effort:
    // a caching failure must never break the real (online) read it's
    // piggybacking on, so every exception here is swallowed and logged.

    public static synchronized void cacheProductos(List<Producto> serverProductos) {
        if (serverProductos == null || serverProductos.isEmpty()) return;
        try {
            for (Producto p : serverProductos) cacheProductoSnapshot(p);
        } catch (SQLException e) {
            log.warn("OfflineStore: no se pudo refrescar el caché local de productos", e);
        }
    }

    public static synchronized void cacheCategorias(List<Categoria> serverCategorias) {
        if (serverCategorias == null || serverCategorias.isEmpty()) return;
        try {
            for (Categoria c : serverCategorias) cacheCategoriaSnapshot(c);
        } catch (SQLException e) {
            log.warn("OfflineStore: no se pudo refrescar el caché local de categorías", e);
        }
    }

    public static synchronized void cacheMovimientos(List<Movimiento> serverMovimientos) {
        if (serverMovimientos == null || serverMovimientos.isEmpty()) return;
        try {
            for (Movimiento m : serverMovimientos) cacheMovimientoSnapshot(m);
        } catch (SQLException e) {
            log.warn("OfflineStore: no se pudo refrescar el caché local de movimientos", e);
        }
    }

    /** Unlike {@link #persistProducto}, this preserves the product's own
     *  (real, server-side) timestamps instead of stamping "now" — stamping
     *  now would poison future offline-conflict detection, which compares
     *  the server's real updated_at against what this snapshot claims it
     *  was. Doesn't touch the in-memory PRODUCTOS list or the outbox —
     *  purely a passive local mirror of what the server has. */
    private static void cacheProductoSnapshot(Producto p) throws SQLException {
        try (PreparedStatement cleanup = conn().prepareStatement(
                "DELETE FROM products WHERE codigo = ? AND id <> ?")) {
            cleanup.setString(1, p.getCodigo());
            cleanup.setString(2, p.getId());
            cleanup.executeUpdate();
        }
        String sql = """
            INSERT INTO products (id, nombre, codigo, descripcion, categoria_id, precio_compra,
                precio_venta, stock_actual, stock_minimo, stock_maximo, unidad, proveedor,
                fecha_vencimiento, foto_url, factura_url, numero_serie, marca, modelo, ubicacion, area, resguardante, fecha_baja, motivo_baja,
                created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                nombre=excluded.nombre, codigo=excluded.codigo, descripcion=excluded.descripcion,
                categoria_id=excluded.categoria_id, precio_compra=excluded.precio_compra,
                precio_venta=excluded.precio_venta, stock_actual=excluded.stock_actual,
                stock_minimo=excluded.stock_minimo, stock_maximo=excluded.stock_maximo,
                unidad=excluded.unidad, proveedor=excluded.proveedor,
                fecha_vencimiento=excluded.fecha_vencimiento, foto_url=excluded.foto_url,
                factura_url=excluded.factura_url,
                numero_serie=excluded.numero_serie, marca=excluded.marca, modelo=excluded.modelo,
                ubicacion=excluded.ubicacion, area=excluded.area, resguardante=excluded.resguardante,
                fecha_baja=excluded.fecha_baja, motivo_baja=excluded.motivo_baja,
                updated_at=excluded.updated_at
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
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
            ps.setString(13, p.getFechaVencimiento() != null ? p.getFechaVencimiento().toString() : null);
            ps.setString(14, p.getFotoUrl());
            ps.setString(15, p.getFacturaUrl());
            ps.setString(16, p.getNumeroSerie());
            ps.setString(17, p.getMarca());
            ps.setString(18, p.getModelo());
            ps.setString(19, p.getUbicacion());
            ps.setString(20, p.getArea());
            ps.setString(21, p.getResguardante());
            ps.setString(22, p.getFechaBaja() != null ? p.getFechaBaja().toString() : null);
            ps.setString(23, p.getMotivoBaja());
            ps.setString(24, str(p.getCreadoEn()));
            ps.setString(25, str(p.getActualizadoEn()));
            ps.executeUpdate();
        }
    }

    private static void cacheCategoriaSnapshot(Categoria c) throws SQLException {
        try (PreparedStatement cleanup = conn().prepareStatement(
                "DELETE FROM categories WHERE nombre = ? AND id <> ?")) {
            cleanup.setString(1, c.getNombre());
            cleanup.setString(2, c.getId());
            cleanup.executeUpdate();
        }
        String sql = """
            INSERT INTO categories (id, nombre, descripcion, color, icono, created_at)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET nombre=excluded.nombre, descripcion=excluded.descripcion,
                color=excluded.color, icono=excluded.icono
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getDescripcion());
            ps.setString(4, c.getColor());
            ps.setString(5, c.getIcono());
            ps.setString(6, str(c.getCreadoEn() != null ? c.getCreadoEn() : LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    /** Movements are effectively immutable once created, so DO NOTHING on a
     *  repeat id (re-fetching a page/date-range that overlaps a previous
     *  cache pass) is enough — no need for persistMovimiento's insert-only
     *  assumption to become an UPDATE here. */
    private static void cacheMovimientoSnapshot(Movimiento m) throws SQLException {
        String sql = """
            INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo,
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at, estado)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO NOTHING
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
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
            ps.setString(13, str(m.getCreadoEn()));
            ps.setString(14, m.getEstado() != null ? m.getEstado() : Movimiento.ESTADO_APROBADO);
            ps.executeUpdate();
        }
    }

    private static void recomputeCategoriaCounts() {
        var counts = PRODUCTOS.stream()
            .collect(Collectors.groupingBy(Producto::getCategoriaId, Collectors.counting()));
        CATEGORIAS.forEach(c -> c.setTotalProductos(counts.getOrDefault(c.getId(), 0L).intValue()));
    }

    // ───────────────────────────── Categorías ───────────────────────────────

    public static synchronized List<Categoria> findAllCategorias() throws SQLException {
        ensureLoaded();
        recomputeCategoriaCounts();
        return new ArrayList<>(CATEGORIAS);
    }

    public static synchronized Optional<Categoria> findCategoriaById(String id) throws SQLException {
        ensureLoaded();
        return CATEGORIAS.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public static void saveCategoria(Categoria c) throws SQLException {
        ensureLoaded();
        Categoria anterior = CATEGORIAS.stream().filter(x -> x.getId().equals(c.getId())).findFirst().orElse(null);
        Connection db = conn();
        db.setAutoCommit(false);
        try {
        CATEGORIAS.removeIf(x -> x.getId().equals(c.getId()));
        CATEGORIAS.add(c);
        String sql = """
            INSERT INTO categories (id, nombre, descripcion, color, icono, created_at)
            VALUES (?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET nombre=excluded.nombre, descripcion=excluded.descripcion,
                color=excluded.color, icono=excluded.icono
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getNombre());
            ps.setString(3, c.getDescripcion());
            ps.setString(4, c.getColor());
            ps.setString(5, c.getIcono());
            ps.setString(6, str(c.getCreadoEn() != null ? c.getCreadoEn() : LocalDateTime.now()));
            ps.executeUpdate();
        }
        enqueueCategory("SAVE", c);
        db.commit();
        } catch (SQLException e) {
            db.rollback();
            CATEGORIAS.removeIf(x -> x.getId().equals(c.getId()));
            if (anterior != null) CATEGORIAS.add(anterior);
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    public static void deleteCategoria(String id) throws SQLException {
        ensureLoaded();
        Categoria c = CATEGORIAS.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        Connection db = conn();
        db.setAutoCommit(false);
        try {
        CATEGORIAS.removeIf(x -> x.getId().equals(id));
        try (PreparedStatement ps = db.prepareStatement("DELETE FROM categories WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        if (c != null) enqueueCategory("DELETE", c);
        db.commit();
        } catch (SQLException e) {
            db.rollback();
            if (c != null) CATEGORIAS.add(c);
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    public static synchronized boolean tieneProductosEnCategoria(String categoriaId) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream().anyMatch(p -> categoriaId.equals(p.getCategoriaId()));
    }

    // ───────────────────────────── Productos ────────────────────────────────

    public static synchronized List<Producto> findAllProductos(Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream()
            .filter(p -> accessibleAreas == null || accessibleAreas.contains(p.getArea()))
            .sorted(Comparator.comparing(Producto::getNombre))
            .collect(Collectors.toList());
    }

    public static synchronized Optional<Producto> findProductoById(String id) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public static synchronized Optional<Producto> findProductoByCodigo(String codigo) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream().filter(p -> p.getCodigo().equalsIgnoreCase(codigo)).findFirst();
    }

    public static synchronized boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream()
            .anyMatch(p -> p.getCodigo().equalsIgnoreCase(codigo)
                       && !p.getId().equals(excludeId != null ? excludeId : ""));
    }

    public static synchronized void saveProducto(Producto p) throws SQLException {
        ensureLoaded();
        // Capture the server's last-known updated_at BEFORE overwriting the product
        // in memory. SyncService uses this to detect whether the server was modified
        // by another machine while this PC was offline (conflict detection).
        String serverSnapshotAt = PRODUCTOS.stream()
            .filter(x -> x.getId().equals(p.getId()))
            .findFirst()
            .map(x -> str(x.getActualizadoEn()))
            .orElse(null);
        Producto anterior = PRODUCTOS_MAP.get(p.getId());
        Connection db = conn();
        db.setAutoCommit(false);
        try {
            PRODUCTOS.removeIf(x -> x.getId().equals(p.getId()));
            PRODUCTOS.add(p);
            PRODUCTOS_MAP.put(p.getId(), p);
            persistProducto(p);
            recomputeCategoriaCounts();
            enqueueProduct("SAVE", p, serverSnapshotAt);
            db.commit();
        } catch (SQLException e) {
            db.rollback();
            PRODUCTOS.removeIf(x -> x.getId().equals(p.getId()));
            if (anterior != null) {
                PRODUCTOS.add(anterior);
                PRODUCTOS_MAP.put(anterior.getId(), anterior);
            } else PRODUCTOS_MAP.remove(p.getId());
            recomputeCategoriaCounts();
            throw e;
        } finally {
            db.setAutoCommit(true);
        }
    }

    public static synchronized void darDeBajaProducto(String id, String motivo) throws SQLException {
        ensureLoaded();
        Producto p = PRODUCTOS.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (p == null) return;
        String serverSnapshotAt = str(p.getActualizadoEn()); // capture before mutation
        p.setFechaBaja(LocalDate.now());
        p.setMotivoBaja(motivo);
        p.setActualizadoEn(LocalDateTime.now());
        persistProducto(p);
        enqueueProduct("BAJA", p, serverSnapshotAt);
    }

    public static synchronized void reactivarProducto(String id) throws SQLException {
        ensureLoaded();
        Producto p = PRODUCTOS.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (p == null) return;
        String serverSnapshotAt = str(p.getActualizadoEn()); // capture before mutation
        p.setFechaBaja(null);
        p.setMotivoBaja(null);
        p.setActualizadoEn(LocalDateTime.now());
        persistProducto(p);
        enqueueProduct("REACTIVAR", p, serverSnapshotAt);
    }

    public static synchronized void updateProductoStock(String id, int newStock) throws SQLException {
        ensureLoaded();
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> {
            p.setStockActual(newStock);
            p.setActualizadoEn(LocalDateTime.now());
        });
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE products SET stock_actual = ?, updated_at = ? WHERE id = ?")) {
            ps.setInt(1, newStock);
            ps.setString(2, str(LocalDateTime.now()));
            ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    public static synchronized void updateProductoArea(String id, String newArea) throws SQLException {
        ensureLoaded();
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> {
            p.setArea(newArea);
            p.setActualizadoEn(LocalDateTime.now());
        });
        try (PreparedStatement ps = conn().prepareStatement(
                "UPDATE products SET area = ?, updated_at = ? WHERE id = ?")) {
            ps.setString(1, newArea);
            ps.setString(2, str(LocalDateTime.now()));
            ps.setString(3, id);
            ps.executeUpdate();
        }
    }

    private static void persistProducto(Producto p) throws SQLException {
        String sql = """
            INSERT INTO products (id, nombre, codigo, descripcion, categoria_id, precio_compra,
                precio_venta, stock_actual, stock_minimo, stock_maximo, unidad, proveedor,
                fecha_vencimiento, foto_url, factura_url, numero_serie, marca, modelo, ubicacion, area, resguardante, fecha_baja, motivo_baja,
                created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                nombre=excluded.nombre, codigo=excluded.codigo, descripcion=excluded.descripcion,
                categoria_id=excluded.categoria_id, precio_compra=excluded.precio_compra,
                precio_venta=excluded.precio_venta, stock_actual=excluded.stock_actual,
                stock_minimo=excluded.stock_minimo, stock_maximo=excluded.stock_maximo,
                unidad=excluded.unidad, proveedor=excluded.proveedor,
                fecha_vencimiento=excluded.fecha_vencimiento, foto_url=excluded.foto_url,
                factura_url=excluded.factura_url,
                numero_serie=excluded.numero_serie, marca=excluded.marca, modelo=excluded.modelo,
                ubicacion=excluded.ubicacion, area=excluded.area, resguardante=excluded.resguardante,
                fecha_baja=excluded.fecha_baja, motivo_baja=excluded.motivo_baja,
                updated_at=excluded.updated_at
            """;
        LocalDateTime now = LocalDateTime.now();
        if (p.getCreadoEn() == null) p.setCreadoEn(now);
        p.setActualizadoEn(now);
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
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
            ps.setString(13, p.getFechaVencimiento() != null ? p.getFechaVencimiento().toString() : null);
            ps.setString(14, p.getFotoUrl());
            ps.setString(15, p.getFacturaUrl());
            ps.setString(16, p.getNumeroSerie());
            ps.setString(17, p.getMarca());
            ps.setString(18, p.getModelo());
            ps.setString(19, p.getUbicacion());
            ps.setString(20, p.getArea());
            ps.setString(21, p.getResguardante());
            ps.setString(22, p.getFechaBaja() != null ? p.getFechaBaja().toString() : null);
            ps.setString(23, p.getMotivoBaja());
            ps.setString(24, str(p.getCreadoEn()));
            ps.setString(25, str(now));
            ps.executeUpdate();
        }
    }

    // ───────────────────────────── Movimientos ──────────────────────────────

    public static synchronized List<Movimiento> findAllMovimientos(Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static synchronized List<Movimiento> findMovimientosByProducto(String productoId, Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> productoId.equals(m.getProductoId()))
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static synchronized List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta, Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .filter(m -> {
                if (m.getCreadoEn() == null) return false;
                LocalDate d = m.getCreadoEn().toLocalDate();
                if (desde != null && d.isBefore(desde)) return false;
                if (hasta != null && d.isAfter(hasta)) return false;
                return true;
            })
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static synchronized Optional<String> findProductoIdByMovimientoId(String movimientoId) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> m.getId().equals(movimientoId))
            .map(Movimiento::getProductoId)
            .findFirst();
    }

    private static String areaOfProducto(String productoId) {
        Producto p = PRODUCTOS_MAP.get(productoId);
        return p != null ? p.getArea() : null;
    }

    public static synchronized void addMovimiento(Movimiento m) throws SQLException {
        addMovimiento(m, null);
    }

    /** Saves a transfer request locally without changing stock or area. */
    public static synchronized void addMovimientoPendiente(Movimiento m) throws SQLException {
        ensureLoaded();
        if (m.getId() == null) m.setId(UUID.randomUUID().toString());
        Producto p = PRODUCTOS_MAP.get(m.getProductoId());
        if (p == null) throw new SQLException("Producto no encontrado: " + m.getProductoId());
        Connection c = conn();
        c.setAutoCommit(false);
        try {
            m.setEstado(Movimiento.ESTADO_PENDIENTE);
            m.setAreaOrigen(p.getArea());
            m.setStockAnterior(p.getStockActual());
            m.setStockNuevo(p.getStockActual());
            if (m.getCreadoEn() == null) m.setCreadoEn(LocalDateTime.now());
            persistMovimiento(m);
            enqueueMovement("ADD", m);
            c.commit();
            MOVIMIENTOS.add(0, m);
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Same "reject instead of overwrite a stale AJUSTE" contract as
     *  DemoDataStore/MovimientoRepository — see those for why. */
    public static synchronized void addMovimiento(Movimiento m, Integer expectedStockAnterior) throws SQLException {
        ensureLoaded();
        synchronized (LOCK) {
            Producto p = PRODUCTOS_MAP.get(m.getProductoId());
            if (p == null) throw new SQLException("Producto no encontrado: " + m.getProductoId());
            int stockActual = p.getStockActual();

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
            if (m.getCreadoEn() == null) m.setCreadoEn(LocalDateTime.now());

            if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
                m.setAreaOrigen(p.getArea());
            }
            Connection c = conn();
            c.setAutoCommit(false);
            try {
                if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null)
                    updateProductoArea(m.getProductoId(), m.getAreaDestino());
                persistMovimiento(m);
                updateProductoStock(m.getProductoId(), stockNuevo);
                enqueueMovement("ADD", m);
                c.commit();
                MOVIMIENTOS.add(0, m);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /** Only the most recent movement for a product may be deleted — same
     *  rule as DemoDataStore/MovimientoRepository (MOVIMIENTOS is
     *  newest-first). */
    public static synchronized void deleteMovimiento(String id) throws SQLException {
        ensureLoaded();
        synchronized (LOCK) {
            Movimiento m = MOVIMIENTOS.stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> new SQLException("Movimiento no encontrado: " + id));
            int idx = MOVIMIENTOS.indexOf(m);
            boolean hasNewer = MOVIMIENTOS.subList(0, idx).stream()
                .anyMatch(other -> other.getProductoId().equals(m.getProductoId()));
            if (hasNewer) {
                throw new SQLException("Solo se puede eliminar el movimiento mas reciente de este producto: "
                    + "existen movimientos registrados despues de este.");
            }
            Connection c = conn();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM movements WHERE id = ?")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                updateProductoStock(m.getProductoId(), m.getStockAnterior());
                if (m.getAreaOrigen() != null) updateProductoArea(m.getProductoId(), m.getAreaOrigen());
                enqueueMovement("DELETE", m);
                c.commit();
                MOVIMIENTOS.remove(m);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void persistMovimiento(Movimiento m) throws SQLException {
        String sql = """
            INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo,
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at, estado)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
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
            ps.setString(13, str(m.getCreadoEn()));
            ps.setString(14, m.getEstado() != null ? m.getEstado() : Movimiento.ESTADO_APROBADO);
            ps.executeUpdate();
        }
    }

    // ─────────────────────── Caché de usuarios (solo lectura) ───────────────

    /** Refreshed after every successful ONLINE login (see AuthService) —
     *  never written from the offline side. Lets a previously-seen real
     *  user keep logging in if the connection later drops, for up to
     *  {@link #OFFLINE_CACHE_TTL_DAYS} days before requiring re-authentication online. */
    static final int OFFLINE_CACHE_TTL_DAYS = 30;

    public static void cacheUser(Usuario u) throws SQLException {
        String sql = """
            INSERT INTO users_cache (id, username, password_hash, nombre, rol, area, debe_cambiar_password, cached_at)
            VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET username=excluded.username, password_hash=excluded.password_hash,
                nombre=excluded.nombre, rol=excluded.rol, area=excluded.area,
                debe_cambiar_password=excluded.debe_cambiar_password, cached_at=excluded.cached_at
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getNombre());
            ps.setString(5, u.getRol().getCodigo());
            ps.setString(6, u.getArea());
            ps.setInt(7, u.isDebeCambiarPassword() ? 1 : 0);
            ps.setString(8, str(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    /**
     * Returns the cached user only if:
     * (a) the entry exists, AND
     * (b) it was cached within the last {@link #OFFLINE_CACHE_TTL_DAYS} days.
     * Expired entries return {@link Optional#empty()} so AuthService can show
     * a meaningful "reconnect required" message rather than accepting stale credentials.
     */
    public static Optional<Usuario> findCachedUserByUsername(String username) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM users_cache WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String cachedAtStr = rs.getString("cached_at");
                if (cachedAtStr != null) {
                    try {
                        LocalDateTime cachedAt = LocalDateTime.parse(cachedAtStr);
                        if (cachedAt.isBefore(LocalDateTime.now().minusDays(OFFLINE_CACHE_TTL_DAYS))) {
                            return Optional.empty(); // caché caducado
                        }
                    } catch (Exception ignored) {}
                }
                Usuario u = new Usuario();
                u.setId(rs.getString("id"));
                u.setUsername(rs.getString("username"));
                u.setPasswordHash(rs.getString("password_hash"));
                u.setNombre(rs.getString("nombre"));
                u.setRol(Rol.fromCodigo(rs.getString("rol")));
                u.setArea(rs.getString("area"));
                u.setDebeCambiarPassword(rs.getInt("debe_cambiar_password") != 0);
                return Optional.of(u);
            }
        }
    }

    // ─────────────────────────────── Outbox ──────────────────────────────────

    private static void enqueueProduct(String operacion, Producto p, String serverSnapshotAt) throws SQLException {
        String sql = """
            INSERT INTO product_outbox (operacion, producto_id, nombre, codigo, descripcion,
                categoria_id, precio_compra, precio_venta, stock_actual, stock_minimo, stock_maximo,
                unidad, proveedor, fecha_vencimiento, foto_url, factura_url, numero_serie, marca, modelo, ubicacion, area, resguardante,
                motivo_baja, created_at, server_snapshot_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, operacion);
            ps.setString(i++, p.getId());
            ps.setString(i++, p.getNombre());
            ps.setString(i++, p.getCodigo());
            ps.setString(i++, p.getDescripcion());
            ps.setString(i++, p.getCategoriaId());
            ps.setBigDecimal(i++, p.getPrecioCompra());
            ps.setBigDecimal(i++, p.getPrecioVenta());
            ps.setInt(i++, p.getStockActual());
            ps.setInt(i++, p.getStockMinimo());
            ps.setInt(i++, p.getStockMaximo());
            ps.setString(i++, p.getUnidad() != null ? p.getUnidad().getCodigo() : "pieza");
            ps.setString(i++, p.getProveedor());
            ps.setString(i++, p.getFechaVencimiento() != null ? p.getFechaVencimiento().toString() : null);
            ps.setString(i++, p.getFotoUrl());
            ps.setString(i++, p.getFacturaUrl());
            ps.setString(i++, p.getNumeroSerie());
            ps.setString(i++, p.getMarca());
            ps.setString(i++, p.getModelo());
            ps.setString(i++, p.getUbicacion());
            ps.setString(i++, p.getArea());
            ps.setString(i++, p.getResguardante());
            ps.setString(i++, p.getMotivoBaja());
            ps.setString(i++, str(LocalDateTime.now()));
            ps.setString(i, serverSnapshotAt);
            ps.executeUpdate();
        }
    }

    private static void enqueueMovement(String operacion, Movimiento m) throws SQLException {
        String sql = """
            INSERT INTO movement_outbox (operacion, movimiento_id, producto_id, tipo, cantidad,
                motivo, referencia, area_destino, usuario_id, usuario_nombre, created_at, estado)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, operacion);
            ps.setString(i++, m.getId());
            ps.setString(i++, m.getProductoId());
            ps.setString(i++, m.getTipo().getCodigo());
            ps.setInt(i++, m.getCantidad());
            ps.setString(i++, m.getMotivo());
            ps.setString(i++, m.getReferencia());
            ps.setString(i++, m.getAreaDestino());
            ps.setString(i++, m.getUsuarioId());
            ps.setString(i++, m.getUsuarioNombre());
            ps.setString(i++, str(LocalDateTime.now()));
            ps.setString(i, m.getEstado() != null ? m.getEstado() : Movimiento.ESTADO_APROBADO);
            ps.executeUpdate();
        }
    }

    private static void enqueueCategory(String operacion, Categoria c) throws SQLException {
        String sql = """
            INSERT INTO category_outbox (operacion, categoria_id, nombre, descripcion, color, icono, created_at)
            VALUES (?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, operacion);
            ps.setString(2, c.getId());
            ps.setString(3, c.getNombre());
            ps.setString(4, c.getDescripcion());
            ps.setString(5, c.getColor());
            ps.setString(6, c.getIcono());
            ps.setString(7, str(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    // ─────────────────────── ConteoFísico (outbox only) ───────────────────────

    /** Queues a completed conteo session (header + items) for replay against
     *  Postgres when the connection comes back. The conteo is NOT cached in
     *  memory — it's append-only and only shown in the admin history view,
     *  which is unavailable offline anyway. */
    public static void saveConteo(ConteoFisico c) throws SQLException {
        String sqlHeader = """
            INSERT INTO conteo_outbox (conteo_id, usuario_id, usuario_nombre,
                total_contados, total_discrepancias, created_at)
            VALUES (?,?,?,?,?,?)
            """;
        String sqlItem = """
            INSERT INTO conteo_items_outbox (conteo_id, item_id, producto_id, producto_nombre,
                area, stock_sistema, stock_contado, ajustado)
            VALUES (?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sqlHeader)) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getUsuarioId());
            ps.setString(3, c.getUsuarioNombre());
            ps.setInt(4, c.getTotalContados());
            ps.setInt(5, c.getTotalDiscrepancias());
            ps.setString(6, str(c.getCreadoEn() != null ? c.getCreadoEn() : LocalDateTime.now()));
            ps.executeUpdate();
        }
        if (c.getItems() != null) {
            try (PreparedStatement ps = conn().prepareStatement(sqlItem)) {
                for (ConteoItem it : c.getItems()) {
                    ps.setString(1, c.getId());
                    ps.setString(2, it.getId());
                    ps.setString(3, it.getProductoId());
                    ps.setString(4, it.getProductoNombre());
                    ps.setString(5, it.getArea());
                    ps.setInt(6, it.getStockSistema());
                    ps.setInt(7, it.getStockContado());
                    ps.setInt(8, it.isAjustado() ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    // ─────────────────────────── AuditLog (outbox only) ───────────────────────

    /** Reads the queued audit trail from the offline SQLite outbox so the admin
     *  audit dialog still works while Postgres is unreachable. */
    public static List<AuditLog> findAuditLog(int limit) throws SQLException {
        List<AuditLog> list = new ArrayList<>();
        String sql = "SELECT * FROM audit_log_outbox ORDER BY id DESC LIMIT ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuditLog a = new AuditLog();
                    a.setId(rs.getString("audit_id"));
                    a.setEntidad(rs.getString("entidad"));
                    a.setEntidadId(rs.getString("entidad_id"));
                    a.setEntidadNombre(rs.getString("entidad_nombre"));
                    a.setAccion(rs.getString("accion"));
                    a.setDetalle(rs.getString("detalle"));
                    a.setUsuarioId(rs.getString("usuario_id"));
                    a.setUsuarioNombre(rs.getString("usuario_nombre"));
                    String createdAt = rs.getString("created_at");
                    if (createdAt != null) {
                        try {
                            a.setCreadoEn(LocalDateTime.parse(createdAt));
                        } catch (Exception ignored) {
                            a.setCreadoEn(LocalDateTime.now());
                        }
                    }
                    list.add(a);
                }
            }
        }
        return list;
    }

    /** Queues an audit entry for replay when Postgres comes back. Never throws —
     *  mirrors AuditLogRepository.log()'s non-blocking contract. */
    public static void logAudit(AuditLog a) {
        String sql = """
            INSERT INTO audit_log_outbox (audit_id, entidad, entidad_id, entidad_nombre, accion,
                detalle, usuario_id, usuario_nombre, created_at)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, a.getId());
            ps.setString(2, a.getEntidad());
            ps.setString(3, a.getEntidadId());
            ps.setString(4, a.getEntidadNombre());
            ps.setString(5, a.getAccion());
            ps.setString(6, a.getDetalle());
            ps.setString(7, a.getUsuarioId());
            ps.setString(8, a.getUsuarioNombre());
            ps.setString(9, str(a.getCreadoEn() != null ? a.getCreadoEn() : LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // Non-blocking: mirror AuditLogRepository.log() behaviour
        }
    }

    static Connection sharedConnection() throws SQLException { return conn(); }

    // ───────────────────────────── Row mapping ──────────────────────────────

    private static Categoria mapCategoria(ResultSet rs) throws SQLException {
        Categoria c = new Categoria();
        c.setId(rs.getString("id"));
        c.setNombre(rs.getString("nombre"));
        c.setDescripcion(rs.getString("descripcion"));
        c.setColor(rs.getString("color"));
        c.setIcono(rs.getString("icono"));
        c.setCreadoEn(dt(rs.getString("created_at")));
        return c;
    }

    private static Producto mapProducto(ResultSet rs) throws SQLException {
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
        String fv = rs.getString("fecha_vencimiento");
        if (fv != null) p.setFechaVencimiento(LocalDate.parse(fv));
        p.setFotoUrl(rs.getString("foto_url"));
        p.setFacturaUrl(rs.getString("factura_url"));
        p.setNumeroSerie(rs.getString("numero_serie"));
        p.setMarca(rs.getString("marca"));
        p.setModelo(rs.getString("modelo"));
        p.setUbicacion(rs.getString("ubicacion"));
        p.setArea(rs.getString("area"));
        p.setResguardante(rs.getString("resguardante"));
        String fb = rs.getString("fecha_baja");
        if (fb != null) p.setFechaBaja(LocalDate.parse(fb));
        p.setMotivoBaja(rs.getString("motivo_baja"));
        p.setCreadoEn(dt(rs.getString("created_at")));
        p.setActualizadoEn(dt(rs.getString("updated_at")));
        return p;
    }

    private static Movimiento mapMovimiento(ResultSet rs) throws SQLException {
        Movimiento m = new Movimiento();
        m.setId(rs.getString("id"));
        m.setProductoId(rs.getString("producto_id"));
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
        m.setCreadoEn(dt(rs.getString("created_at")));
        m.setEstado(rs.getString("estado"));
        Producto p = PRODUCTOS_MAP.get(m.getProductoId());
        if (p != null) {
            m.setProductoNombre(p.getNombre());
            m.setCategoriaColor(p.getCategoriaColor());
        }
        return m;
    }

    private static String str(LocalDateTime dt) { return dt == null ? null : dt.toString(); }
    private static LocalDateTime dt(String s) { return s == null ? null : LocalDateTime.parse(s); }
}
