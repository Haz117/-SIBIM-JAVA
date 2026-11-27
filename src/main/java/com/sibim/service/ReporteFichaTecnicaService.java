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
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ficha técnica of one bien: its photo (and gallery) up front next to the
 * data that identifies it, then its characteristics, acquisition and value,
 * observations, recent history and signatures. Same guinda/dorado palette as
 * the official formatos.
 */
public class ReporteFichaTecnicaService extends ReporteService {

    private static final DeviceRgb GUINDA    = COLOR_HEADER;
    private static final DeviceRgb GUINDA_CL = new DeviceRgb(240, 195, 195);
    private static final DeviceRgb DORADO    = new DeviceRgb(196, 165, 93);
    private static final DeviceRgb OSCURO    = new DeviceRgb(17, 24, 39);
    private static final DeviceRgb TEXTO     = new DeviceRgb(55, 65, 81);
    private static final DeviceRgb TENUE     = new DeviceRgb(107, 114, 128);
    private static final DeviceRgb FONDO     = new DeviceRgb(243, 244, 246);
    private static final DeviceRgb LINEA     = new DeviceRgb(209, 213, 219);
    private static final DeviceRgb VERDE     = new DeviceRgb(21, 128, 61);
    private static final DeviceRgb AMBAR     = new DeviceRgb(180, 83, 9);
    private static final DeviceRgb ROJO      = new DeviceRgb(185, 28, 28);

    private static final int MOVIMIENTOS_MAX = 6;

    public ReporteFichaTecnicaService() { super(); }

    /** Width of the ficha a QR downloads: a phone's screen, so it reads without zooming. */
    static final float ANCHO_TELEFONO = 360f;

    /** Tallest it starts; the page is then cut where its content ends. */
    private static final float ALTO_INICIAL = 3000f;

