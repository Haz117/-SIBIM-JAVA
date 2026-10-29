package com.sibim.config;

/** Vida útil of a bien by its CONAC account, from the Parámetros de Estimación de
 *  Vida Útil of the CONAC (DOF 15-ago-2012): what the bien form suggests when a
 *  category is picked. Equipo de defensa y seguridad, colecciones and activos
 *  biológicos have no single value there, so nothing is suggested for them. */
public final class VidaUtilConac {

    private VidaUtilConac() {}

    /** Most specific prefix first. */
    private static final String[][] PARAMETROS = {
        {"1.2.4.1.3", "3"},    // Equipo de cómputo y de tecnologías de la información
        {"1.2.4.1",   "10"},   // Mobiliario y equipo de administración
        {"1.2.4.2.1", "3"},    // Equipos y aparatos audiovisuales
        {"1.2.4.2.3", "3"},    // Cámaras fotográficas y de video
        {"1.2.4.2",   "5"},    // Aparatos deportivos, otro mobiliario educacional y recreativo
        {"1.2.4.3",   "5"},    // Equipo e instrumental médico y de laboratorio
        {"1.2.4.4",   "5"},    // Equipo de transporte
        {"1.2.4.6",   "10"},   // Maquinaria, otros equipos y herramientas
    };

    /** Years for the CONAC code (e.g. "1.2.4.1.3.515" → 3), or null when there is none. */
    public static Integer anios(String codigoConac) {
        if (codigoConac == null) return null;
        String c = codigoConac.trim();
        for (String[] p : PARAMETROS)
            if (c.equals(p[0]) || c.startsWith(p[0] + ".")) return Integer.valueOf(p[1]);
        return null;
    }
}
