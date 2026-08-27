package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for ReporteService export methods.
 *
 * Now that ReporteService accepts repositories via constructor, tests inject mocks
 * directly instead of using MockedConstruction — no bytecode manipulation needed.
 *
 * List-based methods (exportInventarioExcel(List), exportMovimientosExcel(List),
 * exportFichaTecnica) never call the repos; mock repos are injected but unused.
 * Repo-calling methods (exportAlertasExcel, exportAlertasPdf, exportAlertasCsv)
 * stub findAll() on the product repo.
 *
 * Every test asserts the returned File exists on disk and is non-empty.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReporteServiceExportTest {

    @Mock ProductoRepository   mockProductoRepo;
    @Mock MovimientoRepository mockMovimientoRepo;

    private ReporteService service;
    private Producto activo;
    private Producto agotado;
    private Producto bajoStock;

    @BeforeEach
    void setUp() throws Exception {
        activo = new Producto();
        activo.setId("p-01"); activo.setNombre("Laptop HP Elite"); activo.setCodigo("INF-001");
        activo.setCategoriaNombre("Equipo de Cómputo"); activo.setArea("Sala de cómputo");
        activo.setResguardante("Juan Pérez");
        activo.setPrecioCompra(BigDecimal.valueOf(15000)); activo.setPrecioVenta(BigDecimal.valueOf(18000));
        activo.setStockActual(5); activo.setStockMinimo(2); activo.setStockMaximo(10);

        // stock = 0 → EstadoProducto.AGOTADO
        agotado = new Producto();
        agotado.setId("p-ag"); agotado.setNombre("Cartuchos de Tinta"); agotado.setCodigo("INF-002");
        agotado.setArea("Secretaria General Municipal");
        agotado.setPrecioVenta(BigDecimal.valueOf(500));
        agotado.setStockActual(0); agotado.setStockMinimo(5); agotado.setStockMaximo(20);

        // stock < minimo → EstadoProducto.BAJO_STOCK
        bajoStock = new Producto();
        bajoStock.setId("p-bs"); bajoStock.setNombre("Papel Bond"); bajoStock.setCodigo("PAP-001");
        bajoStock.setArea("Secretaria General Municipal");
        bajoStock.setPrecioVenta(BigDecimal.valueOf(300));
        bajoStock.setStockActual(2); bajoStock.setStockMinimo(10); bajoStock.setStockMaximo(50);

        service = new ReporteService(mockProductoRepo, mockMovimientoRepo);
    }

    // ── exportInventarioExcel(List<Producto>) ─────────────────────────────────

    @Test void exportInventarioExcel_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportInventarioExcel(List.of(activo, bajoStock)), ".xlsx");
    }

    @Test void exportInventarioExcel_emptyList_createsFile() throws Exception {
        assertFileProduced(service.exportInventarioExcel(List.of()), ".xlsx");
    }

    // ── exportMovimientosExcel(List<Movimiento>) ──────────────────────────────

    @Test void exportMovimientosExcel_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportMovimientosExcel(List.of(buildMovimiento())), ".xlsx");
    }

    @Test void exportMovimientosExcel_emptyList_createsFile() throws Exception {
        assertFileProduced(service.exportMovimientosExcel(List.of()), ".xlsx");
    }

    // ── exportFichaTecnica(Producto, List<Movimiento>) ────────────────────────

    @Test void exportFichaTecnica_noMovimientos_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportFichaTecnica(activo, List.of()), ".pdf");
    }

    @Test void exportFichaTecnica_conMovimientos_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportFichaTecnica(activo, List.of(buildMovimiento())), ".pdf");
    }

    @Test void exportFichaTecnica_agotado_createsFile() throws Exception {
        assertFileProduced(service.exportFichaTecnica(agotado, List.of()), ".pdf");
    }

    // ── exportAlertasExcel() ──────────────────────────────────────────────────

    @Test void exportAlertasExcel_emptyInventory_createsFile() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of());
        assertFileProduced(service.exportAlertasExcel(), ".xlsx");
    }

    @Test void exportAlertasExcel_withAlerts_createsNonEmptyFile() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of(agotado, bajoStock, activo));
        assertFileProduced(service.exportAlertasExcel(), ".xlsx");
    }

    // ── exportAlertasPdf() ────────────────────────────────────────────────────

    @Test void exportAlertasPdf_emptyInventory_createsFile() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of());
        assertFileProduced(service.exportAlertasPdf(), ".pdf");
    }

    @Test void exportAlertasPdf_withAlerts_createsNonEmptyFile() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of(agotado, bajoStock));
        assertFileProduced(service.exportAlertasPdf(), ".pdf");
    }

    // ── exportAlertasCsv() ────────────────────────────────────────────────────

    @Test void exportAlertasCsv_withAlerts_createsNonEmptyFile() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of(agotado, bajoStock));
        assertFileProduced(service.exportAlertasCsv(), ".csv");
    }

    @Test void exportAlertasCsv_emptyInventory_createsFile() throws Exception {
        when(mockProductoRepo.findAll()).thenReturn(List.of());
        assertFileProduced(service.exportAlertasCsv(), ".csv");
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
