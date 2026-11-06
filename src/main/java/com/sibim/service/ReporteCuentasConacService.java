package com.sibim.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The two reports Tesorería and Transparencia ask Patrimonio for (6-nov-2026):
 * <ul>
 *   <li><b>Saldos por cuenta contable (CONAC)</b>: cost, depreciación acumulada and
 *       valor en libros of the bienes muebles by account of the Plan de Cuentas
 *       (1.2.4.x), to reconcile the physical inventory with the accounting one.</li>
 *   <li><b>Relación de Bienes Muebles que Componen el Patrimonio</b>: the CONAC
 *       format (código, descripción, valor en libros) to publish the inventory.
 *       No resguardante: it carries no personal data.</li>
 * </ul>
 * A bien without the data to depreciate it (fecha de adquisición or vida útil)
 * counts at its cost, and the report says how many there are.
 */
public class ReporteCuentasConacService extends ReporteService {

    public ReporteCuentasConacService() { super(); }

    /** Names of the CONAC accounts of bienes muebles (Plan de Cuentas, rubro 1.2.4). */
    static final Map<String, String> CUENTAS = Map.ofEntries(
        Map.entry("1.2.4",     "Bienes Muebles"),
        Map.entry("1.2.4.1",   "Mobiliario y Equipo de Administración"),
        Map.entry("1.2.4.1.1", "Muebles de Oficina y Estantería"),
        Map.entry("1.2.4.1.2", "Muebles, Excepto de Oficina y Estantería"),
        Map.entry("1.2.4.1.3", "Equipo de Cómputo y de Tecnologías de la Información"),
        Map.entry("1.2.4.1.9", "Otros Mobiliarios y Equipos de Administración"),
        Map.entry("1.2.4.2",   "Mobiliario y Equipo Educacional y Recreativo"),
        Map.entry("1.2.4.2.1", "Equipos y Aparatos Audiovisuales"),
        Map.entry("1.2.4.2.2", "Aparatos Deportivos"),
        Map.entry("1.2.4.2.3", "Cámaras Fotográficas y de Video"),
        Map.entry("1.2.4.2.9", "Otro Mobiliario y Equipo Educacional y Recreativo"),
        Map.entry("1.2.4.3",   "Equipo e Instrumental Médico y de Laboratorio"),
        Map.entry("1.2.4.3.1", "Equipo Médico y de Laboratorio"),
        Map.entry("1.2.4.3.2", "Instrumental Médico y de Laboratorio"),
        Map.entry("1.2.4.4",   "Equipo de Transporte"),
        Map.entry("1.2.4.4.1", "Automóviles y Equipo Terrestre"),
        Map.entry("1.2.4.4.2", "Carrocerías y Remolques"),
        Map.entry("1.2.4.4.5", "Embarcaciones"),
        Map.entry("1.2.4.4.9", "Otros Equipos de Transporte"),
        Map.entry("1.2.4.5",   "Equipo de Defensa y Seguridad"),
        Map.entry("1.2.4.6",   "Maquinaria, Otros Equipos y Herramientas"),
        Map.entry("1.2.4.6.1", "Maquinaria y Equipo Agropecuario"),
        Map.entry("1.2.4.6.2", "Maquinaria y Equipo Industrial"),
        Map.entry("1.2.4.6.3", "Maquinaria y Equipo de Construcción"),
        Map.entry("1.2.4.6.4", "Sistemas de Aire Acondicionado, Calefacción y de Refrigeración Industrial y Comercial"),
        Map.entry("1.2.4.6.5", "Equipo de Comunicación y Telecomunicación"),
        Map.entry("1.2.4.6.6", "Equipos de Generación Eléctrica, Aparatos y Accesorios Eléctricos"),
        Map.entry("1.2.4.6.7", "Herramientas y Máquinas-Herramienta"),
        Map.entry("1.2.4.6.9", "Otros Equipos"),
        Map.entry("1.2.4.7",   "Colecciones, Obras de Arte y Objetos Valiosos"),
        Map.entry("1.2.4.8",   "Activos Biológicos"));

