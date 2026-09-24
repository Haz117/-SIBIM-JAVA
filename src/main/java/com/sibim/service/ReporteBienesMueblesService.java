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
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Genera el formato "RESGUARDO DE BIENES MUEBLES" (página 3 del documento oficial).
 * Agrupa los bienes por categoría/clave arancelaria con tabla de detalle y TOTAL.
 */
public class ReporteBienesMueblesService extends ReporteService {

    private static final DeviceRgb GUINDA     = new DeviceRgb(162, 35, 45);
    private static final DeviceRgb GUINDA_D   = new DeviceRgb(120, 25, 33);
    private static final DeviceRgb GRAY_100   = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb GRAY_300   = new DeviceRgb(209, 213, 219);
    private static final DeviceRgb GRAY_500   = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb GRAY_700   = new DeviceRgb(55, 65, 81);
    private static final DeviceRgb ROW_ALT     = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb GOLD        = new DeviceRgb(196, 165, 93);
    private static final DeviceRgb GUINDA_LIGHT = new DeviceRgb(252, 240, 241);

    public ReporteBienesMueblesService() { super(); }

    public File exportBienesMueblesPdf(List<Producto> bienes, String resguardante,
                                        String cargo, String area, String numeroResguardo) throws Exception {
        if (bienes.isEmpty()) return null;
        File file = tempFile("bienes_muebles", ".pdf");

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            doc.setMargins(24, 24, 24, 24);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            addEncabezado(doc, bold, reg, resguardante, cargo, area, numeroResguardo);
            addTablasBienes(doc, bold, reg, bienes);
            addTextoResponsiva(doc, reg, resguardante, area);
            addFirmasBlock(doc,
                new String[]{"RECIBÍ DE CONFORMIDAD", nvl(resguardante), nvl(cargo)},
                new String[]{"SUPERVISÓ", "_______________", "Director de Recursos Materiales"},
                new String[]{"VO.BO.", "_______________", "Secretario General Municipal"});
        }
        return file;
    }

    // ── Encabezado ──────────────────────────────────────────────────────────

    private void addEncabezado(Document doc, PdfFont bold, PdfFont reg,
                                String resguardante, String cargo, String area, String numero) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{2f, 3f, 2f}))
            .useAllAvailableWidth().setMarginBottom(4);

        Cell izq = new Cell().setBorder(new SolidBorder(GRAY_300, 0.5f)).setPadding(8)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        com.itextpdf.layout.element.Image logo = loadHeaderLogo();
        if (logo != null) {
            izq.add(logo.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER));
        } else {
            izq.add(new Paragraph("H. AYUNTAMIENTO").setFont(bold).setFontSize(7)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(GRAY_700));
        }
        t.addCell(izq);

        Cell centro = new Cell().setBackgroundColor(GUINDA).setBorder(new SolidBorder(GUINDA_D, 0.5f)).setPadding(10)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        centro.add(new Paragraph(orgName()).setFont(bold).setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.WHITE));
        centro.add(new Paragraph("INVENTARIO DE BIENES MUEBLES").setFont(bold).setFontSize(11)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.WHITE).setMarginTop(2));
        centro.add(new Paragraph("RESGUARDO DE BIENES MUEBLES").setFont(reg).setFontSize(8)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(new DeviceRgb(240, 195, 195)).setMarginTop(1));
        t.addCell(centro);

        Cell der = new Cell().setBorder(new SolidBorder(GRAY_300, 0.5f)).setPadding(8)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        der.add(new Paragraph("MUNICIPIO DE IXMIQUILPAN, HIDALGO").setFont(bold).setFontSize(6.5f)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(GUINDA));
        if (numero != null && !numero.isBlank()) {
            der.add(new Paragraph("NÚMERO DE RESGUARDO: " + numero).setFont(bold).setFontSize(7)
                .setTextAlignment(TextAlignment.CENTER).setFontColor(GRAY_700).setMarginTop(4));
        }
        der.add(new Paragraph("FECHA: " + LocalDate.now().format(FMT)).setFont(reg).setFontSize(7)
            .setTextAlignment(TextAlignment.CENTER).setFontColor(GRAY_500).setMarginTop(2));
        t.addCell(der);

        doc.add(t);

        // Sub-header con datos del resguardante
        Table sub = new Table(UnitValue.createPercentArray(new float[]{1f, 1f})).useAllAvailableWidth()
            .setMarginTop(4).setMarginBottom(6);

        String resguardanteTxt = nvl(resguardante, "_______________");
        String areaLabel = nvl(area, "_______________");

        sub.addCell(new Cell().setBorder(Border.NO_BORDER)
            .add(new Paragraph("PRESIDENCIA MUNICIPAL IXMIQUILPAN, HIDALGO").setFont(bold).setFontSize(7).setFontColor(GRAY_700))
            .add(new Paragraph("RESGUARDANTE: " + resguardanteTxt).setFont(bold).setFontSize(7.5f).setFontColor(GUINDA).setMarginTop(2))
            .setPadding(4));

        String cargoLabel = nvl(cargo, "Servidor Público Municipal");
        sub.addCell(new Cell().setBorder(Border.NO_BORDER)
            .add(new Paragraph("DIRECCIÓN: " + areaLabel).setFont(reg).setFontSize(7).setFontColor(GRAY_700))
            .add(new Paragraph("CARGO: " + cargoLabel).setFont(reg).setFontSize(7).setFontColor(GRAY_700).setMarginTop(1))
            .add(new Paragraph("FECHA: " + LocalDate.now().format(FMT)).setFont(reg).setFontSize(7).setFontColor(GRAY_700).setMarginTop(1))
            .setPadding(4).setTextAlignment(TextAlignment.RIGHT));

        doc.add(sub);
        doc.add(new Table(new float[]{1f}).useAllAvailableWidth()
            .addCell(new Cell().setHeight(3f).setBackgroundColor(GOLD).setBorder(Border.NO_BORDER)));
    }

    // ── Tabla de bienes agrupada por categoría ──────────────────────────────

    private void addTablasBienes(Document doc, PdfFont bold, PdfFont reg, List<Producto> bienes) throws Exception {
        Map<String, List<Producto>> porCategoria = bienes.stream()
            .collect(Collectors.groupingBy(p -> {
                if (p.getClaveArmonizada() != null && !p.getClaveArmonizada().isBlank())
                    return p.getClaveArmonizada() + (p.getCategoriaNombre() != null ? " " + p.getCategoriaNombre().toUpperCase() : "");
                return p.getCategoriaNombre() != null ? p.getCategoriaNombre().toUpperCase() : "SIN CATEGORÍA";
            }, LinkedHashMap::new, Collectors.toList()));

        float[] widths = {0.5f, 1.2f, 0.8f, 0.6f, 2f, 0.8f, 0.7f, 0.8f, 0.7f, 0.7f, 1f, 0.9f, 0.7f};
        String[] headers = {
            "N°\nBIENES", "N° INV.", "UBICACIÓN", "CANTIDAD",
            "DESCRIPCIÓN", "MARCA", "MODELO", "N° SERIE", "FECHA",
            "N° FACTURA", "IMPORTE", "ESTADO\nFÍSICO", "OBSERVACIONES"
        };

        BigDecimal totalGlobal = BigDecimal.ZERO;

        for (Map.Entry<String, List<Producto>> entry : porCategoria.entrySet()) {
            Table table = new Table(widths).useAllAvailableWidth().setMarginTop(8);

            // Fila de categoría — span completo
            table.addCell(new Cell(1, headers.length)
                .add(new Paragraph(entry.getKey()).setFont(bold).setFontSize(7.5f).setFontColor(GUINDA))
                .setBackgroundColor(GUINDA_LIGHT)
                .setBorderLeft(new SolidBorder(GUINDA, 3f))
                .setBorderTop(new SolidBorder(GRAY_300, 0.5f))
                .setBorderRight(new SolidBorder(GRAY_300, 0.5f))
                .setBorderBottom(new SolidBorder(GRAY_300, 0.5f))
                .setPadding(5).setPaddingLeft(10));

            // Headers
            for (String h : headers) {
                table.addCell(new Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(6f).setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(GUINDA).setBorder(new SolidBorder(GUINDA_D, 0.5f)).setPadding(3));
            }

            int n = 1;
            BigDecimal totalCat = BigDecimal.ZERO;
            List<Producto> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                Producto p = items.get(i);
                boolean alt = (i % 2) == 1;
                DeviceRgb bg = alt ? ROW_ALT : new DeviceRgb(255, 255, 255);

                BigDecimal importe = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
                totalCat = totalCat.add(importe.multiply(BigDecimal.valueOf(p.getStockActual() > 0 ? p.getStockActual() : 1)));

                table.addCell(dataCell(String.valueOf(n++), reg, bg));
                table.addCell(dataCell(nvl(p.getCodigo()), reg, bg));
                table.addCell(dataCell(nvl(p.getUbicacion()), reg, bg));
                table.addCell(dataCell(String.valueOf(p.getStockActual()), reg, bg));
                table.addCell(dataCell(nvl(p.getNombre() + (p.getDescripcion() != null && !p.getDescripcion().isBlank() ? ". " + p.getDescripcion() : "")), reg, bg));
                table.addCell(dataCell(nvl(p.getMarca()), reg, bg));
                table.addCell(dataCell(nvl(p.getModelo()), reg, bg));
                table.addCell(dataCell(nvl(p.getNumeroSerie()), reg, bg));
                table.addCell(dataCell(p.getFechaAdquisicion() != null ? p.getFechaAdquisicion().format(FMT) : "", reg, bg));
                table.addCell(dataCell(nvl(p.getNumeroFactura()), reg, bg));
                table.addCell(dataCell(FormatUtils.formatCurrency(importe), reg, bg));
                table.addCell(dataCell(nvl(p.getEstadoFisico()), reg, bg));
                table.addCell(dataCell("", reg, bg)); // observaciones en blanco
            }

            // Fila SUBTOTAL por categoría
            table.addCell(new Cell(1, 10)
                .add(new Paragraph("SUBTOTAL").setFont(bold).setFontSize(6.5f)
                    .setTextAlignment(TextAlignment.RIGHT).setFontColor(GUINDA))
                .setBackgroundColor(GUINDA_LIGHT).setBorder(new SolidBorder(GRAY_300, 0.5f)).setPadding(3));
            table.addCell(new Cell()
                .add(new Paragraph(FormatUtils.formatCurrency(totalCat)).setFont(bold).setFontSize(7f)
                    .setTextAlignment(TextAlignment.RIGHT).setFontColor(GUINDA))
                .setBackgroundColor(GUINDA_LIGHT).setBorder(new SolidBorder(GRAY_300, 0.5f)).setPadding(3));
            table.addCell(new Cell(1, 2).setBorder(new SolidBorder(GRAY_300, 0.5f))
                .setBackgroundColor(GUINDA_LIGHT).setPadding(3));

            doc.add(table);
            totalGlobal = totalGlobal.add(totalCat);
        }

        // Fila TOTAL GLOBAL
        doc.add(new Table(new float[]{1f}).useAllAvailableWidth().setMarginTop(10).setMarginBottom(4)
            .addCell(new Cell()
                .add(new Paragraph("TOTAL GENERAL:   " + FormatUtils.formatCurrency(totalGlobal))
                    .setFont(bold).setFontSize(10)
                    .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(GUINDA)
                .setBorder(Border.NO_BORDER)
                .setPadding(8).setPaddingRight(14)));
    }

    // ── Texto responsiva ────────────────────────────────────────────────────

    private void addTextoResponsiva(Document doc, PdfFont reg, String resguardante, String area) {
        String texto =
            "En cumplimiento a lo dispuesto por el artículo 98 fracción VII, de la Ley orgánica municipal " +
            "para el Estado de Hidalgo, en concordancia con lo dispuesto por los artículos 87, 88 y 89 de la ley de presupuesto " +
            "y contabilidad gubernamental del Estado de Hidalgo, así como lo previsto por la ley general de contabilidad " +
            "gubernamental, con el propósito de controlar y salvaguardar los bienes muebles que conforman el patrimonio del " +
            "municipio de Ixmiquilpan, se integra la presente responsiva de resguardo de bienes muebles.\n\n" +
            "El servidor público, al firmar este documento, reconoce y acepta la responsabilidad derivada de la custodia " +
            "de los bienes mencionados, conforme al artículo 4 de la Ley General de Responsabilidades de los Servidores Públicos, " +
            "comprometiéndose a responder con el pago o reposición de un bien nuevo si las causas de su deterioro o pérdida " +
            "son imputables al servidor público resguardante.\n\n" +
            "Firmando el presente resguardo, una vez enterado y aceptado la recepción y custodia de los bienes muebles " +
            "ya descritos, sabedor de las obligaciones y responsabilidades que implica el cumplimiento de las disposiciones " +
            "legales del estado que las regalan.";

        doc.add(new Paragraph(texto)
            .setFont(reg).setFontSize(6.5f).setFontColor(GRAY_700)
            .setMarginTop(10).setTextAlignment(TextAlignment.JUSTIFIED));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Cell dataCell(String text, PdfFont font, DeviceRgb bg) {
        return new Cell()
            .add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(6.5f))
            .setBackgroundColor(bg).setPadding(3)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(new DeviceRgb(209, 213, 219), 0.3f));
    }

    private static String nvl(String s) { return s != null && !s.isBlank() ? s : ""; }
    private static String nvl(String s, String fallback) { return (s != null && !s.isBlank()) ? s : fallback; }
}
