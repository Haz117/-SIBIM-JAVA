package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Demo mode: orgName()/logo read in-memory configuration, no database. */
class ReporteDictamenBajaServiceTest {

    private final ReporteDictamenBajaService service = new ReporteDictamenBajaService();

    @BeforeAll static void demo()   { DatabaseConfig.setDemoMode(true); }
    @AfterAll  static void noDemo() { DatabaseConfig.setDemoMode(false); }

    @Test
    void unBien_unaPaginaConSusDatosYTodasLasSecciones() throws Exception {
        File pdf = service.exportDictamenBaja(List.of(bien("TICS/07", "Impresora láser")));
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            assertEquals(1, doc.getNumberOfPages(), "el dictamen debe caber en una hoja");
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1));
            for (String s : List.of("DICTAMEN TÉCNICO DE BAJA", "TICS/07", "Impresora láser", "HP",
                    "DIAGNÓSTICO TÉCNICO", "CAUSA DE LA BAJA", "PROCEDE LA BAJA", "NO PROCEDE LA BAJA",
                    "DESTINO FINAL RECOMENDADO", "Titular del área técnica")) {
                assertTrue(texto.contains(s), "falta «" + s + "»");
            }
        } finally {
            pdf.delete();
        }
    }

    @Test
    void bienYaDadoDeBaja_marcaSuDestinoYMuestraLaResolucion() throws Exception {
        Producto p = bien("TICS/09", "CPU");
        p.setTipoDestinoBaja("DONACION");
        p.setDictamenBaja("Se dona a la escuela primaria Benito Juárez.");
        File pdf = service.exportDictamenBaja(List.of(p));
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1));
            assertTrue(texto.contains("[ X ]  Donación"), texto);
            assertFalse(texto.contains("[ X ]  Destrucción"));
            assertTrue(texto.contains("Se dona a la escuela primaria Benito Juárez."));
        } finally {
            pdf.delete();
        }
    }

    @Test
    void variosBienes_unaPaginaPorBien() throws Exception {
        File pdf = service.exportDictamenBaja(List.of(bien("SGM/01", "Escritorio"), bien("SGM/02", "Silla")));
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            assertEquals(2, doc.getNumberOfPages());
            assertTrue(PdfTextExtractor.getTextFromPage(doc.getPage(2)).contains("SGM/02"));
        } finally {
            pdf.delete();
        }
    }

    @Test
    void enBlanco_unaPaginaSinDatosDeNingunBien() throws Exception {
        File pdf = service.exportDictamenBajaEnBlanco();
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            assertEquals(1, doc.getNumberOfPages());
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1));
            assertTrue(texto.contains("DICTAMEN TÉCNICO DE BAJA"));
            assertTrue(texto.contains("CÓDIGO DEL BIEN"));
            assertFalse(texto.contains("$0.00"), "un formato en blanco no debe mostrar valores");
        } finally {
            pdf.delete();
        }
    }

    @Test
    void sinBienes_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> service.exportDictamenBaja(List.of()));
    }

    private static Producto bien(String codigo, String nombre) {
        Producto p = new Producto();
        p.setId(codigo); p.setCodigo(codigo); p.setNombre(nombre);
        p.setMarca("HP");
        p.setArea("Dirección de Tecnologías de la Información");
        p.setPrecioCompra(new BigDecimal("4500.00"));
        return p;
    }
}
