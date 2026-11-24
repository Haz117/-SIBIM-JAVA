package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService.Dato;
import com.sibim.util.ColorEnNombre;
import com.sibim.util.FormatUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Completar datos › «Por grupos»: the bienes with the same name are the same kind of bien and
 * take the same vida útil, color and tipo. From 20 to 24-nov-2026 Patrimonio filled 610 bienes
 * in 20 rounds of «Cambiar datos», each one a group of the same name found with the search and
 * selected by hand (ESPOSAS ×37, CHALECO ×27, UPS ×16); 1,212 of the 1,881 bienes without vida
 * útil were in 226 such groups. Here each group is one row: what is typed goes to every bien of
 * it that lacks it, saved through {@link EditarVarios} (bien by bien, undone as a whole).
 *
 * <p>What the row brings written is what is already known: what the other bienes of that name
 * all have, else the color its name says or «MUEBLE» for what its CONAC account says is not a
 * vehicle. The vida útil is never proposed from the CONAC account: Patrimonio decides it by the
 * kind of bien (24-nov: cámaras 3 or 10 years, defensa 5, 7 or 10).
 */
public final class CompletarPorGrupos {

    private CompletarPorGrupos() {}

    /** What a row fills, in the order of the form. */
    public enum Campo {
        VIDA_UTIL("Vida útil", Dato.VIDA_UTIL, EditarVarios.Dato.VIDA_UTIL),
        COLOR("Color", Dato.COLOR, EditarVarios.Dato.COLOR),
        TIPO("Tipo de bien", Dato.TIPO, EditarVarios.Dato.TIPO_BIEN);

        public final String etiqueta;
        public final Dato dato;
        public final EditarVarios.Dato comoSeGuarda;

        Campo(String etiqueta, Dato dato, EditarVarios.Dato comoSeGuarda) {
            this.etiqueta = etiqueta;
            this.dato = dato;
            this.comoSeGuarda = comoSeGuarda;
        }

        public boolean falta(Producto p) { return CalidadDatosService.falta(p, dato); }
    }

    /** A value already known for the group, and where it comes from (said under the field). */
    public record Propuesta(Object valor, String porQue) {
        public String texto() { return valor instanceof Integer i ? String.valueOf(i) : String.valueOf(valor); }
    }

