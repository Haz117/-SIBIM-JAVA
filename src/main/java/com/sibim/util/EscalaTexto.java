package com.sibim.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Texto: Grande / Extra" for every text in the app, not just the dozen
 * selectors 20-accesibilidad.css lists by hand. The stylesheet gives each
 * component an absolute size in px, so raising the base size reaches almost
 * nothing; instead this reads every rule that sets {@code -fx-font-size} in px
 * and writes the same rule, scaled, under {@code .text-scale-lg} and
 * {@code .text-scale-xl} — a stylesheet built once at startup, so it follows
 * any size someone changes in the CSS.
 *
 * <p>Add {@link #hoja()} BEFORE styles.css: its rules outrank the base ones
 * (one class more), and the hand-tuned {@code .text-scale-*} rules of
 * styles.css, equal to them and later, still win. Buttons grow less than
 * text (the action bars have little width to spare, see 20-accesibilidad.css)
 * but they do grow: "Hoy / Semana", "Expandir todo" or "Texto: Extra" stayed
 * at 11px for whoever asked for larger text. The sidebar's buttons keep their
 * size: its width is fixed.
 */
public final class EscalaTexto {

    private EscalaTexto() {}

    /** Same order as {@link AccessibilityUtils#TEXT_SCALE_CLASSES}, without "Normal". */
    static final String[] CLASES   = { "text-scale-lg", "text-scale-xl" };
    static final double[] FACTORES = { 1.15, 1.3 };
    /** Buttons: about half as much, so a bar of them still fits. */
    static final double[] FACTORES_BOTONES = { 1.08, 1.15 };

    private static final Pattern IMPORT = Pattern.compile("@import\\s+url\\(\"([^\"]+)\"\\)");
    private static final Pattern REGLA = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern TAMANO = Pattern.compile("-fx-font-size\\s*:\\s*([0-9.]+)px");
    /** A leading run of classes (".dialog-pane", ".a.b") followed by a combinator. */
    private static final Pattern PRIMER_SELECTOR = Pattern.compile("((?:\\.[A-Za-z0-9_-]+)+)(?=\\s|>)");

    private static volatile String hoja;

    /** The scaled rules as a stylesheet URL (data:), built on first use. */
    public static String hoja() {
        String h = hoja;
        if (h == null) {
            h = "data:text/css;base64," + Base64.getEncoder()
                .encodeToString(construir().getBytes(StandardCharsets.UTF_8));
            hoja = h;
        }
        return h;
    }

    /** The app stylesheet: the scaled rules first, then styles.css. */
    public static List<String> hojas() {
        var css = EscalaTexto.class.getResource("/css/styles.css");
        List<String> l = new ArrayList<>();
        l.add(hoja());
        if (css != null) l.add(css.toExternalForm());
        return l;
    }

    static String construir() {
        StringBuilder out = new StringBuilder();
        String principal = leer("/css/styles.css");
        Matcher m = IMPORT.matcher(principal);
        while (m.find()) out.append(escalar(leer("/css/" + m.group(1))));
        return out.toString();
    }

    /** For each rule of {@code css} with a size in px, the same rule under each scale class. */
    static String escalar(String css) {
        String sinComentarios = css.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder out = new StringBuilder();
        Matcher r = REGLA.matcher(sinComentarios);
        while (r.find()) {
            Matcher t = TAMANO.matcher(r.group(2));
            String px = null;
            while (t.find()) px = t.group(1);   // the last one is the one that applies
            if (px == null) continue;
            List<String> textos = new ArrayList<>();
            List<String> botones = new ArrayList<>();
            for (String s : r.group(1).split(",")) {
                s = s.trim().replaceAll("\\s+", " ");
                // .root alone is the base size, which 20-accesibilidad.css already scales.
                if (s.isEmpty() || s.contains("text-scale") || s.equals(".root")) continue;
                boolean boton = s.contains("btn") || s.contains("button");
                if (!boton) textos.add(s);
                else if (!s.contains("nav-btn") && !s.contains("sidebar")) botones.add(s);
            }
            double base = Double.parseDouble(px);
            escribir(out, textos, base, FACTORES);
            escribir(out, botones, base, FACTORES_BOTONES);
        }
        return out.toString();
    }

    /** {@code selectores} again under each scale class, at {@code base} times its factor. */
    private static void escribir(StringBuilder out, List<String> selectores, double base, double[] factores) {
        if (selectores.isEmpty()) return;
        for (int i = 0; i < CLASES.length; i++) {
            String clase = CLASES[i];
            List<String> escalados = new ArrayList<>();
            for (String s : selectores) {
                escalados.add("." + clase + " " + s);
                // A dialog carries the class on its DialogPane, the root of its
                // scene: ".dialog-pane .x" or ".root .x" must also match there.
                Matcher primero = PRIMER_SELECTOR.matcher(s);
                if (primero.lookingAt()) escalados.add(primero.group(1) + "." + clase + s.substring(primero.end(1)));
            }
            out.append(String.join(", ", escalados))
               .append(" { -fx-font-size: ").append(redondear(base * factores[i])).append("px; }\n");
        }
    }

    /** To the nearest half pixel, without a trailing ".0". */
    static String redondear(double px) {
        double r = Math.round(px * 2) / 2.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    private static String leer(String recurso) {
        try (InputStream in = EscalaTexto.class.getResourceAsStream(recurso)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
