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
        // Checked here too: the Organigrama calls this class directly, not
        // through ReporteService, and the acta lists what every bien is worth.
        soloPatrimonio();
        if (bienes.isEmpty()) return null;
        File file = tempFile("entrega_recepcion", ".pdf");

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            doc.setMargins(28, 28, 28, 28);

            String folio = generateFolio("ERV");
            // As the official sheet: its name at the top right and the grid, nothing else
            // above it; folio, date and total go in the footer.
            doc.add(new Paragraph("ANEXO V.4").setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
                .setFontSize(15f).setTextAlignment(TextAlignment.RIGHT).setMargin(0).setMarginBottom(10));

            Table table = buildDataTable();
            // A large table, drawn as it fills: laid out whole, the 3,138 bienes of
            // 28-oct-2026 took 11.5 s and 370 MB, enough to fail on a PC with little memory.
            doc.add(table);
            java.util.Map<String, String> folios = foliosDeResguardoVigente();
            java.util.Map<String, String> titulares = TitularesArea.todos(configRepo);
            BigDecimal totalAdq = BigDecimal.ZERO, totalDep = BigDecimal.ZERO, totalLibros = BigDecimal.ZERO;
            int idx = 0;
            for (Producto p : bienes) {
                boolean alt = false;
                idx++;
                // The whole renglón, as the municipio's inventory writes its IMPORTE:
                // a lot of 140 chairs is listed once, at what the 140 cost.
                BigDecimal[] v = valores(p);
                BigDecimal valorAdq = v[0], depAcum = v[1], valorLibros = v[2];
                if (valorAdq != null)    totalAdq = totalAdq.add(valorAdq);
                if (depAcum != null)     totalDep = totalDep.add(depAcum);
                if (valorLibros != null) totalLibros = totalLibros.add(valorLibros);

                addRow(table, p.getCodigo(),                                    alt, TextAlignment.CENTER);
                addRow(table, p.getClaveArmonizada(),                           alt, TextAlignment.CENTER);
                addRow(table, folios.getOrDefault(p.getId(), ""),               alt, TextAlignment.CENTER);
                addRow(table, p.getResguardante(),                              alt, TextAlignment.LEFT);
                addRow(table, p.getArea(),                                      alt, TextAlignment.LEFT);
                addRow(table, descripcionFisica(p),                             alt, TextAlignment.LEFT);
                addRow(table, p.getUbicacion(),                                 alt, TextAlignment.LEFT);
                addRow(table, oSin(p.getMarca(), "Sin marca"),                  alt, TextAlignment.LEFT);
                addRow(table, oSin(p.getModelo(), "Sin modelo"),                alt, TextAlignment.LEFT);
                addRow(table, oSin(p.getNumeroSerie(), "Sin serie"),            alt, TextAlignment.CENTER);
                addRow(table, p.getFacturaParaFormato(),                        alt, TextAlignment.CENTER);
                addRow(table, p.getFechaAdquisicion() != null
                    ? p.getFechaAdquisicion().format(FMT) : "",                 alt, TextAlignment.CENTER);
                // The área's titular, who answers for its resguardos (Configuración › Áreas).
                addRow(table, titulares.get(p.getArea()),                       alt, TextAlignment.LEFT);
                addRow(table, moneda(valorAdq),                                 alt, TextAlignment.RIGHT);
                addRow(table, moneda(depAcum),                                  alt, TextAlignment.RIGHT);
                addRow(table, moneda(valorLibros),                              alt, TextAlignment.RIGHT);
                addRow(table, p.getEstadoFisico(),                              alt, TextAlignment.CENTER);
                if (idx % 50 == 0) table.flush();
            }
            addTotales(table, bienes.size(), totalAdq, totalDep, totalLibros);
            table.complete();

            addFirmasBlock(doc,
                new String[]{"ENTREGÓ", null, "Titular saliente / Responsable"},
                new String[]{"RECIBIÓ", null, "Titular entrante / Receptor"},
                new String[]{"TESTIGO",  null, "Representante de Contraloría"});

            addPdfFooter(doc, bienes.size(), folio);
        }
        return numerarPaginas(file);
    }

    /** Valor de adquisición, depreciación acumulada and valor en libros of the
     *  renglón. A bien without fecha de adquisición or vida útil is not depreciated:
     *  $0.00, and its book value is what it cost, as the official sheet fills it. */
    static BigDecimal[] valores(Producto p) {
        BigDecimal adq = p.getValorTotal();
        BigDecimal libros = p.getValorDepreciadoTotal();
        if (adq == null) return new BigDecimal[]{null, null, null};
        if (libros == null) return new BigDecimal[]{adq, BigDecimal.ZERO, adq};
        return new BigDecimal[]{adq, adq.subtract(libros), libros};
    }

    // ── Estructura de la tabla ───────────────────────────────────────

    private static final DeviceRgb LINEA = new DeviceRgb(0, 0, 0);

    private Table buildDataTable() throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        // 17 columnas — anchos relativos pensados para A4 apaisado
        Table t = tablaGrande(5.6f, 6.2f, 5.4f, 6.8f, 7.2f, 10.8f, 6f, 5.2f,
                        5.6f, 5.2f, 5.8f, 6.0f, 6.8f, 6.2f, 6.4f, 6f, 5.4f).setMarginTop(4);

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
            // White with black bold text and a heavier line under it, as on paper.
            t.addHeaderCell(new Cell()
                .add(new Paragraph(h).setFont(bold).setFontSize(6.4f).setFontColor(ColorConstants.BLACK)
                    .setTextAlignment(TextAlignment.CENTER).setMultipliedLeading(1.1f).setMargin(0))
                .setPadding(3)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setBorder(new SolidBorder(LINEA, 0.8f)).setBorderBottom(new SolidBorder(LINEA, 1.6f)));
        }
        return t;
    }

    private void addRow(Table t, String value, boolean alt, TextAlignment alineacion) {
        t.addCell(new Cell()
            .add(new Paragraph(value != null ? value : "")
                .setFontSize(6.6f).setTextAlignment(alineacion).setMultipliedLeading(1.1f).setMargin(0))
            .setPadding(2.5f)
            .setBorder(new SolidBorder(LINEA, 0.6f)));
    }

    private void addTotales(Table t, int bienes, BigDecimal adq, BigDecimal dep, BigDecimal libros) throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        SolidBorder linea = new SolidBorder(LINEA, 0.8f);
        SolidBorder arriba = new SolidBorder(LINEA, 1.6f);
        t.addCell(new Cell(1, 13)
            .add(new Paragraph("TOTAL  (" + bienes + (bienes == 1 ? " bien)" : " bienes)")).setFont(bold).setFontSize(7f)
                .setTextAlignment(TextAlignment.RIGHT).setMargin(0))
            .setPadding(4).setBorder(linea).setBorderTop(arriba));
        for (BigDecimal v : new BigDecimal[]{adq, dep, libros})
            t.addCell(new Cell()
                // Totals run to millions: a size down so they stay on one line of the column.
                .add(new Paragraph(moneda(v)).setFont(bold).setFontSize(5.8f)
                    .setTextAlignment(TextAlignment.RIGHT).setMargin(0))
                .setPadding(2).setBorder(linea).setBorderTop(arriba)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE));
        t.addCell(new Cell().setBorder(linea).setBorderTop(arriba));
    }

    /** The bien by name and, when it has one, its description: a bare description
     *  column left most rows of the format empty. */
    /** The format has no cantidad column: a lot says how many pieces its row is. */
    static String descripcionFisica(Producto p) {
        String nombre = p.getNombre() != null ? p.getNombre().trim() : "";
        String desc = p.getDescripcion() != null ? p.getDescripcion().trim() : "";
        String texto = desc.isEmpty() || desc.equalsIgnoreCase(nombre) ? nombre
            : nombre.isEmpty() ? desc : nombre + ". " + desc;
        return p.getStockActual() > 1 ? p.getStockActual() + " piezas: " + texto : texto;
    }

    private static String oSin(String valor, String sin) {
        return valor != null && !valor.isBlank() ? valor.trim() : sin;
    }

    private static String moneda(BigDecimal v) {
        if (v == null) return "";
        return FormatUtils.formatCurrency(v);
    }
}
