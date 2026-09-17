package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteDashboardService.exportDashboardPdf.
 *
 * ReporteDashboardService has a public no-arg constructor (no repos to mock);
 * DashboardService.Resumen is built by hand as a fixture instead. Demo mode is
 * enabled so orgName() (used in the PDF header) reads from ConfiguracionRepository's
 * in-memory demo values instead of a real Postgres connection, which isn't
 * available in this test environment.
 *
 * Every test asserts the returned File exists on disk and is non-empty.
 */
class ReporteDashboardServiceTest {

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    private final ReporteDashboardService service = new ReporteDashboardService();

    @Test void exportDashboardPdf_datosPoblados_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportDashboardPdf(buildResumen(), null), ".pdf");
    }

    @Test void exportDashboardPdf_conDestFolder_createsFileInFolder() throws Exception {
        String destFolder = Files.createTempDirectory("sibim-dashboard-test").toString();
        assertFileProduced(service.exportDashboardPdf(buildResumen(), destFolder), ".pdf");
    }

    @Test void exportDashboardPdf_resumenVacio_createsFileWithoutError() throws Exception {
        assertFileProduced(service.exportDashboardPdf(buildResumenVacio(), null), ".pdf");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static DashboardService.Resumen buildResumen() {
        var stats = new ProductoRepository.ProductoStats(50, 40, 5, 3, 2, BigDecimal.valueOf(150000), 8);
        List<ProductoRepository.CategoriaValor> catValores =
            List.of(new ProductoRepository.CategoriaValor("Equipo de Cómputo", BigDecimal.valueOf(90000)));
        List<Producto> agotados  = List.of(buildProducto("p-ag", "Cartuchos de Tinta"));
        List<Producto> bajoStock = List.of(buildProducto("p-bs", "Papel Bond"));
        List<Movimiento> movHoy     = List.of(buildMovimiento());
        List<Movimiento> movSemana  = List.of(buildMovimiento());
        List<MovimientoRepository.MonthlyStats> movMensual =
            List.of(new MovimientoRepository.MonthlyStats("Sep", 10, 5));
        LinkedHashMap<String, Long> byArea = new LinkedHashMap<>();
        byArea.put("Sala de cómputo", 12L);
        byArea.put("Secretaria General Municipal", 8L);

        return new DashboardService.Resumen(stats, catValores, agotados, bajoStock, List.of(),
            movHoy, movSemana, movMensual, byArea, 30L, 25L);
    }

    private static DashboardService.Resumen buildResumenVacio() {
        var stats = new ProductoRepository.ProductoStats(0, 0, 0, 0, 0, BigDecimal.ZERO, 0);
        return new DashboardService.Resumen(stats, List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), new LinkedHashMap<>(), 0L, 0L);
    }

    private static Producto buildProducto(String id, String nombre) {
        Producto p = new Producto();
        p.setId(id); p.setNombre(nombre);
        return p;
    }

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
