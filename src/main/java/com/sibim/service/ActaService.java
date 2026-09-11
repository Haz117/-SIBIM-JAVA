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
import com.sibim.model.ActaEntregaRecepcion;
import com.sibim.model.Producto;
import com.sibim.repository.ActaRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActaService {

    private final ActaRepository actaRepo       = new ActaRepository();
    private final ProductoRepository productoRepo = new ProductoRepository();
    private final ConfiguracionRepository cfgRepo = new ConfiguracionRepository();

    private static final DeviceRgb COLOR_HEADER  = new DeviceRgb(76, 29, 149);
    private static final DeviceRgb COLOR_SUBHEAD = new DeviceRgb(241, 245, 249);
    private static final DeviceRgb COLOR_AREA_BG = new DeviceRgb(224, 231, 255);
    private static final DeviceRgb COLOR_MUTED   = new DeviceRgb(100, 116, 139);
    private static final DateTimeFormatter FMT   = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<ActaEntregaRecepcion> getAll() throws SQLException { return actaRepo.findAll(); }

    public ActaEntregaRecepcion generar(String adminSaliente, String cargoSaliente,
                                       String adminEntrante, String cargoEntrante,
                                       LocalDate fechaEntrega, String observaciones) throws Exception {
        if (adminSaliente == null || adminSaliente.isBlank())
            throw new IllegalArgumentException("El nombre del funcionario saliente es obligatorio");
        if (adminEntrante == null || adminEntrante.isBlank())
            throw new IllegalArgumentException("El nombre del funcionario entrante es obligatorio");
        if (fechaEntrega == null)
            throw new IllegalArgumentException("La fecha de entrega es obligatoria");

        List<Producto> inventario = productoRepo.findAll();
        BigDecimal valorTotal = inventario.stream()
            .map(Producto::getValorTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        ActaEntregaRecepcion acta = new ActaEntregaRecepcion();
        acta.setAdminSaliente(adminSaliente.trim());
        acta.setCargoSaliente(cargoSaliente != null ? cargoSaliente.trim() : null);
        acta.setAdminEntrante(adminEntrante.trim());
        acta.setCargoEntrante(cargoEntrante != null ? cargoEntrante.trim() : null);
        acta.setFechaEntrega(fechaEntrega);
        acta.setObservaciones(observaciones != null ? observaciones.trim() : null);
        acta.setTotalBienes(inventario.size());
        acta.setValorTotal(valorTotal);

        return actaRepo.save(acta);
    }

    public File exportarPdf(ActaEntregaRecepcion acta) throws Exception {
        List<Producto> inventario = productoRepo.findAll();
        return generarPdf(acta, inventario);
    }

    private String orgName() {
        String org = cfgRepo.get("nombre_ayuntamiento", "");
        String mun = cfgRepo.get("municipio", "");
        if (org.isBlank()) return "H. Ayuntamiento Municipal";
        return mun.isBlank() ? org : org + " · " + mun;
    }

    private File generarPdf(ActaEntregaRecepcion acta, List<Producto> inventario) throws IOException {
        File file = File.createTempFile("sibim_acta_", ".pdf");
        file.deleteOnExit();

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb dark  = new DeviceRgb(15, 23, 42);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            doc.setMargins(36, 36, 36, 36);

            // ── Header institucional ──
            Table headerTable = new Table(1).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell hCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("ACTA DE ENTREGA-RECEPCIÓN")
                    .setFont(bold).setFontSize(16).setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(orgName())
                    .setFont(regular).setFontSize(9)
                    .setFontColor(new DeviceRgb(200, 210, 240))
                    .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(COLOR_HEADER).setPadding(14).setBorder(null);
            headerTable.addCell(hCell);
            doc.add(headerTable);

            // ── Folio + Fecha ──
            doc.add(new Paragraph()
                .add(new com.itextpdf.layout.element.Text("Acta No.: ").setFont(bold))
                .add(new com.itextpdf.layout.element.Text(acta.getNumero()).setFont(regular))
                .add(new com.itextpdf.layout.element.Text("     Fecha de Entrega: ").setFont(bold))
                .add(new com.itextpdf.layout.element.Text(acta.getFechaEntrega().format(FMT)).setFont(regular))
                .setFontSize(9).setFontColor(COLOR_MUTED).setMarginTop(8).setMarginBottom(4));

            // ── Funcionarios ──
            Table funcTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth().setMarginTop(8);
            addFuncionarioBlock(funcTable, bold, regular, "FUNCIONARIO SALIENTE",
                acta.getAdminSaliente(), acta.getCargoSaliente());
            addFuncionarioBlock(funcTable, bold, regular, "FUNCIONARIO ENTRANTE",
                acta.getAdminEntrante(), acta.getCargoEntrante());
            doc.add(funcTable);

            // ── Resumen ──
            BigDecimal valorTotal = inventario.stream()
                .map(Producto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, List<Producto>> porArea = inventario.stream()
                .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));

            doc.add(new Paragraph("RESUMEN DEL INVENTARIO PATRIMONIAL")
                .setFont(bold).setFontSize(9).setFontColor(dark)
                .setMarginTop(14).setMarginBottom(4));

            Table resumenTable = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1}))
                .useAllAvailableWidth();
            String[] resHeaders = {"Área / Dirección", "Total Bienes", "Agotados", "Valor Patrimonial"};
            for (String h : resHeaders) {
                resumenTable.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_HEADER).setPadding(5).setBorder(null));
            }
            int rowIdx = 0;
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                List<Producto> ps = entry.getValue();
                BigDecimal valorArea = ps.stream().map(Producto::getValorTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                long agotados = ps.stream()
                    .filter(p -> p.getEstado() == com.sibim.model.enums.EstadoProducto.AGOTADO).count();
                boolean alt = rowIdx++ % 2 == 0;
                DeviceRgb bg = alt ? COLOR_SUBHEAD : null;
                addResumenRow(resumenTable, regular, bg,
                    entry.getKey(),
                    String.valueOf(ps.size()),
                    String.valueOf(agotados),
                    FormatUtils.formatCurrency(valorArea));
            }
            // Totales
            com.itextpdf.layout.element.Cell tLbl = new com.itextpdf.layout.element.Cell(1, 3)
                .add(new Paragraph("TOTAL GENERAL").setFont(bold).setFontSize(8)
                    .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(COLOR_AREA_BG).setPadding(5).setBorder(null);
            com.itextpdf.layout.element.Cell tVal = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(FormatUtils.formatCurrency(valorTotal)).setFont(bold).setFontSize(8))
                .setBackgroundColor(COLOR_AREA_BG).setPadding(5).setBorder(null);
            resumenTable.addCell(tLbl);
            resumenTable.addCell(tVal);
            doc.add(resumenTable);

            // ── Detalle por área ──
            doc.add(new Paragraph("INVENTARIO DETALLADO POR ÁREA")
                .setFont(bold).setFontSize(9).setFontColor(dark)
                .setMarginTop(16).setMarginBottom(4));

            String[] detHeaders = {"Código", "Nombre del Bien", "Resguardante", "Stock", "Estado", "Valor"};
            float[] detWidths   = {1.2f, 3f, 2f, 0.7f, 1.2f, 1.3f};
            Table detTable = new Table(detWidths).useAllAvailableWidth();
            for (String h : detHeaders) {
                detTable.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(7.5f).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(COLOR_HEADER).setPadding(4).setBorder(null));
            }

            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                // Area separator row
                com.itextpdf.layout.element.Cell areaCell = new com.itextpdf.layout.element.Cell(1, 6)
                    .add(new Paragraph(entry.getKey()).setFont(bold).setFontSize(8))
                    .setBackgroundColor(COLOR_AREA_BG).setPadding(4).setBorder(null);
                detTable.addCell(areaCell);

                int rIdx = 0;
                for (Producto p : entry.getValue()) {
                    boolean alt = rIdx++ % 2 == 0;
                    DeviceRgb bg = alt ? COLOR_SUBHEAD : null;
                    addDetRow(detTable, regular, bg,
                        orDash(p.getCodigo()),
                        p.getNombre(),
                        orDash(p.getResguardante()),
                        String.valueOf(p.getStockActual()),
                        p.getEstado().getEtiqueta(),
                        FormatUtils.formatCurrency(p.getValorTotal()));
                }
            }
            doc.add(detTable);

            // ── Observaciones ──
            if (acta.getObservaciones() != null && !acta.getObservaciones().isBlank()) {
                doc.add(new Paragraph("Observaciones: " + acta.getObservaciones())
                    .setFont(regular).setFontSize(8).setFontColor(COLOR_MUTED).setMarginTop(10));
            }

            // ── Firmas ──
            doc.add(new Paragraph("\n\n"));
            Table firmas = new Table(UnitValue.createPercentArray(new float[]{1, 0.4f, 1}))
                .useAllAvailableWidth().setMarginTop(16);
            addFirmaCol(firmas, bold, regular,
                "ENTREGA",
                acta.getAdminSaliente() + (acta.getCargoSaliente() != null
                    ? "\n" + acta.getCargoSaliente() : ""));
            firmas.addCell(new com.itextpdf.layout.element.Cell().setBorder(null));
            addFirmaCol(firmas, bold, regular,
                "RECIBE",
                acta.getAdminEntrante() + (acta.getCargoEntrante() != null
                    ? "\n" + acta.getCargoEntrante() : ""));
            doc.add(firmas);

            // Footer
            doc.add(new Paragraph(
                "SIBIM · " + orgName() + " · Generado el " + LocalDate.now().format(FMT)
                + " · " + inventario.size() + " bienes registrados")
                .setFont(regular).setFontSize(7).setFontColor(COLOR_MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(16));
        }
        return file;
    }

    private void addFuncionarioBlock(Table t, PdfFont bold, PdfFont regular,
                                     String titulo, String nombre, String cargo) {
        com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(titulo).setFont(bold).setFontSize(8).setFontColor(COLOR_MUTED))
            .add(new Paragraph(nombre).setFont(bold).setFontSize(10))
            .add(new Paragraph(cargo != null && !cargo.isBlank() ? cargo : " ")
                .setFont(regular).setFontSize(9).setFontColor(COLOR_MUTED))
            .setBackgroundColor(COLOR_SUBHEAD).setPadding(10).setBorder(null).setMargin(4);
        t.addCell(c);
    }

    private void addResumenRow(Table t, PdfFont regular, DeviceRgb bg, String... vals) {
        for (String v : vals) {
            com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(v).setFont(regular).setFontSize(8))
                .setPadding(4)
                .setBorderTop(new SolidBorder(new DeviceRgb(226, 232, 240), 0.3f))
                .setBorderBottom(null).setBorderLeft(null).setBorderRight(null);
            if (bg != null) c.setBackgroundColor(bg);
            t.addCell(c);
        }
    }

    private void addDetRow(Table t, PdfFont regular, DeviceRgb bg, String... vals) {
        addResumenRow(t, regular, bg, vals);
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
