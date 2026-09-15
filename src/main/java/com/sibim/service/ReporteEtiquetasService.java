package com.sibim.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
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

public class ReporteEtiquetasService extends ReporteService {

    public ReporteEtiquetasService() { super(); }

    public File exportEtiquetasQrPdf(List<Producto> productos) throws Exception {
        if (productos.isEmpty()) return null;
        List<Producto> items = productos.size() > 200 ? productos.subList(0, 200) : productos;
        File file = tempFile("etiquetas_qr", ".pdf");
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerBg = new DeviceRgb(76, 29, 149);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(18, 14, 18, 14);
            Table grid = new Table(3).useAllAvailableWidth();
            grid.setMarginBottom(0);

            for (Producto p : items) {
                byte[] qrBytes = qrToPngBytes(p.getCodigo() != null ? p.getCodigo() : p.getNombre(), 160);

                com.itextpdf.layout.element.Cell card = new com.itextpdf.layout.element.Cell();
                card.setBorder(new com.itextpdf.layout.borders.SolidBorder(new DeviceRgb(203, 213, 225), 0.5f));
                card.setPadding(8).setMargin(3);
                card.setKeepTogether(true);

                if (qrBytes != null) {
                    com.itextpdf.layout.element.Image qrImg = new com.itextpdf.layout.element.Image(
                        com.itextpdf.io.image.ImageDataFactory.create(qrBytes));
                    qrImg.setAutoScale(false).setWidth(80).setHeight(80)
                         .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                    card.add(qrImg);
                }

                Paragraph codigoPar = new Paragraph(p.getCodigo() != null ? p.getCodigo() : "—")
                    .setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE);
                com.itextpdf.layout.element.Cell codBadge = new com.itextpdf.layout.element.Cell()
                    .add(codigoPar).setBackgroundColor(headerBg).setPadding(2)
                    .setBorder(null)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
                Table codTable = new Table(1).useAllAvailableWidth().addCell(codBadge);
                card.add(codTable);

                card.add(new Paragraph(p.getNombre() != null ? p.getNombre() : "—")
                    .setFont(bold).setFontSize(7.5f).setFontColor(new DeviceRgb(15, 23, 42))
                    .setMarginTop(4).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));

                if (p.getArea() != null && !p.getArea().isBlank()) {
                    card.add(new Paragraph(p.getArea())
                        .setFont(regular).setFontSize(6.5f).setFontColor(new DeviceRgb(100, 116, 139))
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
                }

                grid.addCell(card);
            }
            int rem = items.size() % 3;
            if (rem != 0) for (int i = rem; i < 3; i++)
                grid.addCell(new com.itextpdf.layout.element.Cell().setBorder(null));

            doc.add(grid);
            addPdfFooter(doc, items.size());
        }
        return file;
    }

    private static byte[] qrToPngBytes(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size, java.util.Map.of(EncodeHintType.MARGIN, 1));
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }
}
