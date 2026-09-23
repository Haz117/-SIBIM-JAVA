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
            int idx = 0;
            for (Producto p : bienes) {
                boolean alt = (idx++ % 2) == 1;
                BigDecimal valorAdq  = p.getPrecioCompra();
                BigDecimal valorLibros = p.getValorDepreciado();
                BigDecimal depAcum   = (valorAdq != null && valorLibros != null)
                    ? valorAdq.subtract(valorLibros)
                    : null;

                addRow(table, p.getCodigo(),                                    alt);
                addRow(table, p.getCategoriaNombre(),                           alt);
                addRow(table, "",                                                alt); // No. resguardo — no está en modelo
                addRow(table, p.getResguardante(),                              alt);
                addRow(table, p.getArea(),                                      alt);
                addRow(table, p.getDescripcion(),                               alt);
                addRow(table, p.getUbicacion(),                                 alt);
                addRow(table, p.getMarca(),                                     alt);
                addRow(table, p.getModelo(),                                    alt);
                addRow(table, p.getNumeroSerie(),                               alt);
                addRow(table, p.getNumeroFactura(),                             alt);
                addRow(table, p.getFechaAdquisicion() != null
                    ? p.getFechaAdquisicion().format(FMT) : "",                 alt);
                addRow(table, p.getResguardante(),                              alt); // Nombre responsable resguardo
                addRow(table, moneda(valorAdq),                                 alt);
                addRow(table, moneda(depAcum),                                  alt);
                addRow(table, moneda(valorLibros),                              alt);
                addRow(table, p.getEstadoFisico(),                              alt);
            }
            doc.add(table);

            addFirmasBlock(doc,
                new String[]{"ENTREGÓ", "_______________", "Titular saliente / Responsable"},
                new String[]{"RECIBIÓ", "_______________", "Titular entrante / Receptor"},
                new String[]{"TESTIGO",  "_______________", "Representante de Contraloría"});

            addPdfFooter(doc, bienes.size(), folio);
        }
        return file;
    }

    // ── Encabezado estilo ANEXO V.4 ─────────────────────────────────

    private void addEncabezadoAnexoV4(Document doc, String folio) throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb muted = new DeviceRgb(107, 114, 128);

        float[] hw = folio != null ? new float[]{4f, 1.3f} : new float[]{1f};
        Table header = new Table(hw).useAllAvailableWidth();

        Cell titleCell = new Cell()
            .add(new Paragraph("ANEXO V.4")
                .setFont(bold).setFontSize(14f).setFontColor(COLOR_HEADER)
                .setTextAlignment(TextAlignment.LEFT).setMarginBottom(2))
            .add(new Paragraph("INVENTARIO DE ENTREGA-RECEPCIÓN")
                .setFont(bold).setFontSize(9f).setFontColor(new DeviceRgb(55, 65, 81)).setMarginBottom(2))
            .add(new Paragraph(orgName())
                .setFont(reg).setFontSize(8f).setFontColor(muted))
            .setBackgroundColor(ColorConstants.WHITE)
            .setPadding(10).setBorder(null);
        header.addCell(titleCell);

        if (folio != null) {
            header.addCell(new Cell()
                .add(new Paragraph("FOLIO").setFont(bold).setFontSize(7f)
                    .setFontColor(new DeviceRgb(220, 165, 168)).setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(folio).setFont(bold).setFontSize(9f)
                    .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(LocalDate.now().format(FMT)).setFont(reg).setFontSize(7f)
                    .setFontColor(new DeviceRgb(220, 165, 168)).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(new DeviceRgb(120, 25, 33))
                .setPadding(8).setBorder(null)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE));
        }
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

    private Table buildDataTable() throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        // 17 columnas — anchos relativos pensados para A4 apaisado
        float[] widths = {1.2f, 1.2f, 0.9f, 1.4f, 1.2f, 1.8f, 1.2f, 0.9f,
                          0.9f, 1f,   0.9f, 1f,   1.4f, 1f,   1f,   1f,   0.9f};
        Table t = new Table(widths).useAllAvailableWidth().setMarginTop(4);

        String[] headers = {
            "No. de\ninventario", "Clave\nArmonizada", "No. de\nresguardo",
            "Nombre del\nresguardante", "Adscripción",
            "Descripción física\ndel bien", "Ubicación\nactual",
            "Marca", "Modelo", "No. Serie",
            "Factura o\ndocumento", "Fecha de\nadquisición",
            "Nombre responsable\nresguardo",
            "Valor de\nadquisición", "Depreciación\nacumulada",
            "Valor en\nlibros", "Condición\ndel bien"
        };
        for (String h : headers) {
            t.addCell(new Cell()
                .add(new Paragraph(h).setFont(bold).setFontSize(6f).setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(COLOR_HEADER).setPadding(3)
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(new DeviceRgb(120, 25, 33), 1.2f)));
        }
        return t;
    }

    private void addRow(Table t, String value, boolean alt) {
        DeviceRgb bg = alt ? ROW_ALT_BG : new DeviceRgb(255, 255, 255);
        t.addCell(new Cell()
            .add(new Paragraph(value != null ? value : "")
                .setFontSize(6.5f).setTextAlignment(TextAlignment.LEFT))
            .setBackgroundColor(bg).setPadding(3)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.3f)));
    }

    private static String moneda(BigDecimal v) {
        if (v == null) return "";
        return FormatUtils.formatCurrency(v);
    }
}
