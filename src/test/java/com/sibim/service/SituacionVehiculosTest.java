package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.SituacionVehiculos.Tiene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: the vehicle list's situation (placas, estatus, papers) was only text in each
 *  descripción: 45 without seguro, 70 without verificación, 16 broken down, and no screen said so. */
class SituacionVehiculosTest {

    // The report's header reads Configuración: the demo database, as the other reports' tests.
    @org.junit.jupiter.api.BeforeAll static void demo()   { com.sibim.db.DatabaseConfig.setDemoMode(true); }
    @org.junit.jupiter.api.AfterAll  static void noDemo() { com.sibim.db.DatabaseConfig.setDemoMode(false); }

    private static Producto vehiculo(String codigo, String area, String descripcion) {
        Producto p = new Producto();
        p.setId(codigo); p.setCodigo(codigo); p.setNombre("CAMIONETA DE PRUEBA 2020"); p.setArea(area);
        p.setTipoBien("Camioneta"); p.setDescripcion(descripcion);
        return p;
    }

    @Test
    void leeLaDescripcion_yLosCamposCapturadosCuentan() {
        var s = SituacionVehiculos.de(vehiculo("PRU/01", "Área B",
            "Inventario anterior: SSP-154. Placas: HG043A8. Estatus: Funcionando. Tarjeta de circulación: si. "
                + "Tenencia pagada: no. Seguro: no. Verificación: no."));
        assertEquals("HG043A8", s.placas());
        assertEquals("FUNCIONANDO", s.estatus());
        assertEquals(Tiene.SI, s.tarjeta());
        assertEquals(Tiene.NO, s.seguro());
        assertEquals(Tiene.NO, s.tenencia());
        assertEquals(Tiene.NO, s.verificacion());
        assertTrue(s.pendiente());

        Producto conPoliza = vehiculo("PRU/02", "Área A", "Placas: NO. Estatus: Descompuesta. Seguro: no.");
        conPoliza.setNoPolizaSeguro("POL-123");
        var t = SituacionVehiculos.de(conPoliza);
        assertEquals(Tiene.SI, t.seguro(), "la póliza capturada cuenta");
        assertTrue(t.sinPlacas());
        assertTrue(t.descompuesto());
        assertEquals(Tiene.SIN_DATO, t.verificacion(), "lo que no dice, no se inventa");
    }

    @Test
    void soloVehiculos_primeroLosPendientes_yElResumen() {
        Producto silla = vehiculo("PRU/09", "Área A", "Seguro: no.");
        silla.setTipoBien("MUEBLE");
        List<Producto> bienes = List.of(
            vehiculo("PRU/03", "Área A", "Placas: HG1. Estatus: Funcionando. Tarjeta de circulación: si. Tenencia pagada: si. Seguro: si. Verificación: si."),
            vehiculo("PRU/04", "Área C", "Estatus: Retenida. Seguro: no."),
            vehiculo("PRU/05", "Área B", "Sin situación escrita"),
            silla);
        var lista = SituacionVehiculos.de(bienes);
        assertEquals(List.of("PRU/04", "PRU/03", "PRU/05"), lista.stream().map(s -> s.vehiculo().getCodigo()).toList(),
            "la silla no es vehículo; el retenido primero; los demás por área");
        var r = SituacionVehiculos.resumen(lista);
        assertEquals(3, r.vehiculos());
        assertEquals(1, r.sinSeguro());
        assertEquals(1, r.retenidos());
        assertEquals(1, r.sinDatos());
        assertEquals("1 sin seguro · 1 retenido", ReporteSituacionVehiculosService.resumenEnTexto(r));
    }

    @Test
    void elReporteEnPdfYExcel() throws Exception {
        List<Producto> bienes = List.of(
            vehiculo("PRU/06", "Área A", "Placas: HG2. Estatus: Funcionando. Seguro: no. Verificación: si."),
            vehiculo("PRU/07", "Área B", "Placas: NO. Estatus: Descompuesta. Seguro: si."));
        var servicio = new ReporteSituacionVehiculosService();
        java.io.File pdf = servicio.exportPdf(bienes);
        try (var d = new com.itextpdf.kernel.pdf.PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf))) {
            String texto = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(d.getPage(1));
            assertTrue(texto.contains("2 vehículos: 1 sin seguro · 1 sin placas · 1 descompuesto"), texto);
            assertTrue(texto.contains("SIN PLACAS") && texto.contains("PRU/06"), texto);
        } finally {
            pdf.delete();
        }
        java.io.File xlsx = servicio.exportExcel(bienes);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(xlsx)) {
            var hoja = wb.getSheetAt(0);
            assertEquals("Seguro", hoja.getRow(0).getCell(12).getStringCellValue());
            assertEquals(2, hoja.getLastRowNum());
        } finally {
            xlsx.delete();
        }
    }
}
