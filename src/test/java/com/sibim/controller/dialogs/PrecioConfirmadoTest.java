package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Saving a bien only asks about the price above $10 million a piece, and not
 *  again for the price the bien already had. */
class PrecioConfirmadoTest {

    @Test
    void bajoElLimite_oElQueYaTenia_noPregunta() {
        assertTrue(ProductoDialogFactory.precioConfirmado(new BigDecimal("4276827.14"), null), "el tractor de orugas");
        assertTrue(ProductoDialogFactory.precioConfirmado(new BigDecimal("9999999.99"), null));
        Producto ya = new Producto();
        ya.setPrecioCompra(new BigDecimal("12500000.00"));
        assertTrue(ProductoDialogFactory.precioConfirmado(new BigDecimal("12500000"), ya),
            "guardar otro dato de un bien caro no vuelve a preguntar");
    }
}
