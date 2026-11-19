package com.sibim.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: Patrimonio has the facturas in PDF, not XML. What every printed CFDI carries
 *  (folio fiscal, fecha, total) is read for sure; the emisor when the PDF says it clearly. */
class FacturaPdfTest {

    private static final String UUID = "a0000000-0000-4000-8000-0000000000c1";
    private static final List<String> PROPIOS = List.of("PRUEBA");

    @Test
    void unaColumna_conSusEtiquetas() throws Exception {
        String texto = """
            FACTURA A 667
            Nombre emisor: PAPELERIA FICTICIA SA DE CV
            RFC emisor: PFI010101AB1
            Nombre receptor: MUNICIPIO DE PRUEBA
            RFC receptor: MPR850101XY2
            Folio fiscal: A0000000-0000-4000-8000-0000000000C1
            Fecha de emisión: 2025-03-14T11:20:05
            Subtotal $1,000.00
            IVA 16% $160.00
            Total $1,160.00
            """;
        FacturaXml.Factura f = FacturaPdf.leerTexto(texto, List.of("MPR850101XY2"), PROPIOS);
        assertEquals(UUID.toUpperCase(), f.uuid());
        assertEquals("PAPELERIA FICTICIA SA DE CV", f.emisor());
        assertEquals("PFI010101AB1", f.rfcEmisor());
        assertEquals(LocalDate.of(2025, 3, 14), f.fecha());
        assertEquals(0, new BigDecimal("1160.00").compareTo(f.total()), "el total, no el subtotal");
        assertTrue(f.enPesos());
        assertTrue(f.conceptos().isEmpty(), "los conceptos del PDF no se leen");
    }

    /** Emisor and receptor side by side: labels on one line, both names on the next, both RFCs after. */
    @Test
    void dosColumnas_elReceptorSeQuita() throws Exception {
        String texto = """
            Emisor: Receptor:
            COMERCIALIZADORA FICTICIA SA DE CV MUNICIPIO DE PRUEBA
            PFI010101AB1 MPR850101XY2
            Folio Fiscal: Fecha / Hora de Emisión:
            A0000000-0000-4000-8000-0000000000C1 2025-03-14T11:20:05
            Total: $2,320.00
            Cadena Original del complemento de Certificación Digital del SAT
            ||1.1|a0000000-0000-4000-8000-0000000000c1|2025-03-14T11:21:00|PAC010101AA1|abc==|0001||
            """;
        FacturaXml.Factura f = FacturaPdf.leerTexto(texto, List.of(), PROPIOS);
        assertEquals("COMERCIALIZADORA FICTICIA SA DE CV", f.emisor());
        assertEquals("PFI010101AB1", f.rfcEmisor(), "el primero de los dos, y no el del PAC");
        assertEquals(LocalDate.of(2025, 3, 14), f.fecha());
        assertEquals(0, new BigDecimal("2320.00").compareTo(f.total()));
    }

    @Test
    void soloLaCadenaDelSat_daFolioYFecha_sinNombreInventado() throws Exception {
        String texto = "Representación impresa de un CFDI\n||1.1|" + UUID + "|2025-01-02T09:00:00|PAC010101AA1|sello==|0001||\n";
        FacturaXml.Factura f = FacturaPdf.leerTexto(texto, List.of(), PROPIOS);
        assertEquals(UUID.toUpperCase(), f.uuid());
        assertEquals(LocalDate.of(2025, 1, 2), f.fecha());
        assertNull(f.emisor());
        assertNull(f.rfcEmisor(), "el RFC del PAC no es el del emisor");
    }

    @Test
    void unOficioQueMencionaElFolio_oUnEscaneo_noSonFacturas() {
        assertThrows(FacturaXml.NoEsFactura.class,
            () -> FacturaPdf.leerTexto("Oficio: se pagó la factura con folio fiscal " + UUID + ".", List.of(), PROPIOS));
        assertThrows(FacturaXml.NoEsFactura.class, () -> FacturaPdf.leerTexto("  \n ", List.of(), PROPIOS));
        assertThrows(FacturaXml.NoEsFactura.class, () -> FacturaPdf.leerTexto("Total: $10.00 sin folio", List.of(), PROPIOS));
    }

    @Test
    void elXmlImpresoEnPdf_seLeeDeSusAtributos() throws Exception {
        String texto = "<?xml version=\"1.0\"?><cfdi:Comprobante Version=\"4.0\" Fecha=\"2025-02-03T10:00:00\" Total=\"580.00\"\n"
            + "Moneda=\"MXN\"><cfdi:Emisor Rfc=\"PFI010101AB1\" Nombre=\"FERRETERIA FICTICIA\" RegimenFiscal=\"612\"/>\n"
            + "<tfd:TimbreFiscalDigital UUID=\"" + UUID + "\"/></cfdi:Comprobante>";
        FacturaXml.Factura f = FacturaPdf.leerTexto(texto, List.of(), PROPIOS);
        assertEquals("FERRETERIA FICTICIA", f.emisor());
        assertEquals("PFI010101AB1", f.rfcEmisor());
        assertEquals(LocalDate.of(2025, 2, 3), f.fecha());
        assertEquals(0, new BigDecimal("580.00").compareTo(f.total()));
    }

    /** A real PDF, written with the same library the reports use. */
    @Test
    void unPdfDeVerdad(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("factura.pdf");
        try (var doc = new com.itextpdf.layout.Document(new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfWriter(pdf.toFile())))) {
            doc.add(new com.itextpdf.layout.element.Paragraph("Nombre emisor: PAPELERIA FICTICIA SA DE CV"));
            doc.add(new com.itextpdf.layout.element.Paragraph("RFC emisor: PFI010101AB1"));
            doc.add(new com.itextpdf.layout.element.Paragraph("Folio fiscal: " + UUID));
            doc.add(new com.itextpdf.layout.element.Paragraph("Fecha de emision: 14/03/2025"));
            doc.add(new com.itextpdf.layout.element.Paragraph("Total: $1,160.00"));
        }
        FacturaXml.Factura f = FacturaPdf.leer(pdf, List.of(), PROPIOS);
        assertEquals(UUID.toUpperCase(), f.uuid());
        assertEquals("PAPELERIA FICTICIA SA DE CV", f.emisor());
        assertEquals(LocalDate.of(2025, 3, 14), f.fecha());
        assertEquals(0, new BigDecimal("1160.00").compareTo(f.total()));
    }
}
