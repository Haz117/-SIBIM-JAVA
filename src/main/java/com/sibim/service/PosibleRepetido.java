package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.util.FormatUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.*;

/** Before saving a bien: is it already in SIBIM? The 136 copies removed in November 2026
 *  came from the load, but some were typed by hand: two equal archiveros registered three
 *  minutes apart, two fans with the same number. Only a warning: the person decides. */
public final class PosibleRepetido {

    private PosibleRepetido() {}

    /** A serie on more bienes than this is a model or catalogue code, not a bien's own. */
    private static final int SERIE_DE_MODELO = 3;
    /** How recent an equal bien must be to look like the same one registered twice. */
    private static final Duration RECIENTE = Duration.ofMinutes(30);

    /** What to tell before saving {@code nuevo}; empty when nothing looks repeated.
     *  @param antes the bien as it was, null when it is being registered */
    public static List<String> avisos(Producto nuevo, Producto antes, List<Producto> todos, LocalDateTime ahora) {
        List<String> out = new ArrayList<>();
        String serie = serie(nuevo);
        if (serie != null && (antes == null || !serie.equals(serie(antes)))) {
            List<Producto> conLaSerie = todos.stream()
                .filter(o -> o.getFechaBaja() == null && !Objects.equals(o.getId(), nuevo.getId()) && serie.equals(serie(o)))
                .toList();
            if (!conLaSerie.isEmpty() && conLaSerie.size() < SERIE_DE_MODELO)
                conLaSerie.forEach(o -> out.add("La serie " + nuevo.getNumeroSerie().trim() + " ya la tiene " + quien(o) + "."));
        }
        if (antes == null) {
            todos.stream()
                .filter(o -> o.getFechaBaja() == null && !Objects.equals(o.getId(), nuevo.getId()))
                .filter(o -> o.getCreadoEn() != null && Duration.between(o.getCreadoEn(), ahora).abs().compareTo(RECIENTE) <= 0)
                .filter(o -> texto(o.getNombre()).equals(texto(nuevo.getNombre())) && !texto(nuevo.getNombre()).isEmpty()
                    && texto(o.getArea()).equals(texto(nuevo.getArea()))
                    && texto(o.getResguardante()).equals(texto(nuevo.getResguardante()))
                    && mismoCosto(o, nuevo))
                .forEach(o -> out.add("Hace " + minutos(o.getCreadoEn(), ahora) + " se registró " + quien(o)
                    + " con el mismo nombre, área, resguardante y costo."));
        }
        return out;
    }

    /** The serie as compared: no spaces, dashes or case; null when it is no serie. */
    static String serie(Producto p) {
        String s = p.getNumeroSerie();
        if (s == null || CalidadDatosService.noEsSerie(s)) return null;
        String k = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (k.length() < 5 || k.chars().allMatch(c -> c == '0')) return null;
        String factura = p.getNumeroFactura() == null ? "" : p.getNumeroFactura().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return k.equals(factura) ? null : k;
    }

    private static String quien(Producto o) {
        StringBuilder s = new StringBuilder(Objects.requireNonNullElse(o.getCodigo(), "otro bien"));
        if (o.getNombre() != null) s.append(" «").append(o.getNombre().length() > 50 ? o.getNombre().substring(0, 50) + "…" : o.getNombre()).append("»");
        if (o.getArea() != null) s.append(", de ").append(o.getArea());
        if (o.getResguardante() != null && !o.getResguardante().isBlank()) s.append(", con ").append(o.getResguardante());
        return s.toString();
    }

    private static boolean mismoCosto(Producto a, Producto b) {
        var x = a.getPrecioCompra() == null ? java.math.BigDecimal.ZERO : a.getPrecioCompra();
        var y = b.getPrecioCompra() == null ? java.math.BigDecimal.ZERO : b.getPrecioCompra();
        return x.compareTo(y) == 0;
    }

    private static String minutos(LocalDateTime cuando, LocalDateTime ahora) {
        long m = Math.max(1, Duration.between(cuando, ahora).abs().toMinutes());
        return FormatUtils.plural(m, "minuto", "minutos");
    }

    private static String texto(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
