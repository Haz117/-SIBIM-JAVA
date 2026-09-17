package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteResguardoService.exportarResguardoPdf.
 *
 * ReporteResguardoService has a public no-arg constructor (super() with no repos),
 * so no mocking is needed. Demo mode is enabled so orgName() (used in the PDF
 * header) reads from ConfiguracionRepository's in-memory demo values instead of a
 * real Postgres connection, which isn't available in this test environment. The
 * export method never null-checks bienes for empty/size and treats
 * resguardante/area as optional (rendered as "—" when null).
 *
 * Every test asserts the returned File exists on disk and is non-empty.
 */
class ReporteResguardoServiceTest {

    private ReporteResguardoService service;
    private Producto activo;
    private Producto sinExtras;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        activo = new Producto();
        activo.setId("p-01"); activo.setNombre("Laptop HP Elite"); activo.setCodigo("INF-001");
        activo.setDescripcion("15 pulgadas, 16GB RAM"); activo.setNumeroSerie("SN-12345");
        activo.setArea("Sala de cómputo"); activo.setUbicacion("Edificio A, Piso 2");
        activo.setResguardante("Juan Pérez");
        activo.setPrecioCompra(BigDecimal.valueOf(15000)); activo.setPrecioVenta(BigDecimal.valueOf(18000));
        activo.setStockActual(5); activo.setStockMinimo(2); activo.setStockMaximo(10);

        sinExtras = new Producto();
        sinExtras.setId("p-02"); sinExtras.setNombre("Silla Ejecutiva"); sinExtras.setCodigo("MOB-001");
        sinExtras.setStockActual(1); sinExtras.setStockMinimo(1); sinExtras.setStockMaximo(5);

        service = new ReporteResguardoService();
    }

    @Test void exportarResguardoPdf_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportarResguardoPdf("Juan Pérez", "Sala de cómputo", List.of(activo, sinExtras)), ".pdf");
    }

    @Test void exportarResguardoPdf_emptyBienes_createsFile() throws Exception {
        assertFileProduced(service.exportarResguardoPdf("Juan Pérez", "Sala de cómputo", List.of()), ".pdf");
    }

    @Test void exportarResguardoPdf_nullResguardanteYArea_createsFile() throws Exception {
        assertFileProduced(service.exportarResguardoPdf(null, null, List.of(activo)), ".pdf");
    }

    private static void assertFileProduced(File file, String expectedExtension) {
        assertNotNull(file);
        assertTrue(file.exists(), "El archivo debe existir: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(expectedExtension),
            "Se esperaba extensión " + expectedExtension + " pero fue: " + file.getName());
    }
}
