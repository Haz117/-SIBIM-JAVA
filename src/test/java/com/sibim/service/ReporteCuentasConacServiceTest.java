package com.sibim.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Saldos por cuenta contable and the Relación de Bienes Muebles (6-nov-2026).
 *  Demo mode: orgName() and the folio read in-memory data, no database. */
class ReporteCuentasConacServiceTest {

    private final ReporteCuentasConacService service = new ReporteCuentasConacService();

    @BeforeAll static void demo()   { DatabaseConfig.setDemoMode(true); }
    @AfterAll  static void noDemo() { DatabaseConfig.setDemoMode(false); }

    private static Producto bien(String codigo, String nombre, String clave, String precio, int piezas,
                                 LocalDate compra, Integer vidaUtil) {
        Producto p = new Producto();
        p.setId("id-" + codigo);
        p.setCodigo(codigo);
        p.setNombre(nombre);
        p.setClaveArmonizada(clave);
        p.setPrecioCompra(precio == null ? null : new BigDecimal(precio));
        p.setStockActual(piezas);
        p.setStockMaximo(piezas);
        p.setFechaAdquisicion(compra);
        p.setVidaUtilAnios(vidaUtil);
        p.setResguardante("Persona Ficticia Resguardante");
        p.setArea("Área de prueba");
        return p;
    }

    @Test
    void cuentaDe_tomaLaSubcuentaDeLaClaveArmonizada() {
        assertEquals("1.2.4.1.3", ReporteCuentasConacService.cuentaDe(bien("A/1", "Laptop", "1.2.4.1.3.515", "1", 1, null, null)));
        assertEquals("1.2.4.4.1", ReporteCuentasConacService.cuentaDe(bien("A/2", "Camioneta", " 1.2.4.4.1.541 ", "1", 1, null, null)));
        assertEquals("1.2.4.5", ReporteCuentasConacService.cuentaDe(bien("A/3", "Chaleco", "1.2.4.5", "1", 1, null, null)));
        assertNull(ReporteCuentasConacService.cuentaDe(bien("A/4", "Sin clave", null, "1", 1, null, null)));
        assertNull(ReporteCuentasConacService.cuentaDe(bien("A/5", "Clave vacía", "  ", "1", 1, null, null)));
    }

    @Test
    void nombreCuenta_delPlanDeCuentas_oElDeSuCuentaMayor() {
        assertEquals("Equipo de Cómputo y de Tecnologías de la Información", ReporteCuentasConacService.nombreCuenta("1.2.4.1.3"));
        assertEquals("Equipo de Defensa y Seguridad (1.2.4.5.1)", ReporteCuentasConacService.nombreCuenta("1.2.4.5.1"));
        assertEquals(ReporteCuentasConacService.SIN_CLAVE, ReporteCuentasConacService.nombreCuenta(null));
    }

    @Test
    void saldos_sumanPorSubcuentaYCuenta_enOrdenDelPlan_yLosSinClaveAlFinal() {
        LocalDate haceDosAnios = LocalDate.now().minusYears(2);
        List<Producto> bienes = List.of(
            bien("C/1", "Laptop", "1.2.4.1.3.515", "30000", 1, haceDosAnios, 3),   // libros 10,000
            bien("C/2", "Escritorio", "1.2.4.1.1.511", "5000", 2, null, null),     // sin depreciar: 10,000
            bien("C/3", "Camioneta", "1.2.4.4.1.541", "400000", 1, null, 5),       // sin fecha: a su costo
            bien("C/4", "Sin clave", null, "100", 1, null, null));
        Producto baja = bien("C/5", "Dado de baja", "1.2.4.1.1.511", "999999", 1, null, null);
        baja.setFechaBaja(LocalDate.now());

        List<ReporteCuentasConacService.Saldo> sub = ReporteCuentasConacService.saldosPorSubcuenta(
            new java.util.ArrayList<>(List.of(bienes.get(0), bienes.get(1), bienes.get(2), bienes.get(3), baja)));
        assertEquals(List.of("1.2.4.1.1", "1.2.4.1.3", "1.2.4.4.1"),
            sub.stream().map(ReporteCuentasConacService.Saldo::cuenta).limit(3).toList());
        assertNull(sub.get(3).cuenta(), "los que no tienen clave van al final");
        var escritorio = sub.get(0);
        assertEquals(new BigDecimal("10000"), escritorio.costo().stripTrailingZeros().setScale(0));
        assertEquals(2, escritorio.piezas());
        assertEquals(1, escritorio.sinDepreciar());
        var laptop = sub.get(1);
        assertTrue(laptop.libros().compareTo(new BigDecimal("30000")) < 0, "la laptop ya se depreció");
        assertTrue(laptop.depreciacion().signum() > 0);

        List<ReporteCuentasConacService.Saldo> cuentas = ReporteCuentasConacService.saldosPorCuenta(bienes);
        assertEquals("1.2.4.1", cuentas.get(0).cuenta());
        assertEquals(2, cuentas.get(0).bienes());
        assertEquals(new BigDecimal("40000"), cuentas.get(0).costo().stripTrailingZeros().setScale(0));
    }

