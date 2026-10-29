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
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RESGUARDO INDIVIDUAL · PARQUE VEHICULAR: the municipio's own sheet for a
 * vehicle (one page each), as Patrimonio fills it by hand — the área and its
 * secretaría, who holds it, the vehicle's data, four photos (front, right,
 * left, back), the responsiva and the three signatures. Not the V.6, which is
 * the entrega-recepción annex.
 *
 * <p>Placas, estatus and tarjeta de circulación came in the descripción of the
 * vehicle list; submarca and año come together in modelo ("NP300 2023"). What
 * the inventory has no field for (color, cilindraje) is left to fill by hand.
 * RFC, domicilio and who signs come from Configuración.
 */
public class ReporteResguardoVehicularService extends ReporteService {

    public static final String CLAVE_RFC       = "rfc_municipio";
    public static final String CLAVE_DOMICILIO = "domicilio_municipio";
    public static final String CLAVE_ELABORA   = "firma_elabora";
    public static final String CLAVE_VOBO      = "firma_vobo";

    private static final DeviceRgb ROJO  = new DeviceRgb(192, 0, 0);
    private static final DeviceRgb LINEA = new DeviceRgb(0, 0, 0);
    private static final Locale ES = Locale.forLanguageTag("es-MX");
    private static final String[] VISTAS = {"FRONTAL", "LATERAL DERECHO", "LATERAL IZQUIERDO", "TRASERA"};

    public ReporteResguardoVehicularService() { super(); }

    /** One sheet per vehicle, by área and número, with the resguardo in force of each. */
    public File exportar(List<Producto> vehiculos) throws Exception {
        return exportar(vehiculos, null);
    }