    static final String SIN_CLAVE = "Sin clave CONAC";

    /** The account of a bien to the subcuenta (five levels, "1.2.4.1.3"), out of its
     *  clave armonizada ("1.2.4.1.3.515" → "1.2.4.1.3"); null when it has none. */
    public static String cuentaDe(Producto p) {
        String c = p.getClaveArmonizada();
        if (c == null) return null;
        String limpia = c.trim().replaceAll("[^0-9.]", "").replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
        if (limpia.isEmpty()) return null;
        String[] partes = limpia.split("\\.");
        return String.join(".", Arrays.copyOf(partes, Math.min(5, partes.length)));
    }

    /** The account one level up ("1.2.4.1.3" → "1.2.4.1"); itself when it is already short. */
    static String cuentaMayor(String subcuenta) {
        if (subcuenta == null) return null;
        String[] partes = subcuenta.split("\\.");
        return partes.length <= 4 ? subcuenta : String.join(".", Arrays.copyOf(partes, 4));
    }

    /** Its name in the Plan de Cuentas, else the name of the account above it. */
    public static String nombreCuenta(String cuenta) {
        if (cuenta == null) return SIN_CLAVE;
        if (CUENTAS.containsKey(cuenta)) return CUENTAS.get(cuenta);
        String mayor = cuentaMayor(cuenta);
        return CUENTAS.containsKey(mayor) ? CUENTAS.get(mayor) + " (" + cuenta + ")" : "Cuenta " + cuenta;
    }

    /** What the bienes of one account add up to. */
    public record Saldo(String cuenta, String nombre, int bienes, long piezas, BigDecimal costo,
                        BigDecimal libros, int sinDepreciar) {
        public BigDecimal depreciacion() { return costo.subtract(libros); }
    }

    /** The valor en libros of a renglón: its cost when it can't be depreciated yet. */
    static BigDecimal libros(Producto p) {
        BigDecimal v = p.getValorDepreciadoTotal();
        return v != null ? v : p.getValorTotal();
    }

    /** No fecha de adquisición or vida útil: counted at cost. */
    static boolean sinDepreciar(Producto p) {
        return p.getValorDepreciado() == null && p.getValorUnitario().signum() > 0;
    }

    static List<Producto> activos(List<Producto> bienes) {
        return bienes.stream().filter(p -> p.getFechaBaja() == null && !p.isDadoDeBaja()).toList();
    }

    /** By subcuenta, in the order of the Plan de Cuentas; those without a key at the end. */
    public static List<Saldo> saldosPorSubcuenta(List<Producto> bienes) {
        return agrupar(activos(bienes), ReporteCuentasConacService::cuentaDe);
    }

    /** By cuenta (four levels: "1.2.4.1"), to subtotal the subcuentas. */
    public static List<Saldo> saldosPorCuenta(List<Producto> bienes) {
        return agrupar(activos(bienes), p -> cuentaMayor(cuentaDe(p)));
    }

    private static List<Saldo> agrupar(List<Producto> bienes, java.util.function.Function<Producto, String> clave) {
        Map<String, List<Producto>> por = new HashMap<>();
        for (Producto p : bienes) por.computeIfAbsent(Objects.requireNonNullElse(clave.apply(p), ""), k -> new ArrayList<>()).add(p);
        List<Saldo> out = new ArrayList<>();
        for (var e : por.entrySet()) {
            String cuenta = e.getKey().isEmpty() ? null : e.getKey();
            BigDecimal costo = BigDecimal.ZERO, libros = BigDecimal.ZERO;
            long piezas = 0;
            int sin = 0;
            for (Producto p : e.getValue()) {
                costo = costo.add(p.getValorTotal());
                libros = libros.add(libros(p));
                piezas += p.getStockActual();
                if (sinDepreciar(p)) sin++;
            }
            out.add(new Saldo(cuenta, nombreCuenta(cuenta), e.getValue().size(), piezas, costo, libros, sin));
        }
        out.sort(Comparator.comparing((Saldo s) -> s.cuenta() == null)
            .thenComparing(s -> Objects.requireNonNullElse(s.cuenta(), ""), ReporteCuentasConacService::porNumeros));
        return out;
    }

