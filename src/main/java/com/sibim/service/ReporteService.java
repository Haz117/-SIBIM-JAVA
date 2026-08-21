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
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.FormatUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;

public class ReporteService {

    private final ProductoRepository productoRepo = new ProductoRepository();
    private final MovimientoRepository movimientoRepo = new MovimientoRepository();

    private static final DeviceRgb COLOR_HEADER = new DeviceRgb(76, 29, 149); // purple-900
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ───────────────────────────── EXCEL ─────────────────────────────

    public File exportInventarioExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Resguardante", "Stock", "Min", "Max",
                            "Precio Venta", "Valor Total", "Estado", "Proveedor", "Ubicacion", "Fecha Registro"};
        File file = tempFile("inventario", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Inventario");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Producto p : productos) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(p.getNombre());
                r.createCell(1).setCellValue(p.getCodigo());
                r.createCell(2).setCellValue(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
                r.createCell(3).setCellValue(p.getArea());
                r.createCell(4).setCellValue(p.getResguardante() != null ? p.getResguardante() : "");
                r.createCell(5).setCellValue(p.getStockActual());
                r.createCell(6).setCellValue(p.getStockMinimo());
                r.createCell(7).setCellValue(p.getStockMaximo());
                r.createCell(8).setCellValue(p.getPrecioVenta() != null ? p.getPrecioVenta().doubleValue() : 0);
                r.createCell(9).setCellValue(p.getValorTotal().doubleValue());
                r.createCell(10).setCellValue(p.getEstado().getEtiqueta());
                r.createCell(11).setCellValue(p.getProveedor() != null ? p.getProveedor() : "");
                r.createCell(12).setCellValue(p.getUbicacion() != null ? p.getUbicacion() : "");
                r.createCell(13).setCellValue(p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
            }
            autosizeColumns(sheet, headers.length);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportMovimientosExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = movimientoRepo.findByDateRange(desde, hasta);
        String[] headers = {"Producto", "Tipo", "Cantidad", "Stock Anterior", "Stock Nuevo",
                            "Motivo", "Referencia", "Usuario", "Fecha"};
        File file = tempFile("movimientos", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Movimientos");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Movimiento m : movimientos) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(m.getProductoNombre());
                r.createCell(1).setCellValue(m.getTipo().getEtiqueta());
                r.createCell(2).setCellValue(m.getCantidad());
                r.createCell(3).setCellValue(m.getStockAnterior());
                r.createCell(4).setCellValue(m.getStockNuevo());
                r.createCell(5).setCellValue(m.getMotivo() != null ? m.getMotivo() : "");
                r.createCell(6).setCellValue(m.getReferencia() != null ? m.getReferencia() : "");
                r.createCell(7).setCellValue(m.getUsuarioNombre());
                r.createCell(8).setCellValue(FormatUtils.formatDateTime(m.getCreadoEn()));
            }
            autosizeColumns(sheet, headers.length);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportDistribucionExcel() throws Exception {
        List<Producto> productos = productoRepo.findAll();
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));

        File file = tempFile("distribucion", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            // Sheet 1: summary per area
            Sheet summary = createSheet(wb, "Resumen por Área");
            String[] sumHeaders = {"Área", "Total Bienes", "Valor Total ($)", "Agotados", "Bajo Stock"};
            writeHeader(summary, sumHeaders, wb);
            int row = 1;
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                List<Producto> ps = entry.getValue();
                long agotados  = ps.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).count();
                long bajoStock = ps.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).count();
                BigDecimal valor = ps.stream().map(Producto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                Row r = summary.createRow(row++);
                r.createCell(0).setCellValue(entry.getKey());
                r.createCell(1).setCellValue(ps.size());
                r.createCell(2).setCellValue(valor.doubleValue());
                r.createCell(3).setCellValue(agotados);
                r.createCell(4).setCellValue(bajoStock);
            }
            autosizeColumns(summary, sumHeaders.length);

            // Sheet 2: all items grouped by area
            Sheet detail = createSheet(wb, "Detalle por Área");
            String[] detHeaders = {"Área", "Nombre", "Código", "Stock Actual", "Stock Mínimo", "Estado", "Valor Total ($)"};
            writeHeader(detail, detHeaders, wb);
            row = 1;
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                for (Producto p : entry.getValue()) {
                    Row r = detail.createRow(row++);
                    r.createCell(0).setCellValue(entry.getKey());
                    r.createCell(1).setCellValue(p.getNombre());
                    r.createCell(2).setCellValue(p.getCodigo());
                    r.createCell(3).setCellValue(p.getStockActual());
                    r.createCell(4).setCellValue(p.getStockMinimo());
                    r.createCell(5).setCellValue(p.getEstado().getEtiqueta());
                    r.createCell(6).setCellValue(p.getValorTotal().doubleValue());
                }
            }
            autosizeColumns(detail, detHeaders.length);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportDistribucionCsv() throws Exception {
        List<Producto> productos = productoRepo.findAll();
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));

        File file = tempFile("distribucion", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Área,Total Bienes,Valor Total,Agotados,Bajo Stock");
            for (Map.Entry<String, List<Producto>> entry : porArea.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                List<Producto> ps = entry.getValue();
                long agotados  = ps.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).count();
                long bajoStock = ps.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).count();
                BigDecimal valor = ps.stream().map(Producto::getValorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                pw.printf("\"%s\",%d,%.2f,%d,%d%n",
                    esc(entry.getKey()), ps.size(), valor, agotados, bajoStock);
            }
        }
        return file;
    }

    public File exportAlertasCsv() throws Exception {
        List<Producto> todos     = productoRepo.findAll();
        List<Producto> agotados  = todos.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = todos.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
        File file = tempFile("alertas", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Estado,Nombre,Codigo,Stock Actual,Stock Minimo,Area,Proveedor");
            for (Producto p : agotados)
                pw.printf("\"Agotado\",\"%s\",\"%s\",%d,%d,\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()), p.getStockActual(), p.getStockMinimo(),
                    esc(p.getArea()), esc(p.getProveedor()));
            for (Producto p : bajoStock)
                pw.printf("\"Bajo Stock\",\"%s\",\"%s\",%d,%d,\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()), p.getStockActual(), p.getStockMinimo(),
                    esc(p.getArea()), esc(p.getProveedor()));
        }
        return file;
    }

    public File exportAlertasPdf() throws Exception {
        List<Producto> todos     = productoRepo.findAll();
        List<Producto> agotados  = todos.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = todos.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
        File file = tempFile("alertas", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            addPdfHeader(doc, "Alertas de Stock", null, null);
            PdfFont sectionFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            doc.add(new Paragraph("Bienes Agotados (" + agotados.size() + ")")
                .setFont(sectionFont).setFontSize(11).setFontColor(new DeviceRgb(185, 28, 28)));
            String[] headers = {"Nombre", "Código", "Stock", "Mínimo", "Área"};
            float[] widths = {3f, 1.5f, 1f, 1f, 2f};
            Table t1 = createPdfTable(headers, widths);
            for (Producto p : agotados) {
                t1.addCell(cell(p.getNombre())); t1.addCell(cell(p.getCodigo()));
                t1.addCell(cell(String.valueOf(p.getStockActual()))); t1.addCell(cell(String.valueOf(p.getStockMinimo())));
                t1.addCell(cell(p.getArea() != null ? p.getArea() : ""));
            }
            doc.add(t1);
            doc.add(new Paragraph("Existencias Bajas (" + bajoStock.size() + ")")
                .setFont(sectionFont).setFontSize(11).setFontColor(new DeviceRgb(180, 83, 9)));
            Table t2 = createPdfTable(headers, widths);
            for (Producto p : bajoStock) {
                t2.addCell(cell(p.getNombre())); t2.addCell(cell(p.getCodigo()));
                t2.addCell(cell(String.valueOf(p.getStockActual()))); t2.addCell(cell(String.valueOf(p.getStockMinimo())));
                t2.addCell(cell(p.getArea() != null ? p.getArea() : ""));
            }
            doc.add(t2);
            addPdfFooter(doc, agotados.size() + bajoStock.size());
        }
        return file;
    }

    public File exportDistribucionPdf() throws Exception {
        List<Producto> productos = productoRepo.findAll();
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));
        File file = tempFile("distribucion", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            addPdfHeader(doc, "Distribución por Área", null, null);
            String[] headers = {"Área", "Total Bienes", "Valor Total", "Agotados", "Bajo Stock"};
            float[] widths = {3f, 1.5f, 2f, 1.2f, 1.5f};
            Table table = createPdfTable(headers, widths);
            porArea.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                List<Producto> ps = entry.getValue();
                long agotados  = ps.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).count();
                long bajo      = ps.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).count();
                java.math.BigDecimal valor = ps.stream().map(Producto::getValorTotal)
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                table.addCell(cell(entry.getKey()));
                table.addCell(cell(String.valueOf(ps.size())));
                table.addCell(cell(FormatUtils.formatCurrency(valor)));
                table.addCell(cell(String.valueOf(agotados)));
                table.addCell(cell(String.valueOf(bajo)));
            });
            doc.add(table);
            addPdfFooter(doc, porArea.size());
        }
        return file;
    }

    public File exportAlertasExcel() throws Exception {
        List<Producto> todos     = productoRepo.findAll();
        List<Producto> agotados  = todos.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = todos.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
        String[] headers = {"Nombre", "Codigo", "Stock Actual", "Stock Minimo", "Estado"};
        File file = tempFile("alertas", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Alertas");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (Producto p : agotados) {
                Row r = sheet.createRow(row++);
                fillAlertRow(r, p);
            }
            for (Producto p : bajoStock) {
                Row r = sheet.createRow(row++);
                fillAlertRow(r, p);
            }
            autosizeColumns(sheet, headers.length);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ───────────────────────────── PDF ─────────────────────────────

    public File exportInventarioPdf(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        File file = tempFile("inventario", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            addPdfHeader(doc, "Inventario General", desde, hasta);
            String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Stock", "Valor", "Estado"};
            float[] widths = {3f, 1.5f, 1.5f, 2f, 1f, 1.5f, 1.2f};
            Table table = createPdfTable(headers, widths);
            for (Producto p : productos) {
                table.addCell(cell(p.getNombre()));
                table.addCell(cell(p.getCodigo()));
                table.addCell(cell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : ""));
                table.addCell(cell(p.getArea()));
                table.addCell(cell(String.valueOf(p.getStockActual())));
                table.addCell(cell(FormatUtils.formatCurrency(p.getValorTotal())));
                table.addCell(cell(p.getEstado().getEtiqueta()));
            }
            doc.add(table);
            addPdfFooter(doc, productos.size());
        }
        return file;
    }

    public File exportMovimientosPdf(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = movimientoRepo.findByDateRange(desde, hasta);
        File file = tempFile("movimientos", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            addPdfHeader(doc, "Registro de Movimientos", desde, hasta);
            String[] headers = {"Producto", "Tipo", "Cantidad", "Ant.", "Nuevo", "Usuario", "Fecha"};
            float[] widths = {3f, 1.5f, 1f, 1f, 1f, 2f, 2f};
            Table table = createPdfTable(headers, widths);
            for (Movimiento m : movimientos) {
                table.addCell(cell(m.getProductoNombre()));
                table.addCell(cell(m.getTipo().getEtiqueta()));
                table.addCell(cell(String.valueOf(m.getCantidad())));
                table.addCell(cell(String.valueOf(m.getStockAnterior())));
                table.addCell(cell(String.valueOf(m.getStockNuevo())));
                table.addCell(cell(m.getUsuarioNombre()));
                table.addCell(cell(FormatUtils.formatDateTime(m.getCreadoEn())));
            }
            doc.add(table);
            addPdfFooter(doc, movimientos.size());
        }
        return file;
    }

    // ───────────────────────────── CSV ─────────────────────────────

    public File exportInventarioCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        File file = tempFile("inventario", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Codigo,Categoria,Area,Resguardante,Stock,Stock Min,Stock Max,Precio Compra,Precio Venta,Valor Total,Estado,Proveedor,Ubicacion,Fecha Registro");
            for (Producto p : productos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%.2f,%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()),
                    esc(p.getCategoriaNombre()), esc(p.getArea()), esc(p.getResguardante()),
                    p.getStockActual(), p.getStockMinimo(), p.getStockMaximo(),
                    p.getPrecioCompra() != null ? p.getPrecioCompra() : java.math.BigDecimal.ZERO,
                    p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO,
                    p.getValorTotal(),
                    p.getEstado().getEtiqueta(),
                    esc(p.getProveedor()), esc(p.getUbicacion()),
                    p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
            }
        }
        return file;
    }

    public File exportMovimientosCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = movimientoRepo.findByDateRange(desde, hasta);
        File file = tempFile("movimientos", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Producto,Tipo,Cantidad,Stock Anterior,Stock Nuevo,Motivo,Referencia,Usuario,Fecha");
            for (Movimiento m : movimientos) {
                pw.printf("\"%s\",\"%s\",%d,%d,%d,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(m.getProductoNombre()), m.getTipo().getEtiqueta(),
                    m.getCantidad(), m.getStockAnterior(), m.getStockNuevo(),
                    esc(m.getMotivo()), esc(m.getReferencia()),
                    esc(m.getUsuarioNombre()), FormatUtils.formatDateTime(m.getCreadoEn()));
            }
        }
        return file;
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    private File tempFile(String prefix, String suffix) throws IOException {
        File file = File.createTempFile("sibim_" + prefix + "_", suffix);
        // These files get handed to an external viewer via Desktop.open()
        // right after creation, so they can't be deleted immediately —
        // clean them up when the JVM exits instead of leaking one per export.
        file.deleteOnExit();
        return file;
    }

    private Sheet createSheet(Workbook wb, String name) {
        return wb.createSheet(name);
    }

    private void writeHeader(Sheet sheet, String[] headers, Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void autosizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void fillAlertRow(Row r, Producto p) {
        r.createCell(0).setCellValue(p.getNombre());
        r.createCell(1).setCellValue(p.getCodigo());
        r.createCell(2).setCellValue(p.getStockActual());
        r.createCell(3).setCellValue(p.getStockMinimo());
        r.createCell(4).setCellValue(p.getEstado().getEtiqueta());
    }

    private void addPdfHeader(Document doc, String titulo, LocalDate desde, LocalDate hasta) throws IOException {
        PdfFont titleFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        Table header = new Table(1).useAllAvailableWidth();
        com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell().add(new Paragraph("SIBIM — " + titulo)
            .setFont(titleFont).setFontSize(16).setFontColor(ColorConstants.WHITE));
        headerCell.setBackgroundColor(COLOR_HEADER);
        headerCell.setPadding(10);
        header.addCell(headerCell);
        doc.add(header);
        if (desde != null || hasta != null) {
            String periodo = (desde != null ? desde.format(FMT) : "inicio") + " — "
                           + (hasta != null ? hasta.format(FMT) : "hoy");
            PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            doc.add(new Paragraph("Periodo: " + periodo)
                .setFont(regularFont).setFontSize(10).setFontColor(ColorConstants.DARK_GRAY));
        }
    }

    private Table createPdfTable(String[] headers, float[] widths) throws IOException {
        Table table = new Table(widths).useAllAvailableWidth();
        PdfFont hFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        for (String h : headers) {
            com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell().add(new Paragraph(h)
                .setFont(hFont).setFontSize(9).setFontColor(ColorConstants.WHITE));
            headerCell.setBackgroundColor(COLOR_HEADER);
            headerCell.setPadding(5);
            table.addCell(headerCell);
        }
        return table;
    }

    private void addPdfFooter(Document doc, int count) throws IOException {
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        doc.add(new Paragraph("Total de registros: " + count + "   |   SIBIM — Sistema Integral de Bienes Municipales")
            .setFont(font).setFontSize(8).setFontColor(ColorConstants.GRAY));
    }

    /** Wraps plain text in a Cell+Paragraph for Table.addCell — itext7's Table
     *  has no addCell(String) overload, unlike itext5's PdfPTable. Fully
     *  qualified: "Cell" bare would resolve to POI's org.apache.poi.ss.
     *  usermodel.Cell via the wildcard import used by the Excel export code
     *  below, not itext7's com.itextpdf.layout.element.Cell. */
    private static com.itextpdf.layout.element.Cell cell(String text) {
        return new com.itextpdf.layout.element.Cell().add(new Paragraph(text == null ? "" : text));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
