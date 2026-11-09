package com.sibim.service;

import com.sibim.repository.ConfiguracionRepository;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The titular of each área — who answers for its resguardos: the "Nombre del
 * responsable del resguardo" of the ANEXO V.4, a different person from the
 * resguardante. It can't be told from the data (on 29-oct-2026, 18 áreas had
 * several directors or titulars among their resguardantes and 10 had none), so
 * it is typed in Configuración › Áreas. Kept in configuracion, one row per área
 * ("titular_area:&lt;área&gt;"), which needs no change to the areas table.
 */
public final class TitularesArea {

    static final String PREFIJO = "titular_area:";

    private TitularesArea() {}

    /** Every titular typed, by área; an área without one is absent. */
    public static Map<String, String> todos(ConfiguracionRepository config) {
        Map<String, String> out = new HashMap<>();
        try {
            for (var e : config.findAll().entrySet())
                if (e.getKey().startsWith(PREFIJO) && e.getValue() != null && !e.getValue().isBlank())
                    out.put(e.getKey().substring(PREFIJO.length()), e.getValue().trim());
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(TitularesArea.class).warn("Sin titulares de área: {}", e.getMessage());
        }
        return out;
    }

    /** Patrimonio only (ConfiguracionRepository#set); empty clears it. */
    public static void guardar(ConfiguracionRepository config, String area, String titular) throws SQLException {
        config.set(clave(area), titular == null ? "" : com.sibim.util.NombrePersona.sinTitulos(titular.trim()));
    }

    /** The configuracion key of an área's titular. */
    public static String clave(String area) { return PREFIJO + area; }

    // ── Suggestions (9-nov-2026) ──────────────────────────────────────────────

    /** Someone who could be the titular of an área: a resguardo in force there names
     *  them with a cargo that heads something (director, titular, secretario…). */
    public record Candidato(String nombre, String cargo) {
        /** "MARÍA LÓPEZ · Directora de Catastro". */
        public String texto() {
            return cargo == null || cargo.isBlank() ? nombre : nombre + " · " + cargo;
        }
    }

    /** The cargos that head an área, from the most to the least telling. A bare
     *  "SECRETARIA" is the job of an assistant, so a secretario needs "de…" or "general". */
    private static final java.util.regex.Pattern[] JEFES = {
        java.util.regex.Pattern.compile("\\bTITULAR\\b"),
        java.util.regex.Pattern.compile("\\b(DIRECTORA?|SECRETARI[OA] (DE|DEL|GENERAL)|TESORER[OA]|CONTRALORA?|PRESIDENTA?|SINDIC[OA]|OFICIAL MAYOR)\\b"),
        java.util.regex.Pattern.compile("\\bCOORDINADORA?\\b"),
        java.util.regex.Pattern.compile("\\bJEF[EA]\\b"),
        java.util.regex.Pattern.compile("\\b(SUBDIRECTORA?|COMANDANTE)\\b"),
        java.util.regex.Pattern.compile("\\b(ENCARGAD[OA]|RESPONSABLE)\\b"),
    };

    private static final java.util.Set<String> VACIAS = java.util.Set.of(
        "DE", "DEL", "LA", "LAS", "LOS", "EL", "Y", "MUNICIPAL", "MUNICIPIO", "AREA", "DIRECCION", "SECRETARIA");

    /** How much a cargo says "head": 0 the most; -1 when it doesn't. */
    static int peso(String cargo) {
        String c = plano(cargo);
        for (int i = 0; i < JEFES.length; i++) if (JEFES[i].matcher(c).find()) return i;
        return -1;
    }

    /** Up to three candidates for the titular of each área, from its resguardos in
     *  force: the one whose cargo names the área first ("Director de Catastro" for
     *  Dirección de Catastro), then by how much the cargo says "head", then whoever
     *  holds more resguardos there. The same person once, without titles. */
    public static Map<String, List<Candidato>> candidatos(java.util.Collection<com.sibim.model.Resguardo> resguardos) {
        record Visto(String nombre, String cargo, int peso, boolean nombraArea, int veces) {}
        Map<String, Map<String, Visto>> porArea = new HashMap<>();
        for (var r : resguardos) {
            if (!r.isActivo() || r.getResguardanteArea() == null || r.getResguardanteNombre() == null) continue;
            int p = peso(r.getResguardanteCargo());
            if (p < 0) continue;
            String area = r.getResguardanteArea();
            String nombre = com.sibim.util.NombrePersona.sinTitulos(r.getResguardanteNombre().trim().replaceAll("\\s+", " "));
            String cargo = r.getResguardanteCargo().trim().replaceAll("\\s+", " ");
            boolean nombra = nombraArea(cargo, area);
            porArea.computeIfAbsent(area, k -> new HashMap<>()).merge(ResguardoService.clavePersona(nombre),
                new Visto(nombre, cargo, p, nombra, 1),
                (a, b) -> {
                    boolean bMejor = b.nombraArea() && !a.nombraArea() || b.nombraArea() == a.nombraArea() && b.peso() < a.peso();
                    Visto m = bMejor ? b : a;
                    return new Visto(m.nombre(), m.cargo(), m.peso(), m.nombraArea(), a.veces() + b.veces());
                });
        }
        Map<String, List<Candidato>> out = new HashMap<>();
        porArea.forEach((area, vistos) -> out.put(area, vistos.values().stream()
            .sorted(java.util.Comparator.comparing((Visto v) -> !v.nombraArea())
                .thenComparingInt(Visto::peso)
                .thenComparing(java.util.Comparator.comparingInt(Visto::veces).reversed())
                .thenComparing(Visto::nombre))
            .limit(3)
            .map(v -> new Candidato(v.nombre(), v.cargo()))
            .toList()));
        return out;
    }

    /** Whether the cargo says the área's own words ("DIRECTOR DE CATASTRO" for "Dirección de Catastro"). */
    static boolean nombraArea(String cargo, String area) {
        java.util.Set<String> enCargo = new java.util.HashSet<>(java.util.Arrays.asList(plano(cargo).split("[^A-Z0-9]+")));
        for (String w : plano(area).split("[^A-Z0-9]+"))
            if (w.length() > 2 && !VACIAS.contains(w) && enCargo.contains(w)) return true;
        return false;
    }

    private static String plano(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s.toUpperCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
    }
}
