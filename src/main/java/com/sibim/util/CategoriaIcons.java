package com.sibim.util;

import org.kordamp.ikonli.javafx.FontIcon;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Picks a Material Design icon for a category from words in its name.
 *
 *  Categories are free text created by each ayuntamiento ("Equipo de Cómputo
 *  y Tecnologías de Información", "Muebles de Oficina y Estantería"…), so an
 *  exact-name table only ever matched the demo data and every real category
 *  fell back to a generic tag. Rules are checked in order — more specific
 *  words first ("mueble" before "oficina", "electrodom" before "electric"). */
public final class CategoriaIcons {

    private CategoriaIcons() {}

    public static final String DEFAULT = "mdi2t-tag-outline";

    private static final List<Map.Entry<String[], String>> RULES = List.of(
        rule("mdi2l-laptop",                   "computo", "tecnolog", "informat"),
        rule("mdi2r-radio-tower",              "telecom", "comunicacion"),
        rule("mdi2c-camera-outline",           "audiovisual", "fotograf", "camara"),
        rule("mdi2s-stethoscope",              "medic", "instrumental"),
        rule("mdi2s-shield-outline",           "seguridad"),
        rule("mdi2f-fridge-outline",           "electrodomestic"),
        rule("mdi2a-air-conditioner",          "aire acondicionado", "calefaccion"),
        rule("mdi2f-flash-outline",            "generacion", "electric"),
        rule("mdi2t-tractor",                  "agropecuari"),
        rule("mdi2c-crane",                    "construccion"),
        rule("mdi2f-factory",                  "industrial"),
        rule("mdi2t-truck-outline",            "carroceria", "remolque"),
        rule("mdi2c-car-outline",              "vehiculo", "transporte"),
        rule("mdi2s-school-outline",           "educativ", "didactic"),
        rule("mdi2s-soccer",                   "deport"),
        rule("mdi2b-broom",                    "limpieza"),
        rule("mdi2o-office-building-outline",  "inmueble", "edificio"),
        rule("mdi2h-hammer-wrench",            "herramient"),
        rule("mdi2c-cog-outline",              "maquinaria", "maquina"),
        rule("mdi2d-desk",                     "mueble", "mobiliario", "estanteria"),
        rule("mdi2p-printer",                  "oficina"),
        rule("mdi2p-package-variant-closed",   "otros")
    );

    private static Map.Entry<String[], String> rule(String icon, String... words) {
        return Map.entry(words, icon);
    }

    /** Icon literal for a category name; {@link #DEFAULT} when nothing matches. */
    public static String literalFor(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) return DEFAULT;
        String n = Normalizer.normalize(categoryName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        for (var r : RULES) {
            for (String w : r.getKey()) {
                if (n.contains(w)) return r.getValue();
            }
        }
        return DEFAULT;
    }

    /** A FontIcon for the category — falls back to the tag icon rather than
     *  throwing if a literal is ever missing from the bundled icon pack. */
    public static FontIcon iconFor(String categoryName) {
        FontIcon icon = new FontIcon();
        try {
            icon.setIconLiteral(literalFor(categoryName));
        } catch (IllegalArgumentException e) {
            icon.setIconLiteral(DEFAULT);
        }
        return icon;
    }
}
