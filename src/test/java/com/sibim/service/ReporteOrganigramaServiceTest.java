package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteOrganigramaService export methods.
 *
 * The no-arg constructor builds real repositories. ConfiguracionRepository#get only
 * catches SQLException, but a Postgres-unreachable environment fails earlier with an
 * unchecked HikariPool$PoolInitializationException — so demo mode must be enabled to
 * route orgName() to ConfiguracionRepository's in-memory demo values instead.
 */
class ReporteOrganigramaServiceTest {

    private ReporteOrganigramaService service;
    private Producto activo;
    private Producto agotado;
    private Producto bajoStock;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
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

        service = new ReporteOrganigramaService();
    }

    // ── exportOrganigrama(Map<String, List<Producto>>) ────────────────────────

    @Test void exportOrganigrama_conAreas_createsNonEmptyFile() throws Exception {
        Map<String, List<Producto>> porArea = new LinkedHashMap<>();
        porArea.put("Sala de cómputo", List.of(activo));
        porArea.put("Secretaria General Municipal", List.of(agotado, bajoStock));
        assertFileProduced(service.exportOrganigrama(porArea), ".pdf");
    }

    @Test void exportOrganigrama_mapaVacio_createsFile() throws Exception {
        assertFileProduced(service.exportOrganigrama(Map.of()), ".pdf");
    }

    @Test void exportOrganigrama_areaSinBienes_noLanzaExcepcion() throws Exception {
        Map<String, List<Producto>> porArea = new LinkedHashMap<>();
        porArea.put("Área sin bienes", List.of());
        porArea.put("Sala de cómputo", List.of(activo));
        assertFileProduced(service.exportOrganigrama(porArea), ".pdf");
    }

    // ── exportOrganigramaCsv(Map<String, List<Producto>>) ──────────────────────

    @Test void exportOrganigramaCsv_conAreas_createsNonEmptyFile() throws Exception {
        Map<String, List<Producto>> porArea = new LinkedHashMap<>();
        porArea.put("Sala de cómputo", List.of(activo));
        porArea.put("Secretaria General Municipal", List.of(agotado, bajoStock));
        assertFileProduced(service.exportOrganigramaCsv(porArea), ".csv");
    }

    @Test void exportOrganigramaCsv_mapaVacio_createsFile() throws Exception {
        assertFileProduced(service.exportOrganigramaCsv(Map.of()), ".csv");
    }

    @Test void exportOrganigramaCsv_areaSinBienes_noLanzaExcepcion() throws Exception {
        Map<String, List<Producto>> porArea = new LinkedHashMap<>();
        porArea.put("Área sin bienes", List.of());
        porArea.put("Sala de cómputo", List.of(activo));
        assertFileProduced(service.exportOrganigramaCsv(porArea), ".csv");
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
