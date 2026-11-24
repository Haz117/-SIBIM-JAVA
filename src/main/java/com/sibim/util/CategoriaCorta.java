package com.sibim.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * A categoría's name as tables and charts show it: the CONAC names are long and several start
 * alike, so at 1366 px the Bienes table read «Equipo de D…» and «Equipo de C…» (Defensa y
 * Seguridad, Cómputo, Comunicación) and Inicio cut «Equipo de Cómputo y de Tecn…» (19-nov-2026).
 * One word where it can be: the badge of the Bienes table fits about twelve letters bold, with
 * the categoría's icon beside it. The whole name stays in the tooltip, the form and every official format.
 */
public final class CategoriaCorta {

    private CategoriaCorta() {}

    /** The categorías of the inventory (CONAC), by their name without accents, in capitals. */
    private static final Map<String, String> CORTOS = Map.ofEntries(
        Map.entry("ACTIVOS BIOLOGICOS", "Biológicos"),
        Map.entry("APARATOS DEPORTIVOS", "Deportivos"),
        Map.entry("CAMARAS FOTOGRAFICAS Y DE VIDEO", "Cámaras"),
        Map.entry("EQUIPO DE COMPUTO Y DE TECNOLOGIAS DE LA INFORMACION", "Cómputo"),
        Map.entry("EQUIPO DE COMUNICACION Y TELECOMUNICACION", "Comunicación"),
        Map.entry("EQUIPO DE DEFENSA Y SEGURIDAD", "Defensa"),
        Map.entry("EQUIPO MEDICO Y DE LABORATORIO", "Médico"),
        Map.entry("EQUIPOS Y APARATOS AUDIOVISUALES", "Audiovisuales"),
        Map.entry("HERRAMIENTAS Y MAQUINAS-HERRAMIENTA", "Herramientas"),
        Map.entry("MAQUINARIA Y EQUIPO DE CONSTRUCCION", "Construcción"),
        Map.entry("MAQUINARIA Y EQUIPO INDUSTRIAL", "Industrial"),
        Map.entry("MUEBLES DE OFICINA Y ESTANTERIA", "Muebles"),
        Map.entry("OTRO MOBILIARIO Y EQUIPO EDUCACIONAL Y RECREATIVO", "Educativo"),
        Map.entry("OTROS EQUIPOS", "Otros equipos"),
        Map.entry("OTROS EQUIPOS DE TRANSPORTE", "Transporte"),
        Map.entry("OTROS MOBILIARIOS Y EQUIPOS DE ADMINISTRACION", "Mobiliario"),
        Map.entry("SISTEMAS DE AIRE ACONDICIONADO, CALEFACCION Y REFRIGERACION", "Climas"),
        Map.entry("VEHICULOS Y EQUIPO TERRESTRE", "Vehículos"));

    /** Up to this many characters a name is short enough as it is. */
    static final int CORTO = 20;

    /** The short name; one SIBIM doesn't know is cut before its first «y» or comma when long. */
    public static String de(String nombre) {
        if (nombre == null) return "";
        String n = nombre.trim();
        String conocido = CORTOS.get(clave(n));
        if (conocido != null) return conocido;
        if (n.length() <= CORTO) return n;
        String s = n.replaceFirst("(?i)^(equipos? (de )?|sistemas de |otros? )", "");
        int corte = s.length();
        for (String sep : new String[]{" y ", ", "}) {
            int i = s.indexOf(sep);
            if (i >= 4) corte = Math.min(corte, i);
        }
        s = s.substring(0, corte).trim();
        return s.isEmpty() ? n : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String clave(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
