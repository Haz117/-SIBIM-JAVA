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
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    private OfflineStore() {}

    private static Connection conn;
    private static boolean loaded = false;

    private static final List<Producto> PRODUCTOS = new ArrayList<>();
    private static final List<Categoria> CATEGORIAS = new ArrayList<>();
    private static final List<Movimiento> MOVIMIENTOS = new ArrayList<>();

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
                Path dbFile = dbDir.resolve("offline.db");
                conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
                conn.setAutoCommit(true);
                runSchema(conn);
                runOfflineMigrations(conn);
            } catch (IOException e) {
                throw new SQLException("No se pudo abrir el almacén offline local", e);
            }
        }
        return conn;
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
    }

    private static void runSchema(Connection c) throws SQLException {
        try (InputStream in = OfflineStore.class.getResourceAsStream("/offline.sql")) {
            if (in == null) throw new SQLException("No se encontró offline.sql en el classpath");
            String sql;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                sql = r.lines().collect(Collectors.joining("\n"));
            }
            try (Statement st = c.createStatement()) {
                for (String stmt : sql.split(";")) {
                    String trimmed = stmt.strip();
                    if (!trimmed.isEmpty()) st.execute(trimmed);
                }
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
        MOVIMIENTOS.clear();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM movements ORDER BY created_at DESC")) {
            while (rs.next()) MOVIMIENTOS.add(mapMovimiento(rs));
        }
        recomputeCategoriaCounts();
        loaded = true;
    }

    private static void recomputeCategoriaCounts() {
        var counts = PRODUCTOS.stream()
            .collect(Collectors.groupingBy(Producto::getCategoriaId, Collectors.counting()));
        CATEGORIAS.forEach(c -> c.setTotalProductos(counts.getOrDefault(c.getId(), 0L).intValue()));
    }

    // ───────────────────────────── Categorías ───────────────────────────────

    public static List<Categoria> findAllCategorias() throws SQLException {
        ensureLoaded();
        recomputeCategoriaCounts();
        return new ArrayList<>(CATEGORIAS);
    }

    public static Optional<Categoria> findCategoriaById(String id) throws SQLException {
        ensureLoaded();
        return CATEGORIAS.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public static void saveCategoria(Categoria c) throws SQLException {
        ensureLoaded();
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
    }

    public static void deleteCategoria(String id) throws SQLException {
        ensureLoaded();
        Categoria c = CATEGORIAS.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        CATEGORIAS.removeIf(x -> x.getId().equals(id));
        try (PreparedStatement ps = conn().prepareStatement("DELETE FROM categories WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
        if (c != null) enqueueCategory("DELETE", c);
    }

    public static boolean tieneProductosEnCategoria(String categoriaId) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream().anyMatch(p -> categoriaId.equals(p.getCategoriaId()));
    }

    // ───────────────────────────── Productos ────────────────────────────────

    public static List<Producto> findAllProductos(Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream()
            .filter(p -> accessibleAreas == null || accessibleAreas.contains(p.getArea()))
            .sorted(Comparator.comparing(Producto::getNombre))
            .collect(Collectors.toList());
    }

    public static Optional<Producto> findProductoById(String id) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public static Optional<Producto> findProductoByCodigo(String codigo) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream().filter(p -> p.getCodigo().equalsIgnoreCase(codigo)).findFirst();
    }

    public static boolean existsByCodigo(String codigo, String excludeId) throws SQLException {
        ensureLoaded();
        return PRODUCTOS.stream()
            .anyMatch(p -> p.getCodigo().equalsIgnoreCase(codigo)
                       && !p.getId().equals(excludeId != null ? excludeId : ""));
    }

    public static void saveProducto(Producto p) throws SQLException {
        ensureLoaded();
        // Capture the server's last-known updated_at BEFORE overwriting the product
        // in memory. SyncService uses this to detect whether the server was modified
        // by another machine while this PC was offline (conflict detection).
        String serverSnapshotAt = PRODUCTOS.stream()
            .filter(x -> x.getId().equals(p.getId()))
            .findFirst()
            .map(x -> str(x.getActualizadoEn()))
            .orElse(null);
        PRODUCTOS.removeIf(x -> x.getId().equals(p.getId()));
        PRODUCTOS.add(p);
        persistProducto(p);
        recomputeCategoriaCounts();
        enqueueProduct("SAVE", p, serverSnapshotAt);
    }

    public static void darDeBajaProducto(String id, String motivo) throws SQLException {
        ensureLoaded();
        Producto p = PRODUCTOS.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (p == null) return;
        p.setFechaBaja(LocalDate.now());
        p.setMotivoBaja(motivo);
        p.setActualizadoEn(LocalDateTime.now());
        persistProducto(p);
        enqueueProduct("BAJA", p, null);
    }

    public static void reactivarProducto(String id) throws SQLException {
        ensureLoaded();
        Producto p = PRODUCTOS.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
        if (p == null) return;
        p.setFechaBaja(null);
        p.setMotivoBaja(null);
        p.setActualizadoEn(LocalDateTime.now());
        persistProducto(p);
        enqueueProduct("REACTIVAR", p, null);
    }

    public static void updateProductoStock(String id, int newStock) throws SQLException {
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

    public static void updateProductoArea(String id, String newArea) throws SQLException {
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
                fecha_vencimiento, foto_url, ubicacion, area, resguardante, fecha_baja, motivo_baja,
                created_at, updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                nombre=excluded.nombre, codigo=excluded.codigo, descripcion=excluded.descripcion,
                categoria_id=excluded.categoria_id, precio_compra=excluded.precio_compra,
                precio_venta=excluded.precio_venta, stock_actual=excluded.stock_actual,
                stock_minimo=excluded.stock_minimo, stock_maximo=excluded.stock_maximo,
                unidad=excluded.unidad, proveedor=excluded.proveedor,
                fecha_vencimiento=excluded.fecha_vencimiento, foto_url=excluded.foto_url,
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
            ps.setString(15, p.getUbicacion());
            ps.setString(16, p.getArea());
            ps.setString(17, p.getResguardante());
            ps.setString(18, p.getFechaBaja() != null ? p.getFechaBaja().toString() : null);
            ps.setString(19, p.getMotivoBaja());
            ps.setString(20, str(p.getCreadoEn()));
            ps.setString(21, str(now));
            ps.executeUpdate();
        }
    }

    // ───────────────────────────── Movimientos ──────────────────────────────

    public static List<Movimiento> findAllMovimientos(Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static List<Movimiento> findMovimientosByProducto(String productoId, Set<String> accessibleAreas) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> productoId.equals(m.getProductoId()))
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta, Set<String> accessibleAreas) throws SQLException {
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

    public static Optional<String> findProductoIdByMovimientoId(String movimientoId) throws SQLException {
        ensureLoaded();
        return MOVIMIENTOS.stream()
            .filter(m -> m.getId().equals(movimientoId))
            .map(Movimiento::getProductoId)
            .findFirst();
    }

    private static String areaOfProducto(String productoId) {
        return PRODUCTOS.stream().filter(p -> p.getId().equals(productoId))
            .findFirst().map(Producto::getArea).orElse(null);
    }

    public static void addMovimiento(Movimiento m) throws SQLException {
        addMovimiento(m, null);
    }

    /** Same "reject instead of overwrite a stale AJUSTE" contract as
     *  DemoDataStore/MovimientoRepository — see those for why. */
    public static void addMovimiento(Movimiento m, Integer expectedStockAnterior) throws SQLException {
        ensureLoaded();
        synchronized (LOCK) {
            Producto p = PRODUCTOS.stream().filter(x -> x.getId().equals(m.getProductoId())).findFirst()
                .orElseThrow(() -> new SQLException("Producto no encontrado: " + m.getProductoId()));
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
                updateProductoArea(m.getProductoId(), m.getAreaDestino());
            }
            MOVIMIENTOS.add(0, m);
            persistMovimiento(m);
            updateProductoStock(m.getProductoId(), stockNuevo);
            enqueueMovement("ADD", m);
        }
    }

    /** Only the most recent movement for a product may be deleted — same
     *  rule as DemoDataStore/MovimientoRepository (MOVIMIENTOS is
     *  newest-first). */
    public static void deleteMovimiento(String id) throws SQLException {
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
            MOVIMIENTOS.remove(m);
            try (PreparedStatement ps = conn().prepareStatement("DELETE FROM movements WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            updateProductoStock(m.getProductoId(), m.getStockAnterior());
            if (m.getAreaOrigen() != null) updateProductoArea(m.getProductoId(), m.getAreaOrigen());
            enqueueMovement("DELETE", m);
        }
    }

    private static void persistMovimiento(Movimiento m) throws SQLException {
        String sql = """
            INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo,
                area_origen, area_destino, motivo, referencia, usuario_id, usuario_nombre, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
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
            ps.executeUpdate();
        }
    }

    // ─────────────────────── Caché de usuarios (solo lectura) ───────────────

    /** Refreshed after every successful ONLINE login (see AuthService) —
     *  never written from the offline side. Lets a previously-seen real
     *  user keep logging in if the connection later drops. */
    public static void cacheUser(Usuario u) throws SQLException {
        String sql = """
            INSERT INTO users_cache (id, username, password_hash, nombre, rol, area, debe_cambiar_password)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET username=excluded.username, password_hash=excluded.password_hash,
                nombre=excluded.nombre, rol=excluded.rol, area=excluded.area,
                debe_cambiar_password=excluded.debe_cambiar_password
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, u.getId());
            ps.setString(2, u.getUsername());
            ps.setString(3, u.getPasswordHash());
            ps.setString(4, u.getNombre());
            ps.setString(5, u.getRol().getCodigo());
            ps.setString(6, u.getArea());
            ps.setInt(7, u.isDebeCambiarPassword() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public static Optional<Usuario> findCachedUserByUsername(String username) throws SQLException {
        try (PreparedStatement ps = conn().prepareStatement(
                "SELECT * FROM users_cache WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
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
                unidad, proveedor, fecha_vencimiento, foto_url, ubicacion, area, resguardante,
                motivo_baja, created_at, server_snapshot_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                motivo, referencia, area_destino, usuario_id, usuario_nombre, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
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
            ps.setString(i, str(LocalDateTime.now()));
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
        Producto p = PRODUCTOS.stream().filter(x -> x.getId().equals(m.getProductoId())).findFirst().orElse(null);
        if (p != null) {
            m.setProductoNombre(p.getNombre());
            m.setCategoriaColor(p.getCategoriaColor());
        }
        return m;
    }

    private static String str(LocalDateTime dt) { return dt == null ? null : dt.toString(); }
    private static LocalDateTime dt(String s) { return s == null ? null : LocalDateTime.parse(s); }
}
