package com.sibim.service;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** «Candidatos a baja»: the bienes in estado MALO or DEFICIENTE, área by área, the oldest first. */
class ReporteCandidatosBajaTest {

    @org.junit.jupiter.api.BeforeAll static void demo()   { com.sibim.db.DatabaseConfig.setDemoMode(true); }
    @org.junit.jupiter.api.AfterAll  static void noDemo() { com.sibim.db.DatabaseConfig.setDemoMode(false); }

    private static Producto bien(String codigo, String area, String estado, LocalDate fecha) {
        Producto p = new Producto();
        p.setId(codigo);
        p.setCodigo(codigo);
        p.setNombre("SILLA " + codigo);
        p.setArea(area);
        p.setEstadoFisico(estado);
        p.setFechaAdquisicion(fecha);
        p.setStockActual(1);
        return p;
    }

    @Test
    void soloMaloODeficiente_porArea_yLosMasViejosPrimero() throws Exception {
        Producto viejo = bien("SM/1", "Servicios Municipales", "MALO", LocalDate.of(2003, 10, 13));
        viejo.setPrecioCompra(new BigDecimal("10000"));
        viejo.setVidaUtilAnios(10);
        Producto nuevo = bien("SM/2", "Servicios Municipales", "malo ", LocalDate.of(2024, 6, 11));
        Producto sinFecha = bien("SM/3", "Servicios Municipales", "MALO", null);
        Producto deficiente = bien("TES/9", "Tesorería Municipal", "DEFICIENTE", LocalDate.of(2012, 9, 19));
        Producto bueno = bien("TES/10", "Tesorería Municipal", "BUENO", LocalDate.of(2012, 9, 19));
        Producto deBaja = bien("TES/11", "Tesorería Municipal", "MALO", LocalDate.of(2012, 9, 19));
        deBaja.setFechaBaja(LocalDate.of(2026, 1, 5));
        List<Producto> todos = List.of(nuevo, sinFecha, viejo, deficiente, bueno, deBaja);

        Map<String, List<Producto>> porArea = ReporteCandidatosBajaService.porArea(todos);
        assertEquals(List.of("SM/1", "SM/2", "SM/3"), porArea.get("Servicios Municipales").stream().map(Producto::getCodigo).toList(),
            "el más viejo primero; sin fecha al final; «malo » es MALO");
        assertEquals(List.of("TES/9"), porArea.get("Tesorería Municipal").stream().map(Producto::getCodigo).toList());
        assertTrue(ReporteCandidatosBajaService.resumen(porArea).startsWith("4 bienes en estado MALO o DEFICIENTE, en 2 áreas · costo $10,000.00"),
            ReporteCandidatosBajaService.resumen(porArea));
        assertEquals("23 años", ReporteCandidatosBajaService.anosDeUso(viejo, LocalDate.of(2026, 11, 24)));
        assertEquals("—", ReporteCandidatosBajaService.anosDeUso(sinFecha, LocalDate.of(2026, 11, 24)));
        assertEquals("menos de 1 año", ReporteCandidatosBajaService.anosDeUso(bien("X", "A", "MALO", LocalDate.of(2026, 6, 1)), LocalDate.of(2026, 11, 24)));

        var servicio = new ReporteCandidatosBajaService();
        File pdf = servicio.exportPdf(todos);
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            String texto = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(1));
            assertTrue(texto.contains("Servicios Municipales · 3 bienes") && texto.contains("DEFICIENTE"), texto);
            assertFalse(texto.contains("TES/10"), "el bueno no");
        } finally {
            pdf.delete();
        }
        File xlsx = servicio.exportExcel(todos);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(xlsx)) {
            assertEquals(4, wb.getSheetAt(0).getLastRowNum());
            assertEquals("Valor en libros", wb.getSheetAt(0).getRow(0).getCell(13).getStringCellValue());
        } finally {
            xlsx.delete();
        }
        assertNull(servicio.exportPdf(List.of(bueno)));
    }
}
