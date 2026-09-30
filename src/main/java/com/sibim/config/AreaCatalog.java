package com.sibim.config;

import java.util.*;
import java.util.regex.Pattern;

/**
 * The municipality's areas, how they nest and each one's código prefix — one
 * immutable snapshot. {@link Areas} and {@link AreaCodigos} read the snapshot
 * currently in use; it comes from the {@code areas} table (V24, editable in
 * Configuración) and falls back to {@link #PREDETERMINADO} — the same 44
 * areas the table is seeded with — in demo mode, offline, or before the
 * table is reachable.
 *
 * <p>An area's name is also what every bien, resguardo and usuario stores in
 * its {@code area} column, so names are never edited here: an area can be
 * added, moved under another parent or given a different prefix.
 */
public final class AreaCatalog {

    public enum Grupo {
        /** The Despacho de Presidencia itself (exactly one). */
        PRESIDENCIA,
        /** A secretaría; its direcciones hang from it. */
        SECRETARIA,
        /** A dirección under Presidencia or under a secretaría. */
        DIRECCION,
        /** An organismo autónomo (no parent). */
        AUTONOMO
    }

    /** {@code padre} is set only for {@link Grupo#DIRECCION}. */
    public record Entrada(String nombre, Grupo grupo, String padre, String prefijo) {}

    /** Letters and digits, 1–8 characters: it ends up in every código ("TICS/01"). */
    public static final Pattern PREFIJO_VALIDO = Pattern.compile("[A-Z0-9]{1,8}");

    private final List<Entrada> entradas;
    private final Map<String, Entrada> porNombre;

    /** @throws IllegalArgumentException when the areas don't form a valid organigrama. */
    public AreaCatalog(List<Entrada> entradas) {
        this.entradas = List.copyOf(entradas);
        Map<String, Entrada> map = new LinkedHashMap<>();
        Set<String> prefijos = new HashSet<>();
        for (Entrada e : this.entradas) {
            if (e.nombre() == null || e.nombre().isBlank())
                throw new IllegalArgumentException("Hay un área sin nombre");
            if (map.put(e.nombre(), e) != null)
                throw new IllegalArgumentException("El área \"" + e.nombre() + "\" está repetida");
            if (e.prefijo() == null || !PREFIJO_VALIDO.matcher(e.prefijo()).matches())
                throw new IllegalArgumentException("El prefijo de \"" + e.nombre()
                    + "\" debe tener de 1 a 8 letras mayúsculas o números");
            if (!prefijos.add(e.prefijo()))
                throw new IllegalArgumentException("El prefijo " + e.prefijo() + " ya lo usa otra área");
        }
        long presidencias = this.entradas.stream().filter(e -> e.grupo() == Grupo.PRESIDENCIA).count();
        if (presidencias != 1)
            throw new IllegalArgumentException("Debe existir exactamente un Despacho de Presidencia");
        for (Entrada e : this.entradas) {
            if (e.grupo() == Grupo.DIRECCION) {
                Entrada padre = e.padre() == null ? null : map.get(e.padre());
                if (padre == null || (padre.grupo() != Grupo.PRESIDENCIA && padre.grupo() != Grupo.SECRETARIA))
                    throw new IllegalArgumentException("\"" + e.nombre()
                        + "\" debe depender de Presidencia o de una secretaría");
            } else if (e.padre() != null) {
                throw new IllegalArgumentException("Solo una dirección puede depender de otra área");
            }
        }
        this.porNombre = Collections.unmodifiableMap(map);
    }

    public List<Entrada> entradas() { return entradas; }

    public Optional<Entrada> buscar(String nombre) { return Optional.ofNullable(porNombre.get(nombre)); }

    public String presidencia() {
        return entradas.stream().filter(e -> e.grupo() == Grupo.PRESIDENCIA).findFirst().orElseThrow().nombre();
    }

    public List<String> direccionesDe(String padre) {
        return entradas.stream()
            .filter(e -> e.grupo() == Grupo.DIRECCION && padre.equals(e.padre()))
            .map(Entrada::nombre).toList();
    }

    public List<Areas.SecretariaInfo> secretarias() {
        return entradas.stream().filter(e -> e.grupo() == Grupo.SECRETARIA)
            .map(e -> new Areas.SecretariaInfo(e.nombre(), direccionesDe(e.nombre())))
            .toList();
    }

    public List<String> autonomos() {
        return entradas.stream().filter(e -> e.grupo() == Grupo.AUTONOMO).map(Entrada::nombre).toList();
    }

    /** Same catalog with {@code cambio} added, or replacing the area of the same name. */
    public AreaCatalog con(Entrada cambio) {
        List<Entrada> nuevas = new ArrayList<>(entradas);
        int i = indexOf(cambio.nombre());
        if (i >= 0) nuevas.set(i, cambio); else nuevas.add(cambio);
        return new AreaCatalog(nuevas);
    }

