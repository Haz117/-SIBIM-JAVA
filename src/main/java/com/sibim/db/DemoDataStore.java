package com.sibim.db;

import com.sibim.model.*;
import com.sibim.model.enums.*;
import java.math.BigDecimal;
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
    private static final List<Usuario>   USUARIOS;
    private static final List<Categoria> CATEGORIAS;
    private static final List<Producto>  PRODUCTOS;
    private static final List<Movimiento> MOVIMIENTOS;
    private static final int MAX_AUDIT_LOG = 500;
    private static final int MAX_CONTEOS   = 200;
    private static final LinkedList<AuditLog>    AUDIT_LOG = new LinkedList<>();
    private static final LinkedList<ConteoFisico> CONTEOS   = new LinkedList<>();

    // ── Constantes de área ────────────────────────────────────────────────────
    private static final String SGM   = "Secretaria General Municipal";
    private static final String RH    = "Direccion de Recursos Humanos";
    private static final String ADM   = "Direccion de Administracion";
    private static final String BM    = "Direccion de Bienes Municipales";
    private static final String OBRAS = "Secretaria de Obras Publicas y Desarrollo Urbano";
    private static final String PLAN  = "Secretaria de Planeacion y Evaluacion";
    private static final String FIN   = "Secretaria de Finanzas y Tesoreria Municipal";
    private static final String SEG   = "Secretaria de Seguridad Publica Municipal";
    private static final String PRES  = "Despacho de la Presidencia";

    // Hash BCrypt de "admin123456" (factor 12) — solo para demo; la autenticación
    // real usa DEMO_PASSWORDS abajo.
    private static final String HASH = "$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj1o.FxRzFNS";

    static {
        // ── Usuarios ──────────────────────────────────────────────────────────
        USUARIOS = Collections.synchronizedList(new ArrayList<>(List.of(
            new Usuario("u-admin",  "superusuario",         HASH,
                "Administrador del Sistema", "Superusuario", Rol.ADMIN, null,
                LocalDateTime.now().minusDays(365)),
            new Usuario("u-sec-1",  "secretario.demo",      HASH,
                "Luis Ramírez Flores", "Secretario General Municipal", Rol.SECRETARIO, SGM,
                LocalDateTime.now().minusDays(180)),
            new Usuario("u-dir-1",  "direccion.demo",       HASH,
                "María González Hernández", "Directora de Recursos Humanos", Rol.DIRECCION, RH,
                LocalDateTime.now().minusDays(175)),
            new Usuario("u-sec-2",  "secretario.finanzas",  HASH,
                "Jorge Mendoza Castillo", "Secretario de Finanzas y Tesorería", Rol.SECRETARIO, FIN,
                LocalDateTime.now().minusDays(90)),
            new Usuario("u-dir-2",  "director.obras",       HASH,
                "Roberto Sánchez Lima", "Director de Obras Públicas", Rol.DIRECCION, OBRAS,
                LocalDateTime.now().minusDays(60)),
            new Usuario("u-dir-3",  "director.seguridad",   HASH,
                "Carlos Morales Vega", "Director de Seguridad Pública", Rol.DIRECCION, SEG,
                LocalDateTime.now().minusDays(45))
        )));
        // Demo users already have a known password printed in the login screen;
        // forcing a change on first login only creates friction during demos.
        // Production seed users (created via crear-admin.ps1) DO get this flag.

        // ── Categorías ────────────────────────────────────────────────────────
        CATEGORIAS = Collections.synchronizedList(new ArrayList<>(List.of(
            new Categoria("cat-mob",  "Mobiliario",                "Escritorios, sillas, archiveros, módulos", "#8B5CF6", "🪑",  LocalDateTime.now()),
            new Categoria("cat-veh",  "Vehículos",                 "Flota vehicular municipal",                 "#3B82F6", "🚗",  LocalDateTime.now()),
            new Categoria("cat-comp", "Equipo de Cómputo",         "Laptops, computadoras, periféricos",        "#10B981", "💻",  LocalDateTime.now()),
            new Categoria("cat-ofi",  "Equipo de Oficina",         "Impresoras, copiadoras, teléfonos",         "#F59E0B", "🖨",  LocalDateTime.now()),
            new Categoria("cat-maq",  "Herramientas y Maquinaria", "Maquinaria pesada, herramientas, extintores","#EF4444", "🔧",  LocalDateTime.now()),
            new Categoria("cat-av",   "Equipo Audiovisual",        "Cámaras, proyectores, pantallas, audio",    "#EC4899", "📷",  LocalDateTime.now())
        )));

        // ── Productos ─────────────────────────────────────────────────────────
        // Formato: id, nombre, código, catId, catNombre, catColor,
        //          precioCompra, precioVenta,
        //          stockActual, stockMin, stockMax, unidad, área
        PRODUCTOS = Collections.synchronizedList(new ArrayList<>(List.of(
            // MOBILIARIO ──────────────────────────────────────────────────────
            prod("p-01","Escritorio ejecutivo de madera",   "MB-001","cat-mob","Mobiliario","#8B5CF6", 4500, 5000, 18,5,30,UnidadMedida.PIEZA,  SGM),
            prod("p-02","Silla ejecutiva ergonómica",       "MB-002","cat-mob","Mobiliario","#8B5CF6", 3200, 3800,  4,8,40,UnidadMedida.PIEZA,  RH),   // BAJO_STOCK
            prod("p-03","Archivero metálico 4 gavetas",     "MB-003","cat-mob","Mobiliario","#8B5CF6", 2800, 3200,  0,3,20,UnidadMedida.PIEZA,  SGM),  // AGOTADO
            prod("p-09","Mesa de reuniones 10 personas",    "MB-004","cat-mob","Mobiliario","#8B5CF6", 9500,10500,  3,2, 8,UnidadMedida.PIEZA,  SGM),
            prod("p-10","Estantería metálica 5 niveles",    "MB-005","cat-mob","Mobiliario","#8B5CF6", 1800, 2200, 12,5,30,UnidadMedida.PIEZA,  ADM),
            prod("p-11","Silla de espera 3 plazas",         "MB-006","cat-mob","Mobiliario","#8B5CF6", 2200, 2600,  8,5,20,UnidadMedida.PIEZA,  OBRAS),
            prod("p-12","Módulo de trabajo L-shape",        "MB-007","cat-mob","Mobiliario","#8B5CF6", 6800, 7500,  1,3,10,UnidadMedida.PIEZA,  FIN),  // BAJO_STOCK
            prod("p-13","Lockers metálicos 6 puertas",      "MB-008","cat-mob","Mobiliario","#8B5CF6", 3500, 4000,  6,3,15,UnidadMedida.PIEZA,  SEG),
            prod("p-14","Librero de madera ejecutivo",      "MB-009","cat-mob","Mobiliario","#8B5CF6", 4200, 4800,  0,2, 8,UnidadMedida.PIEZA,  RH),   // AGOTADO

            // VEHÍCULOS ───────────────────────────────────────────────────────
            prod("p-04","Camioneta pick-up Ford Ranger",    "VH-001","cat-veh","Vehículos","#3B82F6",380000,420000, 1,1, 5,UnidadMedida.UNIDAD,OBRAS),  // BAJO_STOCK (stock=min)
            prod("p-18","Automóvil Nissan Versa 2023",      "VH-002","cat-veh","Vehículos","#3B82F6",240000,260000, 2,1, 4,UnidadMedida.UNIDAD,PRES),
            prod("p-19","Motocicleta Honda CB150 2022",     "VH-003","cat-veh","Vehículos","#3B82F6", 28000, 32000, 3,2, 6,UnidadMedida.UNIDAD,SEG),
            prod("p-20","Pipa de agua 10,000 L",            "VH-004","cat-veh","Vehículos","#3B82F6",320000,340000, 1,1, 2,UnidadMedida.UNIDAD,OBRAS), // BAJO_STOCK

            // EQUIPO DE CÓMPUTO ───────────────────────────────────────────────
            prod("p-05","Laptop Dell Latitude 5440",        "EC-001","cat-comp","Equipo de Cómputo","#10B981", 22000,25000,10,5,30,UnidadMedida.EQUIPO,PLAN),
            prod("p-22","Computadora HP All-in-One",        "EC-002","cat-comp","Equipo de Cómputo","#10B981", 16000,18000, 8,5,20,UnidadMedida.EQUIPO,SGM),
            prod("p-23","Tablet Samsung Galaxy A8",         "EC-003","cat-comp","Equipo de Cómputo","#10B981",  8500, 9500, 0,2,10,UnidadMedida.EQUIPO,FIN),  // AGOTADO
            prod("p-24","Mouse inalámbrico Logitech M705",  "EC-004","cat-comp","Equipo de Cómputo","#10B981",   450,  600, 3,5,30,UnidadMedida.PIEZA, ADM),  // BAJO_STOCK
            prod("p-25","Teclado HP USB multimedio",        "EC-005","cat-comp","Equipo de Cómputo","#10B981",   350,  480, 5,5,30,UnidadMedida.PIEZA, SGM),  // BAJO_STOCK (stock=min)
            prod("p-26","Monitor LG 24\" Full HD",          "EC-006","cat-comp","Equipo de Cómputo","#10B981",  4800, 5500,12,5,25,UnidadMedida.EQUIPO,SGM),

            // EQUIPO DE OFICINA ────────────────────────────────────────────────
            prod("p-06","Impresora multifuncional Epson",   "EO-001","cat-ofi","Equipo de Oficina","#F59E0B",  8500, 9500, 2,2,10,UnidadMedida.EQUIPO,SGM),  // BAJO_STOCK
            prod("p-29","Fotocopiadora Ricoh MPC2004",      "EO-002","cat-ofi","Equipo de Oficina","#F59E0B", 45000,50000, 1,1, 3,UnidadMedida.EQUIPO,SGM),  // BAJO_STOCK
            prod("p-30","Teléfono IP Grandstream GXP1625",  "EO-003","cat-ofi","Equipo de Oficina","#F59E0B",  1400, 1700,15,5,30,UnidadMedida.EQUIPO,SGM),
            prod("p-31","Calculadora financiera Casio FC",  "EO-004","cat-ofi","Equipo de Oficina","#F59E0B",   800, 1000,10,5,20,UnidadMedida.PIEZA, FIN),
            prod("p-32","Trituradora de papel HSM",         "EO-005","cat-ofi","Equipo de Oficina","#F59E0B",  3200, 3800, 3,2, 8,UnidadMedida.EQUIPO,SGM),
            prod("p-33","Escáner de documentos Fujitsu",    "EO-006","cat-ofi","Equipo de Oficina","#F59E0B",  6500, 7500, 0,1, 5,UnidadMedida.EQUIPO,ADM),  // AGOTADO

            // HERRAMIENTAS Y MAQUINARIA ────────────────────────────────────────
            prod("p-07","Retroexcavadora CAT 420F",         "HM-001","cat-maq","Herramientas y Maquinaria","#EF4444",850000,900000,1,1,3,UnidadMedida.UNIDAD,OBRAS), // BAJO_STOCK
            prod("p-35","Compresor de aire 50 L Truper",    "HM-002","cat-maq","Herramientas y Maquinaria","#EF4444",  4200, 4800, 2,1, 5,UnidadMedida.EQUIPO,OBRAS),
            prod("p-36","Taladro percutor Bosch GSB 550",   "HM-003","cat-maq","Herramientas y Maquinaria","#EF4444",  1800, 2200, 5,3,15,UnidadMedida.EQUIPO,OBRAS),
            prod("p-37","Motosierra Stihl MS 180",          "HM-004","cat-maq","Herramientas y Maquinaria","#EF4444",  5500, 6200, 2,2, 8,UnidadMedida.EQUIPO,OBRAS), // BAJO_STOCK
            prod("p-38","Podadora de pasto Honda GX160",    "HM-005","cat-maq","Herramientas y Maquinaria","#EF4444",  8200, 9000, 0,1, 5,UnidadMedida.EQUIPO,OBRAS), // AGOTADO
            prod("p-45","Extintor PQS 6 kg (lote A-2024)",  "HM-006","cat-maq","Herramientas y Maquinaria","#EF4444",   480,  600, 8,5,20,UnidadMedida.PIEZA, BM),   // → VENCIDO (ajuste abajo)

            // EQUIPO AUDIOVISUAL ──────────────────────────────────────────────
            prod("p-08","Cámara videovigilancia PTZ Dahua", "AV-001","cat-av","Equipo Audiovisual","#EC4899",  4800, 5500,22,5,50,UnidadMedida.PIEZA, SEG),
            prod("p-40","Proyector Epson EW-450 3LCD",      "AV-002","cat-av","Equipo Audiovisual","#EC4899", 12000,14000, 3,2, 8,UnidadMedida.EQUIPO,SGM),
            prod("p-41","Pantalla de proyección 100\"",     "AV-003","cat-av","Equipo Audiovisual","#EC4899",  2800, 3200, 4,2,10,UnidadMedida.PIEZA, SGM),
            prod("p-42","Sistema de audio para sala",       "AV-004","cat-av","Equipo Audiovisual","#EC4899", 18000,21000, 1,1, 3,UnidadMedida.EQUIPO,PRES), // BAJO_STOCK
            prod("p-43","Cámara fotográfica Canon EOS",     "AV-005","cat-av","Equipo Audiovisual","#EC4899", 22000,25000, 2,1, 5,UnidadMedida.EQUIPO,SGM),

            // DADO DE BAJA ────────────────────────────────────────────────────
            prod("p-46","Fax Panasonic KX-FT988",           "EO-007","cat-ofi","Equipo de Oficina","#F59E0B",  1200, 1400, 0,1, 3,UnidadMedida.EQUIPO,SGM)
        )));

        // Ajustar fecha de vencimiento del extintor (lote vencido hace 8 meses)
        PRODUCTOS.stream().filter(p -> "p-45".equals(p.getId())).findFirst()
            .ifPresent(p -> p.setFechaVencimiento(LocalDate.now().minusMonths(8)));

        // Dar de baja el fax obsoleto
        PRODUCTOS.stream().filter(p -> "p-46".equals(p.getId())).findFirst()
            .ifPresent(p -> {
                p.setFechaBaja(LocalDate.now().minusDays(60));
                p.setMotivoBaja("Equipo obsoleto — sustituido por sistema digital de trámites");
                p.setActualizadoEn(LocalDateTime.now().minusDays(60));
            });

        // Recalcular conteos de categorías
        Map<String, Long> catCounts = PRODUCTOS.stream()
            .collect(Collectors.groupingBy(Producto::getCategoriaId, Collectors.counting()));
        CATEGORIAS.forEach(c -> c.setTotalProductos(catCounts.getOrDefault(c.getId(), 0L).intValue()));

        // ── Movimientos (últimos 30 días) ─────────────────────────────────────
        // Formato: id, productoId, productoNombre, catColor,
        //          tipo, cantidad, stockAnterior, stockNuevo,
        //          motivo, referencia, usuarioId, usuarioNombre, fechaHora
        MOVIMIENTOS = Collections.synchronizedList(new ArrayList<>(List.of(

            // ── SEMANA -4 (días 30-22) ────────────────────────────────────────
            mov("m-01","p-05","Laptop Dell Latitude 5440","#10B981",
                TipoMovimiento.ENTRADA, 15, 5, 20,
                "Adquisición nuevas laptops – licitación 2026-L01","OC-2026-001",
                "u-admin","Administrador del Sistema", dh(30, 9, 0)),
            mov("m-02","p-08","Cámara videovigilancia PTZ Dahua","#EC4899",
                TipoMovimiento.ENTRADA, 10, 15, 25,
                "Ampliación sistema CCTV sector norte","OC-2026-002",
                "u-dir-3","Carlos Morales Vega", dh(29, 10, 30)),
            mov("m-03","p-30","Teléfono IP Grandstream GXP1625","#F59E0B",
                TipoMovimiento.ENTRADA, 20, 0, 20,
                "Instalación nueva central telefónica IP","OC-2026-003",
                "u-sec-1","Luis Ramírez Flores", dh(28, 11, 0)),
            mov("m-04","p-02","Silla ejecutiva ergonómica","#8B5CF6",
                TipoMovimiento.SALIDA, 6, 15, 9,
                "Asignación remodelación Sala de Cabildo","REQ-2026-001",
                "u-sec-1","Luis Ramírez Flores", dh(28, 14, 0)),
            mov("m-05","p-36","Taladro percutor Bosch GSB 550","#EF4444",
                TipoMovimiento.ENTRADA, 5, 0, 5,
                "Dotación inicial herramientas mantenimiento","OC-2026-004",
                "u-dir-2","Roberto Sánchez Lima", dh(27, 9, 0)),
            mov("m-06","p-22","Computadora HP All-in-One","#10B981",
                TipoMovimiento.ENTRADA, 10, 0, 10,
                "Equipamiento módulos de atención ciudadana","OC-2026-005",
                "u-admin","Administrador del Sistema", dh(26, 10, 0)),
            mov("m-07","p-26","Monitor LG 24\" Full HD","#10B981",
                TipoMovimiento.ENTRADA, 15, 0, 15,
                "Compra monitores para nueva oficina","OC-2026-006",
                "u-admin","Administrador del Sistema", dh(25, 11, 0)),
            mov("m-08","p-19","Motocicleta Honda CB150 2022","#3B82F6",
                TipoMovimiento.ENTRADA, 3, 0, 3,
                "Adquisición flota de patrullas moto","OC-2026-007",
                "u-dir-3","Carlos Morales Vega", dh(24, 9, 0)),
            mov("m-09","p-10","Estantería metálica 5 niveles","#8B5CF6",
                TipoMovimiento.ENTRADA, 12, 0, 12,
                "Equipamiento bodega de archivo","OC-2026-008",
                "u-sec-1","Luis Ramírez Flores", dh(23, 10, 0)),
            mov("m-10","p-31","Calculadora financiera Casio FC","#F59E0B",
                TipoMovimiento.ENTRADA, 15, 0, 15,
                "Dotación área de tesorería","OC-2026-009",
                "u-sec-2","Jorge Mendoza Castillo", dh(22, 11, 0)),

            // ── SEMANA -3 (días 21-15) ────────────────────────────────────────
            mov("m-11","p-02","Silla ejecutiva ergonómica","#8B5CF6",
                TipoMovimiento.SALIDA, 5, 9, 4,
                "Asignación nuevas oficinas Dirección de Obras","REQ-2026-002",
                "u-sec-1","Luis Ramírez Flores", dh(21, 9, 0)),
            mov("m-12","p-08","Cámara videovigilancia PTZ Dahua","#EC4899",
                TipoMovimiento.SALIDA, 3, 25, 22,
                "Instalación accesos sur del palacio municipal","REQ-2026-003",
                "u-dir-3","Carlos Morales Vega", dh(20, 10, 0)),
            mov("m-13","p-05","Laptop Dell Latitude 5440","#10B981",
                TipoMovimiento.SALIDA, 5, 20, 15,
                "Asignación personal auditoría interna","REQ-2026-004",
                "u-admin","Administrador del Sistema", dh(20, 14, 0)),
            mov("m-14","p-01","Escritorio ejecutivo de madera","#8B5CF6",
                TipoMovimiento.AJUSTE, 18, 20, 18,
                "Corrección conteo físico — 2 unidades deterioradas de baja","INV-2026-001",
                "u-sec-1","Luis Ramírez Flores", dh(19, 11, 0)),
            mov("m-15","p-26","Monitor LG 24\" Full HD","#10B981",
                TipoMovimiento.SALIDA, 3, 15, 12,
                "Equipamiento sala de capacitación","REQ-2026-005",
                "u-admin","Administrador del Sistema", dh(18, 9, 0)),
            mov("m-16","p-13","Lockers metálicos 6 puertas","#8B5CF6",
                TipoMovimiento.ENTRADA, 6, 0, 6,
                "Equipamiento vestuarios corporación de policía","OC-2026-010",
                "u-dir-3","Carlos Morales Vega", dh(17, 10, 0)),
            mov("m-17","p-30","Teléfono IP Grandstream GXP1625","#F59E0B",
                TipoMovimiento.SALIDA, 5, 20, 15,
                "Distribución teléfonos módulos planta baja","REQ-2026-006",
                "u-sec-1","Luis Ramírez Flores", dh(17, 14, 0)),
            mov("m-18","p-32","Trituradora de papel HSM","#F59E0B",
                TipoMovimiento.ENTRADA, 3, 0, 3,
                "Adquisición trituradoras seguridad documental","OC-2026-011",
                "u-sec-2","Jorge Mendoza Castillo", dh(16, 9, 0)),
            mov("m-19","p-05","Laptop Dell Latitude 5440","#10B981",
                TipoMovimiento.SALIDA, 5, 15, 10,
                "Asignación jefes de departamento","REQ-2026-007",
                "u-admin","Administrador del Sistema", dh(15, 11, 0)),
            mov("m-20","p-40","Proyector Epson EW-450 3LCD","#EC4899",
                TipoMovimiento.ENTRADA, 3, 0, 3,
                "Adquisición proyectores salas de juntas","OC-2026-012",
                "u-sec-1","Luis Ramírez Flores", dh(15, 14, 0)),

            // ── SEMANA -2 (días 14-8) ─────────────────────────────────────────
            mov("m-21","p-31","Calculadora financiera Casio FC","#F59E0B",
                TipoMovimiento.SALIDA, 5, 15, 10,
                "Entrega auditores externos","REQ-2026-008",
                "u-sec-2","Jorge Mendoza Castillo", dh(14, 9, 0)),
            mov("m-22","p-22","Computadora HP All-in-One","#10B981",
                TipoMovimiento.SALIDA, 2, 10, 8,
                "Asignación módulo de registro civil","REQ-2026-009",
                "u-sec-1","Luis Ramírez Flores", dh(13, 10, 0)),
            mov("m-23","p-03","Archivero metálico 4 gavetas","#8B5CF6",
                TipoMovimiento.SALIDA, 3, 3, 0,
                "Reasignación a bodega por remodelación","REQ-2026-010",
                "u-sec-1","Luis Ramírez Flores", dh(13, 14, 0)),
            mov("m-24","p-41","Pantalla de proyección 100\"","#EC4899",
                TipoMovimiento.ENTRADA, 4, 0, 4,
                "Dotación salas de juntas pisos 1 y 2","OC-2026-013",
                "u-sec-1","Luis Ramírez Flores", dh(12, 9, 0)),
            mov("m-25","p-43","Cámara fotográfica Canon EOS","#EC4899",
                TipoMovimiento.ENTRADA, 2, 0, 2,
                "Adquisición comunicación social","OC-2026-014",
                "u-sec-1","Luis Ramírez Flores", dh(12, 11, 0)),
            mov("m-26","p-09","Mesa de reuniones 10 personas","#8B5CF6",
                TipoMovimiento.ENTRADA, 3, 0, 3,
                "Equipamiento sala de cabildo y sala directiva","OC-2026-015",
                "u-sec-1","Luis Ramírez Flores", dh(11, 10, 0)),
            mov("m-27","p-35","Compresor de aire 50 L Truper","#EF4444",
                TipoMovimiento.ENTRADA, 2, 0, 2,
                "Equipamiento taller mantenimiento municipal","OC-2026-016",
                "u-dir-2","Roberto Sánchez Lima", dh(11, 14, 0)),
            mov("m-28","p-30","Teléfono IP Grandstream GXP1625","#F59E0B",
                TipoMovimiento.SALIDA, 5, 15, 10,
                "Instalación teléfonos ventanillas de trámites","REQ-2026-011",
                "u-sec-2","Jorge Mendoza Castillo", dh(10, 9, 0)),
            mov("m-29","p-18","Automóvil Nissan Versa 2023","#3B82F6",
                TipoMovimiento.ENTRADA, 2, 0, 2,
                "Adquisición flota presidencia","OC-2026-017",
                "u-admin","Administrador del Sistema", dh(10, 11, 0)),
            mov("m-30","p-14","Librero de madera ejecutivo","#8B5CF6",
                TipoMovimiento.SALIDA, 2, 2, 0,
                "Asignación despachos y deterioro de dos unidades","REQ-2026-012",
                "u-dir-1","María González Hernández", dh(9, 14, 0)),
            mov("m-31","p-05","Laptop Dell Latitude 5440","#10B981",
                TipoMovimiento.TRANSFERENCIA, 0, 10, 10,
                "Préstamo temporal a Dirección de Finanzas","TRF-2026-001",
                "u-admin","Administrador del Sistema", dh(8, 10, 0)),

            // ── SEMANA -1 (días 7-3) ──────────────────────────────────────────
            mov("m-32","p-10","Estantería metálica 5 niveles","#8B5CF6",
                TipoMovimiento.SALIDA, 3, 12, 9,
                "Distribución archivo general planta baja","REQ-2026-013",
                "u-sec-1","Luis Ramírez Flores", dh(7, 9, 0)),
            mov("m-33","p-26","Monitor LG 24\" Full HD","#10B981",
                TipoMovimiento.SALIDA, 3, 12, 9,
                "Asignación contralores","REQ-2026-014",
                "u-admin","Administrador del Sistema", dh(7, 11, 0)),
            mov("m-34","p-36","Taladro percutor Bosch GSB 550","#EF4444",
                TipoMovimiento.SALIDA, 2, 5, 3,
                "Préstamo obra pública calle Juárez","REQ-2026-015",
                "u-dir-2","Roberto Sánchez Lima", dh(6, 10, 0)),
            mov("m-35","p-25","Teclado HP USB multimedio","#10B981",
                TipoMovimiento.SALIDA, 5, 10, 5,
                "Asignación computadoras módulos recepción","REQ-2026-016",
                "u-sec-1","Luis Ramírez Flores", dh(6, 14, 0)),
            mov("m-36","p-24","Mouse inalámbrico Logitech M705","#10B981",
                TipoMovimiento.SALIDA, 7, 10, 3,
                "Asignación personal oficinas diversas","REQ-2026-017",
                "u-sec-1","Luis Ramírez Flores", dh(5, 9, 0)),
            mov("m-37","p-26","Monitor LG 24\" Full HD","#10B981",
                TipoMovimiento.SALIDA, 3, 9, 6,
                "Dotación sala de crisis Presidencia","REQ-2026-018",
                "u-admin","Administrador del Sistema", dh(5, 11, 0)),
            mov("m-38","p-11","Silla de espera 3 plazas","#8B5CF6",
                TipoMovimiento.ENTRADA, 8, 0, 8,
                "Equipamiento sala de espera Obras Públicas","OC-2026-018",
                "u-dir-2","Roberto Sánchez Lima", dh(4, 10, 0)),
            mov("m-39","p-30","Teléfono IP Grandstream GXP1625","#F59E0B",
                TipoMovimiento.SALIDA, 5, 10, 5,
                "Instalación módulo de atención Finanzas","REQ-2026-019",
                "u-sec-2","Jorge Mendoza Castillo", dh(4, 14, 0)),
            mov("m-40","p-33","Escáner de documentos Fujitsu","#F59E0B",
                TipoMovimiento.SALIDA, 2, 2, 0,
                "Devolución proveedor — unidades con falla de sensor","REQ-2026-020",
                "u-sec-1","Luis Ramírez Flores", dh(3, 9, 0)),
            mov("m-41","p-37","Motosierra Stihl MS 180","#EF4444",
                TipoMovimiento.SALIDA, 1, 3, 2,
                "Tala de árbol peligroso colonia Centro","REQ-2026-021",
                "u-dir-2","Roberto Sánchez Lima", dh(3, 11, 0)),

            // ── ESTA SEMANA (días 2-0) ────────────────────────────────────────
            mov("m-42","p-26","Monitor LG 24\" Full HD","#10B981",
                TipoMovimiento.AJUSTE, 12, 6, 12,
                "Conteo físico — localizadas 6 unidades en bodega 3","INV-2026-002",
                "u-admin","Administrador del Sistema", dh(2, 9, 0)),
            mov("m-43","p-08","Cámara videovigilancia PTZ Dahua","#EC4899",
                TipoMovimiento.SALIDA, 3, 22, 19,
                "Instalación 3 cámaras callejón Mercado","REQ-2026-022",
                "u-dir-3","Carlos Morales Vega", dh(2, 10, 30)),
            mov("m-44","p-31","Calculadora financiera Casio FC","#F59E0B",
                TipoMovimiento.SALIDA, 5, 10, 5,
                "Entrega personal de tesorería municipal","REQ-2026-023",
                "u-sec-2","Jorge Mendoza Castillo", dh(2, 14, 0)),
            mov("m-45","p-12","Módulo de trabajo L-shape","#8B5CF6",
                TipoMovimiento.SALIDA, 2, 3, 1,
                "Asignación nuevas oficinas Dirección de Finanzas","REQ-2026-024",
                "u-sec-2","Jorge Mendoza Castillo", dh(1, 9, 0)),
            mov("m-46","p-42","Sistema de audio para sala","#EC4899",
                TipoMovimiento.ENTRADA, 1, 0, 1,
                "Adquisición sistema conferencias sala presidencia","OC-2026-019",
                "u-admin","Administrador del Sistema", dh(1, 11, 0)),
            mov("m-47","p-23","Tablet Samsung Galaxy A8","#10B981",
                TipoMovimiento.SALIDA, 5, 5, 0,
                "Asignación inspectores — todas las unidades distribuidas","REQ-2026-025",
                "u-sec-2","Jorge Mendoza Castillo", dh(1, 14, 0)),
            mov("m-48","p-08","Cámara videovigilancia PTZ Dahua","#EC4899",
                TipoMovimiento.SALIDA, 3, 19, 16,
                "Instalación parque central y plaza cívica","REQ-2026-026",
                "u-dir-3","Carlos Morales Vega", dh(0, 9, 0)),
            mov("m-49","p-38","Podadora de pasto Honda GX160","#EF4444",
                TipoMovimiento.SALIDA, 2, 2, 0,
                "Asignadas a brigada parques — reportadas robadas","REQ-2026-027",
                "u-dir-2","Roberto Sánchez Lima", dh(0, 10, 0)),
            mov("m-50","p-08","Cámara videovigilancia PTZ Dahua","#EC4899",
                TipoMovimiento.ENTRADA, 9, 16, 25,
                "Reposición licitación urgente CCTV","OC-2026-020",
                "u-admin","Administrador del Sistema", dh(0, 11, 0)),
            mov("m-51","p-08","Cámara videovigilancia PTZ Dahua","#EC4899",
                TipoMovimiento.AJUSTE, 22, 25, 22,
                "Conteo físico almacén — 3 unidades en reparación externa","INV-2026-003",
                "u-dir-3","Carlos Morales Vega", dh(0, 15, 0))
        )));

        // Estado inicial del seed: TRANSFERENCIAs quedan PENDIENTE para poder
        // demostrar el flujo de aprobación; el resto queda APROBADO.
        MOVIMIENTOS.forEach(m -> {
            if (m.getEstado() == null)
                m.setEstado(m.getTipo() == TipoMovimiento.TRANSFERENCIA
                    ? Movimiento.ESTADO_PENDIENTE : Movimiento.ESTADO_APROBADO);
        });

        // ── Auditoría inicial ─────────────────────────────────────────────────
        auditEntry("usuario", "u-sec-2",  "Jorge Mendoza Castillo",   "crear",      "Usuario registrado",              "u-admin", "Administrador del Sistema", dh(90, 8, 0));
        auditEntry("usuario", "u-dir-2",  "Roberto Sánchez Lima",     "crear",      "Usuario registrado",              "u-admin", "Administrador del Sistema", dh(60, 9, 0));
        auditEntry("usuario", "u-dir-3",  "Carlos Morales Vega",      "crear",      "Usuario registrado",              "u-admin", "Administrador del Sistema", dh(45, 8, 30));
        auditEntry("producto","p-46",     "Fax Panasonic KX-FT988",   "baja",       "Motivo: Equipo obsoleto",         "u-admin", "Administrador del Sistema", dh(60, 10, 0));
        auditEntry("usuario", "u-dir-1",  "María González Hernández", "actualizar", "Datos del usuario actualizados",  "u-admin", "Administrador del Sistema", dh(30, 9, 0));
        auditEntry("categoria","cat-mob", "Mobiliario",                "actualizar", "Descripción actualizada",         "u-admin", "Administrador del Sistema", dh(15, 11, 0));
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
            Optional<Producto> opt = findProductoById(m.getProductoId());
            if (opt.isEmpty()) throw new java.sql.SQLException("Producto no encontrado: " + m.getProductoId());
            int stockActual = opt.get().getStockActual();

            if (expectedStockAnterior != null && stockActual != expectedStockAnterior) {
                throw new java.sql.SQLException("El stock cambió desde que se capturó el conteo (esperado "
                    + expectedStockAnterior + ", actual " + stockActual + ") — no se aplicó el ajuste.");
            }
            if (m.getTipo() == TipoMovimiento.SALIDA && m.getCantidad() > stockActual) {
                throw new java.sql.SQLException("La cantidad supera el stock disponible (" + stockActual + ")");
            }

            int stockNuevo = com.sibim.util.ProductoUtils.calcularStockNuevo(
                m.getTipo().getCodigo(), stockActual, m.getCantidad());
            m.setStockAnterior(stockActual);
            m.setStockNuevo(stockNuevo);
            m.setEstado(Movimiento.ESTADO_APROBADO);

            if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
                m.setAreaOrigen(opt.get().getArea());
                updateProductoArea(m.getProductoId(), m.getAreaDestino());
            }
            MOVIMIENTOS.add(0, m);
            updateProductoStock(m.getProductoId(), stockNuevo);
        }
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

    public static void aprobarTransferencia(String id) {
        synchronized (STOCK_LOCK) {
            MOVIMIENTOS.stream().filter(m -> m.getId().equals(id) && m.isPendiente()).findFirst()
                .ifPresent(m -> {
                    m.setEstado(Movimiento.ESTADO_APROBADO);
                    updateProductoArea(m.getProductoId(), m.getAreaDestino());
                });
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
            Optional<Movimiento> found = MOVIMIENTOS.stream().filter(m -> m.getId().equals(id)).findFirst();
            if (found.isEmpty()) throw new java.sql.SQLException("Movimiento no encontrado: " + id);
            Movimiento m = found.get();
            if (!m.isPendiente()) {
                int idx = MOVIMIENTOS.indexOf(m);
                boolean hasNewer = MOVIMIENTOS.subList(0, idx).stream()
                    .anyMatch(other -> other.getProductoId().equals(m.getProductoId()));
                if (hasNewer) {
                    throw new java.sql.SQLException(
                        "Solo se puede eliminar el movimiento mas reciente de este producto: "
                        + "existen movimientos registrados despues de este.");
                }
            }
            MOVIMIENTOS.remove(m);
            if (!m.isPendiente()) {
                updateProductoStock(m.getProductoId(), m.getStockAnterior());
                if (m.getAreaOrigen() != null) updateProductoArea(m.getProductoId(), m.getAreaOrigen());
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
        return CONTEOS.stream()
            .filter(c -> c.getId().equals(conteoId))
            .findFirst()
            .map(ConteoFisico::getItems)
            .orElse(List.of());
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    private static Producto prod(String id, String nombre, String codigo,
                                  String catId, String catNombre, String catColor,
                                  int precioCompra, int precioVenta,
                                  int stockActual, int stockMinimo, int stockMaximo,
                                  UnidadMedida unidad, String area) {
        Producto p = new Producto();
        p.setId(id); p.setNombre(nombre); p.setCodigo(codigo);
        p.setCategoriaId(catId); p.setCategoriaNombre(catNombre); p.setCategoriaColor(catColor);
        p.setPrecioCompra(BigDecimal.valueOf(precioCompra));
        p.setPrecioVenta(BigDecimal.valueOf(precioVenta));
        p.setStockActual(stockActual); p.setStockMinimo(stockMinimo); p.setStockMaximo(stockMaximo);
        p.setUnidad(unidad); p.setArea(area);
        p.setCreadoEn(LocalDateTime.now().minusDays(30 + (int)(Math.random() * 60)));
        p.setActualizadoEn(LocalDateTime.now().minusDays((int)(Math.random() * 7)));
        return p;
    }

    private static Movimiento mov(String id, String productoId, String productoNombre,
                                   String categoriaColor, TipoMovimiento tipo,
                                   int cantidad, int stockAnterior, int stockNuevo,
                                   String motivo, String referencia,
                                   String usuarioId, String usuarioNombre,
                                   LocalDateTime creadoEn) {
        Movimiento m = new Movimiento();
        m.setId(id); m.setProductoId(productoId); m.setProductoNombre(productoNombre);
        m.setCategoriaColor(categoriaColor); m.setTipo(tipo);
        m.setCantidad(cantidad); m.setStockAnterior(stockAnterior); m.setStockNuevo(stockNuevo);
        m.setMotivo(motivo); m.setReferencia(referencia);
        m.setUsuarioId(usuarioId); m.setUsuarioNombre(usuarioNombre);
        m.setCreadoEn(creadoEn);
        return m;
    }

    /** días, hora, minuto → LocalDateTime relativo a hoy */
    private static LocalDateTime dh(int daysAgo, int hour, int minute) {
        return LocalDateTime.now().minusDays(daysAgo).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
    }

    private static void auditEntry(String entidad, String entidadId, String entidadNombre,
                                    String accion, String detalle,
                                    String usuarioId, String usuarioNombre, LocalDateTime fecha) {
        AuditLog a = new AuditLog();
        a.setId(UUID.randomUUID().toString());
        a.setEntidad(entidad); a.setEntidadId(entidadId); a.setEntidadNombre(entidadNombre);
        a.setAccion(accion); a.setDetalle(detalle);
        a.setUsuarioId(usuarioId); a.setUsuarioNombre(usuarioNombre);
        a.setCreadoEn(fecha);
        addAuditLog(a);
    }
}
