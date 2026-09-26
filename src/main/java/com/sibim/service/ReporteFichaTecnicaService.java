package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
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
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ReporteFichaTecnicaService extends ReporteService {

    public ReporteFichaTecnicaService() { super(); }

    public File exportFichaTecnica(Producto p, List<Movimiento> movimientos) throws Exception {
        String safeName = p.getCodigo() != null ? p.getCodigo().replaceAll("[^a-zA-Z0-9_\\-]", "_") : "bien";
        File file = tempFile("ficha_" + safeName, ".pdf");

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        DeviceRgb indigo  = new DeviceRgb(99,  102, 241);
        DeviceRgb dark    = new DeviceRgb(15,  23,  42);
        DeviceRgb muted   = new DeviceRgb(100, 116, 139);
        DeviceRgb bgLight = new DeviceRgb(241, 245, 249);
        DeviceRgb bgAlt   = new DeviceRgb(248, 250, 252);
        DeviceRgb green   = new DeviceRgb(22,  163, 74);
        DeviceRgb amber   = new DeviceRgb(180, 83,  9);
        DeviceRgb red     = new DeviceRgb(185, 28,  28);
        DeviceRgb indigo2 = new DeviceRgb(199, 210, 254);

        String generadoEn = "Generado el " + LocalDate.now().format(FMT);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(28, 36, 28, 36);

            // Header band
            Table headerBand = new Table(new float[]{3f, 1f}).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell orgCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(orgName())
                    .setFont(bold).setFontSize(10.5f).setFontColor(ColorConstants.WHITE).setMarginBottom(3))
                .add(new Paragraph("FICHA TÉCNICA DE BIEN PATRIMONIAL")
                    .setFont(bold).setFontSize(15f).setFontColor(ColorConstants.WHITE).setMarginBottom(2))
                .add(new Paragraph("Sistema Integral de Bienes Municipales  ·  SIBIM")
                    .setFont(regular).setFontSize(8f).setFontColor(indigo2))
                .setBackgroundColor(indigo).setPadding(14)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            com.itextpdf.layout.element.Cell dateCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(generadoEn)
                    .setFont(regular).setFontSize(8f).setFontColor(indigo2)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT))
                .setBackgroundColor(indigo).setPadding(14)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.BOTTOM);
            headerBand.addCell(orgCell);
            headerBand.addCell(dateCell);
            doc.add(headerBand);
            doc.add(spacer(4));

            // Code + Status band
            DeviceRgb estadoColor = p.getEstado() == EstadoProducto.ACTIVO ? green
                : p.getEstado() == EstadoProducto.BAJO_STOCK ? amber : red;
            Table codeBand = new Table(new float[]{1f, 1f}).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell codeCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("CÓDIGO").setFont(regular).setFontSize(7.5f).setFontColor(muted).setMarginBottom(2))
                .add(new Paragraph(p.getCodigo() != null ? p.getCodigo() : "—")
                    .setFont(bold).setFontSize(20f).setFontColor(dark))
                .setBackgroundColor(bgLight).setPadding(12)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            com.itextpdf.layout.element.Cell statusCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("ESTADO").setFont(regular).setFontSize(7.5f).setFontColor(muted).setMarginBottom(2))
                .add(new Paragraph(p.getEstado().getEtiqueta().toUpperCase())
                    .setFont(bold).setFontSize(16f).setFontColor(estadoColor).setMarginBottom(2))
                .add(new Paragraph("Stock: " + p.getStockActual()
                    + "  ·  Mín: " + p.getStockMinimo()
                    + "  ·  Máx: " + p.getStockMaximo())
                    .setFont(regular).setFontSize(8.5f).setFontColor(muted))
                .setBackgroundColor(bgLight).setPadding(12)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
            codeBand.addCell(codeCell);
            codeBand.addCell(statusCell);
            doc.add(codeBand);
            doc.add(spacer(8));

            // Identification
            doc.add(sectionTitle("IDENTIFICACIÓN", bold, indigo));
            Table idTable = new Table(new float[]{1f, 2.5f}).useAllAvailableWidth();
            addRow(idTable, "Nombre",          p.getNombre(),          bold, regular, muted, bgAlt, false);
            addRow(idTable, "Categoría",        p.getCategoriaNombre(), bold, regular, muted, bgAlt, true);
            addRow(idTable, "Área / Dirección", p.getArea(),           bold, regular, muted, bgAlt, false);
            addRow(idTable, "Resguardante",     p.getResguardante(),   bold, regular, muted, bgAlt, true);
            if (p.getMarca() != null && !p.getMarca().isBlank())
                addRow(idTable, "Marca", p.getMarca(), bold, regular, muted, bgAlt, false);
            if (p.getModelo() != null && !p.getModelo().isBlank())
                addRow(idTable, "Modelo", p.getModelo(), bold, regular, muted, bgAlt, true);
            if (p.getNumeroSerie() != null && !p.getNumeroSerie().isBlank())
                addRow(idTable, "N° de Serie", p.getNumeroSerie(), bold, regular, muted, bgAlt, false);
            if (p.getUbicacion() != null && !p.getUbicacion().isBlank())
                addRow(idTable, "Ubicación", p.getUbicacion(), bold, regular, muted, bgAlt, true);
            doc.add(idTable);
            doc.add(spacer(8));

            // Patrimonial value
            doc.add(sectionTitle("VALOR PATRIMONIAL", bold, indigo));
            Table valTable = new Table(new float[]{1f, 2.5f}).useAllAvailableWidth();
            addRow(valTable, "Precio de adquisición", FormatUtils.formatCurrency(p.getPrecioCompra()), bold, regular, muted, bgAlt, false);
            addRow(valTable, "Valor patrimonial",      FormatUtils.formatCurrency(p.getValorTotal()),   bold, regular, muted, bgAlt, true);
            if (p.getFechaAdquisicion() != null)
                addRow(valTable, "Fecha de adquisición", FormatUtils.formatDate(p.getFechaAdquisicion()), bold, regular, muted, bgAlt, false);
            if (p.getProveedor() != null && !p.getProveedor().isBlank())
                addRow(valTable, "Proveedor", p.getProveedor(), bold, regular, muted, bgAlt, false);
            if (p.getVidaUtilAnios() != null) {
                addRow(valTable, "Vida útil", p.getVidaUtilAnios() + " año(s)", bold, regular, muted, bgAlt, true);
                java.math.BigDecimal dep = p.getValorDepreciado();
                Integer pct = p.getPorcentajeDepreciado();
                if (dep != null && pct != null)
                    addRow(valTable, "Valor actual (dep.)",
                        FormatUtils.formatCurrency(dep) + "  (" + pct + "% depreciado)",
                        bold, regular, muted, bgAlt, false);
            }
            doc.add(valTable);

            // Description (optional)
            if (p.getDescripcion() != null && !p.getDescripcion().isBlank()) {
                doc.add(spacer(8));
                doc.add(sectionTitle("DESCRIPCIÓN", bold, indigo));
                doc.add(new Paragraph(p.getDescripcion())
                    .setFont(regular).setFontSize(9.5f).setFontColor(dark)
                    .setMarginLeft(4));
            }

            // Movement history
            if (!movimientos.isEmpty()) {
                doc.add(spacer(8));
                int shown = Math.min(movimientos.size(), 8);
                doc.add(sectionTitle("HISTORIAL DE MOVIMIENTOS  (últimos " + shown + ")", bold, indigo));
                Table movTable = createPdfTable(
                    new String[]{"Fecha", "Tipo", "Cant.", "Ant.", "Nuevo", "Usuario", "Motivo"},
                    new float[]{1.6f, 1f, 0.55f, 0.55f, 0.65f, 1.2f, 2f});
                for (int i = 0; i < shown; i++) {
                    Movimiento m = movimientos.get(i);
                    movTable.addCell(cellSm(FormatUtils.formatDateTime(m.getCreadoEn()), regular));
                    movTable.addCell(cellSm(m.getTipo().getEtiqueta(), regular));
                    movTable.addCell(cellSm(String.valueOf(m.getCantidad()), regular));
                    movTable.addCell(cellSm(String.valueOf(m.getStockAnterior()), regular));
                    movTable.addCell(cellSm(String.valueOf(m.getStockNuevo()), regular));
                    movTable.addCell(cellSm(m.getUsuarioNombre(), regular));
                    movTable.addCell(cellSm(m.getMotivo() != null ? m.getMotivo() : "—", regular));
                }
                doc.add(movTable);
            }

            // Signature block
            doc.add(spacer(22));
            Table sigTable = new Table(new float[]{1f, 1f, 1f}).useAllAvailableWidth();
            for (String role : new String[]{"Elaboró", "Revisó", "Autorizó"}) {
                com.itextpdf.layout.element.Cell sigCell = new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph("___________________________")
                        .setFont(regular).setFontSize(10f).setFontColor(muted)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                    .add(new Paragraph(role)
                        .setFont(bold).setFontSize(9f).setFontColor(dark)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                    .add(new Paragraph("Nombre y firma")
                        .setFont(regular).setFontSize(7.5f).setFontColor(muted)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(4);
                sigTable.addCell(sigCell);
            }
            doc.add(sigTable);

            // Footer
            doc.add(spacer(6));
            doc.add(new Paragraph(
                "SIBIM — Sistema Integral de Bienes Municipales  |  " + orgName() + "  |  " + generadoEn)
                .setFont(regular).setFontSize(7.5f).setFontColor(muted)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
        }
        return file;
    }

    public File exportFichasTecnicasMasivas(List<Producto> bienes,
            MovimientoService movimientoService) throws Exception {
        if (bienes == null || bienes.isEmpty())
            throw new IllegalArgumentException("La lista de bienes está vacía — no hay fichas que generar");
        List<String> ids = bienes.stream().map(Producto::getId).toList();
        Map<String, List<Movimiento>> movsByProducto = movimientoService.getByProductoIds(ids);

        File output = tempFile("fichas_tecnicas_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        List<File> temps = new java.util.ArrayList<>();
        try (PdfWriter writer = new PdfWriter(output.getAbsolutePath());
             PdfDocument merged = new PdfDocument(writer)) {
            PdfMerger merger = new PdfMerger(merged);
            for (Producto p : bienes) {
                List<Movimiento> movs = movsByProducto.getOrDefault(p.getId(), List.of());
                File ficha = exportFichaTecnica(p, movs);
                temps.add(ficha);
                try (PdfDocument src = new PdfDocument(new PdfReader(ficha.getAbsolutePath()))) {
                    merger.merge(src, 1, src.getNumberOfPages());
                }
            }
        } finally {
            temps.forEach(File::delete);
        }
        return output;
    }
}