    private int indexOf(String nombre) {
        for (int i = 0; i < entradas.size(); i++) if (entradas.get(i).nombre().equals(nombre)) return i;
        return -1;
    }

    // ── Built-in organigrama (also the V24 seed) ──────────────────────────────

    public static final AreaCatalog PREDETERMINADO = new AreaCatalog(construirPredeterminado());

    private static List<Entrada> construirPredeterminado() {
        List<Entrada> l = new ArrayList<>();
        String pres = Areas.PRESIDENCIA;
        l.add(new Entrada(pres, Grupo.PRESIDENCIA, null, "PRES"));
        dir(l, pres, "Dirección Jurídica", "DJ");
        dir(l, pres, "Comunicación Social y Marketing Digital", "DCS");
        dir(l, pres, "Dirección de Gobierno", "DGOB");
        dir(l, pres, "Dirección de Logística y Eventos", "DLE");
        dir(l, pres, "Instancia Municipal de la Mujer", "IMM");
        dir(l, pres, "Instancia Municipal de la Juventud", "IMJ");
        dir(l, pres, "SIPINNA", "SIPI");

        sec(l, "Secretaría General Municipal", "SGM");
        dir(l, "Secretaría General Municipal", "Archivo Municipal", "ARCH");
        dir(l, "Secretaría General Municipal", "Dirección de Conciliación Municipal", "DCM");
        dir(l, "Secretaría General Municipal", "Oficialía del Registro del Estado Familiar", "OREF");
        dir(l, "Secretaría General Municipal", "Recursos Materiales y Patrimonio", "RMP");
        dir(l, "Secretaría General Municipal", "Reglamentos, Comercio y Espectáculos", "RCE");

        sec(l, "Tesorería Municipal", "TES");
        dir(l, "Tesorería Municipal", "Dirección de Catastro", "DCAT");
        dir(l, "Tesorería Municipal", "Tesorería — Administración", "TESA");
        dir(l, "Tesorería Municipal", "Tesorería — Egresos", "TESE");
        dir(l, "Tesorería Municipal", "Tesorería — Ingresos", "TESI");
        dir(l, "Tesorería Municipal", "Tesorería — Recursos Humanos y Nómina", "TESRH");

        sec(l, "Secretaría de Obras Públicas", "SOP");
        dir(l, "Secretaría de Obras Públicas", "Dirección de Desarrollo Urbano", "DDU");
        dir(l, "Secretaría de Obras Públicas", "Dirección de Medio Ambiente", "DMA");
        dir(l, "Secretaría de Obras Públicas", "Servicios Municipales", "SERM");
        dir(l, "Secretaría de Obras Públicas", "Servicios Públicos y Limpias", "SPL");

        sec(l, "Secretaría de Planeación", "SPLAN");
        dir(l, "Secretaría de Planeación", "Dirección de Tecnologías de la Información", "TICS");

        sec(l, "Secretaría de Desarrollo Económico y Turismo", "SDET");

        sec(l, "Secretaría de Bienestar Social", "SBS");
        dir(l, "Secretaría de Bienestar Social", "Atención al Migrante", "ATM");
        dir(l, "Secretaría de Bienestar Social", "Dirección de Cultura", "DCUL");
        dir(l, "Secretaría de Bienestar Social", "Dirección de Educación", "DEDU");
        dir(l, "Secretaría de Bienestar Social", "Dirección de Salud", "DSAL");
        dir(l, "Secretaría de Bienestar Social", "Dirección del Deporte", "DDEP");
        dir(l, "Secretaría de Bienestar Social", "Junta de Reclutamiento", "JREC");
        dir(l, "Secretaría de Bienestar Social", "Programas Sociales", "PSOC");

        sec(l, "Secretaría de Pueblos Indígenas", "SPI");

        aut(l, "Contraloría Municipal", "CONT");
        aut(l, "Control Canino", "CCAN");
        aut(l, "Coordinación de Bibliotecas", "CBIB");
        aut(l, "Oficialía Mayor de la Asamblea", "OMA");
        aut(l, "Parque Municipal", "PARQ");
        aut(l, "Protección Civil y Bomberos", "PCB");
        aut(l, "Unidad de Transparencia", "UT");
        return l;
    }

    private static void sec(List<Entrada> l, String n, String p) { l.add(new Entrada(n, Grupo.SECRETARIA, null, p)); }
    private static void dir(List<Entrada> l, String padre, String n, String p) { l.add(new Entrada(n, Grupo.DIRECCION, padre, p)); }
    private static void aut(List<Entrada> l, String n, String p) { l.add(new Entrada(n, Grupo.AUTONOMO, null, p)); }
}
