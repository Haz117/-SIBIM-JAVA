package com.sibim.service;

import com.sibim.model.Producto;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Table;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.util.List;

public class ReporteBajasService extends ReporteService {

    public ReporteBajasService() { super(); }

    public File exportBajasPdf(List<Producto> bajas) throws Exception {
        if (bajas.isEmpty()) return null;
        File file = tempFile("bienes_baja", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            String folio = generateFolio("BAJ");
            addPdfHeader(doc, "Bienes Dados de Baja", null, null, folio);
            String[] headers = {"Nombre", "Código", "Área", "Categoría", "Fecha baja", "Motivo"};
            float[] widths  = {3f, 1.5f, 2f, 1.5f, 1.5f, 3f};
            Table table = createPdfTable(headers, widths);
            for (Producto p : bajas) {
                table.addCell(cell(p.getNombre()));
                table.addCell(cell(p.getCodigo()));
                table.addCell(cell(p.getArea() != null ? p.getArea() : ""));
                table.addCell(cell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : ""));
                table.addCell(cell(p.getFechaBaja() != null ? p.getFechaBaja().format(FMT) : ""));
                table.addCell(cell(p.getMotivoBaja() != null ? p.getMotivoBaja() : ""));
            }
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ",    getCurrentUserName(), "Director de Recursos Materiales"},
                new String[]{"JEFE DE INVENTARIOS", "_______________", "Jefe de Inventarios y Patrimonio"},
                new String[]{"VO.BO.",     "_______________", "Secretario General Municipal"});
            addPdfFooter(doc, bajas.size(), folio);
        }
        return file;
    }

    public File exportBajasExcel(List<Producto> bajas) throws Exception {
        if (bajas.isEmpty()) return null;
        String[] headers = {"Nombre", "Código", "Área", "Categoría", "Resguardante",
                            "Precio compra", "Fecha baja", "Motivo"};
        File file = tempFile("bienes_baja", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Dados de Baja");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Producto p : bajas) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(p.getNombre());
                r.createCell(1).setCellValue(p.getCodigo());
                r.createCell(2).setCellValue(p.getArea() != null ? p.getArea() : "");
                r.createCell(3).setCellValue(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
                r.createCell(4).setCellValue(p.getResguardante() != null ? p.getResguardante() : "");
                r.createCell(5).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                r.createCell(6).setCellValue(p.getFechaBaja() != null ? p.getFechaBaja().format(FMT) : "");
                r.createCell(7).setCellValue(p.getMotivoBaja() != null ? p.getMotivoBaja() : "");
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Bienes Dados de Baja", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportBajasCsv(List<Producto> bajas) throws Exception {
        if (bajas.isEmpty()) return null;
        File file = tempFile("bienes_baja", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Código,Área,Categoría,Resguardante,Precio compra,Fecha baja,Motivo");
            for (Producto p : bajas) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()),
                    esc(p.getArea()), esc(p.getCategoriaNombre()),
                    esc(p.getResguardante()),
                    p.getPrecioCompra() != null ? p.getPrecioCompra() : java.math.BigDecimal.ZERO,
                    p.getFechaBaja() != null ? p.getFechaBaja().format(FMT) : "",
                    esc(p.getMotivoBaja()));
            }
        }
        return file;
    }
}
