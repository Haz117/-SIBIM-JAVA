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
import com.itextpdf.layout.borders.DashedBorder;
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

    /** Height of each QR label: four rows fit an A4 sheet with its footer. */
    static final float QR_ALTO = 170f;
    /** Side of the QR on those labels. */
    private static final float QR_LADO = 86f;

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
            // 27-nov-2026 («los QR se ven sin diseño»): each column took the width its text asked for —
            // one label 560 px wide beside two of 180, QRs of different sizes, names of five lines.
            // They are cut and stuck on the bienes: now all the same, three per row and twelve per
            // sheet, with a dashed line to cut along, the número large in guinda as on the official one.
            Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth();
            grid.setMarginBottom(0);
            String municipio = config("municipio", "").replaceAll(",.*$", "").trim().toUpperCase();
            String arriba = (municipio.isEmpty() ? "" : municipio + " · ") + "BIENES MUNICIPALES";

            // Each QR downloads the bien's ficha (published now, so it is current);
            // without Storage it carries the data the label does not show.
            java.util.Map<String, String> contenidos = FichaPublica.publicar(items);
            for (Producto p : items) {
                String contenido = contenidos.get(p.getId());
                if (contenido == null) contenido = com.sibim.util.QrUtils.contenidoBien(p);
                byte[] qrBytes = qrToPngBytes(contenido, 200);

                Cell card = new Cell().setHeight(QR_ALTO).setPadding(8).setKeepTogether(true)
                    .setBorder(new DashedBorder(new DeviceRgb(170, 178, 189), 0.6f))
                    .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.TOP);
                card.add(new Paragraph(arriba).setFont(bold).setFontSize(5.8f).setCharacterSpacing(0.3f)
                    .setFontColor(new DeviceRgb(100, 116, 139)).setMargin(0));
                if (qrBytes != null)
                    card.add(new Image(ImageDataFactory.create(qrBytes)).setAutoScale(false).setWidth(QR_LADO)
                        .setHeight(QR_LADO).setHorizontalAlignment(HorizontalAlignment.CENTER).setMarginTop(3));
                card.add(new Paragraph(p.getCodigo() != null ? p.getCodigo() : "—").setFont(bold).setFontSize(15f)
                    .setFontColor(headerBg).setMargin(0).setMarginTop(2).setMultipliedLeading(1f));
                card.add(new Paragraph(recortar(mayus(p.getNombre(), "SIN DESCRIPCIÓN"), 95)).setFont(bold)
                    .setFontSize(7f).setFontColor(new DeviceRgb(15, 23, 42)).setMultipliedLeading(1.15f)
                    .setMargin(0).setMarginTop(2));
                if (p.getArea() != null && !p.getArea().isBlank())
                    card.add(new Paragraph(recortar(p.getArea().trim(), 48)).setFont(regular).setFontSize(6.5f)
                        .setFontColor(new DeviceRgb(100, 116, 139)).setMargin(0).setMarginTop(2));
                card.add(new Paragraph(contenido.startsWith("http") ? "Escanéalo para ver su ficha"
                        : "Escanéalo para ver quién lo tiene").setFont(regular).setFontSize(5.8f)
                    .setFontColor(new DeviceRgb(148, 163, 184)).setMargin(0).setMarginTop(3));
                grid.addCell(card);
            }
            int rem = items.size() % 3;
            if (rem != 0) for (int i = rem; i < 3; i++)
                grid.addCell(new Cell().setBorder(Border.NO_BORDER).setHeight(QR_ALTO));

            doc.add(grid);
            addPdfFooter(doc, items.size());
        }
        return numerarPaginas(file);
    }

    // ── Etiqueta física (formato oficial) ────────────────────────────────────

    // Medidas y colores de la etiqueta oficial del municipio (el ejemplo
    // impreso de Tesorería): 6.9 × 6.6 cm, con la columna izquierda de un tercio.
    private static final float ETQ_ANCHO   = 195f;
    private static final float ETQ_COL_IZQ = 65f;
    private static final float ETQ_ENCABEZADO = 40f, ETQ_TITULOS = 29f,
                               ETQ_DEPARTAMENTO = 35f, ETQ_RESGUARDO = 32f, ETQ_NUMERO = 50f;
    /** Padding of every cell of the label; the row heights above include it. */
    private static final float ETQ_PAD = 3f;
    /** Side of the QR in the header; its content is kept short (QrUtils.contenidoEtiqueta) so it scans at this size. */
    private static final float ETQ_QR = 33f;
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

            doc.setMargins(8, 12, 8, 12);
            // Dashed guides between the labels show where to cut.
            DashedBorder guia = new DashedBorder(new DeviceRgb(190, 190, 190), 0.4f);
            Table hoja = new Table(UnitValue.createPointArray(new float[]{ETQ_ANCHO + 12, ETQ_ANCHO + 12}))
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
            for (int i = 0; i < items.size(); i++) {
                Cell c = new Cell().add(etiqueta(items.get(i), logo, municipio, periodo, bold, reg))
                    .setBorder(Border.NO_BORDER).setPaddingTop(1.5f).setPaddingBottom(1.5f)
                    .setPaddingLeft(6).setPaddingRight(6).setKeepTogether(true);
                if (i % 2 == 0) c.setBorderRight(guia);
                if (i % 8 < 6 && i + 2 < items.size() + items.size() % 2) c.setBorderBottom(guia);
                hoja.addCell(c);
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

        String codigo = p.getCodigo() != null && !p.getCodigo().isBlank() ? p.getCodigo().trim() : "—";

        // Encabezado: logo | municipio y periodo | QR del bien
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
        Cell cQr = sinBorde();
        // Código on the first line (all SIBIM's scanner reads), then what it is and who has it:
        // the most a QR this small can carry and still be read by a phone.
        byte[] qr = "—".equals(codigo) ? null : qrToPngBytes(com.sibim.util.QrUtils.contenidoEtiqueta(p), 330);
        if (qr != null)
            cQr.add(new Image(ImageDataFactory.create(qr)).setAutoScale(false).setWidth(ETQ_QR).setHeight(ETQ_QR)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
        enc.addCell(cQr);
        t.addCell(celda(1, 2, ETQ_ENCABEZADO).setPadding(1).setHeight(ETQ_ENCABEZADO - 3.6f).add(enc));

        String[] depRes = departamentoYResguardo(p.getArea());

        // Títulos
        t.addCell(celda(1, 1, ETQ_TITULOS).setBackgroundColor(ETQ_ROJO)
            .add(texto("DEPARTAMENTO", bold, 6.5f, ColorConstants.WHITE)));
        t.addCell(celda(1, 1, ETQ_TITULOS)
            .add(texto("DESCRIPCIÓN DEL BIEN Y No. DE INVENTARIO", bold, 5.8f, ETQ_TEXTO)));

        // Departamento | descripción, marca, modelo y serie (ocupa dos filas)
        t.addCell(celda(1, 1, ETQ_DEPARTAMENTO).add(texto(depRes[0], reg, tamArea(depRes[0]), ETQ_TEXTO)));

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
            .add(texto("RESGUARDO", bold, 6.5f, ColorConstants.WHITE)));

        // Resguardo | número de inventario
        t.addCell(celda(1, 1, ETQ_NUMERO).add(texto(depRes[1], reg, tamArea(depRes[1]), ETQ_TEXTO)));
        t.addCell(celda(1, 1, ETQ_NUMERO)
            .add(texto(codigo, bold, tamQueCabe(bold, codigo, ETQ_ANCHO - ETQ_COL_IZQ - 10, 19f), ETQ_NUMERO_ROJO)));
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

    /** A cell exactly {@code alto} points tall, padding included: the eight labels
     *  of a sheet are all the same size whatever they say. */
    private static Cell celda(int filas, int columnas, float alto) {
        return new Cell(filas, columnas).setHeight(alto - 2 * ETQ_PAD - 1.6f).setPadding(ETQ_PAD)
            .setBorder(new SolidBorder(ETQ_LINEA, 0.8f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Cell sinBorde() {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Paragraph texto(String s, PdfFont font, float tam, com.itextpdf.kernel.colors.Color color) {
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
        return largo <= 45 ? 7.5f : largo <= 90 ? 6.5f : largo <= 170 ? 5.5f : 4.8f;
    }

    /** Área names go from "TESORERÍA" to four lines of secretaría y dirección. */
    private static float tamArea(String area) {
        return area.length() <= 30 ? 5.5f : area.length() <= 55 ? 5f : 4.4f;
    }

    /** The largest size up to {@code max} at which {@code s} fits on one line of {@code ancho}. */
    private static float tamQueCabe(PdfFont font, String s, float ancho, float max) {
        float tam = max;
        while (tam > 8f && font.getWidth(s, tam) > ancho) tam -= 0.5f;
        return tam;
    }

    /** The QR as a PNG of at least {@code size} px a side, each module a whole number of pixels
     *  and only its one-module quiet zone around it. Asked for {@code size} px directly, ZXing left
     *  what didn't divide evenly as more white border: on a sheet of labels, each scaled to the same
     *  square, the QRs came out of different sizes, and on the official label's 1.2 cm the code
     *  itself got smaller than its box (27-nov-2026). */
    static byte[] qrToPngBytes(String content, int size) {
        if (content == null || content.isBlank()) return null;
        try {
            BitMatrix modulos = new MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, 0, 0,
                java.util.Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
            int n = modulos.getWidth(), k = Math.max(1, (int) Math.ceil(size / (double) n)), lado = n * k;
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(lado, lado,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < lado; x++)
                for (int y = 0; y < lado; y++)
                    img.setRGB(x, y, modulos.get(x / k, y / k) ? 0x000000 : 0xFFFFFF);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("No se pudo generar el QR para '{}' — la etiqueta se imprimirá sin código", content, e);
            return null;
        }
    }
}
