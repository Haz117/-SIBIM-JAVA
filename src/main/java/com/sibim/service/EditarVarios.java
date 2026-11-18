package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;
import com.sibim.util.ProductoUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Bienes › select several › «Cambiar datos»: the same data for several bienes at once.
 * On 12/13-nov-2026 Patrimonio edited 145 bienes one by one (about 75 s each), most of
 * the time typing what the previous bien had just got: tipo de bien 71 of 88 times,
 * color 68 of 95, proveedor 57 of 90, vida útil 56 of 75; and corrected the same price
 * on three identical bienes three times. Only what is filled in changes; each bien is
 * saved through {@link ProductoService#save} (Auditoría, price history, a change made
 * meanwhile on another PC is not overwritten), and the whole batch can be undone.
 */
public final class EditarVarios {

    private EditarVarios() {}

    /** What can be changed, in the order of the form (the costo before the valor residual:
     *  «15 % del costo» is worked out from the costo this same change leaves). */
    public enum Dato {
        // Not in «Cambiar datos»: only for the bienes that had the name a bien just changed (NombreNuevo).
        NOMBRE("Nombre", Producto::getNombre, (p, v) -> p.setNombre((String) v)),
        FACTURA("N° Factura", Producto::getNumeroFactura, (p, v) -> p.setNumeroFactura((String) v)),
        FOLIO_FISCAL("Folio fiscal", Producto::getFolioFiscal, (p, v) -> p.setFolioFiscal((String) v)),
        PROVEEDOR("Proveedor", Producto::getProveedor, (p, v) -> p.setProveedor((String) v)),
        FECHA("Fecha de adquisición", Producto::getFechaAdquisicion, (p, v) -> p.setFechaAdquisicion((LocalDate) v)),
        // No sale price for a municipal bien: the form saves the costo in both (ProductoDialogFactory).
        COSTO("Costo", Producto::getPrecioCompra, (p, v) -> { p.setPrecioCompra((BigDecimal) v); p.setPrecioVenta((BigDecimal) v); }),
        VIDA_UTIL("Vida útil", Producto::getVidaUtilAnios, (p, v) -> p.setVidaUtilAnios((Integer) v)),
        VALOR_RESIDUAL("Valor residual", Producto::getValorResidual, (p, v) -> p.setValorResidual((BigDecimal) v)),
        COLOR("Color", Producto::getColor, (p, v) -> p.setColor((String) v)),
        TIPO_BIEN("Tipo de bien", Producto::getTipoBien, (p, v) -> p.setTipoBien((String) v)),
        MARCA("Marca", Producto::getMarca, (p, v) -> p.setMarca((String) v)),
        MODELO("Modelo", Producto::getModelo, (p, v) -> p.setModelo((String) v)),
        UBICACION("Ubicación", Producto::getUbicacion, (p, v) -> p.setUbicacion((String) v));

        public final String etiqueta;
        private final Function<Producto, Object> leer;
        private final BiConsumer<Producto, Object> poner;

