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
    private static final Set<String> TITULOS = com.sibim.util.NombrePersona.TITULOS;

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
        Union u = aplicarNombre(nombre, variantes, inventario);
        String otros = variantes.stream().map(Resguardante::nombre).filter(n -> !n.equals(nombre))
            .collect(Collectors.joining(", "));
        audit.log("persona", ResguardoService.clavePersona(nombre), nombre, "actualizar",
            "Se unió «" + otros + "» en «" + nombre + "»: " + u.cuantosBienes() + " bienes y "
                + u.resguardos().values().stream().mapToInt(List::size).sum() + " resguardos vigentes cambiaron de nombre");
        return u;
    }

    /** {@code nombre} on the active bienes and resguardos in force of these spellings. */
    private Union aplicarNombre(String nombre, List<Resguardante> variantes, List<Producto> inventario) throws SQLException {
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
        return new Union(nombre, bienesAntes, resguardosAntes);
    }

    /** Puts back the name each thing had before {@link #unirPersonas}. */
    public void deshacer(Union u) throws SQLException {
        soloPatrimonio();
        conConexion();
        Set<String> nombres = regresarNombres(u);
        audit.log("persona", ResguardoService.clavePersona(u.nombre()), u.nombre(), "actualizar",
            "Se deshizo la unión: regresan los nombres «" + String.join(", ", nombres) + "»");
    }

    /** The name each bien and resguardo had before; returns those names. */
    private Set<String> regresarNombres(Union u) throws SQLException {
        Set<String> nombres = new LinkedHashSet<>(u.bienes().keySet());
        nombres.addAll(u.resguardos().keySet());
        for (String antes : nombres) {
            List<String> bienes = u.bienes().getOrDefault(antes, List.of());
            repo.unirPersona(antes, bienes, u.resguardos().getOrDefault(antes, List.of()));
            bienes.forEach(FichaPublica::actualizarLuego);
        }
        ProductosEnMemoria.invalidar();
        return nombres;
    }

    // ── Costo de los lotes (6-nov-2026) ───────────────────────────────────────

    /** The active bienes of several pieces with a cost, largest total first: where a
     *  factura's total captured as the cost of one piece weighs the most. */
    public static List<Producto> lotesPorRevisar(List<Producto> bienes) {
        return bienes.stream()
            .filter(p -> p.getFechaBaja() == null && p.getStockActual() > 1)
            .filter(p -> p.getPrecioCompra() != null && p.getPrecioCompra().signum() > 0)
            .sorted(Comparator.comparing(Producto::getValorTotal).reversed())
            .toList();
    }

    /** The cost of one piece when {@code totalDelLote} is what the whole lote cost, to
     *  the cent; null when there is nothing to divide. */
    public static java.math.BigDecimal costoPorPieza(java.math.BigDecimal totalDelLote, int piezas) {
        if (totalDelLote == null || totalDelLote.signum() <= 0 || piezas < 2) return null;
        return totalDelLote.divide(java.math.BigDecimal.valueOf(piezas), 2, java.math.RoundingMode.HALF_UP);
    }

    // ── Corregir el nombre de una persona (6-nov-2026) ────────────────────────

    /** A name fixed everywhere, and what it was in each place, to put it back. */
    public record Correccion(Union union, Map<String, String> titularesAntes) {
        public int cuantosResguardos() { return union.resguardos().values().stream().mapToInt(List::size).sum(); }
    }

    /** Leaves {@code nuevo} wherever this person's name is written — active bienes,
     *  resguardos in force, titular of an área — for a typo such as "GÓNZALEZ" in all
     *  of them at once instead of bien by bien. A cancelled resguardo and a bien de baja
     *  stay as they were signed.
     *  @param inventario the active bienes, to find those of a resguardo in force too */
    public Correccion corregirNombre(Resguardante persona, String nuevo, List<Producto> inventario) throws SQLException {
        soloPatrimonio();
        conConexion();
        String nombre = nuevo == null ? "" : nuevo.trim().replaceAll("\\s+", " ");
        if (nombre.isEmpty()) throw new IllegalArgumentException("Escribe el nombre como debe quedar");
        if (nombre.equals(persona.nombre())) throw new IllegalArgumentException("El nombre no cambió");
        Union u = aplicarNombre(nombre, List.of(persona), inventario);
        String clave = ResguardoService.clavePersona(persona.nombre());
        Map<String, String> titularesAntes = new LinkedHashMap<>();
        titularesDe(config.findAll()).forEach((area, t) -> {
            if (ResguardoService.clavePersona(t).equals(clave) && !t.equals(nombre)) titularesAntes.put(area, t);
        });
        for (String area : titularesAntes.keySet()) config.set(TitularesArea.PREFIJO + area, nombre);
        audit.log("persona", ResguardoService.clavePersona(nombre), nombre, "actualizar",
            "Nombre corregido: «" + persona.nombre() + "» queda como «" + nombre + "» en "
                + com.sibim.util.FormatUtils.plural(u.cuantosBienes(), "bien", "bienes") + ", "
                + com.sibim.util.FormatUtils.plural(u.resguardos().values().stream().mapToInt(List::size).sum(),
                    "resguardo vigente", "resguardos vigentes")
                + (titularesAntes.isEmpty() ? "" : " y como titular de " + String.join(", ", titularesAntes.keySet())));
        return new Correccion(u, titularesAntes);
    }

    /** Puts back what {@link #corregirNombre} changed. */
    public void deshacerCorreccion(Correccion c) throws SQLException {
        soloPatrimonio();
        conConexion();
        regresarNombres(c.union());
        for (var t : c.titularesAntes().entrySet()) config.set(TitularesArea.PREFIJO + t.getKey(), t.getValue());
        audit.log("persona", ResguardoService.clavePersona(c.union().nombre()), c.union().nombre(), "actualizar",
            "Se deshizo la corrección del nombre «" + c.union().nombre() + "»");
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

    // ── Títulos en los nombres ────────────────────────────────────────────────

    /** A name written with a title in front, the name without it, and where it is
     *  written like that: active bienes, resguardos in force, titular of áreas. */
    public record ConTitulo(String antes, String despues, List<String> bienes, List<String> resguardos,
                            List<String> titularDe) {
        public String donde() {
            List<String> partes = new ArrayList<>();
            if (!bienes.isEmpty()) partes.add(com.sibim.util.FormatUtils.plural(bienes.size(), "bien", "bienes"));
            if (!resguardos.isEmpty()) partes.add(com.sibim.util.FormatUtils.plural(resguardos.size(), "resguardo", "resguardos"));
            if (!titularDe.isEmpty()) partes.add("titular de " + String.join(", ", titularDe));
            return String.join(" · ", partes);
        }
    }

    /** The names of people with "LIC.", "ING.", "ARQ."… in front. A cancelled
     *  resguardo and a bien de baja stay as they were signed. */
    public static List<ConTitulo> nombresConTitulo(List<Producto> bienes, List<Resguardo> resguardos,
                                                   Map<String, String> titulares) {
        Map<String, ConTitulo> por = new java.util.TreeMap<>();
        java.util.function.Function<String, ConTitulo> de = n -> por.computeIfAbsent(n, k ->
            new ConTitulo(k, com.sibim.util.NombrePersona.sinTitulos(k), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        for (Producto p : bienes)
            if (p.getFechaBaja() == null && com.sibim.util.NombrePersona.tieneTitulo(p.getResguardante()))
                de.apply(p.getResguardante()).bienes().add(p.getId());
        for (Resguardo r : resguardos)
            if (r.isActivo() && com.sibim.util.NombrePersona.tieneTitulo(r.getResguardanteNombre()))
                de.apply(r.getResguardanteNombre()).resguardos().add(r.getId());
        titulares.forEach((area, t) -> {
            if (com.sibim.util.NombrePersona.tieneTitulo(t)) de.apply(t).titularDe().add(area);
        });
        return new ArrayList<>(por.values());
    }

    /** The titulares out of the whole configuration (key → value). */
    static Map<String, String> titularesDe(Map<String, String> config) {
        Map<String, String> out = new LinkedHashMap<>();
        config.forEach((k, v) -> {
            if (k.startsWith(TitularesArea.PREFIJO) && v != null && !v.isBlank())
                out.put(k.substring(TitularesArea.PREFIJO.length()), v.trim());
        });
        return out;
    }

    /** Leaves only the name in each of these places, all at once.
     *  @return what to give {@link #ponerTitulos} to put them back */
    public List<ConTitulo> quitarTitulos(List<ConTitulo> nombres) throws SQLException {
        soloPatrimonio();
        conConexion();
        repo.renombrar(nombres.stream().map(c -> new CalidadDatosRepository.Renombre(c.despues(), c.bienes(), c.resguardos())).toList());
        for (ConTitulo c : nombres)
            for (String area : c.titularDe()) config.set(TitularesArea.PREFIJO + area, c.despues());
        ProductosEnMemoria.invalidar();
        nombres.forEach(c -> c.bienes().forEach(FichaPublica::actualizarLuego));
        audit.log("persona", null, "Títulos en los nombres", "actualizar",
            "Se quitaron los títulos (Lic., Ing., Arq.…) de " + com.sibim.util.FormatUtils.plural(nombres.size(), "nombre", "nombres")
                + ": " + nombres.stream().mapToInt(c -> c.bienes().size()).sum() + " bienes, "
                + nombres.stream().mapToInt(c -> c.resguardos().size()).sum() + " resguardos vigentes y "
                + nombres.stream().mapToInt(c -> c.titularDe().size()).sum() + " titulares de área");
        return nombres;
    }

    /** Puts back the names {@link #quitarTitulos} changed. */
    public void ponerTitulos(List<ConTitulo> nombres) throws SQLException {
        soloPatrimonio();
        conConexion();
        repo.renombrar(nombres.stream().map(c -> new CalidadDatosRepository.Renombre(c.antes(), c.bienes(), c.resguardos())).toList());
        for (ConTitulo c : nombres)
            for (String area : c.titularDe()) config.set(TitularesArea.PREFIJO + area, c.antes());
        ProductosEnMemoria.invalidar();
        nombres.forEach(c -> c.bienes().forEach(FichaPublica::actualizarLuego));
        audit.log("persona", null, "Títulos en los nombres", "actualizar",
            "Se deshizo quitar los títulos: regresan " + com.sibim.util.FormatUtils.plural(nombres.size(), "nombre", "nombres"));
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

    /** One vida útil for several bienes at once: what the CONAC leaves to the municipio
     *  (equipo de defensa y seguridad: 357 bienes on 4-nov-2026, esposas, chalecos,
     *  cascos…) would otherwise be typed bien by bien. A bien that has one keeps it.
     *  @return how many took it */
    public int asignarVidaUtil(int anios, List<Producto> bienes) throws SQLException {
        soloPatrimonio();
        conConexion();
        List<Producto> sin = bienes.stream().filter(p -> p.getVidaUtilAnios() == null).toList();
        if (sin.isEmpty()) return 0;
        int n = repo.asignarVidaUtil(anios, sin.stream().map(Producto::getId).toList());
        if (n > 0) {
            String codigos = sin.stream().limit(60).map(p -> Objects.requireNonNullElse(p.getCodigo(), p.getId()))
                .collect(Collectors.joining(", ")) + (sin.size() > 60 ? " y " + (sin.size() - 60) + " más" : "");
            audit.log("producto", sin.get(0).getId(), "Vida útil a varios bienes", "actualizar",
                "Vida útil de " + anios + " años, capturada a la vez para " + n + " bienes: " + codigos);
        }
        ProductosEnMemoria.invalidar();
        return n;
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

    /** What Completar datos captures. The first four decide the totals and the depreciation;
     *  the rest were typed in the form bien by bien (from 28-oct to 11-nov-2026, 622 edits,
     *  «MUEBLE» 205 times), and are captured in the list too since 1.0.26. */
    public enum Dato {
        PRECIO("Sin precio", null), FECHA("Sin fecha", null), VIDA_UTIL("Sin vida útil", null), FOTO("Sin foto", null),
        PROVEEDOR("Sin proveedor", CalidadDatosRepository.CampoTexto.PROVEEDOR),
        FOLIO("Sin folio fiscal", CalidadDatosRepository.CampoTexto.FOLIO_FISCAL),
        /** A folio fiscal typed with another shape (12-nov-2026: 25 of 288, an O for a zero,
         *  a character short, the factura's own number): corrected in the list. */
        FOLIO_REVISAR("Folio fiscal por revisar", CalidadDatosRepository.CampoTexto.FOLIO_FISCAL),
        FACTURA("Sin factura", CalidadDatosRepository.CampoTexto.FACTURA),
        COLOR("Sin color", CalidadDatosRepository.CampoTexto.COLOR),
        TIPO("Sin tipo de bien", CalidadDatosRepository.CampoTexto.TIPO_BIEN);

        public final String etiqueta;
        /** Null for the four that are not plain text. */
        public final CalidadDatosRepository.CampoTexto campo;
        Dato(String etiqueta, CalidadDatosRepository.CampoTexto campo) { this.etiqueta = etiqueta; this.campo = campo; }

        public boolean esTexto() { return campo != null; }
    }

    public static boolean falta(Producto p, Dato dato) {
        return switch (dato) {
            case PRECIO -> p.getPrecioCompra() == null || p.getPrecioCompra().signum() == 0;
            case FECHA -> p.getFechaAdquisicion() == null;
            case VIDA_UTIL -> p.getVidaUtilAnios() == null;
            case FOTO -> p.getFotoUrl() == null || p.getFotoUrl().isBlank();
            case PROVEEDOR -> vacio(p.getProveedor());
            case FOLIO -> vacio(p.getFolioFiscal());
            case FOLIO_REVISAR -> problemaFolio(p.getFolioFiscal()) != null;
            case FACTURA -> vacio(p.getNumeroFactura());
            case COLOR -> vacio(p.getColor());
            case TIPO -> vacio(p.getTipoBien());
        };
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /** Of the data that decides the totals and the depreciation (price, date, vida útil, photo). */
    public static int faltantes(Producto p) {
        int n = 0;
        for (Dato d : List.of(Dato.PRECIO, Dato.FECHA, Dato.VIDA_UTIL, Dato.FOTO)) if (falta(p, d)) n++;
        return n;
    }

    /** Puts a text dato on a bien (the row of Completar datos). */
    public static void poner(Producto p, Dato dato, String valor) {
        if (!dato.esTexto()) throw new IllegalArgumentException(dato.name());
        switch (dato) {
            case PROVEEDOR -> p.setProveedor(valor);
            case FOLIO, FOLIO_REVISAR -> p.setFolioFiscal(valor);
            case FACTURA -> p.setNumeroFactura(valor);
            case COLOR -> p.setColor(valor);
            case TIPO -> p.setTipoBien(valor);
            default -> { }
        }
    }

    /** What is wrong with a folio fiscal, said for whoever has the factura at hand; null when
     *  it is empty or has the shape of a UUID (32 hexadecimal characters, 8-4-4-4-12). */
    public static String problemaFolio(String folio) {
        if (folio == null || folio.isBlank() || Producto.pareceFolioFiscal(folio)) return null;
        String t = folio.trim();
        if (t.length() <= 12)
            return "parece el número de la factura, no su folio fiscal (que tiene 32 caracteres)";
        List<String> partes = new ArrayList<>();
        if (t.matches("(?i).*O.*")) partes.add("lleva la letra O donde va un cero");
        if (t.matches(".*[IiLl].*")) partes.add("lleva una letra I o L donde va un uno");
        Set<String> otras = new TreeSet<>();
        for (char c : t.toCharArray())
            if (!Character.isDigit(c) && "ABCDEFabcdef-OoIiLl".indexOf(c) < 0) otras.add(c == ' ' ? "espacios" : "«" + c + "»");
        if (!otras.isEmpty()) partes.add("lleva " + String.join(", ", otras) + ", que no van en un folio fiscal");
        String digitos = t.replaceAll("[Oo]", "0").replaceAll("[IiLl]", "1").replaceAll("[^0-9A-Fa-f]", "");
        String donde = dondeEstaElError(t);
        if (digitos.length() != 32) partes.add("tiene " + digitos.length() + " de los 32 caracteres" + (donde == null ? "" : ": " + donde));
        else if (partes.isEmpty()) partes.add(donde != null ? donde : "los guiones no están en su lugar (8-4-4-4-12)");
        return String.join("; ", partes);
    }

    private static final int[] GRUPOS_UUID = {8, 4, 4, 4, 12};
    private static final String[] NOMBRE_GRUPO = {"primer", "segundo", "tercer", "cuarto", "último"};

    /** Which group of a folio written with dashes is short or long (17-nov-2026: of 34 folios typed
     *  wrong in four days, 16 had the last group with 11 of its 12): «al último grupo le falta uno».
     *  Null without dashes or when every group has its length. */
    static String dondeEstaElError(String folio) {
        if (folio == null || !folio.contains("-")) return null;
        String[] g = folio.trim().split("-", -1);
        if (g.length != GRUPOS_UUID.length)
            return "tiene " + g.length + " grupos separados por guiones y van 5 (8-4-4-4-12)";
        List<String> dif = new ArrayList<>();
        for (int i = 0; i < g.length; i++) {
            int d = g[i].trim().length() - GRUPOS_UUID[i];
            if (d == 0) continue;
            int k = Math.abs(d);
            dif.add("al " + NOMBRE_GRUPO[i] + " grupo " + (d < 0 ? (k == 1 ? "le falta uno" : "le faltan " + k)
                : (k == 1 ? "le sobra uno" : "le sobran " + k)));
        }
        return dif.isEmpty() ? null : String.join(" y ", dif);
    }

    /** What a text dato is written as: trimmed, spaces collapsed, in capitals as the
     *  inventory writes it (the folio fiscal too: it is the UUID of the CFDI). */
    public static String normalizarTexto(Dato dato, String texto) {
        if (texto == null) return null;
        String t = texto.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return null;
        if (dato == Dato.FOLIO || dato == Dato.FOLIO_REVISAR) return escribirFolio(t.replace(" ", ""));
        return t.toUpperCase(Locale.forLanguageTag("es-MX"));
    }

    /** As a folio fiscal is written: capitals, and an O as a zero, an I or an L as a one — only
     *  0-9 and A-F go in a UUID (17-nov-2026: 7 folios had an O). Same length, so it can be
     *  applied while typing. */
    public static String escribirFolio(String texto) {
        if (texto == null) return null;
        return texto.toUpperCase(Locale.ROOT).replace('O', '0').replace('I', '1').replace('L', '1');
    }

    /** «MUEBLE» is what Patrimonio writes for every bien that is not a vehicle (243 of 249
     *  on 11-nov-2026, from furniture to computers and tools); a vehicle has its own kind
     *  (CAMIONETA, MOTOCICLETA…), so it gets no suggestion. Null without a CONAC account. */
    public static String tipoSugerido(Producto p) {
        return tipoSugerido(p.getClaveArmonizada());
    }

    /** {@link #tipoSugerido(Producto)} for a CONAC account (the bien's form, as it is typed). */
    public static String tipoSugerido(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String c = clave.trim();
        if (c.startsWith("1.2.4.4.1") || c.startsWith("1.2.4.4.2")) return null;
        return c.startsWith("1.2.4") ? "MUEBLE" : null;
    }

    // ── Proveedores escritos de varias formas ─────────────────────────────────

    /** One way a proveedor is written that becomes {@code queda}, on {@code bienes} bienes. */
    public record ProveedorRepetido(String variante, int bienes, String queda) {}

    /** What was changed, to undo it. */
    public record ProveedorUnido(ProveedorRepetido cambio, List<String> ids) {}

    /** How the company is written at the end: «SA DE CV», «S.A. DE C.V.», «S DE RL»… */
    private static final Set<String> SOCIEDAD = Set.of("SA", "S", "A", "DE", "CV", "C", "V", "SAPI", "RL", "SC", "AC", "SPR", "SAS");

    /** The proveedor as compared: capitals, no accents or punctuation, and without the kind
     *  of company at the end («QUI ASESORIA… SA DE CV» is «QUI ASESORIA…»). */
    static String claveProveedor(String proveedor) {
        if (proveedor == null) return "";
        String t = java.text.Normalizer.normalize(proveedor, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
        List<String> palabras = new ArrayList<>(List.of(t.isEmpty() ? new String[0] : t.split(" ")));
        while (palabras.size() > 2 && SOCIEDAD.contains(palabras.get(palabras.size() - 1))) palabras.remove(palabras.size() - 1);
        return String.join(" ", palabras);
    }

    /** Proveedores written in more than one way (12-nov-2026: «QUI ASESORIA Y LOGISTICA EN
     *  COMERCIALIZACION» 23 times and with «SA DE CV» 24; «SIMON PRUEBA SALVADOR» once and with
     *  «SERRANO» 41). The same name without the kind of company, or one that is the start of
     *  the other (three words at least). The most complete way stays; ties, the most used. */
    public static List<ProveedorRepetido> proveedoresRepetidos(List<Producto> bienes) {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (Producto p : bienes)
            if (p.getProveedor() != null && !p.getProveedor().isBlank()) cuenta.merge(p.getProveedor(), 1, Integer::sum);
        List<String> formas = new ArrayList<>(cuenta.keySet());
        int[] grupo = new int[formas.size()];
        for (int i = 0; i < grupo.length; i++) grupo[i] = i;
        for (int i = 0; i < formas.size(); i++)
            for (int j = i + 1; j < formas.size(); j++)
                if (mismoProveedor(claveProveedor(formas.get(i)), claveProveedor(formas.get(j)))) {
                    int de = grupo[j], a = grupo[i];
                    for (int k = 0; k < grupo.length; k++) if (grupo[k] == de) grupo[k] = a;
                }
        Map<Integer, List<String>> grupos = new LinkedHashMap<>();
        for (int i = 0; i < formas.size(); i++) grupos.computeIfAbsent(grupo[i], k -> new ArrayList<>()).add(formas.get(i));
        List<ProveedorRepetido> out = new ArrayList<>();
        for (List<String> g : grupos.values()) {
            if (g.size() < 2) continue;
            String queda = g.stream().max(Comparator.comparingInt((String s) -> s.trim().length())
                .thenComparingInt(cuenta::get)).orElseThrow();
            for (String v : g) if (!v.equals(queda)) out.add(new ProveedorRepetido(v, cuenta.get(v), queda));
        }
        out.sort(Comparator.comparing(ProveedorRepetido::queda).thenComparing(ProveedorRepetido::variante));
        return out;
    }

    private static boolean mismoProveedor(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        String corto = a.length() <= b.length() ? a : b, largo = corto == a ? b : a;
        return corto.split(" ").length >= 3 && largo.startsWith(corto + " ");
    }

    /** Writes each proveedor as it {@code queda}, on every bien that had that way.
     *  @return what changed, for {@link #separarProveedores} */
    public List<ProveedorUnido> unirProveedores(List<ProveedorRepetido> cambios) throws SQLException {
        soloPatrimonio();
        conConexion();
        List<ProveedorUnido> hecho = new ArrayList<>();
        for (ProveedorRepetido c : cambios) {
            List<String> ids = repo.cambiarProveedor(c.variante(), c.queda());
            if (ids.isEmpty()) continue;
            hecho.add(new ProveedorUnido(c, ids));
            audit.log("producto", ids.get(0), "Varios bienes: proveedor", "actualizar",
                "Proveedor unido: «" + c.variante() + "» se escribe «" + c.queda() + "» en " + com.sibim.util.FormatUtils.plural(ids.size(), "bien", "bienes"));
        }
        ProductosEnMemoria.invalidar();
        return hecho;
    }

    /** Undo of {@link #unirProveedores}, on the bienes that still say what it wrote. */
    public int separarProveedores(List<ProveedorUnido> hecho) throws SQLException {
        soloPatrimonio();
        conConexion();
        int n = 0;
        for (ProveedorUnido u : hecho) n += repo.regresarProveedor(u.ids(), u.cambio().variante(), u.cambio().queda());
        if (n > 0 && !hecho.isEmpty()) audit.log("producto", hecho.get(0).ids().get(0), "Varios bienes: proveedor", "actualizar",
            "Se deshizo unir proveedores: " + com.sibim.util.FormatUtils.plural(n, "bien regresó", "bienes regresaron") + " a como estaban escritos");
        ProductosEnMemoria.invalidar();
        return n;
    }

    // ── Lo escrito de dos formas (proveedor, marca, color, tipo) ──────────────

    /** A dato «Unir lo escrito de dos formas» looks at. */
    public enum Campo {
        PROVEEDOR("Proveedor", CalidadDatosRepository.CampoTexto.PROVEEDOR, Producto::getProveedor),
        MARCA("Marca", CalidadDatosRepository.CampoTexto.MARCA, Producto::getMarca),
        COLOR("Color", CalidadDatosRepository.CampoTexto.COLOR, Producto::getColor),
        TIPO("Tipo de bien", CalidadDatosRepository.CampoTexto.TIPO_BIEN, Producto::getTipoBien);

        public final String etiqueta;
        final CalidadDatosRepository.CampoTexto campo;
        final java.util.function.Function<Producto, String> leer;

        Campo(String etiqueta, CalidadDatosRepository.CampoTexto campo, java.util.function.Function<Producto, String> leer) {
            this.etiqueta = etiqueta;
            this.campo = campo;
            this.leer = leer;
        }
    }

    /** One way {@code campo} is written that becomes {@code queda}, on {@code bienes} bienes. */
    public record Repetido(Campo campo, String variante, int bienes, String queda) {}

    /** What was changed, to undo it. */
    public record Unido(Repetido cambio, List<String> ids) {}

    /** Every dato written in two ways: the proveedores by their own rules ({@link #proveedoresRepetidos}),
     *  the marca, color and tipo by {@link #parecidos}. */
    public static List<Repetido> escritosDeDosFormas(List<Producto> bienes) {
        List<Repetido> out = new ArrayList<>();
        for (ProveedorRepetido r : proveedoresRepetidos(bienes))
            out.add(new Repetido(Campo.PROVEEDOR, r.variante(), r.bienes(), r.queda()));
        for (Campo c : List.of(Campo.MARCA, Campo.COLOR, Campo.TIPO)) out.addAll(parecidos(c, bienes));
        return out;
    }

    /** One way of writing for each marca, color or tipo (13-nov-2026: «SMITH WESSON» 25 times
     *  and «SMITH & WESSON» 79, «HECHO EN MEXICO» and «MÉXICO», «mueble», «TRUPPER», «NEBRO»,
     *  «MUELE»…): the same letters without accents, capitals or signs; or a letter more, less,
     *  changed or two swapped («STHIL» → «STIHL») when one way is used at least twice as much
     *  — two letters apart are other brands (LENOVO and GENOVA, EPSON and EPCOM). What stays
     *  is the way the most bienes write, with its accents. */
    static List<Repetido> parecidos(Campo campo, List<Producto> bienes) {
        Map<String, Integer> cuenta = new LinkedHashMap<>();
        for (Producto p : bienes) {
            String v = campo.leer.apply(p);
            if (v != null && !v.isBlank() && !com.sibim.util.ProductoUtils.esSinDato(v)) cuenta.merge(v, 1, Integer::sum);
        }
        List<String> formas = new ArrayList<>(cuenta.keySet());
        List<String> claves = formas.stream().map(CalidadDatosService::claveTexto).toList();
        Map<String, Integer> porClave = new HashMap<>();
        for (int i = 0; i < formas.size(); i++) porClave.merge(claves.get(i), cuenta.get(formas.get(i)), Integer::sum);
        int[] grupo = new int[formas.size()];
        for (int i = 0; i < grupo.length; i++) grupo[i] = i;
        for (int i = 0; i < formas.size(); i++)
            for (int j = i + 1; j < formas.size(); j++) {
                String a = claves.get(i), b = claves.get(j);
                if (a.isEmpty() || b.isEmpty()) continue;
                boolean mismo = a.equals(b);
                if (!mismo && Math.min(a.length(), b.length()) >= 4 && unaLetra(a, b)) {
                    int ca = porClave.get(a), cb = porClave.get(b);
                    mismo = Math.max(ca, cb) >= 2 * Math.min(ca, cb);
                }
                if (mismo) {
                    int de = grupo[j], en = grupo[i];
                    for (int k = 0; k < grupo.length; k++) if (grupo[k] == de) grupo[k] = en;
                }
            }
        Map<Integer, List<String>> grupos = new LinkedHashMap<>();
        for (int i = 0; i < formas.size(); i++) grupos.computeIfAbsent(grupo[i], k -> new ArrayList<>()).add(formas.get(i));
        List<Repetido> out = new ArrayList<>();
        for (List<String> g : grupos.values()) {
            if (g.size() < 2) continue;
            String queda = g.stream().max(Comparator.comparingInt((String s) -> porClave.get(claveTexto(s)))
                .thenComparing(s -> !s.equals(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")))
                .thenComparingInt(cuenta::get)
                .thenComparing(s -> s.equals(s.toUpperCase(Locale.ROOT)))
                .thenComparing(Comparator.comparingInt((String s) -> s.length()).reversed())
                .thenComparing(Comparator.<String>reverseOrder())).orElseThrow();
            for (String v : g) if (!v.equals(queda)) out.add(new Repetido(campo, v, cuenta.get(v), queda));
        }
        out.sort(Comparator.comparing(Repetido::queda).thenComparing(Repetido::variante));
        return out;
    }

    /** As two ways of writing are compared: capitals, no accents, signs as spaces. */
    public static String claveTexto(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }

    /** One letter more, less or changed, or two next to each other swapped. */
    static boolean unaLetra(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++)
            for (int j = 1; j <= m; j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + c);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
            }
        return d[n][m] <= 1;
    }

    /** Writes each way as it {@code queda}, on every bien that had it.
     *  @return what changed, for {@link #separarEscritos} */
    public List<Unido> unirEscritos(List<Repetido> cambios) throws SQLException {
        soloPatrimonio();
        conConexion();
        List<Unido> hecho = new ArrayList<>();
        for (Repetido c : cambios) {
            List<String> ids = repo.cambiarTexto(c.campo().campo, c.variante(), c.queda());
            if (ids.isEmpty()) continue;
            hecho.add(new Unido(c, ids));
            audit.log("producto", ids.get(0), "Varios bienes: " + c.campo().etiqueta.toLowerCase(Locale.ROOT), "actualizar",
                c.campo().etiqueta + " unido: «" + c.variante() + "» se escribe «" + c.queda() + "» en "
                    + com.sibim.util.FormatUtils.plural(ids.size(), "bien", "bienes"));
        }
        ProductosEnMemoria.invalidar();
        return hecho;
    }

    /** Undo of {@link #unirEscritos}, on the bienes that still say what it wrote. */
    public int separarEscritos(List<Unido> hecho) throws SQLException {
        soloPatrimonio();
        conConexion();
        int n = 0;
        for (Unido u : hecho) n += repo.regresarTexto(u.cambio().campo().campo, u.ids(), u.cambio().variante(), u.cambio().queda());
        if (n > 0 && !hecho.isEmpty()) audit.log("producto", hecho.get(0).ids().get(0), "Varios bienes: datos escritos de dos formas", "actualizar",
            "Se deshizo unir lo escrito de dos formas: " + com.sibim.util.FormatUtils.plural(n, "bien regresó", "bienes regresaron") + " a como estaba escrito");
        ProductosEnMemoria.invalidar();
        return n;
    }

    // ── Tipo de bien y color que ya se saben ──────────────────────────────────

    /** The bienes in force without tipo de bien that their CONAC account says are «MUEBLE»
     *  (13-nov-2026: 2,447 without tipo; «MUEBLE» typed by hand 351 times since 28-oct). */
    public static List<Producto> sinTipoConSugerencia(List<Producto> bienes) {
        return bienes.stream().filter(p -> p.getFechaBaja() == null && falta(p, Dato.TIPO) && tipoSugerido(p) != null).toList();
    }

    /** The bienes in force without color whose name or description says it, with that color
     *  (13-nov-2026: 820 of 2,493; it agreed with 125 of the 139 colors typed by hand). */
    public static Map<Producto, String> coloresDelNombre(List<Producto> bienes) {
        Map<Producto, String> out = new LinkedHashMap<>();
        for (Producto p : bienes) {
            if (p.getFechaBaja() != null || !falta(p, Dato.COLOR)) continue;
            String c = com.sibim.util.ColorEnNombre.de(p.getNombre(), p.getDescripcion());
            if (c != null) out.put(p, c);
        }
        return out;
    }

    // ── Número de factura escrito en «Folio fiscal» ───────────────────────────

    /** A short number in «Folio fiscal» on a bien with no factura number: that is its
     *  factura's own number (12-nov-2026: 12 bienes, «01329», «AAA4B», «1636B»…). */
    public static boolean esNumeroDeFactura(Producto p) {
        String f = p.getFolioFiscal();
        return p.getFechaBaja() == null && f != null && !f.isBlank() && f.trim().length() <= 12
            && (p.getNumeroFactura() == null || p.getNumeroFactura().isBlank());
    }

    public static List<Producto> numeroEnFolio(List<Producto> bienes) {
        return bienes.stream().filter(CalidadDatosService::esNumeroDeFactura).toList();
    }

    /** The number as «N° Factura» takes it: «O1329» is «01329» (an O among digits is a zero). */
    public static String numeroDeFactura(String folio) {
        String t = folio.trim();
        String digitos = t.replaceAll("[Oo]", "0");
        return digitos.matches("\\d+") ? digitos : t.toUpperCase(Locale.ROOT);
    }

    /** Moves each number from «Folio fiscal» to «N° Factura». @return what moved, to undo it */
    public List<CalidadDatosRepository.NumeroEnFolio> pasarNumeroAFactura(List<Producto> bienes) throws SQLException {
        soloPatrimonio();
        conConexion();
        List<CalidadDatosRepository.NumeroEnFolio> cambios = numeroEnFolio(bienes).stream()
            .map(p -> new CalidadDatosRepository.NumeroEnFolio(p.getId(), p.getFolioFiscal(), numeroDeFactura(p.getFolioFiscal())))
            .toList();
        if (cambios.isEmpty()) return List.of();
        int n = repo.pasarNumeroAFactura(cambios);
        if (n > 0) audit.log("producto", cambios.get(0).id(), "Varios bienes: número de factura", "actualizar",
            "El número de la factura estaba escrito en «Folio fiscal»: pasó a «N° Factura» en " + n + " bienes: "
                + codigos(numeroEnFolio(bienes)));
        ProductosEnMemoria.invalidar();
        return n > 0 ? cambios : List.of();
    }

    /** Undo of {@link #pasarNumeroAFactura}. */
    public int regresarNumeroAFolio(List<CalidadDatosRepository.NumeroEnFolio> cambios) throws SQLException {
        soloPatrimonio();
        conConexion();
        if (cambios.isEmpty()) return 0;
        int n = repo.regresarNumeroAFolio(cambios);
        if (n > 0) audit.log("producto", cambios.get(0).id(), "Varios bienes: número de factura", "actualizar",
            "Se deshizo: el número regresó a «Folio fiscal» en " + n + " bienes");
        ProductosEnMemoria.invalidar();
        return n;
    }

    // ── Folio fiscal escrito en «Factura» ────────────────────────────────────

    /** Active bienes whose «Factura» holds the UUID of the CFDI and whose «Folio fiscal» is
     *  empty: 581 on 12-nov-2026, 301 of them of Seguridad Pública C-2 (the lists brought
     *  the UUID in the factura column). The form moves it since 1.0.24; these came before. */
    public static List<Producto> folioEnFactura(List<Producto> bienes) {
        return bienes.stream()
            .filter(p -> p.getFechaBaja() == null && Producto.pareceFolioFiscal(p.getNumeroFactura())
                && (p.getFolioFiscal() == null || p.getFolioFiscal().isBlank()))
            .toList();
    }

    /** Moves the UUID from «Factura» to «Folio fiscal»; the formats print the same (they
     *  show the factura or the folio). @return how many moved */
    public int pasarFolioFiscal(List<Producto> bienes) throws SQLException {
        soloPatrimonio();
        conConexion();
        List<Producto> estos = folioEnFactura(bienes);
        if (estos.isEmpty()) return 0;
        int n = repo.pasarFolioFiscal(estos.stream().map(Producto::getId).toList());
        if (n > 0) audit.log("producto", estos.get(0).getId(), "Varios bienes: folio fiscal", "actualizar",
            "El folio fiscal (UUID) estaba escrito en «Factura»: pasó a «Folio fiscal» en " + n + " bienes: " + codigos(estos));
        ProductosEnMemoria.invalidar();
        return n;
    }

    /** Undo of {@link #pasarFolioFiscal}: each UUID back in «Factura», on the bienes nobody
     *  changed since. @param antes the bienes as they were before */
    public int regresarFolioFiscal(List<Producto> antes) throws SQLException {
        soloPatrimonio();
        conConexion();
        Map<String, String> factura = new LinkedHashMap<>();
        for (Producto p : antes)
            if (Producto.pareceFolioFiscal(p.getNumeroFactura())) factura.put(p.getId(), p.getNumeroFactura());
        if (factura.isEmpty()) return 0;
        int n = repo.regresarFolioFiscal(factura);
        if (n > 0) audit.log("producto", antes.get(0).getId(), "Varios bienes: folio fiscal", "actualizar",
            "Se deshizo: el folio fiscal regresó a «Factura» en " + n + " bienes");
        ProductosEnMemoria.invalidar();
        return n;
    }

    private static String codigos(List<Producto> bienes) {
        return bienes.stream().limit(60).map(p -> Objects.requireNonNullElse(p.getCodigo(), p.getId()))
            .collect(Collectors.joining(", ")) + (bienes.size() > 60 ? " y " + (bienes.size() - 60) + " más" : "");
    }

    /** One text dato for several bienes at once; a bien that has its own keeps it.
     *  @return how many took it */
    public int asignarTexto(Dato dato, String valor, List<Producto> bienes) throws SQLException {
        return asignarTextoIds(dato, valor, bienes).size();
    }

    /** {@link #asignarTexto}, saying which bienes took it, for {@link #quitarTexto}. */
    public List<String> asignarTextoIds(Dato dato, String valor, List<Producto> bienes) throws SQLException {
        soloPatrimonio();
        conConexion();
        // Each wrong folio is its own: never one value for all of them.
        if (!dato.esTexto() || dato == Dato.FOLIO_REVISAR || valor == null || valor.isBlank()) return List.of();
        List<Producto> sin = bienes.stream().filter(p -> falta(p, dato)).toList();
        if (sin.isEmpty()) return List.of();
        List<String> ids = repo.asignarTextoIds(dato.campo, valor, sin.stream().map(Producto::getId).toList());
        if (!ids.isEmpty()) {
            String codigos = sin.stream().limit(60).map(p -> Objects.requireNonNullElse(p.getCodigo(), p.getId()))
                .collect(Collectors.joining(", ")) + (sin.size() > 60 ? " y " + (sin.size() - 60) + " más" : "");
            String que = dato.etiqueta.replaceFirst("^Sin ", "");
            audit.log("producto", sin.get(0).getId(), "Varios bienes: " + que, "actualizar",
                que.substring(0, 1).toUpperCase() + que.substring(1) + " «" + valor + "», capturado a la vez para " + ids.size() + " bienes: " + codigos);
        }
        ProductosEnMemoria.invalidar();
        return ids;
    }

    /** Undo of {@link #asignarTextoIds}: those bienes empty again, if they still say {@code valor}.
     *  @return how many went back */
    public int quitarTexto(Dato dato, String valor, List<String> ids) throws SQLException {
        soloPatrimonio();
        conConexion();
        if (!dato.esTexto() || ids.isEmpty()) return 0;
        int n = repo.quitarTexto(dato.campo, valor, ids);
        if (n > 0) {
            String que = dato.etiqueta.replaceFirst("^Sin ", "");
            audit.log("producto", ids.get(0), "Varios bienes: " + que, "actualizar",
                "Se deshizo " + que + " «" + valor + "»: " + com.sibim.util.FormatUtils.plural(n, "bien quedó", "bienes quedaron") + " sin ese dato otra vez");
        }
        ProductosEnMemoria.invalidar();
        return n;
    }

    /** Several bienes, each with its own value of {@code dato} (the colors taken from the
     *  names): one call per value. @return what each value was written on, for {@link #quitarTextos} */
    public Map<String, List<String>> asignarTextos(Dato dato, Map<Producto, String> valores) throws SQLException {
        Map<String, List<Producto>> porValor = new LinkedHashMap<>();
        valores.forEach((p, v) -> {
            String limpio = normalizarTexto(dato, v);
            if (limpio != null) porValor.computeIfAbsent(limpio, k -> new ArrayList<>()).add(p);
        });
        Map<String, List<String>> hecho = new LinkedHashMap<>();
        for (var e : porValor.entrySet()) {
            List<String> ids = asignarTextoIds(dato, e.getKey(), e.getValue());
            if (!ids.isEmpty()) hecho.put(e.getKey(), ids);
        }
        return hecho;
    }

    /** Undo of {@link #asignarTextos}. @return how many went back */
    public int quitarTextos(Dato dato, Map<String, List<String>> hecho) throws SQLException {
        int n = 0;
        for (var e : hecho.entrySet()) n += quitarTexto(dato, e.getKey(), e.getValue());
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
        agregar(out, nombresConTitulo(bienes, resguardos, titularesDe(config)).size(), "nombres con título (Lic., Ing., Arq.…)",
            "Quitar títulos", "productos", "titulos");
        agregar(out, personasRepetidas(resguardos, bienes, distintas).size(), "personas con el nombre repetido", "Unir personas", "productos", "unir");
        agregar(out, seriesRepetidas(bienes).size(), "series repetidas", "Revisar series", "productos", "series");
        agregar(out, MismaFactura.pendientes(bienes).stream().mapToLong(c -> c.bienes().size()).sum(),
            "datos que se pueden copiar de otro bien de la misma factura", "Copiar datos", "productos", "factura");
        agregar(out, DelMismoNombre.MISMO_NOMBRE.stream().mapToLong(d -> DelMismoNombre.propuestas(bienes, d).size()).sum(),
            "datos que ya dicen, todos igual, los demás bienes con el mismo nombre", "Revisarlos", "productos", "mismonombre");
        agregar(out, escritosDeDosFormas(bienes).size(), "proveedores, marcas, colores o tipos escritos de dos formas",
            "Unirlos", "productos", "proveedores");
        agregar(out, sinTipoConSugerencia(bienes).size(), "bienes sin tipo de bien que por su clave CONAC son «MUEBLE»",
            "Poner MUEBLE", "productos", "tipo");
        agregar(out, folioEnFactura(bienes).size(), "bienes con el folio fiscal escrito en «Factura»",
            "Pasarlo a su lugar", "productos", "folio");
        agregar(out, cuantos(bienes, Dato.FOLIO_REVISAR), "folios fiscales mal escritos", "Revisarlos",
            "productos", "completar:FOLIO_REVISAR");
        agregar(out, cuantos(bienes, Dato.PRECIO), "bienes sin precio", "Completar datos", "productos", "completar:PRECIO");
        agregar(out, cuantos(bienes, Dato.FECHA), "bienes sin fecha", "Completar datos", "productos", "completar:FECHA");
        agregar(out, cuantos(bienes, Dato.VIDA_UTIL), "bienes sin vida útil", "Completar datos", "productos", "completar:VIDA_UTIL");
        agregar(out, cuantos(bienes, Dato.FOTO), "bienes sin foto", "Completar datos", "productos", "completar:FOTO");
        agregar(out, ResguardoService.porResguardante(resguardos, bienes).stream().filter(p -> !p.sinResguardo().isEmpty()).count(),
            "personas con bienes sin resguardo", "Registrar en lote", "resguardos", "lote");
        agregar(out, conVariosResguardos(resguardos), "personas con varios resguardos en la misma área",
            "Juntar resguardos", "resguardos", "juntar");
        agregar(out, bienes.stream().filter(p -> p.getFechaBaja() == null && p.isObservadoPorArea()).count(),
            "bienes que su área dice que no son suyos", "Revisar avisos", "productos", "observados");
        agregar(out, bienes.stream().filter(p -> p.getFechaBaja() == null && !p.isEtiquetado()).count(),
            "bienes sin etiquetar", "Etiquetas por área", "productos", "etiquetas");
        agregar(out, faltanEnConfiguracion(config, com.sibim.config.Areas.getAllAreaNames().size()).size(),
            "datos faltan para los formatos", "Completar datos", "configuracion", "formatos");
        String ultimoRespaldo = config.get(CLAVE_ULTIMO_RESPALDO);
        long sinRespaldo = diasSinRespaldo(ultimoRespaldo, java.time.LocalDate.now());
        if (diasHabilesSinRespaldo(ultimoRespaldo, java.time.LocalDate.now()) > DIAS_SIN_RESPALDO)
            out.add(0, new PorHacer(sinRespaldo, "días sin respaldo de la base: revisa el respaldo diario de la PC de Patrimonio",
                "Ver Configuración", "configuracion", null));
        return out;
    }

    /** Written by scripts/respaldo_diario.ps1 after each backup: "2026-11-11T11:00:04|PC|1.03". */
    public static final String CLAVE_ULTIMO_RESPALDO = "ultimo_respaldo";
    /** More working days than this without a backup is said first in Por hacer: the PC of
     *  Patrimonio is off on weekends, and a Monday morning is not a missed backup. */
    static final int DIAS_SIN_RESPALDO = 2;

    /** Days since the last backup the script noted; 0 when none was noted yet (a database
     *  backed up by hand or before 1.0.26 is not told it has none). */
    static long diasSinRespaldo(String valor, java.time.LocalDate hoy) {
        if (valor == null || valor.isBlank()) return 0;
        try {
            java.time.LocalDate fecha = java.time.LocalDateTime.parse(valor.split("\\|")[0].trim()).toLocalDate();
            return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(fecha, hoy));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Monday to Friday after the last backup, up to today; 0 when none was noted. */
    static long diasHabilesSinRespaldo(String valor, java.time.LocalDate hoy) {
        long dias = diasSinRespaldo(valor, hoy);
        long habiles = 0;
        for (long i = 0; i < dias; i++) {
            java.time.DayOfWeek d = hoy.minusDays(i).getDayOfWeek();
            if (d != java.time.DayOfWeek.SATURDAY && d != java.time.DayOfWeek.SUNDAY) habiles++;
        }
        return habiles;
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
        if (vacio(config, ReporteResguardoVehicularService.CLAVE_ELABORA)) faltan.add("Elaboró");
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
