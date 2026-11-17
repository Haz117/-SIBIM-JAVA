package com.sibim.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The color a bien's name or description already says («SILLA … TAPIZ EN TELA COLOR NEGRO»):
 *  on 13-nov-2026, 820 of the 2,493 bienes without color had it written there, and it agreed
 *  with 125 of the 139 colors typed by hand (the rest, «CROMADO» where «ACERO INOXIDABLE»
 *  was written). «COLOR X» first, with its shade or a second color («AZUL MARINO», «CAOBA /
 *  NEGRO»); else a color word by itself, except the ones that are often something else
 *  (café, vino, crema, arena…) and «LUZ BLANCA», «LÍNEA BLANCA», «PINTURA BLANCA». Written
 *  as the inventory writes it: capitals, masculine, no accents («NEGRA» → «NEGRO»). */
public final class ColorEnNombre {

    private ColorEnNombre() {}

    private static final Map<String, String> COLORES = new HashMap<>();
    static {
        Map<String, String> formas = Map.ofEntries(
            Map.entry("NEGRO", "NEGRO NEGRA NEGROS NEGRAS"), Map.entry("BLANCO", "BLANCO BLANCA BLANCOS BLANCAS"),
            Map.entry("GRIS", "GRIS GRISES"), Map.entry("ROJO", "ROJO ROJA ROJOS ROJAS"), Map.entry("AZUL", "AZUL AZULES"),
            Map.entry("VERDE", "VERDE VERDES"), Map.entry("AMARILLO", "AMARILLO AMARILLA AMARILLOS AMARILLAS"),
            Map.entry("CAFE", "CAFE"), Map.entry("BEIGE", "BEIGE"), Map.entry("VINO", "VINO"), Map.entry("CREMA", "CREMA"),
            Map.entry("NOGAL", "NOGAL"), Map.entry("CHOCOLATE", "CHOCOLATE"), Map.entry("CAOBA", "CAOBA"),
            Map.entry("ARENA", "ARENA"), Map.entry("NARANJA", "NARANJA"), Map.entry("PLATA", "PLATA"),
            Map.entry("PLATEADO", "PLATEADO PLATEADA"), Map.entry("DORADO", "DORADO DORADA"),
            Map.entry("CROMADO", "CROMADO CROMADA CROMADOS CROMADAS"), Map.entry("TRANSPARENTE", "TRANSPARENTE"),
            Map.entry("GRAFITO", "GRAFITO"), Map.entry("MAPLE", "MAPLE"), Map.entry("MORADO", "MORADO MORADA"),
            Map.entry("ROSA", "ROSA"), Map.entry("GUINDA", "GUINDA"), Map.entry("TURQUESA", "TURQUESA"),
            Map.entry("MARFIL", "MARFIL"), Map.entry("HUESO", "HUESO"), Map.entry("ROBLE", "ROBLE"),
            Map.entry("CEREZO", "CEREZO"), Map.entry("MARRON", "MARRON"));
        formas.forEach((color, escritos) -> { for (String f : escritos.split(" ")) COLORES.put(f, color); });
    }

    /** What may follow the color after «COLOR»: «AZUL MARINO», «CAFÉ CLARO», «VERDE LIMÓN». */
    private static final Set<String> MATICES = Set.of("MARINO", "CLARO", "OSCURO", "METALICO", "MATE", "OXFORD", "REY",
        "CIELO", "PASTEL", "LIMON", "BANDERA", "OLIVO", "MILITAR", "PERLA", "CHOCOLATE");

    /** Only after «COLOR»: alone they are often something else (a coffee maker, wine glasses…). */
    private static final Set<String> SOLO_CON_COLOR = Set.of("CAFE", "VINO", "CREMA", "ARENA", "NOGAL", "CHOCOLATE", "ROSA",
        "HUESO", "ROBLE", "CEREZO", "MAPLE", "NARANJA", "PLATA", "MARRON", "GUINDA");

    /** The word before that says it is not the bien's color: «LUZ BLANCA», «LÍNEA BLANCA», «TINTA NEGRA». */
    private static final Set<String> ANTES_NO = Set.of("LINEA", "PINTURA", "TINTA", "LOMA", "HILO", "LUZ", "LUCES", "LED");

    private static final Pattern FRASE = Pattern.compile(
        "\\bCOLOR(?:ES)?\\s*:?\\s+([A-Z]+)((?:\\s*(?:/|\\bY\\b|\\s)\\s*[A-Z]+){0,3})");
    private static final Pattern PIEZA = Pattern.compile("/|\\bY\\b|[A-Z]+");
    private static final Pattern PALABRA = Pattern.compile("[A-Z]+");

    /** @return the color, or null when neither says one */
    public static String de(String nombre, String descripcion) {
        for (String texto : new String[]{nombre, descripcion}) {
            String c = deFrase(plano(texto));
            if (c != null) return c;
        }
        for (String texto : new String[]{nombre, descripcion}) {
            String c = suelto(plano(texto));
            if (c != null) return c;
        }
        return null;
    }

    /** «… COLOR AZUL MARINO …», «COLOR CAOBA/NEGRO». */
    private static String deFrase(String t) {
        Matcher m = FRASE.matcher(t);
        while (m.find()) {
            String primero = COLORES.get(m.group(1));
            if (primero == null) continue;
            List<String> partes = new ArrayList<>(List.of(primero));
            List<String> resto = new ArrayList<>();
            Matcher p = PIEZA.matcher(m.group(2));
            while (p.find()) resto.add(p.group());
            int colores = 1;
            for (int i = 0; i < resto.size(); i++) {
                String w = resto.get(i);
                String ultimo = partes.get(partes.size() - 1);
                if ((w.equals("/") || w.equals("Y")) && i + 1 < resto.size() && COLORES.containsKey(resto.get(i + 1)) && colores < 2) {
                    partes.add("/");
                    partes.add(COLORES.get(resto.get(i + 1)));
                    colores++;
                    i++;
                } else if (MATICES.contains(w) && !ultimo.equals("/") && !w.equals(ultimo)) {
                    partes.add(w);
                } else {
                    break;
                }
            }
            return String.join(" ", partes);
        }
        return null;
    }

    /** A color word by itself; two different ones as «NEGRO / GRIS». */
    private static String suelto(String t) {
        List<String> palabras = new ArrayList<>();
        Matcher m = PALABRA.matcher(t);
        while (m.find()) palabras.add(m.group());
        List<String> hallados = new ArrayList<>();
        for (int i = 0; i < palabras.size() && hallados.size() < 2; i++) {
            String w = palabras.get(i);
            String c = COLORES.get(w);
            if (c == null || SOLO_CON_COLOR.contains(w)) continue;
            if (i > 0 && ANTES_NO.contains(palabras.get(i - 1))) continue;
            if (!hallados.contains(c)) hallados.add(c);
        }
        return hallados.isEmpty() ? null : String.join(" / ", hallados);
    }

    private static String plano(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }
}
