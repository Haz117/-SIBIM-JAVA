package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The bienes of one factura were bought together: they share the proveedor, the
 *  folio fiscal and the date, and the pieces of the same kind share cost, vida útil
 *  and color. From 28-oct to 11-nov-2026 Patrimonio typed the same proveedor 70 times
 *  and the same folio fiscal 40 times, one bien at a time; 473 of 1,019 edits repeated
 *  what another bien of the same factura had just received.
 *
 *  <p>Only empty data, or data equal to what the edited bien had before, is ever
 *  proposed: a different value on another bien is somebody's capture and stays. */
public final class MismaFactura {

    private MismaFactura() {}

    /** What can be passed on, and whether it belongs to the whole factura or only to
     *  the pieces of the same kind (same name). */
    public enum Dato {
        PROVEEDOR("Proveedor", true, Producto::getProveedor, (p, v) -> p.setProveedor((String) v)),
        FOLIO_FISCAL("Folio fiscal", true, Producto::getFolioFiscal, (p, v) -> p.setFolioFiscal((String) v)),
        FECHA("Fecha de adquisición", true, Producto::getFechaAdquisicion, (p, v) -> p.setFechaAdquisicion((LocalDate) v)),
        COSTO("Costo", false, Producto::getPrecioCompra, (p, v) -> p.setPrecioCompra((BigDecimal) v)),
        VIDA_UTIL("Vida útil", false, Producto::getVidaUtilAnios, (p, v) -> p.setVidaUtilAnios((Integer) v)),
        COLOR("Color", false, Producto::getColor, (p, v) -> p.setColor((String) v)),
        // 12-nov-2026: «BODY CAM» typed 20 times for the body cams of one factura.
        MARCA("Marca", false, Producto::getMarca, (p, v) -> p.setMarca((String) v)),
        MODELO("Modelo", false, Producto::getModelo, (p, v) -> p.setModelo((String) v)),
        TIPO_BIEN("Tipo de bien", false, Producto::getTipoBien, (p, v) -> p.setTipoBien((String) v));

        public final String etiqueta;
        /** True: every bien of the factura; false: only those with the same name. */
        public final boolean deTodaLaFactura;
        final Function<Producto, Object> leer;
        final BiConsumer<Producto, Object> poner;

        Dato(String etiqueta, boolean deTodaLaFactura, Function<Producto, Object> leer, BiConsumer<Producto, Object> poner) {
            this.etiqueta = etiqueta;
            this.deTodaLaFactura = deTodaLaFactura;
            this.leer = leer;
            this.poner = poner;
        }

        public Object de(Producto p) { return leer.apply(p); }

        /** The value as the screens show it. */
        public String texto(Object v) {
            if (vacio(v)) return "(vacío)";
            if (v instanceof BigDecimal b) return FormatUtils.formatCurrency(b);
            if (v instanceof LocalDate d) return FormatUtils.formatDate(d);
            if (v instanceof Integer i) return i + (i == 1 ? " año" : " años");
            return v.toString().trim();
        }
    }

    /** One dato to put on some bienes. */
    public record Cambio(Dato dato, String factura, Object nuevo, List<Producto> bienes) {}

    /** What was saved, what failed, and how each saved bien was before (to undo it). */
    public record Resultado(List<Producto> guardados, List<Producto> antes, List<String> fallas) {}

    /** What is written on a bien that has no factura (as {@link #clave} leaves it): none is one. */
    private static final Set<String> NO_ES_FACTURA = Set.of("SINDOCUMENTO", "SINDOCUMETO", "SINFACTURA", "SF", "SN",
        "NA", "SINDATO", "SINNUMERO", "NOTIENE", "PND", "COMODATO", "NINGUNA", "NOAPLICA");