    @Test
    void relacion_sinDatosPersonales_conDescripcionYValorEnLibros() {
        Producto p = bien("D/7", "SILLA SECRETARIAL", "1.2.4.1.1.511", "1200", 3, null, null);
        p.setMarca("OFFIHO");
        p.setModelo("No tiene");
        String d = ReporteCuentasConacService.descripcion(p);
        assertEquals("3 piezas: SILLA SECRETARIAL · OFFIHO", d);
        assertFalse(d.contains("Ficticia"), "la relación no lleva al resguardante");
    }

    @Test
    void saldosPdfYExcel_seGeneranConTotalYCuentas() throws Exception {
        List<Producto> bienes = List.of(
            bien("E/1", "Laptop", "1.2.4.1.3.515", "30000", 1, LocalDate.now().minusYears(1), 3),
            bien("E/2", "Camioneta", "1.2.4.4.1.541", "400000", 1, null, null));
        File pdf = service.exportSaldosPdf(bienes);
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1)).replaceAll("\\s+", " ");
            for (String s : List.of("Saldos por cuenta contable", "1.2.4.1.3", "Equipo de Transporte", "TOTAL"))
                assertTrue(texto.contains(s), "falta «" + s + "»");
        } finally {
            pdf.delete();
        }
        File xlsx = service.exportSaldosExcel(bienes);
        try (FileInputStream in = new FileInputStream(xlsx); Workbook wb = new XSSFWorkbook(in)) {
            Sheet saldos = wb.getSheet("Saldos por cuenta");
            assertNotNull(saldos);
            Sheet detalle = wb.getSheet("Bienes por cuenta");
            assertEquals(2, detalle.getLastRowNum(), "un renglón por bien");
            Row total = saldos.getRow(saldos.getLastRowNum());
            assertEquals("TOTAL", total.getCell(1).getStringCellValue());
            assertTrue(total.getCell(5).getNumericCellValue() > 429_000);
        } finally {
            xlsx.delete();
        }
    }

    @Test
    void relacionPdfYExcel_formatoConac() throws Exception {
        List<Producto> bienes = List.of(bien("F/2", "ARCHIVERO", "1.2.4.1.1.511", "2500", 1, null, null),
            bien("F/1", "ESCRITORIO", "1.2.4.1.1.511", "4000", 1, null, null));
        File pdf = service.exportRelacionBienesMueblesPdf(bienes);
        try (PdfDocument doc = new PdfDocument(new PdfReader(pdf))) {
            String texto = PdfTextExtractor.getTextFromPage(doc.getPage(1)).replaceAll("\\s+", " ");
            // The folio column sits beside the title, and since the logo is always there (13-nov-2026)
            // the title takes two lines: the extracted text puts other things between them.
            assertTrue(texto.contains("Relación de Bienes Muebles que Componen") && texto.contains("el Patrimonio"), texto);
            assertTrue(texto.contains("Al " + java.time.LocalDate.now().getDayOfMonth() + " de "), texto);
            assertTrue(texto.contains("Descripción del bien mueble"));
            assertTrue(texto.indexOf("F/1") < texto.indexOf("F/2"), "en orden de código");
            assertFalse(texto.contains("Ficticia"), "sin datos personales");
        } finally {
            pdf.delete();
        }
        File xlsx = service.exportRelacionBienesMueblesExcel(bienes);
        try (FileInputStream in = new FileInputStream(xlsx); Workbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);
            assertEquals(ReporteCuentasConacService.TITULO_RELACION, hoja.getRow(1).getCell(0).getStringCellValue());
            assertEquals("F/1", hoja.getRow(5).getCell(0).getStringCellValue());
            assertEquals(6500, hoja.getRow(7).getCell(2).getNumericCellValue(), 0.001);
        } finally {
            xlsx.delete();
        }
    }
}
