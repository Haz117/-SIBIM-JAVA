package com.sibim.config;

/**
 * Prefijo de nomenclatura de código patrimonial por área (ej. "TICS/01").
 *
 * Cada área real del municipio (ver {@link Areas}) tiene un prefijo corto y
 * único, guardado junto al área en la tabla {@code areas} ({@link AreaCatalog}).
 * El número dentro de cada prefijo se calcula dinámicamente como el
 * menor entero positivo no usado por ningún bien activo con ese prefijo
 * (ver ProductoService#asignarCodigo) — no se guarda un contador aparte, así
 * que cuando un bien se transfiere o se da de baja, su número queda libre
 * para el siguiente bien nuevo en esa área.
 */
public final class AreaCodigos {

    private AreaCodigos() {}

    /** @throws IllegalArgumentException si el área no es una de las áreas reales del municipio. */
    public static String prefijo(String area) {
        return Areas.catalogo().buscar(area).map(AreaCatalog.Entrada::prefijo)
            .orElseThrow(() -> new IllegalArgumentException("Área sin prefijo de nomenclatura asignado: " + area));
    }

    public static boolean tienePrefijo(String area) {
        return area != null && Areas.catalogo().buscar(area).isPresent();
    }

    /** Menor número positivo no usado entre {@code codigosActivos} con el
     *  prefijo de {@code area}, formateado "PREF/NN". Única implementación de
     *  la regla — quien la llame solo decide de dónde salen los códigos
     *  (una consulta dentro de su transacción, o el almacén local). Los
     *  códigos con sufijo no numérico (datos heredados) se ignoran. */
    public static String siguienteCodigo(String area, Iterable<String> codigosActivos) {
        String prefijo = prefijo(area);
        String inicio = prefijo + "/";
        java.util.Set<Integer> usados = new java.util.HashSet<>();
        for (String codigo : codigosActivos) {
            // Sin distinguir mayúsculas, como la validación de código repetido:
            // un "sgm/01" capturado a mano también ocupa el 01.
            if (codigo == null || !codigo.regionMatches(true, 0, inicio, 0, inicio.length())) continue;
            try { usados.add(Integer.parseInt(codigo.substring(inicio.length()).trim())); }
            catch (NumberFormatException ignored) { /* código heredado sin número */ }
        }
        int numero = 1;
        while (usados.contains(numero)) numero++;
        return inicio + String.format("%02d", numero);
    }

    /** {@code codigo} escrito como lo escribe {@link #siguienteCodigo}
     *  ("tics/5 " → "TICS/05"), o vacío si no es PREFIJO/número de
     *  {@code area}: un código corregido a mano no sale de la nomenclatura,
     *  y "TICS/5" no convive con "TICS/05" como si fueran dos números. */
    public static java.util.Optional<String> normalizar(String area, String codigo) {
        if (codigo == null) return java.util.Optional.empty();
        String prefijo = prefijo(area);
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile(java.util.regex.Pattern.quote(prefijo) + "\\s*/\\s*0*([0-9]{1,6})", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(codigo.trim());
        if (!m.matches()) return java.util.Optional.empty();
        int numero = Integer.parseInt(m.group(1));
        return numero < 1 ? java.util.Optional.empty()
                          : java.util.Optional.of(prefijo + "/" + String.format("%02d", numero));
    }
}
