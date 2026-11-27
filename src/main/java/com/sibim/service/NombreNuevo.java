package com.sibim.service;

import com.sibim.model.Producto;

import java.util.*;

/**
 * When a bien's name is changed in its form, the other bienes in force that still have the
 * name it had: 17-nov-2026, Patrimonio renamed 25 bienes as their factura says and, in the 3
 * cases where several had the same name, renamed all of them one by one (8 of 8 sillas, 2 of
 * 2, 2 of 2). Each one is saved through {@link EditarVarios} (Auditoría, a change made meanwhile
 * on another PC is not overwritten) and the whole batch is undone from the notice.
 */
public final class NombreNuevo {

    private NombreNuevo() {}

    /** The bienes in force, other than this one, whose name is the one it had before (the same
     *  letters and numbers: capitals, accents, spaces and signs aside). Empty when the name did
     *  not change or the bien was just registered. */
    public static List<Producto> seLlamabanIgual(Producto antes, Producto guardado, List<Producto> todos) {
        if (antes == null || guardado == null) return List.of();
        String era = CalidadDatosService.claveTexto(antes.getNombre());
        if (era.isEmpty() || era.equals(CalidadDatosService.claveTexto(guardado.getNombre()))) return List.of();
        return todos.stream()
            .filter(p -> p.getFechaBaja() == null && !Objects.equals(p.getId(), guardado.getId()))
            .filter(p -> era.equals(CalidadDatosService.claveTexto(p.getNombre())))
            .sorted(Comparator.comparing(p -> Objects.requireNonNullElse(p.getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO))
            .toList();
    }

    /** Whether {@code otro} says it is of another purchase than {@code guardado}: another folio
     *  fiscal, another factura number, or another proveedor and another fecha. A generic name
     *  («IMPRESORA MULTIFUNCIONAL»: 10 bienes of 8 facturas on 18-nov-2026) goes to bienes of
     *  other purchases; those are offered unticked. Without data, nothing says it is another. */
    public static boolean deOtraCompra(Producto guardado, Producto otro) {
        String fa = folio(guardado), fb = folio(otro);
        if (fa != null && fb != null) return !fa.equals(fb);
        String na = numero(guardado), nb = numero(otro);
        if (na != null && nb != null) return !na.equals(nb);
        String pa = CalidadDatosService.claveTexto(guardado.getProveedor()), pb = CalidadDatosService.claveTexto(otro.getProveedor());
        boolean otroProveedor = !pa.isEmpty() && !pb.isEmpty() && !pa.equals(pb);
        boolean otraFecha = guardado.getFechaAdquisicion() != null && otro.getFechaAdquisicion() != null
            && !guardado.getFechaAdquisicion().equals(otro.getFechaAdquisicion());
        return otroProveedor && otraFecha;
    }

    /** The same folio fiscal, or the same factura number. */
    public static boolean deLaMismaFactura(Producto a, Producto b) {
        String fa = folio(a), fb = folio(b);
        if (fa != null && fb != null) return fa.equals(fb);
        String na = numero(a), nb = numero(b);
        return na != null && na.equals(nb);
    }

    /** Which come ticked: all of them, unless some say they are of another purchase — then the
     *  name is a generic one (18-nov-2026: renaming one «IMPRESORA MULTIFUNCIONAL», 6 of the
     *  other 9 were of other facturas and 3 had no data) and only those of its factura are. */
    public static Set<Producto> marcados(Producto guardado, List<Producto> otros) {
        boolean generico = otros.stream().anyMatch(p -> deOtraCompra(guardado, p));
        Set<Producto> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Producto p : otros) if (!generico || deLaMismaFactura(guardado, p)) out.add(p);
        return out;
    }

    /** The UUID of its CFDI, in its folio or, as the C-2 lists brought it, in «Factura». */
    private static String folio(Producto p) {
        String u = Producto.pareceFolioFiscal(p.getFolioFiscal()) ? p.getFolioFiscal()
            : Producto.pareceFolioFiscal(p.getNumeroFactura()) ? p.getNumeroFactura() : null;
        return u == null ? null : u.trim().toUpperCase(Locale.ROOT).replace("-", "");
    }

    private static String numero(Producto p) {
        if (Producto.pareceFolioFiscal(p.getNumeroFactura())) return null;
        String n = CalidadDatosService.claveTexto(p.getNumeroFactura()).replace(" ", "");
        return n.isEmpty() || n.startsWith("SIN") ? null : n;
    }

    /** Each one with the new name, for {@link EditarVarios#aplicar}. */
    public static List<EditarVarios.Renglon> renglones(List<Producto> bienes, String nombre) {
        return EditarVarios.revisar(bienes, List.of(EditarVarios.Cambio.fijo(EditarVarios.Dato.NOMBRE, nombre)));
    }

    // ── «Solo a este», remembered (26-nov-2026) ───────────────────────────────

    /** How many changes of name declined are kept: the newest ones. */
    static final int RECORDADOS = 200;

    /** Whether «Solo a este» was already said to this very change of name: on 24 and 25-nov-2026,
     *  renaming the esposas that were not semirígidas as their photo showed, «ESPOSAS SEMIRIGIDAS
     *  (MANO)» → «ESPOSAS (MANO)» was offered for the other 70 six times and declined six times.
     *  @param recordados what {@link #conRechazo} last returned (null when nothing yet) */
    public static boolean yaDijoQueNo(String recordados, String era, String ahora) {
        if (recordados == null || recordados.isEmpty()) return false;
        String par = par(era, ahora);
        if (par == null) return false;
        for (String l : recordados.split("\n")) if (l.equals(par)) return true;
        return false;
    }

    /** {@code recordados} with this change of name first; the oldest go once there are
     *  {@link #RECORDADOS} or they no longer fit in a preference (8 KB). */
    public static String conRechazo(String recordados, String era, String ahora) {
        String par = par(era, ahora);
        List<String> lineas = new ArrayList<>();
        if (par != null) lineas.add(par);
        if (recordados != null && !recordados.isEmpty())
            for (String l : recordados.split("\n")) if (!l.isEmpty() && !l.equals(par)) lineas.add(l);
        while (lineas.size() > RECORDADOS) lineas.remove(lineas.size() - 1);
        String s = String.join("\n", lineas);
        while (s.length() > java.util.prefs.Preferences.MAX_VALUE_LENGTH && lineas.size() > 1) {
            lineas.remove(lineas.size() - 1);
            s = String.join("\n", lineas);
        }
        return s;
    }

    /** «ERA\tAHORA», each as {@link CalidadDatosService#claveTexto}: the same letters and numbers. */
    private static String par(String era, String ahora) {
        String a = CalidadDatosService.claveTexto(era), b = CalidadDatosService.claveTexto(ahora);
        return a.isEmpty() || b.isEmpty() || a.equals(b) ? null : a + "\t" + b;
    }
}
