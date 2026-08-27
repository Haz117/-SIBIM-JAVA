package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for ReporteService export methods.
 *
 * List-based variants (exportInventarioExcel(List), exportMovimientosExcel(List),
 * exportFichaTecnica) receive data directly and never touch the DB. The repos are
 * still mocked because ReporteService instantiates them in field initializers
 * (new ProductoRepository(), new MovimientoRepository()), which would otherwise
 * attempt a DB connection during construction.
 *
 * Repo-calling variants (exportAlertasExcel, exportAlertasPdf, exportAlertasCsv)
 * mock findAll() to return controlled data and verify the generated file.
 *
 * Every test asserts the returned File exists on disk and is non-empty; the
 * content is opaque (PDF/XLSX binary), so structural correctness is the iText /
 * POI library's responsibility.
 */
class ReporteServiceExportTest {

    private Producto activo;
    private Producto agotado;
    private Producto bajoStock;

    @BeforeEach
    void setUp() {
        activo = new Producto();
        activo.setId("p-01");
        activo.setNombre("Laptop HP Elite");
        activo.setCodigo("INF-001");
        activo.setCategoriaNombre("Equipo de Cómputo");
        activo.setArea("Sala de cómputo");
        activo.setResguardante("Juan Pérez");
        activo.setPrecioCompra(BigDecimal.valueOf(15000));
        activo.setPrecioVenta(BigDecimal.valueOf(18000));
        activo.setStockActual(5);
        activo.setStockMinimo(2);
        activo.setStockMaximo(10);

        // stock = 0 → EstadoProducto.AGOTADO
        agotado = new Producto();
        agotado.setId("p-ag");
        agotado.setNombre("Cartuchos de Tinta");
        agotado.setCodigo("INF-002");
        agotado.setArea("Secretaria General Municipal");
        agotado.setPrecioVenta(BigDecimal.valueOf(500));
        agotado.setStockActual(0);
        agotado.setStockMinimo(5);
        agotado.setStockMaximo(20);

        // stock < minimo → EstadoProducto.BAJO_STOCK
        bajoStock = new Producto();
        bajoStock.setId("p-bs");
        bajoStock.setNombre("Papel Bond");
        bajoStock.setCodigo("PAP-001");
        bajoStock.setArea("Secretaria General Municipal");
        bajoStock.setPrecioVenta(BigDecimal.valueOf(300));
        bajoStock.setStockActual(2);
        bajoStock.setStockMinimo(10);
        bajoStock.setStockMaximo(50);
    }

    // ── exportInventarioExcel(List<Producto>) ─────────────────────────────────

    @Test
    void exportInventarioExcel_withData_createsNonEmptyFile() throws Exception {
        withMockedRepos(svc -> {
            File file = svc.exportInventarioExcel(List.of(activo, bajoStock));
            assertFileProduced(file, ".xlsx");
        });
    }

    @Test
    void exportInventarioExcel_emptyList_createsFile() throws Exception {
        withMockedRepos(svc -> {
            File file = svc.exportInventarioExcel(List.of());
            assertFileProduced(file, ".xlsx");
        });
    }

    // ── exportMovimientosExcel(List<Movimiento>) ──────────────────────────────

    @Test
    void exportMovimientosExcel_withData_createsNonEmptyFile() throws Exception {
        Movimiento mov = buildMovimiento(TipoMovimiento.ENTRADA, 5, 0, 5, "Compra inicial");
        withMockedRepos(svc -> {
            File file = svc.exportMovimientosExcel(List.of(mov));
            assertFileProduced(file, ".xlsx");
        });
    }

    @Test
    void exportMovimientosExcel_emptyList_createsFile() throws Exception {
        withMockedRepos(svc -> {
            File file = svc.exportMovimientosExcel(List.of());
            assertFileProduced(file, ".xlsx");
        });
    }

    // ── exportFichaTecnica(Producto, List<Movimiento>) ────────────────────────

    @Test
    void exportFichaTecnica_withNoMovimientos_createsNonEmptyFile() throws Exception {
        withMockedRepos(svc -> {
            File file = svc.exportFichaTecnica(activo, List.of());
            assertFileProduced(file, ".pdf");
        });
    }

