package com.sibim.util;

import com.sibim.model.enums.EstadoProducto;

import java.time.LocalDate;

public final class ProductoUtils {

    private ProductoUtils() {}

    public static EstadoProducto computeEstado(int stockActual, int stockMinimo, LocalDate fechaVencimiento) {
        if (fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now())) {
            return EstadoProducto.VENCIDO;
        }
        // This is a patrimonial inventory, not a shop: nothing is sold or restocked,
        // so a bien is never "agotado" or "bajo stock" — those states (and the
        // minimum they compared against) are no longer produced. The enum keeps
        // the two constants only so old exports and filters still parse.
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
}