        Dato(String etiqueta, Function<Producto, Object> leer, BiConsumer<Producto, Object> poner) {
            this.etiqueta = etiqueta;
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

    /**
     * One dato to put, and how: the same value for all, or one worked out from each bien.
     * @param valor from the bien as this change leaves it so far (the costo already
     *              changed when the valor residual is worked out); null leaves that bien as it is
     * @param como  what the review says is put ("«MUEBLE»", "el 15 % del costo de cada uno")
     */
    public record Cambio(Dato dato, Function<Producto, Object> valor, String como) {
        public static Cambio fijo(Dato dato, Object valor) {
            return new Cambio(dato, p -> valor, dato.texto(valor));
        }
    }

    /** «Sumarle el IVA (16 %) al costo de cada uno»: 99 of 178 cost changes up to 11-nov-2026
     *  were the costo times 1.16. A bien without costo is left as it is. */
    public static Cambio ivaACadaUno() {
        return new Cambio(Dato.COSTO, p -> p.getPrecioCompra() == null || p.getPrecioCompra().signum() <= 0 ? null
            : p.getPrecioCompra().multiply(new BigDecimal("1.16")).setScale(2, RoundingMode.HALF_UP),
            "el suyo más el IVA (16 %)");
    }

    /** «El 15 % del costo de cada uno» (see {@link ProductoUtils#residualSugerido}). */
    public static Cambio residualDeCadaUno() {
        return new Cambio(Dato.VALOR_RESIDUAL, p -> ProductoUtils.residualSugerido(p.getPrecioCompra()),
            "el " + ProductoUtils.PORCENTAJE_RESIDUAL.toPlainString() + " % del costo de cada uno");
    }

    /** One bien in the review: what it has and what it gets, only for what changes. */
    public record Renglon(Producto bien, Producto nuevo, List<Dato> cambian) {
        public String resumen() {
            return cambian.stream().map(d -> d.etiqueta + ": " + d.texto(d.de(bien)) + " → " + d.texto(d.de(nuevo)))
                .collect(Collectors.joining(" · "));
        }
    }

    /** What the changes do to each bien, leaving out the ones that already have it all. */
    public static List<Renglon> revisar(List<Producto> bienes, List<Cambio> cambios) {
        List<Cambio> enOrden = cambios.stream().sorted(Comparator.comparing(c -> c.dato().ordinal())).toList();
        List<Renglon> out = new ArrayList<>();
        for (Producto p : bienes) {
            Producto n = p.copia();
            List<Dato> cambian = new ArrayList<>();
            for (Cambio c : enOrden) {
                Object v = c.valor().apply(n);
                if (v == null) continue;
                c.dato().poner.accept(n, v);
                if (!iguales(c.dato().de(p), v) && !cambian.contains(c.dato())) cambian.add(c.dato());
            }
            if (!cambian.isEmpty()) out.add(new Renglon(p, n, cambian));
        }
        return out;
    }

    /** What was saved, how each saved bien was before (to undo it), what failed, and which datos. */
    public record Resultado(List<Producto> guardados, List<Producto> antes, List<String> fallas, Set<Dato> datos) {}

    /** Saves the reviewed bienes one by one; a bien that can't be saved (another PC changed
     *  it, a valor residual above its costo…) is reported and the rest go on. */
    public static Resultado aplicar(ProductoService servicio, List<Renglon> renglones) {
        return aplicar(servicio, renglones, x -> { });
    }

    /** @param avance from 0 to 1 as the bienes are saved (a batch of hundreds takes a while) */
    public static Resultado aplicar(ProductoService servicio, List<Renglon> renglones, java.util.function.DoubleConsumer avance) {
        Set<Dato> datos = EnumSet.noneOf(Dato.class);
        List<Producto> guardados = new ArrayList<>(), antes = new ArrayList<>();
        List<String> fallas = new ArrayList<>();
        int hechos = 0;
        for (Renglon r : renglones) {
            avance.accept((double) hechos++ / Math.max(1, renglones.size()));
            datos.addAll(r.cambian());
            try {
                guardados.add(servicio.save(r.nuevo().copia()));
                antes.add(r.bien());
            } catch (Exception e) {
                fallas.add(Objects.requireNonNullElse(r.bien().getCodigo(), r.bien().getId()) + ": "
                    + Objects.requireNonNullElse(e.getMessage(), "no se pudo guardar"));
            }
        }
        return new Resultado(guardados, antes, fallas, datos);
    }

    /** Leaves the changed datos as each bien had them; a bien changed again since stays as it is now.
     *  @return how many went back */
    public static int deshacer(ProductoService servicio, Resultado r) {
        int n = 0;
        for (int i = 0; i < r.guardados().size(); i++) {
            Producto ahora = r.guardados().get(i).copia(), antes = r.antes().get(i);
            for (Dato d : r.datos()) d.poner.accept(ahora, d.de(antes));
            try {
                servicio.save(ahora);
                n++;
            } catch (Exception ignored) {
                // changed again meanwhile: what is there now stays
            }
        }
        return n;
    }

    /** What the selected bienes have now, said next to each field: «Todos: MUEBLE»,
     *  «Todos vacíos», «3 distintos · vacío en 5». */
    public static String ahora(Dato d, List<Producto> bienes) {
        Map<Object, Long> valores = new LinkedHashMap<>();
        long vacios = 0;
        for (Producto p : bienes) {
            Object v = d.de(p);
            if (vacio(v)) vacios++;
            else valores.merge(comparable(v), 1L, Long::sum);
        }
        if (valores.isEmpty()) return bienes.size() == 1 ? "Vacío" : "Todos vacíos";
        if (valores.size() == 1 && vacios == 0) {
            Object unico = bienes.stream().map(d::de).filter(v -> !vacio(v)).findFirst().orElse(null);
            return (bienes.size() == 1 ? "" : "Todos: ") + d.texto(unico);
        }
        String distintos = valores.size() == 1
            ? d.texto(bienes.stream().map(d::de).filter(v -> !vacio(v)).findFirst().orElse(null)) + " en " + valores.values().iterator().next()
            : valores.size() + " distintos";
        return distintos + (vacios > 0 ? " · vacío en " + vacios : "");
    }

    static boolean vacio(Object v) {
        if (v == null) return true;
        if (v instanceof String s) return s.isBlank();
        if (v instanceof BigDecimal b) return b.signum() == 0;
        return false;
    }

    private static Object comparable(Object v) {
        if (v instanceof String s) return s.trim().toUpperCase(Locale.ROOT);
        if (v instanceof BigDecimal b) return b.stripTrailingZeros();
        return v;
    }

    static boolean iguales(Object a, Object b) {
        if (vacio(a) && vacio(b)) return true;
        if (vacio(a) || vacio(b)) return false;
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        if (a instanceof String x && b instanceof String y) return x.trim().equals(y.trim());
        return a.equals(b);
    }
}
