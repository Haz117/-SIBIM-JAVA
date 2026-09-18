package com.sibim.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Handles export of the Organigrama (org-chart / area distribution) to PDF and CSV.
 * Extends {@link ReporteService} to inherit shared PDF/CSV helpers.
 */
public class ReporteOrganigramaService extends ReporteService {

    public ReporteOrganigramaService() { super(); }

    /** precioVenta × stockActual entirely in BigDecimal — mixing in double here would
     *  drift the patrimonial totals shown in this report away from the BigDecimal-based
     *  sums the rest of ReporteService's exports use for the same figure. */
    private static java.math.BigDecimal valorProducto(Producto p) {
        java.math.BigDecimal v = p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
        return v.multiply(java.math.BigDecimal.valueOf(p.getStockActual()));
    }

    /** Generates a structured organigrama PDF — one section per area with bienes table. */
    public File exportOrganigrama(Map<String, List<Producto>> porArea) throws Exception {
        File file = tempFile("organigrama_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        String generadoEn = FormatUtils.formatDateTime(LocalDateTime.now());

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, com.itextpdf.kernel.geom.PageSize.A4)) {

            String folio = generateFolio("ORG");
            doc.setMargins(0, 36, 36, 36);

            PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);

            DeviceRgb indigo  = new DeviceRgb(162, 35, 45);
            DeviceRgb dark    = new DeviceRgb(17,  24,  39);
            DeviceRgb muted   = new DeviceRgb(107, 114, 128);
            DeviceRgb bgLight = new DeviceRgb(252, 240, 241);
            DeviceRgb bgAlt   = new DeviceRgb(254, 247, 247);
            DeviceRgb white   = new DeviceRgb(255, 255, 255);

            // ── Header band ────────────────────────────────────────────
            Table header = new Table(new float[]{1f}).useAllAvailableWidth();
            header.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(orgName().toUpperCase())
                    .setFont(bold).setFontSize(9f).setFontColor(white).setMargin(0))
                .add(new Paragraph("ORGANIGRAMA DE BIENES MUNICIPALES")
                    .setFont(bold).setFontSize(15f).setFontColor(white).setMarginTop(2).setMarginBottom(2))
                .add(new Paragraph("Distribución de bienes patrimoniales por secretaría y dirección — " + generadoEn)
                    .setFont(regular).setFontSize(8.5f).setFontColor(bgLight).setMargin(0))
                .setBackgroundColor(indigo).setPadding(18)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            doc.add(header);
            doc.add(spacer(14));

            // ── Summary stats ──────────────────────────────────────────
            int totalAreas  = porArea.size();
            int totalBienes = porArea.values().stream().mapToInt(List::size).sum();
            java.math.BigDecimal totalValor = porArea.values().stream()
                .flatMap(List::stream)
                .map(ReporteOrganigramaService::valorProducto)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

            Table statsTable = new Table(new float[]{1f, 1f, 1f}).useAllAvailableWidth();
            for (String[] stat : new String[][]{
                    {"Áreas con bienes",   String.valueOf(totalAreas)},
                    {"Total de bienes",    String.valueOf(totalBienes)},
                    {"Valor patrimonial",  FormatUtils.formatCurrency(totalValor)}}) {
                statsTable.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(stat[0]).setFont(regular).setFontSize(8f).setFontColor(muted).setMarginBottom(2))
                    .add(new Paragraph(stat[1]).setFont(bold).setFontSize(13f).setFontColor(dark))
                    .setBackgroundColor(bgLight).setPadding(12)
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            }
            doc.add(statsTable);
            doc.add(spacer(16));

            // ── One section per area ────────────────────────────────────
            List<String> areas = new java.util.ArrayList<>(porArea.keySet());
            java.util.Collections.sort(areas);

            for (String area : areas) {
                List<Producto> bienes = porArea.get(area);
                int cnt = bienes.size();
                java.math.BigDecimal valorArea = bienes.stream()
                    .map(ReporteOrganigramaService::valorProducto)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                // Area header row
                Table areaHeader = new Table(new float[]{1f}).useAllAvailableWidth();
                areaHeader.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(area.toUpperCase())
                        .setFont(bold).setFontSize(9.5f).setFontColor(indigo).setMargin(0))
                    .add(new Paragraph(cnt + " bien" + (cnt != 1 ? "es" : "") +
                            (valorArea.signum() > 0 ? "  ·  Valor: " + FormatUtils.formatCurrency(valorArea) : ""))
                        .setFont(regular).setFontSize(8f).setFontColor(muted).setMarginTop(1).setMarginBottom(0))
                    .setBackgroundColor(bgLight).setPadding(8)
                    .setBorderLeft(new com.itextpdf.layout.borders.SolidBorder(indigo, 3))
                    .setBorderTop(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderRight(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER));
                doc.add(areaHeader);

                // Bienes table
                Table t = createPdfTable(
                    new String[]{"Nombre del bien", "Código", "Categoría", "Estado", "Stock", "Valor compra"},
                    new float[]{3f, 1.2f, 1.6f, 1f, 0.7f, 1.4f});
                for (int i = 0; i < bienes.size(); i++) {
                    Producto p = bienes.get(i);
                    DeviceRgb bg = (i % 2 == 1) ? bgAlt : white;
                    String[] vals = {
                        p.getNombre() != null ? p.getNombre() : "—",
                        p.getCodigo() != null ? p.getCodigo() : "—",
                        p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "—",
                        p.getEstado() != null ? p.getEstado().getEtiqueta() : "—",
                        String.valueOf(p.getStockActual()),
                        p.getPrecioCompra() != null ? FormatUtils.formatCurrency(p.getPrecioCompra()) : "—"
                    };
                    for (String v : vals) {
                        t.addCell(new com.itextpdf.layout.element.Cell()
                            .add(new Paragraph(v).setFont(regular).setFontSize(8f))
                            .setBackgroundColor(bg).setPadding(5)
                            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
                    }
                }
                doc.add(t);
                doc.add(spacer(12));
            }

            // ── Footer ──────────────────────────────────────────────────
            doc.add(spacer(8));
            addFirmasBlock(doc,
                new String[]{"ELABORÓ", getCurrentUserName(), "Director de Recursos Materiales"},
                new String[]{"VO.BO.", "_______________", "Secretario General Municipal"});
            addPdfFooter(doc, totalBienes, folio);
        }
        return file;
    }

    /** Flat CSV — one row per bien — from the already-filtered map that
     *  OrganigramaController holds in memory (no extra DB call). */
    public File exportOrganigramaCsv(Map<String, List<Producto>> porArea) throws Exception {
        File file = tempFile("organigrama_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".csv");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
            pw.println("﻿" + "Área,Nombre,Código,Categoría,Stock actual,Estado,Valor compra");
            for (Map.Entry<String, List<Producto>> e : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                for (Producto p : e.getValue()) {
                    pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",%.2f%n",
                        esc(e.getKey()),
                        esc(p.getNombre()),
                        esc(p.getCodigo()),
                        esc(p.getCategoriaNombre()),
                        p.getStockActual(),
                        p.getEstado() != null ? p.getEstado().name() : "",
                        p.getPrecioCompra() != null ? p.getPrecioCompra() : java.math.BigDecimal.ZERO);
                }
            }
        }
        return file;
    }
}
