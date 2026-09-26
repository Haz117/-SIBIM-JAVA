package com.sibim.config;

import java.util.Map;

/**
 * Prefijo de nomenclatura de código patrimonial por área (ej. "TICS/01").
 *
 * Cada área real del municipio (ver {@link Areas}) tiene un prefijo corto y
 * único. El número dentro de cada prefijo se calcula dinámicamente como el
 * menor entero positivo no usado por ningún bien activo con ese prefijo
 * (ver ProductoService#asignarCodigo) — no se guarda un contador aparte, así
 * que cuando un bien se transfiere o se da de baja, su número queda libre
 * para el siguiente bien nuevo en esa área.
 */
public final class AreaCodigos {

    private AreaCodigos() {}

    private static final Map<String, String> PREFIJOS = Map.ofEntries(
        Map.entry(Areas.PRESIDENCIA, "PRES"),
        Map.entry("Dirección Jurídica", "DJ"),
        Map.entry("Comunicación Social y Marketing Digital", "DCS"),
        Map.entry("Dirección de Gobierno", "DGOB"),
        Map.entry("Dirección de Logística y Eventos", "DLE"),
        Map.entry("Instancia Municipal de la Mujer", "IMM"),
        Map.entry("Instancia Municipal de la Juventud", "IMJ"),
        Map.entry("SIPINNA", "SIPI"),

        Map.entry("Secretaría General Municipal", "SGM"),
        Map.entry("Archivo Municipal", "ARCH"),
        Map.entry("Dirección de Conciliación Municipal", "DCM"),
        Map.entry("Oficialía del Registro del Estado Familiar", "OREF"),
        Map.entry("Recursos Materiales y Patrimonio", "RMP"),
        Map.entry("Reglamentos, Comercio y Espectáculos", "RCE"),

        Map.entry("Tesorería Municipal", "TES"),
        Map.entry("Dirección de Catastro", "DCAT"),
        Map.entry("Tesorería — Administración", "TESA"),
        Map.entry("Tesorería — Egresos", "TESE"),
        Map.entry("Tesorería — Ingresos", "TESI"),
        Map.entry("Tesorería — Recursos Humanos y Nómina", "TESRH"),

        Map.entry("Secretaría de Obras Públicas", "SOP"),
        Map.entry("Dirección de Desarrollo Urbano", "DDU"),
        Map.entry("Dirección de Medio Ambiente", "DMA"),
        Map.entry("Servicios Municipales", "SERM"),
        Map.entry("Servicios Públicos y Limpias", "SPL"),

        Map.entry("Secretaría de Planeación", "SPLAN"),
        Map.entry("Dirección de Tecnologías de la Información", "TICS"),

        Map.entry("Secretaría de Desarrollo Económico y Turismo", "SDET"),

        Map.entry("Secretaría de Bienestar Social", "SBS"),
        Map.entry("Atención al Migrante", "ATM"),
        Map.entry("Dirección de Cultura", "DCUL"),
        Map.entry("Dirección de Educación", "DEDU"),
        Map.entry("Dirección de Salud", "DSAL"),
        Map.entry("Dirección del Deporte", "DDEP"),
        Map.entry("Junta de Reclutamiento", "JREC"),
        Map.entry("Programas Sociales", "PSOC"),

        Map.entry("Secretaría de Pueblos Indígenas", "SPI"),

        Map.entry("Contraloría Municipal", "CONT"),
        Map.entry("Control Canino", "CCAN"),
        Map.entry("Coordinación de Bibliotecas", "CBIB"),
        Map.entry("Oficialía Mayor de la Asamblea", "OMA"),
        Map.entry("Parque Municipal", "PARQ"),
        Map.entry("Protección Civil y Bomberos", "PCB"),
        Map.entry("Unidad de Transparencia", "UT")
    );

    /** @throws IllegalArgumentException si el área no es una de las áreas reales del municipio. */
    public static String prefijo(String area) {
        String p = PREFIJOS.get(area);
        if (p == null) throw new IllegalArgumentException("Área sin prefijo de nomenclatura asignado: " + area);
        return p;
    }

    public static boolean tienePrefijo(String area) {
        return PREFIJOS.containsKey(area);
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
            if (codigo == null || !codigo.startsWith(inicio)) continue;
            try { usados.add(Integer.parseInt(codigo.substring(inicio.length()))); }
            catch (NumberFormatException ignored) { /* código heredado sin número */ }
        }
        int numero = 1;
        while (usados.contains(numero)) numero++;
        return inicio + String.format("%02d", numero);
    }
}