    /** The factura as a key: spaces, dashes, case and leading zeros don't make another one
     *  ("F-MR-03343" is "F-MR-003343"; "AA 362" is "AA362"). Null when it isn't one. */
    public static String clave(String factura) {
        if (factura == null) return null;
        String f = sinAcentos(factura).replace(" ", "").replaceAll("(?<![0-9])0+(?=[0-9])", "");
        if (f.length() < 2 || NO_ES_FACTURA.contains(f) || f.contains("SINFACTURA") || f.contains("SINDOCUMENT")) return null;
        return f;
    }

    /** A short number ("139", "A8") is repeated by different proveedores: the factura alone
     *  doesn't say two bienes were one purchase (on 11-nov-2026 «139» was a grúa and a CPU). */
    private static boolean corta(String clave) {
        return clave.length() <= 6;
    }

    /** The folio fiscal as a key: the 32 hex digits of the CFDI's UUID, whatever the case or
     *  dashes. Null when it isn't a whole UUID. */
    public static String uuid(String folio) {
        if (folio == null) return null;
        String h = folio.toUpperCase(Locale.ROOT).replaceAll("[^0-9A-F]", "");
        return h.length() == 32 ? h : null;
    }

    /** Whether the bien says which purchase it came from: a factura number or the UUID of
     *  its CFDI (on 12-nov-2026, 581 bienes had the UUID and no other number). */
    public static boolean tieneCompra(Producto p) {
        return clave(p.getNumeroFactura()) != null || uuid(p.getFolioFiscal()) != null;
    }

    /** How the screens name the purchase: its factura, or its folio fiscal when it has none. */
    public static String etiqueta(Producto p) {
        if (clave(p.getNumeroFactura()) != null) return p.getNumeroFactura().trim();
        String f = p.getFolioFiscal() == null ? "" : p.getFolioFiscal().trim();
        return uuid(f) != null ? "con folio fiscal " + f.substring(0, Math.min(8, f.length())) + "…" : "";
    }

    /** Bought together: the same CFDI (folio fiscal) when both have one; otherwise the same
     *  factura, no date or folio fiscal that says otherwise and, for a short factura number,
     *  the same name, date or folio. */
    public static boolean mismaCompra(Producto a, Producto b) {
        String ua = uuid(a.getFolioFiscal()), ub = uuid(b.getFolioFiscal());
        if (ua != null && ub != null) return ua.equals(ub) && !distintos(a.getFechaAdquisicion(), b.getFechaAdquisicion());
        String k = clave(a.getNumeroFactura());
        if (k == null || !k.equals(clave(b.getNumeroFactura()))) return false;
        if (distintos(a.getFechaAdquisicion(), b.getFechaAdquisicion()) || distintos(a.getFolioFiscal(), b.getFolioFiscal())) return false;
        if (!corta(k)) return true;
        return mismoNombre(a, b) || iguales(a.getFechaAdquisicion(), b.getFechaAdquisicion())
            || iguales(a.getFolioFiscal(), b.getFolioFiscal());
    }

    /** The other active bienes bought with {@code p}. */
    public static List<Producto> hermanos(Producto p, List<Producto> todos) {
        if (!tieneCompra(p)) return List.of();
        return todos.stream()
            .filter(o -> o.getFechaBaja() == null && !Objects.equals(o.getId(), p.getId()))
            .filter(o -> mismaCompra(p, o))
            .toList();
    }

    private static boolean distintos(Object a, Object b) {
        return !vacio(a) && !vacio(b) && !igual(a, b);
    }

    private static boolean iguales(Object a, Object b) {
        return !vacio(a) && !vacio(b) && igual(a, b);
    }

