package com.sibim.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
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
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.FormatUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReporteService {

    private final ProductoRepository   productoRepo;
    private final MovimientoRepository movimientoRepo;
    private final com.sibim.repository.ConfiguracionRepository configRepo;

    public ReporteService() { this(new ProductoRepository(), new MovimientoRepository(), new com.sibim.repository.ConfiguracionRepository()); }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo,
                   com.sibim.repository.ConfiguracionRepository configRepo) {
        this.productoRepo   = productoRepo;
        this.movimientoRepo = movimientoRepo;
        this.configRepo     = configRepo;
    }

    private String orgName() {
        return configRepo.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan") + ", Hgo.";
    }

    private static final DeviceRgb COLOR_HEADER = new DeviceRgb(76, 29, 149); // purple-900
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_EXPORT_ROWS = 50_000;

    private static <T> List<T> guardExportSize(List<T> rows, String entidad) throws Exception {
        if (rows.size() > MAX_EXPORT_ROWS)
            throw new Exception("El reporte incluye " + rows.size() + " " + entidad
                + ". Filtra el rango de fechas para reducirlo (máx. " + MAX_EXPORT_ROWS + " filas por exportación).");
        return rows;
    }

    // ───────────────────────────── EXCEL ─────────────────────────────

    public File exportInventarioExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = (desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll();
        if (productos.isEmpty()) return null;
        return exportInventarioExcel(guardExportSize(productos, "bienes"), desde, hasta);
    }

    /** Same Excel report, given an explicit list — bulk action / selection export. */
    public File exportInventarioExcel(List<Producto> productos) throws Exception {
        return exportInventarioExcel(productos, null, null);
    }

    private File exportInventarioExcel(List<Producto> productos, LocalDate desde, LocalDate hasta) throws Exception {
        String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Resguardante", "Stock", "Min", "Max",
                            "Precio Venta", "Valor Total", "Estado", "Proveedor", "Marca", "Modelo",
                            "N° de Serie", "Ubicacion", "Fecha Registro"};
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
                r.createCell(12).setCellValue(p.getMarca() != null ? p.getMarca() : "");
                r.createCell(13).setCellValue(p.getModelo() != null ? p.getModelo() : "");
                r.createCell(14).setCellValue(p.getNumeroSerie() != null ? p.getNumeroSerie() : "");
                r.createCell(15).setCellValue(p.getUbicacion() != null ? p.getUbicacion() : "");
                r.createCell(16).setCellValue(p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Inventario General", desde, hasta);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportMovimientosExcel(List<Movimiento> movimientos) throws Exception {
        return exportMovimientosExcelImpl(movimientos, null, null);
    }

    public File exportMovimientosExcel(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = guardExportSize(movimientoRepo.findByDateRange(desde, hasta), "movimientos");
        if (movimientos.isEmpty()) return null;
        return exportMovimientosExcelImpl(movimientos, desde, hasta);
    }

    private File exportMovimientosExcelImpl(List<Movimiento> movimientos, LocalDate desde, LocalDate hasta) throws Exception {
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
            addExcelInfoSheet(wb, "Registro de Movimientos", desde, hasta);
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
            addExcelInfoSheet(wb, "Distribución por Área", null, null);
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
            addExcelInfoSheet(wb, "Alertas de Stock", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    // ── Depreciación ── (recibe la lista ya cargada/filtrada por la pantalla
    // de Depreciación en vez de re-consultar productoRepo — exporta
    // exactamente lo que el usuario está viendo)

    private static final String[] DEP_HEADERS = {
        "Nombre", "Categoria", "Fecha Adquisicion", "Vida Util (años)",
        "Valor Compra", "Valor Actual", "% Depreciado"};

    public File exportDepreciacionExcel(List<Producto> productos) throws Exception {
        File file = tempFile("depreciacion", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Depreciación");
            writeHeader(sheet, DEP_HEADERS, wb);
            int row = 1;
            for (Producto p : productos) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(p.getNombre());
                r.createCell(1).setCellValue(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
                r.createCell(2).setCellValue(FormatUtils.formatDate(p.getFechaAdquisicion()));
                r.createCell(3).setCellValue(p.getVidaUtilAnios());
                r.createCell(4).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                r.createCell(5).setCellValue(p.getValorDepreciado() != null ? p.getValorDepreciado().doubleValue() : 0);
                r.createCell(6).setCellValue(p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() : 0);
            }
            autosizeColumns(sheet, DEP_HEADERS.length);
            addExcelInfoSheet(wb, "Depreciación de Activos", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportDepreciacionPdf(List<Producto> productos) throws Exception {
        File file = tempFile("depreciacion", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            addPdfHeader(doc, "Depreciación de Activos", null, null);
            float[] widths = {3f, 1.8f, 1.5f, 1.2f, 1.5f, 1.5f, 1.2f};
            Table table = createPdfTable(DEP_HEADERS, widths);
            for (Producto p : productos) {
                table.addCell(cell(p.getNombre()));
                table.addCell(cell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : ""));
                table.addCell(cell(FormatUtils.formatDate(p.getFechaAdquisicion())));
                table.addCell(cell(String.valueOf(p.getVidaUtilAnios())));
                table.addCell(cell(FormatUtils.formatCurrency(p.getPrecioCompra())));
                table.addCell(cell(FormatUtils.formatCurrency(p.getValorDepreciado())));
                table.addCell(cell(p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() + "%" : "—"));
            }
            doc.add(table);
            addPdfFooter(doc, productos.size());
        }
        return file;
    }

    public File exportDepreciacionCsv(List<Producto> productos) throws Exception {
        File file = tempFile("depreciacion", ".csv");
        java.time.LocalDate hoy = java.time.LocalDate.now();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Categoria,Area,Fecha Adquisicion,Vida Util (años),Vida Util Restante,Valor Compra,Valor Actual,% Depreciado,Depreciado el");
            for (Producto p : productos) {
                java.time.LocalDate fechaTotal = (p.getFechaAdquisicion() != null && p.getVidaUtilAnios() != null && p.getVidaUtilAnios() > 0)
                    ? p.getFechaAdquisicion().plusYears(p.getVidaUtilAnios()) : null;
                String restante = "—";
                if (fechaTotal != null) {
                    if (!fechaTotal.isAfter(hoy)) restante = "Cumplida";
                    else {
                        long meses = java.time.temporal.ChronoUnit.MONTHS.between(hoy, fechaTotal);
                        restante = meses < 12 ? meses + " meses" : (meses / 12) + " años";
                    }
                }
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",%.2f,%.2f,%s,\"%s\"%n",
                    esc(p.getNombre()),
                    esc(p.getCategoriaNombre()),
                    esc(p.getArea()),
                    FormatUtils.formatDate(p.getFechaAdquisicion()),
                    p.getVidaUtilAnios() != null ? p.getVidaUtilAnios() : 0,
                    restante,
                    p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0.0,
                    p.getValorDepreciado() != null ? p.getValorDepreciado().doubleValue() : 0.0,
                    p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() : 0,
                    fechaTotal != null ? fechaTotal.format(FMT) : "");
            }
        }
        return file;
    }

    // ───────────────────────────── PDF ─────────────────────────────

    public File exportInventarioPdf(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = guardExportSize((desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll(), "bienes");
        if (productos.isEmpty()) return null;
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
        List<Movimiento> movimientos = guardExportSize(movimientoRepo.findByDateRange(desde, hasta), "movimientos");
        if (movimientos.isEmpty()) return null;
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

    public File exportAuditoriaPdf(List<com.sibim.model.AuditLog> logs,
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
            for (com.sibim.model.AuditLog l : logs) {
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

    public File exportAuditoriaCsv(List<com.sibim.model.AuditLog> logs) throws Exception {
        if (logs.isEmpty()) return null;
        File file = tempFile("auditoria", ".csv");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            pw.println("Entidad,Nombre,Accion,Usuario,Detalle,Fecha");
            for (com.sibim.model.AuditLog l : logs) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(l.getEntidad()), esc(l.getEntidadNombre()),
                    esc(l.getAccion()), esc(l.getUsuarioNombre()),
                    esc(l.getDetalle()), l.getCreadoEn() != null
                        ? FormatUtils.formatDateTime(l.getCreadoEn()) : "");
            }
        }
        return file;
    }

    // ───────────────────────────── CSV ─────────────────────────────

    public File exportInventarioCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Producto> productos = guardExportSize((desde != null || hasta != null)
            ? productoRepo.findByDateRange(desde, hasta)
            : productoRepo.findAll(), "bienes");
        if (productos.isEmpty()) return null;
        return exportInventarioCsv(productos);
    }

    /** Same CSV report, given an explicit list — see the Excel overload above. */
    public File exportInventarioCsv(List<Producto> productos) throws Exception {
        File file = tempFile("inventario", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Codigo,Categoria,Area,Resguardante,Stock,Stock Min,Stock Max,Precio Compra,Precio Venta,Valor Total,Estado,Proveedor,Marca,Modelo,N° de Serie,Ubicacion,Fecha Registro");
            for (Producto p : productos) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,%d,%d,%.2f,%.2f,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()),
                    esc(p.getCategoriaNombre()), esc(p.getArea()), esc(p.getResguardante()),
                    p.getStockActual(), p.getStockMinimo(), p.getStockMaximo(),
                    p.getPrecioCompra() != null ? p.getPrecioCompra() : java.math.BigDecimal.ZERO,
                    p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO,
                    p.getValorTotal(),
                    p.getEstado().getEtiqueta(),
                    esc(p.getProveedor()),
                    esc(p.getMarca()), esc(p.getModelo()), esc(p.getNumeroSerie()),
                    esc(p.getUbicacion()),
                    p.getCreadoEn() != null ? p.getCreadoEn().toLocalDate().format(FMT) : "");
            }
        }
        return file;
    }

    public File exportMovimientosCsv(List<Movimiento> movimientos) throws Exception {
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

    public File exportMovimientosCsv(LocalDate desde, LocalDate hasta) throws Exception {
        List<Movimiento> movimientos = guardExportSize(movimientoRepo.findByDateRange(desde, hasta), "movimientos");
        if (movimientos.isEmpty()) return null;
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

    private void addExcelInfoSheet(Workbook wb, String titulo, LocalDate desde, LocalDate hasta) {
        Sheet info = wb.createSheet("_Info");
        com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
        String user = u != null ? u.getNombre() : "—";
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String[][] rows = {
            {"Reporte",             "SIBIM — " + titulo},
            {"Institución",         orgName()},
            {"Generado por",        user},
            {"Fecha de generación", ts},
            {"Período",             desde != null || hasta != null
                ? (desde != null ? desde.format(FMT) : "inicio") + " — "
                  + (hasta != null ? hasta.format(FMT) : "hoy")
                : "Sin restricción de fechas"},
        };
        for (int i = 0; i < rows.length; i++) {
            Row r = info.createRow(i);
            r.createCell(0).setCellValue(rows[i][0]);
            r.createCell(1).setCellValue(rows[i][1]);
        }
        info.autoSizeColumn(0);
        info.autoSizeColumn(1);
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
        com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
        String user = u != null ? u.getNombre() : "—";
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        doc.add(new Paragraph(
                "Total: " + count + " registros   |   Generado por: " + user + "   |   " + ts
                + "   |   " + orgName())
            .setFont(font).setFontSize(8).setFontColor(ColorConstants.GRAY));
    }

    // ───────────────────────────── ETIQUETAS QR ─────────────────────

    /** Generates a printable A4 PDF sheet of QR label cards (3 columns × N rows).
     *  Each card has the QR (encoding the product código), nombre, código and área.
     *  Max 200 products per sheet to keep file size reasonable. */
    public File exportEtiquetasQrPdf(List<Producto> productos) throws Exception {
        if (productos.isEmpty()) return null;
        List<Producto> items = productos.size() > 200 ? productos.subList(0, 200) : productos;
        File file = tempFile("etiquetas_qr", ".pdf");
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerBg = new DeviceRgb(76, 29, 149);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(18, 14, 18, 14);
            Table grid = new Table(3).useAllAvailableWidth();
            grid.setMarginBottom(0);

            for (Producto p : items) {
                // Build QR as PNG bytes via BitMatrix → BufferedImage → PNG stream
                byte[] qrBytes = qrToPngBytes(p.getCodigo() != null ? p.getCodigo() : p.getNombre(), 160);

                com.itextpdf.layout.element.Cell card = new com.itextpdf.layout.element.Cell();
                card.setBorder(new com.itextpdf.layout.borders.SolidBorder(new DeviceRgb(203, 213, 225), 0.5f));
                card.setPadding(8).setMargin(3);
                card.setKeepTogether(true);

                // QR image
                if (qrBytes != null) {
                    com.itextpdf.layout.element.Image qrImg = new com.itextpdf.layout.element.Image(
                        ImageDataFactory.create(qrBytes));
                    qrImg.setAutoScale(false).setWidth(80).setHeight(80)
                         .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                    card.add(qrImg);
                }

                // Código badge
                Paragraph codigoPar = new Paragraph(p.getCodigo() != null ? p.getCodigo() : "—")
                    .setFont(bold).setFontSize(8).setFontColor(ColorConstants.WHITE);
                com.itextpdf.layout.element.Cell codBadge = new com.itextpdf.layout.element.Cell()
                    .add(codigoPar).setBackgroundColor(headerBg).setPadding(2)
                    .setBorder(null)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
                Table codTable = new Table(1).useAllAvailableWidth().addCell(codBadge);
                card.add(codTable);

                // Nombre
                card.add(new Paragraph(p.getNombre() != null ? p.getNombre() : "—")
                    .setFont(bold).setFontSize(7.5f).setFontColor(new DeviceRgb(15, 23, 42))
                    .setMarginTop(4).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));

                // Área
                if (p.getArea() != null && !p.getArea().isBlank()) {
                    card.add(new Paragraph(p.getArea())
                        .setFont(regular).setFontSize(6.5f).setFontColor(new DeviceRgb(100, 116, 139))
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
                }

                grid.addCell(card);
            }
            // Pad last row to complete 3-col grid
            int rem = items.size() % 3;
            if (rem != 0) for (int i = rem; i < 3; i++)
                grid.addCell(new com.itextpdf.layout.element.Cell().setBorder(null));

            doc.add(grid);
            addPdfFooter(doc, items.size());
        }
        return file;
    }

    private static byte[] qrToPngBytes(String content, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size, java.util.Map.of(EncodeHintType.MARGIN, 1));
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) { return null; }
    }

    /** Wraps plain text in a Cell+Paragraph for Table.addCell — itext7's Table
     *  has no addCell(String) overload, unlike itext5's PdfPTable. Fully
     *  qualified: "Cell" bare would resolve to POI's org.apache.poi.ss.
     *  usermodel.Cell via the wildcard import used by the Excel export code
     *  below, not itext7's com.itextpdf.layout.element.Cell. */
    private static com.itextpdf.layout.element.Cell cell(String text) {
        return new com.itextpdf.layout.element.Cell().add(new Paragraph(text == null ? "" : text));
    }

    // ───────────────────────────── FICHA TÉCNICA ─────────────────────

    /** Generates a 1-page A4 PDF "ficha técnica" for a single bien,
     *  including identification, patrimonial value, recent movement history,
     *  and a signature block for formal administrative sign-off. */
    public File exportFichaTecnica(Producto p, List<com.sibim.model.Movimiento> movimientos) throws Exception {
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
        DeviceRgb indigo2 = new DeviceRgb(199, 210, 254); // indigo-200

        String generadoEn = "Generado el " + LocalDate.now().format(FMT);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(28, 36, 28, 36);

            // ── 1. Header band ──────────────────────────────────────────
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

            // ── 2. Code + Status band ───────────────────────────────────
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

            // ── 3. Identification ───────────────────────────────────────
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

            // ── 4. Patrimonial value ────────────────────────────────────
            doc.add(sectionTitle("VALOR PATRIMONIAL", bold, indigo));
            Table valTable = new Table(new float[]{1f, 2.5f}).useAllAvailableWidth();
            addRow(valTable, "Precio de adquisición", FormatUtils.formatCurrency(p.getPrecioCompra()), bold, regular, muted, bgAlt, false);
            addRow(valTable, "Precio unitario",        FormatUtils.formatCurrency(p.getPrecioVenta()),  bold, regular, muted, bgAlt, true);
            addRow(valTable, "Valor total inventario", FormatUtils.formatCurrency(p.getValorTotal()),   bold, regular, muted, bgAlt, false);
            if (p.getFechaAdquisicion() != null)
                addRow(valTable, "Fecha de adquisición", FormatUtils.formatDate(p.getFechaAdquisicion()), bold, regular, muted, bgAlt, true);
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

            // ── 5. Description (optional) ───────────────────────────────
            if (p.getDescripcion() != null && !p.getDescripcion().isBlank()) {
                doc.add(spacer(8));
                doc.add(sectionTitle("DESCRIPCIÓN", bold, indigo));
                doc.add(new Paragraph(p.getDescripcion())
                    .setFont(regular).setFontSize(9.5f).setFontColor(dark)
                    .setMarginLeft(4));
            }

            // ── 6. Movement history ─────────────────────────────────────
            if (!movimientos.isEmpty()) {
                doc.add(spacer(8));
                int shown = Math.min(movimientos.size(), 8);
                doc.add(sectionTitle("HISTORIAL DE MOVIMIENTOS  (últimos " + shown + ")", bold, indigo));
                Table movTable = createPdfTable(
                    new String[]{"Fecha", "Tipo", "Cant.", "Ant.", "Nuevo", "Usuario", "Motivo"},
                    new float[]{1.6f, 1f, 0.55f, 0.55f, 0.65f, 1.2f, 2f});
                for (int i = 0; i < shown; i++) {
                    com.sibim.model.Movimiento m = movimientos.get(i);
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

            // ── 7. Signature block ──────────────────────────────────────
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

            // ── 8. Footer ────────────────────────────────────────────────
            doc.add(spacer(6));
            doc.add(new Paragraph(
                "SIBIM — Sistema Integral de Bienes Municipales  |  " + orgName() + "  |  " + generadoEn)
                .setFont(regular).setFontSize(7.5f).setFontColor(muted)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
        }
        return file;
    }

    /** Generates a single merged PDF containing one ficha técnica per bien.
     *  Fetches movements via {@code movimientoService} (one query per bien).
     *  Caller should limit the list to a reasonable size (≤ 100). */
    public File exportFichasTecnicasMasivas(List<Producto> bienes,
            MovimientoService movimientoService) throws Exception {
        if (bienes == null || bienes.isEmpty())
            throw new IllegalArgumentException("La lista de bienes está vacía — no hay fichas que generar");
        // Batch-load all movements in one query instead of N individual calls.
        List<String> ids = bienes.stream().map(Producto::getId).toList();
        java.util.Map<String, List<com.sibim.model.Movimiento>> movsByProducto =
            movimientoService.getByProductoIds(ids);

        File output = tempFile("fichas_tecnicas_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        List<File> temps = new java.util.ArrayList<>();
        try (PdfWriter writer = new PdfWriter(output.getAbsolutePath());
             PdfDocument merged = new PdfDocument(writer)) {
            PdfMerger merger = new PdfMerger(merged);
            for (Producto p : bienes) {
                List<com.sibim.model.Movimiento> movs = movsByProducto.getOrDefault(p.getId(), java.util.List.of());
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

    /**
     * Genera un PDF de resguardo con todos los bienes de un resguardante dado.
     * Formato: encabezado institucional, tabla de bienes, línea de firma al final.
     */
    public File exportarResguardoPdf(String resguardante, String area, List<Producto> bienes) throws Exception {
        File out = File.createTempFile("resguardo_", ".pdf");
        out.deleteOnExit();
        try (PdfWriter writer = new PdfWriter(out);
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER)) {

            doc.setMargins(50, 50, 60, 50);
            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Encabezado
            doc.add(new Paragraph("RESGUARDO DE BIENES MUNICIPALES")
                .setFont(bold).setFontSize(16)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            doc.add(new Paragraph(orgName())
                .setFont(regular).setFontSize(11)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(4));
            doc.add(new Paragraph("Resguardante: " + (resguardante != null ? resguardante : "—")
                    + "    |    Área: " + (area != null ? area : "—")
                    + "    |    Fecha: " + com.sibim.util.FormatUtils.formatDate(java.time.LocalDate.now()))
                .setFont(regular).setFontSize(10)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMarginBottom(16));

            // Tabla de bienes
            float[] widths = {1.5f, 3.5f, 1.2f, 1.2f, 1.5f, 1.5f};
            Table table = new Table(widths).useAllAvailableWidth();
            DeviceRgb headerColor = new DeviceRgb(59, 130, 246);
            String[] headers = {"Código", "Nombre / Descripción", "Serie", "Stock", "Ubicación", "Estado"};
            for (String h : headers) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h).setFont(bold).setFontSize(9).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(headerColor).setPadding(5));
            }
            boolean alt = false;
            for (Producto p : bienes) {
                DeviceRgb rowBg = alt ? new DeviceRgb(243, 244, 246) : new DeviceRgb(255, 255, 255);
                String[] cells = {
                    p.getCodigo(),
                    p.getNombre() + (p.getDescripcion() != null && !p.getDescripcion().isBlank() ? "\n" + p.getDescripcion() : ""),
                    p.getNumeroSerie() != null ? p.getNumeroSerie() : "—",
                    String.valueOf(p.getStockActual()),
                    p.getUbicacion() != null ? p.getUbicacion() : "—",
                    p.getEstado().name()
                };
                for (String cellVal : cells) {
                    table.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(cellVal != null ? cellVal : "").setFont(regular).setFontSize(8))
                        .setBackgroundColor(rowBg).setPadding(4));
                }
                alt = !alt;
            }
            doc.add(table);

            // Total y firma
            doc.add(new Paragraph("\nTotal de bienes: " + bienes.size())
                .setFont(bold).setFontSize(10).setMarginTop(12));
            doc.add(new Paragraph("\n\n\n_______________________________          _______________________________")
                .setFont(regular).setFontSize(10)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            doc.add(new Paragraph("Firma del resguardante                         Vo.Bo. Jefe del Área")
                .setFont(regular).setFontSize(9)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
        }
        return out;
    }

    /** Generates a structured organigrama PDF — one section per area with bienes table. */
    public File exportOrganigrama(Map<String, List<Producto>> porArea) throws Exception {
        File file = tempFile("organigrama_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        String generadoEn = com.sibim.util.FormatUtils.formatDateTime(LocalDateTime.now());

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, com.itextpdf.kernel.geom.PageSize.A4)) {

            doc.setMargins(0, 36, 36, 36);

            PdfFont regular = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);

            DeviceRgb indigo  = new DeviceRgb(79,  70, 229);
            DeviceRgb dark    = new DeviceRgb(17,  24,  39);
            DeviceRgb muted   = new DeviceRgb(107, 114, 128);
            DeviceRgb bgLight = new DeviceRgb(238, 242, 255);
            DeviceRgb bgAlt   = new DeviceRgb(245, 247, 255);
            DeviceRgb white   = new DeviceRgb(255, 255, 255);

            // ── Header band ────────────────────────────────────────────
            Table header = new Table(new float[]{1f}).useAllAvailableWidth();
            header.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("H. AYUNTAMIENTO DE IXMIQUILPAN, HGO.")
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
            double totalValor = porArea.values().stream()
                .flatMap(List::stream)
                .mapToDouble(p -> {
                    java.math.BigDecimal v = p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
                    return v.doubleValue() * p.getStockActual();
                })
                .sum();

            Table statsTable = new Table(new float[]{1f, 1f, 1f}).useAllAvailableWidth();
            for (String[] stat : new String[][]{
                    {"Áreas con bienes",   String.valueOf(totalAreas)},
                    {"Total de bienes",    String.valueOf(totalBienes)},
                    {"Valor patrimonial",  FormatUtils.formatCurrency(java.math.BigDecimal.valueOf(totalValor))}}) {
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
                double valorArea = bienes.stream()
                    .mapToDouble(p -> {
                        java.math.BigDecimal v = p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
                        return v.doubleValue() * p.getStockActual();
                    })
                    .sum();

                // Area header row
                Table areaHeader = new Table(new float[]{1f}).useAllAvailableWidth();
                areaHeader.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(area.toUpperCase())
                        .setFont(bold).setFontSize(9.5f).setFontColor(indigo).setMargin(0))
                    .add(new Paragraph(cnt + " bien" + (cnt != 1 ? "es" : "") +
                            (valorArea > 0 ? "  ·  Valor: " + FormatUtils.formatCurrency(java.math.BigDecimal.valueOf(valorArea)) : ""))
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
            doc.add(new Paragraph(
                "SIBIM — Sistema Integral de Bienes Municipales  |  " + orgName() + "  |  " + generadoEn)
                .setFont(regular).setFontSize(7.5f).setFontColor(muted)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
        }
        return file;
    }

    private Paragraph sectionTitle(String text, PdfFont bold, DeviceRgb color) {
        return new Paragraph(text).setFont(bold).setFontSize(8.5f).setFontColor(color)
            .setMarginBottom(3).setMarginTop(0);
    }

    private void addRow(Table table, String key, String value,
            PdfFont bold, PdfFont regular, DeviceRgb muted, DeviceRgb bgAlt, boolean alt) {
        DeviceRgb bg = alt ? bgAlt : new DeviceRgb(255, 255, 255);
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(key).setFont(bold).setFontSize(8.5f).setFontColor(muted))
            .setBackgroundColor(bg).setPadding(6)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        table.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(value != null ? value : "—").setFont(regular).setFontSize(9.5f))
            .setBackgroundColor(bg).setPadding(6)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
    }

    private static com.itextpdf.layout.element.Cell cellSm(String text, PdfFont font) {
        return new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(text == null ? "" : text).setFont(font).setFontSize(8f))
            .setPadding(4);
    }

    private static Paragraph spacer(float size) {
        return new Paragraph("").setFontSize(size).setMarginBottom(0).setMarginTop(0);
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

    // ─────────────────────────── BIENES DADOS DE BAJA ──────────────────────────

    private List<Producto> fetchBajas() throws Exception {
        return productoRepo.findAll(true).stream().filter(Producto::isDadoDeBaja).toList();
    }

    public File exportBajasPdf() throws Exception  { return exportBajasPdf(fetchBajas()); }
    public File exportBajasExcel() throws Exception { return exportBajasExcel(fetchBajas()); }
    public File exportBajasCsv() throws Exception   { return exportBajasCsv(fetchBajas()); }

    public File exportBajasPdf(List<com.sibim.model.Producto> bajas) throws Exception {
        if (bajas.isEmpty()) return null;
        File file = tempFile("bienes_baja", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            addPdfHeader(doc, "Bienes Dados de Baja", null, null);
            String[] headers = {"Nombre", "Código", "Área", "Categoría", "Fecha baja", "Motivo"};
            float[] widths  = {3f, 1.5f, 2f, 1.5f, 1.5f, 3f};
            Table table = createPdfTable(headers, widths);
            for (com.sibim.model.Producto p : bajas) {
                table.addCell(cell(p.getNombre()));
                table.addCell(cell(p.getCodigo()));
                table.addCell(cell(p.getArea() != null ? p.getArea() : ""));
                table.addCell(cell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : ""));
                table.addCell(cell(p.getFechaBaja() != null ? p.getFechaBaja().format(FMT) : ""));
                table.addCell(cell(p.getMotivoBaja() != null ? p.getMotivoBaja() : ""));
            }
            doc.add(table);
            addPdfFooter(doc, bajas.size());
        }
        return file;
    }

    public File exportBajasExcel(List<com.sibim.model.Producto> bajas) throws Exception {
        if (bajas.isEmpty()) return null;
        String[] headers = {"Nombre", "Código", "Área", "Categoría", "Resguardante",
                            "Precio compra", "Fecha baja", "Motivo"};
        File file = tempFile("bienes_baja", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = createSheet(wb, "Dados de Baja");
            writeHeader(sheet, headers, wb);
            int row = 1;
            for (com.sibim.model.Producto p : bajas) {
                Row r = sheet.createRow(row++);
                r.createCell(0).setCellValue(p.getNombre());
                r.createCell(1).setCellValue(p.getCodigo());
                r.createCell(2).setCellValue(p.getArea() != null ? p.getArea() : "");
                r.createCell(3).setCellValue(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "");
                r.createCell(4).setCellValue(p.getResguardante() != null ? p.getResguardante() : "");
                r.createCell(5).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                r.createCell(6).setCellValue(p.getFechaBaja() != null ? p.getFechaBaja().format(FMT) : "");
                r.createCell(7).setCellValue(p.getMotivoBaja() != null ? p.getMotivoBaja() : "");
            }
            autosizeColumns(sheet, headers.length);
            addExcelInfoSheet(wb, "Bienes Dados de Baja", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    public File exportBajasCsv(List<com.sibim.model.Producto> bajas) throws Exception {
        if (bajas.isEmpty()) return null;
        File file = tempFile("bienes_baja", ".csv");
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("Nombre,Código,Área,Categoría,Resguardante,Precio compra,Fecha baja,Motivo");
            for (com.sibim.model.Producto p : bajas) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,\"%s\",\"%s\"%n",
                    esc(p.getNombre()), esc(p.getCodigo()),
                    esc(p.getArea()), esc(p.getCategoriaNombre()),
                    esc(p.getResguardante()),
                    p.getPrecioCompra() != null ? p.getPrecioCompra() : java.math.BigDecimal.ZERO,
                    p.getFechaBaja() != null ? p.getFechaBaja().format(FMT) : "",
                    esc(p.getMotivoBaja()));
            }
        }
        return file;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
