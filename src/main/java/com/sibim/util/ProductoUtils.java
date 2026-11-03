package com.sibim.util;

import com.sibim.model.enums.EstadoProducto;

import java.time.LocalDate;

public final class ProductoUtils {

    private ProductoUtils() {}

    public static EstadoProducto computeEstado(int stockActual, int stockMinimo, LocalDate fechaVencimiento) {
        if (fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now())) {
            return EstadoProducto.VENCIDO;
        }
        // A patrimonial inventory, not a shop: nothing is sold or restocked, so the
        // quantity never makes a bien "agotado" or "bajo stock" — only its warranty matters.
        return EstadoProducto.ACTIVO;
    }

    public static int calcularStockNuevo(String tipo, int stockActual, int cantidad) {
        return switch (tipo.toLowerCase()) {
            case "entrada" -> stockActual + cantidad;
            case "salida" -> Math.max(0, stockActual - cantidad);
            case "ajuste" -> cantidad;
            // A transfer relocates the product to a different area — it
            // doesn't consume stock, so the count doesn't change here. See
            // MovimientoRepository#addMovimientoAtomic for the area
            // reassignment that actually makes a transfer do something.
            case "transferencia" -> stockActual;
            default -> stockActual;
        };
    }

    private static final java.util.Set<String> SIN_DATO = java.util.Set.of(
        "S/M", "SM", "S/MARCA", "S/MOD", "S/MODELO", "S/N", "SN", "S N", "S/S", "S/SERIE", "S/NUMERO",
        "N/A", "NA", "N/D", "ND", "NO TIENE", "NO APLICA", "NINGUNO", "NINGUNA", "SIN DATO", "SIN DATOS",
        "-", "--", "---");

    /** "SIN MARCA", "SIN MOLDELO", "S/N", "N/A"…: what is typed in marca, modelo or serie
     *  when the bien has none. 14 of the 21 bienes registered 28-oct to 2-nov-2026 had it
     *  written by hand; it is saved empty, and the formats print "SIN MARCA" for an empty
     *  one anyway. */
    public static boolean esSinDato(String texto) {
        if (texto == null) return false;
        String t = java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        if (t.isEmpty()) return false;
        if (SIN_DATO.contains(t)) return true;
        // "SIN MARCA", "SIN MODELO" (and "SIN MOLDELO"), "SIN SERIE", "SIN NUMERO DE SERIE"…
        return t.matches("SIN (MARCA|MODELO|MOLDELO|MODELOS|SERIE|SERIAL|NUMERO( DE SERIE)?|NO\\.? DE SERIE|N° DE SERIE)\\.?");
    }

    /** {@code texto} trimmed, or null when it is empty or only says the bien has none. */
    public static String dato(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() || esSinDato(t) ? null : t;
    }
}