    /** After {@code antes} was saved as {@code despues}: the data it got that the other
     *  bienes of its factura can take too. A bien takes it when it has nothing there or
     *  had the same as {@code antes}; costo, vida útil and color only go to pieces of the
     *  same kind. {@code antes} is null for a new bien. */
    public static List<Cambio> proponer(Producto antes, Producto despues, List<Producto> todos) {
        // Who was bought with it is judged on the bien as it was: correcting its folio or
        // date must reach the siblings that have the old one.
        boolean mismaFactura = antes != null && (
            (clave(despues.getNumeroFactura()) != null && clave(despues.getNumeroFactura()).equals(clave(antes.getNumeroFactura())))
            || (uuid(despues.getFolioFiscal()) != null && uuid(despues.getFolioFiscal()).equals(uuid(antes.getFolioFiscal()))));
        List<Producto> hermanos = hermanos(mismaFactura ? antes : despues, todos).stream()
            .filter(o -> !Objects.equals(o.getId(), despues.getId())).toList();
        if (hermanos.isEmpty()) return List.of();
        String factura = etiqueta(despues);
        List<Cambio> out = new ArrayList<>();
        for (Dato d : Dato.values()) {
            Object nuevo = d.de(despues);
            Object viejo = antes == null ? null : d.de(antes);
            if (vacio(nuevo) || igual(nuevo, viejo)) continue;
            List<Producto> reciben = hermanos.stream()
                .filter(o -> d.deTodaLaFactura || mismoNombre(o, despues))
                .filter(o -> !igual(d.de(o), nuevo) && (vacio(d.de(o)) || (!vacio(viejo) && igual(d.de(o), viejo))))
                .toList();
            if (!reciben.isEmpty()) out.add(new Cambio(d, factura, nuevo, reciben));
        }
        return out;
    }

    /** Data already captured on one bien of a factura that its siblings lack: the
     *  value is taken when all the bienes that have it agree, and it goes only to the
     *  bienes with nothing there. Costo, vida útil and color, per kind of bien. */
    public static List<Cambio> pendientes(List<Producto> todos) {
        List<Cambio> out = new ArrayList<>();
        for (List<Producto> bienes : compras(todos)) {
            if (bienes.size() < 2) continue;
            for (Dato d : Dato.values()) {
                // Each bien that lacks it takes it from those bought with it, when they agree.
                Map<Object, List<Producto>> porValor = new LinkedHashMap<>();
                Map<Object, Object> original = new HashMap<>();
                for (Producto sin : bienes) {
                    if (!vacio(d.de(sin))) continue;
                    List<Object> valores = bienes.stream()
                        .filter(o -> o != sin && !vacio(d.de(o)) && (d.deTodaLaFactura || mismoNombre(o, sin)) && mismaCompra(sin, o))
                        .map(d::de).toList();
                    Set<Object> distintos = valores.stream().map(MismaFactura::comparable).collect(Collectors.toSet());
                    if (distintos.size() != 1) continue;
                    Object k = distintos.iterator().next();
                    original.putIfAbsent(k, valores.get(0));
                    porValor.computeIfAbsent(k, x -> new ArrayList<>()).add(sin);
                }
                String factura = bienes.stream().filter(p -> clave(p.getNumeroFactura()) != null).findFirst()
                    .map(MismaFactura::etiqueta).orElse(etiqueta(bienes.get(0)));
                porValor.forEach((k, sin) -> out.add(new Cambio(d, factura, original.get(k), sin)));
            }
        }
        return out;
    }

    /** The active bienes that can be one purchase, joined by their factura number or by
     *  the UUID of their CFDI (a bien with both links the two); which pairs really are one
     *  purchase is still {@link #mismaCompra}. By factura, as before. */
    static List<List<Producto>> compras(List<Producto> todos) {
        List<Producto> con = todos.stream().filter(p -> p.getFechaBaja() == null && tieneCompra(p)).toList();
        int[] padre = new int[con.size()];
        for (int i = 0; i < padre.length; i++) padre[i] = i;
        Map<String, Integer> primero = new HashMap<>();
        for (int i = 0; i < con.size(); i++) {
            String k = clave(con.get(i).getNumeroFactura()), u = uuid(con.get(i).getFolioFiscal());
            for (String llave : new String[]{k == null ? null : "F:" + k, u == null ? null : "U:" + u}) {
                if (llave == null) continue;
                Integer j = primero.putIfAbsent(llave, i);
                if (j != null) unir(padre, i, j);
            }
        }
        Map<Integer, List<Producto>> grupos = new LinkedHashMap<>();
        for (int i = 0; i < con.size(); i++) grupos.computeIfAbsent(raiz(padre, i), x -> new ArrayList<>()).add(con.get(i));
        return grupos.values().stream()
            .sorted(Comparator.comparing((List<Producto> g) -> Objects.requireNonNullElse(clave(g.get(0).getNumeroFactura()), "~")))
            .toList();
    }

