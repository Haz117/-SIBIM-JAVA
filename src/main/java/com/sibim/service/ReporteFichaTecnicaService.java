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
        der.add(new Paragraph(p.getCodigo() != null ? p.getCodigo() : "—").setFont(bold).setFontSize(21f)
            .setFontColor(GUINDA).setMargin(0));
        der.add(new Paragraph(mayus(p.getNombre())).setFont(bold).setFontSize(11f).setFontColor(OSCURO)
            .setMultipliedLeading(1.15f).setMargin(0).setMarginTop(2));

        Table chips = new Table(2).setMarginTop(6).setMarginBottom(6);
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

        byte[] qr = ReporteEtiquetasService.qrToPngBytes(p.getCodigo(), 180);
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
        l.add(par("Proveedor", p.getProveedor()));
        l.add(par("Costo de adquisición", p.getPrecioCompra() != null ? FormatUtils.formatCurrency(p.getPrecioCompra()) : null));
        BigDecimal enLibros = p.getValorDepreciado() != null ? p.getValorDepreciado() : p.getValorTotal();
        l.add(par("Vida útil", p.getVidaUtilAnios() != null ? p.getVidaUtilAnios() + " año(s)" : null));
        l.add(par("Valor en libros", enLibros != null ? FormatUtils.formatCurrency(enLibros) : null));
        if (p.getPorcentajeDepreciado() != null && p.getPrecioCompra() != null && p.getValorDepreciado() != null)
            l.add(par("Depreciación acumulada", FormatUtils.formatCurrency(p.getPrecioCompra().subtract(p.getValorDepreciado()))
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
        String[][] firmas = {
            {"RECIBÍ DE CONFORMIDAD", hay(p.getResguardante()) ? p.getResguardante().toUpperCase() : "Resguardante"},
            {"ELABORÓ", "Recursos Materiales y Patrimonio"},
            {"VO. BO.", "Nombre y firma"}};
        for (String[] f : firmas) {
            t.addCell(celda().setPaddingLeft(12).setPaddingRight(12)
                .add(new Paragraph(f[0]).setFont(bold).setFontSize(7.5f).setFontColor(TEXTO)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20))
                .add(new Table(1).useAllAvailableWidth().addCell(new Cell().setHeight(1)
                    .setBorder(Border.NO_BORDER).setBorderTop(new SolidBorder(TEXTO, 0.7f))))
                .add(new Paragraph(f[1]).setFont(reg).setFontSize(7.5f).setFontColor(TENUE)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(2)));
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
