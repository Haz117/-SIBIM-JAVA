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

    /** 24-nov-2026: 10 bienes had $1.00 so as not to leave the costo empty; 2 were corrected that week. */
    @Test
    void unCostoDeUnPeso_siguePorCapturar_yElQueYaLoTeniaNoPregunta() {
        Producto relleno = new Producto();
        relleno.setPrecioCompra(new BigDecimal("1.00"));
        assertTrue(com.sibim.service.CalidadDatosService.falta(relleno, com.sibim.service.CalidadDatosService.Dato.PRECIO),
            "sale en Completar datos › Sin precio");
        relleno.setPrecioCompra(new BigDecimal("1.01"));
        assertFalse(com.sibim.service.CalidadDatosService.falta(relleno, com.sibim.service.CalidadDatosService.Dato.PRECIO));
        relleno.setPrecioCompra(BigDecimal.ONE);
        assertTrue(ProductoDialogFactory.precioConfirmado(new BigDecimal("1.00"), relleno),
            "guardar otro dato de un bien que ya tenía $1.00 no pregunta");
    }
}
