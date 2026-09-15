package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles export of Conteo Físico (physical inventory count) sessions to PDF and Excel.
 * Extends {@link ReporteService} to inherit shared PDF/Excel helpers.
 */
public class ReporteConteoService extends ReporteService {

    public ReporteConteoService() { super(); }

    /** Exports a physical inventory count session (ConteoFisico + items) to a formal PDF report. */
    public File exportarConteoPdf(com.sibim.model.ConteoFisico c,
                                   List<com.sibim.model.ConteoItem> items) throws Exception {
        File out = tempFile("conteo_fisico_", ".pdf");
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER.rotate())) {

            doc.setMargins(50, 50, 60, 50);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            DeviceRgb accent = new DeviceRgb(8, 145, 178);

            // Header
            doc.add(new Paragraph("REPORTE DE CONTEO FÍSICO DE INVENTARIO")
                .setFont(bold).setFontSize(15)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            doc.add(new Paragraph(orgName())
                .setFont(regular).setFontSize(11)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(3));

            String fechaStr = c.getCreadoEn() != null
                ? com.sibim.util.FormatUtils.formatDateTime(c.getCreadoEn()) : "—";
            doc.add(new Paragraph(
                    "Fecha: " + fechaStr
                    + "    |    Realizado por: " + (c.getUsuarioNombre() != null ? c.getUsuarioNombre() : "—")
                    + "    |    Bienes: " + c.getTotalContados()
                    + "    |    Discrepancias: " + c.getTotalDiscrepancias())
                .setFont(regular).setFontSize(10)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(16));

            // Summary badge
            boolean sinDiff = c.getTotalDiscrepancias() == 0;
            String statusText = sinDiff
                ? "INVENTARIO CONFORME — Sin diferencias detectadas"
                : c.getTotalDiscrepancias() + " diferencia(s) detectada(s) y registrada(s) como ajustes";
            doc.add(new Paragraph(statusText)
                .setFont(bold).setFontSize(10)
                .setFontColor(sinDiff ? new DeviceRgb(5, 150, 105) : new DeviceRgb(217, 119, 6))
                .setMarginBottom(14));

            // Items table — landscape fits 8 columns comfortably
            float[] widths = {3.0f, 1.2f, 0.8f, 0.8f, 0.7f, 1.0f, 1.2f, 2.5f};
            Table table = new Table(widths).useAllAvailableWidth();
            String[] headers = {"Bien / Área", "Código", "Sistema", "Contado", "Diff.", "Estado", "Incidencia", "Observación"};
            for (String h : headers) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(accent).setPadding(5));
            }
            boolean alt = false;
            for (com.sibim.model.ConteoItem it : items) {
                DeviceRgb rowBg = alt ? new DeviceRgb(243, 244, 246) : new DeviceRgb(255, 255, 255);
                int diff = it.getStockContado() - it.getStockSistema();
                String diffStr    = diff == 0 ? "—" : (diff > 0 ? "+" + diff : String.valueOf(diff));
                String status     = diff == 0 ? "OK" : (it.isAjustado() ? "Ajustado" : "Pendiente");
                String incidencia = itemEstadoLabel(it.getEstadoConteo());
                String nota       = it.getNota() != null ? it.getNota() : "";
                String[] cells = {
                    it.getProductoNombre() + (it.getArea() != null ? "\n" + it.getArea() : ""),
                    it.getProductoCodigo() != null ? it.getProductoCodigo() : "",
                    String.valueOf(it.getStockSistema()),
                    String.valueOf(it.getStockContado()),
                    diffStr,
                    status,
                    incidencia,
                    nota
                };
                for (String cellVal : cells) {
                    table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(cellVal != null ? cellVal : "").setFont(regular).setFontSize(8))
                        .setBackgroundColor(rowBg).setPadding(4));
                }
                alt = !alt;
            }
            doc.add(table);

            doc.add(new Paragraph("\nTotal de bienes contados: " + c.getTotalContados()
                    + "    |    Discrepancias: " + c.getTotalDiscrepancias())
                .setFont(bold).setFontSize(10).setMarginTop(12));
            doc.add(new Paragraph("\n\n\n_______________________________          _______________________________")
                .setFont(regular).setFontSize(10)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            doc.add(new Paragraph("Firma del responsable de conteo                   Vo.Bo. Administrador")
                .setFont(regular).setFontSize(9)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
        }
        return out;
    }

    public File exportConteoPdf(String titulo, String usuario,
                                List<com.sibim.model.ConteoItem> items) throws Exception {
        File file = tempFile("conteo_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            Table headerTable = new Table(1).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("Conteo Físico — " + (titulo != null ? titulo : ""))
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                    .setFontSize(15).setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(LocalDate.now().format(FMT) + " · " + (usuario != null ? usuario : ""))
                    .setFont(regularFont).setFontSize(9)
                    .setFontColor(new DeviceRgb(200, 210, 240)));
            headerCell.setBackgroundColor(COLOR_HEADER).setPadding(12);
            headerTable.addCell(headerCell);
            doc.add(headerTable);

            String[] headers = {"Código", "Nombre", "Área", "Sistema", "Contado", "Diferencia", "Estado", "Nota"};
            float[] widths = {1.5f, 3f, 2f, 1f, 1f, 1f, 1.5f, 2f};
            Table table = createPdfTable(headers, widths);

            DeviceRgb redCell = new DeviceRgb(220, 38, 38);

            for (com.sibim.model.ConteoItem item : items) {
                int diff = item.getStockContado() - item.getStockSistema();
                boolean hasDiff = diff != 0;

                table.addCell(cell(item.getProductoCodigo() != null ? item.getProductoCodigo() : ""));
                table.addCell(cell(item.getProductoNombre() != null ? item.getProductoNombre() : ""));
                table.addCell(cell(item.getArea() != null ? item.getArea() : ""));
                table.addCell(cell(String.valueOf(item.getStockSistema())));
                table.addCell(cell(String.valueOf(item.getStockContado())));

                com.itextpdf.layout.element.Cell diffCell = new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph((diff > 0 ? "+" : "") + diff).setFont(regularFont).setFontSize(9));
                if (hasDiff) diffCell.setFontColor(redCell);
                table.addCell(diffCell);

                table.addCell(cell(item.getEstadoConteo() != null ? item.getEstadoConteo() : (hasDiff ? "DIFERENCIA" : "OK")));
                table.addCell(cell(item.getNota() != null ? item.getNota() : ""));
            }
            doc.add(table);
            addPdfFooter(doc, items.size());
        }
        return file;
    }

    public File exportConteoExcel(String titulo, String usuario,
                                  List<com.sibim.model.ConteoItem> items) throws Exception {
        String[] headers = {"Código", "Nombre", "Área", "Sistema", "Contado", "Diferencia", "Estado", "Nota"};
        File file = tempFile("conteo", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Conteo Físico");
            writeHeader(sheet, headers, wb);

            CellStyle yellowStyle = wb.createCellStyle();
            yellowStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            yellowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 1;
            for (com.sibim.model.ConteoItem it : items) {
                Row r = sheet.createRow(rowIdx++);
                int delta = it.getDelta();
                boolean hasDiff = it.isDiscrepancia();
                r.createCell(0).setCellValue(it.getProductoCodigo() != null ? it.getProductoCodigo() : "");
                r.createCell(1).setCellValue(it.getProductoNombre() != null ? it.getProductoNombre() : "");
                r.createCell(2).setCellValue(it.getArea() != null ? it.getArea() : "");
                r.createCell(3).setCellValue(it.getStockSistema());
                r.createCell(4).setCellValue(it.getStockContado());
                r.createCell(5).setCellValue(delta);
                r.createCell(6).setCellValue(hasDiff ? (it.isAjustado() ? "Ajustado" : "Con diferencia") : "Correcto");
                r.createCell(7).setCellValue(it.getNota() != null ? it.getNota() : "");
                if (hasDiff)
                    for (int i = 0; i < headers.length; i++) r.getCell(i).setCellStyle(yellowStyle);
            }
            autosizeColumns(sheet, headers.length);

            Sheet info = wb.createSheet("_Info");
            info.createRow(0).createCell(0).setCellValue("Título");
            info.getRow(0).createCell(1).setCellValue(titulo != null ? titulo : "Conteo Físico");
            info.createRow(1).createCell(0).setCellValue("Usuario");
            info.getRow(1).createCell(1).setCellValue(usuario != null ? usuario : "—");
            info.createRow(2).createCell(0).setCellValue("Fecha");
            info.getRow(2).createCell(1).setCellValue(LocalDate.now().format(FMT));
            info.autoSizeColumn(0); info.autoSizeColumn(1);

            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    private static String itemEstadoLabel(String code) {
        return switch (code != null ? code : "ENCONTRADO") {
            case "MAL_ESTADO"   -> "Mal estado";
            case "EN_OTRA_AREA" -> "En otra área";
            case "FALTANTE"     -> "Faltante";
            default             -> "Encontrado";
        };
    }
}
