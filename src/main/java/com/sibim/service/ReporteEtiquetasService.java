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

    // ── Etiqueta Física Institucional ────────────────────────────────────────

    /**
     * Genera etiquetas físicas en formato institucional (6 por página, 2×3).
     * Cada etiqueta contiene: encabezado municipal, área, descripción del bien,
     * marca/modelo/serie y el número de inventario en grande.
     */
    public File exportEtiquetaFisicaPdf(List<Producto> productos) throws Exception {
        if (productos.isEmpty()) return null;
        List<Producto> items = productos.size() > 300 ? productos.subList(0, 300) : productos;

        File file = tempFile("etiquetas_fisicas", ".pdf");
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        DeviceRgb brandBg   = new DeviceRgb(76, 29, 149);
        DeviceRgb brandFg   = ColorConstants.WHITE;
        DeviceRgb borderClr = new DeviceRgb(76, 29, 149);
        DeviceRgb labelBg   = new DeviceRgb(237, 233, 254); // violet-100
        DeviceRgb labelFg   = new DeviceRgb(76, 29, 149);
        DeviceRgb grayFg    = new DeviceRgb(55, 65, 81);
        DeviceRgb lightBg   = new DeviceRgb(249, 250, 251);

        int anioInicio = java.time.LocalDate.now().getYear();
        String periodo = anioInicio + " - " + (anioInicio + 3);
        String org     = orgName();

        try (PdfWriter  writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document    doc    = new Document(pdfDoc, PageSize.LETTER)) {

            doc.setMargins(18, 18, 18, 18);
            // Grid 2 columnas
            Table grid = new Table(UnitValue.createPercentArray(new float[]{1f, 1f}))
                .useAllAvailableWidth();

            for (Producto p : items) {
                // Contenedor de la tarjeta
                com.itextpdf.layout.element.Cell card =
                    new com.itextpdf.layout.element.Cell()
                        .setPadding(0).setMargin(3)
                        .setBorder(new com.itextpdf.layout.borders.SolidBorder(borderClr, 1.5f))
                        .setKeepTogether(true);

                // ── Tabla interna de la tarjeta ──
                Table inner = new Table(UnitValue.createPercentArray(new float[]{1f, 1.7f}))
                    .useAllAvailableWidth();

                // Fila cabecera (span completo)
                com.itextpdf.layout.element.Cell hdr =
                    new com.itextpdf.layout.element.Cell(1, 2)
                        .setBackgroundColor(brandBg).setPadding(5)
                        .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
                hdr.add(new Paragraph(org).setFont(bold).setFontSize(7)
                    .setFontColor(brandFg)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
                hdr.add(new Paragraph("INVENTARIO " + periodo).setFont(reg).setFontSize(6.5f)
                    .setFontColor(brandFg)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
                inner.addCell(hdr);

                // Etiqueta izquierda: DEPARTAMENTO
                inner.addCell(labelCell("DEPARTAMENTO", bold, labelBg, labelFg, borderClr));
                // Contenido derecho: nombre del bien
                String nombreBien = p.getNombre() != null ? p.getNombre().toUpperCase() : "—";
                String descBien   = p.getDescripcion() != null && !p.getDescripcion().isBlank()
                    ? p.getDescripcion().toUpperCase() : null;
                com.itextpdf.layout.element.Cell cNombre =
                    new com.itextpdf.layout.element.Cell()
                        .setBorder(new com.itextpdf.layout.borders.SolidBorder(borderClr, 0.4f))
                        .setPadding(5);
                cNombre.add(new Paragraph(nombreBien).setFont(bold).setFontSize(7.5f).setFontColor(grayFg));
                if (descBien != null)
                    cNombre.add(new Paragraph(descBien).setFont(reg).setFontSize(6.5f)
                        .setFontColor(new DeviceRgb(107, 114, 128)));
                inner.addCell(cNombre);

                // Área izquierda: valor departamento
                String areaTxt = p.getArea() != null && !p.getArea().isBlank()
                    ? p.getArea().toUpperCase() : "SIN ÁREA";
                inner.addCell(valueCell(areaTxt, reg, lightBg, grayFg, borderClr, 6.5f));

                // Descripción del bien + No. inventario (label derecha encabezado)
                inner.addCell(labelCell("DESCRIPCIÓN DEL BIEN Y No. DE INVENTARIO",
                    bold, labelBg, labelFg, borderClr));

                // Fila RESGUARDO
                inner.addCell(labelCell("RESGUARDO", bold, labelBg, labelFg, borderClr));

                // Marca / Modelo / Serie
                String marca  = nvl(p.getMarca(),  "SIN MARCA");
                String modelo = nvl(p.getModelo(), "SIN MODELO");
                String serie  = nvl(p.getNumeroSerie(), "SIN SERIE");
                String mms    = marca + "\n" + modelo + "   " + serie;
                inner.addCell(valueCell(mms, reg, lightBg, grayFg, borderClr, 7f));

                // Resguardante izquierdo
                String resguardanteTxt = p.getResguardante() != null && !p.getResguardante().isBlank()
                    ? p.getResguardante().toUpperCase() : "SIN RESGUARDANTE";
                inner.addCell(valueCell(resguardanteTxt, reg, lightBg, grayFg, borderClr, 6.5f));

                // Número de inventario grande (derecha)
                String codigo = p.getCodigo() != null ? p.getCodigo() : "—";
                com.itextpdf.layout.element.Cell cCodigo =
                    new com.itextpdf.layout.element.Cell()
                        .setBackgroundColor(lightBg)
                        .setBorder(new com.itextpdf.layout.borders.SolidBorder(borderClr, 0.4f))
                        .setPadding(8)
                        .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
                cCodigo.add(new Paragraph(codigo).setFont(bold).setFontSize(16)
                    .setFontColor(brandBg)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
                inner.addCell(cCodigo);

                card.add(inner);
                grid.addCell(card);
            }

            // Celdas vacías para completar última fila
            int rem = items.size() % 2;
            if (rem != 0)
                grid.addCell(new com.itextpdf.layout.element.Cell().setBorder(
                    com.itextpdf.layout.borders.Border.NO_BORDER));

            doc.add(grid);
        }
        return file;
    }

    private static com.itextpdf.layout.element.Cell labelCell(
            String text, PdfFont bold, DeviceRgb bg, DeviceRgb fg, DeviceRgb border) {
        return new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(text).setFont(bold).setFontSize(7).setFontColor(fg))
            .setBackgroundColor(bg)
            .setBorder(new com.itextpdf.layout.borders.SolidBorder(border, 0.4f))
            .setPadding(4);
    }

    private static com.itextpdf.layout.element.Cell valueCell(
            String text, PdfFont font, DeviceRgb bg, DeviceRgb fg, DeviceRgb border, float size) {
        return new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(text != null ? text : "—").setFont(font).setFontSize(size).setFontColor(fg))
            .setBackgroundColor(bg)
            .setBorder(new com.itextpdf.layout.borders.SolidBorder(border, 0.4f))
            .setPadding(4);
    }

    private static String nvl(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
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
