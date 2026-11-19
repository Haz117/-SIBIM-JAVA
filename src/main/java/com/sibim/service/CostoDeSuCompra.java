package com.sibim.service;

import com.sibim.model.Producto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bienes of one purchase with the same name and different costos (19-nov-2026: 23 such groups;
 * in 4 of them, 7 bienes without the IVA their twins have — the IVA was added by hand bien by
 * bien, 89 times in a week — and in others a digit typed wrong, $1,905.00 for $12,905.00).
 * Centavos of difference are the factura's unit price rounded and are left alone.
 */
public final class CostoDeSuCompra {

    private CostoDeSuCompra() {}

    /** Up to this much apart, two costos are the same one rounded otherwise. */
    static final BigDecimal CENTAVOS = new BigDecimal("1.00");
    private static final BigDecimal IVA = new BigDecimal("1.16");

    /**
     * One group.
     * @param costos     each costo and how many bienes have it, the most common first
     * @param propuesto  the costo they would all take: the one with IVA, or the most common
     * @param faltaIva   the lower costo times 1.16 is the higher one
     */
    public record Grupo(String nombre, String compra, List<Producto> bienes, LinkedHashMap<BigDecimal, Long> costos,
                        BigDecimal propuesto, boolean faltaIva) {
        /** The ones that change if all take {@code costo}. */
        public List<Producto> cambian(BigDecimal costo) {
            return bienes.stream().filter(p -> p.getPrecioCompra().compareTo(costo) != 0).toList();
        }

        /** «3 × $3,180.69 · 2 × $3,689.60». */
        public String resumen() {
            return costos.entrySet().stream().map(e -> e.getValue() + " × " + com.sibim.util.FormatUtils.formatCurrency(e.getKey()))
                .collect(Collectors.joining(" · "));
        }
    }

    /** The groups, the ones missing the IVA first and then by how many bienes. */
    public static List<Grupo> grupos(List<Producto> todos) {
        List<Grupo> out = new ArrayList<>();
        for (List<Producto> compra : MismaFactura.compras(todos)) {
            Map<String, List<Producto>> porNombre = new LinkedHashMap<>();
            for (Producto p : compra) {
                if (p.getPrecioCompra() == null || p.getPrecioCompra().signum() <= 0) continue;
                String k = CalidadDatosService.claveTexto(p.getNombre());
                if (!k.isEmpty()) porNombre.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
            }
            for (List<Producto> bienes : porNombre.values()) {
                Grupo g = grupo(bienes);
                if (g != null) out.add(g);
            }
        }
        out.sort(Comparator.comparing((Grupo g) -> !g.faltaIva()).thenComparing(g -> -g.bienes().size()));
        return out;
    }

    static Grupo grupo(List<Producto> bienes) {
        if (bienes.size() < 2) return null;
        Map<BigDecimal, Long> cuenta = new HashMap<>();
        for (Producto p : bienes) cuenta.merge(p.getPrecioCompra().setScale(2, RoundingMode.HALF_UP), 1L, Long::sum);
        if (cuenta.size() < 2) return null;
        BigDecimal min = Collections.min(cuenta.keySet()), max = Collections.max(cuenta.keySet());
        if (max.subtract(min).compareTo(CENTAVOS) <= 0) return null;
        LinkedHashMap<BigDecimal, Long> costos = cuenta.entrySet().stream()
            .sorted(Map.Entry.<BigDecimal, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        boolean faltaIva = cuenta.size() == 2 && conIva(min).subtract(max).abs().compareTo(new BigDecimal("0.02")) <= 0;
        BigDecimal propuesto = faltaIva ? max : costos.keySet().iterator().next();
        Producto p = bienes.get(0);
        return new Grupo(Objects.requireNonNullElse(p.getNombre(), "").trim(), MismaFactura.etiqueta(p),
            List.copyOf(bienes), costos, propuesto, faltaIva);
    }

    static BigDecimal conIva(BigDecimal costo) {
        return costo.multiply(IVA).setScale(2, RoundingMode.HALF_UP);
    }

    /** How many bienes the groups would change, each to its proposed costo. */
    public static long cuantos(List<Grupo> grupos) {
        return grupos.stream().mapToLong(g -> g.cambian(g.propuesto()).size()).sum();
    }

    /** What to save: each bien of {@code elegidos} to the costo chosen for its group. */
    public static List<EditarVarios.Renglon> renglones(Map<Grupo, BigDecimal> elegidos) {
        List<EditarVarios.Renglon> out = new ArrayList<>();
        elegidos.forEach((g, costo) -> out.addAll(EditarVarios.revisar(g.cambian(costo),
            List.of(EditarVarios.Cambio.fijo(EditarVarios.Dato.COSTO, costo)))));
        return out;
    }
}
