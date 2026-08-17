package com.sibim.db;

import com.sibim.model.*;
import com.sibim.model.enums.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public final class DemoDataStore {

    private static final List<Usuario>   USUARIOS;
    private static final List<Categoria> CATEGORIAS;
    private static final List<Producto>  PRODUCTOS;
    private static final List<Movimiento> MOVIMIENTOS;

    static {
        USUARIOS = new ArrayList<>(List.of(
            new Usuario("u-admin", "superusuario",
                "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj1o.FxRzFNS",
                "Administrador del Sistema", "Superusuario", Rol.ADMIN, null, LocalDateTime.now()),
            new Usuario("u-sec-1", "jm.zuniga",
                "$2a$12$RfQKHBQQlnBqnUjefY0n9.5Z2hxPzG4A1k2ZOBqFr9QQZJPBDhQBu",
                "Jose Manuel Zuniga Guerrero", "Secretario General Municipal",
                Rol.SECRETARIO, "Secretaria General Municipal", LocalDateTime.now()),
            new Usuario("u-dir-1", "rh.garcia",
                "$2a$12$n8TWbDFZ2F/Q3VdTHxuvTO.7OP7f/bJYKMXPJRb5LTkmHlKxhALiu",
                "Maria Garcia Lopez", "Director de Recursos Humanos",
                Rol.DIRECCION, "Direccion de Recursos Humanos", LocalDateTime.now())
        ));

        CATEGORIAS = new ArrayList<>(List.of(
            new Categoria("cat-mob",  "Mobiliario",               "Escritorios, sillas, archiveros",        "#8B5CF6", "Armchair",    LocalDateTime.now()),
            new Categoria("cat-veh",  "Vehiculos",                "Flota vehicular municipal",               "#3B82F6", "Car",         LocalDateTime.now()),
            new Categoria("cat-comp", "Equipo de Computo",        "Laptops, computadoras, perifericos",      "#10B981", "Laptop",      LocalDateTime.now()),
            new Categoria("cat-ofi",  "Equipo de Oficina",        "Impresoras, copiadoras",                  "#F59E0B", "Printer",     LocalDateTime.now()),
            new Categoria("cat-maq",  "Herramientas y Maquinaria","Maquinaria pesada y herramientas",        "#EF4444", "Wrench",      LocalDateTime.now()),
            new Categoria("cat-av",   "Equipo Audiovisual",       "Camaras, proyectores, pantallas",         "#EC4899", "VideoCamera", LocalDateTime.now())
        ));

        PRODUCTOS = new ArrayList<>(List.of(
            prod("p-01","Escritorio ejecutivo de madera", "MB-001","cat-mob","Mobiliario",            "#8B5CF6", 4500,  5000, 18,5,30,UnidadMedida.PIEZA,  "Secretaria General Municipal"),
            prod("p-02","Silla ejecutiva ergonomica",    "MB-002","cat-mob","Mobiliario",            "#8B5CF6", 3200,  3800,  6,8,40,UnidadMedida.PIEZA,  "Direccion de Recursos Humanos"),
            prod("p-03","Archivero metalico 4 gavetas",  "MB-003","cat-mob","Mobiliario",            "#8B5CF6", 2800,  3200,  0,3,20,UnidadMedida.PIEZA,  "Secretaria General Municipal"),
            prod("p-04","Camioneta pick-up Ford Ranger", "VH-001","cat-veh","Vehiculos",             "#3B82F6",380000,420000, 1,1, 5,UnidadMedida.UNIDAD, "Secretaria de Obras Publicas y Desarrollo Urbano"),
            prod("p-05","Laptop Dell Latitude 5440",     "EC-001","cat-comp","Equipo de Computo",    "#10B981", 22000, 25000,12,5,30,UnidadMedida.EQUIPO, "Secretaria de Planeacion y Evaluacion"),
            prod("p-06","Impresora multifuncional Epson","EO-001","cat-ofi","Equipo de Oficina",     "#F59E0B",  8500,  9500, 2,2,10,UnidadMedida.EQUIPO, "Secretaria General Municipal"),
            prod("p-07","Retroexcavadora CAT 420",       "HM-001","cat-maq","Herramientas y Maquinaria","#EF4444",850000,900000,1,1,3,UnidadMedida.UNIDAD,"Secretaria de Obras Publicas y Desarrollo Urbano"),
            prod("p-08","Camara de videovigilancia PTZ", "AV-001","cat-av","Equipo Audiovisual",     "#EC4899",  4800,  5500,22,5,50,UnidadMedida.PIEZA,  "Secretaria de Seguridad Publica Municipal")
        ));

        Map<String, Long> counts = PRODUCTOS.stream()
            .collect(Collectors.groupingBy(Producto::getCategoriaId, Collectors.counting()));
        CATEGORIAS.forEach(c -> c.setTotalProductos(counts.getOrDefault(c.getId(), 0L).intValue()));

        // Demo movimientos — últimos 7 días para popular el gráfico del Dashboard
        MOVIMIENTOS = new ArrayList<>(List.of(
            mov("m-01","p-05","Laptop Dell Latitude 5440","#10B981",
                TipoMovimiento.ENTRADA, 5, 7, 12,
                "Compra de reposicion","OC-2026-001",
                "u-admin","Administrador del Sistema", LocalDateTime.now().minusDays(6).withHour(9)),
            mov("m-02","p-02","Silla ejecutiva ergonomica","#8B5CF6",
                TipoMovimiento.SALIDA, 2, 8, 6,
                "Asignacion a sala de juntas","REQ-2026-012",
                "u-sec-1","Jose Manuel Zuniga Guerrero", LocalDateTime.now().minusDays(5).withHour(10)),
            mov("m-03","p-06","Impresora multifuncional Epson","#F59E0B",
                TipoMovimiento.ENTRADA, 1, 1, 2,
                "Adquisicion nueva","OC-2026-002",
                "u-admin","Administrador del Sistema", LocalDateTime.now().minusDays(4).withHour(11)),
            mov("m-04","p-01","Escritorio ejecutivo de madera","#8B5CF6",
                TipoMovimiento.AJUSTE, 18, 16, 18,
                "Correccion de conteo fisico","INV-2026-003",
                "u-sec-1","Jose Manuel Zuniga Guerrero", LocalDateTime.now().minusDays(3).withHour(14)),
            mov("m-05","p-08","Camara de videovigilancia PTZ","#EC4899",
                TipoMovimiento.SALIDA, 3, 25, 22,
                "Instalacion en accesos norte","REQ-2026-015",
                "u-admin","Administrador del Sistema", LocalDateTime.now().minusDays(2).withHour(9)),
            mov("m-06","p-03","Archivero metalico 4 gavetas","#8B5CF6",
                TipoMovimiento.ENTRADA, 2, 0, 2,
                "Compra urgente","OC-2026-003",
                "u-sec-1","Jose Manuel Zuniga Guerrero", LocalDateTime.now().minusDays(1).withHour(16)),
            mov("m-07","p-05","Laptop Dell Latitude 5440","#10B981",
                TipoMovimiento.TRANSFERENCIA, 2, 12, 10,
                "Transferencia a Planeacion","TRF-2026-001",
                "u-admin","Administrador del Sistema", LocalDateTime.now().withHour(8))
        ));

        // Sincronizar stocks tras movimientos demo (estado final coherente con el historial)
        PRODUCTOS.stream().filter(p -> p.getId().equals("p-03"))
            .findFirst().ifPresent(p -> p.setStockActual(2));   // m-06: 0→2
        PRODUCTOS.stream().filter(p -> p.getId().equals("p-05"))
            .findFirst().ifPresent(p -> p.setStockActual(10));  // m-01: 7→12, m-07: 12→10
    }

    private static final Map<String, String> DEMO_PASSWORDS = Map.of(
        "superusuario", "admin123456",
        "jm.zuniga",    "sec123456",
        "rh.garcia",    "dir123456"
    );

    private DemoDataStore() {}

    public static boolean verifyDemoPassword(String username, String password) {
        String expected = DEMO_PASSWORDS.get(username);
        return expected != null && expected.equals(password);
    }

    // ── Usuarios ──────────────────────────────────────────────────────────

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

    public static void updateUsuarioPassword(String id, String hash) {
        USUARIOS.stream().filter(u -> u.getId().equals(id)).findFirst()
            .ifPresent(u -> u.setPasswordHash(hash));
    }

    // ── Categorias ────────────────────────────────────────────────────────

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

    // ── Productos ─────────────────────────────────────────────────────────

    public static List<Producto> findAllProductos(Set<String> accessibleAreas) {
        return PRODUCTOS.stream()
            .filter(p -> accessibleAreas == null || accessibleAreas.contains(p.getArea()))
            .sorted(Comparator.comparing(Producto::getNombre))
            .collect(Collectors.toList());
    }

    public static Optional<Producto> findProductoById(String id) {
        return PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public static Optional<Producto> findProductoByCodigo(String codigo) {
        return PRODUCTOS.stream().filter(p -> p.getCodigo().equalsIgnoreCase(codigo)).findFirst();
    }

    public static boolean existsByCodigo(String codigo, String excludeId) {
        return PRODUCTOS.stream()
            .anyMatch(p -> p.getCodigo().equalsIgnoreCase(codigo)
                       && !p.getId().equals(excludeId != null ? excludeId : ""));
    }

    public static void saveProducto(Producto p) {
        PRODUCTOS.removeIf(x -> x.getId().equals(p.getId()));
        PRODUCTOS.add(p);
        // Recalcular totalProductos en categorias
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

    public static void deleteProducto(String id) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst().ifPresent(p -> {
            String catId = p.getCategoriaId();
            PRODUCTOS.remove(p);
            if (catId != null) {
                long count = PRODUCTOS.stream()
                    .filter(x -> catId.equals(x.getCategoriaId())).count();
                CATEGORIAS.stream().filter(c -> c.getId().equals(catId))
                    .findFirst().ifPresent(c -> c.setTotalProductos((int) count));
            }
        });
    }

    public static void updateProductoStock(String id, int newStock) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst()
            .ifPresent(p -> {
                p.setStockActual(newStock);
                p.setActualizadoEn(LocalDateTime.now());
            });
    }

    public static void updateProductoArea(String id, String newArea) {
        PRODUCTOS.stream().filter(p -> p.getId().equals(id)).findFirst()
            .ifPresent(p -> {
                p.setArea(newArea);
                p.setActualizadoEn(LocalDateTime.now());
            });
    }

    // ── Movimientos ───────────────────────────────────────────────────────

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

    public static List<Movimiento> findMovimientosByDateRange(LocalDate desde, LocalDate hasta, Set<String> accessibleAreas) {
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

    private static String areaOfProducto(String productoId) {
        return findProductoById(productoId).map(Producto::getArea).orElse(null);
    }

    public static Optional<String> findProductoIdByMovimientoId(String movimientoId) {
        return MOVIMIENTOS.stream()
            .filter(m -> m.getId().equals(movimientoId))
            .map(Movimiento::getProductoId)
            .findFirst();
    }

    public static void addMovimiento(Movimiento m) {
        if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
            m.setAreaOrigen(areaOfProducto(m.getProductoId()));
            updateProductoArea(m.getProductoId(), m.getAreaDestino());
        }
        MOVIMIENTOS.add(0, m);
        updateProductoStock(m.getProductoId(), m.getStockNuevo());
    }

    public static void deleteMovimiento(String id) {
        MOVIMIENTOS.stream().filter(m -> m.getId().equals(id)).findFirst().ifPresent(m -> {
            MOVIMIENTOS.remove(m);
            updateProductoStock(m.getProductoId(), m.getStockAnterior());
            if (m.getAreaOrigen() != null) updateProductoArea(m.getProductoId(), m.getAreaOrigen());
        });
    }

    // ── Factory helpers ───────────────────────────────────────────────────

    private static Producto prod(String id, String nombre, String codigo,
                                  String catId, String catNombre, String catColor,
                                  int precioCompra, int precioVenta,
                                  int stockActual, int stockMinimo, int stockMaximo,
                                  UnidadMedida unidad, String area) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCodigo(codigo);
        p.setCategoriaId(catId);
        p.setCategoriaNombre(catNombre);
        p.setCategoriaColor(catColor);
        p.setPrecioCompra(BigDecimal.valueOf(precioCompra));
        p.setPrecioVenta(BigDecimal.valueOf(precioVenta));
        p.setStockActual(stockActual);
        p.setStockMinimo(stockMinimo);
        p.setStockMaximo(stockMaximo);
        p.setUnidad(unidad);
        p.setArea(area);
        p.setCreadoEn(LocalDateTime.now());
        p.setActualizadoEn(LocalDateTime.now());
        return p;
    }

    private static Movimiento mov(String id, String productoId, String productoNombre,
                                   String categoriaColor, TipoMovimiento tipo,
                                   int cantidad, int stockAnterior, int stockNuevo,
                                   String motivo, String referencia,
                                   String usuarioId, String usuarioNombre,
                                   LocalDateTime creadoEn) {
        Movimiento m = new Movimiento();
        m.setId(id);
        m.setProductoId(productoId);
        m.setProductoNombre(productoNombre);
        m.setCategoriaColor(categoriaColor);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(stockAnterior);
        m.setStockNuevo(stockNuevo);
        m.setMotivo(motivo);
        m.setReferencia(referencia);
        m.setUsuarioId(usuarioId);
        m.setUsuarioNombre(usuarioNombre);
        m.setCreadoEn(creadoEn);
        return m;
    }
}
