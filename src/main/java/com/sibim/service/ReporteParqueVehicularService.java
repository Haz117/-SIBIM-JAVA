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
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.model.Producto;

import java.io.File;
import java.util.List;

/** Genera el formato oficial V.6 INVENTARIO DE PARQUE VEHICULAR.
 *  Produce una tarjeta por vehículo (una página) con los datos del bien
 *  y una lista de verificación de componentes para llenar a mano. */
public class ReporteParqueVehicularService extends ReporteService {

    private static final String[] COMPONENTES = {
        "Gato Hidráulico", "Herramientas", "Llanta de Refacción",
        "Espejo Retrovisor Lateral Izquierdo", "Espejo Retrovisor Lateral Derecho",
        "Torreta", "Sirena", "Espejo Retrovisor Interno",
        "Cable pasa Corriente", "Llave de Cruz", "Llantas", "Luces"
    };

    public ReporteParqueVehicularService() { super(); }

    public File exportParqueVehicularPdf(List<Producto> vehiculos) throws Exception {
        if (vehiculos.isEmpty()) return null;
        File file = tempFile("parque_vehicular", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.A4)) {
            doc.setMargins(32, 36, 32, 36);
            for (int i = 0; i < vehiculos.size(); i++) {
                if (i > 0) doc.add(new com.itextpdf.layout.element.AreaBreak());
                addVehicleCard(doc, vehiculos.get(i), pdfDoc);
            }
        }
        return file;
    }

    private void addVehicleCard(Document doc, Producto p, PdfDocument pdfDoc) throws Exception {
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb muted   = new DeviceRgb(107, 114, 128);
        DeviceRgb labelBg = new DeviceRgb(252, 240, 241);

        // ── Encabezado del formulario ──────────────────────────────────
        Image logoImg = loadHeaderLogo();
        float[] hwCols = logoImg != null ? new float[]{1f, 5f} : new float[]{1f};
        Table headerRow = new Table(hwCols).useAllAvailableWidth().setMarginBottom(4);
        if (logoImg != null) {
            headerRow.addCell(new Cell()
                .add(logoImg.setAutoScale(true).setMaxHeight(50).setMaxWidth(60)
                    .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER))
                .setBorder(Border.NO_BORDER).setPadding(4)
                .setVerticalAlignment(VerticalAlignment.MIDDLE));
        }
        Cell titleCell = new Cell()
            .add(new Paragraph("ESTADOS E INFORMACIÓN FINANCIERA")
                .setFont(bold).setFontSize(8f).setFontColor(muted)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2))
            .add(new Paragraph("V.6   INVENTARIO DE PARQUE VEHICULAR")
                .setFont(bold).setFontSize(13f).setFontColor(COLOR_HEADER)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2))
            .add(new Paragraph(orgName())
                .setFont(reg).setFontSize(8f).setFontColor(muted)
                .setTextAlignment(TextAlignment.CENTER))
            .setBorder(Border.NO_BORDER).setPadding(6);
        headerRow.addCell(titleCell);
        doc.add(headerRow);

        // Barra de acento
        doc.add(new Table(new float[]{1f}).useAllAvailableWidth()
            .addCell(new Cell().setHeight(3f)
                .setBackgroundColor(new DeviceRgb(196, 165, 93))
                .setBorder(Border.NO_BORDER)));
        doc.add(spacer(6));

        // ── Tabla de datos del vehículo (4 columnas: etiqueta | valor | etiqueta | valor) ──
        Table dataTable = new Table(new float[]{1.3f, 1.7f, 1.5f, 1.5f})
            .useAllAvailableWidth().setMarginBottom(10);

        addVRow(dataTable, "Marca:",               v(p.getMarca()),               "Unidad Administrativa:", v(p.getArea()),                  bold, reg, muted, labelBg);
        addVRow(dataTable, "Tipo:",                v(p.getTipoBien()),             "Factura:",               v(p.getNumeroFactura()),          bold, reg, muted, labelBg);
        addVRow(dataTable, "Modelo:",              v(p.getModelo()),               "No. Tarjeta Circulación:", v(p.getNoTarjetaCirculacion()), bold, reg, muted, labelBg);
        addVRow(dataTable, "No. De Serie:",        v(p.getNumeroSerie()),          "No. Póliza de Seguro:", v(p.getNoPolizaSeguro()),          bold, reg, muted, labelBg);
        addVRow(dataTable, "No. De Motor:",        v(p.getNoMotor()),              "Condiciones del Bien:", v(p.getEstadoFisico()),            bold, reg, muted, labelBg);
        addVRow(dataTable, "Color:",               v(p.getColor()),                "No. De Resguardo:",     "_______________",                 bold, reg, muted, labelBg);
        addVRow(dataTable, "No. De Inventario:",   v(p.getCodigo()),               "", "",                                                    bold, reg, muted, labelBg);
        addVRow(dataTable, "Clave Armonizada:",    v(p.getClaveArmonizada()),      "", "",                                                    bold, reg, muted, labelBg);
        doc.add(dataTable);

        // ── Fotografía + checklist de componentes (tabla de 2 columnas) ──
        Table bottomSection = new Table(new float[]{1.4f, 1f}).useAllAvailableWidth();

        // Foto (o área en blanco)
        Cell fotoCell = buildFotoCell(p, reg);
        bottomSection.addCell(fotoCell);

        // Lista de componentes
        Table checkTable = new Table(new float[]{3f, 1f}).useAllAvailableWidth();
        checkTable.addCell(new Cell(1, 2)
            .add(new Paragraph("COMPONENTES DEL VEHÍCULO")
                .setFont(bold).setFontSize(7.5f).setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(COLOR_HEADER).setPadding(5)
            .setBorder(Border.NO_BORDER));
        checkTable.addCell(new Cell()
            .add(new Paragraph("Componente").setFont(bold).setFontSize(7f).setFontColor(muted))
            .setBackgroundColor(labelBg).setPadding(3)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER).setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f)));
        checkTable.addCell(new Cell()
            .add(new Paragraph("Sí / No").setFont(bold).setFontSize(7f).setFontColor(muted)
                .setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(labelBg).setPadding(3)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER).setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f)));

        for (String comp : COMPONENTES) {
            checkTable.addCell(new Cell()
                .add(new Paragraph(comp).setFont(reg).setFontSize(7.5f))
                .setPadding(4)
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f)));
            checkTable.addCell(new Cell()
                .add(new Paragraph("_____").setFont(reg).setFontSize(7.5f)
                    .setTextAlignment(TextAlignment.CENTER))
                .setPadding(4)
                .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_LIGHT, 0.4f)));
        }

        bottomSection.addCell(new Cell()
            .add(checkTable).setPadding(0).setPaddingLeft(10).setBorder(Border.NO_BORDER));
        doc.add(bottomSection);

        // ── Firmas ────────────────────────────────────────────────────
        addFirmasBlock(doc,
            new String[]{"ELABORÓ", getCurrentUserName(), "Director de Recursos Materiales"},
            new String[]{"RECIBIÓ", "_______________", "Resguardante / Titular"},
            new String[]{"VO.BO.", "_______________", "Secretario General Municipal"});
    }

    private Cell buildFotoCell(Producto p, PdfFont reg) {
        if (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) {
            try {
                Image img = new Image(ImageDataFactory.create(p.getFotoUrl()))
                    .setAutoScale(true).setMaxWidth(220).setMaxHeight(160);
                return new Cell()
                    .add(img.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER))
                    .setPadding(6).setMinHeight(170)
                    .setBorder(new SolidBorder(BORDER_LIGHT, 0.8f))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
            } catch (Exception ignored) {}
        }
        return new Cell()
            .add(new Paragraph("\n\n\n\n\n[ FOTOGRAFÍA DEL VEHÍCULO ]")
                .setFont(reg).setFontSize(9f)
                .setFontColor(new DeviceRgb(200, 200, 200))
                .setTextAlignment(TextAlignment.CENTER))
            .setMinHeight(170).setPadding(6)
            .setBorder(new SolidBorder(BORDER_LIGHT, 0.8f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private void addVRow(Table t,
            String lbl1, String val1, String lbl2, String val2,
            PdfFont bold, PdfFont reg, DeviceRgb muted, DeviceRgb labelBg) {
        SolidBorder bottom = new SolidBorder(BORDER_LIGHT, 0.4f);
        t.addCell(new Cell()
            .add(new Paragraph(lbl1).setFont(bold).setFontSize(7.5f).setFontColor(muted))
            .setBackgroundColor(labelBg).setPadding(4)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER).setBorderBottom(bottom));
        t.addCell(new Cell()
            .add(new Paragraph(val1 != null ? val1 : "").setFont(reg).setFontSize(8f))
            .setPadding(4)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER).setBorderBottom(bottom));
        t.addCell(new Cell()
            .add(new Paragraph(lbl2).setFont(bold).setFontSize(7.5f).setFontColor(muted))
            .setBackgroundColor(labelBg).setPadding(4)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER).setBorderBottom(bottom));
        t.addCell(new Cell()
            .add(new Paragraph(val2 != null ? val2 : "").setFont(reg).setFontSize(8f))
            .setPadding(4)
            .setBorderTop(Border.NO_BORDER).setBorderLeft(Border.NO_BORDER)
            .setBorderRight(Border.NO_BORDER).setBorderBottom(bottom));
    }

    private static String v(String s) {
        return (s != null && !s.isBlank()) ? s : "_______________";
    }
}
