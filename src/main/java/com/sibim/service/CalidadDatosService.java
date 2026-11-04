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
        "MTRO", "MTRA", "PROF", "PROFR", "PROFRA", "C", "CP", "COMANDANTE", "ABOG",
        "LAP", "AP", "TSU", "MVZ", "ENCARGADA", "ENCARGADO");

    /** What two spellings of one person share: the words, in any order, without titles. */
    public static String clavePalabras(String nombre) {
        List<String> palabras = new ArrayList<>();
        // "A/B" and "A, B" are the same two people; "LIC.LAURA" is "LIC. LAURA" (a title
        // glued to the name), while "L.A.P" stays one title.
        for (String p : ResguardoService.clavePersona(nombre).replace('/', ' ')
                .replaceAll("\\.(?=\\p{L}{2})", ". ").trim().split(" +")) {
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
        juntarParecidos(grupos, distintas);
        return grupos.entrySet().stream()
            .filter(e -> e.getValue().size() > 1 && !distintas.contains(e.getKey()))
            .sorted(Comparator.comparingInt((Map.Entry<String, List<Resguardante>> e) ->
                -e.getValue().stream().mapToInt(CalidadDatosService::bienesDe).sum()))
            .map(Map.Entry::getValue)
            .toList();
    }

    /** TANIA and TANYA, M0ISES with a zero: a typing slip keeps the same person apart under
     *  other words. Only asked about: MARIO and MARIA also differ in one letter. */
    private static void juntarParecidos(Map<String, List<Resguardante>> grupos, Set<String> distintas) {
        List<String> claves = grupos.keySet().stream().filter(c -> !distintas.contains(c)).sorted().toList();
        for (int i = 0; i < claves.size(); i++) {
            List<Resguardante> a = grupos.get(claves.get(i));
            if (a == null) continue;
            for (int j = i + 1; j < claves.size(); j++) {
                if (grupos.containsKey(claves.get(j)) && casiIguales(claves.get(i), claves.get(j)))
                    a.addAll(grupos.remove(claves.get(j)));
            }
        }
    }

    /** The same words but one, which differs by a letter (two if it is a long one). */
    static boolean casiIguales(String claveA, String claveB) {
        String[] a = claveA.split(" "), b = claveB.split(" ");
        if (a.length != b.length) return false;
        int distintas = 0, cuanto = 0, largo = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i].equals(b[i])) continue;
            if (++distintas > 1) return false;
            cuanto = letrasDistintas(a[i], b[i]);
            largo = Math.min(a[i].length(), b[i].length());
        }
        return distintas == 1 && largo >= 4 && cuanto <= (largo >= 7 ? 2 : 1);
    }

    private static int letrasDistintas(String a, String b) {
        int[] previa = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previa[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] fila = new int[b.length() + 1];
            fila[0] = i;
            for (int j = 1; j <= b.length(); j++)
                fila[j] = Math.min(Math.min(previa[j] + 1, fila[j - 1] + 1), previa[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previa = fila;
        }
        return previa[b.length()];
    }

    /** Of {@code conocidos}, the person {@code escrito} probably is without being written
     *  the same: the same words with a title more or less or in another order ("LIC.LAURA
     *  PRUEBA ORTEGA" for "LIC. LAURA PRUEBA ORTEGA"), or a letter apart ("TANYA" for
     *  "TANIA"). Empty when it is written exactly as one of them, or when none is close —
     *  a new person. Asked before the name is saved, so no new spelling starts. */
    public static java.util.Optional<String> parecidoA(String escrito, java.util.Collection<String> conocidos) {
        if (escrito == null || escrito.isBlank() || conocidos == null) return java.util.Optional.empty();
        String tal = ResguardoService.clavePersona(escrito);
        String clave = clavePalabras(escrito);
        if (!clave.contains(" ")) return java.util.Optional.empty();
        String igual = null, parecido = null;
        for (String c : conocidos) {
            if (c == null || c.isBlank()) continue;
            if (ResguardoService.clavePersona(c).equals(tal)) return java.util.Optional.empty();
            String k = clavePalabras(c);
            if (igual == null && k.equals(clave)) igual = c.trim();
            else if (parecido == null && casiIguales(k, clave)) parecido = c.trim();
        }
        return java.util.Optional.ofNullable(igual != null ? igual : parecido);
    }

    /** True when all of these spell the same words; false for ones that only look alike. */
    public static boolean mismasPalabras(List<Resguardante> variantes) {
        return variantes.stream().map(v -> clavePalabras(v.nombre())).distinct().count() <= 1;
    }

    /** "ANTONIO EJEMPLO ROMO/ MISAEL PRUEBA GARCIA": two people in one name, each of at
     *  least two words ("LOPEZ, JUAN" is one). A resguardo there is signed by both or by none. */
    public static boolean dosPersonas(String nombre) {
        if (nombre == null) return false;
        String[] partes = nombre.trim().split("[,/]");
        if (partes.length < 2) return false;
        return java.util.Arrays.stream(partes).filter(p -> p.trim().split("\\s+").length >= 2).count() >= 2;
    }

    /** What the person holds: the resguardos in force and what is in their name without one. */
    public static int bienesDe(Resguardante p) {
        return p.resguardos().stream().filter(Resguardo::isActivo).mapToInt(r -> r.getItems() == null ? 0 : r.getItems().size()).sum()
            + p.sinResguardo().size();
    }

    /** The spelling proposed: the one with the most bienes, without a title if tied. */
    public static String nombrePropuesto(List<Resguardante> variantes) {
        // A digit in a name is a typing slip, however many bienes it holds (M0ISES).
        return variantes.stream().max(Comparator.comparing((Resguardante p) -> p.nombre().chars().noneMatch(Character::isDigit))
            .thenComparingInt(CalidadDatosService::bienesDe)
            .thenComparing(p -> -p.nombre().length())).map(Resguardante::nombre).orElse("");
    }

    /** What a union changed, by the name each thing had: enough to put it back. */
    public record Union(String nombre, Map<String, List<String>> bienes, Map<String, List<String>> resguardos) {
        public int cuantosBienes() { return bienes.values().stream().mapToInt(List::size).sum(); }
    }

    /** Leaves {@code nombre} on the active bienes and the resguardos in force of these
     *  spellings. A cancelled resguardo stays as it was signed.
     *  @param inventario the active bienes, to find those of a resguardo in force too
     *  @return what changed, for {@link #deshacer} */
    public Union unirPersonas(String nombre, List<Resguardante> variantes, List<Producto> inventario) throws SQLException {
        soloPatrimonio();
        conConexion();
        Set<String> claves = variantes.stream().map(v -> ResguardoService.clavePersona(v.nombre())).collect(Collectors.toSet());
        Map<String, List<String>> bienesAntes = inventario.stream()
            .filter(p -> p.getResguardante() != null && !p.getResguardante().isBlank())
            .filter(p -> claves.contains(ResguardoService.clavePersona(p.getResguardante())))
            .filter(p -> !nombre.equals(p.getResguardante()))
            .collect(Collectors.groupingBy(Producto::getResguardante, LinkedHashMap::new,
                Collectors.mapping(Producto::getId, Collectors.toList())));
        Map<String, List<String>> resguardosAntes = variantes.stream().flatMap(v -> v.resguardos().stream())
            .filter(Resguardo::isActivo).filter(r -> !nombre.equals(r.getResguardanteNombre()))
            .collect(Collectors.groupingBy(Resguardo::getResguardanteNombre, LinkedHashMap::new,
                Collectors.mapping(Resguardo::getId, Collectors.toList())));
        List<String> bienes = bienesAntes.values().stream().flatMap(List::stream).toList();
        List<String> resguardos = resguardosAntes.values().stream().flatMap(List::stream).toList();
        repo.unirPersona(nombre, bienes, resguardos);
        ProductosEnMemoria.invalidar();
        bienes.forEach(FichaPublica::actualizarLuego);   // the ficha of the QR names the resguardante
        String otros = variantes.stream().map(Resguardante::nombre).filter(n -> !n.equals(nombre))
            .collect(Collectors.joining(", "));
        audit.log("persona", ResguardoService.clavePersona(nombre), nombre, "actualizar",
            "Se unió «" + otros + "» en «" + nombre + "»: " + bienes.size() + " bienes y "
                + resguardos.size() + " resguardos vigentes cambiaron de nombre");
        return new Union(nombre, bienesAntes, resguardosAntes);
    }

    /** Puts back the name each thing had before {@link #unirPersonas}. */
    public void deshacer(Union u) throws SQLException {
        soloPatrimonio();
        conConexion();
        Set<String> nombres = new LinkedHashSet<>(u.bienes().keySet());
        nombres.addAll(u.resguardos().keySet());
        for (String antes : nombres) {
            List<String> bienes = u.bienes().getOrDefault(antes, List.of());
            repo.unirPersona(antes, bienes, u.resguardos().getOrDefault(antes, List.of()));
            bienes.forEach(FichaPublica::actualizarLuego);
        }
        ProductosEnMemoria.invalidar();
        audit.log("persona", ResguardoService.clavePersona(u.nombre()), u.nombre(), "actualizar",
            "Se deshizo la unión: regresan los nombres «" + String.join(", ", nombres) + "»");
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
        // Each spelling: ones that only look alike have more than one.
        for (String clave : variantes.stream().map(v -> clavePalabras(v.nombre())).collect(Collectors.toSet()))
            config.set(DISTINTAS + clave, "1");
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
        "SIN NÚMERO", "NO TIENE", "NINGUNA", "-", "--", "0", "S/SERIE", "S/S", "PND");

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

    /** Leaves all these bienes without serie at once: "1215" on 54 chairs was 54 clicks.
     *  The audit says which ones and what they had, so it can be told apart afterwards.
     *  @return how many changed */
    public int quitarSerie(String serie, List<Producto> bienes) throws SQLException {
        soloPatrimonio();
        conConexion();
        List<String> ids = bienes.stream().map(Producto::getId).toList();
        int n = repo.quitarSerie(ids);
        ProductosEnMemoria.invalidar();
        ids.forEach(FichaPublica::actualizarLuego);   // the ficha of the QR prints the serie
        String codigos = bienes.stream().map(Producto::getCodigo).limit(60).collect(Collectors.joining(", "));
        audit.log("producto", ids.get(0), "Serie «" + serie + "»", "actualizar",
            "Se quitó la serie «" + serie + "» a " + n + " bienes: " + codigos + (bienes.size() > 60 ? "…" : ""));
        return n;
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

    // ── Por hacer ─────────────────────────────────────────────────────────────

    /** Something the inventory still needs and where to fix it.
     *  @param vista the screen
     *  @param abrir the window to open on arriving, or null */
    public record PorHacer(long cantidad, String titulo, String accion, String vista, String abrir) {}

    /** What is left to do, with its count; what is done doesn't appear. */
    public static List<PorHacer> porHacer(List<Producto> bienes, List<Resguardo> resguardos, Set<String> distintas, Map<String, String> config) {
        List<PorHacer> out = new ArrayList<>();
        agregar(out, personasRepetidas(resguardos, bienes, distintas).size(), "personas con el nombre repetido", "Unir personas", "productos", "unir");
        agregar(out, seriesRepetidas(bienes).size(), "series repetidas", "Revisar series", "productos", "series");
        agregar(out, cuantos(bienes, Dato.PRECIO), "bienes sin precio", "Completar datos", "productos", "completar:PRECIO");
        agregar(out, cuantos(bienes, Dato.FECHA), "bienes sin fecha", "Completar datos", "productos", "completar:FECHA");
        agregar(out, cuantos(bienes, Dato.VIDA_UTIL), "bienes sin vida útil", "Completar datos", "productos", "completar:VIDA_UTIL");
        agregar(out, cuantos(bienes, Dato.FOTO), "bienes sin foto", "Completar datos", "productos", "completar:FOTO");
        agregar(out, ResguardoService.porResguardante(resguardos, bienes).stream().filter(p -> !p.sinResguardo().isEmpty()).count(),
            "personas con bienes sin resguardo", "Registrar en lote", "resguardos", "lote");
        agregar(out, conVariosResguardos(resguardos), "personas con varios resguardos en la misma área",
            "Juntar resguardos", "resguardos", "juntar");
        agregar(out, bienes.stream().filter(p -> p.getFechaBaja() == null && !p.isEtiquetado()).count(),
            "bienes sin etiquetar", "Etiquetas por área", "productos", "etiquetas");
        agregar(out, faltanEnConfiguracion(config, com.sibim.config.Areas.getAllAreaNames().size()).size(),
            "datos faltan para los formatos", "Ir a Configuración", "configuracion", null);
        return out;
    }

    /** People with two or more resguardos in force in the same área (Juntar resguardos). */
    static long conVariosResguardos(List<Resguardo> resguardos) {
        return resguardos.stream()
            .filter(r -> r.isActivo() && r.getResguardanteNombre() != null && !r.getResguardanteNombre().isBlank())
            .collect(Collectors.groupingBy(r -> ResguardoService.clavePersona(r.getResguardanteNombre()) + "|" + r.getResguardanteArea(),
                Collectors.counting()))
            .values().stream().filter(n -> n > 1).count();
    }

    private static void agregar(List<PorHacer> out, long n, String titulo, String accion, String vista, String abrir) {
        if (n > 0) out.add(new PorHacer(n, titulo, accion, vista, abrir));
    }

    private static long cuantos(List<Producto> bienes, Dato d) {
        return bienes.stream().filter(p -> p.getFechaBaja() == null && falta(p, d)).count();
    }

    /** What the official formats print and nobody has typed, in the screen's words. */
    public static List<String> faltanEnConfiguracion(Map<String, String> config, int areas) {
        List<String> faltan = new ArrayList<>();
        if (vacio(config, ReporteResguardoVehicularService.CLAVE_RFC)) faltan.add("RFC");
        if (vacio(config, ReporteResguardoVehicularService.CLAVE_DOMICILIO)) faltan.add("Domicilio");
        if (vacio(config, ReporteResguardoVehicularService.CLAVE_ELABORA)) faltan.add("Elabora");
        if (vacio(config, ReporteResguardoVehicularService.CLAVE_VOBO)) faltan.add("Vo. Bo.");
        long titulares = config.entrySet().stream()
            .filter(e -> e.getKey().startsWith(TitularesArea.PREFIJO) && e.getValue() != null && !e.getValue().isBlank()).count();
        if (titulares < areas) faltan.add("Titular de " + (areas - titulares) + (areas - titulares == 1 ? " área" : " áreas"));
        return faltan;
    }

    private static boolean vacio(Map<String, String> config, String clave) {
        String v = config.get(clave);
        return v == null || v.isBlank();
    }
}
