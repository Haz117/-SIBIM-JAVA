package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FacturaXml;
import com.sibim.util.FormatUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Bienes › Más › «Completar por factura»: the bienes of one purchase share its proveedor, its fecha
 * and its folio fiscal, typed once for all of them (or read from its PDF or XML). On 24-nov-2026, 273
 * facturas (1,371 bienes) lacked one of them, and in 214 no bien of the factura had the proveedor —
 * «Copiar datos de la misma factura» has nothing to copy there; that week the 300 cámaras of one
 * factura of the C-2 got theirs through «Cambiar datos», and other purchases bien by bien.
 * A purchase is what {@link MismaFactura#mismaCompra} says (a short factura number shared by two
 * proveedores is two). Saved through {@link EditarVarios}, only where it is missing.
 */
public final class CompletarPorFactura {

    private CompletarPorFactura() {}

    public enum Campo {
        PROVEEDOR("Proveedor", EditarVarios.Dato.PROVEEDOR),
        FECHA("Fecha de adquisición", EditarVarios.Dato.FECHA),
        FOLIO("Folio fiscal", EditarVarios.Dato.FOLIO_FISCAL);

        public final String etiqueta;
        public final EditarVarios.Dato comoSeGuarda;

        Campo(String etiqueta, EditarVarios.Dato comoSeGuarda) {
            this.etiqueta = etiqueta;
            this.comoSeGuarda = comoSeGuarda;
        }

        /** A folio fiscal written in «Factura» is not missing: «Folio fiscal a su lugar» moves it. */
        public boolean falta(Producto p) {
            return switch (this) {
                case PROVEEDOR -> vacio(p.getProveedor());
                case FECHA -> p.getFechaAdquisicion() == null;
                case FOLIO -> vacio(p.getFolioFiscal()) && MismaFactura.uuid(p.getNumeroFactura()) == null;
            };
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank() || com.sibim.util.ProductoUtils.esSinDato(s);
    }

    public record Propuesta(Object valor, String porQue) {}

    /**
     * One purchase.
     * @param etiqueta  its factura as written («A 667»), or «Folio 3822637C…» when it has only the folio
     * @param uuid      the 32 hex digits of its CFDI when a bien says it (to tell a PDF of another one), or null
     * @param bienes    the ones of the área chosen, those lacking something first
     */
    public record Factura(String etiqueta, String uuid, List<Producto> bienes, int enLaCompra,
                          Map<Campo, Integer> faltan, Map<Campo, Propuesta> propuestas, Map<Campo, String> tienen) {

        public int incompletos() {
            return (int) bienes.stream().filter(p -> faltan.keySet().stream().anyMatch(c -> c.falta(p))).count();
        }

        /** «CAMARA BALA IP (48), POSTE DE MONITOREO (18) y 31 más». */
        public String nombres() {
            Map<String, Long> n = bienes.stream().collect(Collectors.groupingBy(
                p -> Objects.requireNonNullElse(p.getNombre(), "").trim(), LinkedHashMap::new, Collectors.counting()));
            List<Map.Entry<String, Long>> orden = n.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList();
            String dos = orden.stream().limit(2).map(e -> e.getKey() + (e.getValue() > 1 ? " (" + e.getValue() + ")" : ""))
                .collect(Collectors.joining(", "));
            long resto = orden.stream().skip(2).mapToLong(Map.Entry::getValue).sum();
            return dos + (resto > 0 ? " y " + resto + " más" : "");
        }

        public Set<String> areas() {
            return bienes.stream().map(p -> p.getArea() == null || p.getArea().isBlank() ? "Sin área" : p.getArea())
                .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    public static List<Factura> facturas(List<Producto> bienes) {
        return facturas(bienes, p -> true, EnumSet.allOf(Campo.class));
    }

    /** The purchases where some of {@code cuales} lack one of {@code campos}; the most bienes first. */
    public static List<Factura> facturas(List<Producto> bienes, Predicate<Producto> cuales, Set<Campo> campos) {
        List<Factura> out = new ArrayList<>();
        for (List<Producto> posible : MismaFactura.compras(bienes))
            for (List<Producto> compra : separar(posible)) {
                List<Producto> aqui = compra.stream().filter(cuales).toList();
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
                    tienen.put(c, tienen(c, compra));
                    if (!faltan.containsKey(c)) continue;
                    Propuesta p = propuesta(c, compra);
                    if (p != null) propuestas.put(c, p);
                }
                List<Producto> orden = new ArrayList<>(aqui);
                orden.sort(Comparator.comparing((Producto p) -> faltan.keySet().stream().noneMatch(c -> c.falta(p)))
                    .thenComparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO));
                out.add(new Factura(etiqueta(compra), uuidDe(compra), orden, compra.size(), faltan, propuestas, tienen));
            }
        out.sort(Comparator.comparingInt(Factura::incompletos).reversed().thenComparing(Factura::etiqueta));
        return out;
    }

