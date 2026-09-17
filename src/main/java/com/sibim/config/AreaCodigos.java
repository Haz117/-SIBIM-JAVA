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

        Map.entry("Direccion de Audiencias y Atencion Ciudadana", "DAAC"),
        Map.entry("Secretaria Particular y Relaciones Publicas", "SPRP"),
        Map.entry("Direccion de Logistica y Eventos", "DLE"),
        Map.entry("Direccion Juridica", "DJ"),
        Map.entry("Instancia Municipal para el Desarrollo de las Mujeres", "IMDM"),
        Map.entry("Direccion de Comunicacion Social y Marketing Digital", "DCS"),
        Map.entry("Secretaria Ejecutiva de SIPINNA", "SIPI"),
        Map.entry("Secretario Tecnico", "SETE"),

        Map.entry("Secretaria General Municipal", "SGM"),
        Map.entry("Direccion de Recursos Humanos", "RH"),
        Map.entry("Direccion de Administracion", "ADM"),
        Map.entry("Direccion de Archivo General", "DAG"),
        Map.entry("Direccion de Servicios Generales", "DSG"),
        Map.entry("Direccion de Compras y Adquisiciones", "DCA"),
        Map.entry("Direccion de Bienes Municipales", "DBM"),
        Map.entry("Direccion de Noticias", "DNOT"),
        Map.entry("Direccion de Registro Civil", "DRC"),
        Map.entry("Coordinacion de Gestion", "CGES"),
        Map.entry("Subdireccion Juridica", "SDJ"),

        Map.entry("Secretaria de Tesoreria Municipal", "STES"),
        Map.entry("Direccion de Ingresos", "DING"),
        Map.entry("Direccion de Egresos y Presupuesto", "DEP"),
        Map.entry("Direccion de Contabilidad", "DCONT"),
        Map.entry("Direccion de Catastro", "DCAT"),
        Map.entry("Direccion de Recaudacion", "DREC"),
        Map.entry("Direccion de Inversiones", "DINV"),

        Map.entry("Secretaria de Obras Publicas y Desarrollo Urbano", "SOPU"),
        Map.entry("Direccion de Obras Publicas", "DOP"),
        Map.entry("Direccion de Desarrollo Urbano", "DDU"),
        Map.entry("Direccion de Medio Ambiente", "DMA"),
        Map.entry("Direccion de Servicios Municipales", "DSM"),

        Map.entry("Secretaria de Planeacion y Evaluacion", "SPEV"),
        Map.entry("Direccion de Tecnologias de la Informacion", "TICS"),

        Map.entry("Secretaria de Desarrollo Economico y Turismo", "SDET"),
        Map.entry("Direccion de Desarrollo Economico", "DDE"),
        Map.entry("Direccion de Turismo", "DTUR"),
        Map.entry("Direccion de Empleo", "DEMP"),

        Map.entry("Secretaria de Bienestar Social", "SBIE"),
        Map.entry("Direccion de Asistencia Social", "DAS"),
        Map.entry("Direccion de Programas Sociales", "DPS"),
        Map.entry("Direccion de Adultos Mayores", "DAM"),
        Map.entry("Direccion de Juventud", "DJUV"),
        Map.entry("Direccion de Deportes", "DDEP"),
        Map.entry("Direccion de Educacion", "DEDU"),

        Map.entry("Secretaria de Seguridad Publica Municipal", "SSEG"),
        Map.entry("Direccion de Seguridad Publica", "DSP"),

        Map.entry("Secretaria de Desarrollo para Pueblos Indigenas", "SDPI"),

        Map.entry("Contraloria Municipal", "CONT"),
        Map.entry("Direccion de la Unidad de Investigacion", "DUI"),
        Map.entry("Unidad Municipal de Transparencia y Acceso a la Informacion", "UMTA"),
        Map.entry("SMDIF", "SMDIF"),
        Map.entry("CAPASMIH", "CAPA")
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
}