    /**
     * The ficha that the bien's QR downloads ({@code enlace} is where it will live), made for the
     * phone that scanned the label (27-nov-2026: it was the letter-size ficha without amounts, read
     * at a tenth of its size on a phone, with a QR of its own to scan again and «—» in every empty
     * field). One column the width of a phone, the photo large, what tells where the bien is and who
     * holds it first, only the data it has, and whom to tell if it isn't where it should be. Anyone
     * who scans the label can open it: no amounts, no history, no signatures.
     */
    File exportFichaPublica(Producto p, String enlace) throws Exception {
        String safeName = p.getCodigo() != null ? p.getCodigo().replaceAll("[^a-zA-Z0-9_\\-]", "_") : "bien";
        File file = tempFile("ficha_qr_" + safeName, ".pdf");
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        List<ImageData> fotos = FotosPdf.de(p, 4, 1000);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, new PageSize(ANCHO_TELEFONO, ALTO_INICIAL))) {
            doc.setMargins(0, 0, 0, 0);
            doc.add(encabezadoTelefono(bold, reg));
            doc.add(barra(DORADO, 3));

            com.itextpdf.layout.element.Div cuerpo = new com.itextpdf.layout.element.Div()
                .setMarginLeft(18).setMarginRight(18).setMarginTop(14);
            String numero = ReporteParqueVehicularService.numeroDeInventario(p);
            cuerpo.add(etiqueta("NÚMERO DE INVENTARIO", bold));
            cuerpo.add(new Paragraph(numero != null ? numero : "—").setFont(bold).setFontSize(26f)
                .setFontColor(GUINDA).setMargin(0).setMultipliedLeading(1f));
            cuerpo.add(new Paragraph(recortar(mayus(p.getNombre()), 170)).setFont(bold).setFontSize(12.5f)
                .setFontColor(OSCURO).setMultipliedLeading(1.2f).setMargin(0).setMarginTop(4));
            Table chips = new Table(hay(p.getEstadoFisico()) ? 2 : 1).setMarginTop(8);
            chips.addCell(chip(p.isDadoDeBaja() ? "DADO DE BAJA" : "ACTIVO", p.isDadoDeBaja() ? ROJO : VERDE, bold));
            if (hay(p.getEstadoFisico()))
                chips.addCell(chip("ESTADO: " + p.getEstadoFisico().toUpperCase(), colorEstadoFisico(p.getEstadoFisico()), bold));
            cuerpo.add(chips);
            cuerpo.add(fotosTelefono(fotos, bold, reg));

            String[] depRes = ReporteEtiquetasService.departamentoYResguardo(p.getArea());
            List<String[]> donde = new ArrayList<>();
            donde.add(par("Área", depRes[1]));
            if (!depRes[0].equalsIgnoreCase(depRes[1])) donde.add(par("Depende de", depRes[0]));
            donde.add(par("Resguardante", p.getResguardante()));
            donde.add(par("Ubicación", p.getUbicacion()));
            seccionTelefono(cuerpo, "DÓNDE ESTÁ Y QUIÉN LO TIENE", donde, 1, bold, reg);

            List<String[]> datos = new ArrayList<>(List.of(
                par("Marca", p.getMarca()), par("Modelo", p.getModelo()),
                par("No. de serie", p.getNumeroSerie()), par("Color", p.getColor()),
                par("Categoría", p.getCategoriaNombre()), par("Tipo de bien", p.getTipoBien()),
                par("No. de motor", p.getNoMotor()), par("Tarjeta de circulación", p.getNoTarjetaCirculacion()),
                par("Póliza de seguro", p.getNoPolizaSeguro())));
            if (p.getStockActual() > 1) datos.add(par("Cantidad", FormatUtils.plural(p.getStockActual(), "pieza", "piezas")));
            seccionTelefono(cuerpo, "DATOS DEL BIEN", datos, 2, bold, reg);

            List<String[]> compra = new ArrayList<>(List.of(
                par("Adquirido", p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : null),
                par("Factura", p.getNumeroFactura()),
                par("Proveedor", p.getProveedor()),
                par("Garantía hasta", p.getFechaVencimiento() != null ? FormatUtils.formatDate(p.getFechaVencimiento()) : null),
                par("Próxima revisión", p.getProximaRevision() != null ? FormatUtils.formatDate(p.getProximaRevision()) : null)));
            if (p.isDadoDeBaja()) {
                compra.add(par("Fecha de baja", p.getFechaBaja() != null ? FormatUtils.formatDate(p.getFechaBaja()) : null));
                compra.add(par("Motivo de baja", p.getMotivoBaja()));
            }
            seccionTelefono(cuerpo, "ADQUISICIÓN", compra, 2, bold, reg);

            String obs = observaciones(p);
            if (!obs.isBlank()) {
                cuerpo.add(tituloTelefono("DESCRIPCIÓN", bold));
                cuerpo.add(new Paragraph(recortar(obs, 700)).setFont(reg).setFontSize(10f).setFontColor(TEXTO)
                    .setMultipliedLeading(1.3f).setMargin(0));
            }

            // Whom to tell: whoever finds the bien elsewhere or broken has the label in front of them.
            String responsable = config("responsable", ""), correo = config("correo_contacto", "");
            if (hay(responsable) || hay(correo)) {
                Cell aviso = new Cell().setBackgroundColor(FONDO).setBorder(Border.NO_BORDER).setPadding(10)
                    .add(new Paragraph("¿No está donde dice, o está dañado?").setFont(bold).setFontSize(10f)
                        .setFontColor(OSCURO).setMargin(0))
                    .add(new Paragraph("Avisa a " + (hay(responsable) ? responsable.trim() : "Patrimonio")
                            + (hay(correo) ? ": " + correo.trim() : ""))
                        .setFont(reg).setFontSize(9.5f).setFontColor(TEXTO).setMargin(0).setMarginTop(2));
                cuerpo.add(new Table(1).useAllAvailableWidth().setMarginTop(16).addCell(aviso));
            }
            cuerpo.add(new Paragraph("Datos al " + FormatUtils.formatDateTime(LocalDateTime.now()) + " · SIBIM · " + orgName())
                .setFont(reg).setFontSize(7.5f).setFontColor(TENUE).setTextAlignment(TextAlignment.CENTER)
                .setMargin(0).setMarginTop(12));
            doc.add(cuerpo);

            // One page as long as what it says: cut where the content ended.
            float fin = doc.getRenderer().getCurrentArea().getBBox().getTop();
            float desde = Math.max(0, fin - 18);
            pdfDoc.getPage(pdfDoc.getNumberOfPages())
                .setMediaBox(new com.itextpdf.kernel.geom.Rectangle(0, desde, ANCHO_TELEFONO, ALTO_INICIAL - desde));
        }
        return file;
    }

    private Table encabezadoTelefono(PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{22, 78})).useAllAvailableWidth();
        Cell logo = celda().setBackgroundColor(GUINDA).setPadding(12).setPaddingRight(0)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        Image img = loadHeaderLogo();
        if (img != null)
            logo.add(new Table(1).useAllAvailableWidth().addCell(new Cell()
                .add(img.setMaxHeight(38).setMaxWidth(56).setHorizontalAlignment(HorizontalAlignment.CENTER))
                .setBackgroundColor(ColorConstants.WHITE).setPadding(3).setBorder(Border.NO_BORDER)));
        t.addCell(logo);
        t.addCell(celda().setBackgroundColor(GUINDA).setPadding(12).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .add(new Paragraph(orgName()).setFont(bold).setFontSize(10.5f).setFontColor(ColorConstants.WHITE)
                .setMultipliedLeading(1.15f).setMargin(0))
            .add(new Paragraph("Ficha del bien · Patrimonio municipal").setFont(reg).setFontSize(8.5f)
                .setFontColor(GUINDA_CL).setMargin(0).setMarginTop(2)));
        return t;
    }

    /** The principal photo the width of the screen, and the others small below it. */
    private static Table fotosTelefono(List<ImageData> fotos, PdfFont bold, PdfFont reg) {
        Table t = new Table(1).useAllAvailableWidth().setMarginTop(12);
        Cell marco = new Cell().setPadding(4).setBackgroundColor(FONDO).setBorder(new SolidBorder(LINEA, 0.6f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (fotos.isEmpty()) {
            marco.setHeight(70).add(new Paragraph("Sin fotografía").setFont(bold).setFontSize(10f).setFontColor(TENUE)
                .setTextAlignment(TextAlignment.CENTER).setMargin(0));
        } else {
            marco.add(new Image(fotos.get(0)).scaleToFit(ANCHO_TELEFONO - 46, 320)
                .setHorizontalAlignment(HorizontalAlignment.CENTER));
        }
        t.addCell(marco);
        if (fotos.size() > 1) {
            Table otras = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth().setMarginTop(4);
            for (int i = 1; i < 4; i++) {
                // Only the photos it has: an empty gray box read as a photo missing.
                Cell c = new Cell().setHeight(78).setPadding(2).setBorder(Border.NO_BORDER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
                if (i < fotos.size())
                    c.setBackgroundColor(FONDO).setBorder(new SolidBorder(LINEA, 0.6f))
                        .add(new Image(fotos.get(i)).scaleToFit(100, 72).setHorizontalAlignment(HorizontalAlignment.CENTER));
                otras.addCell(c);
            }
            t.addCell(new Cell().setBorder(Border.NO_BORDER).setPadding(0).add(otras));
        }
        return t;
    }

    private static Paragraph tituloTelefono(String texto, PdfFont bold) {
        return new Paragraph(texto).setFont(bold).setFontSize(9f).setFontColor(GUINDA).setCharacterSpacing(0.4f)
            .setMargin(0).setMarginTop(16).setPaddingBottom(4)
            .setBorderBottom(new SolidBorder(DORADO, 1.2f));
    }

    /** A section with only the data the bien has (a «—» per empty field said nothing on a phone),
     *  each one its label above its value; nothing at all when none is captured. */
    private static void seccionTelefono(com.itextpdf.layout.element.Div cuerpo, String titulo, List<String[]> pares,
                                        int columnas, PdfFont bold, PdfFont reg) {
        List<String[]> con = pares.stream().filter(x -> hay(x[1])).toList();
        if (con.isEmpty()) return;
        cuerpo.add(tituloTelefono(titulo, bold));
        Table t = new Table(columnas).useAllAvailableWidth();
        // Two short data side by side; a long one (an área, a proveedor), or a short one without a
        // short one after it, takes the whole row, so no row is left half empty.
        for (int i = 0; i < con.size(); i++) {
            boolean juntos = columnas > 1 && !largo(con.get(i)) && i + 1 < con.size() && !largo(con.get(i + 1));
            if (juntos) {
                t.addCell(datoTelefono(con.get(i), 1, bold, reg));
                t.addCell(datoTelefono(con.get(++i), 1, bold, reg));
            } else {
                t.addCell(datoTelefono(con.get(i), columnas, bold, reg));
            }
        }
        cuerpo.add(t);
    }

    private static boolean largo(String[] dato) {
        return dato[1].trim().length() > 22;
    }

    private static Cell datoTelefono(String[] x, int columnas, PdfFont bold, PdfFont reg) {
        return new Cell(1, columnas).setBorder(Border.NO_BORDER)
            .setBorderBottom(new SolidBorder(LINEA, 0.6f)).setPadding(0).setPaddingTop(6).setPaddingBottom(6)
            .setPaddingRight(8)
            .add(new Paragraph(x[0].toUpperCase()).setFont(bold).setFontSize(7.5f).setFontColor(TENUE).setMargin(0))
            .add(new Paragraph(x[1].trim()).setFont(reg).setFontSize(11.5f).setFontColor(OSCURO)
                .setMultipliedLeading(1.2f).setMargin(0).setMarginTop(1));
    }

    private static String recortar(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }

    private String pesos(BigDecimal valor) {
        return com.sibim.session.Permisos.pesos(valor);
    }

    public File exportFichaTecnica(Producto p, List<Movimiento> movimientos) throws Exception {
        String safeName = p.getCodigo() != null ? p.getCodigo().replaceAll("[^a-zA-Z0-9_\\-]", "_") : "bien";
        File file = tempFile("ficha_" + safeName, ".pdf");

        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        List<ImageData> fotos = FotosPdf.de(p, 4, 1000);

        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document doc = new Document(pdfDoc, PageSize.LETTER)) {

            doc.setMargins(24, 30, 24, 30);
            doc.add(encabezado(bold, reg));
            doc.add(barra(DORADO, 3).setMarginBottom(8));
            doc.add(bloquePrincipal(p, fotos, bold, reg));

            doc.add(titulo("DATOS DEL BIEN", bold));
            List<String[]> datos = new ArrayList<>(List.of(
                par("Marca", p.getMarca()), par("Modelo", p.getModelo()),
                par("No. de serie", p.getNumeroSerie()), par("Color", p.getColor()),
                par("Clave armonizada", p.getClaveArmonizada()), par("Tipo de bien", p.getTipoBien())));
            if (hay(p.getNoMotor()) || hay(p.getNoTarjetaCirculacion()) || hay(p.getNoPolizaSeguro())) {
                datos.add(par("No. de motor", p.getNoMotor()));
                datos.add(par("Tarjeta de circulación", p.getNoTarjetaCirculacion()));
                datos.add(par("Póliza de seguro", p.getNoPolizaSeguro()));
            }
            doc.add(cuadricula(datos, bold, reg));

            doc.add(titulo("ADQUISICIÓN Y VALOR", bold));
            doc.add(cuadricula(adquisicion(p), bold, reg));

            String obs = observaciones(p);
            if (!obs.isBlank()) {
                doc.add(titulo("DESCRIPCIÓN Y OBSERVACIONES", bold));
                doc.add(new Paragraph(obs).setFont(reg).setFontSize(8.5f).setFontColor(TEXTO)
                    .setMultipliedLeading(1.2f).setMargin(0).setMarginBottom(8).setPaddingLeft(2));
            }

            if (movimientos != null && !movimientos.isEmpty())
                doc.add(historial(movimientos, bold, reg));

            doc.add(firmas(p, bold, reg));

            // Pie fijo en el margen inferior de cada hoja
            String pie = "SIBIM · " + orgName() + " · Emitido el " + FormatUtils.formatDateTime(LocalDateTime.now());
            int paginas = pdfDoc.getNumberOfPages();
            float centro = PageSize.LETTER.getWidth() / 2;
            for (int i = 1; i <= paginas; i++) {
                String texto = paginas > 1 ? pie + " · Hoja " + i + " de " + paginas : pie;
                doc.showTextAligned(new Paragraph(texto).setFont(reg).setFontSize(6.5f).setFontColor(TENUE),
                    centro, 11, i, TextAlignment.CENTER, VerticalAlignment.BOTTOM, 0);
            }
        }
        return file;
    }

    // ── Encabezado ───────────────────────────────────────────────────────────

    private Table encabezado(PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{13, 62, 25})).useAllAvailableWidth();
        Cell logo = celda().setBackgroundColor(GUINDA).setPadding(7);
        Image img = loadHeaderLogo();
        if (img != null) {
            logo.add(new Table(1).useAllAvailableWidth().addCell(new Cell()
                .add(img.setMaxHeight(42).setMaxWidth(52).setHorizontalAlignment(HorizontalAlignment.CENTER))
                .setBackgroundColor(ColorConstants.WHITE).setPadding(3).setBorder(Border.NO_BORDER)));
        }
        t.addCell(logo.setVerticalAlignment(VerticalAlignment.MIDDLE));
        t.addCell(celda().setBackgroundColor(GUINDA).setPadding(8).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .add(new Paragraph(orgName()).setFont(bold).setFontSize(9f).setFontColor(ColorConstants.WHITE).setMargin(0))
            .add(new Paragraph("FICHA TÉCNICA DEL BIEN MUEBLE").setFont(bold).setFontSize(15f)
                .setFontColor(ColorConstants.WHITE).setMargin(0).setMarginTop(2))
            .add(new Paragraph("Inventario " + config("periodo_inventario", "2024 - 2027")
                    + "  ·  Sistema Integral de Bienes Municipales")
                .setFont(reg).setFontSize(7.5f).setFontColor(GUINDA_CL).setMargin(0).setMarginTop(2)));
        t.addCell(celda().setBackgroundColor(GUINDA).setPadding(10).setVerticalAlignment(VerticalAlignment.MIDDLE)
            .setTextAlignment(TextAlignment.RIGHT)
            .add(new Paragraph("FECHA DE EMISIÓN").setFont(bold).setFontSize(6.5f).setFontColor(GUINDA_CL).setMargin(0))
            .add(new Paragraph(LocalDate.now().format(FMT)).setFont(bold).setFontSize(10f)
                .setFontColor(ColorConstants.WHITE).setMargin(0)));
        return t;
    }

    // ── Foto + identificación ────────────────────────────────────────────────

    private Table bloquePrincipal(Producto p, List<ImageData> fotos, PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth()
            .setMarginBottom(8);

        // Izquierda: foto principal y galería
        Cell izq = celda().setPaddingRight(8);
        Cell marco = new Cell().setHeight(190).setPadding(6).setBackgroundColor(FONDO)
            .setBorder(new SolidBorder(LINEA, 0.8f)).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (!fotos.isEmpty()) {
            marco.add(new Image(fotos.get(0)).scaleToFit(245, 178).setHorizontalAlignment(HorizontalAlignment.CENTER));
        } else {
            marco.add(new Paragraph("SIN FOTOGRAFÍA REGISTRADA").setFont(bold).setFontSize(9f).setFontColor(TENUE)
                .setTextAlignment(TextAlignment.CENTER).setMargin(0))
                .add(new Paragraph("Agrega una foto desde Editar bien para que aparezca aquí")
                    .setFont(reg).setFontSize(7.5f).setFontColor(TENUE).setTextAlignment(TextAlignment.CENTER));
        }
        izq.add(new Table(1).useAllAvailableWidth().addCell(marco));
        if (fotos.size() > 1) {
            Table galeria = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth()
                .setMarginTop(4);
            for (int i = 1; i < 4; i++) {
                Cell c = new Cell().setHeight(50).setPadding(2).setBackgroundColor(FONDO)
                    .setBorder(new SolidBorder(LINEA, 0.6f)).setVerticalAlignment(VerticalAlignment.MIDDLE);
                if (i < fotos.size())
                    c.add(new Image(fotos.get(i)).scaleToFit(76, 44).setHorizontalAlignment(HorizontalAlignment.CENTER));
                galeria.addCell(c);
            }
            izq.add(galeria);
        }
        t.addCell(izq);

        // Derecha: número de inventario, descripción, estado y ubicación
        Cell der = celda().setPaddingLeft(8);
        der.add(etiqueta("NO. DE INVENTARIO", bold));
        // A vehicle: its old number, not SIBIM's (ReporteParqueVehicularService.numeroDeInventario).
        String numero = ReporteParqueVehicularService.numeroDeInventario(p);
        der.add(new Paragraph(numero != null ? numero : "—").setFont(bold).setFontSize(21f)
            .setFontColor(GUINDA).setMargin(0));
        der.add(new Paragraph(mayus(p.getNombre())).setFont(bold).setFontSize(11f).setFontColor(OSCURO)
            .setMultipliedLeading(1.15f).setMargin(0).setMarginTop(2));

        // As many columns as chips: an incomplete row breaks the table's bottom border.
        Table chips = new Table(hay(p.getEstadoFisico()) ? 2 : 1).setMarginTop(6).setMarginBottom(6);
        chips.addCell(chip(p.isDadoDeBaja() ? "DADO DE BAJA" : "ACTIVO", p.isDadoDeBaja() ? ROJO : VERDE, bold));
        if (hay(p.getEstadoFisico()))
            chips.addCell(chip("ESTADO: " + p.getEstadoFisico().toUpperCase(), colorEstadoFisico(p.getEstadoFisico()), bold));
        der.add(chips);

        String[] depRes = ReporteEtiquetasService.departamentoYResguardo(p.getArea());
        Table datos = new Table(UnitValue.createPercentArray(new float[]{36, 64})).useAllAvailableWidth();
        filaDato(datos, "Departamento", depRes[0], bold, reg);
        filaDato(datos, "Área (resguardo)", depRes[1], bold, reg);
        filaDato(datos, "Resguardante", mayus(p.getResguardante()), bold, reg);
        filaDato(datos, "Ubicación", mayus(p.getUbicacion()), bold, reg);
        filaDato(datos, "Categoría", mayus(p.getCategoriaNombre()), bold, reg);
        der.add(datos);

        byte[] qr = ReporteEtiquetasService.qrToPngBytes(com.sibim.util.QrUtils.contenidoBien(p), 180);
        if (qr != null) {
            Table qrT = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth()
                .setMarginTop(8);
            qrT.addCell(celda().add(new Image(ImageDataFactory.create(qr)).scaleToFit(66, 66)));
            qrT.addCell(celda().setVerticalAlignment(VerticalAlignment.MIDDLE)
                .add(new Paragraph("Código QR del bien").setFont(bold).setFontSize(8f).setFontColor(TEXTO).setMargin(0))
                .add(new Paragraph("Escanéalo para identificar este bien en SIBIM durante el levantamiento físico.")
                    .setFont(reg).setFontSize(7f).setFontColor(TENUE).setMargin(0)));
            der.add(qrT);
        }
        t.addCell(der);
        return t;
    }

    // ── Secciones ────────────────────────────────────────────────────────────

    private List<String[]> adquisicion(Producto p) {
        List<String[]> l = new ArrayList<>();
        l.add(par("Fecha de adquisición", p.getFechaAdquisicion() != null ? FormatUtils.formatDate(p.getFechaAdquisicion()) : null));
        l.add(par("No. de factura", p.getNumeroFactura()));
        l.add(par("Folio fiscal (UUID)", p.getFolioFiscal()));
        l.add(par("Proveedor", p.getProveedor()));
        // A lot: the piece's cost, how many, and the book value of all of them —
        // it used to mix one piece's depreciated value with the lot's total.
        boolean lote = p.getStockActual() > 1;
        if (lote) l.add(par("Cantidad", FormatUtils.plural(p.getStockActual(), "pieza", "piezas")));
        // A costo of 0 is one not captured (1,080 bienes on 18-nov-2026): «—», not «$0.00».
        boolean conCosto = p.getPrecioCompra() != null && p.getPrecioCompra().signum() > 0;
        l.add(par(lote ? "Costo de adquisición por pieza" : "Costo de adquisición",
            conCosto ? pesos(p.getPrecioCompra()) : null));
        BigDecimal enLibros = !conCosto ? null
            : p.getValorDepreciadoTotal() != null ? p.getValorDepreciadoTotal() : p.getValorTotal();
        l.add(par("Vida útil", p.getVidaUtilAnios() != null ? FormatUtils.plural(p.getVidaUtilAnios(), "año", "años") : null));
        l.add(par("Valor en libros", enLibros != null ? pesos(enLibros) : null));
        if (conCosto && p.getPorcentajeDepreciado() != null && p.getValorDepreciadoTotal() != null)
            l.add(par("Depreciación acumulada", pesos(p.getValorTotal().subtract(p.getValorDepreciadoTotal()))
                + "  (" + p.getPorcentajeDepreciado() + "%)"));
        if (p.getCreadoEn() != null)
            l.add(par("Alta en SIBIM", FormatUtils.formatDate(p.getCreadoEn().toLocalDate())));
        if (p.isDadoDeBaja()) {
            l.add(par("Fecha de baja", p.getFechaBaja() != null ? FormatUtils.formatDate(p.getFechaBaja()) : null));
            l.add(par("Motivo de baja", p.getMotivoBaja()));
        }
        return l;
    }

    private static String observaciones(Producto p) {
        StringBuilder sb = new StringBuilder();
        if (hay(p.getDescripcion())) sb.append(p.getDescripcion().trim());
        if (hay(p.getNotasMantenimiento())) {
            if (!sb.isEmpty()) sb.append("\n");
            sb.append("Mantenimiento: ").append(p.getNotasMantenimiento().trim());
        }
        return sb.toString();
    }

    private Table historial(List<Movimiento> movimientos, PdfFont bold, PdfFont reg) {
        int n = Math.min(movimientos.size(), MOVIMIENTOS_MAX);
        Table wrap = new Table(1).useAllAvailableWidth().setKeepTogether(true);
        wrap.addCell(celda().add(titulo("HISTORIAL RECIENTE (" + n + " de " + movimientos.size() + ")", bold)));
        Table t = new Table(UnitValue.createPercentArray(new float[]{17, 15, 48, 20})).useAllAvailableWidth();
        for (String h : new String[]{"Fecha", "Movimiento", "Detalle", "Registró"})
            t.addHeaderCell(new Cell().add(new Paragraph(h).setFont(bold).setFontSize(7.5f).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(GUINDA).setPadding(4).setBorder(Border.NO_BORDER));
        for (int i = 0; i < n; i++) {
            Movimiento m = movimientos.get(i);
            DeviceRgb bg = i % 2 == 0 ? new DeviceRgb(255, 255, 255) : FONDO;
            t.addCell(celdaMov(FormatUtils.formatDateTime(m.getCreadoEn()), reg, bg));
            t.addCell(celdaMov(m.getTipo() != null ? m.getTipo().getEtiqueta() : "—", reg, bg));
            t.addCell(celdaMov(detalle(m), reg, bg));
            t.addCell(celdaMov(m.getUsuarioNombre(), reg, bg));
        }
        wrap.addCell(celda().add(t));
        return wrap;
    }

    /** What a movement did to the bien, in words: where it went, its new código, why. */
    static String detalle(Movimiento m) {
        List<String> partes = new ArrayList<>();
        if (hay(m.getAreaOrigen()) || hay(m.getAreaDestino()))
            partes.add((hay(m.getAreaOrigen()) ? m.getAreaOrigen() : "—") + " » "
                + (hay(m.getAreaDestino()) ? m.getAreaDestino() : "—"));
        if (hay(m.getCodigoAnterior()) && hay(m.getCodigoNuevo()) && !m.getCodigoAnterior().equals(m.getCodigoNuevo()))
            partes.add("código " + m.getCodigoAnterior() + " » " + m.getCodigoNuevo());
        if (hay(m.getMotivo())) partes.add(m.getMotivo().trim());
        if (hay(m.getReferencia())) partes.add("Ref. " + m.getReferencia().trim());
        return partes.isEmpty() ? "—" : String.join(" · ", partes);
    }

    private Table firmas(Producto p, PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1})).useAllAvailableWidth()
            .setMarginTop(14).setKeepTogether(true);
        // {title, name or null, cargo}: the name first and the cargo under it.
        String[][] firmas = {
            {"RECIBÍ DE CONFORMIDAD", hay(p.getResguardante()) ? p.getResguardante().toUpperCase() : null, "Resguardante"},
            {"ELABORÓ", firmaElabora(), "Recursos Materiales y Patrimonio"},
            {"VO. BO.", firmaVoBo(), "Secretario General Municipal"}};
        for (String[] f : firmas) {
            boolean conNombre = f[1] != null && !f[1].isBlank() && !f[1].startsWith("___");
            t.addCell(celda().setPaddingLeft(12).setPaddingRight(12)
                .add(new Paragraph(f[0]).setFont(bold).setFontSize(7.5f).setFontColor(TEXTO)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20))
                .add(new Table(1).useAllAvailableWidth().addCell(new Cell().setHeight(1)
                    .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(TEXTO, 0.7f))))
                .add(new Paragraph(conNombre ? f[1] : "Nombre y firma").setFont(conNombre ? bold : reg).setFontSize(7.5f)
                    .setFontColor(conNombre ? TEXTO : TENUE).setTextAlignment(TextAlignment.CENTER).setMarginTop(2))
                .add(new Paragraph(f[2]).setFont(reg).setFontSize(7f).setFontColor(TENUE)
                    .setTextAlignment(TextAlignment.CENTER).setMargin(0)));
        }
        return t;
    }

    // ── Piezas ───────────────────────────────────────────────────────────────

    private static Table cuadricula(List<String[]> pares, PdfFont bold, PdfFont reg) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{18, 32, 18, 32})).useAllAvailableWidth()
            .setMarginBottom(6);
        for (String[] kv : pares) {
            t.addCell(new Cell().add(new Paragraph(kv[0].toUpperCase()).setFont(bold).setFontSize(6.8f).setFontColor(TENUE))
                .setBackgroundColor(FONDO).setPadding(3).setPaddingLeft(6)
                .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(new DeviceRgb(255, 255, 255), 1.5f))
                .setVerticalAlignment(VerticalAlignment.MIDDLE));
            t.addCell(new Cell().add(new Paragraph(hay(kv[1]) ? kv[1] : "—").setFont(reg).setFontSize(8.5f).setFontColor(OSCURO))
                .setPadding(3).setPaddingLeft(6)
                .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINEA, 0.5f))
                .setVerticalAlignment(VerticalAlignment.MIDDLE));
        }
        if (pares.size() % 2 != 0) {
            t.addCell(new Cell().setBorder(Border.NO_BORDER));
            t.addCell(new Cell().setBorder(Border.NO_BORDER));
        }
        return t;
    }

    private static void filaDato(Table t, String k, String v, PdfFont bold, PdfFont reg) {
        t.addCell(new Cell().add(new Paragraph(k.toUpperCase()).setFont(bold).setFontSize(6.8f).setFontColor(TENUE))
            .setPadding(3).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINEA, 0.5f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE));
        t.addCell(new Cell().add(new Paragraph(hay(v) ? v : "—").setFont(reg).setFontSize(8.5f).setFontColor(OSCURO))
            .setPadding(3).setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(LINEA, 0.5f))
            .setVerticalAlignment(VerticalAlignment.MIDDLE));
    }

    private static Cell chip(String texto, DeviceRgb color, PdfFont bold) {
        return new Cell().add(new Paragraph(texto).setFont(bold).setFontSize(7.5f).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(color).setPaddingTop(3).setPaddingBottom(3).setPaddingLeft(8).setPaddingRight(8)
            .setBorder(new SolidBorder(ColorConstants.WHITE, 2f));
    }

    private static DeviceRgb colorEstadoFisico(String estado) {
        String e = estado.trim().toLowerCase();
        if (e.startsWith("buen") || e.startsWith("excel") || e.startsWith("nuev")) return VERDE;
        if (e.startsWith("regul")) return AMBAR;
        if (e.startsWith("mal") || e.startsWith("inserv") || e.startsWith("desc")) return ROJO;
        return TENUE;
    }

    private static Paragraph titulo(String texto, PdfFont bold) {
        return new Paragraph(texto).setFont(bold).setFontSize(8.5f).setFontColor(GUINDA)
            .setMargin(0).setMarginBottom(3).setPaddingBottom(2)
            .setBorderBottom(new SolidBorder(DORADO, 1f));
    }

    private static Paragraph etiqueta(String texto, PdfFont bold) {
        return new Paragraph(texto).setFont(bold).setFontSize(7f).setFontColor(TENUE).setMargin(0);
    }

    private static Cell celdaMov(String s, PdfFont reg, DeviceRgb bg) {
        return new Cell().add(new Paragraph(hay(s) ? s : "—").setFont(reg).setFontSize(7.5f).setFontColor(TEXTO))
            .setBackgroundColor(bg).setPadding(3).setBorder(Border.NO_BORDER);
    }

    private static Table barra(DeviceRgb color, float alto) {
        return new Table(1).useAllAvailableWidth()
            .addCell(new Cell().setHeight(alto).setPadding(0).setBackgroundColor(color).setBorder(Border.NO_BORDER));
    }

    private static Cell celda() {
        return new Cell().setBorder(Border.NO_BORDER).setPadding(0);
    }

    private static String[] par(String k, String v) { return new String[]{k, v}; }

    private static boolean hay(String s) { return s != null && !s.isBlank(); }

    private static String mayus(String s) { return hay(s) ? s.trim().toUpperCase() : "—"; }

    public File exportFichasTecnicasMasivas(List<Producto> bienes,
            MovimientoService movimientoService) throws Exception {
        if (bienes == null || bienes.isEmpty())
            throw new IllegalArgumentException("La lista de bienes está vacía — no hay fichas que generar");
        List<String> ids = bienes.stream().map(Producto::getId).toList();
        Map<String, List<Movimiento>> movsByProducto = movimientoService.getByProductoIds(ids);

        File output = tempFile("fichas_tecnicas_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")), ".pdf");
        List<File> temps = new java.util.ArrayList<>();
        try (PdfWriter writer = new PdfWriter(output.getAbsolutePath());
             PdfDocument merged = new PdfDocument(writer)) {
            PdfMerger merger = new PdfMerger(merged);
            for (Producto p : bienes) {
                List<Movimiento> movs = movsByProducto.getOrDefault(p.getId(), List.of());
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
}
