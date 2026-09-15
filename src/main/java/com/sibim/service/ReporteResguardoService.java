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
import com.sibim.model.Producto;

import java.io.File;
import java.util.List;

public class ReporteResguardoService extends ReporteService {

    public ReporteResguardoService() { super(); }

    public File exportarResguardoPdf(String resguardante, String area, List<Producto> bienes) throws Exception {
        File out = File.createTempFile("resguardo_", ".pdf");
        out.deleteOnExit();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER)) {

            doc.setMargins(50, 50, 60, 50);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            doc.add(new Paragraph("RESGUARDO DE BIENES MUNICIPALES")
                .setFont(bold).setFontSize(16)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            doc.add(new Paragraph(orgName())
                .setFont(regular).setFontSize(11)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(4));
            doc.add(new Paragraph("Resguardante: " + (resguardante != null ? resguardante : "—")
                    + "    |    Área: " + (area != null ? area : "—")
                    + "    |    Fecha: " + com.sibim.util.FormatUtils.formatDate(java.time.LocalDate.now()))
                .setFont(regular).setFontSize(10)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(16));

            float[] widths = {1.5f, 3.5f, 1.2f, 1.2f, 1.5f, 1.5f};
            Table table = new Table(widths).useAllAvailableWidth();
            DeviceRgb headerColor = new DeviceRgb(59, 130, 246);
            String[] headers = {"Código", "Nombre / Descripción", "Serie", "Stock", "Ubicación", "Estado"};
            for (String h : headers) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(headerColor).setPadding(5));
            }
            boolean alt = false;
            for (Producto p : bienes) {
                DeviceRgb rowBg = alt ? new DeviceRgb(243, 244, 246) : new DeviceRgb(255, 255, 255);
                String[] cells = {
                    p.getCodigo(),
                    p.getNombre() + (p.getDescripcion() != null && !p.getDescripcion().isBlank()
                        ? "\n" + p.getDescripcion() : ""),
                    p.getNumeroSerie() != null ? p.getNumeroSerie() : "—",
                    String.valueOf(p.getStockActual()),
                    p.getUbicacion() != null ? p.getUbicacion() : "—",
                    p.getEstado().name()
                };
                for (String cellVal : cells) {
                    table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(cellVal != null ? cellVal : "").setFont(regular).setFontSize(8))
                        .setBackgroundColor(rowBg).setPadding(4));
                }
                alt = !alt;
            }
            doc.add(table);

            doc.add(new Paragraph("\nTotal de bienes: " + bienes.size())
                .setFont(bold).setFontSize(10).setMarginTop(12));
            doc.add(new Paragraph("\n\n\n_______________________________          _______________________________")
                .setFont(regular).setFontSize(10)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            doc.add(new Paragraph("Firma del resguardante                         Vo.Bo. Jefe del Área")
                .setFont(regular).setFontSize(9)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
        }
        return out;
    }
}
