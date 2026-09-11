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
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.element.Image;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.ResguardoRepository;
import com.sibim.util.FormatUtils;
import com.sibim.util.QrUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ResguardoService {

    private final ResguardoRepository repo = new ResguardoRepository();
    private final ConfiguracionRepository configRepo = new ConfiguracionRepository();

    private static final DeviceRgb COLOR_HEADER  = new DeviceRgb(76, 29, 149);
    private static final DeviceRgb COLOR_SUBHEAD = new DeviceRgb(241, 245, 249);
    private static final DeviceRgb COLOR_MUTED   = new DeviceRgb(100, 116, 139);
    private static final DateTimeFormatter FMT   = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<Resguardo> getAll() throws SQLException { return repo.findAll(); }

    public Resguardo findById(String id) throws SQLException { return repo.findById(id); }

    public Resguardo crear(String resguardanteNombre, String resguardanteCargo,
                           String resguardanteArea, List<ResguardoItem> items,
                           String observaciones) throws SQLException {
        if (resguardanteNombre == null || resguardanteNombre.isBlank())
            throw new IllegalArgumentException("El nombre del resguardante es obligatorio");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Debe agregar al menos un bien al resguardo");
        Resguardo r = new Resguardo();
        r.setResguardanteNombre(resguardanteNombre.trim());
        r.setResguardanteCargo(resguardanteCargo != null ? resguardanteCargo.trim() : null);
        r.setResguardanteArea(resguardanteArea != null ? resguardanteArea.trim() : null);
        r.setObservaciones(observaciones != null ? observaciones.trim() : null);
        r.setItems(items);
        return repo.save(r);
    }

    public void cancelar(String id) throws SQLException { repo.cancelar(id); }

    public java.util.List<Resguardo> getByProductoId(String productoId) throws java.sql.SQLException {
        return repo.findByProductoId(productoId);
    }

    public File exportarPdf(Resguardo resguardo) throws Exception {
        if (resguardo.getItems() == null || resguardo.getItems().isEmpty()) {
            Resguardo full = repo.findById(resguardo.getId());
            if (full != null) resguardo = full;
        }
        return generarPdf(resguardo);
    }

    private String orgName() {
        String org = configRepo.get("nombre_ayuntamiento", "");
        String mun = configRepo.get("municipio", "");
        if (org.isBlank()) return "H. Ayuntamiento Municipal";
        return mun.isBlank() ? org : org + " · " + mun;
    }

    private File generarPdf(Resguardo r) throws IOException {
        File file = File.createTempFile("sibim_resguardo_", ".pdf");
        file.deleteOnExit();

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb dark  = new DeviceRgb(15, 23, 42);
        DeviceRgb muted = COLOR_MUTED;

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            // ── Header institucional ──
            Table headerTable = new Table(1).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell hCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("RESGUARDO DE BIENES").setFont(bold).setFontSize(16)
                    .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(orgName()).setFont(regular).setFontSize(9)
                    .setFontColor(new DeviceRgb(200, 210, 240)).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(COLOR_HEADER).setPadding(14).setBorder(null);
            headerTable.addCell(hCell);
            doc.add(headerTable);

            // ── Número + Fecha + QR ──
            Table folioQrRow = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth().setMarginTop(8).setMarginBottom(4);
            com.itextpdf.layout.element.Cell folioCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph()
                    .add(new com.itextpdf.layout.element.Text("Folio: ").setFont(bold))
                    .add(new com.itextpdf.layout.element.Text(r.getNumero()).setFont(regular))
                    .setFontSize(9).setFontColor(muted))
                .add(new Paragraph()
                    .add(new com.itextpdf.layout.element.Text("Fecha: ").setFont(bold))
                    .add(new com.itextpdf.layout.element.Text(
                        r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate().format(FMT)
                        : LocalDate.now().format(FMT)).setFont(regular))
                    .setFontSize(9).setFontColor(muted))
                .add(new Paragraph()
                    .add(new com.itextpdf.layout.element.Text("Estado: ").setFont(bold))
                    .add(new com.itextpdf.layout.element.Text(r.getEstado()).setFont(regular))
                    .setFontSize(9).setFontColor(muted))
                .setBorder(null)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
            com.itextpdf.layout.element.Cell qrCell = new com.itextpdf.layout.element.Cell()
                .setBorder(null)
                .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.RIGHT);
            byte[] qrBytes = QrUtils.toPngBytes(r.getNumero(), 90);
            if (qrBytes != null) {
                Image qrImg = new Image(ImageDataFactory.create(qrBytes));
                qrImg.setWidth(60).setHeight(60);
                qrCell.add(qrImg);
            }
            folioQrRow.addCell(folioCell);
            folioQrRow.addCell(qrCell);
            doc.add(folioQrRow);

            // ── Datos del resguardante ──
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginTop(10);
            addInfoRow(infoTable, "Resguardante:", r.getResguardanteNombre(), bold, regular);
            addInfoRow(infoTable, "Cargo:", orDash(r.getResguardanteCargo()), bold, regular);
            addInfoRow(infoTable, "Área / Dirección:", orDash(r.getResguardanteArea()), bold, regular);
            doc.add(infoTable);

            // ── Tabla de bienes ──
            doc.add(new Paragraph("BIENES ENTREGADOS EN RESGUARDO")
                .setFont(bold).setFontSize(9).setFontColor(dark)
                .setMarginTop(14).setMarginBottom(4));

            String[] headers = {"#", "Código", "Descripción / Nombre", "Área", "Serie", "Valor Unitario"};
            float[] widths   = {0.4f, 1.2f, 3f, 1.8f, 1.4f, 1.2f};
            Table bienesTable = new Table(widths).useAllAvailableWidth();
            for (String h : headers) {
                bienesTable.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_HEADER).setPadding(5).setBorder(null));
            }
            BigDecimal totalValor = BigDecimal.ZERO;
            int idx = 1;
            for (ResguardoItem item : r.getItems()) {
                boolean alt = idx % 2 == 0;
                DeviceRgb rowBg = alt ? COLOR_SUBHEAD : null;
                addTableRow(bienesTable, bold, regular, rowBg,
                    String.valueOf(idx++),
                    orDash(item.getProductoCodigo()),
                    item.getProductoNombre(),
                    orDash(item.getArea()),
                    orDash(item.getNumeroSerie()),
                    item.getValorUnitario() != null
                        ? FormatUtils.formatCurrency(item.getValorUnitario()) : "—");
                if (item.getValorUnitario() != null)
                    totalValor = totalValor.add(item.getValorUnitario());
            }
            // Total row
            com.itextpdf.layout.element.Cell totalLbl = new com.itextpdf.layout.element.Cell(1, 5)
                .add(new Paragraph("VALOR TOTAL").setFont(bold).setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(COLOR_SUBHEAD).setPadding(5).setBorder(null);
            com.itextpdf.layout.element.Cell totalVal = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(FormatUtils.formatCurrency(totalValor))
                    .setFont(bold).setFontSize(8))
                .setBackgroundColor(COLOR_SUBHEAD).setPadding(5).setBorder(null);
            bienesTable.addCell(totalLbl);
            bienesTable.addCell(totalVal);
            doc.add(bienesTable);

            // ── Observaciones ──
            if (r.getObservaciones() != null && !r.getObservaciones().isBlank()) {
                doc.add(new Paragraph("Observaciones: " + r.getObservaciones())
                    .setFont(regular).setFontSize(8).setFontColor(muted).setMarginTop(8));
            }

            // ── Firmas ──
            doc.add(new Paragraph("\n\n"));
            Table firmasTable = new Table(UnitValue.createPercentArray(new float[]{1, 0.3f, 1}))
                .useAllAvailableWidth().setMarginTop(20);
            addFirmaCol(firmasTable, bold, regular,
                "ENTREGA", "Nombre y cargo de quien entrega");
            firmasTable.addCell(new com.itextpdf.layout.element.Cell().setBorder(null));
            addFirmaCol(firmasTable, bold, regular,
                "RECIBE / RESGUARDANTE",
                r.getResguardanteNombre() + (r.getResguardanteCargo() != null
                    ? "\n" + r.getResguardanteCargo() : ""));
            doc.add(firmasTable);

            // ── Footer ──
            doc.add(new Paragraph(
                "Generado por SIBIM · " + orgName() + " · " + LocalDate.now().format(FMT))
                .setFont(regular).setFontSize(7).setFontColor(muted)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(20));
        }
        return file;
    }

    private void addInfoRow(Table t, String label, String value,
                            PdfFont bold, PdfFont regular) {
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(label).setFont(bold).setFontSize(9))
            .setBorder(null).setPaddingBottom(4));
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(value).setFont(regular).setFontSize(9))
            .setBorder(null).setPaddingBottom(4));
    }

    private void addTableRow(Table t, PdfFont bold, PdfFont regular, DeviceRgb bg,
                             String... vals) {
        for (String v : vals) {
            com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(v != null ? v : "").setFont(regular).setFontSize(8))
                .setPadding(4)
                .setBorderTop(new SolidBorder(new DeviceRgb(226, 232, 240), 0.3f))
                .setBorderBottom(null).setBorderLeft(null).setBorderRight(null);
            if (bg != null) cell.setBackgroundColor(bg);
            t.addCell(cell);
        }
    }

    private void addFirmaCol(Table t, PdfFont bold, PdfFont regular, String titulo, String nombre) {
        com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph("\n\n\n")
                .setBorderBottom(new SolidBorder(new DeviceRgb(15, 23, 42), 1)))
            .add(new Paragraph(titulo).setFont(bold).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(4))
            .add(new Paragraph(nombre).setFont(regular).setFontSize(7)
                .setFontColor(COLOR_MUTED).setTextAlignment(TextAlignment.CENTER))
            .setBorder(null);
        t.addCell(c);
    }

    private String orDash(String v) { return (v != null && !v.isBlank()) ? v : "—"; }
}
