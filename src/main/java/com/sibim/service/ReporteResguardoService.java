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
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.session.SessionManager;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReporteResguardoService extends ReporteService {

    private static final DeviceRgb PURPLE        = new DeviceRgb(76,  29, 149);
    private static final DeviceRgb PURPLE_DARK   = new DeviceRgb(49,  46, 129);
    private static final DeviceRgb GRAY_100      = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb GRAY_300      = new DeviceRgb(209, 213, 219);
    private static final DeviceRgb GRAY_500      = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb GRAY_700      = new DeviceRgb(55,  65,  81);
    private static final DeviceRgb AMBER_200     = new DeviceRgb(253, 230, 138);
    private static final DeviceRgb AMBER_800     = new DeviceRgb(146, 64,  14);
    private static final DeviceRgb GREEN_100     = new DeviceRgb(220, 252, 231);
    private static final DeviceRgb GREEN_800     = new DeviceRgb(22,  101, 52);
    private static final DeviceRgb ROW_ALT       = new DeviceRgb(249, 250, 251);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ReporteResguardoService() { super(); }

    /** Export rápido desde selección masiva de productos. */
    public File exportarResguardoPdf(String resguardante, String area, List<Producto> bienes) throws Exception {
        Resguardo r = new Resguardo();
        r.setNumero(generateFolio("RSG"));
        r.setResguardanteNombre(resguardante != null ? resguardante : "—");
        r.setResguardanteCargo("");
        r.setResguardanteArea(area != null ? area : "—");
        r.setEstado("PENDIENTE");
        r.setCreadoEn(LocalDateTime.now());

        Usuario u = SessionManager.getCurrentUser();
        r.setCreadoPorNombre(u != null ? u.getNombre() : "");

        List<ResguardoItem> items = new ArrayList<>();
        for (Producto p : bienes) {
            ResguardoItem it = new ResguardoItem();
            it.setProductoNombre(p.getNombre());
            it.setProductoCodigo(p.getCodigo());
            it.setDescripcion(p.getDescripcion());
            it.setNumeroSerie(p.getNumeroSerie());
            it.setArea(p.getArea() != null ? p.getArea() : area);
            it.setCantidad(p.getStockActual());
            it.setValorUnitario(p.getPrecioCompra());
            items.add(it);
        }
        r.setItems(items);

        return generarPdf(r, bienes);
    }

    /** Export formal desde entidad Resguardo completa. */
    public File exportarResguardoPdf(Resguardo r) throws Exception {
        return generarPdf(r, null);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private File generarPdf(Resguardo r, List<Producto> productosExtra) throws Exception {
        File out = File.createTempFile("resguardo_", ".pdf");
        out.deleteOnExit();

        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf   = new PdfDocument(writer);
             Document    doc   = new Document(pdf, PageSize.LETTER)) {

            doc.setMargins(36, 40, 52, 40);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            LocalDateTime ts   = r.getCreadoEn() != null ? r.getCreadoEn() : LocalDateTime.now();
            String fechaStr    = ts.toLocalDate().format(DATE_FMT);
            String horaStr     = ts.format(TIME_FMT);
            String numero      = r.getNumero() != null ? r.getNumero() : generateFolio("RSG");
            String estado      = r.getEstado() != null ? r.getEstado() : "PENDIENTE";
            boolean pendiente  = !"ACTIVO".equalsIgnoreCase(estado);

            seccionHeader(doc, bold, reg, numero, fechaStr);
            seccionInfoGeneral(doc, bold, reg, r, fechaStr, pendiente, numero);
            seccionBienes(doc, bold, reg, r, productosExtra, fechaStr);
            seccionNota(doc, reg, r.getItems().size());
            seccionFundamento(doc, reg);
            seccionFirmas(doc, bold, reg, r);
            seccionFooter(doc, reg, numero, fechaStr, horaStr);
        }
        return out;
    }

    // ── Sección 1: Encabezado ────────────────────────────────────────────────

    private void seccionHeader(Document doc, PdfFont bold, PdfFont reg, String numero, String fechaStr) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{1.1f, 3.5f, 1.4f}))
            .useAllAvailableWidth().setMarginBottom(0);

        // Izquierda — logo municipal (si está configurado) o identificador de texto
        Cell left = cell().setBorder(brd(GRAY_300)).setPadding(8).setVerticalAlignment(VerticalAlignment.MIDDLE);
        String lp = logoPath();
        boolean logoLoaded = false;
        if (lp != null) {
            try {
                ImageData imgData = ImageDataFactory.create(lp);
                Image logoImg = new Image(imgData);
                logoImg.setMaxHeight(50).setMaxWidth(65).setAutoScale(false);
                logoImg.setHorizontalAlignment(HorizontalAlignment.CENTER);
                left.add(logoImg);
                logoLoaded = true;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ReporteResguardoService.class)
                    .warn("No se pudo cargar el logo municipal '{}': {}", lp, e.getMessage());
            }
        }
        if (!logoLoaded) {
            left.add(para("H. AYUNTAMIENTO\nMUNICIPAL", bold, 7).setTextAlignment(TextAlignment.CENTER).setFontColor(GRAY_700));
        }
        t.addCell(left);

        // Centro — título principal
        Cell mid = cell().setBackgroundColor(PURPLE).setBorder(brd(PURPLE_DARK)).setPadding(12)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        mid.add(para(orgName(), bold, 9).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.WHITE));
        mid.add(para("RESGUARDO DE BIENES", bold, 15).setTextAlignment(TextAlignment.CENTER)
            .setFontColor(ColorConstants.WHITE).setMarginTop(3));
        t.addCell(mid);

        // Derecha — folio + fecha
        Cell right = cell().setBorder(brd(GRAY_300)).setPadding(8).setVerticalAlignment(VerticalAlignment.MIDDLE);
        right.add(para("RESGUARDO", bold, 7).setTextAlignment(TextAlignment.CENTER).setFontColor(GRAY_500));
        right.add(para(numero, bold, 12).setTextAlignment(TextAlignment.CENTER).setFontColor(PURPLE));
        right.add(para("FECHA", bold, 7).setTextAlignment(TextAlignment.CENTER).setFontColor(GRAY_500).setMarginTop(6));
        right.add(para(fechaStr, reg, 8).setTextAlignment(TextAlignment.CENTER));
        t.addCell(right);

        doc.add(t);
    }

    // ── Sección 2: Información general ──────────────────────────────────────

    private void seccionInfoGeneral(Document doc, PdfFont bold, PdfFont reg,
                                    Resguardo r, String fechaStr, boolean pendiente, String numero) {
        doc.add(para("INFORMACION GENERAL DEL RESGUARDO " + numero, bold, 8.5f)
            .setFontColor(GRAY_700).setMarginTop(8).setMarginBottom(3));

        Table t = new Table(UnitValue.createPercentArray(new float[]{4f, 1.3f})).useAllAvailableWidth();

        // Columna izquierda: campos
        Cell leftCol = cell().setBorder(Border.NO_BORDER).setPadding(0);
        Table fields = new Table(UnitValue.createPercentArray(new float[]{1.2f, 3.8f})).useAllAvailableWidth();

        String cargo = r.getResguardanteCargo() != null && !r.getResguardanteCargo().isBlank()
            ? r.getResguardanteCargo() : "Servidor Público Municipal";
        String ubicacion = r.getResguardanteArea() != null ? r.getResguardanteArea() : "—";

        String[][] filas = {
            {"NOMBRE",      r.getResguardanteNombre() != null ? r.getResguardanteNombre() : "—"},
            {"PUESTO",      cargo},
            {"AREA",        r.getResguardanteArea() != null ? r.getResguardanteArea() : "—"},
            {"DESCRIPCION", "CON FUNDAMENTO EN LAS FACULTADES QUE SE ESTABLECEN EN EL ART. 98 " +
                            "INCISO VII DE LA LEY ORGANICA MUNICIPAL PARA EL ESTADO DE HIDALGO"},
            {"UBICACION",   ubicacion},
        };
        for (String[] fila : filas) {
            fields.addCell(cell().add(para(fila[0], bold, 8))
                .setBackgroundColor(GRAY_100).setBorder(brd(GRAY_300)).setPadding(5));
            fields.addCell(cell().add(para(fila[1], reg, 8))
                .setBorder(brd(GRAY_300)).setPadding(5));
        }
        leftCol.add(fields);
        t.addCell(leftCol);

        // Columna derecha: badge estado
        DeviceRgb badgeBg = pendiente ? AMBER_200 : GREEN_100;
        DeviceRgb badgeFg = pendiente ? AMBER_800 : GREEN_800;
        Cell rightCol = cell().setBackgroundColor(badgeBg).setBorder(brd(GRAY_300))
            .setPadding(12).setVerticalAlignment(VerticalAlignment.MIDDLE);
        rightCol.add(para(pendiente ? "PENDIENTE" : "ACTIVO", bold, 13)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(badgeFg));
        if (r.getObservaciones() != null && !r.getObservaciones().isBlank()) {
            rightCol.add(para(r.getObservaciones(), reg, 7)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(badgeFg).setMarginTop(4));
        }
        t.addCell(rightCol);

        doc.add(t);
    }

    // ── Sección 3: Lista de bienes ───────────────────────────────────────────

    private void seccionBienes(Document doc, PdfFont bold, PdfFont reg,
                               Resguardo r, List<Producto> extra, String fechaResguardo) {
        doc.add(para("LISTA DE BIENES ASIGNADOS", bold, 8.5f)
            .setFontColor(GRAY_700).setTextAlignment(TextAlignment.CENTER).setMarginTop(10).setMarginBottom(3));

        float[] cw = {0.4f, 1.5f, 3.2f, 1.1f, 1f, 1f};
        Table t = new Table(UnitValue.createPercentArray(cw)).useAllAvailableWidth();

        String[] hdrs = {"NO.", "INVENTARIO", "CONCEPTO", "TIPO BIEN", "ASIGNACION", "CARACTERISTICAS"};
        for (String h : hdrs) {
            t.addHeaderCell(cell()
                .add(para(h, bold, 8).setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(PURPLE).setBorder(brd(PURPLE_DARK)).setPadding(5));
        }

        List<ResguardoItem> items = r.getItems();
        for (int i = 0; i < items.size(); i++) {
            ResguardoItem item = items.get(i);
            DeviceRgb rowBg = (i % 2 == 1) ? ROW_ALT : new DeviceRgb(255, 255, 255);

            Producto p = buscarProducto(extra, item.getProductoCodigo());

            String concepto = upper(item.getProductoNombre());
            if (item.getDescripcion() != null && !item.getDescripcion().isBlank())
                concepto += " - " + upper(item.getDescripcion());

            String tipoBien    = p != null && p.getCategoriaNombre() != null ? upper(p.getCategoriaNombre()) : "MUEBLE";
            String asignacion  = p != null && p.getFechaAdquisicion() != null
                ? p.getFechaAdquisicion().format(DATE_FMT) : fechaResguardo;
            String caracterist = "—";
            if (p != null) {
                String m = join(" ", p.getMarca(), p.getModelo());
                if (!m.isBlank()) caracterist = upper(m);
            }

            String[] vals = {
                String.valueOf(i + 1),
                nvl(item.getProductoCodigo()),
                concepto,
                tipoBien,
                asignacion,
                caracterist
            };
            for (String v : vals) {
                t.addCell(cell().add(para(v, reg, 7.5f))
                    .setBackgroundColor(rowBg).setBorder(brd(GRAY_300)).setPadding(4));
            }
        }
        doc.add(t);
    }

    // ── Sección 4: Nota ─────────────────────────────────────────────────────

    private void seccionNota(Document doc, PdfFont reg, int total) {
        doc.add(para("NOTA: " + total + " Número(s) de inventarios impresos Respecto al Total de " + total,
            reg, 8).setFontColor(GRAY_700).setMarginTop(6));
    }

    // ── Sección 5: Fundamento legal ──────────────────────────────────────────

    private void seccionFundamento(Document doc, PdfFont reg) {
        doc.add(para(
            "En cumplimiento a lo dispuesto por el Artículo 98 fracción VII, de la Ley Orgánica Municipal " +
            "para el Estado de Hidalgo, con el propósito de controlar y salvaguardar los bienes muebles que " +
            "conforman el patrimonio del municipio, se integra la presente responsiva de resguardo de bienes muebles.",
            reg, 7).setFontColor(GRAY_500).setMarginTop(8));
    }

    // ── Sección 6: Firmas ────────────────────────────────────────────────────

    private void seccionFirmas(Document doc, PdfFont bold, PdfFont reg, Resguardo r) {
        doc.add(new Paragraph("").setMarginTop(24));

        Table t = new Table(UnitValue.createPercentArray(new float[]{1f, 1f, 1f, 1f}))
            .useAllAvailableWidth().setMarginTop(8);

        String elaboro = r.getCreadoPorNombre() != null && !r.getCreadoPorNombre().isBlank()
            ? r.getCreadoPorNombre() : "_______________";
        String resguardante = r.getResguardanteNombre() != null ? r.getResguardanteNombre() : "_______________";
        String cargo        = r.getResguardanteCargo()  != null && !r.getResguardanteCargo().isBlank()
            ? r.getResguardanteCargo() : "Resguardante";

        Object[][] firmas = {
            {"RECIBÍ DE CONFORMIDAD", resguardante, cargo},
            {"ELABORÓ",               elaboro,       "Director de Recursos Materiales"},
            {"VO.BO.",                "_______________", "Secretario General Municipal"},
            {"SUPERVISO",             "_______________", "Síndico Hacendario"},
        };

        for (Object[] f : firmas) {
            Cell c = cell().setBorder(Border.NO_BORDER).setPadding(6).setTextAlignment(TextAlignment.CENTER);
            c.add(para((String) f[0], bold, 8).setFontColor(GRAY_700));
            c.add(para("\n\n________________________", reg, 9));
            c.add(para((String) f[1], bold, 7.5f).setMarginTop(2));
            c.add(para((String) f[2], reg, 7).setFontColor(GRAY_500));
            t.addCell(c);
        }
        doc.add(t);
    }

    // ── Sección 7: Pie de página ─────────────────────────────────────────────

    private void seccionFooter(Document doc, PdfFont reg,
                               String numero, String fechaStr, String horaStr) {
        String digits = numero.replaceAll("[^0-9]", "");
        String reporteId = "RESG" + (digits.isEmpty() ? numero : digits);

        doc.add(para(
            "REPORTE: " + reporteId + "     FECHA: " + fechaStr +
            "     SIBIM     HORA: " + horaStr + "     NÚMERO: 1 DE 1",
            reg, 7)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(GRAY_500)
            .setMarginTop(14)
            .setBorderTop(new SolidBorder(GRAY_300, 0.5f))
            .setPaddingTop(4));
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static Cell cell() { return new Cell(); }

    private static Paragraph para(String text, PdfFont font, float size) {
        return new Paragraph(text != null ? text : "").setFont(font).setFontSize(size);
    }

    private static SolidBorder brd(DeviceRgb color) { return new SolidBorder(color, 0.5f); }

    private static String nvl(String s) { return s != null ? s : "—"; }

    private static String upper(String s) { return s != null ? s.toUpperCase() : "—"; }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }

    private static Producto buscarProducto(List<Producto> lista, String codigo) {
        if (lista == null || codigo == null) return null;
        for (Producto p : lista) {
            if (codigo.equals(p.getCodigo())) return p;
        }
        return null;
    }


}
