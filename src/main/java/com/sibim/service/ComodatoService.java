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
import com.sibim.model.Comodato;
import com.sibim.model.Producto;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ComodatoRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.PrestamoRepository;
import com.sibim.repository.ProductoRepository;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ComodatoService {

    private final ComodatoRepository      repo;
    private final ProductoRepository      productoRepo;
    private final PrestamoRepository      prestamoRepo;
    private final ConfiguracionRepository cfgRepo;
    private final AuditLogRepository      auditRepo;

    public ComodatoService() {
        this(new ComodatoRepository(), new ProductoRepository(), new PrestamoRepository(),
             new ConfiguracionRepository(), new AuditLogRepository());
    }

    ComodatoService(ComodatoRepository repo, ProductoRepository productoRepo,
                    PrestamoRepository prestamoRepo,
                    ConfiguracionRepository cfgRepo, AuditLogRepository auditRepo) {
        this.repo         = repo;
        this.productoRepo  = productoRepo;
        this.prestamoRepo  = prestamoRepo;
        this.cfgRepo      = cfgRepo;
        this.auditRepo    = auditRepo;
    }

    // Guinda header — rgb(162, 35, 45) = Pantone 1805 C
    private static final DeviceRgb COLOR_HEADER  = new DeviceRgb(162, 35, 45);
    private static final DeviceRgb COLOR_SUBHEAD = new DeviceRgb(252, 240, 241);  // guinda-50
    private static final DeviceRgb COLOR_MUTED   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb COLOR_WARN    = new DeviceRgb(146, 64, 14);    // amber-800
    private static final DateTimeFormatter FMT   = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Public API ────────────────────────────────────────────────────────────

    public List<Comodato> getAll() throws SQLException     { return repo.findAll(); }
    public List<Comodato> getVigentes() throws SQLException { return repo.findVigentes(); }
    public List<Comodato> getVencidos() throws SQLException { return repo.findVencidos(); }

    public int actualizarVencidos() throws SQLException    { return repo.updateVencidos(); }

    public Comodato crear(String productoId, String entidadReceptora,
                          String contactoNombre, String contactoCargo,
                          String domicilio, String motivo, String condiciones,
                          LocalDate fechaInicio, LocalDate fechaFin) throws Exception {
        if (productoId == null || productoId.isBlank())
            throw new IllegalArgumentException("Debe seleccionar un bien");
        if (entidadReceptora == null || entidadReceptora.isBlank())
            throw new IllegalArgumentException("La entidad receptora es obligatoria");
        if (contactoNombre == null || contactoNombre.isBlank())
            throw new IllegalArgumentException("El nombre del contacto es obligatorio");
        if (fechaInicio == null)
            throw new IllegalArgumentException("La fecha de inicio es obligatoria");
        if (fechaFin != null && !fechaFin.isAfter(fechaInicio))
            throw new IllegalArgumentException("La fecha de fin debe ser posterior a la fecha de inicio");

        Producto producto = productoRepo.findById(productoId)
            .orElseThrow(() -> new IllegalArgumentException("Bien no encontrado"));
        if (repo.existeVigentePorProducto(productoId))
            throw new IllegalArgumentException("Este bien ya tiene un comodato vigente — concluye o rescinde el anterior primero");

        if (prestamoRepo.existsActivoForProducto(productoId))
            throw new IllegalArgumentException("El bien ya tiene un préstamo activo — debe devolverse antes de crear un comodato");

        Comodato c = new Comodato();
        c.setProductoId(productoId);
        c.setProductoNombre(producto.getNombre());
        c.setProductoCodigo(producto.getCodigo());
        c.setEntidadReceptora(entidadReceptora.trim());
        c.setContactoNombre(contactoNombre.trim());
        c.setContactoCargo(contactoCargo != null ? contactoCargo.trim() : null);
        c.setDomicilio(domicilio != null ? domicilio.trim() : null);
        c.setMotivo(motivo != null ? motivo.trim() : null);
        c.setCondiciones(condiciones != null ? condiciones.trim() : null);
        c.setFechaInicio(fechaInicio);
        c.setFechaFin(fechaFin);

        Comodato saved = repo.save(c);
        auditRepo.log("comodato", saved.getId(), saved.getProductoNombre(), "crear",
            "Comodato " + saved.getNumero() + " · " + saved.getEntidadReceptora()
                + " · Inicio: " + fechaInicio.format(FMT)
                + (fechaFin != null ? " · Fin: " + fechaFin.format(FMT) : ""));
        return saved;
    }

    public void concluir(String id, LocalDate fechaDevolucionReal) throws SQLException {
        if (fechaDevolucionReal == null) fechaDevolucionReal = LocalDate.now();
        Comodato c = repo.findById(id);
        repo.concluir(id, fechaDevolucionReal);
        if (c != null)
            auditRepo.log("comodato", id, c.getProductoNombre(), "concluir",
                "Comodato " + c.getNumero() + " concluido — devolución: " + fechaDevolucionReal.format(FMT));
    }

    public void rescindir(String id, String motivo) throws SQLException {
        Comodato c = repo.findById(id);
        repo.rescindir(id);
        if (c != null)
            auditRepo.log("comodato", id, c.getProductoNombre(), "rescindir",
                "Comodato " + c.getNumero() + " rescindido" + (motivo != null && !motivo.isBlank() ? " · " + motivo : ""));
    }

    public File exportarPdf(Comodato c) throws Exception {
        return generarPdf(c);
    }

    // ── PDF generation ────────────────────────────────────────────────────────

    private String orgName() {
        String org = cfgRepo.get("nombre_ayuntamiento", "");
        String mun = cfgRepo.get("municipio", "");
        if (org.isBlank()) return "H. Ayuntamiento Municipal";
        return mun.isBlank() ? org : org + " · " + mun;
    }

    private File generarPdf(Comodato c) throws IOException {
        File file = File.createTempFile("sibim_comodato_", ".pdf");
        file.deleteOnExit();

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        boolean vencido = c.isVencido() || Comodato.ESTADO_VENCIDO.equals(c.getEstado());

        // Timestamp-based folio fallback
        String folio = c.getNumero() != null ? c.getNumero()
            : "CDT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            // ── Header ──
            DeviceRgb hColor = vencido ? new DeviceRgb(146, 64, 14) : COLOR_HEADER;
            Table headerTable = new Table(1).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell hCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("COMODATO EN PRÉSTAMO")
                    .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(orgName())
                    .setFont(regular).setFontSize(9)
                    .setFontColor(new DeviceRgb(240, 195, 195))
                    .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(hColor).setPadding(14).setBorder(null);
            headerTable.addCell(hCell);
            doc.add(headerTable);

            // Folio + estado badge
            String estadoTxt = c.isConcluido()  ? "CONCLUIDO"
                : c.isRescindido() ? "RESCINDIDO"
                : vencido          ? "VENCIDO"
                :                    "VIGENTE";
            DeviceRgb estadoColor = c.isConcluido()  ? new DeviceRgb(71, 85, 105)
                : c.isRescindido() ? new DeviceRgb(127, 29, 29)
                : vencido          ? COLOR_WARN
                :                    COLOR_HEADER;
            doc.add(new Paragraph()
                .add(new com.itextpdf.layout.element.Text("No. " + folio + "   ").setFont(bold))
                .add(new com.itextpdf.layout.element.Text("Estado: ").setFont(regular))
                .add(new com.itextpdf.layout.element.Text(estadoTxt).setFont(bold)
                    .setFontColor(estadoColor))
                .setFontSize(9).setFontColor(COLOR_MUTED).setMarginTop(8).setMarginBottom(6));

            // ── Bien ──
            sectionTitle(doc, bold, "BIEN MUNICIPAL");
            Table bienTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginTop(4);
            addInfoRow(bienTable, "Nombre:", c.getProductoNombre(), bold, regular);
            addInfoRow(bienTable, "Código:", orDash(c.getProductoCodigo()), bold, regular);
            doc.add(bienTable);

            // ── Entidad receptora ──
            sectionTitle(doc, bold, "ENTIDAD RECEPTORA");
            Table entTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginTop(4);
            addInfoRow(entTable, "Entidad:", c.getEntidadReceptora(), bold, regular);
            addInfoRow(entTable, "Contacto:", c.getContactoNombre(), bold, regular);
            if (c.getContactoCargo() != null && !c.getContactoCargo().isBlank())
                addInfoRow(entTable, "Cargo:", c.getContactoCargo(), bold, regular);
            if (c.getDomicilio() != null && !c.getDomicilio().isBlank())
                addInfoRow(entTable, "Domicilio:", c.getDomicilio(), bold, regular);
            doc.add(entTable);

            // ── Datos del comodato ──
            sectionTitle(doc, bold, "DATOS DEL COMODATO");
            Table datTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginTop(4);
            addInfoRow(datTable, "Fecha inicio:",
                c.getFechaInicio() != null ? c.getFechaInicio().format(FMT) : "—", bold, regular);
            addInfoRow(datTable, "Fecha fin:",
                c.getFechaFin() != null ? c.getFechaFin().format(FMT) : "Indefinida", bold, regular);
            if (c.getFechaDevolucionReal() != null)
                addInfoRow(datTable, "Devolución efectiva:",
                    c.getFechaDevolucionReal().format(FMT), bold, regular);
            addInfoRow(datTable, "Estado:", estadoTxt, bold, regular);
            doc.add(datTable);

            if (vencido) {
                doc.add(new Paragraph(
                    "⚠ Este comodato está vencido — la fecha de término fue "
                    + (c.getFechaFin() != null ? c.getFechaFin().format(FMT) : "—"))
                    .setFont(bold).setFontSize(8).setFontColor(COLOR_WARN)
                    .setBackgroundColor(new DeviceRgb(254, 243, 199))
                    .setPadding(8).setMarginTop(10));
            }

            // ── Condiciones ──
            if (c.getCondiciones() != null && !c.getCondiciones().isBlank()) {
                sectionTitle(doc, bold, "CONDICIONES DEL COMODATO");
                doc.add(new Paragraph(c.getCondiciones())
                    .setFont(regular).setFontSize(9).setFontColor(new DeviceRgb(15, 23, 42))
                    .setMarginTop(4).setMarginBottom(6));
            }

            // ── Motivo ──
            if (c.getMotivo() != null && !c.getMotivo().isBlank()) {
                sectionTitle(doc, bold, "MOTIVO / USO");
                doc.add(new Paragraph(c.getMotivo())
                    .setFont(regular).setFontSize(9).setFontColor(new DeviceRgb(15, 23, 42))
                    .setMarginTop(4).setMarginBottom(6));
            }

            // ── Firmas ──
            doc.add(new Paragraph("\n\n"));
            Table firmas = new Table(UnitValue.createPercentArray(new float[]{1, 0.4f, 1}))
                .useAllAvailableWidth().setMarginTop(20);
            addFirmaCol(firmas, bold, regular, "ENTREGA POR EL MUNICIPIO", orgName());
            firmas.addCell(new com.itextpdf.layout.element.Cell().setBorder(null));
            addFirmaCol(firmas, bold, regular,
                "RECIBE POR " + c.getEntidadReceptora().toUpperCase(),
                c.getContactoNombre()
                + (c.getContactoCargo() != null && !c.getContactoCargo().isBlank()
                    ? "\n" + c.getContactoCargo() : ""));
            doc.add(firmas);

            // Footer
            doc.add(new Paragraph(
                "Generado por SIBIM · " + orgName() + " · " + LocalDate.now().format(FMT))
                .setFont(regular).setFontSize(7).setFontColor(COLOR_MUTED)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(16));
        }
        return file;
    }

    private void sectionTitle(Document doc, PdfFont bold, String title) {
        doc.add(new Paragraph(title)
            .setFont(bold).setFontSize(8.5f)
            .setFontColor(new DeviceRgb(15, 23, 42))
            .setMarginTop(14).setMarginBottom(2));
    }

    private void addInfoRow(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(label).setFont(bold).setFontSize(9))
            .setBackgroundColor(COLOR_SUBHEAD).setPadding(5)
            .setBorderBottom(new SolidBorder(new DeviceRgb(230, 180, 183), 0.5f))
            .setBorderTop(null).setBorderLeft(null).setBorderRight(null));
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(value != null ? value : "—").setFont(regular).setFontSize(9))
            .setPadding(5)
            .setBorderBottom(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
            .setBorderTop(null).setBorderLeft(null).setBorderRight(null));
    }

    private void addFirmaCol(Table t, PdfFont bold, PdfFont regular, String titulo, String nombre) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph("\n\n\n")
                .setBorderBottom(new SolidBorder(new DeviceRgb(15, 23, 42), 1)))
            .add(new Paragraph(titulo).setFont(bold).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(4))
            .add(new Paragraph(nombre).setFont(regular).setFontSize(7)
                .setFontColor(COLOR_MUTED).setTextAlignment(TextAlignment.CENTER))
            .setBorder(null);
        t.addCell(cell);
    }

    private String orDash(String v) { return (v != null && !v.isBlank()) ? v : "—"; }
}
