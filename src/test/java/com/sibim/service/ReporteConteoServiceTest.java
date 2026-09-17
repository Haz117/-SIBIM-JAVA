package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteConteoService export methods.
 *
 * ReporteConteoService() delegates to ReporteService()'s no-arg constructor, which builds
 * real repositories; none of the three export methods here call productoRepo/movimientoRepo
 * directly, but orgName() does go through ConfiguracionRepository.get(), which only catches
 * SQLException — a Postgres-unreachable environment fails earlier with an unchecked
 * HikariPool$PoolInitializationException. Demo mode is enabled so orgName() reads from
 * ConfiguracionRepository's in-memory demo values instead.
 *
 * Every test asserts the returned File exists on disk and is non-empty.
 */
class ReporteConteoServiceTest {

    private ReporteConteoService service;
    private ConteoFisico conteo;
    private ConteoItem item;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        service = new ReporteConteoService();

        item = new ConteoItem();
        item.setId("ci-01"); item.setConteoId("c-01");
        item.setProductoId("p-01"); item.setProductoNombre("Laptop HP Elite"); item.setProductoCodigo("INF-001");
        item.setArea("Sala de cómputo");
        item.setStockSistema(5); item.setStockContado(3);
        item.setAjustado(false); item.setEstadoConteo("FALTANTE"); item.setNota("Revisar con resguardante");

        conteo = new ConteoFisico();
        conteo.setId("c-01"); conteo.setUsuarioId("u-01"); conteo.setUsuarioNombre("Juan Pérez");
        conteo.setTotalContados(10); conteo.setTotalDiscrepancias(1);
        conteo.setCreadoEn(LocalDateTime.now());
        conteo.setItems(List.of(item));
    }

    // ── exportarConteoPdf(ConteoFisico, List<ConteoItem>) ─────────────────────

    @Test void exportarConteoPdf_withItems_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportarConteoPdf(conteo, List.of(item)), ".pdf");
    }

    @Test void exportarConteoPdf_emptyItems_createsFile() throws Exception {
        assertFileProduced(service.exportarConteoPdf(conteo, List.of()), ".pdf");
    }

    @Test void exportarConteoPdf_sinDiscrepancias_createsFile() throws Exception {
        conteo.setTotalDiscrepancias(0);
        assertFileProduced(service.exportarConteoPdf(conteo, List.of()), ".pdf");
    }

    // ── exportConteoPdf(String, String, List<ConteoItem>) ─────────────────────

    @Test void exportConteoPdf_withItems_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportConteoPdf("Conteo Septiembre", "Admin Test", List.of(item)), ".pdf");
    }

    @Test void exportConteoPdf_emptyItems_createsFile() throws Exception {
        assertFileProduced(service.exportConteoPdf("Conteo Septiembre", "Admin Test", List.of()), ".pdf");
    }

    // ── exportConteoExcel(String, String, List<ConteoItem>) ───────────────────

    @Test void exportConteoExcel_withItems_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportConteoExcel("Conteo Septiembre", "Admin Test", List.of(item)), ".xlsx");
    }

    @Test void exportConteoExcel_emptyItems_createsFile() throws Exception {
        assertFileProduced(service.exportConteoExcel("Conteo Septiembre", "Admin Test", List.of()), ".xlsx");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void assertFileProduced(File file, String expectedExtension) {
        assertNotNull(file);
        assertTrue(file.exists(), "El archivo debe existir: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(expectedExtension),
            "Se esperaba extensión " + expectedExtension + " pero fue: " + file.getName());
    }
}