    /** {@link MismaFactura#compras} joins what may be one purchase; this keeps together only what is
     *  (a short number of two proveedores, two dates of one number, are two). */
    static List<List<Producto>> separar(List<Producto> posible) {
        if (unaSola(posible)) return List.of(posible);
        int n = posible.size();
        int[] padre = new int[n];
        for (int i = 0; i < n; i++) padre[i] = i;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (MismaFactura.mismaCompra(posible.get(i), posible.get(j))) padre[raiz(padre, i)] = raiz(padre, j);
        Map<Integer, List<Producto>> g = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) g.computeIfAbsent(raiz(padre, i), k -> new ArrayList<>()).add(posible.get(i));
        return new ArrayList<>(g.values());
    }

    /** Without comparing each pair (the 300 cámaras of one factura of the C-2 are 45,000 pairs): one
     *  long factura number on all, or one folio fiscal on all, and no two fechas or folios that differ —
     *  then {@link MismaFactura#mismaCompra} says yes to every pair. */
    static boolean unaSola(List<Producto> posible) {
        if (posible.size() < 2) return true;
        Set<LocalDate> fechas = new HashSet<>();
        Set<String> folios = new HashSet<>(), claves = new HashSet<>(), uuids = new HashSet<>();
        boolean todosConUuid = true;
        for (Producto p : posible) {
            if (p.getFechaAdquisicion() != null) fechas.add(p.getFechaAdquisicion());
            if (p.getFolioFiscal() != null && !p.getFolioFiscal().isBlank()) folios.add(p.getFolioFiscal().trim());
            claves.add(String.valueOf(MismaFactura.clave(p.getNumeroFactura())));
            String u = MismaFactura.uuid(p.getFolioFiscal());
            if (u == null) todosConUuid = false; else uuids.add(u);
        }
        if (fechas.size() > 1) return false;
        if (todosConUuid && uuids.size() == 1) return true;
        String clave = claves.size() == 1 ? claves.iterator().next() : null;
        return clave != null && !"null".equals(clave) && clave.length() > 6 && folios.size() <= 1;
    }

    private static int raiz(int[] padre, int i) {
        while (padre[i] != i) i = padre[i] = padre[padre[i]];
        return i;
    }

    static String etiqueta(List<Producto> compra) {
        Optional<String> numero = compra.stream().map(Producto::getNumeroFactura)
            .filter(f -> MismaFactura.clave(f) != null && MismaFactura.uuid(f) == null).map(String::trim)
            .collect(Collectors.groupingBy(f -> f, Collectors.counting())).entrySet().stream()
            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
        if (numero.isPresent()) return numero.get();
        String u = uuidDe(compra);
        return u != null ? "Folio " + u.substring(0, 8) + "…" : compra.get(0).getNumeroFactura().trim();
    }

    /** The CFDI's UUID of the purchase, from a folio fiscal or a UUID written in «Factura». */
    static String uuidDe(List<Producto> compra) {
        for (Producto p : compra) {
            String u = MismaFactura.uuid(p.getFolioFiscal());
            if (u == null) u = MismaFactura.uuid(p.getNumeroFactura());
            if (u != null) return u;
        }
        return null;
    }

    /** What every bien of the purchase that has it says. */
    static Propuesta propuesta(Campo c, List<Producto> compra) {
        List<Producto> con = compra.stream().filter(p -> !c.falta(p) && c.comoSeGuarda.de(p) != null).toList();
        Set<Object> distintos = con.stream().map(p -> comparable(c.comoSeGuarda.de(p))).collect(Collectors.toSet());
        if (distintos.size() != 1) return null;
        Object v = c.comoSeGuarda.de(con.get(0));
        // A folio fiscal as it is written: capitals (24-nov-2026: one factura's 46 bienes had it in small letters).
        if (c == Campo.FOLIO && v instanceof String s) v = CalidadDatosService.normalizarTexto(CalidadDatosService.Dato.FOLIO, s);
        return new Propuesta(v instanceof String s ? s.trim() : v,
            con.size() == 1 ? "como " + con.get(0).getCodigo() : "como los otros " + con.size());
    }

