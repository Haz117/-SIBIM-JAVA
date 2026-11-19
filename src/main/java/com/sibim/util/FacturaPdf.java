package com.sibim.util;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The PDF of a factura (the printed CFDI), read for what its XML would say when only the PDF
 * is at hand (19-nov-2026: Patrimonio has the facturas in PDF, not XML). What every printed CFDI
 * carries is read for sure: the folio fiscal (UUID), the fecha and the total; the emisor's RFC and
 * name when the PDF says them clearly, since each proveedor lays its PDF out its own way (two
 * columns put the emisor and the receptor on one line). The conceptos are not read: their tables
 * come out of the PDF as loose text. A scanned PDF has no text and is not a factura here.
 */
public final class FacturaPdf {

    private FacturaPdf() {}

    private static final String UUID_RE = "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}";
    /** The SAT's string of the timbre: «||1.1|UUID|2024-07-19T10:16:41|RFC del PAC|…». */
    private static final Pattern CADENA = Pattern.compile("\\|\\|1\\.[01]\\|(" + UUID_RE + ")\\|(\\d{4}-\\d{2}-\\d{2})T[\\d:]{8}\\|([A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3})?");
    private static final Pattern UUID = Pattern.compile("(?<![0-9A-Fa-f-])(" + UUID_RE + ")(?![0-9A-Fa-f-])");
    private static final Pattern RFC = Pattern.compile("(?<![A-ZÑ&0-9])([A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3})(?![A-Z0-9])");
    private static final Pattern ISO = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})T\\d{2}:\\d{2}(:\\d{2})?");
    private static final Pattern DMY = Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})(?!\\d)");
    private static final String MONTO = "\\$?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})(?!\\d)";
    private static final Pattern TOTAL = Pattern.compile("(?i)(?<![a-zá])total\\s*:?\\s*(?:MXN|M\\.N\\.)?\\s*" + MONTO);
    /** «Nombre emisor: X», «Razón social: X», «Emisor: X». */
    private static final Pattern NOMBRE = Pattern.compile(
        "(?im)^[ \\t]*(?:nombre\\s+(?:del\\s+)?emisor|raz[oó]n\\s+social(?:\\s+del\\s+emisor)?|emisor)\\s*:?[ \\t]*(.*)$");
    /** «<cfdi:Emisor Rfc="…" Nombre="…" …/>» in a PDF that prints the XML. */
    private static final Pattern XML_EMISOR = Pattern.compile("(?i)<\\s*cfdi:Emisor\\b([^>]*)>");
    private static final Pattern XML_COMPROBANTE = Pattern.compile("(?i)<\\s*cfdi:Comprobante\\b([^>]*)>");

    private static String atributo(String atributos, String nombre) {
        Matcher m = Pattern.compile("(?i)\\b" + nombre + "\\s*=\\s*\"([^\"]*)\"").matcher(atributos);
        if (!m.find() || m.group(1).isBlank()) return null;
        return m.group(1).replace("&amp;", "&").replace("&quot;", "\"").trim();
    }

    /** The generic RFCs of the SAT (público en general, extranjero) and its own. */
    private static final Set<String> GENERICOS = Set.of("XAXX010101000", "XEXX010101000", "SAT970701NN3");

    /** The text of every page, in order. */
    public static String texto(Path pdf) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (PdfDocument d = new PdfDocument(new PdfReader(pdf.toFile()))) {
            for (int i = 1; i <= d.getNumberOfPages(); i++) sb.append(PdfTextExtractor.getTextFromPage(d.getPage(i))).append('\n');
        } catch (com.itextpdf.kernel.exceptions.PdfException e) {
            throw new IOException("No es un PDF que se pueda abrir: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    /** @see #leerTexto */
    public static FacturaXml.Factura leer(Path pdf, Collection<String> rfcsAjenos, Collection<String> nombresPropios)
            throws IOException, FacturaXml.NoEsFactura {
        return leerTexto(texto(pdf), rfcsAjenos, nombresPropios);
    }

    /**
     * @param rfcsAjenos     RFCs that are not the emisor's: the municipio's (the receptor) and the
     *                       ones a whole folder repeats; may be empty
     * @param nombresPropios words that name the receptor («IXMIQUILPAN»): a line with them is not the emisor
     */
    public static FacturaXml.Factura leerTexto(String texto, Collection<String> rfcsAjenos, Collection<String> nombresPropios)
            throws FacturaXml.NoEsFactura {
        if (texto == null || texto.isBlank())
            throw new FacturaXml.NoEsFactura("El PDF no tiene texto (¿es una imagen escaneada?): no se puede leer");
        String t = texto.replace(' ', ' ');
        Matcher c = CADENA.matcher(t.replaceAll("\\s*\\n\\s*", ""));
        String uuid = null, pac = null;
        LocalDate timbrado = null;
        if (c.find()) {
            uuid = c.group(1);
            timbrado = fecha(c.group(2));
            pac = c.group(3);
        }
        if (uuid == null) {
            Matcher u = UUID.matcher(t);
            if (u.find()) uuid = u.group(1);
        }
        if (uuid == null) throw new FacturaXml.NoEsFactura("No se encontró el folio fiscal (UUID): no parece una factura electrónica");
        uuid = uuid.toUpperCase(Locale.ROOT);

        Set<String> ajenos = new HashSet<>(GENERICOS);
        if (rfcsAjenos != null) rfcsAjenos.forEach(r -> ajenos.add(r.toUpperCase(Locale.ROOT)));
        if (pac != null) ajenos.add(pac);
        List<String> propios = nombresPropios == null ? List.of()
            : nombresPropios.stream().filter(n -> n != null && !n.isBlank()).map(FacturaPdf::mayus).toList();

        // The XML itself printed as a PDF: its attributes say it all.
        String unido = t.replaceAll("\\s*\\n\\s*", " ");
        Matcher x = XML_EMISOR.matcher(unido), comp = XML_COMPROBANTE.matcher(unido);
        if (x.find() && comp.find()) {
            String total = atributo(comp.group(1), "Total"), fecha = atributo(comp.group(1), "Fecha");
            return new FacturaXml.Factura(uuid, null, fecha != null && fecha.length() >= 10 ? fecha(fecha.substring(0, 10)) : timbrado,
                atributo(x.group(1), "Nombre"), atributo(x.group(1), "Rfc"), moneda(t),
                total != null && total.matches("\\d+(\\.\\d+)?") ? new BigDecimal(total) : null, List.of());
        }
        BigDecimal total = total(t);
        if (timbrado == null && total == null)
            throw new FacturaXml.NoEsFactura("Dice un folio fiscal pero no el total ni el timbre del SAT: no parece la factura");
        String rfc = rfcEmisor(t, ajenos);
        String emisor = nombreEmisor(t, rfc, propios);
        return new FacturaXml.Factura(uuid, null, fechaDeEmision(t, timbrado), emisor, rfc, moneda(t), total, List.of());
    }

    /** The first RFC after the word «Emisor» (and before «Receptor» when both columns are on one
     *  line it is the first of the two), or the first that isn't the receptor's or the PAC's. */
    static String rfcEmisor(String t, Set<String> ajenos) {
        String u = mayus(t);
        int emisor = u.indexOf("EMISOR");
        if (emisor >= 0) {
            Matcher m = RFC.matcher(u);
            if (m.find(emisor) && !ajenos.contains(m.group(1)) && m.start() - emisor < 600) return m.group(1);
        }
        Matcher m = RFC.matcher(u);
        while (m.find()) if (!ajenos.contains(m.group(1))) return m.group(1);
        return null;
    }

    /** The emisor's name when the PDF says it clearly: after its label (cut at «RFC» or at the
     *  receptor's column), or the line above or below its RFC. Null when unsure. */
    static String nombreEmisor(String t, String rfc, List<String> propios) {
        String[] lineas = t.split("\\R");
        Matcher m = NOMBRE.matcher(t);
        while (m.find()) {
            String resto = m.group(1);
            String nombre = limpiarNombre(resto, propios);
            if (nombre == null && resto.trim().matches("(?i)(receptor\\s*:?\\s*)?")) {
                // Labels on one line, values on the next («Emisor: Receptor:» then both names).
                int linea = t.substring(0, m.end()).split("\\R", -1).length;
                for (int i = linea; i < Math.min(lineas.length, linea + 2) && nombre == null; i++)
                    nombre = limpiarNombre(lineas[i], propios);
            }
            if (nombre != null) return nombre;
        }
        if (rfc != null) {
            for (int i = 0; i < lineas.length; i++) {
                if (!mayus(lineas[i]).contains(rfc)) continue;
                String enLinea = limpiarNombre(mayus(lineas[i]).replace(rfc, " ").replaceAll("(?i)\\bRFC\\b\\s*:?", " "), propios);
                if (enLinea != null) return enLinea;
                if (i > 0 && limpiarNombre(lineas[i - 1], propios) != null) return limpiarNombre(lineas[i - 1], propios);
            }
        }
        return null;
    }

    /** A line as a proveedor's name, or null when it isn't one: cut at «RFC», at the receptor's
     *  label or name, without labels, and with three letters at least. */
    static String limpiarNombre(String linea, List<String> propios) {
        if (linea == null) return null;
        String s = linea.replace(' ', ' ').trim();
        s = s.replaceAll("(?i)\\s*\\b(R\\.?F\\.?C\\.?|receptor|r[eé]gimen|domicilio|c\\.?p\\.?\\s*:|lugar de expedici[oó]n|folio)\\b.*$", "");
        String u = mayus(s);
        for (String p : propios) {
            int i = u.indexOf(p);
            if (i >= 0) {
                // «PROVEEDOR SA DE CV MUNICIPIO DE IXMIQUILPAN»: the receptor's column starts before its name.
                int corte = i;
                for (String antes : List.of("MUNICIPIO DE ", "H. AYUNTAMIENTO", "AYUNTAMIENTO", "PRESIDENCIA MUNICIPAL", "GOBIERNO MUNICIPAL")) {
                    int j = u.lastIndexOf(antes, i);
                    if (j >= 0 && j < corte) corte = j;
                }
                s = s.substring(0, corte);
                u = mayus(s);
            }
        }
        // An RFC on the line (two columns: «RFC-EMISOR RFC-RECEPTOR») is not part of the name.
        Matcher r = RFC.matcher(mayus(s));
        StringBuilder sinRfc = new StringBuilder(s);
        while (r.find()) for (int k = r.start(); k < r.end(); k++) sinRfc.setCharAt(k, ' ');
        s = sinRfc.toString();
        if (s.matches(".*[<>=\"].*")) return null;
        s = s.replaceAll("[\\s:;,·|-]+$", "").replaceAll("^[\\s:;,·|-]+", "").replaceAll("\\s{2,}", " ");
        if (s.replaceAll("[^A-Za-zÁÉÍÓÚÑáéíóúñ]", "").length() < 3) return null;
        if (RFC.matcher(mayus(s)).matches() || UUID.matcher(s).find()) return null;
        return s;
    }

    /** The date after «emisión» (ISO or day/month/year), else the timbre's, else the first ISO date. */
    static LocalDate fechaDeEmision(String t, LocalDate timbrado) {
        String u = t.toLowerCase(Locale.ROOT);
        int e = u.indexOf("emisi");
        if (e >= 0) {
            String cerca = t.substring(e, Math.min(t.length(), e + 250));
            Matcher iso = ISO.matcher(cerca);
            if (iso.find()) return fecha(iso.group(1));
            Matcher dmy = DMY.matcher(cerca);
            if (dmy.find()) {
                LocalDate d = dma(dmy);
                if (d != null) return d;
            }
        }
        if (timbrado != null) return timbrado;
        Matcher iso = ISO.matcher(t);
        return iso.find() ? fecha(iso.group(1)) : null;
    }

    /** The largest amount labelled «Total» (not «Subtotal»): the total is never below the rest. */
    static BigDecimal total(String t) {
        BigDecimal max = null;
        Matcher m = TOTAL.matcher(t);
        while (m.find()) {
            BigDecimal v = new BigDecimal(m.group(1).replace(",", ""));
            if (max == null || v.compareTo(max) > 0) max = v;
        }
        return max;
    }

    static String moneda(String t) {
        String u = mayus(t);
        return u.contains("USD") || u.contains("DOLAR AMERICANO") || u.contains("DOLARES") ? "USD" : "MXN";
    }

    private static LocalDate fecha(String iso) {
        try { return LocalDate.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE); } catch (RuntimeException e) { return null; }
    }

    private static LocalDate dma(Matcher m) {
        try { return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))); }
        catch (RuntimeException e) { return null; }
    }

    static String mayus(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT);
    }
}
