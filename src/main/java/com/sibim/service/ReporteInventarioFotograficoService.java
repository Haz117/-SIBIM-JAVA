package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.AreaBreakType;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.model.Producto;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventario fotográfico de bienes muebles — the municipality's "inventario
 * digital" Word format (INV.DIG MLA/&lt;área&gt;/MLA.docx): a sheet framed in
 * guinda with the municipal logo and the 2024-2027 government logo at the top
 * and the watermark at the bottom left; DESCRIPCIÓN | NO. DE INVENTARIO |
 * FOTOGRAFÍA DE BIEN MUEBLE, three bienes per sheet. Each área starts on its
 * own sheet; the área and the sheet number go in a small line at the foot.
 * Without photos, an área's inventory is the Inventario General of its bienes
 * ({@link ReporteService#exportInventarioSinFotos}).
 */
public class ReporteInventarioFotograficoService extends ReporteService {

    private static final DeviceRgb GUINDA = new DeviceRgb(157, 23, 45);   // #9D172D, as in MLA.docx
    private static final DeviceRgb MARCO  = new DeviceRgb(150, 0, 0);     // #960000, the page border
    private static final DeviceRgb OSCURO = new DeviceRgb(17, 24, 39);
    private static final DeviceRgb TENUE  = new DeviceRgb(107, 114, 128);

    private static final int MAX_BIENES = 500;
    /** Three rows and the header fill the space the format leaves between the logos and the foot. */
    private static final float ALTO_FILA = 189f;
    private static final float ALTO_ENCABEZADO = 35f;

    public ReporteInventarioFotograficoService() { super(); }

    /** Most bienes one document with photos can take (each photo is a page's worth of memory). */
    public static int maxBienesConFotos() { return MAX_BIENES; }

    public File exportInventarioFotografico(List<Producto> seleccion) throws Exception {
        if (seleccion == null || seleccion.isEmpty())
            throw new IllegalArgumentException("Selecciona al menos un bien para el inventario");
        List<Producto> bienes = ordenadosPorArea(seleccion.size() > MAX_BIENES ? seleccion.subList(0, MAX_BIENES) : seleccion);
        Map<String, ImageData> fotos = FotosPdf.principales(bienes, 700);
        Map<String, List<Producto>> porArea = new LinkedHashMap<>();
        for (Producto p : bienes)
            porArea.computeIfAbsent(hay(p.getArea()) ? p.getArea().trim() : "", a -> new ArrayList<>()).add(p);

        File file = tempFile("inventario_fotografico", ".pdf");
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             // Not flushed page by page: the sheet and its foot are drawn at the end,
             // once the total is known ("Cannot draw elements on already flushed pages").
             Document doc = new Document(pdfDoc, PageSize.LETTER, false)) {

            // Margins of MLA.docx, with the top below the logos.
            doc.setMargins(110, 71, 64, 87);
            List<String> areaDeHoja = new ArrayList<>();   // index = sheet - 1
            for (var grupo : porArea.entrySet()) {
                if (!areaDeHoja.isEmpty()) doc.add(new AreaBreak(AreaBreakType.NEXT_PAGE));
                Table t = tabla(32.5f, 31.5f, 36f);
                t.addHeaderCell(titulo("DESCRIPCIÓN", 12f, bold));
                t.addHeaderCell(titulo("NO. DE INVENTARIO", 12f, bold));
                t.addHeaderCell(titulo("FOTOGRAFÍA DE BIEN MUEBLE", 10f, bold));
                for (Producto p : grupo.getValue()) {
                    t.addCell(descripcion(p, reg));
                    t.addCell(numero(p, bold));
                    t.addCell(foto(fotos.get(p.getId()), reg));
                }
                doc.add(t);
                while (areaDeHoja.size() < pdfDoc.getNumberOfPages()) areaDeHoja.add(grupo.getKey());
            }

            Hoja hoja = new Hoja();
            String emitido = "Emitido el " + LocalDate.now().format(FMT);
            int paginas = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= paginas; i++) {
                hoja.dibujar(pdfDoc, pdfDoc.getPage(i));
                String[] depRes = ReporteEtiquetasService.departamentoYResguardo(areaDeHoja.get(i - 1));
                String area = depRes[0].equals(depRes[1]) ? depRes[1] : depRes[1] + "  ·  " + depRes[0];
                doc.showTextAligned(new Paragraph(area + "   ·   " + emitido + "   ·   Hoja " + i + " de " + paginas)
                        .setFont(reg).setFontSize(6.5f).setFontColor(TENUE),
                    576, 33, i, TextAlignment.RIGHT, VerticalAlignment.BOTTOM, 0);
            }
        }
        return file;
    }

    /** What every sheet of the format carries outside the table: the border with
     *  its shadow, both logos at the top and the watermark behind the table. */
    private static final class Hoja {
        private final PdfImageXObject escudo    = imagen("escudo-ixmiquilpan.png");
        private final PdfImageXObject gobierno  = imagen("gobierno-municipal-2024-2027.png");
        private final PdfImageXObject marcaAgua = imagen("marca-agua.png");

        /** Positions and sizes are the ones the images have in MLA.docx (letter, points). */
        void dibujar(PdfDocument pdf, PdfPage page) {
            if (marcaAgua != null)
                new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdf)
                    .addXObjectFittedIntoRectangle(marcaAgua, new Rectangle(28, 33, 160, 312)).release();
            PdfCanvas c = new PdfCanvas(page.newContentStreamAfter(), page.getResources(), pdf);
            c.saveState()
                .setFillColor(ColorConstants.BLACK)
                .rectangle(588, 21, 3, 744).rectangle(27, 21, 564, 3).fill()
                .setStrokeColor(MARCO).setLineWidth(3).rectangle(25.5, 25.5, 561, 741).stroke()
                .restoreState();
            if (escudo != null)   c.addXObjectFittedIntoRectangle(escudo,   new Rectangle(35.5f, 698.8f, 79f, 63.8f));
            if (gobierno != null) c.addXObjectFittedIntoRectangle(gobierno, new Rectangle(334.6f, 713f, 243.1f, 50.7f));
            c.release();
        }

        private static PdfImageXObject imagen(String nombre) {
            try (InputStream in = ReporteInventarioFotograficoService.class.getResourceAsStream("/img/formato/" + nombre)) {
                return in == null ? null : new PdfImageXObject(ImageDataFactory.create(in.readAllBytes()));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ReporteInventarioFotograficoService.class)
                    .warn("No se pudo cargar la imagen del formato '{}': {}", nombre, e.getMessage());
                return null;
            }
        }
    }

    private static Cell titulo(String texto, float tam, PdfFont bold) {
        return new Cell().add(new Paragraph(texto).setFont(bold).setFontSize(tam).setFontColor(ColorConstants.WHITE)
                .setMultipliedLeading(1.05f).setMargin(0))
            .setMinHeight(ALTO_ENCABEZADO - 8).setBackgroundColor(GUINDA).setPadding(4)
            .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setBorder(new SolidBorder(ColorConstants.BLACK, 0.6f));
    }

    private static Cell descripcion(Producto p, PdfFont reg) {
        String nombre = mayus(p.getNombre(), "SIN DESCRIPCIÓN");
        Cell c = fila(8).setTextAlignment(TextAlignment.CENTER);
        float tam = nombre.length() <= 120 ? 12.5f : nombre.length() <= 200 ? 11f : 9.5f;
        c.add(new Paragraph(nombre).setFont(reg).setFontSize(tam).setFontColor(OSCURO)
            .setMultipliedLeading(1.15f).setMargin(0));
        // Marca, modelo and serie say which bien it is; one already written in the
        // description is not repeated.
        List<String> detalles = new ArrayList<>();
        for (String[] d : new String[][]{{"Marca", p.getMarca()}, {"Modelo", p.getModelo()},
                                         {"Serie", p.getNumeroSerie()}, {"Color", p.getColor()}})
            if (hay(d[1]) && !nombre.contains(d[1].trim().toUpperCase())) detalles.add(d[0] + ": " + d[1].trim());
        if (!detalles.isEmpty())
            c.add(new Paragraph(String.join("\n", detalles)).setFont(reg).setFontSize(8.5f)
                .setFontColor(TENUE).setMargin(0).setMarginTop(6));
        return c;
    }

    private static Cell numero(Producto p, PdfFont bold) {
        String codigo = hay(p.getCodigo()) ? p.getCodigo().trim() : "—";
        return fila(6).setTextAlignment(TextAlignment.CENTER)
            .add(new Paragraph(codigo).setFont(bold).setFontSize(codigo.length() > 12 ? 11.5f : 13f)
                .setFontColor(OSCURO).setMargin(0));
    }

    private static Cell foto(ImageData img, PdfFont reg) {
        Cell c = fila(3).setTextAlignment(TextAlignment.CENTER);
        if (img != null)
            c.add(new Image(img).scaleToFit(156, ALTO_FILA - 10).setHorizontalAlignment(HorizontalAlignment.CENTER));
        else
            c.add(new Paragraph("Sin fotografía registrada").setFont(reg).setFontSize(8.5f).setFontColor(TENUE).setMargin(0));
        return c;
    }

    /** Every row is a third of the sheet, as in the format (iText adds the padding
     *  to the height it is given). */
    private static Cell fila(float relleno) {
        return new Cell().setPadding(relleno).setMinHeight(ALTO_FILA - 2 * relleno)
            .setBorder(new SolidBorder(ColorConstants.BLACK, 0.6f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE).setKeepTogether(true);
    }

    private static boolean hay(String s) { return s != null && !s.isBlank(); }

    private static String mayus(String s, String siVacio) { return hay(s) ? s.trim().toUpperCase() : siVacio; }
}
