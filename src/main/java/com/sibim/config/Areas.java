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

    /** The group of the áreas that hang from no one, as the organigrama names it. */
    public static final String AUTONOMOS = "Organismos Autónomos";

    /** Where an área hangs in the organigrama: its secretaría (or the Presidencia), the
     *  área itself when it heads one, or the organismos autónomos. */
    public static String dependenciaDe(String area) {
        Optional<AreaCatalog.Entrada> e = catalogo.buscar(area);
        if (e.isEmpty()) return AUTONOMOS;
        return switch (e.get().grupo()) {
            case PRESIDENCIA, SECRETARIA -> area;
            case DIRECCION -> e.get().padre() != null && !e.get().padre().isBlank() ? e.get().padre() : AUTONOMOS;
            case AUTONOMO -> AUTONOMOS;
        };
    }

    /** {@code items} in groups by dependencia: each group in the order its head comes in
     *  the organigrama, the head first, and the organismos autónomos at the end. */
    public static <T> LinkedHashMap<String, List<T>> porDependencia(Collection<T> items, java.util.function.Function<T, String> areaDe) {
        Map<String, List<T>> grupos = new LinkedHashMap<>();
        for (T x : items) grupos.computeIfAbsent(dependenciaDe(areaDe.apply(x)), k -> new ArrayList<>()).add(x);
        List<String> orden = new ArrayList<>(getAllAreaNames());
        Comparator<String> porOrganigrama = Comparator
            .comparing((String g) -> g.equals(AUTONOMOS))
            .thenComparingInt(g -> orden.indexOf(g) < 0 ? Integer.MAX_VALUE : orden.indexOf(g));
        LinkedHashMap<String, List<T>> out = new LinkedHashMap<>();
        grupos.keySet().stream().sorted(porOrganigrama).forEach(g -> {
            List<T> l = new ArrayList<>(grupos.get(g));
            l.sort(Comparator.comparing((T x) -> !areaDe.apply(x).equals(g)));
            out.put(g, l);
        });
        return out;
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