    /**
     * One name.
     * @param bienes      the bienes of the row (of the área chosen), the ones lacking first
     * @param conNombre   how many bienes in force have that name, in every área
     * @param faltan      how many of the row lack each campo (a campo none lacks isn't there)
     * @param propuestas  what is already known for a campo that some lack
     * @param tienen      what the bienes of that name already have, for each campo: «10 años en 4»
     */
    public record Grupo(String nombre, List<Producto> bienes, int conNombre, Map<Campo, Integer> faltan,
                        Map<Campo, Propuesta> propuestas, Map<Campo, String> tienen) {

        /** The bienes of the row lacking at least one campo. */
        public int incompletos() {
            return (int) bienes.stream().filter(p -> faltan.keySet().stream().anyMatch(c -> c.falta(p))).count();
        }

        public Set<String> areas() {
            return bienes.stream().map(p -> p.getArea() == null || p.getArea().isBlank() ? "Sin área" : p.getArea())
                .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /** {@link #grupos(List, Predicate, Set)} for every área and the three campos. */
    public static List<Grupo> grupos(List<Producto> bienes) {
        return grupos(bienes, p -> true, EnumSet.allOf(Campo.class));
    }

    /**
     * The names with two or more bienes in force where some of {@code cuales} lack one of {@code campos}.
     * The others of the name count for what is known (an área's chairs take the vida útil of their
     * twins in another), but only {@code cuales} are in the row and get what is saved.
     * @return the groups with the most bienes to complete first
     */
    public static List<Grupo> grupos(List<Producto> bienes, Predicate<Producto> cuales, Set<Campo> campos) {
        Map<String, List<Producto>> porNombre = new LinkedHashMap<>();
        for (Producto p : bienes) {
            if (p.getFechaBaja() != null) continue;
            String clave = CalidadDatosService.claveTexto(p.getNombre());
            if (!clave.isEmpty()) porNombre.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
        }
        List<Grupo> out = new ArrayList<>();
        for (List<Producto> todos : porNombre.values()) {
            if (todos.size() < 2) continue;
            List<Producto> aqui = todos.stream().filter(cuales).toList();
            if (aqui.isEmpty()) continue;
            Map<Campo, Integer> faltan = new EnumMap<>(Campo.class);
            for (Campo c : campos) {
                int n = (int) aqui.stream().filter(c::falta).count();
                if (n > 0) faltan.put(c, n);
            }
            if (faltan.isEmpty()) continue;
            Map<Campo, Propuesta> propuestas = new EnumMap<>(Campo.class);
            Map<Campo, String> tienen = new EnumMap<>(Campo.class);
            for (Campo c : Campo.values()) {
                tienen.put(c, tienen(c, todos));
                if (!faltan.containsKey(c)) continue;
                Propuesta pr = propuesta(c, todos, aqui.stream().filter(c::falta).toList());
                if (pr != null) propuestas.put(c, pr);
            }
            List<Producto> orden = new ArrayList<>(aqui);
            orden.sort(Comparator.comparing((Producto p) -> faltan.keySet().stream().noneMatch(c -> c.falta(p)))
                .thenComparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO));
            out.add(new Grupo(comoSeEscribe(todos), orden, todos.size(), faltan, propuestas, tienen));
        }
        out.sort(Comparator.comparingInt(Grupo::incompletos).reversed()
            .thenComparing(g -> CalidadDatosService.claveTexto(g.nombre())));
        return out;
    }

    /** What is known for those of the group lacking it: what every other bien of the name has;
     *  else, for the color, what all their names say; for the tipo, «MUEBLE» by the CONAC account. */
    static Propuesta propuesta(Campo c, List<Producto> todos, List<Producto> sin) {
        if (sin.isEmpty()) return null;
        List<Producto> con = todos.stream().filter(p -> !c.falta(p)).toList();
        Map<Object, List<Producto>> valores = new LinkedHashMap<>();
        for (Producto p : con) valores.computeIfAbsent(comparable(c.comoSeGuarda.de(p)), k -> new ArrayList<>()).add(p);
        // A color written as tipo («NEGRO», 18-nov-2026) is not passed on: saving it would be refused.
        if (valores.size() == 1 && !(c == Campo.TIPO && ColorEnNombre.esColor(String.valueOf(c.comoSeGuarda.de(con.get(0)))))) {
            Object v = c.comoSeGuarda.de(con.get(0));
            return new Propuesta(v instanceof String s ? s.trim() : v,
                con.size() == 1 ? "como " + con.get(0).getCodigo() : "como los otros " + con.size());
        }
        if (c == Campo.COLOR) {
            String delNombre = igualEnTodos(sin, p -> ColorEnNombre.de(p.getNombre(), null));
            if (delNombre != null) return new Propuesta(delNombre, "lo dice su nombre");
            String deDescripcion = igualEnTodos(sin, p -> ColorEnNombre.de(p.getNombre(), p.getDescripcion()));
            if (deDescripcion != null) return new Propuesta(deDescripcion, "lo dice su descripción");
        }
        if (c == Campo.TIPO && valores.keySet().stream().allMatch(k -> ColorEnNombre.esColor(String.valueOf(k)))) {
            String tipo = igualEnTodos(sin, CalidadDatosService::tipoSugerido);
            if (tipo != null) return new Propuesta(tipo, "por su clave CONAC");
        }
        return null;
    }

    private static String igualEnTodos(List<Producto> bienes, java.util.function.Function<Producto, String> de) {
        String unico = null;
        for (Producto p : bienes) {
            String v = de.apply(p);
            if (v == null || (unico != null && !unico.equals(v))) return null;
            unico = v;
        }
        return unico;
    }

    /** What the bienes of the name have: «Ninguno tiene», «Todos: 10 años», «3 años en 5, 10 años en 2». */
    static String tienen(Campo c, List<Producto> todos) {
        Map<Object, Integer> cuenta = new LinkedHashMap<>();
        Map<Object, Object> comoSeVe = new HashMap<>();
        for (Producto p : todos) {
            if (c.falta(p)) continue;
            Object v = c.comoSeGuarda.de(p);
            Object k = comparable(v);
            cuenta.merge(k, 1, Integer::sum);
            comoSeVe.putIfAbsent(k, v);
        }
        if (cuenta.isEmpty()) return "Ninguno tiene";
        if (cuenta.size() == 1 && cuenta.values().iterator().next() == todos.size())
            return "Todos: " + c.comoSeGuarda.texto(comoSeVe.values().iterator().next());
        return cuenta.entrySet().stream().sorted(Map.Entry.<Object, Integer>comparingByValue().reversed()).limit(3)
            .map(e -> c.comoSeGuarda.texto(comoSeVe.get(e.getKey())) + " en " + e.getValue())
            .collect(Collectors.joining(", ")) + (cuenta.size() > 3 ? "…" : "");
    }

    /** The name as most of its bienes write it. */
    private static String comoSeEscribe(List<Producto> todos) {
        return todos.stream().map(p -> p.getNombre().trim())
            .collect(Collectors.groupingBy(n -> n, LinkedHashMap::new, Collectors.counting()))
            .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private static Object comparable(Object v) {
        return v instanceof String s ? CalidadDatosService.claveTexto(s) : v;
    }

    /** What is typed for a campo, as it is saved; null when it is empty. A wrong value is said in
     *  {@link IllegalArgumentException}, with words for the screen. */
    public static Object leer(Campo c, String texto, List<String> tipos) {
        if (texto == null || texto.isBlank()) return null;
        if (c == Campo.VIDA_UTIL) {
            try {
                int v = Integer.parseInt(texto.trim().replaceFirst("(?i)\\s*años?$", ""));
                if (v >= 1 && v <= 100) return v;
            } catch (NumberFormatException ignored) {
                // said below
            }
            throw new IllegalArgumentException("La vida útil son los años, de 1 a 100");
        }
        String v = CalidadDatosService.normalizarTexto(c.dato, texto);
        if (v.length() > 200) throw new IllegalArgumentException(c.etiqueta + ": es demasiado largo");
        if (c == Campo.TIPO) {
            if (ColorEnNombre.esColor(v)) throw new IllegalArgumentException("«" + v + "» parece un color: el color va en «Color»");
            String enLista = CalidadDatosService.comoEnLaLista(v, tipos);
            if (enLista != null) v = enLista;
        }
        return v;
    }

    /** What saving the row does to each of its bienes: each campo only where it is missing. */
    public static List<EditarVarios.Renglon> renglones(Grupo g, Map<Campo, Object> valores) {
        List<EditarVarios.Cambio> cambios = new ArrayList<>();
        valores.forEach((c, v) -> {
            if (v != null) cambios.add(new EditarVarios.Cambio(c.comoSeGuarda, p -> c.falta(p) ? v : null, c.comoSeGuarda.texto(v)));
        });
        return cambios.isEmpty() ? List.of() : EditarVarios.revisar(g.bienes(), cambios);
    }

    /** «70 bienes», «3 de 18 bienes con este nombre» when the row is of one área. */
    public static String cuantos(Grupo g) {
        return g.bienes().size() == g.conNombre() ? FormatUtils.plural(g.bienes().size(), "bien", "bienes")
            : g.bienes().size() + " de " + g.conNombre() + " bienes con este nombre";
    }
}
