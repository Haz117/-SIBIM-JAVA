package com.sibim.config;

import java.util.*;

public final class Areas {

    private Areas() {}

    public record SecretariaInfo(String nombre, String titular, List<String> direcciones) {}

    public static final String PRESIDENCIA = "Despacho de la Presidencia";

    public static final List<String> DIRECCIONES_PRESIDENCIA = List.of(
        "Direccion de Audiencias y Atencion Ciudadana",
        "Secretaria Particular y Relaciones Publicas",
        "Direccion de Logistica y Eventos",
        "Direccion Juridica",
        "Instancia Municipal para el Desarrollo de las Mujeres",
        "Direccion de Comunicacion Social y Marketing Digital",
        "Secretaria Ejecutiva de SIPINNA",
        "Secretario Tecnico"
    );

    public static final List<SecretariaInfo> SECRETARIAS = List.of(
        new SecretariaInfo(
            "Secretaria General Municipal",
            "Jose Manuel Zuniga Guerrero",
            List.of(
                "Direccion de Recursos Humanos",
                "Direccion de Administracion",
                "Direccion de Archivo General",
                "Direccion de Servicios Generales",
                "Direccion de Compras y Adquisiciones",
                "Direccion de Bienes Municipales",
                "Direccion de Noticias",
                "Direccion de Registro Civil",
                "Coordinacion de Gestion",
                "Subdireccion Juridica"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Tesoreria Municipal",
            "Ruben Martinez Sanchez",
            List.of(
                "Direccion de Ingresos",
                "Direccion de Egresos y Presupuesto",
                "Direccion de Contabilidad",
                "Direccion de Catastro",
                "Direccion de Recaudacion",
                "Direccion de Inversiones"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Obras Publicas y Desarrollo Urbano",
            "Ivan Arturo Lugo Martin",
            List.of(
                "Direccion de Obras Publicas",
                "Direccion de Desarrollo Urbano",
                "Direccion de Medio Ambiente",
                "Direccion de Servicios Municipales"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Planeacion y Evaluacion",
            "Rigoberto Barrera Roldan",
            List.of(
                "Direccion de Tecnologias de la Informacion"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Desarrollo Economico y Turismo",
            "Lucila Ocampo Valle",
            List.of(
                "Direccion de Desarrollo Economico",
                "Direccion de Turismo",
                "Direccion de Empleo"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Bienestar Social",
            "Socorro Vargas Chavez",
            List.of(
                "Direccion de Asistencia Social",
                "Direccion de Programas Sociales",
                "Direccion de Adultos Mayores",
                "Direccion de Juventud",
                "Direccion de Deportes",
                "Direccion de Educacion"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Seguridad Publica Municipal",
            "Diadymir Morelos Esquivel",
            List.of(
                "Direccion de Seguridad Publica"
            )
        ),
        new SecretariaInfo(
            "Secretaria de Desarrollo para Pueblos Indigenas",
            "Lupita Anneth Patricio Reyes",
            List.of()
        )
    );

    public static final List<String> AUTONOMOS = List.of(
        "Contraloria Municipal",
        "Direccion de la Unidad de Investigacion",
        "Unidad Municipal de Transparencia y Acceso a la Informacion",
        "SMDIF",
        "CAPASMIH"
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
