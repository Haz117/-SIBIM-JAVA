package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService.Dato;

import java.math.BigDecimal;
import java.util.*;

/**
 * Completar datos › «Tomar de los del mismo nombre»: a bien without factura, proveedor, costo,
 * fecha or vida útil whose other bienes with exactly the same name all say the same (17-nov-2026:
 * 153 facturas, 74 proveedores, 123 costos, 119 fechas and 72 vidas útiles that «Copiar datos de
 * la misma factura» doesn't reach, because the bien has no factura yet). The same name is not
 * always the same purchase, so it is a proposal reviewed bien by bien; it is saved through
 * {@link EditarVarios} (each bien by {@link ProductoService#save}, undone as a whole).
 */
public final class DelMismoNombre {

    private DelMismoNombre() {}

    /** A bien, what it would take and the bienes of its name that say it. */
    public record Propuesta(Producto bien, Object valor, List<String> de) {
        public String deTexto() {
            return de.size() <= 3 ? String.join(", ", de)
                : String.join(", ", de.subList(0, 3)) + " y " + (de.size() - 3) + " más";
        }
    }

    /** The datos it is offered for, in the order they help most (with the factura, «misma
     *  factura» brings the rest of the purchase). */
    public static final List<Dato> MISMO_NOMBRE = List.of(Dato.FACTURA, Dato.PROVEEDOR, Dato.PRECIO, Dato.FECHA, Dato.VIDA_UTIL);

    /** The dato with the most proposals (where Por hacer opens Completar datos), or null. */
    public static Dato conMas(List<Producto> bienes) {
        Dato mejor = null;
        int max = 0;
        for (Dato d : MISMO_NOMBRE) {
            int n = propuestas(bienes, d).size();
            if (n > max) { max = n; mejor = d; }
        }
        return mejor;
    }

    /** The datos it works for, as «Cambiar datos» saves them. */
    public static EditarVarios.Dato comoSeGuarda(Dato dato) {
        return switch (dato) {
            case FACTURA -> EditarVarios.Dato.FACTURA;
            case PROVEEDOR -> EditarVarios.Dato.PROVEEDOR;
            case PRECIO -> EditarVarios.Dato.COSTO;
            case FECHA -> EditarVarios.Dato.FECHA;
            case VIDA_UTIL -> EditarVarios.Dato.VIDA_UTIL;
            default -> null;
        };
    }

    /** The bienes in force lacking {@code dato} whose name's other bienes all have one same value. */
    public static List<Propuesta> propuestas(List<Producto> bienes, Dato dato) {
        EditarVarios.Dato ev = comoSeGuarda(dato);
        if (ev == null) return List.of();
        Map<String, List<Producto>> porNombre = new LinkedHashMap<>();
        for (Producto p : bienes) {
            if (p.getFechaBaja() != null) continue;
            String clave = CalidadDatosService.claveTexto(p.getNombre());
            if (!clave.isEmpty()) porNombre.computeIfAbsent(clave, k -> new ArrayList<>()).add(p);
        }
        List<Propuesta> out = new ArrayList<>();
        for (List<Producto> g : porNombre.values()) {
            if (g.size() < 2) continue;
            List<Producto> con = new ArrayList<>(), sin = new ArrayList<>();
            for (Producto p : g) (CalidadDatosService.falta(p, dato) ? sin : con).add(p);
            if (con.isEmpty() || sin.isEmpty()) continue;
            Set<Object> valores = new HashSet<>();
            for (Producto p : con) valores.add(comparable(ev.de(p)));
            if (valores.size() != 1) continue;
            Object valor = ev.de(con.get(0));
            if (!sirve(valor)) continue;
            List<String> de = con.stream().map(p -> Objects.requireNonNullElse(p.getCodigo(), p.getId()))
                .sorted(com.sibim.util.OrdenCodigo.POR_CODIGO).toList();
            for (Producto p : sin) out.add(new Propuesta(p, valor instanceof String s ? s.trim() : valor, de));
        }
        out.sort(Comparator.comparing((Propuesta x) -> CalidadDatosService.claveTexto(x.bien().getNombre()))
            .thenComparing(x -> Objects.requireNonNullElse(x.bien().getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO));
        return out;
    }

    /** What «Cambiar datos» saves for the chosen ones. */
    public static List<EditarVarios.Renglon> renglones(Dato dato, List<Propuesta> elegidas) {
        Map<String, Object> porBien = new HashMap<>();
        for (Propuesta p : elegidas) porBien.put(p.bien().getId(), p.valor());
        EditarVarios.Cambio cambio = new EditarVarios.Cambio(comoSeGuarda(dato), p -> porBien.get(p.getId()),
            "el de los demás bienes con su mismo nombre");
        return EditarVarios.revisar(elegidas.stream().map(Propuesta::bien).toList(), List.of(cambio));
    }

    /** «SIN DOCUMENTO», «S/FECHA»: written where the factura goes, not a factura to pass on; nor a
     *  folio fiscal typed as factura («Folio fiscal a su lugar» moves those). */
    private static boolean sirve(Object valor) {
        if (!(valor instanceof String s)) return true;
        String t = s.trim().toUpperCase(Locale.ROOT);
        return !(t.startsWith("SIN ") || t.startsWith("S/") || com.sibim.util.ProductoUtils.esSinDato(t) || Producto.pareceFolioFiscal(t));
    }

    private static Object comparable(Object v) {
        if (v instanceof String s) return CalidadDatosService.claveTexto(s);
        if (v instanceof BigDecimal b) return b.stripTrailingZeros();
        return v;
    }
}
