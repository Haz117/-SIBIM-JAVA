package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FacturaXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: «Leer las facturas de una carpeta». 415 bienes with a UUID had no proveedor and
 *  the facturas are in PDF: each one fills what its bienes lack, matched by the folio fiscal. */
class FacturasDeCarpetaTest {

    private static final String U1 = "A0000000-0000-4000-8000-00000005BA08";
    private static final String U2 = "B0000000-0000-4000-8000-000000000002";

    private static Producto bien(String codigo, String folio, String proveedor, LocalDate fecha) {
        Producto p = new Producto();
        p.setId(codigo); p.setCodigo(codigo); p.setNombre("SILLA"); p.setFolioFiscal(folio); p.setProveedor(proveedor);
        p.setFechaAdquisicion(fecha); p.setPrecioCompra(new BigDecimal("100"));
        return p;
    }

    private static FacturasDeCarpeta.Leida leida(String archivo, String uuid, String emisor, String rfc, LocalDate fecha) {
        return new FacturasDeCarpeta.Leida(Path.of(archivo),
            new FacturaXml.Factura(uuid, null, fecha, emisor, rfc, "MXN", new BigDecimal("1160.00"), List.of()));
    }

    @Test
    void llenaSoloLoQueFalta_conElFolioCortoYElProveedorComoYaSeEscribe() {
        LocalDate dia = LocalDate.of(2025, 3, 14);
        List<Producto> inventario = List.of(
            bien("PRUE/01", U1, null, null),
            bien("PRUE/02", U1.toLowerCase(), "OTRO PROVEEDOR", dia.minusDays(3)),
            bien("PRUE/03", "5BA08", null, null),                      // only the end of U1
            bien("PRUE/04", U2, null, null),
            bien("PRUE/05", null, "Papelería Ficticia SA de CV", null)); // how the inventory writes it
        var lectura = new FacturasDeCarpeta.Lectura(List.of(
            leida("a.pdf", U1, "PAPELERIA FICTICIA SA DE CV", "PFI010101AB1", dia),
            leida("b.pdf", U2, null, "OTR010101AB1", null),
            leida("c.pdf", "C0000000-0000-4000-8000-000000000003", "NADIE", null, dia)), List.of());

        List<FacturasDeCarpeta.Propuesta> ps = FacturasDeCarpeta.proponer(lectura, inventario);

        assertEquals(2, ps.size(), "la factura sin bienes no sale");
        FacturasDeCarpeta.Propuesta a = ps.get(0);
        assertEquals("a.pdf", a.leida().archivo().toString());
        assertEquals(List.of("PRUE/01", "PRUE/02", "PRUE/03"), a.bienes().stream().map(Producto::getCodigo).toList());
        assertEquals("Papelería Ficticia SA de CV", a.proveedor(), "como ya está escrito en el inventario");
        assertEquals(2, a.sinProveedor());
        assertEquals(2, a.sinFecha());
        assertEquals(1, a.folioIncompleto());
        assertNull(ps.get(1).proveedor(), "no se leyó: se escribe en la revisión");

        var renglones = FacturasDeCarpeta.renglones(List.of(a, ps.get(1)), Map.of(ps.get(1), "  "));
        Map<String, Producto> nuevo = new java.util.HashMap<>();
        renglones.forEach(r -> nuevo.put(r.bien().getCodigo(), r.nuevo()));
        assertEquals("Papelería Ficticia SA de CV", nuevo.get("PRUE/01").getProveedor());
        assertEquals(dia, nuevo.get("PRUE/01").getFechaAdquisicion());
        assertFalse(nuevo.containsKey("PRUE/02"), "ya tiene proveedor (otro), fecha y folio: no se toca");
        assertEquals(U1, nuevo.get("PRUE/03").getFolioFiscal(), "el folio corto se completa");
        assertEquals("Papelería Ficticia SA de CV", nuevo.get("PRUE/03").getProveedor());
        assertFalse(nuevo.containsKey("PRUE/04"), "sin proveedor escrito ni fecha, nada que llenar");
    }

    @Test
    void elRfcQueRepitenCasiTodas_esElDelMunicipio() {
        List<String> textos = List.of("Emisor A RFC AAA010101AA1 Receptor MPR850101XY2", "BBB010101BB2 MPR850101XY2",
            "CCC010101CC3 MPR850101XY2", "DDD010101DD4 MPR850101XY2 PAC010101AA1");
        assertEquals(java.util.Set.of("MPR850101XY2"), FacturasDeCarpeta.repetidos(textos));
        assertTrue(FacturasDeCarpeta.repetidos(textos.subList(0, 3)).isEmpty(), "con menos de 4 no se sabe");
    }

    @Test
    void unaPorFolio_elXmlAntesQueSuPdf_yElNombreDeOtraDelMismoRfc() {
        var l = FacturasDeCarpeta.sinRepetir(List.of(
            leida("x.pdf", U2, null, "PFI010101AB1", null),
            leida("x.xml", U2, "DEL XML", "PFI010101AB1", null),
            leida("y.pdf", "C0000000-0000-4000-8000-000000000003", null, "PFI010101AB1", null)));
        assertEquals(2, l.size());
        assertEquals("x.xml", l.get(0).archivo().toString());
        assertEquals("DEL XML", l.get(1).factura().emisor(), "el nombre que dio otra factura de su RFC");
    }

    @Test
    void leeUnaCarpetaDeVerdad(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("factura.pdf");
        try (var doc = new com.itextpdf.layout.Document(new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfWriter(pdf.toFile())))) {
            doc.add(new com.itextpdf.layout.element.Paragraph("Nombre emisor: PAPELERIA FICTICIA SA DE CV"));
            doc.add(new com.itextpdf.layout.element.Paragraph("Folio fiscal: " + U2));
            doc.add(new com.itextpdf.layout.element.Paragraph("Total: $1,160.00"));
        }
        Files.writeString(dir.resolve("notas.pdf"), "no es un pdf");
        Files.writeString(dir.resolve("leeme.txt"), "se ignora");

        var lectura = FacturasDeCarpeta.leer(dir, "", "Prueba", x -> { });

        assertEquals(1, lectura.leidas().size());
        assertEquals("PAPELERIA FICTICIA SA DE CV", lectura.leidas().get(0).factura().emisor());
        assertEquals(1, lectura.noLeidas().size(), "el PDF dañado se dice, el .txt no cuenta");
    }
}
