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
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ReporteExcelService. Each workbook is re-opened with Apache POI and
 * asserted on sheet names, headers, row counts and cell values — not just that a
 * file was written.
 *
 * Repositories are mocked; demo mode keeps the ReporteService constructor and the
 * "_Info" sheet (orgName) from reaching a real database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteExcelServiceTest {

    @Mock ProductoRepository   productoRepo;
    @Mock MovimientoRepository movimientoRepo;

    private ReporteExcelService service;
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
        service = new ReporteExcelService(productoRepo, movimientoRepo);
    }

    // ── fixtures / helpers ───────────────────────────────────────────────────

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
        m.setTipo(TipoMovimiento.SALIDA);
        m.setCantidad(1);
        m.setStockAnterior(5);
        m.setStockNuevo(4);
        m.setUsuarioNombre("Admin");
        m.setCreadoEn(LocalDateTime.of(2026, 3, 10, 9, 30));
        return m;
    }

    private static final DataFormatter FMT = new DataFormatter();

    private static Workbook abrir(File f) throws Exception {
        assertNotNull(f, "Se esperaba un archivo");
        assertTrue(f.getName().endsWith(".xlsx"));
        assertTrue(f.length() > 0);
        try (FileInputStream in = new FileInputStream(f)) {
            return new XSSFWorkbook(in);
        }
    }

    private static String celda(Sheet s, int fila, int col) {
        Row r = s.getRow(fila);
        return r == null || r.getCell(col) == null ? "" : FMT.formatCellValue(r.getCell(col));
    }

    /** Filas con datos (sin el encabezado). */
    private static int filasDeDatos(Sheet s) { return s.getLastRowNum(); }

    // ── Inventario ───────────────────────────────────────────────────────────

    @Test
    void exportInventarioExcel_lista_hojaInventarioMasInfo() throws Exception {
        try (Workbook wb = abrir(service.exportInventarioExcel(List.of(activo, agotado)))) {
            assertEquals("Inventario", wb.getSheetAt(0).getSheetName());
            assertEquals("_Info", wb.getSheetAt(wb.getNumberOfSheets() - 1).getSheetName());
            Sheet s = wb.getSheet("Inventario");
            assertEquals("Nombre", celda(s, 0, 0));
            assertEquals(2, filasDeDatos(s));
            assertEquals("Laptop HP", celda(s, 1, 0));
            assertEquals("INF-002", celda(s, 2, 1));
        }
    }

    @Test
    void exportInventarioExcel_rangoDeFechas_usaFindByDateRange_yVacioRetornaNull() throws Exception {
        LocalDate d = LocalDate.of(2026, 1, 1), h = LocalDate.of(2026, 6, 30);
        when(productoRepo.findByDateRange(d, h)).thenReturn(List.of(activo));

        try (Workbook wb = abrir(service.exportInventarioExcel(d, h))) {
            assertEquals(1, filasDeDatos(wb.getSheet("Inventario")));
        }
        verify(productoRepo).findByDateRange(d, h);

        when(productoRepo.findAll()).thenReturn(List.of());
        assertNull(service.exportInventarioExcel((LocalDate) null, null));
    }

    // ── Movimientos ──────────────────────────────────────────────────────────

    @Test
    void exportMovimientosExcel_lista_escribeTipoYCantidades() throws Exception {
        try (Workbook wb = abrir(service.exportMovimientosExcel(List.of(movimiento("Laptop HP"))))) {
            Sheet s = wb.getSheet("Movimientos");
            assertEquals("Producto", celda(s, 0, 0));
            assertEquals("Laptop HP", celda(s, 1, 0));
            assertEquals("Salida", celda(s, 1, 1));
            assertEquals("1", celda(s, 1, 2));
            assertEquals("5", celda(s, 1, 3));
            assertEquals("4", celda(s, 1, 4));
        }
    }

    @Test
    void exportMovimientosExcel_rangoDeFechas_yVacioRetornaNull() throws Exception {
        LocalDate d = LocalDate.of(2026, 3, 1), h = LocalDate.of(2026, 3, 31);
        when(movimientoRepo.findByDateRange(d, h)).thenReturn(List.of(movimiento("A"), movimiento("B")));

        try (Workbook wb = abrir(service.exportMovimientosExcel(d, h))) {
            assertEquals(2, filasDeDatos(wb.getSheet("Movimientos")));
        }

        when(movimientoRepo.findByDateRange(d, h)).thenReturn(List.of());
        assertNull(service.exportMovimientosExcel(d, h));
    }

    // ── Distribución y alertas ───────────────────────────────────────────────

    @Test
    void exportDistribucionExcel_resumenYDetallePorArea() throws Exception {
        when(productoRepo.findAll()).thenReturn(List.of(activo, agotado, bajoStock));

        try (Workbook wb = abrir(service.exportDistribucionExcel())) {
            Sheet resumen = wb.getSheetAt(0);
            assertEquals(2, filasDeDatos(resumen), "2 grupos: 'Sala A' y el área sin asignar");
            // Filas ordenadas por nombre de área — se localiza 'Sala A' por contenido.
            int fila = celda(resumen, 1, 0).equals("Sala A") ? 1 : 2;
            assertEquals("Sala A", celda(resumen, fila, 0));
            assertEquals("2", celda(resumen, fila, 1));      // bienes
            assertEquals("75000", celda(resumen, fila, 2));  // valor total
            assertEquals("1", celda(resumen, fila, 3));      // agotados

            Sheet detalle = wb.getSheetAt(1);
            assertEquals(3, filasDeDatos(detalle), "un renglón por bien");
        }
    }

    @Test
    void exportAlertasExcel_soloAgotadosYBajoStock() throws Exception {
        when(productoRepo.findAll()).thenReturn(List.of(activo, agotado, bajoStock));

        try (Workbook wb = abrir(service.exportAlertasExcel())) {
            Sheet s = wb.getSheet("Alertas");
            assertEquals(2, filasDeDatos(s), "el bien activo no genera alerta");
            assertEquals("Cartuchos", celda(s, 1, 0));   // agotados primero
            assertEquals("Papel Bond", celda(s, 2, 0));
        }
    }

    // ── Documentos patrimoniales ─────────────────────────────────────────────

    @Test
    void exportResguardosExcel_cuentaBienes_yTolerarCamposNulos() throws Exception {
        Resguardo r = new Resguardo();
        r.setNumero("RSG-2026-000001");
        r.setResguardanteNombre("Ana Ruiz");
        r.setResguardanteArea("Tesorería");
        r.setResguardanteCargo("Directora");
        r.setCreadoEn(LocalDateTime.of(2026, 2, 5, 8, 0));
        r.setItems(List.of(new ResguardoItem(), new ResguardoItem()));
        Resguardo vacio = new Resguardo();
        vacio.setNumero("RSG-2026-000002");

        try (Workbook wb = abrir(service.exportResguardosExcel(List.of(r, vacio)))) {
            Sheet s = wb.getSheet("Resguardos");
            assertEquals(2, filasDeDatos(s));
            assertEquals("Ana Ruiz", celda(s, 1, 1));
            assertEquals("05/02/2026", celda(s, 1, 4));
            assertEquals("2", celda(s, 1, 5));
            assertEquals("", celda(s, 2, 4), "sin fecha");
            assertEquals("0", celda(s, 2, 5), "sin bienes");
        }
    }

    @Test
    void exportComodatosExcel_marcaVencido() throws Exception {
        Comodato vencido = new Comodato();
        vencido.setNumero("CDT-1");
        vencido.setProductoNombre("Proyector");
        vencido.setEntidadReceptora("DIF");
        vencido.setFechaInicio(LocalDate.now().minusDays(60));
        vencido.setFechaFin(LocalDate.now().minusDays(5));
        Comodato indefinido = new Comodato();
        indefinido.setNumero("CDT-2");

        try (Workbook wb = abrir(service.exportComodatosExcel(List.of(vencido, indefinido)))) {
            Sheet s = wb.getSheet("Comodatos");
            assertEquals(2, filasDeDatos(s));
            assertEquals("VENCIDO", celda(s, 1, 7));
            assertEquals("VIGENTE", celda(s, 2, 7));
            assertEquals("", celda(s, 2, 5), "sin fecha de inicio");
        }
    }

    @Test
    void exportPrestamosExcel_marcaVencido() throws Exception {
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

        try (Workbook wb = abrir(service.exportPrestamosExcel(List.of(vencido, devuelto)))) {
            Sheet s = wb.getSheet("Préstamos");
            assertEquals(2, filasDeDatos(s));
            assertEquals("VENCIDO", celda(s, 1, 8));
            assertEquals("DEVUELTO", celda(s, 2, 8));
        }
    }

    // ── hoja _Info ───────────────────────────────────────────────────────────

    @Test
    void todasLasExportacionesAgreganLaHojaInfoConElPeriodo() throws Exception {
        LocalDate d = LocalDate.of(2026, 3, 1), h = LocalDate.of(2026, 3, 31);
        when(movimientoRepo.findByDateRange(d, h)).thenReturn(List.of(movimiento("A")));

        try (Workbook wb = abrir(service.exportMovimientosExcel(d, h))) {
            Sheet info = wb.getSheet("_Info");
            assertNotNull(info);
            assertEquals("Reporte", celda(info, 0, 0));
            assertTrue(celda(info, 0, 1).contains("Registro de Movimientos"));
            assertEquals("Período", celda(info, 4, 0));
            assertTrue(celda(info, 4, 1).contains("01/03/2026") && celda(info, 4, 1).contains("31/03/2026"),
                celda(info, 4, 1));
        }
    }
}
