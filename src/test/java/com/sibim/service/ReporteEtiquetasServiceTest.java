package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteEtiquetasService's QR and official physical labels.
 *
 * ReporteEtiquetasService has a public no-arg constructor and never touches
 * the product/movimiento repos for this export, so no mocking is needed. Demo
 * mode is enabled so orgName() (used in the PDF header) reads from
 * ConfiguracionRepository's in-memory demo values instead of a real Postgres
 * connection, which isn't available in this test environment.
 */
class ReporteEtiquetasServiceTest {

    private ReporteEtiquetasService service;
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
        activo.setArea("Sala de cómputo");

        agotado = new Producto();
        agotado.setId("p-ag"); agotado.setNombre("Cartuchos de Tinta"); agotado.setCodigo("INF-002");
        agotado.setArea("Secretaria General Municipal");

        bajoStock = new Producto();
        bajoStock.setId("p-bs"); bajoStock.setNombre("Papel Bond"); bajoStock.setCodigo("PAP-001");
        bajoStock.setArea("Secretaria General Municipal");

        service = new ReporteEtiquetasService();
    }

    // ── exportEtiquetasQrPdf(List<Producto>) ──────────────────────────────────

    @Test void exportEtiquetasQrPdf_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportEtiquetasQrPdf(List.of(activo, agotado, bajoStock)), ".pdf");
    }

    @Test void exportEtiquetasQrPdf_emptyList_returnsNull() throws Exception {
        assertNull(service.exportEtiquetasQrPdf(List.of()));
    }

    @Test void exportEtiquetasQrPdf_faltaCodigoYArea_noArrojaExcepcion() throws Exception {
        Producto sinCodigo = new Producto();
        sinCodigo.setId("p-sc"); sinCodigo.setNombre("Silla Ejecutiva");
        // codigo null → QR cae al nombre; area null → se omite la línea de área

        assertFileProduced(service.exportEtiquetasQrPdf(List.of(sinCodigo, activo)), ".pdf");
    }

    @Test void exportEtiquetasQrPdf_sinCodigoNiNombre_noArrojaExcepcion() throws Exception {
        Producto vacio = new Producto();
        vacio.setId("p-vacio");
        // codigo y nombre null → contenido de QR es null; qrToPngBytes lo captura y
        // devuelve null; el bloque de imagen se omite y las etiquetas caen en "—"

        assertFileProduced(service.exportEtiquetasQrPdf(List.of(vacio)), ".pdf");
    }

    @Test void exportEtiquetasQrPdf_areaEnBlanco_seOmite() throws Exception {
        Producto areaBlanco = new Producto();
        areaBlanco.setId("p-ab"); areaBlanco.setNombre("Escritorio"); areaBlanco.setCodigo("MOB-001");
        areaBlanco.setArea("   ");

        assertFileProduced(service.exportEtiquetasQrPdf(List.of(areaBlanco)), ".pdf");
    }

    // ── exportEtiquetaFisicaPdf(List<Producto>) — formato oficial ─────────────

    @Test void exportEtiquetaFisicaPdf_withData_createsNonEmptyFile() throws Exception {
        Producto completo = new Producto();
        completo.setId("p-cm"); completo.setNombre("Cinta métrica de fibra de vidrio 50 mts.");
        completo.setCodigo("SF-DC-2561"); completo.setArea("Dirección de Catastro");
        completo.setMarca("Truper"); completo.setModelo("TFV-50"); completo.setNumeroSerie("A1B2C3");
        Producto largo = new Producto();
        largo.setId("p-lg"); largo.setCodigo("SGM-MM-3261-BIS-2024");
        largo.setNombre("Archivero metálico ".repeat(20));   // longer than the label holds
        Producto vacio = new Producto();
        vacio.setId("p-vc");

        assertFileProduced(service.exportEtiquetaFisicaPdf(List.of(completo, largo, vacio)), ".pdf");
    }

    @Test void exportEtiquetaFisicaPdf_emptyList_returnsNull() throws Exception {
        assertNull(service.exportEtiquetaFisicaPdf(List.of()));
    }

    @Test void departamentoEsLaSecretariaYResguardoLaDireccion() {
        assertArrayEquals(new String[]{"TESORERÍA MUNICIPAL", "DIRECCIÓN DE CATASTRO"},
            ReporteEtiquetasService.departamentoYResguardo("Dirección de Catastro"));
    }

    @Test void areaSinPadreVaEnAmbos() {
        assertArrayEquals(new String[]{"TESORERÍA MUNICIPAL", "TESORERÍA MUNICIPAL"},
            ReporteEtiquetasService.departamentoYResguardo("Tesorería Municipal"));
        assertArrayEquals(new String[]{"SIN ÁREA", "SIN ÁREA"},
            ReporteEtiquetasService.departamentoYResguardo("  "));
    }

    @Test void municipioComoEnElFormatoImpreso() {
        assertEquals("IXMIQUILPAN, HGO.", ReporteEtiquetasService.municipioEtiqueta("Ixmiquilpan, Hidalgo"));
        assertEquals("PACHUCA", ReporteEtiquetasService.municipioEtiqueta("Pachuca"));
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
