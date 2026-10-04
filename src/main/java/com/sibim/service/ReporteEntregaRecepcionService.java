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
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Genera el ANEXO V.4 de Entrega-Recepción: tabla completa con datos
 *  patrimoniales, depreciación y condición de cada bien. Formato oficial
 *  para respaldar actos de entrega-recepción en el Ayuntamiento. */
public class ReporteEntregaRecepcionService extends ReporteService {

    public ReporteEntregaRecepcionService() { super(); }

    public File exportEntregaRecepcionPdf(List<Producto> bienes) throws Exception {
        if (bienes.isEmpty()) return null;
        File file = tempFile("entrega_recepcion", ".pdf");

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            doc.setMargins(28, 28, 28, 28);

            String folio = generateFolio("ERV");
            addEncabezadoAnexoV4(doc, folio);

            Table table = buildDataTable();
            BigDecimal totalAdq = BigDecimal.ZERO, totalDep = BigDecimal.ZERO, totalLibros = BigDecimal.ZERO;
            int idx = 0;
            for (Producto p : bienes) {
                boolean alt = (idx++ % 2) == 1;
                BigDecimal valorAdq  = p.getPrecioCompra();
                BigDecimal valorLibros = p.getValorDepreciado();
                BigDecimal depAcum   = (valorAdq != null && valorLibros != null)
                    ? valorAdq.subtract(valorLibros)
                    : null;
                if (valorAdq != null)    totalAdq = totalAdq.add(valorAdq);
                if (depAcum != null)     totalDep = totalDep.add(depAcum);
                if (valorLibros != null) totalLibros = totalLibros.add(valorLibros);

                addRow(table, p.getCodigo(),                                    alt, TextAlignment.CENTER);
                addRow(table, p.getClaveArmonizada(),                           alt, TextAlignment.CENTER);
                addRow(table, "",                                                alt, TextAlignment.CENTER); // No. resguardo — no está en modelo
                addRow(table, p.getResguardante(),                              alt, TextAlignment.LEFT);
                addRow(table, p.getArea(),                                      alt, TextAlignment.LEFT);
                addRow(table, descripcionFisica(p),                             alt, TextAlignment.LEFT);
                addRow(table, p.getUbicacion(),                                 alt, TextAlignment.LEFT);
                addRow(table, oSin(p.getMarca(), "Sin marca"),                  alt, TextAlignment.LEFT);
                addRow(table, oSin(p.getModelo(), "Sin modelo"),                alt, TextAlignment.LEFT);
                addRow(table, oSin(p.getNumeroSerie(), "Sin serie"),            alt, TextAlignment.CENTER);
                addRow(table, p.getNumeroFactura(),                             alt, TextAlignment.CENTER);
                addRow(table, p.getFechaAdquisicion() != null
                    ? p.getFechaAdquisicion().format(FMT) : "",                 alt, TextAlignment.CENTER);
                addRow(table, p.getResguardante(),                              alt, TextAlignment.LEFT); // Nombre responsable resguardo
                addRow(table, moneda(valorAdq),                                 alt, TextAlignment.RIGHT);
                addRow(table, moneda(depAcum),                                  alt, TextAlignment.RIGHT);
                addRow(table, moneda(valorLibros),                              alt, TextAlignment.RIGHT);
                addRow(table, p.getEstadoFisico(),                              alt, TextAlignment.CENTER);
            }
            addTotales(table, bienes.size(), totalAdq, totalDep, totalLibros);
            doc.add(table);

            addFirmasBlock(doc,
                new String[]{"ENTREGÓ", null, "Titular saliente / Responsable"},
                new String[]{"RECIBIÓ", null, "Titular entrante / Receptor"},
                new String[]{"TESTIGO",  null, "Representante de Contraloría"});

            addPdfFooter(doc, bienes.size(), folio);
        }
        return numerarPaginas(file);
    }

    // ── Encabezado estilo ANEXO V.4 ─────────────────────────────────

    private void addEncabezadoAnexoV4(Document doc, String folio) throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb muted = new DeviceRgb(107, 114, 128);

        Table header = tabla(3.2f, 1.3f, 1.2f);
        com.itextpdf.layout.properties.VerticalAlignment medio = com.itextpdf.layout.properties.VerticalAlignment.MIDDLE;

        header.addCell(new Cell()
            .add(new Paragraph(orgName()).setFont(bold).setFontSize(9f).setFontColor(ColorConstants.WHITE).setMargin(0))
            .add(new Paragraph("INVENTARIO DE BIENES MUEBLES PARA ENTREGA-RECEPCIÓN")
                .setFont(bold).setFontSize(12f).setFontColor(ColorConstants.WHITE).setMargin(0).setMarginTop(2))
            .setBackgroundColor(COLOR_HEADER).setPadding(10).setBorder(null).setVerticalAlignment(medio));

        header.addCell(new Cell()
            .add(new Paragraph("FOLIO").setFont(bold).setFontSize(7f)
                .setFontColor(new DeviceRgb(220, 165, 168)).setTextAlignment(TextAlignment.CENTER).setMargin(0))
            .add(new Paragraph(folio != null ? folio : "").setFont(bold).setFontSize(9f)
                .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER).setMargin(0))
            .add(new Paragraph(LocalDate.now().format(FMT)).setFont(reg).setFontSize(7f)
                .setFontColor(new DeviceRgb(220, 165, 168)).setTextAlignment(TextAlignment.CENTER).setMargin(0))
            .setBackgroundColor(new DeviceRgb(120, 25, 33))
            .setPadding(8).setBorder(null).setVerticalAlignment(medio));

        // The format's own name, where the printed one carries it: top right.
        header.addCell(new Cell()
            .add(new Paragraph("ANEXO V.4").setFont(bold).setFontSize(17f).setFontColor(COLOR_HEADER)
                .setTextAlignment(TextAlignment.RIGHT).setMargin(0))
            .setPadding(8).setBorder(null).setVerticalAlignment(medio));
        doc.add(header);

        // Barra dorada
        doc.add(new Table(new float[]{1f}).useAllAvailableWidth()
            .addCell(new Cell().setHeight(3f)
                .setBackgroundColor(new DeviceRgb(196, 165, 93)).setBorder(Border.NO_BORDER)));

        com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
        String gen = "Generado " + LocalDate.now().format(FMT)
            + (u != null ? " por " + u.getNombre() : "");
        doc.add(new Paragraph(gen)
            .setFont(reg).setFontSize(8f).setFontColor(ColorConstants.DARK_GRAY).setMarginTop(4).setMarginBottom(6));
    }

    // ── Estructura de la tabla ───────────────────────────────────────

    private static final DeviceRgb LINEA = new DeviceRgb(120, 120, 120);

    private Table buildDataTable() throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        // 17 columnas — anchos relativos pensados para A4 apaisado
        Table t = tabla(5.6f, 6.2f, 5.4f, 6.8f, 7.2f, 11.4f, 6f, 5.2f,
                        5.6f, 5.2f, 5.8f, 5.4f, 6.8f, 6.2f, 6.4f, 6f, 5.4f).setMarginTop(4);

        String[] headers = {
            "No. de\ninventario", "Clave\nArmonizada", "No. de\nresguardo",
            "Nombre de\nresguardante", "Adscripción",
            "Descripción física\ndel bien", "Ubicación\nactual",
            "Marca", "Modelo", "No. Serie",
            "Factura o\ndocumento\nque ampara", "Fecha de\nadquisición",
            "Nombre del\nresponsable\ndel resguardo",
            "Valor de\nadquisición", "Depreciación\nacumulada",
            "Valor en\nlibros", "Condición\ndel bien"
        };
        // Repeats on every page: the official format is a full grid, as here.
        for (String h : headers) {
            t.addHeaderCell(new Cell()
                .add(new Paragraph(h).setFont(bold).setFontSize(5.6f).setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER).setMultipliedLeading(1.1f).setMargin(0))
                .setBackgroundColor(COLOR_HEADER).setPadding(2.5f)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(new DeviceRgb(120, 25, 33), 0.5f)));
        }
        return t;
    }

    private void addRow(Table t, String value, boolean alt, TextAlignment alineacion) {
        DeviceRgb bg = alt ? ROW_ALT_BG : new DeviceRgb(255, 255, 255);
        t.addCell(new Cell()
            .add(new Paragraph(value != null ? value : "")
                .setFontSize(6.3f).setTextAlignment(alineacion).setMultipliedLeading(1.1f).setMargin(0))
            .setBackgroundColor(bg).setPadding(3)
            .setBorder(new SolidBorder(LINEA, 0.4f)));
    }

    private void addTotales(Table t, int bienes, BigDecimal adq, BigDecimal dep, BigDecimal libros) throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        SolidBorder linea = new SolidBorder(LINEA, 0.4f);
        t.addCell(new Cell(1, 13)
            .add(new Paragraph("TOTAL  (" + bienes + (bienes == 1 ? " bien)" : " bienes)")).setFont(bold).setFontSize(6.8f)
                .setFontColor(COLOR_HEADER).setTextAlignment(TextAlignment.RIGHT).setMargin(0))
            .setBackgroundColor(ROW_ALT_BG).setPadding(4).setBorder(linea));
        for (BigDecimal v : new BigDecimal[]{adq, dep, libros})
            t.addCell(new Cell()
                // Totals run to millions: a size down so they stay on one line of the column.
                .add(new Paragraph(moneda(v)).setFont(bold).setFontSize(5.6f).setFontColor(COLOR_HEADER)
                    .setTextAlignment(TextAlignment.RIGHT).setMargin(0))
                .setBackgroundColor(ROW_ALT_BG).setPadding(2).setBorder(linea)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE));
        t.addCell(new Cell().setBackgroundColor(ROW_ALT_BG).setBorder(linea));
    }

    /** The bien by name and, when it has one, its description: a bare description
     *  column left most rows of the format empty. */
    private static String descripcionFisica(Producto p) {
        String nombre = p.getNombre() != null ? p.getNombre().trim() : "";
        String desc = p.getDescripcion() != null ? p.getDescripcion().trim() : "";
        if (desc.isEmpty() || desc.equalsIgnoreCase(nombre)) return nombre;
        return nombre.isEmpty() ? desc : nombre + ". " + desc;
    }

    private static String oSin(String valor, String sin) {
        return valor != null && !valor.isBlank() ? valor.trim() : sin;
    }

    private static String moneda(BigDecimal v) {
        if (v == null) return "";
        return FormatUtils.formatCurrency(v);
    }
}
