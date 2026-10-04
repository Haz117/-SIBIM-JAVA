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
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.AuditLog;
import com.sibim.model.Comodato;
import com.sibim.model.Movimiento;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.FolioRepository;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.FormatUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReporteService {

    private static volatile ReporteService INSTANCE;

    public static ReporteService getInstance() {
        if (INSTANCE == null) {
            synchronized (ReporteService.class) {
                if (INSTANCE == null) INSTANCE = new ReporteService();
            }
        }
        return INSTANCE;
    }

    protected final ProductoRepository   productoRepo;
    protected final MovimientoRepository movimientoRepo;
    private final ConfiguracionRepository configRepo;
    private final FolioRepository folioRepo;

    public ReporteService() { this(new ProductoRepository(), new MovimientoRepository(), new ConfiguracionRepository(), new FolioRepository()); }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo) {
        this(productoRepo, movimientoRepo, new ConfiguracionRepository(), new FolioRepository());
    }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo,
                   ConfiguracionRepository configRepo) {
        this(productoRepo, movimientoRepo, configRepo, new FolioRepository());
    }
    ReporteService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo,
                   ConfiguracionRepository configRepo, FolioRepository folioRepo) {
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

    /** A value from configuracion, or {@code fallback} when it is missing or blank. */
    protected String config(String clave, String fallback) {
        String v = configRepo.get(clave, "");
        return v == null || v.isBlank() ? fallback : v;
    }

    /** A local file with the municipality logo (see {@link LogoMunicipal}), or null. */
    protected String logoPath() {
        return LogoMunicipal.rutaLocal(configRepo);
    }

    /** Resource path of the municipality logo shipped with the app. */
    static final String BUNDLED_LOGO = "/img/logo-municipio.png";
    private static volatile String bundledLogoFile;

    /** The shipped municipality logo, copied once to a temp file so every
     *  consumer can keep passing a plain path to ImageDataFactory. Null when
     *  the build carries no logo. A logo_path set in Configuración wins. */
    static String bundledLogoPath() {
        if (bundledLogoFile != null) return bundledLogoFile;
        synchronized (ReporteService.class) {
            if (bundledLogoFile != null) return bundledLogoFile;
            try (var in = ReporteService.class.getResourceAsStream(BUNDLED_LOGO)) {
                if (in == null) return null;
                java.nio.file.Path tmp = java.nio.file.Files.createTempFile("sibim-logo-", ".png");
                tmp.toFile().deleteOnExit();
                java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                bundledLogoFile = tmp.toString();
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ReporteService.class)
                    .warn("No se pudo preparar el logo municipal incluido: {}", e.getMessage());
            }
            return bundledLogoFile;
        }
    }

    /**
     * Loads the municipal logo for a PDF header, sized to fit a small header
     * cell, or null if none is configured / the file can't be read. Shared so
     * PrestamoService/ComodatoService/ResguardoService/ActaService — each of
     * which builds its own PDF acta/comprobante outside the ReporteXxxService
     * hierarchy — don't each reimplement this loader (before this, only
     * Préstamo actually loaded the logo; Comodato/Resguardo/Acta PDFs never
     * showed it purely because nobody had factored this out).
     */
    protected Image loadHeaderLogo() {
        String lp = logoPath();
        if (lp == null) return null;
        try {
            Image img = new Image(ImageDataFactory.create(lp));
            img.setMaxHeight(45).setMaxWidth(60).setAutoScale(false);
            img.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
            return img;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ReporteService.class)
                .warn("No se pudo cargar el logo municipal '{}': {}", lp, e.getMessage());
            return null;
        }
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
        Usuario u = SessionManager.getCurrentUser();
        return u != null ? u.getNombre() : "_______________";
    }

    /** Relative column widths that hold. iText reads a plain {@code float[]} as
     *  points and then sizes the columns by their content, so two tables given
     *  the same widths did not line up and narrow columns were crushed. */
    protected static Table tabla(float... relativos) {
        return new Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(relativos))
            .useAllAvailableWidth().setFixedLayout();
    }

    /** Signature block: {title, name or null, role} per signer. Kept in one piece,
     *  so a page never ends with the titles and starts with the lines. */
    protected void addFirmasBlock(Document doc, String[]... firmas) throws IOException {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb grayFg  = new DeviceRgb(55,  65,  81);
        DeviceRgb grayMut = new DeviceRgb(107, 114, 128);
        float[] cols = new float[firmas.length];
        Arrays.fill(cols, 1f);
        Table t = tabla(cols).setMarginTop(26).setKeepTogether(true);
        for (String[] f : firmas) {
            boolean conNombre = f[1] != null && !f[1].isBlank() && !f[1].startsWith("___");
            com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
                .setBorder(Border.NO_BORDER).setPaddingLeft(14).setPaddingRight(14)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
            c.add(new Paragraph(f[0] != null ? f[0] : "").setFont(bold).setFontSize(8).setFontColor(grayFg)
                .setMargin(0).setMarginBottom(30));
            c.add(new Table(1).useAllAvailableWidth().addCell(new com.itextpdf.layout.element.Cell()
                .setHeight(1).setPadding(0).setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(grayFg, 0.7f))));
            c.add(new Paragraph(conNombre ? f[1] : "Nombre y firma").setFont(conNombre ? bold : reg)
                .setFontSize(7.5f).setFontColor(conNombre ? grayFg : grayMut).setMargin(0).setMarginTop(3));
            c.add(new Paragraph(f[2] != null ? f[2] : "").setFont(reg).setFontSize(7).setFontColor(grayMut).setMargin(0));
            t.addCell(c);
        }
        doc.add(t);
    }

    /** Stamps "Página N de M" and the institution at the foot of every page. Done
     *  on the finished file: the total is not known while the document is laid
     *  out, and iText has already flushed the earlier pages by then. */
    protected File numerarPaginas(File pdf) {
        if (pdf == null) return null;
        File tmp = null;
        try {
            tmp = File.createTempFile("sibim_paginas_", ".pdf");
            PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            DeviceRgb gris = new DeviceRgb(107, 114, 128);
            try (PdfDocument d = new PdfDocument(new com.itextpdf.kernel.pdf.PdfReader(pdf.getAbsolutePath()),
                                                 new PdfWriter(tmp.getAbsolutePath()));
                 Document doc = new Document(d)) {
                int total = d.getNumberOfPages();
                String org = "SIBIM  ·  " + orgName();
                for (int i = 1; i <= total; i++) {
                    com.itextpdf.kernel.geom.Rectangle hoja = d.getPage(i).getPageSize();
                    doc.showTextAligned(new Paragraph(org).setFont(font).setFontSize(6.5f).setFontColor(gris),
                        hoja.getLeft() + 28, hoja.getBottom() + 12, i,
                        com.itextpdf.layout.properties.TextAlignment.LEFT,
                        com.itextpdf.layout.properties.VerticalAlignment.BOTTOM, 0);
                    doc.showTextAligned(new Paragraph("Página " + i + " de " + total).setFont(font).setFontSize(6.5f).setFontColor(gris),
                        hoja.getRight() - 28, hoja.getBottom() + 12, i,
                        com.itextpdf.layout.properties.TextAlignment.RIGHT,
                        com.itextpdf.layout.properties.VerticalAlignment.BOTTOM, 0);
                }
            }
            java.nio.file.Files.move(tmp.toPath(), pdf.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ReporteService.class)
                .warn("No se pudieron numerar las páginas de {}: {}", pdf.getName(), e.getMessage());
            if (tmp != null) tmp.delete();
        }
        return pdf;
    }

    /** For the reports that audit the inventory as a whole. */
    private static void soloPatrimonio() {
        if (!com.sibim.session.Permisos.veReportesDeControl())
            throw new SecurityException(com.sibim.session.Permisos.SOLO_PATRIMONIO_REPORTE);
    }

    protected static <T> List<T> guardExportSize(List<T> rows, String entidad) throws Exception {
        if (rows.size() > MAX_EXPORT_ROWS)
            throw new Exception("El reporte incluye " + rows.size() + " " + entidad
                + ". Filtra el rango de fechas para reducirlo (máx. " + MAX_EXPORT_ROWS + " filas por exportación).");
        return rows;
    }

    // ───────────────────────────── EXCEL ─────────────────────────────

    public File exportInventarioExcel(LocalDate desde, LocalDate hasta) throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportInventarioExcel(desde, hasta);
    }

    /** Same Excel report, given an explicit list — bulk action / selection export. */
    public File exportInventarioExcel(List<Producto> productos) throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportInventarioExcel(productos);
    }

    public File exportMovimientosExcel(List<Movimiento> movimientos) throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportMovimientosExcel(movimientos);
    }

    public File exportMovimientosExcel(LocalDate desde, LocalDate hasta) throws Exception {
        soloPatrimonio();
        return new ReporteExcelService(productoRepo, movimientoRepo).exportMovimientosExcel(desde, hasta);
    }

    public File exportDistribucionExcel() throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportDistribucionExcel();
    }

    public File exportDistribucionCsv() throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportDistribucionCsv();
    }

    public File exportAlertasCsv() throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportAlertasCsv();
    }

    public File exportAlertasPdf() throws Exception {
        List<Producto> todos      = guardExportSize(productoRepo.findAll(), "bienes");
        List<Producto> garantias  = productoRepo.findVencidosProximos(30);
        List<Producto> pendientes = todos.stream().filter(p -> pendientePatrimonial(p) != null).toList();
        File file = tempFile("alertas", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            String folio = generateFolio("ALE");
            addPdfHeader(doc, "Alertas y pendientes", null, null, folio);
            PdfFont sectionFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            doc.add(new Paragraph("Garantías vencidas o por vencer en 30 días (" + garantias.size() + ")")
                .setFont(sectionFont).setFontSize(11).setFontColor(COLOR_HEADER).setMarginTop(8).setMarginBottom(0));
            Table t1 = createPdfTable(new String[]{"Bien", "Código", "Área", "Garantía hasta"},
                new float[]{3f, 1.2f, 2.6f, 2.7f});
            int i1 = 0;
            for (Producto p : garantias) {
                boolean alt = (i1++ % 2) == 1;
                t1.addCell(fila(p.getNombre(), alt)); t1.addCell(fila(p.getCodigo(), alt));
                t1.addCell(fila(p.getArea(), alt));
                t1.addCell(fila(p.getFechaVencimiento() != null ? FormatUtils.formatDate(p.getFechaVencimiento()) : "", alt));
            }
            if (garantias.isEmpty()) sinRegistros(t1, 4, "Sin garantías vencidas ni por vencer");
            doc.add(t1);
            doc.add(new Paragraph("Pendientes patrimoniales (" + pendientes.size() + ")")
                .setFont(sectionFont).setFontSize(11).setFontColor(COLOR_HEADER).setMarginTop(14).setMarginBottom(0));
            Table t2 = createPdfTable(new String[]{"Bien", "Código", "Área", "Pendiente"},
                new float[]{3f, 1.2f, 2.6f, 2.7f});
            int i2 = 0;
            for (Producto p : pendientes) {
                boolean alt = (i2++ % 2) == 1;
                t2.addCell(fila(p.getNombre(), alt)); t2.addCell(fila(p.getCodigo(), alt));
                t2.addCell(fila(p.getArea(), alt));
                t2.addCell(fila(pendientePatrimonial(p), alt));
            }
            if (pendientes.isEmpty()) sinRegistros(t2, 4, "Todos los bienes tienen resguardante y etiqueta");
            doc.add(t2);
            addPdfFooter(doc, garantias.size() + pendientes.size(), folio);
        }
        return numerarPaginas(file);
    }

    /** "Sin resguardante" / "Sin etiquetar": what an audit asks about a bien. */
    static String pendientePatrimonial(Producto p) {
        boolean sinResguardo = p.getResguardante() == null || p.getResguardante().isBlank();
        if (sinResguardo && !p.isEtiquetado()) return "Sin resguardante · Sin etiquetar";
        if (sinResguardo) return "Sin resguardante";
        return p.isEtiquetado() ? null : "Sin etiquetar";
    }

    public File exportDistribucionPdf() throws Exception {
        List<Producto> productos = guardExportSize(productoRepo.findAll(), "bienes");
        Map<String, List<Producto>> porArea = productos.stream()
            .collect(Collectors.groupingBy(p -> p.getArea() != null ? p.getArea() : "Sin área"));
        File file = tempFile("distribucion", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            String folio = generateFolio("DIS");
            addPdfHeader(doc, "Distribución por Área", null, null, folio);
            String[] headers = {"Área", "Bienes", "Valor total", "Sin resguardante", "Sin etiquetar"};
            float[] widths = {4f, 1f, 1.6f, 1.4f, 1.3f};
            Table table = createPdfTable(headers, widths);
            alinearDerecha(table, 1, 2, 3, 4);
            long sinResguardoTotal = 0, sinEtiquetaTotal = 0;
            BigDecimal valorGlobal = BigDecimal.ZERO;
            int idx = 0;
            for (Map.Entry<String, List<Producto>> entry : new java.util.TreeMap<>(porArea).entrySet()) {
                boolean alt = (idx++ % 2) == 1;
                List<Producto> ps = entry.getValue();
                long sinResguardo = ps.stream().filter(p -> p.getResguardante() == null || p.getResguardante().isBlank()).count();
                long sinEtiqueta  = ps.stream().filter(p -> !p.isEtiquetado()).count();
                BigDecimal valor = ps.stream().map(Producto::getValorTotal).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                sinResguardoTotal += sinResguardo;
                sinEtiquetaTotal += sinEtiqueta;
                valorGlobal = valorGlobal.add(valor);
                table.addCell(fila(entry.getKey(), alt));
                table.addCell(num(fila(String.valueOf(ps.size()), alt)));
                table.addCell(num(fila(com.sibim.session.Permisos.pesos(valor), alt)));
                table.addCell(num(fila(String.valueOf(sinResguardo), alt)));
                table.addCell(num(fila(String.valueOf(sinEtiqueta), alt)));
            }
            addFilaTotal(table, 1, "TOTAL", String.valueOf(productos.size()), com.sibim.session.Permisos.pesos(valorGlobal),
                String.valueOf(sinResguardoTotal), String.valueOf(sinEtiquetaTotal));
            doc.add(table);
            addPdfFooter(doc, porArea.size(), folio);
        }
        return numerarPaginas(file);
    }

    public File exportAlertasExcel() throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportAlertasExcel();
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
            String[] headers = {"Bien", "Código", "Categoría", "Área", "Cantidad", "Valor", "Estado"};
            float[] widths = {3.2f, 1.2f, 1.7f, 2.6f, 0.9f, 1.4f, 1f};
            Table table = createPdfTable(headers, widths);
            alinearDerecha(table, 4, 5);
            int idx = 0;
            BigDecimal valorTotal = BigDecimal.ZERO;
            for (Producto p : productos) {
                boolean alt = (idx++ % 2) == 1;
                BigDecimal valor = p.getValorTotal() != null ? p.getValorTotal() : BigDecimal.ZERO;
                valorTotal = valorTotal.add(valor);
                table.addCell(fila(p.getNombre(), alt));
                table.addCell(fila(p.getCodigo(), alt));
                table.addCell(fila(p.getCategoriaNombre(), alt));
                table.addCell(fila(p.getArea(), alt));
                table.addCell(num(fila(String.valueOf(p.getStockActual()), alt)));
                table.addCell(num(fila(com.sibim.session.Permisos.pesos(valor), alt)));
                table.addCell(fila(p.getEstado().getEtiqueta(), alt));
            }
            addFilaTotal(table, 5, "VALOR TOTAL DEL INVENTARIO  (" + productos.size() + " bienes)",
                com.sibim.session.Permisos.pesos(valorTotal), "");
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ", getCurrentUserName(), "Director de Recursos Materiales"},
                new String[]{"VO.BO.", null, "Secretario General Municipal"});
            addPdfFooter(doc, productos.size(), folio);
        }
        return numerarPaginas(file);
    }

    public File exportMovimientosPdf(LocalDate desde, LocalDate hasta) throws Exception {
        soloPatrimonio();
        List<Movimiento> movimientos = guardExportSize(movimientoRepo.findByDateRange(desde, hasta), "movimientos");
        if (movimientos.isEmpty()) return null;
        File file = tempFile("movimientos", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {
            String folio = generateFolio("MOV");
            addPdfHeader(doc, "Registro de Movimientos", desde, hasta, folio);
            String[] headers = {"Bien", "Tipo", "Cantidad", "Antes", "Después", "Registró", "Fecha"};
            float[] widths = {3.2f, 1.3f, 0.9f, 0.8f, 0.9f, 2.3f, 1.6f};
            Table table = createPdfTable(headers, widths);
            alinearDerecha(table, 2, 3, 4);
            int idx = 0;
            for (Movimiento m : movimientos) {
                boolean alt = (idx++ % 2) == 1;
                table.addCell(fila(m.getProductoNombre(), alt));
                table.addCell(fila(m.getTipo().getEtiqueta(), alt));
                table.addCell(num(fila(String.valueOf(m.getCantidad()), alt)));
                table.addCell(num(fila(String.valueOf(m.getStockAnterior()), alt)));
                table.addCell(num(fila(String.valueOf(m.getStockNuevo()), alt)));
                table.addCell(fila(m.getUsuarioNombre(), alt));
                table.addCell(fila(FormatUtils.formatDateTime(m.getCreadoEn()), alt));
            }
            doc.add(table);
            addFirmasBlock(doc,
                new String[]{"ELABORÓ", getCurrentUserName(), "Director de Recursos Materiales"},
                new String[]{"VO.BO.", null, "Secretario General Municipal"});
            addPdfFooter(doc, movimientos.size(), folio);
        }
        return numerarPaginas(file);
    }

    public File exportAuditoriaPdf(List<AuditLog> logs,
                               String busqueda, String entidad,
                               LocalDate desde, LocalDate hasta) throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaPdf(logs, busqueda, entidad, desde, hasta);
    }

    public File exportAuditoriaCsv(List<AuditLog> logs) throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaCsv(logs);
    }

    public File exportAuditoriaExcel(List<AuditLog> logs) throws Exception {
        return new ReporteAuditoriaService().exportAuditoriaExcel(logs);
    }

    // ───────────────────────────── CSV ─────────────────────────────

    public File exportInventarioCsv(LocalDate desde, LocalDate hasta) throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportInventarioCsv(desde, hasta);
    }

    /** Same CSV report, given an explicit list — see the Excel overload above. */
    public File exportInventarioCsv(List<Producto> productos) throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportInventarioCsv(productos);
    }

    public File exportMovimientosCsv(List<Movimiento> movimientos) throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportMovimientosCsv(movimientos);
    }

    public File exportMovimientosCsv(LocalDate desde, LocalDate hasta) throws Exception {
        soloPatrimonio();
        return new ReporteCsvService(productoRepo, movimientoRepo).exportMovimientosCsv(desde, hasta);
    }

    // ───────────────────────── Resguardos ──────────────────────────────

    public File exportResguardosExcel(List<Resguardo> resguardos) throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportResguardosExcel(resguardos);
    }

    public File exportResguardosCsv(List<Resguardo> resguardos) throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportResguardosCsv(resguardos);
    }

    // ───────────────────────── Comodatos ───────────────────────────────

    public File exportComodatosExcel(List<Comodato> comodatos) throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportComodatosExcel(comodatos);
    }

    public File exportComodatosCsv(List<Comodato> comodatos) throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportComodatosCsv(comodatos);
    }

    // ───────────────────────── Préstamos ───────────────────────────────

    public File exportPrestamosExcel(List<Prestamo> prestamos) throws Exception {
        return new ReporteExcelService(productoRepo, movimientoRepo).exportPrestamosExcel(prestamos);
    }

    public File exportPrestamosCsv(List<Prestamo> prestamos) throws Exception {
        return new ReporteCsvService(productoRepo, movimientoRepo).exportPrestamosCsv(prestamos);
    }

    // ───────────────────────────── Helpers ─────────────────────────────

    protected File tempFile(String prefix, String suffix) throws IOException {
        // Every Excel and CSV export passes through here: an editable copy of the
        // inventory leaves the system only from Patrimonio's hands.
        if ((".xlsx".equals(suffix) || ".csv".equals(suffix)) && !com.sibim.session.Permisos.exportaHojasDeCalculo())
            throw new SecurityException(com.sibim.session.Permisos.SOLO_PDF);
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
        Usuario u = SessionManager.getCurrentUser();
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
        CellStyle etiqueta = wb.createCellStyle();
        Font negrita = wb.createFont();
        negrita.setBold(true);
        etiqueta.setFont(negrita);
        for (int i = 0; i < rows.length; i++) info.getRow(i).getCell(0).setCellStyle(etiqueta);
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
            style.setFillForegroundColor(IndexedColors.MAROON.getIndex());   // closest indexed guinda
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

    /** Last step of every Excel sheet: column widths, then the look of the data
     *  rows and how the sheet prints. */
    protected void autosizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
            // Room for the filter arrow, and no single long text taking the whole screen.
            sheet.setColumnWidth(i, Math.min(Math.max(sheet.getColumnWidth(i) + 900, 2800), 60 * 256));
        }
        darFormatoDeDatos(sheet, count);
    }

    private static final java.util.regex.Pattern COLUMNA_DE_DINERO = java.util.regex.Pattern.compile(
        "valor|importe|precio|costo|monto", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** Zebra rows with a hairline under each, amounts as currency, and a sheet that
     *  prints landscape, one page wide, with the title row on every page. */
    private void darFormatoDeDatos(Sheet sheet, int count) {
        Workbook wb = sheet.getWorkbook();
        Row encabezado = sheet.getRow(0);
        if (encabezado == null) return;
        boolean[] dinero = new boolean[count];
        for (int c = 0; c < count; c++) {
            org.apache.poi.ss.usermodel.Cell h = encabezado.getCell(c);
            dinero[c] = h != null && h.getCellType() == CellType.STRING
                && COLUMNA_DE_DINERO.matcher(h.getStringCellValue()).find();
        }
        // [zebra][kind]: 0 text, 1 number, 2 currency — a workbook holds few styles, so they are shared.
        CellStyle[][] estilos = new CellStyle[2][3];
        short moneda = wb.createDataFormat().getFormat("$#,##0.00");
        for (int z = 0; z < 2; z++) for (int k = 0; k < 3; k++) {
            CellStyle st = wb.createCellStyle();
            st.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            st.setBorderBottom(BorderStyle.HAIR);
            st.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            if (k > 0) st.setAlignment(HorizontalAlignment.RIGHT);
            if (k == 2) st.setDataFormat(moneda);
            if (z == 1 && st instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle x) {
                x.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(
                    new byte[]{(byte) 252, (byte) 240, (byte) 241}, null));
                x.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            estilos[z][k] = st;
        }
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int z = r % 2 == 0 ? 1 : 0;
            for (int c = 0; c < count; c++) {
                org.apache.poi.ss.usermodel.Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                boolean numero = cell.getCellType() == CellType.NUMERIC;
                cell.setCellStyle(estilos[z][numero ? (dinero[c] ? 2 : 1) : 0]);
            }
        }
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, count - 1));
        sheet.getFooter().setLeft("SIBIM · " + orgName());
        sheet.getFooter().setRight("Página &P de &N");
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
        Table header = tabla(hw);

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
            .setBackgroundColor(COLOR_HEADER).setPadding(12).setBorder(null)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
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
        Usuario u = SessionManager.getCurrentUser();
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

    /** A listing table. The header row repeats on every page. */
    protected Table createPdfTable(String[] headers, float[] widths) throws IOException {
        Table table = tabla(widths).setMarginTop(8);
        PdfFont hFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        for (String h : headers) {
            com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(h).setFont(hFont).setFontSize(8).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(COLOR_HEADER)
                .setPadding(6)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setBorderTop(Border.NO_BORDER)
                .setBorderLeft(Border.NO_BORDER)
                .setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(new DeviceRgb(120, 25, 33), 1.5f));
            table.addHeaderCell(headerCell);
        }
        return table;
    }

    /** Right-aligns the header cells of the numeric columns, to match {@link #num}. */
    protected static void alinearDerecha(Table table, int... columnas) {
        for (int c : columnas)
            table.getHeader().getCell(0, c).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
    }

    /** Amounts and quantities line up on the right. */
    protected static com.itextpdf.layout.element.Cell num(com.itextpdf.layout.element.Cell c) {
        return c.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT);
    }

    /** A closing row: label over the first {@code span} columns, then one bold cell per value. */
    protected void addFilaTotal(Table table, int span, String etiqueta, String... valores) throws IOException {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        SolidBorder arriba = new SolidBorder(COLOR_HEADER, 1f);
        table.addCell(new com.itextpdf.layout.element.Cell(1, span)
            .add(new Paragraph(etiqueta).setFont(bold).setFontSize(8.5f).setFontColor(COLOR_HEADER))
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
            .setBackgroundColor(ROW_ALT_BG).setPadding(5).setBorder(Border.NO_BORDER).setBorderTop(arriba));
        for (String v : valores)
            table.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(v == null ? "" : v).setFont(bold).setFontSize(8.5f).setFontColor(COLOR_HEADER))
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
                .setBackgroundColor(ROW_ALT_BG).setPadding(5).setBorder(Border.NO_BORDER).setBorderTop(arriba));
    }

    protected void addPdfFooter(Document doc, int count) throws IOException {
        addPdfFooter(doc, count, null);
    }

    protected void addPdfFooter(Document doc, int count, String folio) throws IOException {
        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        Usuario u = SessionManager.getCurrentUser();
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
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorderTop(Border.NO_BORDER)
            .setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f));
    }

    protected static com.itextpdf.layout.element.Cell cellAlt(String text) {
        return cell(text).setBackgroundColor(ROW_ALT_BG);
    }

    /** A data cell of a zebra-striped listing. */
    protected static com.itextpdf.layout.element.Cell fila(String text, boolean alt) {
        return alt ? cellAlt(text) : cell(text);
    }

    /** One full-width row saying the listing is empty, instead of a header with nothing under it. */
    protected static void sinRegistros(Table table, int columnas, String mensaje) {
        table.addCell(new com.itextpdf.layout.element.Cell(1, columnas)
            .add(new Paragraph(mensaje).setFontSize(8.5f).setFontColor(new DeviceRgb(107, 114, 128)))
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setPadding(10).setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f)));
    }

    // ───────────────────────────── FICHA TÉCNICA ─────────────────────

    public File exportFichaTecnica(Producto p, List<Movimiento> movimientos) throws Exception {
        return new ReporteFichaTecnicaService().exportFichaTecnica(p, movimientos);
    }

    /** Formato para que un área pida la baja de uno o varios bienes (una página por bien). */
    public File exportSolicitudBaja(List<Producto> bienes) throws Exception {
        return new ReporteSolicitudBajaService().exportSolicitudBaja(bienes);
    }

    /** Dictamen técnico que el área técnica anexa a la solicitud de baja (una página por bien). */
    public File exportDictamenBaja(List<Producto> bienes) throws Exception {
        return new ReporteDictamenBajaService().exportDictamenBaja(bienes);
    }

    /** El dictamen técnico de baja sin datos, para llenarlo a mano. */
    public File exportDictamenBajaEnBlanco() throws Exception {
        return new ReporteDictamenBajaService().exportDictamenBajaEnBlanco();
    }

    public File exportFichasTecnicasMasivas(List<Producto> bienes,
            MovimientoService movimientoService) throws Exception {
        return new ReporteFichaTecnicaService().exportFichasTecnicasMasivas(bienes, movimientoService);
    }

    /** Formato MLA: descripción, número de inventario y fotografía de cada bien. */
    public File exportInventarioFotografico(List<Producto> bienes) throws Exception {
        return new ReporteInventarioFotograficoService().exportInventarioFotografico(bienes);
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
        List<Producto> bajas = productoRepo.findAll(true).stream().filter(Producto::isDadoDeBaja).toList();
        return guardExportSize(bajas, "bienes dados de baja");
    }

    public File exportBajasPdf() throws Exception { soloPatrimonio(); return exportBajasPdf(fetchBajas()); }
    public File exportBajasExcel() throws Exception { soloPatrimonio(); return exportBajasExcel(fetchBajas()); }
    public File exportBajasCsv() throws Exception { soloPatrimonio(); return exportBajasCsv(fetchBajas()); }

    public File exportBajasPdf(List<Producto> bajas) throws Exception {
        return new ReporteBajasService().exportBajasPdf(bajas);
    }

    public File exportBajasExcel(List<Producto> bajas) throws Exception {
        return new ReporteBajasService().exportBajasExcel(bajas);
    }

    public File exportBajasCsv(List<Producto> bajas) throws Exception {
        return new ReporteBajasService().exportBajasCsv(bajas);
    }

    public File exportActaBaja(Producto p) throws Exception {
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

    // ── Resguardo de Bienes Muebles (formato pág. 3) ─────
    public File exportBienesMueblesPdf(List<Producto> bienes, String resguardante,
                                        String cargo, String area, String numero) throws Exception {
        return new ReporteBienesMueblesService().exportBienesMueblesPdf(bienes, resguardante, cargo, area, numero);
    }

    // ── Parque Vehicular V.6 ─────────────────────────────

    public File exportParqueVehicularPdf(List<Producto> vehiculos) throws Exception {
        soloPatrimonio();
        return new ReporteParqueVehicularService().exportParqueVehicularPdf(vehiculos);
    }

    // ── Entrega-Recepción ANEXO V.4 ──────────────────────

    public File exportEntregaRecepcionPdf(List<Producto> bienes) throws Exception {
        soloPatrimonio();
        return new ReporteEntregaRecepcionService().exportEntregaRecepcionPdf(bienes);
    }

    // ── Dashboard PDF export ──────────────────────────────

    public File exportDashboardPdf(DashboardService.Resumen resumen, String destFolder) throws Exception {
        return new ReporteDashboardService().exportDashboardPdf(resumen, destFolder);
    }

    // ─────────────────── AUDITORÍA CONSOLIDADA ───────────────────

    public File exportAuditoriaPdf() throws Exception {
        soloPatrimonio();
        return new ReporteAuditoriaService().exportAuditoriaPdf();
    }
}
