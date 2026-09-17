package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Table;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class ReporteDepreciacionService extends ReporteService {

    public ReporteDepreciacionService() { super(); }

    private static final String[] DEP_HEADERS = {
        "Nombre", "Categoria", "Fecha Adquisicion", "Vida Util (años)",
        "Valor Compra", "Valor Actual", "% Depreciado"};

    public File exportDepreciacionExcel(List<Producto> productos) throws Exception {
        File file = tempFile("depreciacion", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Depreciación");
            writeHeader(sheet, DEP_HEADERS, wb);
            int row = 1;
            for (Producto p : productos) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(p.getNombre());
                r.createCell(1).setCellValue(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
                r.createCell(2).setCellValue(FormatUtils.formatDate(p.getFechaAdquisicion()));
                r.createCell(3).setCellValue(p.getVidaUtilAnios());
                r.createCell(4).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                r.createCell(5).setCellValue(p.getValorDepreciado() != null ? p.getValorDepreciado().doubleValue() : 0);
                r.createCell(6).setCellValue(p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() : 0);
            }
            autosizeColumns(sheet, DEP_HEADERS.length);
            addExcelInfoSheet(wb, "Depreciación de Activos", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportDepreciacionPdf(List<Producto> productos) throws Exception {
        File file = tempFile("depreciacion", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            String folio = generateFolio("DEP");
            addPdfHeader(doc, "Depreciación de Activos", null, null, folio);
            float[] widths = {3f, 1.8f, 1.5f, 1.2f, 1.5f, 1.5f, 1.2f};
            Table table = createPdfTable(DEP_HEADERS, widths);
            for (Producto p : productos) {
                table.addCell(cell(p.getNombre()));
                table.addCell(cell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : ""));
                table.addCell(cell(FormatUtils.formatDate(p.getFechaAdquisicion())));
                table.addCell(cell(String.valueOf(p.getVidaUtilAnios())));
                table.addCell(cell(FormatUtils.formatCurrency(p.getPrecioCompra())));
                table.addCell(cell(FormatUtils.formatCurrency(p.getValorDepreciado())));
                table.addCell(cell(p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() + "%" : "—"));
            }
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ",           getCurrentUserName(),    "Director de Recursos Materiales"},
                new String[]{"CONTADOR MUNICIPAL", "_______________", "Contador / Contralor Municipal"},
                new String[]{"VO.BO.",             "_______________", "Tesorero Municipal"});
            addPdfFooter(doc, productos.size(), folio);
        }
        return file;
    }

    public File exportDepreciacionCsv(List<Producto> productos) throws Exception {
        File file = tempFile("depreciacion", ".csv");
        java.time.LocalDate hoy = java.time.LocalDate.now();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            pw.println("Nombre,Categoria,Area,Fecha Adquisicion,Vida Util (años),Vida Util Restante,Valor Compra,Valor Actual,% Depreciado,Depreciado el");
            for (Producto p : productos) {
                java.time.LocalDate fechaTotal = (p.getFechaAdquisicion() != null && p.getVidaUtilAnios() != null && p.getVidaUtilAnios() > 0)
                    ? p.getFechaAdquisicion().plusYears(p.getVidaUtilAnios()) : null;
                String restante = "—";
                if (fechaTotal != null) {
                    if (!fechaTotal.isAfter(hoy)) restante = "Cumplida";
                    else {
                        long meses = java.time.temporal.ChronoUnit.MONTHS.between(hoy, fechaTotal);
                        restante = meses < 12 ? meses + " meses" : (meses / 12) + " años";
                    }
                }
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",%.2f,%.2f,%s,\"%s\"%n",
                    esc(p.getNombre()),
                    esc(p.getCategoriaNombre()),
                    esc(p.getArea()),
                    FormatUtils.formatDate(p.getFechaAdquisicion()),
                    p.getVidaUtilAnios() != null ? p.getVidaUtilAnios() : 0,
                    restante,
                    p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0.0,
                    p.getValorDepreciado() != null ? p.getValorDepreciado().doubleValue() : 0.0,
                    p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() : 0,
                    fechaTotal != null ? fechaTotal.format(FMT) : "");
            }
        }
        return file;
    }
}
