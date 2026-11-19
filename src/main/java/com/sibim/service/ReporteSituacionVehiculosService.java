package com.sibim.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Producto;
import com.sibim.service.SituacionVehiculos.Situacion;
import com.sibim.service.SituacionVehiculos.Tiene;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Objects;

/** Reportes › «Situación de los vehículos»: each vehicle in force with its placas, its estatus and
 *  whether it has tarjeta de circulación, seguro, tenencia paid and verificación (see
 *  {@link SituacionVehiculos}), the ones with something to attend first. */
public class ReporteSituacionVehiculosService extends ReporteService {

    private static final String[] COLUMNAS = {"Número", "Vehículo", "Placas", "Área", "Resguardante",
        "Estatus", "Tarjeta", "Seguro", "Tenencia", "Verificación"};
    private static final DeviceRgb ROJO = new DeviceRgb(159, 34, 65);

    /** «45 sin seguro · 70 sin verificación · …», only what there is. */
    public static String resumenEnTexto(SituacionVehiculos.Resumen r) {
        List<String> p = new java.util.ArrayList<>();
        if (r.sinSeguro() > 0) p.add(r.sinSeguro() + " sin seguro");
        if (r.sinTarjeta() > 0) p.add(r.sinTarjeta() + " sin tarjeta de circulación");
        if (r.sinVerificacion() > 0) p.add(r.sinVerificacion() + " sin verificación");
        if (r.sinTenencia() > 0) p.add(r.sinTenencia() + " sin la tenencia pagada");
        if (r.sinPlacas() > 0) p.add(r.sinPlacas() + " sin placas");
        if (r.descompuestos() > 0) p.add(r.descompuestos() + (r.descompuestos() == 1 ? " descompuesto" : " descompuestos"));
        if (r.retenidos() > 0) p.add(r.retenidos() + (r.retenidos() == 1 ? " retenido" : " retenidos"));
        return String.join(" · ", p);
    }

    private static final String FUENTE = "Según la descripción de cada vehículo (la lista de vehículos de octubre de 2026). "
        + "Para ponerla al día, edita su descripción («Seguro: si.») o captura en su formulario la póliza o la tarjeta de circulación.";

    public File exportPdf(List<Producto> bienes) throws Exception {
        List<Situacion> lista = SituacionVehiculos.de(bienes);
        SituacionVehiculos.Resumen r = SituacionVehiculos.resumen(lista);
        File file = tempFile("situacion_vehiculos", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER.rotate())) {
            String folio = generateFolio("VEH");
            addPdfHeader(doc, "Situación de los vehículos", null, null, folio);
            doc.add(new Paragraph(r.vehiculos() + " vehículos" + (resumenEnTexto(r).isEmpty() ? "" : ": " + resumenEnTexto(r)))
                .setFontSize(10).setMarginTop(4).setMarginBottom(2));
            doc.add(new Paragraph(FUENTE).setFontSize(8).setFontColor(new DeviceRgb(107, 114, 128)).setMarginBottom(6));
            // Wide enough for «DESCOMPUESTA», «SIN PLACAS» and «Verificación» without breaking them.
            Table t = createPdfTable(COLUMNAS, new float[]{1.55f, 2.35f, 1.3f, 2.0f, 1.75f, 1.9f, 0.95f, 0.95f, 1.05f, 1.25f});
            int i = 0;
            for (Situacion s : lista) {
                boolean alt = (i++ % 2) == 1;
                Producto p = s.vehiculo();
                t.addCell(fila(Objects.requireNonNullElse(ReporteParqueVehicularService.numeroDeInventario(p), ""), alt).setKeepTogether(true));
                t.addCell(fila(Objects.requireNonNullElse(p.getNombre(), ""), alt).setKeepTogether(true));
                t.addCell(marcado(s.placas().isEmpty() ? "—" : s.placas(), s.sinPlacas(), alt));
                t.addCell(fila(Objects.requireNonNullElse(p.getArea(), ""), alt).setKeepTogether(true));
                t.addCell(fila(Objects.requireNonNullElse(p.getResguardante(), ""), alt).setKeepTogether(true));
                t.addCell(marcado(s.estatus().isEmpty() ? "—" : s.estatus(), s.descompuesto() || s.retenido(), alt));
                for (Tiene x : List.of(s.tarjeta(), s.seguro(), s.tenencia(), s.verificacion()))
                    t.addCell(marcado(x.texto(), x == Tiene.NO, alt));
            }
            if (lista.isEmpty()) sinRegistros(t, COLUMNAS.length, "No hay vehículos en el inventario");
            doc.add(t);
            addPdfFooter(doc, lista.size(), folio);
        }
        return numerarPaginas(file);
    }

    /** A cell whose «No» (or broken down, or without placas) reads in guinda bold. */
    private static com.itextpdf.layout.element.Cell marcado(String texto, boolean mal, boolean alt) {
        com.itextpdf.layout.element.Cell c = fila(texto, alt).setKeepTogether(true);   // a row is not split between pages
        if (mal) c.getChildren().forEach(e -> {
            if (e instanceof Paragraph p) {
                p.setFontColor(ROJO);
                try { p.setFont(com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)); }
                catch (java.io.IOException ignored) { /* stays regular */ }
            }
        });
        return c;
    }

    public File exportExcel(List<Producto> bienes) throws Exception {
        List<Situacion> lista = SituacionVehiculos.de(bienes);
        File file = tempFile("situacion_vehiculos", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet hoja = createSheet(wb, "Situación de los vehículos");
            String[] titulos = {"Número", "Número en SIBIM", "Vehículo", "Marca", "Modelo", "Serie", "Placas", "Área",
                "Resguardante", "Estado físico", "Estatus", "Tarjeta de circulación", "Seguro", "Tenencia pagada", "Verificación"};
            writeHeader(hoja, titulos, wb);
            int n = 1;
            for (Situacion s : lista) {
                Producto p = s.vehiculo();
                Row row = hoja.createRow(n++);
                String[] v = {ReporteParqueVehicularService.numeroDeInventario(p), p.getCodigo(), p.getNombre(), p.getMarca(),
                    p.getModelo(), p.getNumeroSerie(), s.placas(), p.getArea(), p.getResguardante(), p.getEstadoFisico(),
                    s.estatus(), s.tarjeta().texto(), s.seguro().texto(), s.tenencia().texto(), s.verificacion().texto()};
                for (int c = 0; c < v.length; c++) row.createCell(c).setCellValue(Objects.requireNonNullElse(v[c], ""));
            }
            autosizeColumns(hoja, titulos.length);
            addExcelInfoSheet(wb, "Situación de los vehículos", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }
}
