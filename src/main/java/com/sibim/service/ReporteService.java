package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
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
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo) {
        this(productoRepo, movimientoRepo, new com.sibim.repository.ConfiguracionRepository());
    }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo,
                   com.sibim.repository.ConfiguracionRepository configRepo) {
        this.productoRepo   = productoRepo;
        this.movimientoRepo = movimientoRepo;
        this.configRepo     = configRepo;
    }

    protected String orgName() {
        String org = configRepo.get("nombre_ayuntamiento", "");
        String mun = configRepo.get("municipio", "");
        if (org.isBlank()) return "SIBIM — Sistema Integral de Bienes Municipales";
        return mun.isBlank() ? org : org + "  ·  " + mun;
    }

    protected static final DeviceRgb COLOR_HEADER = new DeviceRgb(76, 29, 149); // purple-900
    protected static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
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

    protected File tempFile(String prefix, String suffix) throws IOException {
        File file = File.createTempFile("sibim_" + prefix + "_", suffix);
        // These files get handed to an external viewer via Desktop.open()
        // right after creation, so they can't be deleted immediately —
        // clean them up when the JVM exits instead of leaking one per export.
        file.deleteOnExit();
        return file;
    }

    protected Sheet createSheet(Workbook wb, String name) {
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

    protected void writeHeader(Sheet sheet, String[] headers, Workbook wb) {
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

    protected void autosizeColumns(Sheet sheet, int count) {
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

    protected void addPdfHeader(Document doc, String titulo, LocalDate desde, LocalDate hasta) throws IOException {
        PdfFont titleFont   = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        Table header = new Table(1).useAllAvailableWidth();
        com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(titulo).setFont(titleFont).setFontSize(15).setFontColor(ColorConstants.WHITE))
            .add(new Paragraph(orgName()).setFont(regularFont).setFontSize(9)
                .setFontColor(new DeviceRgb(200, 210, 240)));
        headerCell.setBackgroundColor(COLOR_HEADER);
        headerCell.setPadding(12);
        header.addCell(headerCell);
        doc.add(header);
        if (desde != null || hasta != null) {
            String periodo = (desde != null ? desde.format(FMT) : "inicio") + " — "
                           + (hasta != null ? hasta.format(FMT) : "hoy");
            com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
            String gen = "Generado " + LocalDate.now().format(FMT) + (u != null ? " por " + u.getNombre() : "");
            doc.add(new Paragraph("Período: " + periodo + "    ·    " + gen)
                .setFont(regularFont).setFontSize(9).setFontColor(ColorConstants.DARK_GRAY)
                .setMarginTop(4));
        } else {
            com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
            String gen = "Generado " + LocalDate.now().format(FMT) + (u != null ? " por " + u.getNombre() : "");
            doc.add(new Paragraph(gen)
                .setFont(regularFont).setFontSize(9).setFontColor(ColorConstants.DARK_GRAY)
                .setMarginTop(4));
        }
    }

    protected Table createPdfTable(String[] headers, float[] widths) throws IOException {
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

    protected void addPdfFooter(Document doc, int count) throws IOException {
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

    public File exportEtiquetasQrPdf(List<Producto> productos) throws Exception {
        return new ReporteEtiquetasService().exportEtiquetasQrPdf(productos);
    }

    /** Wraps plain text in a Cell+Paragraph for Table.addCell — itext7's Table
     *  has no addCell(String) overload, unlike itext5's PdfPTable. Fully
     *  qualified: "Cell" bare would resolve to POI's org.apache.poi.ss.
     *  usermodel.Cell via the wildcard import used by the Excel export code
     *  below, not itext7's com.itextpdf.layout.element.Cell. */
    protected static com.itextpdf.layout.element.Cell cell(String text) {
        return new com.itextpdf.layout.element.Cell().add(new Paragraph(text == null ? "" : text));
    }

    // ───────────────────────────── FICHA TÉCNICA ─────────────────────

    public File exportFichaTecnica(Producto p, List<Movimiento> movimientos) throws Exception {
        return new ReporteFichaTecnicaService().exportFichaTecnica(p, movimientos);
    }

    public File exportFichasTecnicasMasivas(List<Producto> bienes,
            MovimientoService movimientoService) throws Exception {
        return new ReporteFichaTecnicaService().exportFichasTecnicasMasivas(bienes, movimientoService);
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

    protected Paragraph sectionTitle(String text, PdfFont bold, DeviceRgb color) {
        return new Paragraph(text).setFont(bold).setFontSize(8.5f).setFontColor(color)
            .setMarginBottom(3).setMarginTop(0);
    }

    protected void addRow(Table table, String key, String value,
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

    protected static com.itextpdf.layout.element.Cell cellSm(String text, PdfFont font) {
        return new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(text == null ? "" : text).setFont(font).setFontSize(8f))
            .setPadding(4);
    }

    protected static Paragraph spacer(float size) {
        return new Paragraph("").setFontSize(size).setMarginBottom(0).setMarginTop(0);
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

    protected String esc(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }

    // ── FEATURE 8: Dashboard PDF export ──────────────────────────────

    public File exportDashboardPdf(DashboardService.Resumen resumen, String destFolder) throws Exception {
        File file = destFolder != null
            ? new File(destFolder, "dashboard_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf")
            : tempFile("dashboard", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            addPdfHeader(doc, "Resumen del Inventario", null, null);

            PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // Stats table
            doc.add(new Paragraph("Estadísticas Generales")
                .setFont(bold).setFontSize(11).setFontColor(COLOR_HEADER)
                .setMarginTop(8).setMarginBottom(4));

            var stats = resumen.stats();
            String[][] statsRows = {
                {"Total de bienes",         String.valueOf(stats.total())},
                {"Bienes activos",          String.valueOf(stats.activos())},
                {"Agotados",                String.valueOf(resumen.agotados().size())},
                {"Bajo stock",              String.valueOf(resumen.bajoStock().size())},
                {"Categorías",              String.valueOf(stats.categorias())},
                {"Valor total (compra)",    com.sibim.util.FormatUtils.formatCurrency(stats.valorTotal())},
            };
            Table tStats = createPdfTable(new String[]{"Indicador", "Valor"}, new float[]{3f, 2f});
            for (String[] row : statsRows) {
                tStats.addCell(cell(row[0]));
                tStats.addCell(cell(row[1]));
            }
            doc.add(tStats);

            // Movimientos hoy
            doc.add(new Paragraph("Movimientos Hoy (" + resumen.movHoy().size() + ")")
                .setFont(bold).setFontSize(11).setFontColor(COLOR_HEADER)
                .setMarginTop(12).setMarginBottom(4));
            if (!resumen.movHoy().isEmpty()) {
                Table tMov = createPdfTable(
                    new String[]{"Bien", "Tipo", "Cantidad", "Usuario"},
                    new float[]{3f, 1.5f, 1f, 2f});
                resumen.movHoy().stream().limit(20).forEach(m -> {
                    tMov.addCell(cell(m.getProductoNombre() != null ? m.getProductoNombre() : ""));
                    tMov.addCell(cell(m.getTipo() != null ? m.getTipo().getEtiqueta() : ""));
                    tMov.addCell(cell(String.valueOf(m.getCantidad())));
                    tMov.addCell(cell(m.getUsuarioNombre() != null ? m.getUsuarioNombre() : ""));
                });
                doc.add(tMov);
                if (resumen.movHoy().size() > 20)
                    doc.add(new Paragraph("… y " + (resumen.movHoy().size() - 20) + " movimientos más")
                        .setFont(regular).setFontSize(9).setFontColor(ColorConstants.GRAY));
            } else {
                doc.add(new Paragraph("Sin movimientos registrados hoy.")
                    .setFont(regular).setFontSize(10).setFontColor(ColorConstants.GRAY));
            }

            // Distribution by area
            if (!resumen.byArea().isEmpty()) {
                doc.add(new Paragraph("Distribución por Área (Top " + resumen.byArea().size() + ")")
                    .setFont(bold).setFontSize(11).setFontColor(COLOR_HEADER)
                    .setMarginTop(12).setMarginBottom(4));
                Table tArea = createPdfTable(new String[]{"Área", "Total bienes"}, new float[]{4f, 1.5f});
                resumen.byArea().forEach((area, count) -> {
                    tArea.addCell(cell(area));
                    tArea.addCell(cell(String.valueOf(count)));
                });
                doc.add(tArea);
            }

            addPdfFooter(doc, (int) stats.total());
        }
        return file;
    }

    // ─────────────────── AUDITORÍA CONSOLIDADA ───────────────────

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
            String org = configRepo.get("nombre_ayuntamiento", "");
            String mun = configRepo.get("municipio", "");
            orgName = org.isBlank() ? "H. Ayuntamiento Municipal"
                    : mun.isBlank() ? org : org + " · " + mun;
        } catch (Exception e) { orgName = "H. Ayuntamiento Municipal"; }

        File file = tempFile("auditoria_consolidada", ".pdf");
        try (com.itextpdf.kernel.pdf.PdfWriter   writer  = new com.itextpdf.kernel.pdf.PdfWriter(file.getAbsolutePath());
             com.itextpdf.kernel.pdf.PdfDocument pdfDoc  = new com.itextpdf.kernel.pdf.PdfDocument(writer);
             com.itextpdf.layout.Document        doc     = new com.itextpdf.layout.Document(pdfDoc, PageSize.A4)) {

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
