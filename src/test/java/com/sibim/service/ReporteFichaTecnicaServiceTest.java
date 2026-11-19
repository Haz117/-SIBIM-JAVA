package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.AuditLogRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for ReporteFichaTecnicaService — the class ReporteService.exportFichaTecnica
 * and exportFichasTecnicasMasivas actually delegate to (see ReporteService lines ~621-628).
 * These tests exercise the same implementation ReporteServiceExportTest reaches indirectly,
 * but call it directly and cover exportFichasTecnicasMasivas, which had no prior coverage.
 *
 * exportFichasTecnicasMasivas needs a MovimientoService; we use its package-private
 * constructor to inject mock repos, same idea as ReporteService's package-private
 * (ProductoRepository, MovimientoRepository) constructor used elsewhere in this package.
 * ReporteFichaTecnicaService itself only has a no-arg constructor though, which builds real
 * repositories including ConfiguracionRepository — demo mode is enabled so orgName() (used in
 * the PDF header) reads from its in-memory demo values instead of a real Postgres connection,
 * which isn't available in this test environment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteFichaTecnicaServiceTest {

    @Mock MovimientoRepository mockMovimientoRepo;
    @Mock ProductoRepository   mockProductoRepo;
    @Mock AuditLogRepository   mockAuditRepo;

    private ReporteFichaTecnicaService service;
    private MovimientoService movimientoService;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }
    private Producto activo;
    private Producto agotado;
    private Producto bajoStock;

    @BeforeEach
    void setUp() {
        activo = new Producto();
        activo.setId("p-01"); activo.setNombre("Laptop HP Elite"); activo.setCodigo("INF-001");
        activo.setCategoriaNombre("Equipo de Cómputo"); activo.setArea("Sala de cómputo");
        activo.setResguardante("Juan Pérez");
        activo.setPrecioCompra(BigDecimal.valueOf(15000)); activo.setPrecioVenta(BigDecimal.valueOf(18000));
        activo.setStockActual(5); activo.setStockMinimo(2); activo.setStockMaximo(10);

        // cantidad 0
        agotado = new Producto();
        agotado.setId("p-ag"); agotado.setNombre("Cartuchos de Tinta"); agotado.setCodigo("INF-002");
        agotado.setArea("Secretaria General Municipal");
        agotado.setPrecioVenta(BigDecimal.valueOf(500));
        agotado.setStockActual(0); agotado.setStockMinimo(5); agotado.setStockMaximo(20);

        // cantidad por debajo del antiguo mínimo
        bajoStock = new Producto();
        bajoStock.setId("p-bs"); bajoStock.setNombre("Papel Bond"); bajoStock.setCodigo("PAP-001");
        bajoStock.setArea("Secretaria General Municipal");
        bajoStock.setPrecioVenta(BigDecimal.valueOf(300));
        bajoStock.setStockActual(2); bajoStock.setStockMinimo(10); bajoStock.setStockMaximo(50);

        service = new ReporteFichaTecnicaService();
        movimientoService = new MovimientoService(mockMovimientoRepo, mockProductoRepo, mockAuditRepo);
    }

    // ── exportFichaTecnica(Producto, List<Movimiento>) ────────────────────────

    @Test void exportFichaTecnica_noMovimientos_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportFichaTecnica(activo, List.of()), ".pdf");
    }

    @Test void exportFichaTecnica_conMovimientos_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportFichaTecnica(activo, List.of(buildMovimiento())), ".pdf");
    }

    /** 18-nov-2026: 1,080 bienes have 0 saved as their costo: the ficha said «$0.00». */
    @Test void sinCostoCapturado_laFichaNoDiceCeroPesos() throws Exception {
        Producto sinCosto = new Producto();
        sinCosto.setId("p-sc"); sinCosto.setNombre("Silla de prueba"); sinCosto.setCodigo("PRU-001");
        sinCosto.setArea("Secretaria General Municipal");
        sinCosto.setPrecioCompra(BigDecimal.ZERO);
        sinCosto.setVidaUtilAnios(10);
        sinCosto.setFechaAdquisicion(java.time.LocalDate.of(2024, 3, 1));
        sinCosto.setStockActual(1);
        File f = service.exportFichaTecnica(sinCosto, List.of());
        StringBuilder texto = new StringBuilder();
        try (var pdf = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(f))) {
            for (int i = 1; i <= pdf.getNumberOfPages(); i++)
                texto.append(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(pdf.getPage(i)));
        }
        assertTrue(texto.toString().contains("COSTO DE ADQUISICIÓN —"), texto.toString());
        assertTrue(texto.toString().contains("VALOR EN LIBROS —"), texto.toString());
        assertFalse(texto.toString().contains("$0.00"), texto.toString());
    }

    // ── exportFichasTecnicasMasivas(List<Producto>, MovimientoService) ────────

    @Test void exportFichasTecnicasMasivas_conVariosBienes_createsNonEmptyFile() throws Exception {
        when(mockMovimientoRepo.findByProductoIds(anyList())).thenReturn(Map.of(
            "p-01", List.of(buildMovimiento())
        ));

        File file = service.exportFichasTecnicasMasivas(
            List.of(activo, agotado, bajoStock), movimientoService);

        assertFileProduced(file, ".pdf");
    }

    @Test void exportFichasTecnicasMasivas_sinMovimientos_createsNonEmptyFile() throws Exception {
        when(mockMovimientoRepo.findByProductoIds(anyList())).thenReturn(Map.of());

        File file = service.exportFichasTecnicasMasivas(List.of(activo, agotado), movimientoService);

        assertFileProduced(file, ".pdf");
    }

    @Test void exportFichasTecnicasMasivas_listaVacia_lanzaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> service.exportFichasTecnicasMasivas(List.of(), movimientoService));
    }

    @Test void exportFichasTecnicasMasivas_listaNull_lanzaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> service.exportFichasTecnicasMasivas(null, movimientoService));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Movimiento buildMovimiento() {
        Movimiento m = new Movimiento();
        m.setTipo(TipoMovimiento.ENTRADA); m.setCantidad(5);
        m.setStockAnterior(0); m.setStockNuevo(5);
        m.setMotivo("Compra inicial"); m.setProductoNombre("Laptop HP Elite");
        m.setUsuarioNombre("Admin Test"); m.setCreadoEn(LocalDateTime.now());
        return m;
    }

    private static void assertFileProduced(File file, String expectedExtension) {
        assertNotNull(file);
        assertTrue(file.exists(), "El archivo debe existir: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(expectedExtension),
            "Se esperaba extensión " + expectedExtension + " pero fue: " + file.getName());
    }
}
