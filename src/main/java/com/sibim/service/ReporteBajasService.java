package com.sibim.service;

import com.sibim.model.Producto;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.time.LocalDate;
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

    // ── Acta de Baja Patrimonial ─────────────────────────────────────────────

    /** Generates a formal "Acta de Baja Patrimonial" PDF for a single bien
     *  that has been decommissioned. Includes the basic baja data and, when
     *  available, the committee dictamen block with the final destination,
     *  acta number, dictamen date and resolution text. */
    public File exportActaBaja(Producto p) throws Exception {
        File file = tempFile("acta_baja", ".pdf");

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        DeviceRgb muted  = new DeviceRgb(107, 114, 128);
        DeviceRgb bgAlt  = new DeviceRgb(248, 250, 252);
        DeviceRgb purple = COLOR_HEADER; // same purple-900 used throughout

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            String folio = generateFolio("BAJ");

            // 1. Standard header
            addPdfHeader(doc, "ACTA DE BAJA PATRIMONIAL", null, null, folio);

            // 2. Info block — 2-column label/value table
            doc.add(new Paragraph("").setMarginTop(10));
            Table infoTable = new Table(new float[]{1f, 2.5f}).useAllAvailableWidth();

            String fechaBajaStr = p.getFechaBaja() != null
                ? p.getFechaBaja().format(FMT)
                : LocalDate.now().format(FMT);

            addRow(infoTable, "BIEN",          p.getNombre(),                           bold, regular, muted, bgAlt, false);
            addRow(infoTable, "CÓDIGO",        p.getCodigo(),                           bold, regular, muted, bgAlt, true);
            addRow(infoTable, "ÁREA",          p.getArea(),                             bold, regular, muted, bgAlt, false);
            addRow(infoTable, "RESGUARDANTE",  p.getResguardante() != null ? p.getResguardante() : "—",
                              bold, regular, muted, bgAlt, true);
            addRow(infoTable, "FECHA DE BAJA", fechaBajaStr,                            bold, regular, muted, bgAlt, false);
            addRow(infoTable, "MOTIVO",        p.getMotivoBaja() != null ? p.getMotivoBaja() : "—",
                              bold, regular, muted, bgAlt, true);

            doc.add(infoTable);

            // 3. Optional dictamen section (only when committee data was filled)
            if (p.getTipoDestinoBaja() != null) {
                doc.add(new Paragraph("").setMarginTop(14));

                // Purple header bar
                Table sectionHeader = new Table(new float[]{1f}).useAllAvailableWidth();
                com.itextpdf.layout.element.Cell hCell = new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph("DICTAMEN DEL COMITÉ")
                        .setFont(bold).setFontSize(9f).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(purple).setPadding(7)
                    .setBorder(Border.NO_BORDER);
                sectionHeader.addCell(hCell);
                doc.add(sectionHeader);

                String destinoLabel = switch (p.getTipoDestinoBaja()) {
                    case "DESTRUCCION"       -> "Destrucción";
                    case "DONACION"          -> "Donación";
                    case "SUBASTA"           -> "Subasta pública";
                    case "TRANSFERENCIA_ENTE"-> "Transferencia a otro ente";
                    case "OTRO"              -> "Otro";
                    default                  -> p.getTipoDestinoBaja();
                };

                String fechaDictamenStr = p.getFechaDictamen() != null
                    ? p.getFechaDictamen().format(FMT) : "—";

                Table dictTable = new Table(new float[]{1f, 2.5f}).useAllAvailableWidth();
                addRow(dictTable, "DESTINO FINAL",        destinoLabel,      bold, regular, muted, bgAlt, false);
                addRow(dictTable, "NO. DE ACTA",          p.getNumeroActaBaja() != null ? p.getNumeroActaBaja() : "—",
                                  bold, regular, muted, bgAlt, true);
                addRow(dictTable, "FECHA DEL DICTAMEN",   fechaDictamenStr,   bold, regular, muted, bgAlt, false);
                doc.add(dictTable);

                // Dictamen/resolución text — multiline cell if not blank
                if (p.getDictamenBaja() != null && !p.getDictamenBaja().isBlank()) {
                    doc.add(new Paragraph("").setMarginTop(6));
                    doc.add(new Paragraph("DICTAMEN / RESOLUCIÓN")
                        .setFont(bold).setFontSize(8.5f).setFontColor(muted).setMarginBottom(3));
                    Table dictTextTable = new Table(new float[]{1f}).useAllAvailableWidth();
                    com.itextpdf.layout.element.Cell dictCell = new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(p.getDictamenBaja()).setFont(regular).setFontSize(9.5f))
                        .setBackgroundColor(bgAlt).setPadding(8)
                        .setBorder(Border.NO_BORDER);
                    dictTextTable.addCell(dictCell);
                    doc.add(dictTextTable);
                }
            }

            // 4. Firmas block
            addFirmasBlock(doc,
                new String[]{"Elaboró",                getCurrentUserName(), ""},
                new String[]{"Jefe de Inventarios",    "_______________",    ""},
                new String[]{"Vo.Bo. Secretario General", "_______________", ""},
                new String[]{"Presidente Municipal",   "_______________",    ""}
            );

            // 5. Footer
            addPdfFooter(doc, 1);
        }
        return file;
    }
}
