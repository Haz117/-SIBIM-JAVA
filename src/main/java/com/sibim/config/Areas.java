package com.sibim.config;

import java.util.*;

/** Estructura orgánica real del municipio (Ixmiquilpan), alineada con los
 *  valores de {@code area} / {@code resguardante_area} que trae el
 *  inventario físico MLA importado (ver tools/importar_inventario_mla.py y
 *  su AREA_NORMALIZE). Desde V24 las áreas viven en la tabla {@code areas}
 *  (editable en Configuración): esta clase expone el {@link AreaCatalog}
 *  vigente, que es {@link AreaCatalog#PREDETERMINADO} hasta que
 *  {@code AreaService} carga el de la base. */
public final class Areas {

    private Areas() {}

    public record SecretariaInfo(String nombre, List<String> direcciones) {}

    /** The Despacho is the organigrama's root and is never renamed. */
    public static final String PRESIDENCIA = "Despacho de Presidencia";

    private static volatile AreaCatalog catalogo = AreaCatalog.PREDETERMINADO;

    public static AreaCatalog catalogo() { return catalogo; }

    /** Replaces the catalog every screen reads (after loading or editing the areas table). */
    public static void usar(AreaCatalog nuevo) { catalogo = java.util.Objects.requireNonNull(nuevo); }

    public static List<String> direccionesPresidencia() { return catalogo.direccionesDe(PRESIDENCIA); }

    public static List<SecretariaInfo> secretarias() { return catalogo.secretarias(); }

    public static List<String> autonomos() { return catalogo.autonomos(); }

    /** Returns all area names as a flat set */
    public static Set<String> getAllAreaNames() {
        Set<String> names = new LinkedHashSet<>();
        names.add(PRESIDENCIA);
        names.addAll(direccionesPresidencia());
        for (SecretariaInfo s : secretarias()) {
            names.add(s.nombre());
            names.addAll(s.direcciones());
        }
        names.addAll(autonomos());
        return Collections.unmodifiableSet(names);
    }

    /** Returns the list of direction names under a given secretariat name, or empty if not found */
    public static List<String> getDireccionesDeSecretaria(String secretariaNombre) {
        for (SecretariaInfo s : secretarias()) {
            if (s.nombre().equalsIgnoreCase(secretariaNombre)) {
                return s.direcciones();
            }
        }
        return Collections.emptyList();
    }
}
