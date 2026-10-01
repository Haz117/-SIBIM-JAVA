package com.sibim.service;

import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.model.Producto;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared layout of the one-page-per-bien baja formats (solicitud de baja,
 * dictamen técnico de baja): guinda header band with folio/fecha to fill in,
 * código + área band, datos del bien with its photo, checkbox grids, lines to
 * write on by hand and the signature row.
 */
abstract class ReporteFormatoBajaBase extends ReporteService {

    protected static final DeviceRgb DARK    = new DeviceRgb(15, 23, 42);
    protected static final DeviceRgb MUTED   = new DeviceRgb(100, 116, 139);
    protected static final DeviceRgb BG_BAND = new DeviceRgb(248, 241, 242);   // guinda, very light
    protected static final DeviceRgb BG_ALT  = new DeviceRgb(252, 248, 248);
    protected static final DeviceRgb LINE    = new DeviceRgb(203, 213, 225);
    protected static final DeviceRgb GUINDA_CLARO = new DeviceRgb(240, 195, 195);

    @FunctionalInterface
    protected interface Formato { File generar(Producto p) throws Exception; }

    /** One bien → its own PDF; several → one PDF with a page per bien. */
    protected File unaPaginaPorBien(String prefijo, List<Producto> bienes, Formato formato) throws Exception {
        if (bienes == null || bienes.isEmpty())
            throw new IllegalArgumentException("Selecciona al menos un bien");
        if (bienes.size() == 1) return formato.generar(bienes.get(0));

        File output = tempFile(prefijo + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        List<File> partes = new ArrayList<>();
        try (PdfWriter writer = new PdfWriter(output.getAbsolutePath());
             PdfDocument merged = new PdfDocument(writer)) {
            PdfMerger merger = new PdfMerger(merged);
            for (Producto p : bienes) {
                File parte = formato.generar(p);
                partes.add(parte);
                try (PdfDocument src = new PdfDocument(new PdfReader(parte.getAbsolutePath()))) {
                    merger.merge(src, 1, src.getNumberOfPages());
                }
            }
        } finally {
            partes.forEach(File::delete);
        }
        return output;
    }

    protected static String nombreArchivo(Producto p) {
        return p.getCodigo() != null ? p.getCodigo().replaceAll("[^a-zA-Z0-9_\\-]", "_") : "bien";
    }

    /** Logo + institution + title, and the folio/fecha blanks on the right. */
    protected void encabezado(Document doc, String titulo, String subtitulo, String notaFolio,
                              PdfFont bold, PdfFont regular) {
        Table header = new Table(new float[]{0.7f, 3f, 1.3f}).useAllAvailableWidth();
        Image logo = loadHeaderLogo();
        Cell logoCell = new Cell().setBackgroundColor(COLOR_HEADER).setPadding(10)
            .setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (logo != null) logoCell.add(logo);
        header.addCell(logoCell);
        header.addCell(new Cell()
            .add(new Paragraph(orgName()).setFont(bold).setFontSize(10f).setFontColor(ColorConstants.WHITE).setMarginBottom(2))
            .add(new Paragraph(titulo).setFont(bold).setFontSize(14f)
                .setFontColor(ColorConstants.WHITE).setMarginBottom(1))
            .add(new Paragraph(subtitulo).setFont(regular).setFontSize(8f)
                .setFontColor(GUINDA_CLARO))
            .setBackgroundColor(COLOR_HEADER).setPadding(10).setBorder(Border.NO_BORDER));
        header.addCell(new Cell()
            .add(new Paragraph("Folio: ____________").setFont(regular).setFontSize(8.5f)
                .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT).setMarginBottom(6))
            .add(new Paragraph("Fecha: ____/____/______").setFont(regular).setFontSize(8.5f)
                .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT))
            .add(new Paragraph(notaFolio).setFont(regular).setFontSize(6.5f)
                .setFontColor(GUINDA_CLARO).setTextAlignment(TextAlignment.RIGHT))
            .setBackgroundColor(COLOR_HEADER).setPadding(10).setBorder(Border.NO_BORDER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE));
        doc.add(header);
    }

    protected static Cell bandCell(String label, String value, float size, PdfFont bold, PdfFont regular) {
        return new Cell()
            .add(new Paragraph(label).setFont(regular).setFontSize(7.5f).setFontColor(MUTED).setMarginBottom(2))
            .add(new Paragraph(value != null && !value.isBlank() ? value : "—").setFont(bold).setFontSize(size).setFontColor(DARK))
            .setBackgroundColor(BG_BAND).setPadding(10).setBorder(Border.NO_BORDER);
    }

    protected static void fila(Table t, String key, String value, PdfFont bold, PdfFont regular, int i) {
        DeviceRgb bg = i % 2 == 1 ? BG_ALT : new DeviceRgb(255, 255, 255);
        t.addCell(new Cell().add(new Paragraph(key).setFont(bold).setFontSize(8.5f).setFontColor(MUTED))
            .setBackgroundColor(bg).setPadding(5).setBorder(Border.NO_BORDER));
        t.addCell(new Cell().add(new Paragraph(value != null && !value.isBlank() ? value : "—")
                .setFont(regular).setFontSize(9f).setFontColor(DARK))
            .setBackgroundColor(bg).setPadding(5).setBorder(Border.NO_BORDER));
    }

    /** "[   ]  opción" (or "[ X ]" when marcada) laid out in {@code columnas} columns. */
    protected static Table casillas(int columnas, PdfFont regular, List<String> opciones, String marcada) {
        float[] w = new float[columnas];
        java.util.Arrays.fill(w, 1f);
        Table t = new Table(w).useAllAvailableWidth().setFixedLayout();
        for (String o : opciones) {
            String caja = o.equals(marcada) ? "[ X ]  " : "[   ]  ";
            t.addCell(new Cell().add(new Paragraph(caja + o).setFont(regular).setFontSize(9f))
                .setBorder(Border.NO_BORDER).setPadding(3));
        }
        for (int k = opciones.size() % columnas; k != 0 && k < columnas; k++)
            t.addCell(new Cell().setBorder(Border.NO_BORDER));
        return t;
    }

    /** Ruled lines to write on by hand. */
    protected static Table renglones(int n, float alto) {
        Table lineas = new Table(1).useAllAvailableWidth();
        for (int k = 0; k < n; k++)
            lineas.addCell(new Cell().setHeight(alto).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(LINE, 0.8f)));
        return lineas;
    }

    /** {rol, cargo} per column: a line to sign on, the rol and "cargo · Nombre y firma". */
    protected static Table firmas(PdfFont bold, PdfFont regular, String[]... firmas) {
        float[] w = new float[firmas.length];
        java.util.Arrays.fill(w, 1f);
        Table t = new Table(w).useAllAvailableWidth().setFixedLayout();
        for (String[] f : firmas) {
            t.addCell(new Cell()
                .add(new Paragraph("___________________________").setFont(regular).setFontSize(10f)
                    .setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(f[0]).setFont(bold).setFontSize(9f).setFontColor(DARK)
                    .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(f[1] + " · Nombre y firma").setFont(regular).setFontSize(7.5f)
                    .setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER))
                .setBorder(Border.NO_BORDER).setPadding(4));
        }
        return t;
    }

    protected void pie(Document doc, String nota, PdfFont regular) {
        doc.add(new Paragraph(nota)
            .setFont(regular).setFontSize(7.5f).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph("SIBIM — Sistema Integral de Bienes Municipales  |  " + orgName()
            + "  |  Generado el " + LocalDate.now().format(FMT))
            .setFont(regular).setFontSize(7.5f).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));
    }

    /** The bien's photo (main one, else the first of its gallery), or a framed
     *  space to paste one when it has none. */
    protected Cell fotoCell(Producto p, PdfFont bold, PdfFont regular, float maxAlto) {
        Cell c = new Cell().add(sectionTitle("FOTOGRAFÍA DEL BIEN", bold, COLOR_HEADER))
            .setBorder(Border.NO_BORDER).setPaddingLeft(4);
        Image img = cargarFoto(p);
        if (img != null) {
            img.setMaxWidth(170).setMaxHeight(maxAlto).setAutoScale(false)
               .setHorizontalAlignment(HorizontalAlignment.CENTER)
               .setBorder(new SolidBorder(LINE, 0.8f));
            c.add(img);
        } else {
            c.add(new Table(1).useAllAvailableWidth().addCell(new Cell()
                // No id = a blank format, not a bien that lacks a photo.
                .add(new Paragraph((p.getId() != null ? "Sin fotografía registrada\n\n" : "")
                        + "Pegue aquí una foto actual del bien")
                    .setFont(regular).setFontSize(8.5f).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER))
                .setHeight(maxAlto - 20).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(LINE, 1f))));
        }
        return c;
    }

    private static Image cargarFoto(Producto p) {
        List<ImageData> fotos = FotosPdf.de(p, 1, 1000);
        return fotos.isEmpty() ? null : new Image(fotos.get(0));
    }
}
