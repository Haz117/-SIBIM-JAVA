package com.sibim.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A person's name without the title in front: "LIC. ANA RUIZ" → "ANA RUIZ",
 * "ING.ARQ. JUAN PÉREZ" → "JUAN PÉREZ", "L.A.P TAURINO CRUZ" → "TAURINO CRUZ".
 * The same person was written with and without it in bienes, resguardos and
 * titulares; from 5-nov-2026 only the name is kept (decided by Patrimonio).
 * Only what comes BEFORE the name is a title: "JUAN C. PÉREZ" keeps its C.
 */
public final class NombrePersona {

    private NombrePersona() {}

    /** Titles and ranks written before a name, without dots, in capitals and without accents. */
    public static final Set<String> TITULOS = Set.of(
        "LIC", "LICDA", "LICENCIADO", "LICENCIADA", "LD", "LAP", "LAE", "LCP", "AP", "CP", "CPA",
        "ING", "INGA", "INGE", "INGENIERO", "INGENIERA", "ARQ", "ARQUI", "ARQUITECTO", "ARQUITECTA",
        "DR", "DRA", "DOCTOR", "DOCTORA", "MTRO", "MTRA", "MAESTRO", "MAESTRA", "MC",
        "PROF", "PROFA", "PROFR", "PROFRA", "PROFESOR", "PROFESORA",
        "C", "TSU", "MVZ", "QFB", "ENF", "ABOG", "SR", "SRA", "SRITA",
        "COMANDANTE", "CMDTE", "ENCARGADA", "ENCARGADO");

    /** What can't start a name: "ENCARGADO DE ALMACÉN" is a puesto, not a person with a title. */
    private static final Pattern CONECTOR = Pattern.compile("(?iu)(DE|DEL|LA|LAS|LOS|EL|Y|EN)\\b");

    /** {@code nombre} without the titles in front of each person (two people go
     *  as "A / B"); the very same String when it has none, null for null. */
    public static String sinTitulos(String nombre) {
        if (nombre == null || nombre.isBlank()) return nombre;
        String[] partes = nombre.split("/", -1);
        StringBuilder sb = new StringBuilder();
        boolean cambio = false;
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) sb.append('/');
            String parte = partes[i];
            int ini = 0, fin = parte.length();
            while (ini < fin && Character.isWhitespace(parte.charAt(ini))) ini++;
            while (fin > ini && Character.isWhitespace(parte.charAt(fin - 1))) fin--;
            String nucleo = parte.substring(ini, fin);
            String limpio = sinTituloUno(nucleo);
            if (!limpio.equals(nucleo)) cambio = true;
            // The spaces around "/" stay as they were; the outer ones go.
            sb.append(i > 0 ? parte.substring(0, ini) : "").append(limpio)
              .append(i < partes.length - 1 ? parte.substring(fin) : "");
        }
        return cambio ? sb.toString() : nombre;
    }

    /** Whether {@link #sinTitulos} would change it. */
    public static boolean tieneTitulo(String nombre) {
        return nombre != null && !sinTitulos(nombre).equals(nombre);
    }

    private static String sinTituloUno(String s) {
        String resto = s;
        while (true) {
            int corte = largoDelTitulo(resto);
            if (corte == 0) return resto;
            String despues = resto.substring(corte).replaceFirst("^[\\s.:,]+", "");
            // Nothing left, or a puesto ("ENCARGADO DE…"): it wasn't a title before a name.
            if (despues.isEmpty() || !Character.isLetter(despues.charAt(0)) || CONECTOR.matcher(despues).lookingAt())
                return resto;
            resto = despues;
        }
    }

    /** Length of the longest title at the start of {@code s} that ends where a
     *  word does (a space, or after a dot or colon: "LIC.LEIDY"); 0 if none. */
    private static int largoDelTitulo(String s) {
        int mejor = 0;
        int limite = Math.min(s.length(), 24);
        for (int i = 1; i <= limite; i++) {
            boolean frontera = i == s.length() ? false
                : Character.isWhitespace(s.charAt(i)) || s.charAt(i - 1) == '.' || s.charAt(i - 1) == ':';
            if (!frontera) continue;
            String clave = clave(s.substring(0, i));
            if (clave.length() > 12) break;
            if (TITULOS.contains(clave)) mejor = i;
        }
        return mejor;
    }

    private static String clave(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .replaceAll("[\\s.:,]", "").toUpperCase(Locale.ROOT);
    }
}
