package com.sibim.service;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.util.OrdenCodigo;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;

/**
 * Configuración › Áreas › «Separar en un área nueva…»: part of an área becomes an área of
 * its own, in one step — the Rastro out of Servicios Municipales (7-nov-2026) and the
 * Mercado Municipal out of Reglamentos (13-nov-2026) were both done outside the app,
 * because in it they were three separate tasks (new área, transfer by person, sign).
 * The bienes go with whoever holds them: one new resguardo per person, with the cargo of
 * the resguardo they had; the old resguardos left empty are cancelled by the transfer.
 */
public final class SepararArea {

    private SepararArea() {}

    /** The bienes of one person (null: no one, or two people in one name — those go without
     *  a resguardo, as nobody alone can sign it), in código order. */
    public record Grupo(String persona, String cargo, List<Producto> bienes) {}

    /** Who takes what: one group per person ("JUAN PÉREZ" and "Juan Perez" are one), in the
     *  order of their first código; the ones without a person last.
     *  @param resguardoDe the resguardo in force of a bien, or null — for the cargo */
    public static List<Grupo> agrupar(List<Producto> bienes, Function<Producto, Resguardo> resguardoDe) {
        List<Producto> enOrden = bienes.stream()
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), OrdenCodigo.POR_CODIGO)).toList();
        Map<String, List<Producto>> porPersona = new LinkedHashMap<>();
        Map<String, String> nombre = new HashMap<>();
        List<Producto> sinPersona = new ArrayList<>();
        for (Producto p : enOrden) {
            String r = p.getResguardante() == null ? "" : p.getResguardante().trim().replaceAll("\\s+", " ");
            if (r.isEmpty() || CalidadDatosService.dosPersonas(r) || r.equalsIgnoreCase("Sin resguardante")) {
                sinPersona.add(p);
                continue;
            }
            String clave = porPersona.keySet().stream().filter(k -> ResguardoService.mismaPersona(nombre.get(k), r))
                .findFirst().orElse(ResguardoService.clavePersona(r));
            nombre.putIfAbsent(clave, r);
            porPersona.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
        }
        List<Grupo> out = new ArrayList<>();
        porPersona.forEach((clave, suyos) -> out.add(new Grupo(nombre.get(clave), cargo(suyos, resguardoDe), List.copyOf(suyos))));
        if (!sinPersona.isEmpty()) out.add(new Grupo(null, null, List.copyOf(sinPersona)));
        return out;
    }

    private static String cargo(List<Producto> bienes, Function<Producto, Resguardo> resguardoDe) {
        for (Producto p : bienes) {
            Resguardo r = resguardoDe.apply(p);
            if (r != null && r.getResguardanteCargo() != null && !r.getResguardanteCargo().isBlank())
                return r.getResguardanteCargo().trim();
        }
        return null;
    }

    private static final Set<String> SIN_LETRA = Set.of("DE", "DEL", "LA", "LAS", "LOS", "EL", "Y", "E", "EN", "PARA", "A");

    /** A prefix for a new área no other área uses: the initials of its name ("Mercado
     *  Municipal" → MM), or its first letters when it is one word ("Rastro" → RAST); a
     *  number is added if those are taken. */
    public static String prefijoSugerido(String nombre, Collection<String> usados) {
        if (nombre == null || nombre.isBlank()) return "";
        String plano = Normalizer.normalize(nombre.toUpperCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("[^A-Z0-9 ]", " ").trim();
        List<String> palabras = Arrays.stream(plano.split("\\s+")).filter(w -> !w.isEmpty() && !SIN_LETRA.contains(w)).toList();
        if (palabras.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (palabras.size() == 1) sb.append(palabras.get(0), 0, Math.min(4, palabras.get(0).length()));
        else palabras.stream().limit(6).forEach(w -> sb.append(w.charAt(0)));
        String base = sb.toString();
        Set<String> tomados = new HashSet<>();
        usados.forEach(u -> tomados.add(u.toUpperCase(Locale.ROOT)));
        if (!tomados.contains(base)) return base;
        for (int i = 2; i < 100; i++) {
            String otro = (base.length() > 6 ? base.substring(0, 6) : base) + i;
            if (!tomados.contains(otro)) return otro;
        }
        return base;
    }

    /** What was done: one transfer per group, in the order of {@link #agrupar}. */
    public record Resultado(String area, boolean areaNueva, List<TransferenciaRapidaService.Resultado> lotes) {
        public int bienes() { return lotes.stream().mapToInt(l -> l.hechos().size()).sum(); }
        public List<Resguardo> resguardos() {
            return lotes.stream().map(TransferenciaRapidaService.Resultado::resguardo).filter(Objects::nonNull).toList();
        }
        public List<String> errores() { return lotes.stream().flatMap(l -> l.errores().stream()).toList(); }
    }

    /** Creates the área (unless it already exists) and moves each group with its person.
     *  @param crearArea {@link AreaService#guardar(AreaCatalog.Entrada)} */
    public static Resultado aplicar(AreaCatalog.Entrada nueva, List<Grupo> grupos, String motivo,
                                    AreaCreador crearArea, TransferenciaRapidaService transferencias) throws Exception {
        if (grupos.stream().allMatch(g -> g.bienes().isEmpty()))
            throw new IllegalArgumentException("Marca los bienes que pasan al área nueva");
        boolean areaNueva = Areas.catalogo().buscar(nueva.nombre()).isEmpty();
        if (areaNueva) crearArea.crear(nueva);
        List<TransferenciaRapidaService.Resultado> lotes = new ArrayList<>();
        for (Grupo g : grupos) {
            if (g.bienes().isEmpty()) continue;
            lotes.add(transferencias.transferir(g.bienes(), nueva.nombre(), motivo, g.persona(), g.cargo()));
        }
        return new Resultado(nueva.nombre(), areaNueva, lotes);
    }

    @FunctionalInterface
    public interface AreaCreador { void crear(AreaCatalog.Entrada e) throws Exception; }

    /** «Separación de Mercado Municipal de Reglamentos, Comercio y Espectáculos». */
    public static String motivo(String nueva, String origen) {
        return "Separación de " + nueva + " de " + origen;
    }
}
