package com.sibim.util;

import com.sibim.model.enums.EstadoProducto;

import java.time.LocalDate;

public final class ProductoUtils {

    private ProductoUtils() {}

    public static EstadoProducto computeEstado(int stockActual, int stockMinimo, LocalDate fechaVencimiento) {
        if (fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now())) {
            return EstadoProducto.VENCIDO;
        }
        if (stockActual == 0) {
            return EstadoProducto.AGOTADO;
        }
        if (stockActual <= stockMinimo) {
            return EstadoProducto.BAJO_STOCK;
        }
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
