package com.sibim.db;

import com.sibim.model.*;
import com.sibim.model.enums.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public final class DemoDataStore {

    // Synchronized: every demo-mode repository call runs on its own
    // background Thread/virtual thread (see AppExecutor, used throughout
    // the controllers), so two screens hitting these lists at nearly the
    // same moment (e.g. a save while the Dashboard refreshes) could
    // otherwise race on a plain ArrayList and throw
    // ConcurrentModificationException or corrupt iteration — the real
    // Postgres path is already protected by row locks, demo mode needs its
    // own equivalent.
    static final List<Usuario>   USUARIOS    = Collections.synchronizedList(new ArrayList<>());
    static final List<Categoria> CATEGORIAS  = Collections.synchronizedList(new ArrayList<>());
    static final List<Producto>  PRODUCTOS   = Collections.synchronizedList(new ArrayList<>());
    static final List<Movimiento> MOVIMIENTOS = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_AUDIT_LOG = 500;
    private static final int MAX_CONTEOS   = 200;
    private static final LinkedList<AuditLog>    AUDIT_LOG = new LinkedList<>();
    private static final LinkedList<ConteoFisico> CONTEOS   = new LinkedList<>();

    static { DemoSemilla.sembrar(); }

    /** Puts the demo data back exactly as it is at startup — for tests that
     *  write through demo mode and must not leak into the next test class. */
    public static void reiniciar() {
        synchronized (STOCK_LOCK) {
            AUDIT_LOG.clear();
            CONTEOS.clear();
            DemoSemilla.sembrar();
        }
    }

    // ── Contraseñas para modo demo (sin BCrypt) ───────────────────────────────
    private static final Map<String, String> DEMO_PASSWORDS = Map.of(
        "superusuario",         "admin123456",
        "secretario.demo",      "sec123456",
        "direccion.demo",       "dir123456",
        "secretario.finanzas",  "fin123456",
        "director.obras",       "obras1234",
        "director.seguridad",   "seg123456"
    );

    private DemoDataStore() {}

    public static boolean verifyDemoPassword(String username, String password) {
        String expected = DEMO_PASSWORDS.get(username);
        return expected != null && expected.equals(password);
    }

    // ── Usuarios ──────────────────────────────────────────────────────────────

    public static Optional<Usuario> findUserByUsername(String username) {
        return USUARIOS.stream()
            .filter(u -> u.getUsername().equalsIgnoreCase(username))
            .findFirst();
    }

    public static List<Usuario> findAllUsers() {
        return Collections.unmodifiableList(USUARIOS);
    }

    public static void saveUsuario(Usuario u) {
        USUARIOS.removeIf(x -> x.getId().equals(u.getId()));
        USUARIOS.add(u);
    }

    public static void deleteUsuario(String id) {
        USUARIOS.removeIf(u -> u.getId().equals(id));
    }

    public static void updateUsuarioPassword(String id, String hash, boolean debeCambiarPassword) {
        USUARIOS.stream().filter(u -> u.getId().equals(id)).findFirst()
            .ifPresent(u -> {
                u.setPasswordHash(hash);
                u.setDebeCambiarPassword(debeCambiarPassword);
            });
    }

    // ── Categorías ────────────────────────────────────────────────────────────

    public static List<Categoria> findAllCategorias() {
        List<Categoria> result = new ArrayList<>(CATEGORIAS);
        Map<String, Long> counts = PRODUCTOS.stream()
            .collect(Collectors.groupingBy(Producto::getCategoriaId, Collectors.counting()));
        result.forEach(c -> c.setTotalProductos(counts.getOrDefault(c.getId(), 0L).intValue()));
        return result;
    }

    public static Optional<Categoria> findCategoriaById(String id) {
        return CATEGORIAS.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public static void saveCategoria(Categoria c) {
        CATEGORIAS.removeIf(x -> x.getId().equals(c.getId()));
        CATEGORIAS.add(c);
    }

    public static void deleteCategoria(String id) {
        CATEGORIAS.removeIf(c -> c.getId().equals(id));
    }

    public static boolean tieneProductosEnCategoria(String categoriaId) {
        return PRODUCTOS.stream().anyMatch(p -> categoriaId.equals(p.getCategoriaId()));
    }

    // ── Productos ─────────────────────────────────────────────────────────────

    public static List<Producto> findAllProductos(Set<String> accessibleAreas) {
        return PRODUCTOS.stream()
            .filter(p -> accessibleAreas == null || accessibleAreas.contains(p.getArea()))
            .sorted(Comparator.comparing(Producto::getNombre))
            .map(Producto::copia)
            .collect(Collectors.toList());
    }

    /** Copies, like a SQL read: callers may edit what they get (see Producto#copia). */
    public static Optional<Producto> findProductoById(String id) {
        return PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().map(Producto::copia);
    }

    public static Optional<Producto> findProductoByCodigo(String codigo) {
        return PRODUCTOS.stream().filter(p -> p.getCodigo().equalsIgnoreCase(codigo)).findFirst().map(Producto::copia);
    }

    public static boolean existsByCodigo(String codigo, String excludeId) {
        return PRODUCTOS.stream()
            .anyMatch(p -> p.getCodigo().equalsIgnoreCase(codigo)
                       && !p.isDadoDeBaja()
                       && !p.getId().equals(excludeId != null ? excludeId : ""));
    }

    public static void saveProducto(Producto p) {
        PRODUCTOS.removeIf(x -> x.getId().equals(p.getId()));
        PRODUCTOS.add(p.copia());
        if (p.getCategoriaId() != null) {
            long count = PRODUCTOS.stream()
                .filter(x -> p.getCategoriaId().equals(x.getCategoriaId())).count();
            CATEGORIAS.stream().filter(c -> c.getId().equals(p.getCategoriaId()))
                .findFirst().ifPresent(c -> c.setTotalProductos((int) count));
        }
    }

    public static void darDeBajaProducto(String id, String motivo) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> {
            p.setFechaBaja(LocalDate.now());
            p.setMotivoBaja(motivo);
            p.setActualizadoEn(LocalDateTime.now());
        });
    }

    public static void reactivarProducto(String id) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> {
            p.setFechaBaja(null);
            p.setMotivoBaja(null);
            p.setActualizadoEn(LocalDateTime.now());
        });
    }

    public static void cambiarCodigo(String id, String codigo) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> p.setCodigo(codigo));
    }

    public static void deleteProducto(String id) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> {
            String catId = p.getCategoriaId();
            PRODUCTOS.remove(p);
            if (catId != null) {
                long count = PRODUCTOS.stream().filter(x -> catId.equals(x.getCategoriaId())).count();
                CATEGORIAS.stream().filter(c -> c.getId().equals(catId))
                    .findFirst().ifPresent(c -> c.setTotalProductos((int) count));
            }
        });
    }

    public static void updateProductoStock(String id, int newStock) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst()
            .ifPresent(p -> { p.setStockActual(newStock); p.setActualizadoEn(LocalDateTime.now()); });
    }

    public static void updateProductoArea(String id, String newArea) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst()
            .ifPresent(p -> { p.setArea(newArea); p.setActualizadoEn(LocalDateTime.now()); });
    }

    public static void updateProductoCodigo(String id, String codigo) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst()
            .ifPresent(p -> { p.setCodigo(codigo); p.setActualizadoEn(LocalDateTime.now()); });
    }

    // ── Movimientos ───────────────────────────────────────────────────────────

    public static List<Movimiento> findAllMovimientos(Set<String> accessibleAreas) {
        return MOVIMIENTOS.stream()
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static List<Movimiento> findMovimientosByProducto(String productoId, Set<String> accessibleAreas) {
        return MOVIMIENTOS.stream()
            .filter(m -> productoId.equals(m.getProductoId()))
            .filter(m -> accessibleAreas == null || accessibleAreas.contains(areaOfProducto(m.getProductoId())))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn).reversed())
            .collect(Collectors.toList());
    }

    public static List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta,
                                                               Set<String> accessibleAreas) {
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

    public static Optional<String> findProductoIdByMovimientoId(String movimientoId) {
        return MOVIMIENTOS.stream()
            .filter(m -> m.getId().equals(movimientoId))
            .map(Movimiento::getProductoId)
            .findFirst();
    }

    /** Lock guarding the read-recompute-write sequence in addMovimiento/
     *  deleteMovimiento below — mirrors what {@code SELECT ... FOR UPDATE}
     *  does for the real Postgres path in
     *  MovimientoRepository#addMovimientoAtomic. Without it, two concurrent
     *  registrations on the same product (each running on its own
     *  background Thread/virtual thread — see AppExecutor) could both read
     *  the same stale stock and independently compute a "new" value,
     *  silently losing one of the two updates. */
    private static final Object STOCK_LOCK = new Object();

    public static void addMovimiento(Movimiento m) throws java.sql.SQLException {
        addMovimiento(m, null);
    }

    /** @param expectedStockAnterior see MovimientoRepository#addMovimientoAtomic(Movimiento, Integer) —
     *  same "reject instead of overwrite a stale AJUSTE" contract, checked
     *  here atomically inside the same lock as the read+write below (not
     *  as a separate pre-check, which would leave a race window). */
    public static void addMovimiento(Movimiento m, Integer expectedStockAnterior) throws java.sql.SQLException {
        synchronized (STOCK_LOCK) {
            Producto p = findProductoById(m.getProductoId()).orElseThrow(() ->
                new java.sql.SQLException("Bien no encontrado: " + m.getProductoId()));
            ReglasLocales.aplicar(m, p, expectedStockAnterior, codigosActivos());
            if (m.getAreaOrigen() != null) {   // transferencia
                updateProductoArea(m.getProductoId(), m.getAreaDestino());
                updateProductoCodigo(m.getProductoId(), m.getCodigoNuevo());
            }
            MOVIMIENTOS.add(0, m);
            updateProductoStock(m.getProductoId(), m.getStockNuevo());
        }
    }

    private static List<String> codigosActivos() {
        return PRODUCTOS.stream().filter(x -> !x.isDadoDeBaja()).map(Producto::getCodigo).toList();
    }

    /** A transferencia registered by a non-Admin doesn't move stock/area
     *  yet — it just sits here until an Admin calls aprobarTransferencia or
     *  rechazarTransferencia (see MovimientosController's "Pendientes"
     *  panel). stock_anterior/stock_nuevo are recorded equal (nothing's
     *  changed yet) purely so the row has consistent, non-null values to
     *  display while pending. */
    public static void addMovimientoPendiente(Movimiento m) {
        synchronized (STOCK_LOCK) {
            m.setEstado(Movimiento.ESTADO_PENDIENTE);
            m.setAreaOrigen(areaOfProducto(m.getProductoId()));
            int stock = PRODUCTOS.stream().filter(p -> p.getId().equals(m.getProductoId()))
                .findFirst().map(Producto::getStockActual).orElse(0);
            m.setStockAnterior(stock);
            m.setStockNuevo(stock);
            if (m.getCreadoEn() == null) m.setCreadoEn(LocalDateTime.now());
            MOVIMIENTOS.add(0, m);
        }
    }

    public static List<Movimiento> findPendientesTransferencias() {
        return MOVIMIENTOS.stream()
            .filter(m -> m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.isPendiente())
            .sorted(Comparator.comparing(Movimiento::getCreadoEn))
            .collect(Collectors.toList());
    }

    /** Same checks as MovimientoRepository#aprobarTransferencia. */
    public static void aprobarTransferencia(String id) throws java.sql.SQLException {
        synchronized (STOCK_LOCK) {
            Movimiento m = MOVIMIENTOS.stream().filter(x -> x.getId().equals(id) && x.isPendiente()).findFirst()
                .orElseThrow(() -> new java.sql.SQLException("Transferencia pendiente no encontrada: " + id));
            Producto p = findProductoById(m.getProductoId()).orElseThrow(() ->
                new java.sql.SQLException("Bien no encontrado: " + m.getProductoId()));
            if (p.isDadoDeBaja()) throw new ReglaIncumplida(com.sibim.service.MovimientoService.BIEN_DE_BAJA);
            if (p.getStockActual() <= 0)
                throw new ReglaIncumplida(com.sibim.service.MovimientoService.SIN_EXISTENCIA);
            if (m.getAreaOrigen() != null && !m.getAreaOrigen().equals(p.getArea()))
                throw new ReglaIncumplida("El bien ya no está en " + m.getAreaOrigen() + " (ahora está en "
                    + p.getArea() + "): rechaza esta solicitud y registra una nueva si sigue siendo necesaria.");
            String codigo = com.sibim.config.AreaCodigos.tienePrefijo(m.getAreaDestino())
                ? com.sibim.config.AreaCodigos.siguienteCodigo(m.getAreaDestino(), codigosActivos())
                : p.getCodigo();
            m.setEstado(Movimiento.ESTADO_APROBADO);
            m.setStockAnterior(p.getStockActual());
            m.setStockNuevo(p.getStockActual());
            m.setCodigoAnterior(p.getCodigo());
            m.setCodigoNuevo(codigo);
            updateProductoArea(m.getProductoId(), m.getAreaDestino());
            updateProductoCodigo(m.getProductoId(), codigo);
        }
    }

    public static List<Movimiento> findPorRecibir(java.util.Set<String> areas) {
        return MOVIMIENTOS.stream()
            .filter(Movimiento::isPorRecibir)
            .filter(m -> areas == null || areas.contains(m.getAreaDestino()))
            .sorted(Comparator.comparing(Movimiento::getCreadoEn))
            .collect(Collectors.toList());
    }

    public static boolean confirmarRecepcion(String id, String recibidoPor) {
        synchronized (STOCK_LOCK) {
            Movimiento m = MOVIMIENTOS.stream().filter(x -> x.getId().equals(id) && x.isPorRecibir())
                .findFirst().orElse(null);
            if (m == null) return false;
            m.setRecibidoPor(recibidoPor);
            m.setRecibidoEn(LocalDateTime.now());
            return true;
        }
    }

    public static void rechazarTransferencia(String id) {
        MOVIMIENTOS.stream().filter(m -> m.getId().equals(id) && m.isPendiente()).findFirst()
            .ifPresent(m -> m.setEstado(Movimiento.ESTADO_RECHAZADO));
    }

    /** Mirrors the "only the most recent movement can be deleted" rule
     *  enforced by MovimientoRepository#deleteMovimientoAtomic against a
     *  real DB — restoring stock_anterior blindly would be wrong here too
     *  if a later movement for the same product exists (MOVIMIENTOS is
     *  ordered newest-first, see addMovimiento). Pending transferencias
     *  never moved stock in the first place, so they're exempt from the
     *  "most recent" check — deleting one is just discarding the request. */
    public static void deleteMovimiento(String id) throws java.sql.SQLException {
        synchronized (STOCK_LOCK) {
            Movimiento m = MOVIMIENTOS.stream().filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> new java.sql.SQLException("Movimiento no encontrado: " + id));
            ReglasLocales.exigirQueSeaElUltimo(m, MOVIMIENTOS);
            Optional<Producto> p = findProductoById(m.getProductoId());
            MOVIMIENTOS.remove(m);
            if (ReglasLocales.aplicado(m) && p.isPresent()) {
                ReglasLocales.Deshacer r = ReglasLocales.deshacer(m, p.get(), codigosActivos());
                updateProductoStock(m.getProductoId(), r.stock());
                updateProductoArea(m.getProductoId(), r.area());
                updateProductoCodigo(m.getProductoId(), r.codigo());
            }
        }
    }

    private static String areaOfProducto(String productoId) {
        return findProductoById(productoId).map(Producto::getArea).orElse(null);
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    public static void addAuditLog(AuditLog a) {
        synchronized (AUDIT_LOG) {
            AUDIT_LOG.addFirst(a);
            if (AUDIT_LOG.size() > MAX_AUDIT_LOG) AUDIT_LOG.removeLast();
        }
    }

    public static List<AuditLog> findAuditLog(int limit) {
        synchronized (AUDIT_LOG) {
            return AUDIT_LOG.stream().limit(limit).toList();
        }
    }

    // ── Conteos físicos ───────────────────────────────────────────────────────

    public static void addConteo(ConteoFisico c) {
        synchronized (CONTEOS) {
            CONTEOS.addFirst(c);
            if (CONTEOS.size() > MAX_CONTEOS) CONTEOS.removeLast();
        }
    }

    public static List<ConteoFisico> findConteos(int limit) {
        synchronized (CONTEOS) {
            return CONTEOS.stream().limit(limit).toList();
        }
    }

    public static List<ConteoItem> findConteoItems(String conteoId) {
        synchronized (CONTEOS) {
            return CONTEOS.stream()
                .filter(c -> c.getId().equals(conteoId))
                .findFirst()
                .map(ConteoFisico::getItems)
                .orElse(List.of());
        }
    }

}
