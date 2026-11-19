package com.sibim.service;

import com.sibim.model.AuditLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A bitácora entry read for the Auditoría screen: the bien's número and área and each change,
 * out of the text {@link ProductoService} writes («Datos del bien actualizados · número SSPCR/19 ·
 * Seguridad Pública — Central de Radio · Cambió: color (sin dato → GRIS), vida útil (sin dato → 3)»).
 * On 19-nov-2026 the screen showed that text from its start, so «Datos del bien actualizados ·
 * número …» was all that fit, and what changed never did.
 */
public final class LecturaAuditoria {

    private LecturaAuditoria() {}

    /** One change; {@code antes} and {@code ahora} are null when the entry doesn't say them
     *  («descripción», «foto agregada»). «sin dato» reads as null too. */
    public record Cambio(String campo, String antes, String ahora) {
        /** «color: — → GRIS», «descripción». */
        public String texto() {
            if (antes == null && ahora == null) return campo;
            return campo + ": " + Objects.requireNonNullElse(antes, "—") + " → " + Objects.requireNonNullElse(ahora, "—");
        }
    }

    /** @param numero the bien's número when the entry says it, else null
     *  @param area   the área it names, else null
     *  @param cambios what changed, in order; empty when the entry isn't a list of changes */
    public record Lectura(String numero, String area, List<Cambio> cambios, String resumen) {}

    /** The fields {@link ProductoService} names, so a value with commas or parentheses inside
     *  («SILLA (NEGRA), 4 PATAS») doesn't cut a change in two. Longest first. */
    private static final List<String> CAMPOS = List.of(
        "fecha de adquisición", "tarjeta de circulación", "notas de mantenimiento", "marcado como etiquetado",
        "marcado sin etiqueta", "archivo de factura", "clave armonizada", "póliza de seguro", "próxima revisión",
        "valor residual", "estado físico", "folio fiscal", "tipo de bien", "no. de motor", "resguardante",
        "foto agregada", "foto quitada", "descripción", "ubicación", "categoría", "proveedor", "vida útil",
        "garantía", "factura", "nombre", "número", "unidad", "modelo", "marca", "serie", "color", "fotos", "foto",
        // The corrections of the October load also wrote these.
        "área");

    private static final Pattern NUMERO = Pattern.compile("·\\s*número\\s+([^·]+?)\\s*(?:·|$)");
    private static final Pattern AREA_RESGUARDO = Pattern.compile("Área:\\s*([^·(]+?)\\s*(?:·|\\(|$)");

    public static Lectura de(AuditLog a) {
        String d = Objects.requireNonNullElse(a.getDetalle(), "").trim();
        String numero = null, area = null;
        Matcher n = NUMERO.matcher(d);
        if (n.find()) numero = n.group(1).trim();
        List<Cambio> cambios = new ArrayList<>();
        int cambio = d.indexOf("Cambió:");
        if ("producto".equals(a.getEntidad())) {
            // «Datos del bien actualizados · número X · ÁREA · Cambió: …»: the área is the part after the número.
            String[] partes = (cambio >= 0 ? d.substring(0, cambio) : d).split("\\s*·\\s*");
            for (int i = 1; i < partes.length; i++)
                if (partes[i - 1].startsWith("número ") && !partes[i].isBlank()) { area = partes[i].trim(); break; }
        } else {
            Matcher m = AREA_RESGUARDO.matcher(d);
            if (m.find()) area = m.group(1).trim();
        }
        if (cambio >= 0) cambios = cambios(d.substring(cambio + "Cambió:".length()));
        String resumen;
        if (!cambios.isEmpty()) {
            resumen = String.join(" · ", cambios.stream().map(Cambio::texto).toList());
        } else if ("producto".equals(a.getEntidad()) && d.startsWith("Datos del bien actualizados")) {
            resumen = "Se guardó sin cambios";
        } else {
            // What the entry says, without what the other columns already say («· número X · área»).
            String areaDelBien = "producto".equals(a.getEntidad()) ? area : null;
            List<String> partes = new ArrayList<>();
            for (String p : d.split("\\s*·\\s*"))
                if (!p.startsWith("número ") && !p.equals(areaDelBien)) partes.add(p);
            resumen = String.join(" · ", partes);
        }
        return new Lectura(numero, area, cambios, resumen.trim());
    }

    /** «color (sin dato → GRIS), descripción, nombre (A, B → C)». */
    static List<Cambio> cambios(String texto) {
        List<Cambio> out = new ArrayList<>();
        String t = texto.trim();
        int i = 0;
        while (i < t.length()) {
            String campo = campoEn(t, i);
            if (campo == null) {   // not a known field: the rest as it is
                out.add(new Cambio(t.substring(i).trim(), null, null));
                break;
            }
            int fin = i + campo.length();
            int siguiente = siguienteCampo(t, fin);
            String cuerpo = t.substring(fin, siguiente < 0 ? t.length() : siguiente).trim();
            if (cuerpo.endsWith(",")) cuerpo = cuerpo.substring(0, cuerpo.length() - 1).trim();
            String nota = cuerpo;
            int cierre = cuerpo.startsWith("(") ? cierre(cuerpo) : -1;
            // A value cut at 40 characters can leave its own «(» open: then the last «)» closes.
            if (cierre < 0 && cuerpo.startsWith("(") && cuerpo.endsWith(")")) cierre = cuerpo.length() - 1;
            if (cierre > 0 && cuerpo.substring(1, cierre).contains(" → ")) {
                String dentro = cuerpo.substring(1, cierre);
                int flecha = dentro.lastIndexOf(" → ");
                out.add(new Cambio(campo, valor(dentro.substring(0, flecha)), valor(dentro.substring(flecha + 3))));
                nota = cuerpo.substring(cierre + 1);
            } else {
                out.add(new Cambio(campo, null, null));
            }
            // What a correction added after the last change («· venía de una hoja de 2023»), as its own line.
            nota = nota.replaceFirst("^[\\s·,;]+", "").trim();
            if (siguiente < 0 && !nota.isEmpty()) out.add(new Cambio(nota, null, null));
            if (siguiente < 0) break;
            i = siguiente;
        }
        return out;
    }

    /** The «)» that closes the «(» at 0, counting the ones inside a value («SILLA (NEGRA)»), or -1. */
    private static int cierre(String s) {
        int nivel = 0;
        for (int k = 0; k < s.length(); k++) {
            if (s.charAt(k) == '(') nivel++;
            else if (s.charAt(k) == ')' && --nivel == 0) return k;
        }
        return -1;
    }

    private static String campoEn(String t, int i) {
        for (String c : CAMPOS)
            if (t.startsWith(c, i)) {
                int fin = i + c.length();
                if (fin == t.length() || t.charAt(fin) == ' ' || t.charAt(fin) == ',') return c;
            }
        return null;
    }

    /** Where the next change starts: «, » followed by a known field and then « (», «, » or the end. */
    private static int siguienteCampo(String t, int desde) {
        int j = desde;
        while ((j = t.indexOf(", ", j)) >= 0) {
            String c = campoEn(t, j + 2);
            if (c != null) {
                int fin = j + 2 + c.length();
                if (fin == t.length() || t.startsWith(" (", fin) || t.startsWith(", ", fin)) return j + 2;
            }
            j += 2;
        }
        return -1;
    }

    private static String valor(String v) {
        String s = v.trim();
        return s.isEmpty() || s.equalsIgnoreCase("sin dato") ? null : s;
    }
}
