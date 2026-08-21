package com.sibim.util;

import com.sibim.model.enums.EstadoProducto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductoUtilsTest {

    @Test
    void entradaSumaAlStock() {
        assertEquals(15, ProductoUtils.calcularStockNuevo("entrada", 10, 5));
    }

    @Test
    void salidaRestaAlStock() {
        assertEquals(7, ProductoUtils.calcularStockNuevo("salida", 10, 3));
    }

    @Test
    void salidaNuncaBajaDeCero() {
        assertEquals(0, ProductoUtils.calcularStockNuevo("salida", 5, 10));
    }

    @Test
    void ajusteReemplazaElStockPorElValorAbsoluto() {
        assertEquals(42, ProductoUtils.calcularStockNuevo("ajuste", 10, 42));
    }

    @Test
    void transferenciaNoCambiaElStock() {
        assertEquals(10, ProductoUtils.calcularStockNuevo("transferencia", 10, 999));
    }

    @Test
    void estadoAgotadoCuandoStockEsCero() {
        assertEquals(EstadoProducto.AGOTADO, ProductoUtils.computeEstado(0, 5, null));
    }

    @Test
    void estadoBajoStockCuandoNoSuperaElMinimo() {
        assertEquals(EstadoProducto.BAJO_STOCK, ProductoUtils.computeEstado(3, 5, null));
    }

    @Test
    void estadoVencidoTienePrioridadSobreElStock() {
        assertEquals(EstadoProducto.VENCIDO, ProductoUtils.computeEstado(100, 5, LocalDate.now().minusDays(1)));
    }

    @Test
    void estadoActivoCuandoStockSuperaElMinimoYNoHayVencimiento() {
        assertEquals(EstadoProducto.ACTIVO, ProductoUtils.computeEstado(20, 5, LocalDate.now().plusDays(30)));
    }
}