    private static int raiz(int[] padre, int i) {
        while (padre[i] != i) i = padre[i] = padre[padre[i]];
        return i;
    }

    private static void unir(int[] padre, int a, int b) {
        padre[raiz(padre, a)] = raiz(padre, b);
    }

    /** Puts the changes on their bienes, each bien saved once with all its data, through
     *  {@link ProductoService#save} (Auditoría, price history, a change from another PC is
     *  not overwritten). A bien that can't be saved is reported and the rest go on. */
    public static Resultado aplicar(ProductoService servicio, List<Cambio> cambios) {
        Map<String, Producto> antes = new LinkedHashMap<>();
        Map<String, Producto> nuevos = new LinkedHashMap<>();
        for (Cambio c : cambios) {
            for (Producto p : c.bienes()) {
                antes.putIfAbsent(p.getId(), p.copia());
                Producto n = nuevos.computeIfAbsent(p.getId(), id -> p.copia());
                c.dato().poner.accept(n, c.nuevo());
            }
        }
        List<Producto> guardados = new ArrayList<>(), previos = new ArrayList<>();
        List<String> fallas = new ArrayList<>();
        for (Producto n : nuevos.values()) {
            try {
                guardados.add(servicio.save(n));
                previos.add(antes.get(n.getId()));
            } catch (Exception e) {
                fallas.add(Objects.requireNonNullElse(n.getCodigo(), n.getId()) + ": " + Objects.requireNonNullElse(e.getMessage(), "no se pudo guardar"));
            }
        }
        return new Resultado(guardados, previos, fallas);
    }

    /** Leaves the data that {@link #aplicar} changed as each bien had it; only on bienes
     *  still as {@link #aplicar} left them (a later capture stays). */
    public static int deshacer(ProductoService servicio, Resultado r, List<Cambio> cambios) {
        Set<Dato> datos = cambios.stream().map(Cambio::dato).collect(Collectors.toCollection(() -> EnumSet.noneOf(Dato.class)));
        int n = 0;
        for (int i = 0; i < r.guardados().size(); i++) {
            Producto ahora = r.guardados().get(i).copia(), antes = r.antes().get(i);
            for (Dato d : datos) d.poner.accept(ahora, d.de(antes));
            try {
                servicio.save(ahora);
                n++;
            } catch (Exception ignored) {
                // changed again meanwhile: what is there now stays
            }
        }
        return n;
    }

    // ── comparar ──────────────────────────────────────────────────────────────

    /** What the inventory writes when it has nothing: a bien with «SIN MARCA» can take the marca. */
    private static final Set<String> NADA = Set.of("SIN MARCA", "SIN MODELO", "S/M", "SM", "N/A", "NA", "-", "--", "SIN DATO");

    static boolean vacio(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank() || NADA.contains(s.trim().toUpperCase(java.util.Locale.ROOT));
        if (v instanceof BigDecimal b) return b.signum() == 0;
        return false;
    }

    private static Object comparable(Object v) {
        if (v instanceof String s) return sinAcentos(s);
        if (v instanceof BigDecimal b) return b.stripTrailingZeros();
        return v;
    }

    static boolean igual(Object a, Object b) {
        if (vacio(a) || vacio(b)) return vacio(a) && vacio(b);
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(comparable(a), comparable(b));
    }

    private static boolean mismoNombre(Producto a, Producto b) {
        return nombre(a).equals(nombre(b));
    }

    private static String nombre(Producto p) {
        return sinAcentos(Objects.requireNonNullElse(p.getNombre(), ""));
    }

    private static String sinAcentos(String s) {
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replaceAll("[^\\p{Alnum}]+", " ").trim().toUpperCase(Locale.ROOT);
    }
}
