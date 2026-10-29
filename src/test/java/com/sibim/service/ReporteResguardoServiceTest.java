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

    /** Bienes › selection › Resguardo PDF with bienes of two people: each signs for
     *  their own (everything went under the first one's name). */
    @Test void seleccionDeVariasPersonas_unResguardoPorCadaUna() throws Exception {
        Producto deJuan = new Producto();
        deJuan.setId("p-03"); deJuan.setNombre("Escritorio"); deJuan.setCodigo("MOB-002");
        deJuan.setResguardante("JUAN PEREZ"); deJuan.setStockActual(1);
        Producto deAna = new Producto();
        deAna.setId("p-04"); deAna.setNombre("Impresora"); deAna.setCodigo("INF-002");
        deAna.setResguardante("Ana Ruiz"); deAna.setArea("Contraloría"); deAna.setStockActual(1);

        File pdf = ReporteService.getInstance().exportarResguardoPorResguardante(List.of(activo, deAna, deJuan, sinExtras));
        List<String> paginas = new java.util.ArrayList<>();
        try (var doc = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            for (int i = 1; i <= doc.getNumberOfPages(); i++)
                paginas.add(com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(i)));
        }
        String todo = String.join("\n", paginas);
        assertTrue(paginas.size() >= 3, "Juan (con JUAN PEREZ), Ana y los sin resguardante: " + paginas.size());
        String deJuanPdf = paginas.stream().filter(t -> t.contains("INF-001")).findFirst().orElseThrow();
        assertTrue(deJuanPdf.contains("MOB-002"), "«JUAN PEREZ» es Juan Pérez: un solo resguardo");
        assertFalse(deJuanPdf.contains("INF-002"), "la impresora de Ana no va en el de Juan");
        assertTrue(todo.toUpperCase().contains("SIN RESGUARDANTE"));
    }

    /** Printed from a selection in Bienes without registering a resguardo: its own
     *  folio series, so the registered resguardos (RSG) keep a numbering without gaps. */
    @Test void resguardoSinRegistrar_noTomaFolioDeLosRegistrados() throws Exception {
        File pdf = service.exportarResguardoPdf("Juan Pérez", "Sala de cómputo", List.of(activo));
        String texto;
        try (var doc = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            texto = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(1));
        }
        assertTrue(texto.contains("RSP-"), "folio de la serie RSP");
        assertFalse(texto.contains("RSG-"), "no consume un folio RSG");
    }

    /** The área's GENERAL resguardo: titled as such, its own folio series, and for each
     *  bien who holds it individually — or that nobody does yet. */
    @Test void resguardoGeneralArea_listaElResguardanteDeCadaBienYSuFolioPropio() throws Exception {
        File pdf = service.exportarResguardoGeneralArea("Sala de cómputo", "Ing. Ana Ruiz", "Titular del área",
            List.of(activo, sinExtras));
        assertFileProduced(pdf, ".pdf");
        String texto;
        try (var doc = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            texto = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(1))
                .replaceAll("\\s+", " ");   // a cell wraps its text over several lines
        }
        assertTrue(texto.contains("RESGUARDO GENERAL DEL"), "Título del resguardo general");
        assertTrue(texto.contains("RGA-"), "Folio de la serie RGA, no de los resguardos registrados");
        assertTrue(texto.contains("RESGUARDANTE"), "Columna del resguardante individual");
        assertTrue(texto.contains("JUAN PÉREZ") || texto.contains("JUAN PEREZ"), "Resguardante del bien");
        assertTrue(texto.contains("SIN RESGUARDO INDIVIDUAL"), "Bien sin resguardante");
        assertTrue(texto.toUpperCase().contains("ANA RUIZ"), "Titular que firma");
    }

    private static void assertFileProduced(File file, String expectedExtension) {
        assertNotNull(file);
        assertTrue(file.exists(), "El archivo debe existir: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(expectedExtension),
            "Se esperaba extensión " + expectedExtension + " pero fue: " + file.getName());
    }
}