    static String tienen(Campo c, List<Producto> compra) {
        Map<Object, Integer> cuenta = new LinkedHashMap<>();
        Map<Object, Object> comoSeVe = new HashMap<>();
        for (Producto p : compra) {
            if (c.falta(p)) continue;
            Object v = c.comoSeGuarda.de(p);
            if (v == null) continue;
            cuenta.merge(comparable(v), 1, Integer::sum);
            comoSeVe.putIfAbsent(comparable(v), v);
        }
        if (cuenta.isEmpty()) return "Ninguno tiene";
        if (cuenta.size() == 1 && cuenta.values().iterator().next() == compra.size())
            return "Todos: " + c.comoSeGuarda.texto(comoSeVe.values().iterator().next());
        return cuenta.entrySet().stream().sorted(Map.Entry.<Object, Integer>comparingByValue().reversed()).limit(2)
            .map(e -> c.comoSeGuarda.texto(comoSeVe.get(e.getKey())) + " en " + e.getValue())
            .collect(Collectors.joining(", ")) + (cuenta.size() > 2 ? "…" : "");
    }

    private static Object comparable(Object v) {
        return v instanceof String s ? CalidadDatosService.claveTexto(s) : v;
    }

    /** What is typed for the proveedor or the folio, as it is saved; null when empty. A wrong
     *  value is said in {@link IllegalArgumentException}, with words for the screen. */
    public static Object leer(Campo c, String texto) {
        if (texto == null || texto.isBlank()) return null;
        if (c == Campo.PROVEEDOR) {
            String v = CalidadDatosService.normalizarTexto(CalidadDatosService.Dato.PROVEEDOR, texto);
            if (v.length() > 200) throw new IllegalArgumentException("El proveedor es demasiado largo");
            return v;
        }
        if (c == Campo.FOLIO) {
            String v = CalidadDatosService.normalizarTexto(CalidadDatosService.Dato.FOLIO, texto);
            String problema = CalidadDatosService.problemaFolio(v);
            if (problema != null) throw new IllegalArgumentException("El folio fiscal " + problema);
            return v;
        }
        throw new IllegalArgumentException("La fecha se elige en su calendario");
    }

    /** @return the fecha, or an error for one in the future */
    public static LocalDate leerFecha(LocalDate fecha) {
        if (fecha != null && fecha.isAfter(LocalDate.now())) throw new IllegalArgumentException("La fecha de adquisición no puede ser futura");
        return fecha;
    }

    /** What its PDF or XML says for the row: proveedor, fecha and folio fiscal (only what it says),
     *  and why it is not this factura's when its folio is another one. */
    public record DeSuFactura(Map<Campo, Object> valores, String total, String otraFactura) {}

    public static DeSuFactura deSuFactura(Factura f, FacturaXml.Factura leida) {
        Map<Campo, Object> v = new EnumMap<>(Campo.class);
        if (leida.emisor() != null && !leida.emisor().isBlank())
            v.put(Campo.PROVEEDOR, CalidadDatosService.normalizarTexto(CalidadDatosService.Dato.PROVEEDOR, leida.emisor()));
        if (leida.fecha() != null) v.put(Campo.FECHA, leida.fecha());
        if (leida.uuid() != null) v.put(Campo.FOLIO, CalidadDatosService.normalizarTexto(CalidadDatosService.Dato.FOLIO, leida.uuid()));
        String suyo = MismaFactura.uuid(leida.uuid());
        String otra = f.uuid() != null && suyo != null && !suyo.equals(f.uuid())
            ? "Este archivo es de otra factura (folio " + leida.uuid().substring(0, Math.min(8, leida.uuid().length())) + "…)" : null;
        String total = leida.total() == null ? null : "Total de la factura: " + FormatUtils.formatCurrency(leida.total());
        return new DeSuFactura(v, total, otra);
    }

    /** What saving the row does to each bien: each dato only where it is missing. */
    public static List<EditarVarios.Renglon> renglones(Factura f, Map<Campo, Object> valores) {
        List<EditarVarios.Cambio> cambios = new ArrayList<>();
        valores.forEach((c, v) -> {
            if (v != null) cambios.add(new EditarVarios.Cambio(c.comoSeGuarda, p -> c.falta(p) ? v : null, c.comoSeGuarda.texto(v)));
        });
        return cambios.isEmpty() ? List.of() : EditarVarios.revisar(f.bienes(), cambios);
    }
}
