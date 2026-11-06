package com.sibim.config;

/**
 * Prefijo de nomenclatura de código patrimonial por área (ej. "TICS/01").
 *
 * Cada área real del municipio (ver {@link Areas}) tiene un prefijo corto y
 * único, guardado junto al área en la tabla {@code areas} ({@link AreaCatalog}).
 * El número dentro de cada prefijo es el siguiente al más alto que se haya
 * usado alguna vez con ese prefijo (ver ProductoService#asignarCodigo): un
 * número que ya tuvo un bien —activo, dado de baja, transferido a otra área o
 * borrado— no se vuelve a dar a otro bien. Reutilizarlos confundía etiquetas,
 * resguardos y actas impresas con el número viejo (decisión del 5-nov-2026;
 * antes el siguiente bien tomaba el menor número libre). La base guarda cada
 * código usado en la tabla {@code codigos_usados} (V34).
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

    /** El número siguiente al más alto de {@code codigosUsados} con el prefijo
     *  de {@code area}, formateado "PREF/NN": los huecos no se rellenan, un
     *  número que ya se usó no vuelve a salir. Única implementación de la
     *  regla — quien la llame solo decide de dónde salen los códigos usados
     *  (los bienes activos y dados de baja más los que ya no tiene ningún
     *  bien: una consulta dentro de su transacción, o el almacén local). Los
     *  códigos con sufijo no numérico (datos heredados) se ignoran. */
    public static String siguienteCodigo(String area, Iterable<String> codigosUsados) {
        String inicio = prefijo(area) + "/";
        int mayor = 0;
        for (String codigo : codigosUsados) {
            // Sin distinguir mayúsculas, como la validación de código repetido:
            // un "sgm/01" capturado a mano también ocupa el 01.
            if (codigo == null || !codigo.regionMatches(true, 0, inicio, 0, inicio.length())) continue;
            try { mayor = Math.max(mayor, Integer.parseInt(codigo.substring(inicio.length()).trim())); }
            catch (NumberFormatException ignored) { /* código heredado sin número */ }
        }
        return inicio + String.format("%02d", mayor + 1);
    }

    /** {@code preferido} si ningún bien activo lo tiene, si no {@link #siguienteCodigo}:
     *  un bien cuya transferencia se deshace, o que se reactiva, recupera su
     *  propio número (no es reutilizarlo: sigue siendo el mismo bien).
     *  @param codigosActivos los de los bienes activos, para ver si otro lo tiene
     *  @param codigosUsados todos los que se han usado, para el siguiente */
    public static String codigoPreferido(String area, String preferido, Iterable<String> codigosActivos,
                                         Iterable<String> codigosUsados) {
        if (preferido == null || preferido.isBlank()) return siguienteCodigo(area, codigosUsados);
        String buscado = preferido.trim();
        for (String codigo : codigosActivos)
            if (codigo != null && codigo.trim().equalsIgnoreCase(buscado)) return siguienteCodigo(area, codigosUsados);
        return buscado;
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
