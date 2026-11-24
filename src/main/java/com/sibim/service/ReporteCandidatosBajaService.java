package com.sibim.service;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import com.sibim.util.ProductoUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Reportes › «Candidatos a baja»: the bienes in force in estado MALO or DEFICIENTE, área by área,
 *  the oldest first, with their resguardante, years of use, costo and valor en libros — to prepare
 *  the bajas with each área (24-nov-2026: 83 MALO and 2 DEFICIENTE). A list to look at: the baja
 *  itself is still the solicitud and the dictamen, in Bajas. */
public class ReporteCandidatosBajaService extends ReporteService {

    static final Set<String> ESTADOS = Set.of("MALO", "DEFICIENTE");
    private static final String[] COLUMNAS = {"Número", "Número anterior", "Bien", "Estado", "Resguardante", "Adquirido",
        "Años de uso", "Costo", "Valor en libros"};
    private static final String NOTA = "Para darlos de baja: la solicitud y el dictamen, en Bajas. El valor en libros sale del "
        + "costo, la fecha y la vida útil; sin alguno de ellos dice «—».";

    public static boolean esCandidato(Producto p) {
        return p.getFechaBaja() == null && p.getEstadoFisico() != null
            && ESTADOS.contains(p.getEstadoFisico().trim().toUpperCase(Locale.ROOT));
    }

    /** The candidates by área (in the organigrama's order), the oldest first in each. */
    public static Map<String, List<Producto>> porArea(List<Producto> bienes) {
        List<String> organigrama = new ArrayList<>(com.sibim.config.Areas.getAllAreaNames());
        Map<String, List<Producto>> out = new TreeMap<>(Comparator
            .comparingInt((String a) -> organigrama.contains(a) ? organigrama.indexOf(a) : Integer.MAX_VALUE)
            .thenComparing(Comparator.naturalOrder()));
        for (Producto p : bienes)
            if (esCandidato(p))
                out.computeIfAbsent(p.getArea() == null || p.getArea().isBlank() ? "Sin área" : p.getArea(), k -> new ArrayList<>()).add(p);
        Comparator<Producto> masViejo = Comparator.comparing(Producto::getFechaAdquisicion, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO);
        out.values().forEach(l -> l.sort(masViejo));
        return out;
    }

    /** «13 años», «menos de 1 año», «—» without a fecha. */
    static String anosDeUso(Producto p, LocalDate hoy) {
        if (p.getFechaAdquisicion() == null) return "—";
        long a = ChronoUnit.YEARS.between(p.getFechaAdquisicion(), hoy);
        return a < 1 ? "menos de 1 año" : FormatUtils.plural(a, "año", "años");
    }

    /** «85 bienes en 12 áreas · costo $1,234.00 · valor en libros $567.00». */
    public static String resumen(Map<String, List<Producto>> porArea) {
        int n = porArea.values().stream().mapToInt(List::size).sum();
        BigDecimal costo = BigDecimal.ZERO, libros = BigDecimal.ZERO;
        for (List<Producto> l : porArea.values())
            for (Producto p : l) {
                costo = costo.add(p.getValorTotal());
                BigDecimal v = p.getValorDepreciadoTotal();
                if (v != null) libros = libros.add(v);
            }
        return FormatUtils.plural(n, "bien", "bienes") + " en estado MALO o DEFICIENTE, en " + FormatUtils.plural(porArea.size(), "área", "áreas")
            + " · costo " + FormatUtils.formatCurrency(costo) + " · valor en libros " + FormatUtils.formatCurrency(libros);
    }

