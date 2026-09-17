package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ReporteDashboardService extends ReporteService {

    public ReporteDashboardService() { super(); }

    public File exportDashboardPdf(DashboardService.Resumen resumen, String destFolder) throws Exception {
        File file = destFolder != null
            ? new java.io.File(destFolder, "dashboard_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf")
            : tempFile("dashboard", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            String folio = generateFolio("DAS");
            addPdfHeader(doc, "Resumen del Inventario", null, null, folio);

            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("Estadísticas Generales")
                .setFont(bold).setFontSize(11).setFontColor(COLOR_HEADER)
                .setMarginTop(8).setMarginBottom(4));

            var stats = resumen.stats();
            String[][] statsRows = {
                {"Total de bienes",      String.valueOf(stats.total())},
                {"Bienes activos",       String.valueOf(stats.activos())},
                {"Agotados",             String.valueOf(resumen.agotados().size())},
                {"Bajo stock",           String.valueOf(resumen.bajoStock().size())},
                {"Categorías",           String.valueOf(stats.categorias())},
                {"Valor total (compra)", com.sibim.util.FormatUtils.formatCurrency(stats.valorTotal())},
            };
            Table tStats = createPdfTable(new String[]{"Indicador", "Valor"}, new float[]{3f, 2f});
            for (String[] row : statsRows) {
                tStats.addCell(cell(row[0]));
                tStats.addCell(cell(row[1]));
            }
            doc.add(tStats);

            doc.add(new Paragraph("Movimientos Hoy (" + resumen.movHoy().size() + ")")
                .setFont(bold).setFontSize(11).setFontColor(COLOR_HEADER)
                .setMarginTop(12).setMarginBottom(4));
            if (!resumen.movHoy().isEmpty()) {
                Table tMov = createPdfTable(
                    new String[]{"Bien", "Tipo", "Cantidad", "Usuario"},
                    new float[]{3f, 1.5f, 1f, 2f});
                resumen.movHoy().stream().limit(20).forEach(m -> {
                    tMov.addCell(cell(m.getProductoNombre() != null ? m.getProductoNombre() : ""));
                    tMov.addCell(cell(m.getTipo() != null ? m.getTipo().getEtiqueta() : ""));
                    tMov.addCell(cell(String.valueOf(m.getCantidad())));
                    tMov.addCell(cell(m.getUsuarioNombre() != null ? m.getUsuarioNombre() : ""));
                });
                doc.add(tMov);
                if (resumen.movHoy().size() > 20)
                    doc.add(new Paragraph("… y " + (resumen.movHoy().size() - 20) + " movimientos más")
                        .setFont(regular).setFontSize(9).setFontColor(ColorConstants.GRAY));
            } else {
                doc.add(new Paragraph("Sin movimientos registrados hoy.")
                    .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY));
            }

            if (!resumen.byArea().isEmpty()) {
                doc.add(new Paragraph("Distribución por Área (Top " + resumen.byArea().size() + ")")
                    .setFont(bold).setFontSize(11).setFontColor(COLOR_HEADER)
                    .setMarginTop(12).setMarginBottom(4));
                Table tArea = createPdfTable(new String[]{"Área", "Total bienes"}, new float[]{4f, 1.5f});
                resumen.byArea().forEach((area, count) -> {
                    tArea.addCell(cell(area));
                    tArea.addCell(cell(String.valueOf(count)));
                });
                doc.add(tArea);
            }

            addPdfFooter(doc, (int) stats.total(), folio);
        }
        return file;
    }
}
