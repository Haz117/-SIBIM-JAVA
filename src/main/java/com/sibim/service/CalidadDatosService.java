package com.sibim.service;

import com.sibim.config.VidaUtilConac;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.CalidadDatosRepository;
import com.sibim.repository.ConfiguracionRepository;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.session.SessionManager;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/** What the inventory loaded from the Excel files still needs fixing: people under two
 *  spellings, series repeated, vida útil missing (30-oct-2026: 26 people, 49 series and
 *  2,951 of 3,062 bienes). The people to fix it are the ones who know the bienes, so
 *  this only finds the cases and applies what they decide; every change is audited. */
public class CalidadDatosService {

    private final CalidadDatosRepository repo;
    private final AuditLogRepository audit;
    private final ConfiguracionRepository config;

    public CalidadDatosService() {
        this(new CalidadDatosRepository(), new AuditLogRepository(), new ConfiguracionRepository());
    }

    CalidadDatosService(CalidadDatosRepository repo, AuditLogRepository audit, ConfiguracionRepository config) {
        this.repo = repo;
        this.audit = audit;
        this.config = config;
    }

    private static void soloPatrimonio() {
        if (!SessionManager.isAdmin())
            throw new SecurityException("Solo Patrimonio (administrador) corrige estos datos");
    }

    private static void conConexion() throws SQLException {
        // The resguardos and the other PCs have to see it at once: it isn't queued.
        if (DatabaseConfig.isOfflineMode()) throw new SQLException("Hace falta conexión para esta corrección");
    }

    // ── Personas repetidas ────────────────────────────────────────────────────

    /** Titles that come and go in the same person's name, without their dots. */
    private static final Set<String> TITULOS = Set.of("LIC", "LICDA", "LD", "ING", "INGA", "ARQ", "DR", "DRA",
        "MTRO", "MTRA", "PROF", "PROFR", "PROFRA", "C", "CP", "COMANDANTE", "ABOG");

    /** What two spellings of one person share: the words, in any order, without titles. */
    static String clavePalabras(String nombre) {
        List<String> palabras = new ArrayList<>();
        for (String p : ResguardoService.clavePersona(nombre).split(" ")) {
            String limpia = p.replaceAll("[.:,]", "");
            if (!limpia.isEmpty() && !TITULOS.contains(limpia)) palabras.add(limpia);
        }
        Collections.sort(palabras);
        return String.join(" ", palabras);
    }

    /** The people who are in the list under more than one spelling, most bienes first. */
    public static List<List<Resguardante>> personasRepetidas(List<Resguardo> resguardos, List<Producto> bienes, Set<String> distintas) {
        Map<String, List<Resguardante>> grupos = new LinkedHashMap<>();
        for (Resguardante p : ResguardoService.porResguardante(resguardos, bienes)) {
            String clave = clavePalabras(p.nombre());
            // A name of only titles or one word can't tell two people apart.
            if (clave.contains(" ")) grupos.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
        }
        return grupos.entrySet().stream()
            .filter(e -> e.getValue().size() > 1 && !distintas.contains(e.getKey()))
            .sorted(Comparator.comparingInt((Map.Entry<String, List<Resguardante>> e) ->
                -e.getValue().stream().mapToInt(CalidadDatosService::bienesDe).sum()))
            .map(Map.Entry::getValue)
            .toList();
    }

    /** What the person holds: the resguardos in force and what is in their name without one. */
    public static int bienesDe(Resguardante p) {
        return p.resguardos().stream().filter(Resguardo::isActivo).mapToInt(r -> r.getItems() == null ? 0 : r.getItems().size()).sum()
            + p.sinResguardo().size();
    }

    /** The spelling proposed: the one with the most bienes, without a title if tied. */
    public static String nombrePropuesto(List<Resguardante> variantes) {
        return variantes.stream().max(Comparator.comparingInt(CalidadDatosService::bienesDe)
            .thenComparing(p -> -p.nombre().length())).map(Resguardante::nombre).orElse("");
    }

    /** Leaves {@code nombre} on the active bienes and the resguardos in force of these
     *  spellings. A cancelled resguardo stays as it was signed.
     *  @param inventario the active bienes, to find those of a resguardo in force too
     *  @return how many bienes changed name */
    public int unirPersonas(String nombre, List<Resguardante> variantes, List<Producto> inventario) throws SQLException {
        soloPatrimonio();
        conConexion();
        Set<String> claves = variantes.stream().map(v -> ResguardoService.clavePersona(v.nombre())).collect(Collectors.toSet());
        List<String> bienes = inventario.stream()
            .filter(p -> p.getResguardante() != null && !p.getResguardante().isBlank())
            .filter(p -> claves.contains(ResguardoService.clavePersona(p.getResguardante())))
            .filter(p -> !nombre.equals(p.getResguardante()))
            .map(Producto::getId).toList();
        List<String> resguardos = variantes.stream().flatMap(v -> v.resguardos().stream())
            .filter(Resguardo::isActivo).filter(r -> !nombre.equals(r.getResguardanteNombre()))
            .map(Resguardo::getId).toList();
        repo.unirPersona(nombre, bienes, resguardos);
        ProductosEnMemoria.invalidar();
        bienes.forEach(FichaPublica::actualizarLuego);   // the ficha of the QR names the resguardante
        String otros = variantes.stream().map(Resguardante::nombre).filter(n -> !n.equals(nombre))
            .collect(Collectors.joining(", "));
        audit.log("persona", ResguardoService.clavePersona(nombre), nombre, "actualizar",
            "Se unió «" + otros + "» en «" + nombre + "»: " + bienes.size() + " bienes y "
                + resguardos.size() + " resguardos vigentes cambiaron de nombre");
        return bienes.size();
    }