    /** "1.2.4.10" after "1.2.4.9": level by level, as numbers. */
    static int porNumeros(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            int c;
            try { c = Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i])); }
            catch (NumberFormatException e) { c = x[i].compareTo(y[i]); }
            if (c != 0) return c;
        }
        return Integer.compare(x.length, y.length);
    }

    private static Saldo total(List<Saldo> saldos) {
        BigDecimal costo = BigDecimal.ZERO, libros = BigDecimal.ZERO;
        int bienes = 0, sin = 0;
        long piezas = 0;
        for (Saldo s : saldos) {
            costo = costo.add(s.costo()); libros = libros.add(s.libros());
            bienes += s.bienes(); piezas += s.piezas(); sin += s.sinDepreciar();
        }
        return new Saldo(null, "Total", bienes, piezas, costo, libros, sin);
    }

    private static String fechaLarga(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX")));
    }

    // ── Saldos por cuenta: PDF ──────────────────────────────────────────────────

    private static final String[] SALDOS = {"Cuenta", "Nombre de la cuenta", "Bienes", "Costo de adquisición",
        "Depreciación acumulada", "Valor en libros"};

    public File exportSaldosPdf(List<Producto> bienes) throws Exception {
        List<Saldo> sub = saldosPorSubcuenta(bienes);
        Map<String, Saldo> mayores = new LinkedHashMap<>();
        for (Saldo s : saldosPorCuenta(bienes)) mayores.put(Objects.requireNonNullElse(s.cuenta(), ""), s);
        Saldo total = total(sub);
        File file = tempFile("saldos_cuenta_conac", ".pdf");
        PdfFont bold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER.rotate())) {
            String folio = generateFolio("CTA");
            addPdfHeader(doc, "Saldos por cuenta contable (CONAC)", null, null, folio);
            doc.add(alDia("Bienes muebles activos al " + fechaLarga(LocalDate.now())));
            Table t = createPdfTable(SALDOS, new float[]{1.1f, 4.2f, 0.8f, 1.6f, 1.6f, 1.6f});
            alinearDerecha(t, 2, 3, 4, 5);
            String mayorActual = null;
            int i = 0;
            for (Saldo s : sub) {
                String mayor = s.cuenta() == null ? "" : cuentaMayor(s.cuenta());
                // The cuenta, once, before its subcuentas, with what they add up to.
                if (!Objects.equals(mayor, mayorActual) && !mayor.isEmpty() && !mayor.equals(s.cuenta())) {
                    Saldo m = mayores.get(mayor);
                    if (m != null) {
                        t.addCell(negrita(fila(m.cuenta(), false), bold));
                        t.addCell(negrita(fila(m.nombre(), false), bold));
                        t.addCell(negrita(num(fila(String.valueOf(m.bienes()), false)), bold));
                        t.addCell(negrita(num(fila(FormatUtils.formatCurrency(m.costo()), false)), bold));
                        t.addCell(negrita(num(fila(FormatUtils.formatCurrency(m.depreciacion()), false)), bold));
                        t.addCell(negrita(num(fila(FormatUtils.formatCurrency(m.libros()), false)), bold));
                    }
                }
                mayorActual = mayor;
                boolean alt = (i++ % 2) == 1;
                t.addCell(fila(s.cuenta() == null ? "—" : "   " + s.cuenta(), alt));
                t.addCell(fila(s.nombre(), alt));
                t.addCell(num(fila(String.valueOf(s.bienes()), alt)));
                t.addCell(num(fila(FormatUtils.formatCurrency(s.costo()), alt)));
                t.addCell(num(fila(FormatUtils.formatCurrency(s.depreciacion()), alt)));
                t.addCell(num(fila(FormatUtils.formatCurrency(s.libros()), alt)));
            }
            if (sub.isEmpty()) sinRegistros(t, SALDOS.length, "No hay bienes activos");
            addFilaTotal(t, 2, "TOTAL  (" + FormatUtils.plural(total.bienes(), "bien", "bienes") + ")",
                String.valueOf(total.bienes()), FormatUtils.formatCurrency(total.costo()),
                FormatUtils.formatCurrency(total.depreciacion()), FormatUtils.formatCurrency(total.libros()));
            doc.add(t);
            if (total.sinDepreciar() > 0)
                doc.add(new Paragraph(FormatUtils.plural(total.sinDepreciar(), "bien no tiene", "bienes no tienen")
                    + " fecha de adquisición o vida útil: se cuentan a su costo, sin depreciar. "
                    + "Complétalos en Bienes › Más › Completar datos para que el valor en libros sea el real.")
                    .setFontSize(8).setFontColor(new com.itextpdf.kernel.colors.DeviceRgb(107, 114, 128)).setMarginTop(6));
            addFirmasBlock(doc,
                new String[]{"ELABORÓ", firmaElabora(), "Director de Recursos Materiales"},
                new String[]{"CONTADOR MUNICIPAL", firma(FIRMA_CONTADOR), "Contador / Contralor Municipal"},
                new String[]{"VO.BO.", firma(FIRMA_TESORERO), "Tesorero Municipal"});
            addPdfFooter(doc, total.bienes(), folio);
        }
        return numerarPaginas(file);
    }

    private static Paragraph alDia(String texto) throws java.io.IOException {
        return new Paragraph(texto).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))
            .setFontSize(10).setMarginTop(4).setMarginBottom(0);
    }

    private static com.itextpdf.layout.element.Cell negrita(com.itextpdf.layout.element.Cell c, PdfFont bold) {
        c.getChildren().forEach(e -> { if (e instanceof Paragraph p) p.setFont(bold); });
        return c.setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(245, 240, 232));
    }

    // ── Saldos por cuenta: Excel (with the bienes behind each total) ────────────

    public File exportSaldosExcel(List<Producto> bienes) throws Exception {
        File file = tempFile("saldos_cuenta_conac", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle dinero = wb.createCellStyle();
            dinero.setDataFormat(wb.createDataFormat().getFormat("$#,##0.00"));
            Font negrita = wb.createFont();
            negrita.setBold(true);
            CellStyle dineroNegrita = wb.createCellStyle();
            dineroNegrita.cloneStyleFrom(dinero);
            dineroNegrita.setFont(negrita);
            CellStyle textoNegrita = wb.createCellStyle();
            textoNegrita.setFont(negrita);

            Sheet hoja = createSheet(wb, "Saldos por cuenta");
            writeHeader(hoja, new String[]{"Cuenta", "Nombre de la cuenta", "Nivel", "Bienes", "Piezas",
                "Costo de adquisición", "Depreciación acumulada", "Valor en libros", "Sin datos para depreciar"}, wb);
            List<Saldo> filas = new ArrayList<>();
            List<Saldo> mayores = saldosPorCuenta(bienes);
            List<Saldo> sub = saldosPorSubcuenta(bienes);
            for (Saldo m : mayores) {
                filas.add(m);
                for (Saldo s : sub)
                    if (s.cuenta() != null && m.cuenta() != null && !s.cuenta().equals(m.cuenta())
                        && cuentaMayor(s.cuenta()).equals(m.cuenta())) filas.add(s);
            }
            int r = 1;
            for (Saldo s : filas) {
                boolean esMayor = mayores.contains(s);
                Row row = hoja.createRow(r++);
                celda(row, 0, s.cuenta() == null ? SIN_CLAVE : s.cuenta(), esMayor ? textoNegrita : null);
                celda(row, 1, s.nombre(), esMayor ? textoNegrita : null);
                celda(row, 2, esMayor ? "Cuenta" : "Subcuenta", null);
                row.createCell(3).setCellValue(s.bienes());
                row.createCell(4).setCellValue(s.piezas());
                dinero(row, 5, s.costo(), esMayor ? dineroNegrita : dinero);
                dinero(row, 6, s.depreciacion(), esMayor ? dineroNegrita : dinero);
                dinero(row, 7, s.libros(), esMayor ? dineroNegrita : dinero);
                row.createCell(8).setCellValue(s.sinDepreciar());
            }
            Saldo total = total(mayores);
            Row tr = hoja.createRow(r);
            celda(tr, 1, "TOTAL", textoNegrita);
            tr.createCell(3).setCellValue(total.bienes());
            tr.createCell(4).setCellValue(total.piezas());
            dinero(tr, 5, total.costo(), dineroNegrita);
            dinero(tr, 6, total.depreciacion(), dineroNegrita);
            dinero(tr, 7, total.libros(), dineroNegrita);
            tr.createCell(8).setCellValue(total.sinDepreciar());
            autosizeColumns(hoja, 9);

            Sheet detalle = createSheet(wb, "Bienes por cuenta");
            writeHeader(detalle, new String[]{"Cuenta", "Clave armonizada", "Código", "Bien", "Área", "Piezas",
                "Costo de adquisición", "Depreciación acumulada", "Valor en libros", "Se deprecia"}, wb);
            int d = 1;
            List<Producto> ordenados = new ArrayList<>(activos(bienes));
            ordenados.sort(Comparator.comparing((Producto p) -> cuentaDe(p) == null)
                .thenComparing(p -> Objects.requireNonNullElse(cuentaDe(p), ""), ReporteCuentasConacService::porNumeros)
                .thenComparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), POR_CODIGO));
            for (Producto p : ordenados) {
                Row row = detalle.createRow(d++);
                celda(row, 0, Objects.requireNonNullElse(cuentaDe(p), SIN_CLAVE), null);
                celda(row, 1, Objects.toString(p.getClaveArmonizada(), ""), null);
                celda(row, 2, Objects.toString(p.getCodigo(), ""), null);
                celda(row, 3, Objects.toString(p.getNombre(), ""), null);
                celda(row, 4, Objects.toString(p.getArea(), ""), null);
                row.createCell(5).setCellValue(p.getStockActual());
                dinero(row, 6, p.getValorTotal(), dinero);
                dinero(row, 7, p.getValorTotal().subtract(libros(p)), dinero);
                dinero(row, 8, libros(p), dinero);
                celda(row, 9, sinDepreciar(p) ? "No: falta fecha o vida útil" : "Sí", null);
            }
            autosizeColumns(detalle, 10);
            addExcelInfoSheet(wb, "Saldos por cuenta contable (CONAC)", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }

    private static void celda(Row row, int col, String valor, CellStyle estilo) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(valor);
        if (estilo != null) c.setCellStyle(estilo);
    }

    private static void dinero(Row row, int col, BigDecimal valor, CellStyle estilo) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(valor == null ? 0 : valor.doubleValue());
        c.setCellStyle(estilo);
    }

    // ── Relación de Bienes Muebles que Componen el Patrimonio (CONAC) ───────────

    static final String TITULO_RELACION = "Relación de Bienes Muebles que Componen el Patrimonio";

    /** "SILLA SECRETARIAL · OFFIHO · GIRATORIA": what the bien is, without whose it is. */
    static String descripcion(Producto p) {
        List<String> partes = new ArrayList<>();
        if (p.getNombre() != null && !p.getNombre().isBlank()) partes.add(p.getNombre().trim());
        for (String extra : new String[]{p.getMarca(), p.getModelo()})
            if (extra != null && !extra.isBlank() && !"No tiene".equalsIgnoreCase(extra.trim())
                && partes.stream().noneMatch(x -> x.toUpperCase(Locale.ROOT).contains(extra.trim().toUpperCase(Locale.ROOT))))
                partes.add(extra.trim());
        String d = String.join(" · ", partes);
        return p.getStockActual() > 1 ? p.getStockActual() + " piezas: " + d : d;
    }

    private List<Producto> ordenadosPorCodigo(List<Producto> bienes) {
        List<Producto> l = new ArrayList<>(activos(bienes));
        l.sort(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), POR_CODIGO));
        return l;
    }

    public File exportRelacionBienesMueblesPdf(List<Producto> bienes) throws Exception {
        List<Producto> l = ordenadosPorCodigo(bienes);
        File file = tempFile("relacion_bienes_muebles", ".pdf");
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER)) {
            String folio = generateFolio("RBM");
            addPdfHeader(doc, TITULO_RELACION, null, null, folio);
            doc.add(alDia("Al " + fechaLarga(LocalDate.now())));
            // Thousands of rows: drawn as it fills instead of laid out whole in memory.
            Table t = createPdfTable(new String[]{"Código", "Descripción del bien mueble", "Valor en libros"},
                new float[]{1.2f, 5.6f, 1.5f}, true);
            alinearDerecha(t, 2);
            doc.add(t);
            BigDecimal total = BigDecimal.ZERO;
            int i = 0;
            for (Producto p : l) {
                boolean alt = (i++ % 2) == 1;
                BigDecimal v = libros(p);
                total = total.add(v);
                t.addCell(fila(Objects.toString(p.getCodigo(), "—"), alt));
                t.addCell(fila(descripcion(p), alt));
                t.addCell(num(fila(FormatUtils.formatCurrency(v), alt)));
                if (i % 50 == 0) t.flush();
            }
            if (l.isEmpty()) sinRegistros(t, 3, "No hay bienes activos");
            addFilaTotal(t, 2, "TOTAL  (" + FormatUtils.plural(l.size(), "bien", "bienes") + ")", FormatUtils.formatCurrency(total));
            t.complete();
            addPdfFooter(doc, l.size(), folio);
        }
        return numerarPaginas(file);
    }

    public File exportRelacionBienesMueblesExcel(List<Producto> bienes) throws Exception {
        List<Producto> l = ordenadosPorCodigo(bienes);
        File file = tempFile("relacion_bienes_muebles", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle dinero = wb.createCellStyle();
            dinero.setDataFormat(wb.createDataFormat().getFormat("$#,##0.00"));
            Font negrita = wb.createFont();
            negrita.setBold(true);
            CellStyle titulo = wb.createCellStyle();
            titulo.setFont(negrita);
            Sheet hoja = createSheet(wb, "Bienes muebles");
            // The CONAC format: ente, title and date above the three columns.
            celda(hoja.createRow(0), 0, orgName(), titulo);
            celda(hoja.createRow(1), 0, TITULO_RELACION, titulo);
            celda(hoja.createRow(2), 0, "Al " + fechaLarga(LocalDate.now()), null);
            Row enc = hoja.createRow(4);
            String[] cols = {"Código", "Descripción del bien mueble", "Valor en libros"};
            for (int c = 0; c < cols.length; c++) celda(enc, c, cols[c], titulo);
            int r = 5;
            BigDecimal total = BigDecimal.ZERO;
            for (Producto p : l) {
                Row row = hoja.createRow(r++);
                celda(row, 0, Objects.toString(p.getCodigo(), ""), null);
                celda(row, 1, descripcion(p), null);
                BigDecimal v = libros(p);
                total = total.add(v);
                dinero(row, 2, v, dinero);
            }
            Row tr = hoja.createRow(r);
            celda(tr, 1, "TOTAL", titulo);
            CellStyle dineroNegrita = wb.createCellStyle();
            dineroNegrita.cloneStyleFrom(dinero);
            dineroNegrita.setFont(negrita);
            dinero(tr, 2, total, dineroNegrita);
            autosizeColumns(hoja, 3);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }
}
