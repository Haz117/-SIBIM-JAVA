package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Demo mode: orgName()/logo read in-memory configuration, no database. */
class ReporteSolicitudBajaServiceTest {

    private final ReporteSolicitudBajaService service = new ReporteSolicitudBajaService();

    @BeforeAll static void demo()   { DatabaseConfig.setDemoMode(true); }
    @AfterAll  static void noDemo() { DatabaseConfig.setDemoMode(false); }

    @Test
    void unBienSinFoto_unaPaginaConSusDatosYEspacioParaLaFoto() throws Exception {
        File pdf = service.exportSolicitudBaja(List.of(bien("TICS/07", "Impresora láser", null)));
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            assertEquals(1, doc.getNumberOfPages());
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1));
            assertTrue(texto.contains("SOLICITUD DE BAJA DE BIEN PATRIMONIAL"));
            assertTrue(texto.contains("TICS/07"));
            assertTrue(texto.contains("Impresora láser"));
            assertTrue(texto.contains("Sin fotografía registrada"));
            assertTrue(texto.contains("MOTIVO DE LA SOLICITUD"));
        } finally {
            pdf.delete();
        }
    }

    @Test
    void unBienConFoto_laIncluye(@TempDir Path dir) throws Exception {
        File foto = dir.resolve("foto.png").toFile();
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", foto);

        File pdf = service.exportSolicitudBaja(List.of(bien("TICS/08", "Monitor", foto.getAbsolutePath())));
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1));
            assertFalse(texto.contains("Sin fotografía registrada"));
            var xobjects = doc.getPage(1).getResources().getResource(com.itextpdf.kernel.pdf.PdfName.XObject);
            boolean hayFoto = xobjects != null && xobjects.keySet().stream()
                .anyMatch(k -> xobjects.getAsStream(k) != null
                    && com.itextpdf.kernel.pdf.PdfName.Image.equals(xobjects.getAsStream(k).getAsName(com.itextpdf.kernel.pdf.PdfName.Subtype))
                    && new PdfImageXObject(xobjects.getAsStream(k)).getWidth() == 40);
            assertTrue(hayFoto, "la foto del bien debe estar en la página");
        } finally {
            pdf.delete();
        }
    }

    @Test
    void variosBienes_unaPaginaPorBien() throws Exception {
        File pdf = service.exportSolicitudBaja(List.of(
            bien("SGM/01", "Escritorio", null), bien("SGM/02", "Silla", null), bien("SGM/03", "Archivero", null)));
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            assertEquals(3, doc.getNumberOfPages());
            assertTrue(PdfTextExtractor.getTextFromPage(doc.getPage(3)).contains("SGM/03"));
        } finally {
            pdf.delete();
        }
    }

    @Test
    void sinBienes_seRechaza() {
        assertThrows(IllegalArgumentException.class, () -> service.exportSolicitudBaja(List.of()));
    }

    private static Producto bien(String codigo, String nombre, String foto) {
        Producto p = new Producto();
        p.setId(codigo); p.setCodigo(codigo); p.setNombre(nombre);
        p.setArea("Dirección de Tecnologías de la Información");
        p.setPrecioCompra(new BigDecimal("4500.00"));
        p.setFotoUrl(foto);
        return p;
    }
}
