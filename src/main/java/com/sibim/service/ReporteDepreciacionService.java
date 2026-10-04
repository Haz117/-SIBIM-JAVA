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
        "Bien", "Categoría", "Fecha de adquisición", "Vida útil (años)",
        "Valor de compra", "Valor actual", "% depreciado"};

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
                // A bien with no vida útil set (most of an inventory just captured) leaves the
                // cell empty; unboxing the null used to abort the whole export.
                if (p.getVidaUtilAnios() != null) r.createCell(3).setCellValue(p.getVidaUtilAnios());
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
            float[] widths = {3.2f, 1.9f, 1.3f, 1f, 1.4f, 1.4f, 1.1f};
            Table table = createPdfTable(DEP_HEADERS, widths);
            alinearDerecha(table, 3, 4, 5, 6);
            int idx = 0;
            java.math.BigDecimal compra = java.math.BigDecimal.ZERO, actual = java.math.BigDecimal.ZERO;
            for (Producto p : productos) {
                boolean alt = (idx++ % 2) == 1;
                if (p.getPrecioCompra() != null) compra = compra.add(p.getPrecioCompra());
                if (p.getValorDepreciado() != null) actual = actual.add(p.getValorDepreciado());
                table.addCell(fila(p.getNombre(), alt));
                table.addCell(fila(p.getCategoriaNombre(), alt));
                table.addCell(fila(p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : "—", alt));
                table.addCell(num(fila(p.getVidaUtilAnios() != null ? String.valueOf(p.getVidaUtilAnios()) : "—", alt)));
                table.addCell(num(fila(FormatUtils.formatCurrency(p.getPrecioCompra()), alt)));
                table.addCell(num(fila(FormatUtils.formatCurrency(p.getValorDepreciado()), alt)));
                table.addCell(num(fila(p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() + "%" : "—", alt)));
            }
            addFilaTotal(table, 4, "TOTAL  (" + productos.size() + " bienes)",
                FormatUtils.formatCurrency(compra), FormatUtils.formatCurrency(actual), "");
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ",           getCurrentUserName(),    "Director de Recursos Materiales"},
                new String[]{"CONTADOR MUNICIPAL", null, "Contador / Contralor Municipal"},
                new String[]{"VO.BO.",             null, "Tesorero Municipal"});
            addPdfFooter(doc, productos.size(), folio);
        }
        return numerarPaginas(file);
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
