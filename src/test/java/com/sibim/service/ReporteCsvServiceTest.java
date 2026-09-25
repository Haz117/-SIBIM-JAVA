package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Comodato;
import com.sibim.model.Movimiento;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ReporteCsvService. The CSV files are read back and asserted line by
 * line (header, row count, quoting, computed states), not just checked for existence.
 *
 * Repositories are mocked; demo mode keeps the ReporteService constructor from
 * reaching a real database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteCsvServiceTest {

    @Mock ProductoRepository   productoRepo;
    @Mock MovimientoRepository movimientoRepo;

    private ReporteCsvService service;
    private Producto activo;
    private Producto agotado;
    private Producto bajoStock;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        activo = producto("Laptop HP", "INF-001", "Sala A", 5, 2, 10, 15000);
        agotado = producto("Cartuchos", "INF-002", "Sala A", 0, 5, 20, 500);
        bajoStock = producto("Papel Bond", "PAP-001", null, 2, 10, 50, 300);
        service = new ReporteCsvService(productoRepo, movimientoRepo);
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Producto producto(String nombre, String codigo, String area,
                                     int stock, int min, int max, double precio) {
        Producto p = new Producto();
        p.setId("id-" + codigo);
        p.setNombre(nombre);
        p.setCodigo(codigo);
        p.setArea(area);
        p.setCategoriaNombre("Categoria");
        p.setStockActual(stock);
        p.setStockMinimo(min);
        p.setStockMaximo(max);
        p.setPrecioCompra(BigDecimal.valueOf(precio));
        p.setPrecioVenta(BigDecimal.valueOf(precio));
        return p;
    }

    private static Movimiento movimiento(String producto) {
        Movimiento m = new Movimiento();
        m.setProductoNombre(producto);
        m.setTipo(TipoMovimiento.ENTRADA);
        m.setCantidad(3);
        m.setStockAnterior(2);
        m.setStockNuevo(5);
        m.setMotivo("Compra");
        m.setReferencia("FAC-1");
        m.setUsuarioNombre("Admin");
        m.setCreadoEn(LocalDateTime.of(2026, 3, 10, 9, 30));
        return m;
    }

    private static List<String> lineas(File f) throws Exception {
        assertNotNull(f, "Se esperaba un archivo");
        assertTrue(f.getName().endsWith(".csv"));
        return Files.readAllLines(f.toPath(), Charset.defaultCharset());
    }

    // ── Inventario ───────────────────────────────────────────────────────────

    @Test
    void exportInventarioCsv_lista_escribeEncabezadoYUnaFilaPorBien() throws Exception {
        List<String> l = lineas(service.exportInventarioCsv(List.of(activo, agotado)));

        assertEquals(3, l.size(), "encabezado + 2 bienes");
        assertTrue(l.get(0).startsWith("Nombre,Codigo,Categoria,Area,Resguardante,Stock"));
        assertTrue(l.get(1).startsWith("\"Laptop HP\",\"INF-001\""));
        assertTrue(l.get(2).startsWith("\"Cartuchos\",\"INF-002\""));
    }

    @Test
    void exportInventarioCsv_neutralizaInyeccionDeFormulas() throws Exception {
        activo.setNombre("=HYPERLINK(\"http://evil\")");

        List<String> l = lineas(service.exportInventarioCsv(List.of(activo)));

        assertTrue(l.get(1).startsWith("\"'=HYPERLINK(\"\"http://evil\"\")\""),
            "Un valor que empieza con '=' se antepone con apóstrofo y las comillas se duplican: " + l.get(1));
    }

    @Test
    void exportInventarioCsv_rangoDeFechas_usaFindByDateRange() throws Exception {
        LocalDate d = LocalDate.of(2026, 1, 1), h = LocalDate.of(2026, 12, 31);
        when(productoRepo.findByDateRange(d, h)).thenReturn(List.of(activo));

        List<String> l = lineas(service.exportInventarioCsv(d, h));

        assertEquals(2, l.size());
        verify(productoRepo).findByDateRange(d, h);
    }

    @Test
    void exportInventarioCsv_sinFechas_usaFindAll() throws Exception {
        when(productoRepo.findAll()).thenReturn(List.of(activo, bajoStock));

        assertEquals(3, lineas(service.exportInventarioCsv((LocalDate) null, null)).size());
        verify(productoRepo).findAll();
    }

    @Test
    void exportInventarioCsv_sinResultados_retornaNull() throws Exception {
        when(productoRepo.findAll()).thenReturn(List.of());
        when(productoRepo.findByDateRange(any(), any())).thenReturn(List.of());

        assertNull(service.exportInventarioCsv((LocalDate) null, null));
        assertNull(service.exportInventarioCsv(LocalDate.now(), LocalDate.now()));
    }

    @Test
    void exportInventarioCsv_masDe50milFilas_rechazaElReporte() throws Exception {
        when(productoRepo.findByDateRange(any(), any())).thenReturn(Collections.nCopies(50_001, activo));

        Exception ex = assertThrows(Exception.class,
            () -> service.exportInventarioCsv(LocalDate.now(), LocalDate.now()));

        assertTrue(ex.getMessage().contains("50001"), "El mensaje indica cuántas filas se pidieron");
    }

    // ── Movimientos ──────────────────────────────────────────────────────────

    @Test
    void exportMovimientosCsv_lista_escribeCantidadesYTipo() throws Exception {
        List<String> l = lineas(service.exportMovimientosCsv(List.of(movimiento("Laptop HP"))));

        assertEquals(2, l.size());
        assertTrue(l.get(0).startsWith("Producto,Tipo,Cantidad,Stock Anterior,Stock Nuevo"));
        assertTrue(l.get(1).startsWith("\"Laptop HP\",\"Entrada\",3,2,5,\"Compra\",\"FAC-1\",\"Admin\""));
    }

    @Test
    void exportMovimientosCsv_rangoDeFechas_yVacio() throws Exception {
        LocalDate d = LocalDate.of(2026, 3, 1), h = LocalDate.of(2026, 3, 31);
        when(movimientoRepo.findByDateRange(d, h)).thenReturn(List.of(movimiento("A"), movimiento("B")));

        assertEquals(3, lineas(service.exportMovimientosCsv(d, h)).size());

        when(movimientoRepo.findByDateRange(d, h)).thenReturn(List.of());
        assertNull(service.exportMovimientosCsv(d, h));
    }

    // ── Distribución y alertas ───────────────────────────────────────────────

    @Test
    void exportDistribucionCsv_agrupaPorAreaConConteosDeAlertas() throws Exception {
        when(productoRepo.findAll()).thenReturn(List.of(activo, agotado, bajoStock));

        List<String> l = lineas(service.exportDistribucionCsv());

        assertEquals(3, l.size(), "encabezado + 'Sala A' + área sin asignar");
        // Se valida por contenido, no por posición de la fila.
        String salaA = l.stream().filter(s -> s.startsWith("\"Sala A\"")).findFirst().orElseThrow();
        // 2 bienes (activo + agotado): valor 5*15000 + 0*500 = 75000.00, 1 agotado, 0 bajo stock
        assertEquals("\"Sala A\",2,75000.00,1,0", salaA);
        String sinArea = l.stream().filter(s -> s.startsWith("\"Sin ")).findFirst().orElseThrow();
        // 1 bien (bajoStock, sin área): valor 2*300 = 600.00, 0 agotados, 1 bajo stock
        assertTrue(sinArea.endsWith(",1,600.00,0,1"), sinArea);
    }

    @Test
    void exportAlertasCsv_soloAgotadosYBajoStock() throws Exception {
        when(productoRepo.findAll()).thenReturn(List.of(activo, agotado, bajoStock));

        List<String> l = lineas(service.exportAlertasCsv());

        assertEquals(3, l.size(), "encabezado + 1 agotado + 1 bajo stock (el activo no se lista)");
        assertTrue(l.get(1).startsWith("\"Agotado\",\"Cartuchos\""));
        assertTrue(l.get(2).startsWith("\"Bajo Stock\",\"Papel Bond\""));
    }

    // ── Documentos patrimoniales ─────────────────────────────────────────────

    @Test
    void exportResguardosCsv_cuentaLosBienesDelResguardo() throws Exception {
        Resguardo r = new Resguardo();
        r.setNumero("RSG-2026-000001");
        r.setResguardanteNombre("Ana Ruiz");
        r.setResguardanteArea("Tesorería");
        r.setResguardanteCargo("Directora");
        r.setCreadoEn(LocalDateTime.of(2026, 2, 5, 8, 0));
        r.setItems(List.of(new ResguardoItem(), new ResguardoItem()));
        Resguardo sinFecha = new Resguardo();
        sinFecha.setNumero("RSG-2026-000002");

        List<String> l = lineas(service.exportResguardosCsv(List.of(r, sinFecha)));

        assertEquals(3, l.size());
        assertEquals("\"RSG-2026-000001\",\"Ana Ruiz\",\"Tesorería\",\"Directora\",\"05/02/2026\",2,\"ACTIVO\"", l.get(1));
        assertTrue(l.get(2).contains(",\"\",0,\"ACTIVO\""), "Sin fecha ni bienes: fecha vacía y 0 bienes");
    }

    @Test
    void exportComodatosCsv_marcaVencidoCuandoPasoLaFechaFin() throws Exception {
        Comodato vencido = new Comodato();
        vencido.setNumero("CDT-1");
        vencido.setProductoNombre("Proyector");
        vencido.setEntidadReceptora("DIF");
        vencido.setFechaInicio(LocalDate.now().minusDays(60));
        vencido.setFechaFin(LocalDate.now().minusDays(5));
        Comodato indefinido = new Comodato();
        indefinido.setNumero("CDT-2");

        List<String> l = lineas(service.exportComodatosCsv(List.of(vencido, indefinido)));

        assertEquals(3, l.size());
        assertTrue(l.get(1).endsWith(",\"VENCIDO\""), "Estado efectivo VENCIDO: " + l.get(1));
        assertTrue(l.get(2).endsWith(",\"\",\"\",\"VIGENTE\""), "Sin fechas: vacías y VIGENTE: " + l.get(2));
    }

    @Test
    void exportPrestamosCsv_marcaVencidoCuandoSePasoLaDevolucionPrevista() throws Exception {
        Prestamo vencido = new Prestamo();
        vencido.setNumero("PRS-1");
        vencido.setProductoNombre("Laptop");
        vencido.setAreaOrigen("Sistemas");
        vencido.setAreaDestino("Tesorería");
        vencido.setResponsableNombre("Luis");
        vencido.setFechaPrestamo(LocalDate.now().minusDays(30));
        vencido.setFechaDevolucionPrevista(LocalDate.now().minusDays(3));
        assertTrue(vencido.isVencidoCalc(), "Precondición: préstamo activo con devolución prevista pasada");
        Prestamo devuelto = new Prestamo();
        devuelto.setNumero("PRS-2");
        devuelto.setEstado(Prestamo.ESTADO_DEVUELTO);

        List<String> l = lineas(service.exportPrestamosCsv(List.of(vencido, devuelto)));

        assertEquals(3, l.size());
        assertTrue(l.get(1).endsWith(",\"VENCIDO\""), l.get(1));
        assertTrue(l.get(2).endsWith(",\"DEVUELTO\""), l.get(2));
    }
}
