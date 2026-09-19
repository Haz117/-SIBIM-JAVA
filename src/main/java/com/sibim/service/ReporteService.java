package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.FolioRepository;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.FormatUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
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
    private final FolioRepository folioRepo;

    public ReporteService() { this(new ProductoRepository(), new MovimientoRepository(), new com.sibim.repository.ConfiguracionRepository(), new FolioRepository()); }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo) {
        this(productoRepo, movimientoRepo, new com.sibim.repository.ConfiguracionRepository(), new FolioRepository());
    }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo,
                   com.sibim.repository.ConfiguracionRepository configRepo) {
        this(productoRepo, movimientoRepo, configRepo, new FolioRepository());
    }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo,
                   com.sibim.repository.ConfiguracionRepository configRepo, FolioRepository folioRepo) {
        this.productoRepo   = productoRepo;
        this.movimientoRepo = movimientoRepo;
        this.configRepo     = configRepo;
        this.folioRepo      = folioRepo;
    }

    protected String orgName() {
        String org = configRepo.get("nombre_ayuntamiento", "");
        String mun = configRepo.get("municipio", "");
        if (org.isBlank()) return "SIBIM — Sistema Integral de Bienes Municipales";
        return mun.isBlank() ? org : org + "  ·  " + mun;
    }

    /**
     * Returns the configured logo file path if it exists on disk, or null.
     * Subclasses call this instead of duplicating the configRepo + file check.
     */
    protected String logoPath() {
        try {
            String path = configRepo.get("logo_path", null);
            if (path != null && new java.io.File(path).exists()) return path;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ReporteService.class)
                .warn("No se pudo leer logo_path de configuración: {}", e.getMessage());
        }
        return null;
    }

    protected static final DeviceRgb COLOR_HEADER = new DeviceRgb(162, 35, 45); // guinda Pantone 1805 C
    protected static final DeviceRgb ROW_ALT_BG  = new DeviceRgb(252, 240, 241); // guinda claro tint for alternating rows
    protected static final DeviceRgb BORDER_LIGHT = new DeviceRgb(226, 228, 233); // light gray border
    protected static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MAX_EXPORT_ROWS = 50_000;

    protected String generateFolio(String prefix) {
        try {
            return folioRepo.next(prefix);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ReporteService.class)
                .warn("FolioRepository.next falló para '{}', usando folio por timestamp: {}", prefix, e.getMessage());
            return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        }
    }

    protected static String getCurrentUserName() {
        com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
        return u != null ? u.getNombre() : "_______________";
    }

    protected void addFirmasBlock(Document doc, String[]... firmas) throws IOException {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb grayFg  = new DeviceRgb(55,  65,  81);
        DeviceRgb grayMut = new DeviceRgb(107, 114, 128);
        doc.add(new Paragraph("").setMarginTop(28));
        float[] cols = new float[firmas.length];
        java.util.Arrays.fill(cols, 1f);
        Table t = new Table(cols).useAllAvailableWidth().setMarginTop(8);
        for (String[] f : firmas) {
            com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(6)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
            c.add(new Paragraph(f[0] != null ? f[0] : "").setFont(bold).setFontSize(8).setFontColor(grayFg));
            c.add(new Paragraph("\n\n________________________").setFont(reg).setFontSize(9));
            c.add(new Paragraph(f[1] != null ? f[1] : "_______________").setFont(bold).setFontSize(7.5f).setMarginTop(2));
            c.add(new Paragraph(f[2] != null ? f[2] : "").setFont(reg).setFontSize(7).setFontColor(grayMut));
            t.addCell(c);
        }
        doc.add(t);
    }

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
                            "N° de Serie", "Ubicacion", "Fecha Registro", "Estado Físico", "N° Factura"};
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
                r.createCell(17).setCellValue(p.getEstadoFisico() != null ? p.getEstadoFisico() : "");
                r.createCell(18).setCellValue(p.getNumeroFactura() != null ? p.getNumeroFactura() : "");
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
            String folio = generateFolio("ALE");
            addPdfHeader(doc, "Alertas de Stock", null, null, folio);
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
            addPdfFooter(doc, agotados.size() + bajoStock.size(), folio);
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
            String folio = generateFolio("DIS");
            addPdfHeader(doc, "Distribución por Área", null, null, folio);
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
            addPdfFooter(doc, porArea.size(), folio);
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

    // ── Depreciación ── delegates to ReporteDepreciacionService

    public File exportDepreciacionExcel(List<Producto> productos) throws Exception {
        return new ReporteDepreciacionService().exportDepreciacionExcel(productos);
    }

    public File exportDepreciacionPdf(List<Producto> productos) throws Exception {
        return new ReporteDepreciacionService().exportDepreciacionPdf(productos);
    }

    public File exportDepreciacionCsv(List<Producto> productos) throws Exception {
        return new ReporteDepreciacionService().exportDepreciacionCsv(productos);
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
            String folio = generateFolio("INV");
            addPdfHeader(doc, "Inventario General", desde, hasta, folio);
            String[] headers = {"Nombre", "Codigo", "Categoria", "Area", "Stock", "Valor", "Estado"};
            float[] widths = {3f, 1.5f, 1.5f, 2f, 1f, 1.5f, 1.2f};
            Table table = createPdfTable(headers, widths);
            int idx = 0;
            for (Producto p : productos) {
                boolean alt = (idx++ % 2) == 1;
                table.addCell(alt ? cellAlt(p.getNombre()) : cell(p.getNombre()));
                table.addCell(alt ? cellAlt(p.getCodigo()) : cell(p.getCodigo()));
                table.addCell(alt ? cellAlt(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : "") : cell(p.getCategoriaNombre() != null ? p.getCategoriaNombre() : ""));
                table.addCell(alt ? cellAlt(p.getArea()) : cell(p.getArea()));
                table.addCell(alt ? cellAlt(String.valueOf(p.getStockActual())) : cell(String.valueOf(p.getStockActual())));
                table.addCell(alt ? cellAlt(FormatUtils.formatCurrency(p.getValorTotal())) : cell(FormatUtils.formatCurrency(p.getValorTotal())));
                table.addCell(alt ? cellAlt(p.getEstado().getEtiqueta()) : cell(p.getEstado().getEtiqueta()));
            }
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ", getCurrentUserName(), "Director de Recursos Materiales"},
                new String[]{"VO.BO.", "_______________", "Secretario General Municipal"});
            addPdfFooter(doc, productos.size(), folio);
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
            String folio = generateFolio("MOV");
            addPdfHeader(doc, "Registro de Movimientos", desde, hasta, folio);
            String[] headers = {"Producto", "Tipo", "Cantidad", "Ant.", "Nuevo", "Usuario", "Fecha"};
            float[] widths = {3f, 1.5f, 1f, 1f, 1f, 2f, 2f};
            Table table = createPdfTable(headers, widths);
            int idx = 0;
            for (Movimiento m : movimientos) {
                boolean alt = (idx++ % 2) == 1;
                table.addCell(alt ? cellAlt(m.getProductoNombre()) : cell(m.getProductoNombre()));
                table.addCell(alt ? cellAlt(m.getTipo().getEtiqueta()) : cell(m.getTipo().getEtiqueta()));
                table.addCell(alt ? cellAlt(String.valueOf(m.getCantidad())) : cell(String.valueOf(m.getCantidad())));
                table.addCell(alt ? cellAlt(String.valueOf(m.getStockAnterior())) : cell(String.valueOf(m.getStockAnterior())));
                table.addCell(alt ? cellAlt(String.valueOf(m.getStockNuevo())) : cell(String.valueOf(m.getStockNuevo())));
                table.addCell(alt ? cellAlt(m.getUsuarioNombre()) : cell(m.getUsuarioNombre()));
                table.addCell(alt ? cellAlt(FormatUtils.formatDateTime(m.getCreadoEn())) : cell(FormatUtils.formatDateTime(m.getCreadoEn())));
            }
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ", getCurrentUserName(), "Director de Recursos Materiales"},
                new String[]{"VO.BO.", "_______________", "Secretario General Municipal"});
            addPdfFooter(doc, movimientos.size(), folio);
        }
        return file;
    }

    public File exportAuditoriaPdf(List<com.sibim.model.AuditLog> logs,
                               String busqueda, String entidad,
                               LocalDate desde, LocalDate hasta) throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaPdf(logs, busqueda, entidad, desde, hasta);
    }

    public File exportAuditoriaCsv(List<com.sibim.model.AuditLog> logs) throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaCsv(logs);
    }

    public File exportAuditoriaExcel(List<com.sibim.model.AuditLog> logs) throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaExcel(logs);
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
        return exportMovimientosCsv(movimientos);
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

    protected void addExcelInfoSheet(Workbook wb, String titulo, LocalDate desde, LocalDate hasta) {
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
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        if (wb instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook xssfWb) {
            org.apache.poi.xssf.usermodel.XSSFCellStyle xStyle =
                (org.apache.poi.xssf.usermodel.XSSFCellStyle) style;
            xStyle.setFillForegroundColor(
                new org.apache.poi.xssf.usermodel.XSSFColor(new byte[]{(byte)162, (byte)35, (byte)45}, null));
        } else {
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.MEDIUM);
        Row headerRow = sheet.createRow(0);
        headerRow.setHeight((short) 480);
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
        sheet.createFreezePane(0, 1);
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
        addPdfHeader(doc, titulo, desde, hasta, null);
    }

    protected void addPdfHeader(Document doc, String titulo, LocalDate desde, LocalDate hasta, String folio) throws IOException {
        PdfFont titleFont   = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regularFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // Try to load the municipal logo; fall back gracefully on any failure.
        Image logoImg = null;
        String lp = logoPath();
        if (lp != null) {
            try {
                ImageData imgData = ImageDataFactory.create(lp);
                logoImg = new Image(imgData);
                logoImg.setMaxHeight(45).setMaxWidth(60).setAutoScale(false);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ReporteService.class)
                    .warn("No se pudo cargar el logo municipal '{}': {}", lp, e.getMessage());
                logoImg = null;
            }
        }

        // Column layout: [logo | title+org | folio] or [title+org | folio] when no logo.
        float[] hw;
        if (logoImg != null && folio != null)       hw = new float[]{1f, 4f, 1.5f};
        else if (logoImg != null)                   hw = new float[]{1f, 4f};
        else if (folio != null)                     hw = new float[]{4f, 1.3f};
        else                                        hw = new float[]{1f};
        Table header = new Table(hw).useAllAvailableWidth();

        if (logoImg != null) {
            com.itextpdf.layout.element.Cell logoCell = new com.itextpdf.layout.element.Cell()
                .add(logoImg.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER))
                .setBackgroundColor(ColorConstants.WHITE)
                .setPadding(6).setBorder(null)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
            header.addCell(logoCell);
        }

        com.itextpdf.layout.element.Cell leftCell = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(titulo).setFont(titleFont).setFontSize(14).setFontColor(ColorConstants.WHITE))
            .add(new Paragraph(orgName()).setFont(regularFont).setFontSize(9)
                .setFontColor(new DeviceRgb(240, 195, 195)))
            .setBackgroundColor(COLOR_HEADER).setPadding(12).setBorder(null);
        header.addCell(leftCell);

        if (folio != null) {
            com.itextpdf.layout.element.Cell folioCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("FOLIO").setFont(titleFont).setFontSize(7)
                    .setFontColor(new DeviceRgb(220, 165, 168))
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                .add(new Paragraph(folio).setFont(titleFont).setFontSize(8)
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER))
                .setBackgroundColor(new DeviceRgb(120, 25, 33))
                .setPadding(8).setBorder(null)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
            header.addCell(folioCell);
        }

        doc.add(header);

        // Thin indigo accent bar below the header
        Table accentBar = new Table(new float[]{1f}).useAllAvailableWidth();
        accentBar.addCell(new com.itextpdf.layout.element.Cell()
            .setHeight(3f)
            .setBackgroundColor(new DeviceRgb(196, 165, 93))
            .setBorder(Border.NO_BORDER));
        doc.add(accentBar);

        String gen = "Generado " + LocalDate.now().format(FMT);
        com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
        if (u != null) gen += " por " + u.getNombre();
        if (desde != null || hasta != null) {
            String periodo = (desde != null ? desde.format(FMT) : "inicio") + " — "
                           + (hasta != null ? hasta.format(FMT) : "hoy");
            doc.add(new Paragraph("Período: " + periodo + "    ·    " + gen)
                .setFont(regularFont).setFontSize(9).setFontColor(ColorConstants.DARK_GRAY)
                .setMarginTop(4));
        } else {
            doc.add(new Paragraph(gen)
                .setFont(regularFont).setFontSize(9).setFontColor(ColorConstants.DARK_GRAY)
                .setMarginTop(4));
        }
    }

    protected Table createPdfTable(String[] headers, float[] widths) throws IOException {
        Table table = new Table(widths).useAllAvailableWidth().setMarginTop(8);
        PdfFont hFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        for (String h : headers) {
            com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(h).setFont(hFont).setFontSize(8).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_HEADER)
                .setPadding(6)
                .setBorderTop(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(new DeviceRgb(120, 25, 33), 1.5f));
            table.addCell(headerCell);
        }
        return table;
    }

    protected void addPdfFooter(Document doc, int count) throws IOException {
        addPdfFooter(doc, count, null);
    }

    protected void addPdfFooter(Document doc, int count, String folio) throws IOException {
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        com.sibim.model.Usuario u = com.sibim.session.SessionManager.getCurrentUser();
        String user = u != null ? u.getNombre() : "—";
        String ts   = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String folioTxt = folio != null ? "Folio: " + folio + "   |   " : "";
        doc.add(new Paragraph(
                folioTxt + "Total: " + count + " registros   |   Generado por: " + user + "   |   " + ts
                + "   |   " + orgName())
            .setFont(font).setFontSize(8).setFontColor(ColorConstants.GRAY)
            .setBorderTop(new com.itextpdf.layout.borders.SolidBorder(new DeviceRgb(209, 213, 219), 0.5f))
            .setPaddingTop(4).setMarginTop(8));
    }

    // ───────────────────────────── ETIQUETAS QR ─────────────────────

    public File exportEtiquetasQrPdf(List<Producto> productos) throws Exception {
        return new ReporteEtiquetasService().exportEtiquetasQrPdf(productos);
    }

    public File exportEtiquetaFisicaPdf(List<Producto> productos) throws Exception {
        return new ReporteEtiquetasService().exportEtiquetaFisicaPdf(productos);
    }

    /** Wraps plain text in a Cell+Paragraph for Table.addCell — itext7's Table
     *  has no addCell(String) overload, unlike itext5's PdfPTable. Fully
     *  qualified: "Cell" bare would resolve to POI's org.apache.poi.ss.
     *  usermodel.Cell via the wildcard import used by the Excel export code
     *  below, not itext7's com.itextpdf.layout.element.Cell. */
    protected static com.itextpdf.layout.element.Cell cell(String text) {
        return new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(text == null ? "" : text).setFontSize(8.5f))
            .setPadding(5)
            .setBorderTop(Border.NO_BORDER)
            .setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f));
    }

    protected static com.itextpdf.layout.element.Cell cellAlt(String text) {
        return cell(text).setBackgroundColor(ROW_ALT_BG);
    }

    // ───────────────────────────── FICHA TÉCNICA ─────────────────────

    public File exportFichaTecnica(Producto p, List<Movimiento> movimientos) throws Exception {
        return new ReporteFichaTecnicaService().exportFichaTecnica(p, movimientos);
    }

    public File exportFichasTecnicasMasivas(List<Producto> bienes,
            MovimientoService movimientoService) throws Exception {
        return new ReporteFichaTecnicaService().exportFichasTecnicasMasivas(bienes, movimientoService);
    }

    public File exportarResguardoPdf(String resguardante, String area, List<Producto> bienes) throws Exception {
        return new ReporteResguardoService().exportarResguardoPdf(resguardante, area, bienes);
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
        return new ReporteBajasService().exportBajasPdf(bajas);
    }

    public File exportBajasExcel(List<com.sibim.model.Producto> bajas) throws Exception {
        return new ReporteBajasService().exportBajasExcel(bajas);
    }

    public File exportBajasCsv(List<com.sibim.model.Producto> bajas) throws Exception {
        return new ReporteBajasService().exportBajasCsv(bajas);
    }

    public File exportActaBaja(com.sibim.model.Producto p) throws Exception {
        return new ReporteBajasService().exportActaBaja(p);
    }

    /** CSV field escaping. Doubles embedded quotes (standard CSV escaping)
     *  AND neutralizes CSV/formula injection (CWE-1236): a free-text field
     *  (proveedor, motivo, nombre de bien, etc.) that starts with = + - or @
     *  gets interpreted as a live formula by Excel/LibreOffice when the
     *  exported file is opened, regardless of the surrounding double-quotes
     *  the printf format strings already add — those are just CSV field
     *  delimiters, stripped before the spreadsheet app looks at the value.
     *  A leading single quote is the standard mitigation: it forces the
     *  cell to render as literal text instead of evaluating it. */
    protected String esc(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        if (!escaped.isEmpty()) {
            char first = escaped.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                escaped = "'" + escaped;
            }
        }
        return escaped;
    }

    // ── Dashboard PDF export ──────────────────────────────

    public File exportDashboardPdf(DashboardService.Resumen resumen, String destFolder) throws Exception {
        return new ReporteDashboardService().exportDashboardPdf(resumen, destFolder);
    }

    // ─────────────────── AUDITORÍA CONSOLIDADA ───────────────────

    public File exportAuditoriaPdf() throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaPdf();
    }
}
