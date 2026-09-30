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

    @Test void inventarioFotografico_conYSinFoto_variasAreas() throws Exception {
        String foto = FotosPdfTest.imagen(dir, "f.jpg", 1600, 1200).toString();
        List<Producto> bienes = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            bienes.add(bien("p" + i, "STM-DC-0" + i, i < 4 ? "Dirección de Catastro" : "Tesorería — Ingresos",
                i % 2 == 0 ? foto : null));

        File pdf = new ReporteInventarioFotograficoService().exportInventarioFotografico(bienes);
        String t = texto(pdf);
        assertTrue(t.contains("INVENTARIO FOTOGRÁFICO DE BIENES MUEBLES"));
        assertTrue(t.contains("FOTOGRAFÍA DE BIEN MUEBLE"));
        assertTrue(t.contains("STM-DC-05"));
        assertTrue(t.contains("Sin fotografía registrada"));
        assertTrue(t.contains("DIRECCIÓN DE CATASTRO") && t.contains("TESORERÍA — INGRESOS"), "subtítulo por área");
        assertTrue(imagenes(pdf) >= 3, "las tres fotos van en el PDF");
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
