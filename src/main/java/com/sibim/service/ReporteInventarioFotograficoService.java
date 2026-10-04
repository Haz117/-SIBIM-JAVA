package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
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
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Inventario fotográfico de bienes muebles — the municipality's "MLA" format
 * (INV.DIG MLA/&lt;área&gt;/MLA.docx): DESCRIPCIÓN | NO. DE INVENTARIO |
 * FOTOGRAFÍA DE BIEN MUEBLE, one bien per row with its photo, now generated
 * from SIBIM instead of pasting photos into Word by hand. Bienes are grouped by
 * área; the header and column titles repeat on every sheet.
 */
public class ReporteInventarioFotograficoService extends ReporteService {

    private static final DeviceRgb GUINDA    = new DeviceRgb(157, 23, 45);   // #9D172D, as in MLA.docx
    private static final DeviceRgb GUINDA_CL = new DeviceRgb(252, 240, 241);
    private static final DeviceRgb ROSA      = new DeviceRgb(240, 195, 195);
    private static final DeviceRgb DORADO    = new DeviceRgb(196, 165, 93);
    private static final DeviceRgb OSCURO    = new DeviceRgb(17, 24, 39);
    private static final DeviceRgb TENUE     = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb FONDO     = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb LINEA     = new DeviceRgb(156, 163, 175);

    private static final int MAX_BIENES = 500;
    private static final float ALTO_FILA = 128f;

    public ReporteInventarioFotograficoService() { super(); }

    public File exportInventarioFotografico(List<Producto> seleccion) throws Exception {
        if (seleccion == null || seleccion.isEmpty())
            throw new IllegalArgumentException("Selecciona al menos un bien para el inventario fotográfico");
        List<Producto> bienes = new ArrayList<>(seleccion.size() > MAX_BIENES ? seleccion.subList(0, MAX_BIENES) : seleccion);
        bienes.sort(Comparator.comparing((Producto p) -> Objects.requireNonNullElse(p.getArea(), "~"))
            .thenComparing(p -> Objects.requireNonNullElse(p.getCodigo(), "")));
        Map<String, ImageData> fotos = FotosPdf.principales(bienes, 700);

        File file = tempFile("inventario_fotografico", ".pdf");
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        long areas = bienes.stream().map(p -> Objects.requireNonNullElse(p.getArea(), "")).distinct().count();
        String areaTitulo = areas == 1 && bienes.get(0).getArea() != null && !bienes.get(0).getArea().isBlank()
            ? bienes.get(0).getArea().toUpperCase() : "VARIAS ÁREAS";

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             // Not flushed page by page: the footer of each sheet is drawn at the end,
             // once the total is known ("Cannot draw elements on already flushed pages").
             Document doc = new Document(pdfDoc, PageSize.LETTER, false)) {

            doc.setMargins(24, 30, 30, 30);
            Table t = new Table(UnitValue.createPercentArray(new float[]{40, 22, 38})).useAllAvailableWidth();

            // Encabezado que se repite en cada hoja
            t.addHeaderCell(encabezado(areaTitulo, bienes.size(), bold, reg));
            t.addHeaderCell(new Cell(1, 3).setHeight(3).setPadding(0).setBackgroundColor(DORADO).setBorder(Border.NO_BORDER));
            t.addHeaderCell(new Cell(1, 3).setHeight(6).setPadding(0).setBorder(Border.NO_BORDER));
            for (String h : new String[]{"DESCRIPCIÓN", "NO. DE INVENTARIO", "FOTOGRAFÍA DE BIEN MUEBLE"})
                t.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(8.5f).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(GUINDA).setPadding(6).setTextAlignment(TextAlignment.CENTER)
                    .setBorder(new SolidBorder(GUINDA, 0.8f)));

            String areaActual = null;
            for (Producto p : bienes) {
                String area = p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área";
                if (areas > 1 && !area.equals(areaActual)) {
                    String[] depRes = ReporteEtiquetasService.departamentoYResguardo(area);
                    String sub = depRes[0].equals(depRes[1]) ? depRes[1] : depRes[1] + "  ·  " + depRes[0];
                    t.addCell(new Cell(1, 3).add(new Paragraph(sub).setFont(bold).setFontSize(8f).setFontColor(GUINDA))
                        .setBackgroundColor(GUINDA_CL).setPadding(5).setPaddingLeft(8)
                        .setBorder(new SolidBorder(LINEA, 0.6f)).setKeepWithNext(true));
                    areaActual = area;
                }
                t.addCell(descripcion(p, bold, reg));
                t.addCell(numero(p, bold, reg));
                t.addCell(foto(fotos.get(p.getId()), reg));
            }
            doc.add(t);
            doc.add(firmas(bold, reg));

