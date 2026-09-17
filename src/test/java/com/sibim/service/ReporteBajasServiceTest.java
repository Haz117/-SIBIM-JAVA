package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteBajasService export methods.
 *
 * ReporteBajasService has a public no-arg constructor (calls ReporteService's
 * default constructor) and never touches the repositories, so no mocking is needed.
 * Demo mode is enabled so orgName() (used in PDF/Excel headers) reads from
 * ConfiguracionRepository's in-memory demo values instead of a real Postgres
 * connection, which isn't available in this test environment.
 *
 * Every export method returns null when the incoming list is empty; otherwise it
 * produces a non-empty file with the expected extension.
 */
class ReporteBajasServiceTest {

    private ReporteBajasService service;
    private Producto bajaCompleta;
    private Producto bajaSinDatosOpcionales;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        bajaCompleta = new Producto();
        bajaCompleta.setId("p-01"); bajaCompleta.setNombre("Laptop HP Elite"); bajaCompleta.setCodigo("INF-001");
        bajaCompleta.setArea("Sala de cómputo"); bajaCompleta.setCategoriaNombre("Equipo de Cómputo");
        bajaCompleta.setResguardante("Juan Pérez");
        bajaCompleta.setPrecioCompra(BigDecimal.valueOf(15000));
        bajaCompleta.setFechaBaja(LocalDate.of(2025, 3, 10));
        bajaCompleta.setMotivoBaja("Obsolescencia técnica");

        bajaSinDatosOpcionales = new Producto();
        bajaSinDatosOpcionales.setId("p-02"); bajaSinDatosOpcionales.setNombre("Silla de oficina");
        bajaSinDatosOpcionales.setCodigo("MOB-002");

        service = new ReporteBajasService();
    }

    // ── exportBajasPdf(List<Producto>) ────────────────────────────────────────

    @Test void exportBajasPdf_emptyList_returnsNull() throws Exception {
        assertNull(service.exportBajasPdf(List.of()));
    }

    @Test void exportBajasPdf_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportBajasPdf(List.of(bajaCompleta, bajaSinDatosOpcionales)), ".pdf");
    }

    // ── exportBajasExcel(List<Producto>) ──────────────────────────────────────

    @Test void exportBajasExcel_emptyList_returnsNull() throws Exception {
        assertNull(service.exportBajasExcel(List.of()));
    }

    @Test void exportBajasExcel_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportBajasExcel(List.of(bajaCompleta, bajaSinDatosOpcionales)), ".xlsx");
    }

    // ── exportBajasCsv(List<Producto>) ────────────────────────────────────────

    @Test void exportBajasCsv_emptyList_returnsNull() throws Exception {
        assertNull(service.exportBajasCsv(List.of()));
    }

    @Test void exportBajasCsv_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportBajasCsv(List.of(bajaCompleta, bajaSinDatosOpcionales)), ".csv");
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
