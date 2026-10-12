package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.util.QrUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FichaPublicaTest {

    @BeforeEach
    void demo() { DatabaseConfig.setDemoMode(true); }

    private static Producto bien() {
        Producto p = new Producto();
        p.setId("b-1");
        p.setCodigo("SGM/07");
        p.setNombre("Computadora de escritorio");
        p.setArea("Secretaría General Municipal");
        p.setResguardante("Ana Pérez");
        p.setPrecioCompra(new BigDecimal("18500.00"));
        p.setPrecioVenta(new BigDecimal("18500.00"));
        p.setStockActual(1);
        p.setFechaAdquisicion(LocalDate.of(2023, 3, 15));
        return p;
    }

    /** No Storage in tests (as on a PC without the public key): the QR falls
     *  back to the data, for one bien and for a sheet of labels. */
    @Test
    void sinAlmacenamiento_elQrLlevaLosDatos() {
        Producto p = bien();
        assertEquals(QrUtils.contenidoBien(p), FichaPublica.publicar(p));
        assertEquals(QrUtils.contenidoBien(p), FichaPublica.contenidoQr(p));
        assertEquals(QrUtils.contenidoBien(p), FichaPublica.publicar(List.of(p)).get("b-1"));
    }

    /** Armamento, the C-2, patrol cars: the bienes of Seguridad Pública and of
     *  its direcciones never get a ficha that anyone with the link can open. */
    @Test
    void bienesDeSeguridadPublica_nuncaSePublican() {
        String ssp = "Secretaría de Seguridad Pública y Tránsito";
        var conSeguridad = new java.util.ArrayList<>(com.sibim.config.AreaCatalog.PREDETERMINADO.entradas());
        conSeguridad.add(new com.sibim.config.AreaCatalog.Entrada(ssp, com.sibim.config.AreaCatalog.Grupo.SECRETARIA, null, "SSP"));
        conSeguridad.add(new com.sibim.config.AreaCatalog.Entrada("Seguridad Pública — Armamento",
            com.sibim.config.AreaCatalog.Grupo.DIRECCION, ssp, "SSPAR"));
        com.sibim.config.Areas.usar(new com.sibim.config.AreaCatalog(conSeguridad));
        try {
            Producto arma = bien();
            arma.setArea("Seguridad Pública — Armamento");
            Producto patrulla = bien();
            patrulla.setArea(ssp);

            assertTrue(FichaPublica.reservado(arma), "una dirección de Seguridad Pública");
            assertTrue(FichaPublica.reservado(patrulla), "la secretaría misma");
            assertFalse(FichaPublica.reservado(bien()), "las demás áreas siguen publicando su ficha");
            assertEquals(QrUtils.contenidoBien(arma), FichaPublica.publicar(arma));
            assertEquals(QrUtils.contenidoBien(arma), FichaPublica.contenidoQr(arma));
        } finally {
            com.sibim.config.Areas.usar(com.sibim.config.AreaCatalog.PREDETERMINADO);
        }
    }

    /** Anyone who scans the label can open it: no amounts, no signatures. */
    @Test
    void laFichaQueSeDescarga_noLlevaMontos() throws Exception {
        Producto p = bien();
        String publica = texto(new ReporteFichaTecnicaService().exportFichaPublica(p, "https://x/fichas/b-1.pdf"))
            .toUpperCase();
        assertTrue(publica.contains("SGM/07") && publica.contains("ANA PÉREZ"), publica);
        assertFalse(publica.contains("18,500") || publica.contains("COSTO DE ADQUISICI"), publica);
        assertFalse(publica.contains("VALOR EN LIBROS"), publica);

        String interna = texto(new ReporteFichaTecnicaService().exportFichaTecnica(p, List.of())).toUpperCase();
        assertTrue(interna.contains("COSTO DE ADQUISICI") && interna.contains("18,500"),
            "la ficha interna sí conserva los montos: " + interna);
    }

    private static String texto(File pdf) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= doc.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(doc.getPage(i)));
            return sb.toString();
        } finally {
            pdf.delete();
        }
    }
}