            String pie = "SIBIM · " + orgName() + " · Emitido el " + FormatUtils.formatDateTime(LocalDateTime.now());
            int paginas = pdfDoc.getNumberOfPages();
            float centro = PageSize.LETTER.getWidth() / 2;
            for (int i = 1; i <= paginas; i++)
                doc.showTextAligned(new Paragraph(pie + " · Hoja " + i + " de " + paginas)
                        .setFont(reg).setFontSize(6.5f).setFontColor(TENUE),
                    centro, 14, i, TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0);
        }
        return file;
    }

    private Cell encabezado(String area, int total, PdfFont bold, PdfFont reg) {
        Table enc = new Table(UnitValue.createPercentArray(new float[]{13, 62, 25})).useAllAvailableWidth();
        Cell logo = new Cell().setBorder(Border.NO_BORDER).setPadding(7).setVerticalAlignment(VerticalAlignment.MIDDLE);
        Image img = loadHeaderLogo();
        if (img != null)
            logo.add(new Table(1).useAllAvailableWidth().addCell(new Cell()
                .add(img.setMaxHeight(40).setMaxWidth(50).setHorizontalAlignment(HorizontalAlignment.CENTER))
                .setBackgroundColor(ColorConstants.WHITE).setPadding(3).setBorder(Border.NO_BORDER)));
        enc.addCell(logo);
        enc.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(8).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .add(new Paragraph(orgName()).setFont(bold).setFontSize(8.5f).setFontColor(ColorConstants.WHITE).setMargin(0))
            .add(new Paragraph("INVENTARIO FOTOGRÁFICO DE BIENES MUEBLES").setFont(bold).setFontSize(12f)
                .setFontColor(ColorConstants.WHITE).setMargin(0).setMarginTop(2))
            .add(new Paragraph(area).setFont(bold).setFontSize(8.5f).setFontColor(ROSA).setMargin(0).setMarginTop(2)));
        enc.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(8).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setTextAlignment(TextAlignment.RIGHT)
            .add(new Paragraph("INVENTARIO " + config("periodo_inventario", "2024 - 2027")).setFont(bold).setFontSize(7f)
                .setFontColor(ROSA).setMargin(0))
            .add(new Paragraph(LocalDate.now().format(FMT)).setFont(bold).setFontSize(10f)
                .setFontColor(ColorConstants.WHITE).setMargin(0))
            .add(new Paragraph(total + (total == 1 ? " bien" : " bienes")).setFont(reg).setFontSize(7.5f)
                .setFontColor(ROSA).setMargin(0)));
        return new Cell(1, 3).add(enc).setBackgroundColor(GUINDA).setPadding(0).setBorder(Border.NO_BORDER);
    }

    private static Cell descripcion(Producto p, PdfFont bold, PdfFont reg) {
        Cell c = fila().setPadding(8);
        c.add(new Paragraph(mayus(p.getNombre(), "SIN DESCRIPCIÓN")).setFont(bold).setFontSize(9f).setFontColor(OSCURO)
            .setMultipliedLeading(1.15f).setMargin(0));
        List<String> detalles = new ArrayList<>();
        if (hay(p.getMarca()))       detalles.add("Marca: " + p.getMarca().trim());
        if (hay(p.getModelo()))      detalles.add("Modelo: " + p.getModelo().trim());
        if (hay(p.getNumeroSerie())) detalles.add("Serie: " + p.getNumeroSerie().trim());
        if (hay(p.getColor()))       detalles.add("Color: " + p.getColor().trim());
        if (!detalles.isEmpty())
            c.add(new Paragraph(String.join("   ·   ", detalles)).setFont(reg).setFontSize(7.5f).setFontColor(TENUE)
                .setMargin(0).setMarginTop(5));
        if (hay(p.getEstadoFisico()))
            c.add(new Paragraph("Estado físico: " + p.getEstadoFisico().trim()).setFont(reg).setFontSize(7.5f)
                .setFontColor(TENUE).setMargin(0).setMarginTop(2));
        if (hay(p.getResguardante()))
            c.add(new Paragraph("Resguardante: " + p.getResguardante().trim()).setFont(reg).setFontSize(7.5f)
                .setFontColor(TENUE).setMargin(0).setMarginTop(2));
        return c;
    }

    private static Cell numero(Producto p, PdfFont bold, PdfFont reg) {
        Cell c = fila().setPadding(6).setTextAlignment(TextAlignment.CENTER);
        String codigo = hay(p.getCodigo()) ? p.getCodigo().trim() : "—";
        c.add(new Paragraph(codigo).setFont(bold).setFontSize(codigo.length() > 12 ? 10.5f : 13f)
            .setFontColor(GUINDA).setMargin(0));
        if (hay(p.getUbicacion()))
            c.add(new Paragraph(p.getUbicacion().trim()).setFont(reg).setFontSize(7f).setFontColor(TENUE)
                .setMargin(0).setMarginTop(4));
        return c;
    }

    private static Cell foto(ImageData img, PdfFont reg) {
        Cell c = fila().setPadding(5).setTextAlignment(TextAlignment.CENTER);
        if (img != null) {
            c.add(new Image(img).scaleToFit(190, ALTO_FILA - 4).setHorizontalAlignment(HorizontalAlignment.CENTER));
        } else {
            c.setBackgroundColor(FONDO).add(new Paragraph("Sin fotografía registrada").setFont(reg).setFontSize(8f)
                .setFontColor(TENUE).setMargin(0));
        }
        return c;
    }

    private static Table firmas(PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth()
            .setMarginTop(28).setKeepTogether(true);
        for (String[] f : new String[][]{
                {"RESPONSABLE DEL ÁREA", "Nombre y firma"},
                {"ELABORÓ", "Recursos Materiales y Patrimonio"},
                {"VO. BO.", "Nombre y firma"}}) {
            t.addCell(new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(12).setPaddingRight(12)
                .add(new Paragraph(f[0]).setFont(bold).setFontSize(7.5f).setFontColor(OSCURO)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(24))
                .add(new Table(1).useAllAvailableWidth().addCell(new Cell().setHeight(1)
                    .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(OSCURO, 0.7f))))
                .add(new Paragraph(f[1]).setFont(reg).setFontSize(7.5f).setFontColor(TENUE)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(2)));
        }
        return t;
    }

    private static Cell fila() {
        return new Cell().setMinHeight(ALTO_FILA).setBorder(new SolidBorder(LINEA, 0.6f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE).setKeepTogether(true);
    }

    private static boolean hay(String s) { return s != null && !s.isBlank(); }

    private static String mayus(String s, String siVacio) { return hay(s) ? s.trim().toUpperCase() : siVacio; }
}
