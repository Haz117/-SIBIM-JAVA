package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The formats of a bien that carry its photo: inventario fotográfico (MLA) and ficha técnica. */
class ReporteInventarioFotograficoServiceTest {

    @TempDir Path dir;

    @BeforeAll static void demo() { DatabaseConfig.setDemoMode(true); }
    @AfterAll  static void real() { DatabaseConfig.setDemoMode(false); }

    private static Producto bien(String id, String codigo, String area, String foto) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Archivero metálico " + codigo); p.setArea(area);
        p.setFotoUrl(foto);
        return p;
    }

    private static String texto(File pdf) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PdfDocument d = new PdfDocument(new PdfReader(pdf))) {
            for (int i = 1; i <= d.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(d.getPage(i)));
        }
        return sb.toString();
    }

    private static int imagenes(File pdf) throws Exception {
        int n = 0;
        try (PdfDocument d = new PdfDocument(new PdfReader(pdf))) {
            for (int i = 1; i <= d.getNumberOfPages(); i++) {
                var xo = d.getPage(i).getResources().getResource(com.itextpdf.kernel.pdf.PdfName.XObject);
                if (xo != null) n += xo.size();
            }
        }
        return n;
    }

    private static List<String> hojas(File pdf) throws Exception {
        List<String> out = new ArrayList<>();
        try (PdfDocument d = new PdfDocument(new PdfReader(pdf))) {
            for (int i = 1; i <= d.getNumberOfPages(); i++) out.add(PdfTextExtractor.getTextFromPage(d.getPage(i)));
        }
        return out;
    }

    /** Every sheet carries the format's two logos and its watermark. */
    private static final int IMAGENES_DE_LA_HOJA = 3;

    /** The Word format of the inventario digital: three bienes per sheet, each área
     *  on its own sheets, the área and the sheet number at the foot. */
    @Test void inventarioFotografico_tresPorHoja_cadaAreaEnSusHojas() throws Exception {
        String foto = FotosPdfTest.imagen(dir, "f.jpg", 1600, 1200).toString();
        List<Producto> bienes = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            bienes.add(bien("p" + i, "STM-DC-0" + i, i < 4 ? "Dirección de Catastro" : "Tesorería — Ingresos",
                i % 2 == 0 ? foto : null));

        File pdf = new ReporteInventarioFotograficoService().exportInventarioFotografico(bienes);
        String t = texto(pdf);
        assertTrue(t.contains("DESCRIPCIÓN") && t.contains("NO. DE INVENTARIO") && t.contains("FOTOGRAFÍA DE BIEN MUEBLE"));
        assertTrue(t.contains("Sin fotografía registrada"));

        List<String> h = hojas(pdf);
        assertEquals(3, h.size(), "Catastro: 3 + 1; Tesorería: 2");
        assertTrue(h.get(0).contains("STM-DC-02") && !h.get(0).contains("STM-DC-03"), "tres por hoja");
        assertTrue(h.get(1).contains("STM-DC-03") && !h.get(1).contains("STM-DC-04"), "la otra área empieza hoja");
        assertTrue(h.get(2).contains("STM-DC-05"));
        assertTrue(h.get(0).contains("DIRECCIÓN DE CATASTRO") && h.get(0).contains("Hoja 1 de 3"), "pie con el área");
        assertTrue(h.get(2).contains("TESORERÍA — INGRESOS") && h.get(2).contains("Hoja 3 de 3"));
        assertEquals(3 * IMAGENES_DE_LA_HOJA + 3, imagenes(pdf), "las tres fotos y en cada hoja los logos y la marca de agua");
    }

    /** Reportes › Inventario por área › Sin fotos: the Inventario General of the
     *  chosen áreas (same header, columns, total and firmas), not the photo sheet,
     *  and without loading a single photo. */
    @Test void inventarioSinFotos_esElInventarioGeneralDeEsasAreas() throws Exception {
        String foto = FotosPdfTest.imagen(dir, "g.jpg", 800, 600).toString();
        List<Producto> bienes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            bienes.add(bien("s" + i, "SGM/" + (i == 0 ? "100" : "0" + i), i < 2 ? "Dirección de Catastro" : "Tesorería — Ingresos", foto));

        File pdf = new ReporteService().exportInventarioSinFotos(bienes);
        String t = texto(pdf);
        assertTrue(t.contains("Inventario por Área"));
        assertTrue(t.contains("Categoría") && t.contains("Cantidad") && t.contains("Valor"), "columnas del Inventario General");
        assertTrue(t.contains("VALOR TOTAL DEL INVENTARIO") && t.contains("(4 bienes)"));
        assertFalse(t.contains("FOTOGRAF"), "no es la hoja de fotos");
        assertTrue(t.indexOf("SGM/01") < t.indexOf("SGM/100") && t.indexOf("SGM/100") < t.indexOf("SGM/02"),
            "por área y número: Catastro (01, 100) y luego Tesorería (02, 03)");
        assertTrue(imagenes(pdf) <= 1, "a lo más el logo: ninguna foto de bien");
        assertTrue(t.contains("TOTAL DIRECCIÓN DE CATASTRO  (2 bienes)") && t.contains("TOTAL TESORERÍA — INGRESOS  (2 bienes)"),
            "cada área cierra con su total");
        assertTrue(t.indexOf("TOTAL DIRECCIÓN DE CATASTRO") < t.indexOf("SGM/02"), "el total del área va al terminarla");

        File una = new ReporteService().exportInventarioSinFotos(bienes.subList(0, 2));
        String u = texto(una);
        assertTrue(u.contains("Inventario por Área · Dirección de Catastro"), "con una sola área, su nombre");
        assertTrue(u.contains("TOTAL DIRECCIÓN DE CATASTRO") && !u.contains("VALOR TOTAL DEL INVENTARIO"),
            "con una sola área, su total es el del documento");
    }

    @Test void losCodigosVanEnOrdenDeNumero() {
        List<String> codigos = new ArrayList<>(List.of("DGOB/100", "DGOB/10", "TICS/01", "DGOB/09", "DGOB/1"));
        codigos.sort(ReporteService.POR_CODIGO);
        assertEquals(List.of("DGOB/1", "DGOB/09", "DGOB/10", "DGOB/100", "TICS/01"), codigos);
    }

    /** Old códigos without a número mixed in: still one consistent order (a sort of a
     *  few hundred rows refuses a comparator that is not). */
    @Test void elOrdenDeCodigosEsConsistente_conCodigosViejos() {
        List<String> raros = List.of("X/10", "X/9", "X/2a", "X/", "SGM-DG-01", "sgm-dg-1", "SF-DC-2561", "", "A", "X/09", "x/9");
        var c = ReporteService.POR_CODIGO;
        for (String a : raros) for (String b : raros) {
            assertEquals(Integer.signum(c.compare(a, b)), -Integer.signum(c.compare(b, a)), a + " / " + b);
            for (String d : raros)
                if (c.compare(a, b) <= 0 && c.compare(b, d) <= 0)
                    assertTrue(c.compare(a, d) <= 0, a + " ≤ " + b + " ≤ " + d);
        }
    }

    @Test void inventarioFotografico_sinBienes_esError() {
        assertThrows(IllegalArgumentException.class,
            () -> new ReporteInventarioFotograficoService().exportInventarioFotografico(List.of()));
    }

    @Test void fichaTecnica_llevaLaFoto_yYaNoHablaDeStock() throws Exception {
        Producto p = bien("f1", "SF-DC-2561", "Dirección de Catastro",
            FotosPdfTest.imagen(dir, "ficha.jpg", 2400, 1800).toString());
        p.setFotosUrls(new ArrayList<>(List.of(FotosPdfTest.imagen(dir, "g.png", 400, 400).toString())));
        p.setResguardante("Alejandro Díaz");
        Movimiento m = new Movimiento();
        m.setTipo(TipoMovimiento.TRANSFERENCIA); m.setCreadoEn(LocalDateTime.now());
        m.setAreaOrigen("Tesorería — Ingresos"); m.setAreaDestino("Dirección de Catastro");

        File pdf = new ReporteFichaTecnicaService().exportFichaTecnica(p, List.of(m));
        String t = texto(pdf);
        assertTrue(t.contains("FICHA TÉCNICA DEL BIEN MUEBLE"));
        assertTrue(t.contains("SF-DC-2561"));
        assertTrue(t.contains("TESORERÍA MUNICIPAL"), "departamento = secretaría del área");
        assertFalse(t.contains("Stock"), "un bien patrimonial no tiene stock");
        assertTrue(imagenes(pdf) >= 3, "foto principal, galería y QR");
    }

    @Test void detalleDeMovimiento_enPalabras() {
        Movimiento m = new Movimiento();
        m.setAreaOrigen("A"); m.setAreaDestino("B");
        m.setCodigoAnterior("X-1"); m.setCodigoNuevo("Y-1"); m.setMotivo("Reasignación");
        assertEquals("A » B · código X-1 » Y-1 · Reasignación", ReporteFichaTecnicaService.detalle(m));
        assertEquals("—", ReporteFichaTecnicaService.detalle(new Movimiento()));
    }
}
