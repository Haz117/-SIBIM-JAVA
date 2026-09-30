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
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.model.Producto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class ReporteEtiquetasService extends ReporteService {

    private static final Logger log = LoggerFactory.getLogger(ReporteEtiquetasService.class);

    public ReporteEtiquetasService() { super(); }

    public File exportEtiquetasQrPdf(List<Producto> productos) throws Exception {
        if (productos.isEmpty()) return null;
        List<Producto> items = productos.size() > 200 ? productos.subList(0, 200) : productos;
        File file = tempFile("etiquetas_qr", ".pdf");
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerBg = new DeviceRgb(162, 35, 45);

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

    // ── Etiqueta física (formato oficial) ────────────────────────────────────

    // Medidas y colores de la etiqueta oficial del municipio (el ejemplo
    // impreso de Tesorería): 6.9 × 6.6 cm, con la columna izquierda de un tercio.
    private static final float ETQ_ANCHO   = 195f;
    private static final float ETQ_COL_IZQ = 65f;
    private static final float ETQ_ENCABEZADO = 40f, ETQ_TITULOS = 29f,
                               ETQ_DEPARTAMENTO = 35f, ETQ_RESGUARDO = 32f, ETQ_NUMERO = 50f;
    private static final DeviceRgb ETQ_ROJO        = new DeviceRgb(192, 80, 77);
    private static final DeviceRgb ETQ_LINEA       = new DeviceRgb(38, 38, 38);
    private static final DeviceRgb ETQ_TEXTO       = new DeviceRgb(30, 30, 30);
    private static final DeviceRgb ETQ_NUMERO_ROJO = new DeviceRgb(200, 40, 60);

    /**
     * Etiquetas físicas con el formato oficial: encabezado con el logo, el
     * municipio y el periodo del inventario; DEPARTAMENTO (la secretaría) y
     * RESGUARDO (la dirección que tiene el bien) a la izquierda; descripción,
     * marca, modelo y serie a la derecha, y el número de inventario en grande.
     * Ocho por hoja carta, todas del mismo tamaño para recortarlas.
     */
    public File exportEtiquetaFisicaPdf(List<Producto> productos) throws Exception {
        if (productos.isEmpty()) return null;
        List<Producto> items = productos.size() > 300 ? productos.subList(0, 300) : productos;

        File file = tempFile("etiquetas_fisicas", ".pdf");
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        String municipio = municipioEtiqueta(config("municipio", "Ixmiquilpan, Hidalgo"));
        String periodo   = "INVENTARIO " + config("periodo_inventario", "2024 - 2027");
        byte[] logo      = leerLogo();

        try (PdfWriter  writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document    doc    = new Document(pdfDoc, PageSize.LETTER)) {

            doc.setMargins(12, 12, 12, 12);
            Table hoja = new Table(UnitValue.createPointArray(new float[]{ETQ_ANCHO + 8, ETQ_ANCHO + 8}))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
            for (Producto p : items) {
                hoja.addCell(new Cell().add(etiqueta(p, logo, municipio, periodo, bold, reg))
                    .setBorder(Border.NO_BORDER).setPaddingTop(2).setPaddingBottom(2)
                    .setPaddingLeft(4).setPaddingRight(4).setKeepTogether(true));
            }
            if (items.size() % 2 != 0) hoja.addCell(new Cell().setBorder(Border.NO_BORDER));
            doc.add(hoja);
        }
        return file;
    }

    private Table etiqueta(Producto p, byte[] logo, String municipio, String periodo,
                           PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPointArray(new float[]{ETQ_COL_IZQ, ETQ_ANCHO - ETQ_COL_IZQ}))
            .setFixedLayout().setWidth(ETQ_ANCHO)
            .setBorder(new SolidBorder(ETQ_ROJO, 1.8f));

        // Encabezado: logo | municipio y periodo | espacio que deja el texto centrado
        Table enc = new Table(UnitValue.createPercentArray(new float[]{22, 56, 22})).useAllAvailableWidth();
        Cell cLogo = sinBorde();
        if (logo != null) {
            try {
                cLogo.add(new Image(ImageDataFactory.create(logo)).scaleToFit(36, 32)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
            } catch (Exception e) {
                log.warn("El logo municipal no se pudo poner en la etiqueta: {}", e.getMessage());
            }
        }
        enc.addCell(cLogo);
        enc.addCell(sinBorde()
            .add(texto(municipio, bold, 8.5f, ETQ_TEXTO))
            .add(texto(periodo, bold, 8.5f, ETQ_TEXTO)));
        enc.addCell(sinBorde());
        t.addCell(celda(1, 2, ETQ_ENCABEZADO).setPadding(2).add(enc));

        String[] depRes = departamentoYResguardo(p.getArea());

        // Títulos
        t.addCell(celda(1, 1, ETQ_TITULOS).setBackgroundColor(ETQ_ROJO)
            .add(texto("DEPARTAMENTO", bold, 6.5f, ETQ_TEXTO)));
        t.addCell(celda(1, 1, ETQ_TITULOS)
            .add(texto("DESCRIPCIÓN DEL BIEN Y No. DE INVENTARIO", bold, 5.8f, ETQ_TEXTO)));

        // Departamento | descripción, marca, modelo y serie (ocupa dos filas)
        t.addCell(celda(1, 1, ETQ_DEPARTAMENTO).add(texto(depRes[0], reg, 5.5f, ETQ_TEXTO)));

        String descripcion = recortar(mayus(p.getNombre(), "SIN DESCRIPCIÓN"), 230);
        Cell cDesc = celda(2, 1, ETQ_DEPARTAMENTO + ETQ_RESGUARDO)
            .add(texto(descripcion, reg, tamDescripcion(descripcion.length()), ETQ_TEXTO))
            .add(texto(mayus(p.getMarca(), "SIN MARCA"), reg, 5.5f, ETQ_TEXTO).setMarginTop(3));
        Table modeloSerie = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        modeloSerie.setMarginTop(3);
        String modelo = mayus(p.getModelo(), "SIN MODELO");
        String serie  = mayus(p.getNumeroSerie(), "SIN SERIE");
        float tamMs = Math.max(modelo.length(), serie.length()) > 14 ? 6f : 7.5f;
        modeloSerie.addCell(sinBorde().add(texto(modelo, reg, tamMs, ETQ_TEXTO)));
        modeloSerie.addCell(sinBorde().add(texto(serie, reg, tamMs, ETQ_TEXTO)));
        t.addCell(cDesc.add(modeloSerie));

        t.addCell(celda(1, 1, ETQ_RESGUARDO).setBackgroundColor(ETQ_ROJO)
            .add(texto("RESGUARDO", bold, 6.5f, ETQ_TEXTO)));

        // Resguardo | número de inventario
        t.addCell(celda(1, 1, ETQ_NUMERO).add(texto(depRes[1], reg, 5.5f, ETQ_TEXTO)));
        String codigo = p.getCodigo() != null && !p.getCodigo().isBlank() ? p.getCodigo().trim() : "—";
        t.addCell(celda(1, 1, ETQ_NUMERO)
            .add(texto(codigo, bold, tamQueCabe(bold, codigo, ETQ_ANCHO - ETQ_COL_IZQ - 10, 17f), ETQ_NUMERO_ROJO)));
        return t;
    }

    /** DEPARTAMENTO es la secretaría (o Presidencia) de la que depende el área;
     *  RESGUARDO es el área misma. Un área que no depende de otra va en ambos. */
    static String[] departamentoYResguardo(String area) {
        if (area == null || area.isBlank()) return new String[]{"SIN ÁREA", "SIN ÁREA"};
        String a = area.trim();
        String departamento = Areas.catalogo().buscar(a)
            .map(AreaCatalog.Entrada::padre)
            .filter(padre -> padre != null && !padre.isBlank())
            .orElse(a);
        return new String[]{departamento.toUpperCase(), a.toUpperCase()};
    }

    /** "Ixmiquilpan, Hidalgo" → "IXMIQUILPAN, HGO.", como en el formato impreso. */
    static String municipioEtiqueta(String municipio) {
        return municipio.trim().toUpperCase().replaceAll(",\\s*HIDALGO\\.?$", ", HGO.");
    }

    private byte[] leerLogo() {
        String ruta = logoPath();
        if (ruta == null) return null;
        try {
            return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(ruta));
        } catch (Exception e) {
            log.warn("No se pudo leer el logo municipal '{}' para las etiquetas: {}", ruta, e.getMessage());
            return null;
        }
    }

    private static Cell celda(int filas, int columnas, float alto) {
        return new Cell(filas, columnas).setMinHeight(alto).setPadding(3)
            .setBorder(new SolidBorder(ETQ_LINEA, 0.8f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Cell sinBorde() {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Paragraph texto(String s, PdfFont font, float tam, DeviceRgb color) {
        return new Paragraph(s).setFont(font).setFontSize(tam).setFontColor(color)
            .setMultipliedLeading(1.1f).setMargin(0).setTextAlignment(TextAlignment.CENTER);
    }

    private static String mayus(String s, String siVacio) {
        return s != null && !s.isBlank() ? s.trim().toUpperCase() : siVacio;
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    /** Descriptions run from "SILLA" to a full paragraph; the cell stays the same size. */
    private static float tamDescripcion(int largo) {
        return largo <= 90 ? 7f : largo <= 170 ? 6f : 5f;
    }

    /** The largest size up to {@code max} at which {@code s} fits on one line of {@code ancho}. */
    private static float tamQueCabe(PdfFont font, String s, float ancho, float max) {
        float tam = max;
        while (tam > 8f && font.getWidth(s, tam) > ancho) tam -= 0.5f;
        return tam;
    }

    static byte[] qrToPngBytes(String content, int size) {
        if (content == null || content.isBlank()) return null;
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
        } catch (Exception e) {
            log.warn("No se pudo generar el QR para '{}' — la etiqueta se imprimirá sin código", content, e);
            return null;
        }
    }
}