    @Test
    void exportFichaTecnica_withMovimientos_createsNonEmptyFile() throws Exception {
        Movimiento mov = buildMovimiento(TipoMovimiento.ENTRADA, 5, 0, 5, "Compra");
        withMockedRepos(svc -> {
            File file = svc.exportFichaTecnica(activo, List.of(mov));
            assertFileProduced(file, ".pdf");
        });
    }

    @Test
    void exportFichaTecnica_agotadoProducto_createsFile() throws Exception {
        withMockedRepos(svc -> {
            File file = svc.exportFichaTecnica(agotado, List.of());
            assertFileProduced(file, ".pdf");
        });
    }

    // ── exportAlertasExcel() ──────────────────────────────────────────────────

    @Test
    void exportAlertasExcel_emptyInventory_createsFile() throws Exception {
        withMockedFindAll(List.of(), svc -> {
            File file = svc.exportAlertasExcel();
            assertFileProduced(file, ".xlsx");
        });
    }

    @Test
    void exportAlertasExcel_withAgotadosAndBajoStock_createsNonEmptyFile() throws Exception {
        withMockedFindAll(List.of(agotado, bajoStock, activo), svc -> {
            File file = svc.exportAlertasExcel();
            assertFileProduced(file, ".xlsx");
        });
    }

    // ── exportAlertasPdf() ────────────────────────────────────────────────────

    @Test
    void exportAlertasPdf_emptyInventory_createsFile() throws Exception {
        withMockedFindAll(List.of(), svc -> {
            File file = svc.exportAlertasPdf();
            assertFileProduced(file, ".pdf");
        });
    }

    @Test
    void exportAlertasPdf_withAlerts_createsNonEmptyFile() throws Exception {
        withMockedFindAll(List.of(agotado, bajoStock), svc -> {
            File file = svc.exportAlertasPdf();
            assertFileProduced(file, ".pdf");
        });
    }

    // ── exportAlertasCsv() ────────────────────────────────────────────────────

    @Test
    void exportAlertasCsv_withAlerts_createsNonEmptyFile() throws Exception {
        withMockedFindAll(List.of(agotado, bajoStock), svc -> {
            File file = svc.exportAlertasCsv();
            assertFileProduced(file, ".csv");
        });
    }

    @Test
    void exportAlertasCsv_emptyInventory_createsFile() throws Exception {
        withMockedFindAll(List.of(), svc -> {
            File file = svc.exportAlertasCsv();
            assertFileProduced(file, ".csv");
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface ServiceConsumer {
        void accept(ReporteService svc) throws Exception;
    }

    /** Mocks both repos (no findAll stub — for list-based methods that don't call them). */
    private void withMockedRepos(ServiceConsumer consumer) throws Exception {
        try (MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class);
             MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class)) {
            consumer.accept(new ReporteService());
        }
    }

    /** Mocks both repos with a findAll() stub returning the given list. */
    private void withMockedFindAll(List<Producto> data, ServiceConsumer consumer) throws Exception {
        try (MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findAll()).thenReturn(data));
             MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class)) {
            consumer.accept(new ReporteService());
        }
    }

    private static Movimiento buildMovimiento(TipoMovimiento tipo, int cantidad,
                                               int stockAnterior, int stockNuevo, String motivo) {
        Movimiento m = new Movimiento();
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setStockAnterior(stockAnterior);
        m.setStockNuevo(stockNuevo);
        m.setMotivo(motivo);
        m.setProductoNombre("Laptop HP Elite");
        m.setUsuarioNombre("Admin Test");
        m.setCreadoEn(LocalDateTime.now());
        return m;
    }

    private static void assertFileProduced(File file, String expectedExtension) {
        assertNotNull(file, "El método debe retornar un File no nulo");
        assertTrue(file.exists(), "El archivo debe existir en disco: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(expectedExtension),
            "Se esperaba extensión " + expectedExtension + " pero fue: " + file.getName());
    }
}
