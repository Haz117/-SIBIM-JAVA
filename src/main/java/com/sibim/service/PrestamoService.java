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
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.repository.PrestamoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class PrestamoService {

    private final PrestamoRepository repo        = new PrestamoRepository();
    private final ProductoRepository productoRepo = new ProductoRepository();
    private final ConfiguracionRepository cfgRepo = new ConfiguracionRepository();

    private static final DeviceRgb COLOR_HEADER  = new DeviceRgb(22, 101, 52);   // green-800
    private static final DeviceRgb COLOR_SUBHEAD = new DeviceRgb(240, 253, 244);  // green-50
    private static final DeviceRgb COLOR_MUTED   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb COLOR_WARN    = new DeviceRgb(146, 64, 14);    // amber-800
    private static final DateTimeFormatter FMT   = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<Prestamo> getAll() throws SQLException { return repo.findAll(); }

    public List<Prestamo> getActivos() throws SQLException { return repo.findActivos(); }

    public int actualizarVencidos() throws SQLException { return repo.updateVencidos(); }

    public Prestamo crear(String productoId, String areaDestino,
                          String responsableNombre, String responsableCargo,
                          String motivo, LocalDate fechaDevolucionPrevista) throws Exception {
        if (productoId == null || productoId.isBlank())
            throw new IllegalArgumentException("Debe seleccionar un bien");
        if (areaDestino == null || areaDestino.isBlank())
            throw new IllegalArgumentException("El área destino es obligatoria");
        if (responsableNombre == null || responsableNombre.isBlank())
            throw new IllegalArgumentException("El nombre del responsable es obligatorio");
        if (fechaDevolucionPrevista == null)
            throw new IllegalArgumentException("La fecha de devolución prevista es obligatoria");
        if (!fechaDevolucionPrevista.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("La fecha de devolución debe ser posterior a hoy");

        Producto producto = productoRepo.findById(productoId)
            .orElseThrow(() -> new IllegalArgumentException("Bien no encontrado"));

        Prestamo p = new Prestamo();
        p.setProductoId(productoId);
        p.setProductoNombre(producto.getNombre());
        p.setProductoCodigo(producto.getCodigo());
        p.setAreaOrigen(producto.getArea() != null ? producto.getArea() : "Sin área");
        p.setAreaDestino(areaDestino.trim());
        p.setResponsableNombre(responsableNombre.trim());
        p.setResponsableCargo(responsableCargo != null ? responsableCargo.trim() : null);
        p.setMotivo(motivo != null ? motivo.trim() : null);
        p.setFechaPrestamo(LocalDate.now());
        p.setFechaDevolucionPrevista(fechaDevolucionPrevista);
        return repo.save(p);
    }

    public void devolver(String prestamoId, LocalDate fechaDevolucionReal) throws SQLException {
        if (fechaDevolucionReal == null) fechaDevolucionReal = LocalDate.now();
        repo.devolver(prestamoId, fechaDevolucionReal);
    }

    public File exportarPdf(Prestamo prestamo) throws Exception {
        return generarPdf(prestamo);
    }

    private String orgName() {
        String org = cfgRepo.get("nombre_ayuntamiento", "");
        String mun = cfgRepo.get("municipio", "");
        if (org.isBlank()) return "H. Ayuntamiento Municipal";
        return mun.isBlank() ? org : org + " · " + mun;
    }

    private File generarPdf(Prestamo p) throws IOException {
        File file = File.createTempFile("sibim_prestamo_", ".pdf");
        file.deleteOnExit();

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        boolean vencido = p.isVencidoCalc()
            || Prestamo.ESTADO_VENCIDO.equals(p.getEstado());

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            // ── Header ──
            DeviceRgb hColor = vencido ? new DeviceRgb(146, 64, 14) : COLOR_HEADER;
            Table headerTable = new Table(1).useAllAvailableWidth();
            com.itextpdf.layout.element.Cell hCell = new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("COMPROBANTE DE PRÉSTAMO TEMPORAL")
                    .setFont(bold).setFontSize(14).setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(orgName())
                    .setFont(regular).setFontSize(9)
                    .setFontColor(new DeviceRgb(200, 210, 240))
                    .setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(hColor).setPadding(14).setBorder(null);
            headerTable.addCell(hCell);
            doc.add(headerTable);

            // Estado badge
            String estadoText = Prestamo.ESTADO_DEVUELTO.equals(p.getEstado()) ? "DEVUELTO"
                : vencido ? "VENCIDO" : "ACTIVO";
            DeviceRgb estadoColor = Prestamo.ESTADO_DEVUELTO.equals(p.getEstado())
                ? new DeviceRgb(71, 85, 105)
                : vencido ? COLOR_WARN : COLOR_HEADER;
            doc.add(new Paragraph()
                .add(new com.itextpdf.layout.element.Text("No. " + p.getNumero() + "   ").setFont(bold))
                .add(new com.itextpdf.layout.element.Text("Estado: ").setFont(regular))
                .add(new com.itextpdf.layout.element.Text(estadoText).setFont(bold)
                    .setFontColor(estadoColor))
                .setFontSize(9).setFontColor(COLOR_MUTED).setMarginTop(8).setMarginBottom(6));

            // ── Información del bien ──
            sectionTitle(doc, bold, "BIEN PRESTADO");
            Table bienTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginTop(4);
            addInfoRow(bienTable, "Nombre:", p.getProductoNombre(), bold, regular);
            addInfoRow(bienTable, "Código:", orDash(p.getProductoCodigo()), bold, regular);
            addInfoRow(bienTable, "Área de origen:", p.getAreaOrigen(), bold, regular);
            doc.add(bienTable);

            // ── Detalles del préstamo ──
            sectionTitle(doc, bold, "DATOS DEL PRÉSTAMO");
            Table detTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                .useAllAvailableWidth().setMarginTop(4);
            addInfoRow(detTable, "Prestado a (área):", p.getAreaDestino(), bold, regular);
            addInfoRow(detTable, "Responsable:", p.getResponsableNombre(), bold, regular);
            if (p.getResponsableCargo() != null && !p.getResponsableCargo().isBlank())
                addInfoRow(detTable, "Cargo:", p.getResponsableCargo(), bold, regular);
            addInfoRow(detTable, "Fecha de préstamo:",
                p.getFechaPrestamo() != null ? p.getFechaPrestamo().format(FMT) : "—", bold, regular);
            addInfoRow(detTable, "Devolución prevista:",
                p.getFechaDevolucionPrevista().format(FMT), bold, regular);
            if (p.getFechaDevolucionReal() != null)
                addInfoRow(detTable, "Devolución efectiva:",
                    p.getFechaDevolucionReal().format(FMT), bold, regular);
            if (p.getMotivo() != null && !p.getMotivo().isBlank())
                addInfoRow(detTable, "Motivo / Uso:", p.getMotivo(), bold, regular);
            doc.add(detTable);

            if (vencido) {
                doc.add(new Paragraph(
                    "⚠ Este préstamo está vencido — la fecha de devolución fue "
                    + p.getFechaDevolucionPrevista().format(FMT))
                    .setFont(bold).setFontSize(8).setFontColor(COLOR_WARN)
                    .setBackgroundColor(new DeviceRgb(254, 243, 199))
                    .setPadding(8).setMarginTop(10));
            }

            // ── Firmas ──
            doc.add(new Paragraph("\n\n"));
            Table firmas = new Table(UnitValue.createPercentArray(new float[]{1, 0.4f, 1}))
                .useAllAvailableWidth().setMarginTop(20);
            addFirmaCol(firmas, bold, regular, "ENTREGA / DESPACHO", orgName());
            firmas.addCell(new com.itextpdf.layout.element.Cell().setBorder(null));
            addFirmaCol(firmas, bold, regular, "RECIBE / RESPONSABLE",
                p.getResponsableNombre()
                + (p.getResponsableCargo() != null ? "\n" + p.getResponsableCargo() : ""));
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
            .setBorderBottom(new SolidBorder(new DeviceRgb(220, 252, 231), 0.5f))
            .setBorderTop(null).setBorderLeft(null).setBorderRight(null));
        t.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(value != null ? value : "—").setFont(regular).setFontSize(9))
            .setPadding(5)
            .setBorderBottom(new SolidBorder(new DeviceRgb(226, 232, 240), 0.5f))
            .setBorderTop(null).setBorderLeft(null).setBorderRight(null));
    }

    private void addFirmaCol(Table t, PdfFont bold, PdfFont regular, String titulo, String nombre) {
        com.itextpdf.layout.element.Cell c = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph("\n\n\n")
                .setBorderBottom(new SolidBorder(new DeviceRgb(15, 23, 42), 1)))
            .add(new Paragraph(titulo).setFont(bold).setFontSize(8)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(4))
            .add(new Paragraph(nombre).setFont(regular).setFontSize(7)
                .setFontColor(COLOR_MUTED).setTextAlignment(TextAlignment.CENTER))
            .setBorder(null);
        t.addCell(c);
    }

    private String orDash(String v) { return (v != null && !v.isBlank()) ? v : "—"; }
}