    /** @param de the resguardo being printed (Resguardos › PDF), or null for the one in force of each */
    public File exportar(List<Producto> vehiculos, Resguardo de) throws Exception {
        if (vehiculos == null || vehiculos.isEmpty()) return null;
        List<Producto> ordenados = ordenadosPorArea(vehiculos);
        Map<String, Resguardo> vigentes = de != null ? new HashMap<>() : vigentes();
        if (de != null) for (Producto p : ordenados) vigentes.put(p.getId(), de);
        Map<String, List<ImageData>> fotos = fotos(ordenados);
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont reg  = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        Datos datos = new Datos(config(CLAVE_RFC, ""), config(CLAVE_DOMICILIO, ""),
            config(CLAVE_ELABORA, ""), config(CLAVE_VOBO, ""), configRepoMunicipio());
        File file = tempFile("resguardo_vehicular", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER)) {
            doc.setMargins(20, 22, 18, 22);
            for (int i = 0; i < ordenados.size(); i++) {
                if (i > 0) doc.add(new AreaBreak());
                Producto p = ordenados.get(i);
                hoja(doc, p, vigentes.get(p.getId()), fotos.getOrDefault(p.getId(), List.of()), datos, bold, reg);
            }
        }
        return file;
    }

    record Datos(String rfc, String domicilio, String elabora, String vobo, String municipio) {}

    private String configRepoMunicipio() {
        String m = config("municipio", "Ixmiquilpan, Hidalgo");
        int coma = m.indexOf(',');
        return (coma > 0 ? m.substring(0, coma) : m).trim();
    }

    private void hoja(Document doc, Producto p, Resguardo r, List<ImageData> fotos, Datos d,
                      PdfFont bold, PdfFont reg) {
        LocalDate fecha = r != null && r.getCreadoEn() != null ? r.getCreadoEn().toLocalDate() : LocalDate.now();
        String[] secArea = secretariaYArea(p.getArea());
        Vehiculo v = Vehiculo.de(p);

        // ── Encabezado: escudo · títulos · logo del gobierno municipal ──
        Table enc = tabla(1.5f, 4.2f, 2.3f).setBorder(new SolidBorder(LINEA, 1));
        enc.addCell(imagenCelda("escudo-ixmiquilpan.png", 100, 62));
        Cell titulos = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(2);
        titulos.add(linea("RESGUARDO INDIVIDUAL", bold, 12.5f));
        titulos.add(linea("PARQUE VEHICULAR", bold, 12.5f));
        titulos.add(linea((d.municipio() + " " + estado()).toUpperCase(ES), bold, 11.5f));
        titulos.add(linea("GOBIERNO MUNICIPAL 2024-2027", bold, 9.5f));
        if (!d.rfc().isBlank()) titulos.add(linea("RFC: " + d.rfc().toUpperCase(ES), bold, 9.5f));
        if (!d.domicilio().isBlank()) titulos.add(linea(d.domicilio().toUpperCase(ES), bold, 9.5f));
        enc.addCell(titulos);
        enc.addCell(imagenCelda("gobierno-municipal-2024-2027.png", 160, 52));
        doc.add(enc);

        Table t1 = tabla(1.6f, 1.9f, 1.3f, 1.9f, 1.6f).setMarginTop(3);
        t1.addCell(etiqueta("FECHA:", bold));
        t1.addCell(valor(fecha.format(DateTimeFormatter.ofPattern("d-MMM-yy", ES)).replace(".", ""), bold, 10.5f));
        t1.addCell(valor("", bold, 8));
        t1.addCell(etiqueta("NO. DE RESGUARDO:", bold));
        t1.addCell(valor(r != null ? r.getNumero() : "", bold, r != null && r.getNumero().length() > 8 ? 10 : 15));
        doc.add(t1);
        Table t2 = tabla(1.8f, 6.5f);
        t2.addCell(etiqueta("NO. DE INVENTARIO:", bold));
        t2.addCell(valor(p.getCodigo(), bold, 17));
        doc.add(t2);

        doc.add(barra("ÁREA RESGUARDANTE", bold));
        Table t3 = tabla(1.3f, 7f);
        t3.addCell(etiqueta("SECRETARÍA:", bold)); t3.addCell(valor(secArea[0], bold, 6.8f));
        t3.addCell(etiqueta("ÁREA:", bold));       t3.addCell(valor(secArea[1], bold, 6.8f));
        doc.add(t3);

        doc.add(barra("DATOS DEL RESGUARDANTE", bold));
        Table t4 = tabla(1.3f, 3.1f, 1.4f, 3.1f);
        t4.addCell(etiqueta("NOMBRE:", bold));
        t4.addCell(valor(mayus(p.getResguardante()), bold, 8.5f).setPaddingTop(3).setPaddingBottom(3));
        t4.addCell(etiqueta("CARGO:", bold));
        t4.addCell(valor(r != null ? mayus(r.getResguardanteCargo()) : "", bold, 8.5f));
        doc.add(t4);

        doc.add(barra("DATOS DEL VEHÍCULO", bold));
        Table t5 = tabla(1.3f, 3.1f, 1.4f, 3.1f);
        fila(t5, "TIPO", mayus(p.getTipoBien()), "NO. DE SERIE", mayus(p.getNumeroSerie()), bold);
        fila(t5, "MARCA", mayus(p.getMarca()), "NO. DE MOTOR", mayus(p.getNoMotor()), bold);
        fila(t5, "SUBMARCA", v.submarca(), "COLOR", mayus(p.getColor()), bold);
        fila(t5, "MODELO", v.anio(), "CILINDRAJE", "", bold);
        fila(t5, "FACTURA No.", mayus(p.getNumeroFactura()), "No. DE PLACAS", v.placas(), bold);
        fila(t5, "ORIGINAL Y/O", hay(p.getNumeroFactura()) || hay(p.getFolioFiscal()) ? "FACTURA" : "", "DOCUMENTOS", v.documentos(), bold);
        fila(t5, "A NOMBRE DE:", "MUNICIPIO DE " + d.municipio().toUpperCase(ES), "VALOR EN LIBROS",
            com.sibim.session.Permisos.pesos(p.getValorTotal()), bold);
        doc.add(t5);

        doc.add(barra("OBSERVACIONES", bold));
        Table t6 = tabla(1f);
        t6.addCell(valor(v.observaciones(p), bold, 7.5f).setPaddingTop(3).setPaddingBottom(3));
        doc.add(t6);

        doc.add(barra("FOTOS", bold));
        Table t7 = tabla(1f, 1f, 1f, 1f);
        for (String vista : VISTAS) t7.addCell(etiqueta(vista, bold).setTextAlignment(TextAlignment.CENTER));
        for (int i = 0; i < VISTAS.length; i++) {
            Cell c = new Cell().setHeight(80).setPadding(1).setBorder(new SolidBorder(LINEA, 0.5f))
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
            if (i < fotos.size())
                c.add(new Image(fotos.get(i)).scaleToFit(136, 77).setHorizontalAlignment(HorizontalAlignment.CENTER));
            t7.addCell(c);
        }
        doc.add(t7);

        Table t8 = tabla(1f).setMarginTop(3);
        t8.addCell(new Cell().add(new Paragraph("Responsiva del resguardo del vehículo").setFont(bold).setFontSize(8.5f)
                .setTextAlignment(TextAlignment.CENTER).setMargin(0))
            .setBorder(new SolidBorder(LINEA, 0.5f)).setPadding(2));
        Cell texto = new Cell().setBorder(new SolidBorder(LINEA, 0.5f)).setPadding(4);
        for (String parrafo : responsiva(d.municipio()))
            texto.add(new Paragraph(parrafo).setFont(bold).setFontSize(6.5f).setTextAlignment(TextAlignment.JUSTIFIED)
                .setMultipliedLeading(1.12f).setMargin(0).setMarginBottom(2));
        texto.add(new Paragraph()
            .add(new Text("Firmando el presente resguardo, aceptando y entendiendo expresamente todas y cada una de las "
                + "obligaciones y consecuencias de hecho y de derecho que llevan inherente el mismo, ").setFont(bold))
            .add(new Text(fechaEnTexto(fecha)).setFont(bold).setFontColor(ROJO))
            .add(new Text(", " + d.municipio() + ", " + estado() + ".").setFont(bold))
            .setFontSize(6.5f).setTextAlignment(TextAlignment.JUSTIFIED).setMultipliedLeading(1.12f).setMargin(0));
        t8.addCell(texto);
        doc.add(t8);

        Table firmas = tabla(1f, 1f, 1f).setMarginTop(4).setBorder(new SolidBorder(LINEA, 1));
        firma(firmas, "RECIBÍ DE CONFORMIDAD", mayus(p.getResguardante()),
            r != null ? mayus(r.getResguardanteCargo()) : "", bold);
        firma(firmas, "ELABORÓ", d.elabora().toUpperCase(ES), "DIRECTOR DE RECURSOS MATERIALES Y PATRIMONIO", bold);
        firma(firmas, "Vo. Bo.", d.vobo().toUpperCase(ES), "SECRETARIO GENERAL MUNICIPAL", bold);
        doc.add(firmas);
    }

    /** Presidencia's own and its direcciones hang from the Despacho: the sheet says
     *  PRESIDENCIA MUNICIPAL there, as the municipio's format does. */
    static String[] secretariaYArea(String area) {
        String[] d = ReporteEtiquetasService.departamentoYResguardo(area);
        if (d[0].equalsIgnoreCase(com.sibim.config.Areas.PRESIDENCIA)) d[0] = "PRESIDENCIA MUNICIPAL";
        return d;
    }

    private String estado() {
        String m = config("municipio", "Ixmiquilpan, Hidalgo");
        int coma = m.indexOf(',');
        return coma > 0 ? m.substring(coma + 1).trim() : "Hidalgo";
    }

    static List<String> responsiva(String municipio) {
        return List.of(
            "En cumplimiento a lo dispuesto por el artículo 98 fracción VII de la Ley Orgánica Municipal para el Estado de "
                + "Hidalgo, en concordancia con los artículos 87, 88 y 89 de la Ley de Presupuesto y Contabilidad Gubernamental "
                + "del Estado de Hidalgo, así como lo previsto por la Ley General de Contabilidad Gubernamental, con el propósito "
                + "de controlar y salvaguardar los bienes muebles que conforman el patrimonio del municipio de " + municipio
                + ", se integra la presente responsiva de resguardo individual de vehículo, cuya descripción se detalla en el "
                + "recuadro correspondiente.",
            "El servidor público acepta y se obliga a custodiar y hacer buen uso del vehículo descrito, comprometiéndose a "
                + "informar de manera inmediata a la Dirección de Recursos Materiales y Patrimonio sobre cualquier deterioro, "
                + "falla, pérdida y/o robo que pudiera presentar dicho bien. En caso de daño o pérdida que sufra por descuido, "
                + "negligencia, omisión, falta de pericia o incumplimiento de las leyes que le sean imputables, el resguardante "
                + "se compromete a resarcir los daños al Ayuntamiento de " + municipio + " hasta por el valor total del bien, "
                + "así como a las terceras personas que resulten afectadas.",
            "Declarando en este acto que recibe a su entero conocimiento y satisfacción el vehículo y las características y "
                + "condiciones que se detallan, manifestando su conformidad y obligándose como depositario, y conocedor de la "
                + "responsabilidad y sanciones que pudiera hacerse acreedor en caso de daño o pérdida parcial. En este sentido, "
                + "se hace referencia a lo dispuesto en el artículo 4 de la Ley General de Responsabilidades de los Servidores "
                + "Públicos para el Estado de Hidalgo, que establece que los procedimientos para la aplicación de sanciones se "
                + "desarrollarán autónomamente según su naturaleza y por la vía procesal que corresponda, así como a los "
                + "artículos 221 y 222 del Código Penal para el Estado de Hidalgo, que prevén la punibilidad por el daño "
                + "culposo a la propiedad, incluyendo la obligación de resarcir el daño causado y responder penalmente por la "
                + "destrucción o deterioro de bienes ajenos.",
            "Una vez enterado y aceptado la recepción y custodia del vehículo ya descrito sabedor de las obligaciones y "
                + "responsabilidades que implican la falta de cumplimiento de las disposiciones legales del estado que los regula.",
            "Aceptando las responsabilidades de hecho y de derecho en que el presente acto representa para su persona y su "
                + "patrimonio personal y para el caso de hacer mal uso o bien uso negligente e inadecuado del bien propiedad del "
                + "municipio de " + municipio + ", Hgo., haciéndose acreedor a las penas y sanciones civiles, administrativas y "
                + "penales que correspondan.");
    }

    /** "a los 15 días del mes de Octubre de 2026". */
    static String fechaEnTexto(LocalDate f) {
        String mes = f.format(DateTimeFormatter.ofPattern("MMMM", ES));
        return "a los " + f.getDayOfMonth() + (f.getDayOfMonth() == 1 ? " día" : " días") + " del mes de "
            + Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " de " + f.getYear();
    }

    /** What the vehicle list put in modelo and descripción, apart. */
    record Vehiculo(String submarca, String anio, String placas, String documentos, String estatus) {
        private static final Pattern ANIO = Pattern.compile("^(.*?)\\s*\\b((?:19|20)\\d{2})\\s*$");
        private static final Pattern PLACAS = Pattern.compile("(?i)placas:\\s*([^.]+)\\.");
        private static final Pattern TARJETA = Pattern.compile("(?i)tarjeta de circulaci[oó]n:\\s*([^.]+)\\.");
        private static final Pattern ESTATUS = Pattern.compile("(?i)estatus:\\s*([^.]+)\\.");

        static Vehiculo de(Producto p) {
            String modelo = p.getModelo() != null ? p.getModelo().trim() : "";
            String submarca = modelo, anio = "";
            Matcher m = ANIO.matcher(modelo);
            if (m.matches()) { submarca = m.group(1); anio = m.group(2); }
            else {
                Matcher n = ANIO.matcher(p.getNombre() != null ? p.getNombre().trim() : "");
                if (n.matches()) anio = n.group(2);
            }
            String desc = p.getDescripcion() != null ? p.getDescripcion() : "";
            String placas = grupo(PLACAS, desc);
            if (placas.equalsIgnoreCase("NO")) placas = "SIN PLACAS";
            String tarjeta = hay(p.getNoTarjetaCirculacion()) ? p.getNoTarjetaCirculacion() : grupo(TARJETA, desc);
            String documentos = tarjeta.isBlank() || tarjeta.equalsIgnoreCase("no") ? "" : "TARJETA DE CIRCULACIÓN";
            return new Vehiculo(submarca.toUpperCase(ES), anio, placas.toUpperCase(ES), documentos,
                grupo(ESTATUS, desc).toUpperCase(ES));
        }

        String observaciones(Producto p) {
            List<String> partes = new ArrayList<>();
            if (hay(p.getEstadoFisico())) partes.add("ESTADO FÍSICO: " + p.getEstadoFisico().trim().toUpperCase(ES));
            if (!estatus.isBlank()) partes.add("ESTATUS: " + estatus);
            return String.join(" · ", partes);
        }

        private static String grupo(Pattern p, String s) {
            Matcher m = p.matcher(s);
            return m.find() ? m.group(1).trim() : "";
        }
    }

    /** The resguardo in force of each bien. Without it the sheet still prints, number and cargo blank. */
    private static Map<String, Resguardo> vigentes() {
        Map<String, Resguardo> m = new HashMap<>();
        try {
            for (Resguardo r : new com.sibim.repository.ResguardoRepository().findAll()) {
                if (!r.isActivo() || r.getItems() == null) continue;
                for (var it : r.getItems()) if (it.getProductoId() != null) m.putIfAbsent(it.getProductoId(), r);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ReporteResguardoVehicularService.class)
                .warn("Sin resguardos para el resguardo vehicular: {}", e.getMessage());
        }
        return m;
    }

    /** Up to four photos of each vehicle, fetched in parallel. */
    private static Map<String, List<ImageData>> fotos(List<Producto> vehiculos) {
        Map<String, List<ImageData>> out = new HashMap<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Map<String, Future<List<ImageData>>> pendientes = new HashMap<>();
            for (Producto p : vehiculos) pendientes.put(p.getId(), pool.submit(() -> FotosPdf.de(p, 4, 700)));
            for (var e : pendientes.entrySet()) {
                try { out.put(e.getKey(), e.getValue().get()); }
                catch (Exception ex) { out.put(e.getKey(), List.of()); }
            }
        }
        return out;
    }

    // ── Celdas ────────────────────────────────────────────────────────────────

    private static Cell etiqueta(String texto, PdfFont bold) {
        return new Cell().add(new Paragraph(texto).setFont(bold).setFontSize(7f).setFontColor(ColorConstants.WHITE).setMargin(0))
            .setBackgroundColor(ROJO).setBorder(new SolidBorder(LINEA, 0.5f)).setPadding(1).setPaddingLeft(4)
            .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Cell valor(String texto, PdfFont bold, float tam) {
        return new Cell().add(new Paragraph(texto != null ? texto : "").setFont(bold).setFontSize(tam)
                .setTextAlignment(TextAlignment.CENTER).setMargin(0).setMultipliedLeading(1.05f))
            .setBorder(new SolidBorder(LINEA, 0.5f)).setPadding(1).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static void fila(Table t, String e1, String v1, String e2, String v2, PdfFont bold) {
        t.addCell(etiqueta(e1, bold)); t.addCell(valor(v1, bold, 7.8f));
        t.addCell(etiqueta(e2, bold)); t.addCell(valor(v2, bold, 7.8f));
    }

    private static Table barra(String texto, PdfFont bold) {
        Table t = tabla(1f).setMarginTop(2);
        t.addCell(new Cell().add(new Paragraph(texto).setFont(bold).setFontSize(7.5f).setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER).setMargin(0))
            .setBackgroundColor(ROJO).setBorder(new SolidBorder(LINEA, 0.5f)).setPadding(1.5f));
        return t;
    }

    private static Paragraph linea(String texto, PdfFont bold, float tam) {
        return new Paragraph(texto).setFont(bold).setFontSize(tam).setMargin(0).setMultipliedLeading(1.0f);
    }

    private static void firma(Table t, String titulo, String nombre, String cargo, PdfFont bold) {
        Cell c = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER).setPaddingTop(4).setPaddingBottom(4);
        c.add(new Paragraph(titulo).setFont(bold).setFontSize(9f).setMargin(0).setMarginBottom(34));
        c.add(new Paragraph(" ").setMargin(0).setFontSize(4)
            .setBorderBottom(new SolidBorder(LINEA, 0.6f)).setMarginLeft(18).setMarginRight(18));
        c.add(new Paragraph(nombre.isBlank() ? " " : nombre).setFont(bold).setFontSize(7.8f).setMargin(0).setMarginTop(2));
        c.add(new Paragraph(cargo).setFont(bold).setFontSize(6.2f).setMargin(0));
        t.addCell(c);
    }

    private static Cell imagenCelda(String recurso, float ancho, float alto) {
        Cell c = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE).setPadding(3);
        try (var in = ReporteResguardoVehicularService.class.getResourceAsStream("/img/formato/" + recurso)) {
            if (in != null)
                c.add(new Image(ImageDataFactory.create(in.readAllBytes())).scaleToFit(ancho, alto)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER));
        } catch (Exception ignored) {
            // without the image the header keeps its text
        }
        return c;
    }

    private static boolean hay(String s) { return s != null && !s.isBlank(); }

    private static String mayus(String s) { return hay(s) ? s.trim().toUpperCase(ES) : ""; }
}
