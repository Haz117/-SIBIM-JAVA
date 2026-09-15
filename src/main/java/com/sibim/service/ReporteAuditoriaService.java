package com.sibim.service;

import com.sibim.model.AuditLog;
import com.sibim.util.FormatUtils;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Table;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class ReporteAuditoriaService extends ReporteService {

    public ReporteAuditoriaService() { super(); }

    public File exportAuditoriaPdf(List<AuditLog> logs,
                                   String busqueda, String entidad,
                                   LocalDate desde, LocalDate hasta) throws Exception {
        if (logs.isEmpty()) return null;
        File file = tempFile("auditoria", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            addPdfHeader(doc, "Registro de Auditoría", desde, hasta);
            String[] headers = {"Entidad", "Nombre", "Acción", "Usuario", "Detalle", "Fecha"};
            float[] widths = {1.5f, 1.5f, 1.2f, 1.5f, 3f, 2f};
            Table table = createPdfTable(headers, widths);
            for (AuditLog l : logs) {
                table.addCell(cell(l.getEntidad() != null ? l.getEntidad() : ""));
                table.addCell(cell(l.getEntidadNombre() != null ? l.getEntidadNombre() : ""));
                table.addCell(cell(l.getAccion() != null ? l.getAccion() : ""));
                table.addCell(cell(l.getUsuarioNombre() != null ? l.getUsuarioNombre() : ""));
                table.addCell(cell(l.getDetalle() != null ? l.getDetalle() : ""));
                table.addCell(cell(l.getCreadoEn() != null ? FormatUtils.formatDateTime(l.getCreadoEn()) : ""));
            }
            doc.add(table);
            addPdfFooter(doc, logs.size());
        }
        return file;
    }

    public File exportAuditoriaCsv(List<AuditLog> logs) throws Exception {
        if (logs.isEmpty()) return null;
        File file = tempFile("auditoria", ".csv");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            pw.println("Entidad,Nombre,Accion,Usuario,Detalle,Fecha");
            for (AuditLog l : logs) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(l.getEntidad()), esc(l.getEntidadNombre()),
                    esc(l.getAccion()), esc(l.getUsuarioNombre()),
                    esc(l.getDetalle()), l.getCreadoEn() != null
                        ? FormatUtils.formatDateTime(l.getCreadoEn()) : "");
            }
        }
        return file;
    }

    public File exportAuditoriaPdf() throws Exception {
        java.util.List<com.sibim.model.Prestamo> todosPrestamos;
        java.util.List<com.sibim.model.Resguardo> resguardos;
        try {
            todosPrestamos = new com.sibim.repository.PrestamoRepository().findAll();
            resguardos     = new com.sibim.repository.ResguardoRepository().findAll();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar datos para el reporte de auditoría", e);
        }

        java.util.List<com.sibim.model.Prestamo> prestamosAbiertos = todosPrestamos.stream()
            .filter(p -> !com.sibim.model.Prestamo.ESTADO_DEVUELTO.equals(p.getEstado()))
            .sorted(java.util.Comparator
                .comparing((com.sibim.model.Prestamo p) -> com.sibim.model.Prestamo.ESTADO_VENCIDO.equals(p.getEstado()) ? 0 : 1)
                .thenComparing(p -> p.getFechaDevolucionPrevista() != null
                    ? p.getFechaDevolucionPrevista() : java.time.LocalDate.MAX))
            .toList();
        java.util.List<com.sibim.model.Resguardo> resguardosActivos = resguardos.stream()
            .filter(r -> com.sibim.model.Resguardo.ESTADO_ACTIVO.equals(r.getEstado()))
            .toList();

        com.itextpdf.kernel.colors.DeviceRgb colorPurple  = new com.itextpdf.kernel.colors.DeviceRgb(76, 29, 149);
        com.itextpdf.kernel.colors.DeviceRgb colorMuted   = new com.itextpdf.kernel.colors.DeviceRgb(100, 116, 139);
        com.itextpdf.kernel.colors.DeviceRgb colorAmber   = new com.itextpdf.kernel.colors.DeviceRgb(146, 64, 14);
        com.itextpdf.kernel.colors.DeviceRgb colorSubhead = new com.itextpdf.kernel.colors.DeviceRgb(241, 245, 249);
        java.time.format.DateTimeFormatter fmtD = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

        com.itextpdf.kernel.font.PdfFont bold    = com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        com.itextpdf.kernel.font.PdfFont regular = com.itextpdf.kernel.font.PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);

        String orgName;
        try {
            String org = new com.sibim.repository.ConfiguracionRepository().get("nombre_ayuntamiento", "");
            String mun = new com.sibim.repository.ConfiguracionRepository().get("municipio", "");
            orgName = org.isBlank() ? "H. Ayuntamiento Municipal"
                    : mun.isBlank() ? org : org + " · " + mun;
        } catch (Exception e) { orgName = "H. Ayuntamiento Municipal"; }

        File file = tempFile("auditoria_consolidada", ".pdf");
        try (com.itextpdf.kernel.pdf.PdfWriter   writer  = new com.itextpdf.kernel.pdf.PdfWriter(file.getAbsolutePath());
             com.itextpdf.kernel.pdf.PdfDocument pdfDoc  = new com.itextpdf.kernel.pdf.PdfDocument(writer);
             com.itextpdf.layout.Document        doc     = new com.itextpdf.layout.Document(pdfDoc, com.itextpdf.kernel.geom.PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            com.itextpdf.layout.element.Table headerTbl = new com.itextpdf.layout.element.Table(1).useAllAvailableWidth();
            headerTbl.addCell(new com.itextpdf.layout.element.Cell()
                .add(new com.itextpdf.layout.element.Paragraph("REPORTE DE AUDITORÍA DE BIENES")
                    .setFont(bold).setFontSize(15).setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                .add(new com.itextpdf.layout.element.Paragraph(orgName)
                    .setFont(regular).setFontSize(9)
                    .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(200, 210, 240))
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                .add(new com.itextpdf.layout.element.Paragraph(
                    "Generado: " + java.time.LocalDate.now().format(fmtD))
                    .setFont(regular).setFontSize(8)
                    .setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(180, 190, 220))
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                .setBackgroundColor(colorPurple).setPadding(14).setBorder(null));
            doc.add(headerTbl);

            doc.add(new com.itextpdf.layout.element.Paragraph("RESUMEN")
                .setFont(bold).setFontSize(9).setMarginTop(14).setMarginBottom(4));
            com.itextpdf.layout.element.Table sumTbl = new com.itextpdf.layout.element.Table(
                com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{2, 1, 2, 1}))
                .useAllAvailableWidth();
            addAuditCell(sumTbl, bold, regular, "Resguardos activos",  String.valueOf(resguardosActivos.size()), colorSubhead);
            addAuditCell(sumTbl, bold, regular, "Préstamos abiertos",  String.valueOf(prestamosAbiertos.size()), colorSubhead);
            addAuditCell(sumTbl, bold, regular, "Préstamos vencidos",
                String.valueOf(prestamosAbiertos.stream()
                    .filter(p -> com.sibim.model.Prestamo.ESTADO_VENCIDO.equals(p.getEstado())).count()),
                colorSubhead);
            addAuditCell(sumTbl, bold, regular, "Total resguardos", String.valueOf(resguardos.size()), colorSubhead);
            doc.add(sumTbl);

            if (!resguardosActivos.isEmpty()) {
                doc.add(new com.itextpdf.layout.element.Paragraph("RESGUARDOS ACTIVOS (" + resguardosActivos.size() + ")")
                    .setFont(bold).setFontSize(9).setMarginTop(18).setMarginBottom(4));
                com.itextpdf.layout.element.Table rsgTbl = new com.itextpdf.layout.element.Table(
                    com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{1.2f, 2f, 1.5f, 1f}))
                    .useAllAvailableWidth();
                for (String h : new String[]{"Folio", "Resguardante", "Área", "Fecha"}) {
                    rsgTbl.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(h).setFont(bold).setFontSize(8)
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                        .setBackgroundColor(colorPurple).setPadding(5).setBorder(null));
                }
                int rowIdx = 0;
                for (com.sibim.model.Resguardo r : resguardosActivos) {
                    com.itextpdf.kernel.colors.DeviceRgb bg = rowIdx++ % 2 == 0 ? null : colorSubhead;
                    for (String v : new String[]{
                        r.getNumero() != null ? r.getNumero() : "—",
                        r.getResguardanteNombre() != null ? r.getResguardanteNombre() : "—",
                        r.getResguardanteArea() != null ? r.getResguardanteArea() : "—",
                        r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate().format(fmtD) : "—"
                    }) {
                        com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
                            .add(new com.itextpdf.layout.element.Paragraph(v).setFont(regular).setFontSize(8))
                            .setPadding(4).setBorderTop(null).setBorderLeft(null).setBorderRight(null)
                            .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(
                                new com.itextpdf.kernel.colors.DeviceRgb(226, 232, 240), 0.5f));
                        if (bg != null) c.setBackgroundColor(bg);
                        rsgTbl.addCell(c);
                    }
                }
                doc.add(rsgTbl);
            }

            if (!prestamosAbiertos.isEmpty()) {
                doc.add(new com.itextpdf.layout.element.Paragraph("PRÉSTAMOS ABIERTOS (" + prestamosAbiertos.size() + ")")
                    .setFont(bold).setFontSize(9).setMarginTop(18).setMarginBottom(4));
                com.itextpdf.layout.element.Table prsTbl = new com.itextpdf.layout.element.Table(
                    com.itextpdf.layout.properties.UnitValue.createPercentArray(new float[]{1f, 2f, 1.5f, 1.2f, 0.8f}))
                    .useAllAvailableWidth();
                for (String h : new String[]{"Folio", "Bien", "Responsable", "Dev. Prevista", "Estado"}) {
                    prsTbl.addHeaderCell(new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(h).setFont(bold).setFontSize(8)
                            .setFontColor(com.itextpdf.kernel.colors.ColorConstants.WHITE))
                        .setBackgroundColor(colorPurple).setPadding(5).setBorder(null));
                }
                int pRowIdx = 0;
                for (com.sibim.model.Prestamo p : prestamosAbiertos) {
                    boolean vencido = com.sibim.model.Prestamo.ESTADO_VENCIDO.equals(p.getEstado());
                    com.itextpdf.kernel.colors.DeviceRgb bg = vencido
                        ? new com.itextpdf.kernel.colors.DeviceRgb(254, 243, 199)
                        : (pRowIdx % 2 == 0 ? null : colorSubhead);
                    pRowIdx++;
                    String[] vals = {
                        p.getNumero() != null ? p.getNumero() : "—",
                        p.getProductoNombre() != null ? p.getProductoNombre() : "—",
                        p.getResponsableNombre() != null ? p.getResponsableNombre() : "—",
                        p.getFechaDevolucionPrevista() != null ? p.getFechaDevolucionPrevista().format(fmtD) : "—",
                        p.getEstado() != null ? p.getEstado() : "—"
                    };
                    for (int vi = 0; vi < vals.length; vi++) {
                        com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
                            .add(new com.itextpdf.layout.element.Paragraph(vals[vi]).setFont(
                                (vencido && vi == 4) ? bold : regular).setFontSize(8))
                            .setPadding(4).setBorderTop(null).setBorderLeft(null).setBorderRight(null)
                            .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(
                                new com.itextpdf.kernel.colors.DeviceRgb(226, 232, 240), 0.5f));
                        if (bg != null) c.setBackgroundColor(bg);
                        if (vencido && vi == 4) c.setFontColor(colorAmber);
                        prsTbl.addCell(c);
                    }
                }
                doc.add(prsTbl);
            }

            if (resguardosActivos.isEmpty() && prestamosAbiertos.isEmpty()) {
                doc.add(new com.itextpdf.layout.element.Paragraph(
                    "No hay resguardos activos ni préstamos abiertos registrados en el sistema.")
                    .setFont(regular).setFontSize(9).setFontColor(colorMuted).setMarginTop(20));
            }

            doc.add(new com.itextpdf.layout.element.Paragraph(
                "Generado por SIBIM · " + orgName + " · " + java.time.LocalDate.now().format(fmtD))
                .setFont(regular).setFontSize(7).setFontColor(colorMuted)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER).setMarginTop(20));
        }
        return file;
    }

    private void addAuditCell(com.itextpdf.layout.element.Table t,
                               com.itextpdf.kernel.font.PdfFont bold,
                               com.itextpdf.kernel.font.PdfFont regular,
                               String label, String value,
                               com.itextpdf.kernel.colors.DeviceRgb bg) {
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new com.itextpdf.layout.element.Paragraph(label).setFont(bold).setFontSize(9))
            .setBackgroundColor(bg).setPadding(6).setBorder(null));
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new com.itextpdf.layout.element.Paragraph(value).setFont(regular).setFontSize(9))
            .setPadding(6).setBorder(null));
    }
}
