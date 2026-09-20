package com.sibim.config;

import java.util.*;

/** Estructura orgánica real del municipio (Ixmiquilpan), alineada con los
 *  valores de {@code area} / {@code resguardante_area} que trae el
 *  inventario físico MLA importado (ver tools/importar_inventario_mla.py y
 *  su AREA_NORMALIZE) — 44 áreas en total, verificadas contra
 *  tools/output/2_import_bienes.sql y 3_import_resguardos.sql. Antes de esta
 *  versión, esta clase usaba una convención distinta (sin acentos, otra
 *  jerarquía) que no coincidía con ningún área real: el organigrama y todo
 *  filtro/scoping por área quedaban vacíos para los 2515 bienes importados. */
public final class Areas {

    private Areas() {}

    public record SecretariaInfo(String nombre, List<String> direcciones) {}

    public static final String PRESIDENCIA = "Despacho de Presidencia";

    public static final List<String> DIRECCIONES_PRESIDENCIA = List.of(
        "Dirección Jurídica",
        "Comunicación Social y Marketing Digital",
        "Dirección de Gobierno",
        "Dirección de Logística y Eventos",
        "Instancia Municipal de la Mujer",
        "Instancia Municipal de la Juventud",
        "SIPINNA"
    );

    public static final List<SecretariaInfo> SECRETARIAS = List.of(
        new SecretariaInfo(
            "Secretaría General Municipal",
            List.of(
                "Archivo Municipal",
                "Dirección de Conciliación Municipal",
                "Oficialía del Registro del Estado Familiar",
                "Recursos Materiales y Patrimonio",
                "Reglamentos, Comercio y Espectáculos"
            )
        ),
        new SecretariaInfo(
            "Tesorería Municipal",
            List.of(
                "Dirección de Catastro",
                "Tesorería — Administración",
                "Tesorería — Egresos",
                "Tesorería — Ingresos",
                "Tesorería — Recursos Humanos y Nómina"
            )
        ),
        new SecretariaInfo(
            "Secretaría de Obras Públicas",
            List.of(
                "Dirección de Desarrollo Urbano",
                "Dirección de Medio Ambiente",
                "Servicios Municipales",
                "Servicios Públicos y Limpias"
            )
        ),
        new SecretariaInfo(
            "Secretaría de Planeación",
            List.of(
                "Dirección de Tecnologías de la Información"
            )
        ),
        new SecretariaInfo(
            "Secretaría de Desarrollo Económico y Turismo",
            List.of()
        ),
        new SecretariaInfo(
            "Secretaría de Bienestar Social",
            List.of(
                "Atención al Migrante",
                "Dirección de Cultura",
                "Dirección de Educación",
                "Dirección de Salud",
                "Dirección del Deporte",
                "Junta de Reclutamiento",
                "Programas Sociales"
            )
        ),
        new SecretariaInfo(
            "Secretaría de Pueblos Indígenas",
            List.of()
        )
    );

    public static final List<String> AUTONOMOS = List.of(
        "Contraloría Municipal",
        "Control Canino",
        "Coordinación de Bibliotecas",
        "Oficialía Mayor de la Asamblea",
        "Parque Municipal",
        "Protección Civil y Bomberos",
        "Unidad de Transparencia"
    );

    /** Returns all area names as a flat set */
    public static Set<String> getAllAreaNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add(PRESIDENCIA);
        names.addAll(DIRECCIONES_PRESIDENCIA);
        for (SecretariaInfo s : SECRETARIAS) {
            names.add(s.nombre());
            names.addAll(s.direcciones());
        }
        names.addAll(AUTONOMOS);
        return Collections.unmodifiableSet(names);
    }

    /** Returns the list of direction names under a given secretariat name, or empty if not found */
    public static List<String> getDireccionesDeSecretaria(String secretariaNombre) {
        for (SecretariaInfo s : SECRETARIAS) {
            if (s.nombre().equalsIgnoreCase(secretariaNombre)) {
                return s.direcciones();
            }
        }
        return Collections.emptyList();
    }
}