    private static final String DISTINTAS = "personas_distintas:";

    /** What was answered "no son la misma persona", so it isn't asked again. */
    public Set<String> personasDistintas() {
        try {
            return config.findAll().keySet().stream().filter(k -> k.startsWith(DISTINTAS))
                .map(k -> k.substring(DISTINTAS.length())).collect(Collectors.toSet());
        } catch (SQLException e) {
            return Set.of();
        }
    }

    public void marcarDistintas(List<Resguardante> variantes) throws SQLException {
        soloPatrimonio();
        config.set(DISTINTAS + clavePalabras(variantes.get(0).nombre()), "1");
    }

    // ── Vida útil ─────────────────────────────────────────────────────────────

    /** The bienes without vida útil whose CONAC account has one, by years. Equipo de
     *  defensa y seguridad has no value there, so it is left for someone to decide. */
    public static Map<Integer, List<Producto>> propuestaVidaUtil(List<Producto> bienes) {
        Map<Integer, List<Producto>> porAnios = new TreeMap<>();
        for (Producto p : bienes) {
            if (p.getVidaUtilAnios() != null) continue;
            Integer anios = VidaUtilConac.anios(p.getClaveArmonizada());
            if (anios != null) porAnios.computeIfAbsent(anios, k -> new ArrayList<>()).add(p);
        }
        return porAnios;
    }

    /** @return how many bienes took a vida útil */
    public int aplicarVidaUtil(Map<Integer, List<Producto>> propuesta) throws SQLException {
        soloPatrimonio();
        conConexion();
        int total = 0;
        for (var e : propuesta.entrySet()) {
            List<String> ids = e.getValue().stream().map(Producto::getId).toList();
            int n = repo.asignarVidaUtil(e.getKey(), ids);
            total += n;
            if (n > 0) audit.log("producto", ids.get(0), "Vida útil por CONAC", "actualizar",
                "Vida útil de " + e.getKey() + " años, según la clave CONAC, a " + n + " bienes que no tenían");
        }
        ProductosEnMemoria.invalidar();
        return total;
    }

    // ── Series repetidas ──────────────────────────────────────────────────────

    /** What gets typed when a bien has no serie: it isn't one. */
    private static final Set<String> NO_ES_SERIE = Set.of("S/N", "SN", "S N", "N/A", "NA", "SIN SERIE", "SIN NUMERO",
        "SIN NÚMERO", "NO TIENE", "NINGUNA", "-", "--", "0");

    public static boolean noEsSerie(String serie) {
        return serie != null && NO_ES_SERIE.contains(serie.trim().toUpperCase(Locale.ROOT));
    }

    /** A serie on more than one bien. */
    public record SerieRepetida(String serie, List<Producto> bienes) {
        public boolean noEsSerie() { return CalidadDatosService.noEsSerie(serie); }
        public long areas() { return bienes.stream().map(Producto::getArea).filter(Objects::nonNull).distinct().count(); }
    }

    /** Most bienes first; a serie is told apart by its text, not by case or spaces. */
    public static List<SerieRepetida> seriesRepetidas(List<Producto> bienes) {
        Map<String, List<Producto>> porSerie = new LinkedHashMap<>();
        for (Producto p : bienes) {
            if (p.getNumeroSerie() == null || p.getNumeroSerie().isBlank()) continue;
            porSerie.computeIfAbsent(p.getNumeroSerie().trim().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
        }
        return porSerie.entrySet().stream().filter(e -> e.getValue().size() > 1)
            .map(e -> new SerieRepetida(e.getKey(), List.copyOf(e.getValue())))
            .sorted(Comparator.comparingInt((SerieRepetida s) -> -s.bienes().size()).thenComparing(SerieRepetida::serie))
            .toList();
    }

    // ── Lo que le falta a un bien ─────────────────────────────────────────────

    /** What a bien can be missing, as the Completar datos screen asks for it. */
    public enum Dato {
        PRECIO("Sin precio"), FECHA("Sin fecha"), VIDA_UTIL("Sin vida útil"), FOTO("Sin foto");

        public final String etiqueta;
        Dato(String etiqueta) { this.etiqueta = etiqueta; }
    }

    public static boolean falta(Producto p, Dato dato) {
        return switch (dato) {
            case PRECIO -> p.getPrecioCompra() == null || p.getPrecioCompra().signum() == 0;
            case FECHA -> p.getFechaAdquisicion() == null;
            case VIDA_UTIL -> p.getVidaUtilAnios() == null;
            case FOTO -> p.getFotoUrl() == null || p.getFotoUrl().isBlank();
        };
    }

    public static int faltantes(Producto p) {
        int n = 0;
        for (Dato d : Dato.values()) if (falta(p, d)) n++;
        return n;
    }
}