    /** @return the PDF, or null when no bien is in a bad state */
    public File exportPdf(List<Producto> bienes) throws Exception {
        Map<String, List<Producto>> areas = porArea(bienes);
        if (areas.isEmpty()) return null;
        LocalDate hoy = LocalDate.now();
        File file = tempFile("candidatos_baja", ".pdf");
        int total = 0;
        try (PdfWriter writer = new PdfWriter(file.getAbsolutePath());
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf, PageSize.LETTER.rotate())) {
            String folio = generateFolio("CBA");
            addPdfHeader(doc, "Candidatos a baja", null, null, folio);
            doc.add(new Paragraph(resumen(areas)).setFontSize(10).setMarginTop(4).setMarginBottom(2));
            doc.add(new Paragraph(NOTA).setFontSize(8).setFontColor(new DeviceRgb(107, 114, 128)).setMarginBottom(6));
            for (var e : areas.entrySet()) {
                doc.add(new Paragraph(e.getKey() + " · " + FormatUtils.plural(e.getValue().size(), "bien", "bienes"))
                    .setFontSize(10).setBold().setMarginTop(8).setMarginBottom(3).setKeepWithNext(true));
                Table t = createPdfTable(COLUMNAS, new float[]{1.2f, 1.5f, 3.6f, 1.1f, 2.0f, 1.1f, 1.1f, 1.3f, 1.4f});
                int i = 0;
                for (Producto p : e.getValue()) {
                    boolean alt = (i++ % 2) == 1;
                    BigDecimal libros = p.getValorDepreciadoTotal();
                    String[] v = {p.getCodigo(), ProductoUtils.inventarioAnterior(p), p.getNombre(), p.getEstadoFisico().trim().toUpperCase(Locale.ROOT),
                        Objects.requireNonNullElse(p.getResguardante(), "—"),
                        p.getFechaAdquisicion() == null ? "—" : FormatUtils.formatDate(p.getFechaAdquisicion()), anosDeUso(p, hoy),
                        p.getPrecioCompra() == null || p.getPrecioCompra().signum() == 0 ? "—" : FormatUtils.formatCurrency(p.getValorTotal()),
                        libros == null ? "—" : FormatUtils.formatCurrency(libros)};
                    for (String x : v) t.addCell(fila(Objects.requireNonNullElse(x, ""), alt).setKeepTogether(true));
                }
                alinearDerecha(t, 6, 7, 8);
                doc.add(t);
                total += e.getValue().size();
            }
            addPdfFooter(doc, total, folio);
        }
        return numerarPaginas(file);
    }

    public File exportExcel(List<Producto> bienes) throws Exception {
        Map<String, List<Producto>> areas = porArea(bienes);
        if (areas.isEmpty()) return null;
        LocalDate hoy = LocalDate.now();
        File file = tempFile("candidatos_baja", ".xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet hoja = createSheet(wb, "Candidatos a baja");
            String[] titulos = {"Área", "Número", "Número anterior", "Bien", "Marca", "Modelo", "Serie", "Estado", "Ubicación",
                "Resguardante", "Fecha de adquisición", "Años de uso", "Costo", "Valor en libros"};
            writeHeader(hoja, titulos, wb);
            int n = 1;
            for (var e : areas.entrySet())
                for (Producto p : e.getValue()) {
                    Row row = hoja.createRow(n++);
                    BigDecimal libros = p.getValorDepreciadoTotal();
                    String[] v = {e.getKey(), p.getCodigo(), ProductoUtils.inventarioAnterior(p), p.getNombre(), p.getMarca(), p.getModelo(),
                        p.getNumeroSerie(), p.getEstadoFisico().trim().toUpperCase(Locale.ROOT), p.getUbicacion(), p.getResguardante(),
                        p.getFechaAdquisicion() == null ? "" : FormatUtils.formatDate(p.getFechaAdquisicion()), anosDeUso(p, hoy)};
                    for (int c = 0; c < v.length; c++) row.createCell(c).setCellValue(Objects.requireNonNullElse(v[c], ""));
                    if (p.getPrecioCompra() != null && p.getPrecioCompra().signum() > 0) row.createCell(12).setCellValue(p.getValorTotal().doubleValue());
                    if (libros != null) row.createCell(13).setCellValue(libros.doubleValue());
                }
            autosizeColumns(hoja, titulos.length);
            addExcelInfoSheet(wb, "Candidatos a baja", null, null);
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        return file;
    }
}
