package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
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
 * Tests for ReporteParqueVehicularService (formato oficial V.6).
 *
 * Demo mode is enabled so orgName()/logo lookups in the PDF header use the
 * in-memory demo values instead of a real Postgres connection. The PDFs are
 * re-read with iText to assert on real content (page count and text), not just
 * that a file was written.
 */
class ReporteParqueVehicularServiceTest {

    private ReporteParqueVehicularService service;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        service = new ReporteParqueVehicularService();
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Producto vehiculoCompleto(String codigo) {
        Producto p = new Producto();
        p.setId("veh-" + codigo);
        p.setNombre("Camioneta Pick-up");
        p.setCodigo(codigo);
        p.setMarca("Ford");
        p.setModelo("Ranger 2021");
        p.setTipoBien("Camioneta");
        p.setNumeroSerie("3FTTW8E30MRA00001");
        p.setNoMotor("MTR-556677");
        p.setColor("Blanco");
        p.setNoTarjetaCirculacion("TC-998877");
        p.setNoPolizaSeguro("POL-2026-1234");
        p.setClaveArmonizada("1.2.4.4.541.1");
        p.setNumeroFactura("FAC-2021-0099");
        p.setEstadoFisico("BUENO");
        p.setArea("Seguridad Pública");
        return p;
    }

    private static Producto vehiculoVacio() {
        Producto p = new Producto();
        p.setId("veh-vacio");
        p.setNombre("Vehículo sin datos");
        p.setCodigo("VEH-000");
        return p;
    }

    private static String textoDePagina(File pdf, int pagina) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            return PdfTextExtractor.getTextFromPage(doc.getPage(pagina));
        }
    }

    private static int paginas(File pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            return doc.getNumberOfPages();
        }
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void exportParqueVehicularPdf_listaVacia_retornaNull() throws Exception {
        assertNull(service.exportParqueVehicularPdf(List.of()));
    }

    @Test
    void exportParqueVehicularPdf_unVehiculo_creaPdfValidoDeUnaPagina() throws Exception {
        File pdf = service.exportParqueVehicularPdf(List.of(vehiculoCompleto("VEH-001")));

        assertNotNull(pdf);
        assertTrue(pdf.exists(), "El archivo debe existir");
        assertTrue(pdf.length() > 0, "El archivo no debe estar vacío");
        assertTrue(pdf.getName().endsWith(".pdf"), "Debe tener extensión .pdf");
        assertEquals(1, paginas(pdf), "Un vehículo = una tarjeta = una página");
    }

    @Test
    void exportParqueVehicularPdf_contieneEncabezadoDatosYComponentes() throws Exception {
        File pdf = service.exportParqueVehicularPdf(List.of(vehiculoCompleto("VEH-001")));

        String texto = textoDePagina(pdf, 1);
        assertTrue(texto.contains("INVENTARIO DE PARQUE VEHICULAR"), "Encabezado del formato V.6");
        assertTrue(texto.contains("VEH-001"), "Número de inventario");
        assertTrue(texto.contains("Ford"), "Marca");
        assertTrue(texto.contains("MTR-556677"), "Número de motor");
        assertTrue(texto.contains("POL-2026-1234"), "Póliza de seguro");
        assertTrue(texto.contains("Gato Hidr"), "Checklist de componentes");
        assertTrue(texto.contains("ELABOR"), "Bloque de firmas");
    }

    @Test
    void exportParqueVehicularPdf_variosVehiculos_unaPaginaPorVehiculo() throws Exception {
        File pdf = service.exportParqueVehicularPdf(List.of(
            vehiculoCompleto("VEH-001"), vehiculoCompleto("VEH-002"), vehiculoCompleto("VEH-003")));

        assertEquals(3, paginas(pdf));
        assertTrue(textoDePagina(pdf, 2).contains("VEH-002"), "La página 2 corresponde al segundo vehículo");
        assertTrue(textoDePagina(pdf, 3).contains("VEH-003"), "La página 3 corresponde al tercer vehículo");
    }

    @Test
    void exportParqueVehicularPdf_camposNulos_usaLineasEnBlancoSinLanzar() throws Exception {
        File pdf = service.exportParqueVehicularPdf(List.of(vehiculoVacio()));

        assertNotNull(pdf);
        assertTrue(pdf.length() > 0);
        assertTrue(textoDePagina(pdf, 1).contains("_______________"),
            "Los campos sin dato se imprimen como línea para llenar a mano");
    }

    @Test
    void exportParqueVehicularPdf_fotoInexistente_cae_alRecuadroVacio() throws Exception {
        Producto p = vehiculoCompleto("VEH-004");
        p.setFotoUrl("C:/ruta/que/no/existe/foto.png");

        File pdf = assertDoesNotThrowReturning(() -> service.exportParqueVehicularPdf(List.of(p)));

        assertTrue(textoDePagina(pdf, 1).contains("FOTOGRAF"),
            "Con foto ilegible se muestra el recuadro de fotografía en blanco");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private interface ThrowingSupplier<T> { T get() throws Exception; }

    private static <T> T assertDoesNotThrowReturning(ThrowingSupplier<T> s) {
        try {
            return s.get();
        } catch (Exception e) {
            fail("No debía lanzar excepción: " + e);
            return null;
        }
    }
}
