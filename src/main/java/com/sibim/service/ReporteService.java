package com.sibim.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
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

    private static final BaseColor COLOR_HEADER = new BaseColor(76, 29, 149); // purple-900
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ───────────────────────────── EXCEL ─────────────────────────────

    public File exportInventarioExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Stock", "Min", "Max",
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
                r.createCell(4).setCellValue(p.getStockActual());
                r.createCell(5).setCellValue(p.getStockMinimo());
                r.createCell(6).setCellValue(p.getStockMaximo());
                r.createCell(7).setCellValue(p.getPrecioVenta() != null ? p.getPrecioVenta().doubleValue() : 0);
                r.createCell(8).setCellValue(p.getValorTotal().doubleValue());
                r.createCell(9).setCellValue(p.getEstado().getEtiqueta());
                r.createCell(10).setCellValue(p.getProveedor() != null ? p.getProveedor() : "");
                r.createCell(11).setCellValue(p.getUbicacion() != null ? p.getUbicacion() : "");
                r.createCell(12).setCellValue(p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
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

    public File exportAlertasCsv() throws Exception {
        List<Producto> agotados  = productoRepo.findAll().stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = productoRepo.findAll().stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
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
        List<Producto> agotados  = productoRepo.findAll().stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = productoRepo.findAll().stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
        File file = tempFile("alertas", ".pdf");
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();
        addPdfHeader(doc, "Alertas de Stock", null, null);
        com.itextpdf.text.Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new BaseColor(185, 28, 28));
        doc.add(new Paragraph("Bienes Agotados (" + agotados.size() + ")", sectionFont));
        doc.add(Chunk.NEWLINE);
        String[] headers = {"Nombre", "Código", "Stock", "Mínimo", "Área"};
        float[] widths = {3f, 1.5f, 1f, 1f, 2f};
        PdfPTable t1 = createPdfTable(headers, widths);
        for (Producto p : agotados) {
            t1.addCell(p.getNombre()); t1.addCell(p.getCodigo());
            t1.addCell(String.valueOf(p.getStockActual())); t1.addCell(String.valueOf(p.getStockMinimo()));
            t1.addCell(p.getArea() != null ? p.getArea() : "");
        }
        doc.add(t1);
        doc.add(Chunk.NEWLINE);
        com.itextpdf.text.Font sectionFont2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new BaseColor(180, 83, 9));
        doc.add(new Paragraph("Existencias Bajas (" + bajoStock.size() + ")", sectionFont2));
        doc.add(Chunk.NEWLINE);
        PdfPTable t2 = createPdfTable(headers, widths);
        for (Producto p : bajoStock) {
            t2.addCell(p.getNombre()); t2.addCell(p.getCodigo());
            t2.addCell(String.valueOf(p.getStockActual())); t2.addCell(String.valueOf(p.getStockMinimo()));
            t2.addCell(p.getArea() != null ? p.getArea() : "");
        }
        doc.add(t2);
        addPdfFooter(doc, agotados.size() + bajoStock.size());
        doc.close();
        return file;
    }

    public File exportDistribucionPdf() throws Exception {
        List<Producto> productos = productoRepo.findAll();
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));
        File file = tempFile("distribucion", ".pdf");
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();
        addPdfHeader(doc, "Distribución por Área", null, null);
        String[] headers = {"Área", "Total Bienes", "Valor Total", "Agotados", "Bajo Stock"};
        float[] widths = {3f, 1.5f, 2f, 1.2f, 1.5f};
        PdfPTable table = createPdfTable(headers, widths);
        porArea.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<Producto> ps = entry.getValue();
            long agotados  = ps.stream().filter(p -> p.getEstado() == EstadoProducto.AGOTADO).count();
            long bajo      = ps.stream().filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).count();
            java.math.BigDecimal valor = ps.stream().map(Producto::getValorTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            table.addCell(entry.getKey());
            table.addCell(String.valueOf(ps.size()));
            table.addCell(FormatUtils.formatCurrency(valor));
            table.addCell(String.valueOf(agotados));
            table.addCell(String.valueOf(bajo));
        });
        doc.add(table);
        addPdfFooter(doc, porArea.size());
        doc.close();
        return file;
    }

    public File exportAlertasExcel() throws Exception {
        List<Producto> agotados = productoRepo.findAll().stream()
            .filter(p -> p.getEstado() == EstadoProducto.AGOTADO).toList();
        List<Producto> bajoStock = productoRepo.findAll().stream()
            .filter(p -> p.getEstado() == EstadoProducto.BAJO_STOCK).toList();
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
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();
        addPdfHeader(doc, "Inventario General", desde, hasta);
        String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Stock", "Valor", "Estado"};
        float[] widths = {3f, 1.5f, 1.5f, 2f, 1f, 1.5f, 1.2f};
        PdfPTable table = createPdfTable(headers, widths);
        for (Producto p : productos) {
            table.addCell(p.getNombre());
            table.addCell(p.getCodigo());
            table.addCell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
            table.addCell(p.getArea());
            table.addCell(String.valueOf(p.getStockActual()));
            table.addCell(FormatUtils.formatCurrency(p.getValorTotal()));
            table.addCell(p.getEstado().getEtiqueta());
        }
        doc.add(table);
        addPdfFooter(doc, productos.size());
        doc.close();
        return file;
    }

    public File exportMovimientosPdf(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = movimientoRepo.findByDateRange(desde, hasta);
        File file = tempFile("movimientos", ".pdf");
        Document doc = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();
        addPdfHeader(doc, "Registro de Movimientos", desde, hasta);
        String[] headers = {"Producto", "Tipo", "Cantidad", "Ant.", "Nuevo", "Usuario", "Fecha"};
        float[] widths = {3f, 1.5f, 1f, 1f, 1f, 2f, 2f};
        PdfPTable table = createPdfTable(headers, widths);
        for (Movimiento m : movimientos) {
            table.addCell(m.getProductoNombre());
            table.addCell(m.getTipo().getEtiqueta());
            table.addCell(String.valueOf(m.getCantidad()));
            table.addCell(String.valueOf(m.getStockAnterior()));
            table.addCell(String.valueOf(m.getStockNuevo()));
            table.addCell(m.getUsuarioNombre());
            table.addCell(FormatUtils.formatDateTime(m.getCreadoEn()));
        }
        doc.add(table);
        addPdfFooter(doc, movimientos.size());
        doc.close();
        return file;
    }

    // ───────────────────────────── CSV ─────────────────────────────

    public File exportInventarioCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        File file = tempFile("inventario", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Codigo,Categoria,Area,Stock,Stock Min,Stock Max,Precio Compra,Precio Venta,Valor Total,Estado,Proveedor,Ubicacion,Fecha Registro");
            for (Producto p : productos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%.2f,%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()),
                    esc(p.getCategoriaNombre()), esc(p.getArea()),
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
        return File.createTempFile("sibim_" + prefix + "_", suffix);
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

    private void addPdfHeader(Document doc, String titulo, LocalDate desde, LocalDate hasta)
            throws DocumentException {
        com.itextpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.WHITE);
        PdfPTable header = new PdfPTable(1);
        header.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase("SIBIM — " + titulo, titleFont));
        cell.setBackgroundColor(COLOR_HEADER);
        cell.setPadding(10);
        header.addCell(cell);
        doc.add(header);
        if (desde != null || hasta != null) {
            String periodo = (desde != null ? desde.format(FMT) : "inicio") + " — "
                           + (hasta != null ? hasta.format(FMT) : "hoy");
            doc.add(new Paragraph("Periodo: " + periodo,
                FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.DARK_GRAY)));
        }
        doc.add(Chunk.NEWLINE);
    }

    private PdfPTable createPdfTable(String[] headers, float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        com.itextpdf.text.Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BaseColor.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
            cell.setBackgroundColor(COLOR_HEADER);
            cell.setPadding(5);
            table.addCell(cell);
        }
        return table;
    }

    private void addPdfFooter(Document doc, int count) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("Total de registros: " + count + "   |   SIBIM — Sistema Integral de Bienes Municipales",
            FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.GRAY)));
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
