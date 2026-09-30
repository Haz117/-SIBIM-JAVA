package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
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
import com.sibim.util.FormatUtils;
import com.sibim.util.SupabaseStorage;

import java.io.File;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * "Solicitud de baja de bien patrimonial": the form an área fills in, signs and
 * sends to Recursos Materiales y Patrimonio to ask for a bien to be written off.
 * Same look as the ficha técnica (datos del bien + its photo), plus the parts
 * the área completes by hand: motivo, estado físico, documentos anexos, firmas.
 * It records nothing and burns no folio — Patrimonio assigns one on receipt and
 * the baja itself is still registered in SIBIM (which issues the Acta de baja).
 */
public class ReporteSolicitudBajaService extends ReporteService {

    private static final DeviceRgb DARK    = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb MUTED   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb BG_BAND = new DeviceRgb(248, 241, 242);   // guinda, very light
    private static final DeviceRgb BG_ALT  = new DeviceRgb(252, 248, 248);
    private static final DeviceRgb LINE    = new DeviceRgb(203, 213, 225);
    private static final DeviceRgb GUINDA_CLARO = new DeviceRgb(240, 195, 195);

    public ReporteSolicitudBajaService() { super(); }

    /** One bien → one page; several → one PDF with a page per bien. */
    public File exportSolicitudBaja(List<Producto> bienes) throws Exception {
        if (bienes == null || bienes.isEmpty())
            throw new IllegalArgumentException("Selecciona al menos un bien");
        if (bienes.size() == 1) return exportUna(bienes.get(0));

        File output = tempFile("solicitudes_baja_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        List<File> partes = new ArrayList<>();
        try (PdfWriter writer = new PdfWriter(output.getAbsolutePath());
             PdfDocument merged = new PdfDocument(writer)) {
            PdfMerger merger = new PdfMerger(merged);
            for (Producto p : bienes) {
                File parte = exportUna(p);
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

    private File exportUna(Producto p) throws Exception {
        String safe = p.getCodigo() != null ? p.getCodigo().replaceAll("[^a-zA-Z0-9_\\-]", "_") : "bien";
        File file = tempFile("solicitud_baja_" + safe, ".pdf");

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(26, 36, 26, 36);

            // ── Header band (logo + institution + title) ──
            Table header = new Table(new float[]{0.7f, 3f, 1.3f}).useAllAvailableWidth();
            Image logo = loadHeaderLogo();
            Cell logoCell = new Cell().setBackgroundColor(COLOR_HEADER).setPadding(10)
                .setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
            if (logo != null) logoCell.add(logo);
            header.addCell(logoCell);
            header.addCell(new Cell()
                .add(new Paragraph(orgName()).setFont(bold).setFontSize(10f).setFontColor(ColorConstants.WHITE).setMarginBottom(2))
                .add(new Paragraph("SOLICITUD DE BAJA DE BIEN PATRIMONIAL").setFont(bold).setFontSize(14f)
                    .setFontColor(ColorConstants.WHITE).setMarginBottom(1))
                .add(new Paragraph("Para entregar a Recursos Materiales y Patrimonio").setFont(regular).setFontSize(8f)
                    .setFontColor(GUINDA_CLARO))
                .setBackgroundColor(COLOR_HEADER).setPadding(10).setBorder(Border.NO_BORDER));
            header.addCell(new Cell()
                .add(new Paragraph("Folio: ____________").setFont(regular).setFontSize(8.5f)
                    .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT).setMarginBottom(6))
                .add(new Paragraph("Fecha: ____/____/______").setFont(regular).setFontSize(8.5f)
                    .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("(folio lo asigna Patrimonio)").setFont(regular).setFontSize(6.5f)
                    .setFontColor(GUINDA_CLARO).setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(COLOR_HEADER).setPadding(10).setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE));
            doc.add(header);
            doc.add(spacer(5));

            // ── Código + área solicitante ──
            Table band = new Table(new float[]{1f, 1.4f}).useAllAvailableWidth();
            band.addCell(bandCell("CÓDIGO DEL BIEN", p.getCodigo(), 17f, bold, regular));
            band.addCell(bandCell("ÁREA SOLICITANTE", p.getArea(), 11f, bold, regular));
            doc.add(band);
            doc.add(spacer(8));

            // ── Datos del bien (left) + fotografía (right) ──
            Table datosYFoto = new Table(new float[]{1.75f, 1f}).useAllAvailableWidth();
            Table datos = new Table(new float[]{1.25f, 2f}).useAllAvailableWidth();
            int i = 0;
            fila(datos, "Nombre / descripción", p.getNombre(), bold, regular, i++);
            fila(datos, "Categoría", p.getCategoriaNombre(), bold, regular, i++);
            fila(datos, "Marca", p.getMarca(), bold, regular, i++);
            fila(datos, "Modelo", p.getModelo(), bold, regular, i++);
            fila(datos, "N° de serie", p.getNumeroSerie(), bold, regular, i++);
            fila(datos, "Ubicación", p.getUbicacion(), bold, regular, i++);
            fila(datos, "Resguardante", p.getResguardante(), bold, regular, i++);
            fila(datos, "Fecha de adquisición",
                p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : null, bold, regular, i++);
            fila(datos, "Valor de adquisición", FormatUtils.formatCurrency(p.getPrecioCompra()), bold, regular, i++);
            if (p.getValorDepreciado() != null)
                fila(datos, "Valor actual (depreciado)", FormatUtils.formatCurrency(p.getValorDepreciado())
                    + (p.getPorcentajeDepreciado() != null ? "  (" + p.getPorcentajeDepreciado() + "%)" : ""),
                    bold, regular, i++);
            datosYFoto.addCell(new Cell().add(sectionTitle("DATOS DEL BIEN", bold, COLOR_HEADER)).add(datos)
                .setBorder(Border.NO_BORDER).setPaddingRight(10));
            datosYFoto.addCell(fotoCell(p, bold, regular));
            doc.add(datosYFoto);
            doc.add(spacer(8));

            // ── Motivo ──
            doc.add(sectionTitle("MOTIVO DE LA SOLICITUD  (marque uno)", bold, COLOR_HEADER));
            Table motivos = new Table(new float[]{1f, 1f, 1f}).useAllAvailableWidth();
            for (String m : new String[]{"Inservible / descompuesto", "Obsoleto", "Desgaste por uso",
                                         "Extravío", "Robo (anexar denuncia)", "Siniestro"}) {
                motivos.addCell(new Cell().add(new Paragraph("[   ]  " + m).setFont(regular).setFontSize(9f))
                    .setBorder(Border.NO_BORDER).setPadding(3));
            }
            doc.add(motivos);
            doc.add(new Paragraph("Otro: ________________________________________________________________________")
                .setFont(regular).setFontSize(9f).setMarginTop(2));
            doc.add(spacer(6));

            // ── Estado actual (to be written by hand) ──
            doc.add(sectionTitle("DESCRIPCIÓN DEL ESTADO ACTUAL DEL BIEN", bold, COLOR_HEADER));
            Table lineas = new Table(1).useAllAvailableWidth();
            for (int k = 0; k < 3; k++)
                lineas.addCell(new Cell().setHeight(18).setBorder(Border.NO_BORDER)
                    .setBorderBottom(new SolidBorder(LINE, 0.8f)));
            doc.add(lineas);
            doc.add(spacer(8));

            // ── Documentos anexos ──
            doc.add(sectionTitle("DOCUMENTOS QUE SE ANEXAN", bold, COLOR_HEADER));
            Table anexos = new Table(new float[]{1f, 1f}).useAllAvailableWidth();
            for (String a : new String[]{"Dictamen técnico del bien", "Fotografías del estado actual",
                                         "Copia del resguardo", "Acta circunstanciada / denuncia (robo o extravío)"}) {
                anexos.addCell(new Cell().add(new Paragraph("[   ]  " + a).setFont(regular).setFontSize(9f))
                    .setBorder(Border.NO_BORDER).setPadding(3));
            }
            doc.add(anexos);
            doc.add(spacer(26));

            // ── Firmas ──
            Table firmas = new Table(new float[]{1f, 1f, 1f}).useAllAvailableWidth();
            for (String[] f : new String[][]{
                    {"Solicita", "Titular del área"},
                    {"Entrega", "Resguardante del bien"},
                    {"Recibe", "Recursos Materiales y Patrimonio"}}) {
                firmas.addCell(new Cell()
                    .add(new Paragraph("___________________________").setFont(regular).setFontSize(10f)
                        .setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER))
                    .add(new Paragraph(f[0]).setFont(bold).setFontSize(9f).setFontColor(DARK)
                        .setTextAlignment(TextAlignment.CENTER))
                    .add(new Paragraph(f[1] + " · Nombre y firma").setFont(regular).setFontSize(7.5f)
                        .setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER))
                    .setBorder(Border.NO_BORDER).setPadding(4));
            }
            doc.add(firmas);

            doc.add(spacer(8));
            doc.add(new Paragraph("Esta solicitud no da de baja el bien por sí misma: Patrimonio la revisa y, "
                + "si procede, registra la baja en SIBIM, que emite el Acta de baja patrimonial.")
                .setFont(regular).setFontSize(7.5f).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("SIBIM — Sistema Integral de Bienes Municipales  |  " + orgName()
                + "  |  Generado el " + LocalDate.now().format(FMT))
                .setFont(regular).setFontSize(7.5f).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER));
        }
        return file;
    }

    private static Cell bandCell(String label, String value, float size, PdfFont bold, PdfFont regular) {
        return new Cell()
            .add(new Paragraph(label).setFont(regular).setFontSize(7.5f).setFontColor(MUTED).setMarginBottom(2))
            .add(new Paragraph(value != null && !value.isBlank() ? value : "—").setFont(bold).setFontSize(size).setFontColor(DARK))
            .setBackgroundColor(BG_BAND).setPadding(10).setBorder(Border.NO_BORDER);
    }

    private static void fila(Table t, String key, String value, PdfFont bold, PdfFont regular, int i) {
        DeviceRgb bg = i % 2 == 1 ? BG_ALT : new DeviceRgb(255, 255, 255);
        t.addCell(new Cell().add(new Paragraph(key).setFont(bold).setFontSize(8.5f).setFontColor(MUTED))
            .setBackgroundColor(bg).setPadding(5).setBorder(Border.NO_BORDER));
        t.addCell(new Cell().add(new Paragraph(value != null && !value.isBlank() ? value : "—")
                .setFont(regular).setFontSize(9f).setFontColor(DARK))
            .setBackgroundColor(bg).setPadding(5).setBorder(Border.NO_BORDER));
    }

    /** The bien's photo (main one, else the first of its gallery), or a framed
     *  space to paste one when it has none. */
    private Cell fotoCell(Producto p, PdfFont bold, PdfFont regular) {
        Cell c = new Cell().add(sectionTitle("FOTOGRAFÍA DEL BIEN", bold, COLOR_HEADER))
            .setBorder(Border.NO_BORDER).setPaddingLeft(4);
        Image img = cargarFoto(p);
        if (img != null) {
            img.setMaxWidth(170).setMaxHeight(190).setAutoScale(false)
               .setHorizontalAlignment(HorizontalAlignment.CENTER)
               .setBorder(new SolidBorder(LINE, 0.8f));
            c.add(img);
        } else {
            c.add(new Table(1).useAllAvailableWidth().addCell(new Cell()
                .add(new Paragraph("Sin fotografía registrada\n\nPegue aquí una foto actual del bien")
                    .setFont(regular).setFontSize(8.5f).setFontColor(MUTED).setTextAlignment(TextAlignment.CENTER))
                .setHeight(170).setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(LINE, 1f))));
        }
        return c;
    }

    private static Image cargarFoto(Producto p) {
        List<String> candidatas = new ArrayList<>();
        if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) candidatas.add(p.getFotoUrl());
        if (p.getFotosUrls() != null) candidatas.addAll(p.getFotosUrls());
        for (String url : candidatas) {
            try {
                ImageData data;
                if (SupabaseStorage.isRemoteUrl(url)) data = ImageDataFactory.create(URI.create(url).toURL());
                else if (new File(url).isFile()) data = ImageDataFactory.create(url);
                else continue;
                return new Image(data);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ReporteSolicitudBajaService.class)
                    .warn("No se pudo cargar la foto '{}' del bien {}: {}", url, p.getCodigo(), e.getMessage());
            }
        }
        return null;
    }
}
