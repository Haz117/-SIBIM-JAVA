package com.sibim.util;

import com.sibim.model.enums.EstadoProducto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProductoUtilsTest {

    // ── computeEstado ─────────────────────────────────────────────────────────

    @Test
    void computeEstado_stockNormal_esActivo() {
        assertEquals(EstadoProducto.ACTIVO, ProductoUtils.computeEstado(10, 5, null));
    }

    @Test
    void computeEstado_stockCero_esAgotado() {
        assertEquals(EstadoProducto.AGOTADO, ProductoUtils.computeEstado(0, 5, null));
    }

    @Test
    void computeEstado_stockIgualAlMinimo_esBajoStock() {
        assertEquals(EstadoProducto.BAJO_STOCK, ProductoUtils.computeEstado(5, 5, null));
    }

    @Test
    void computeEstado_stockMenorQueMinimo_esBajoStock() {
        assertEquals(EstadoProducto.BAJO_STOCK, ProductoUtils.computeEstado(3, 5, null));
    }

    @Test
    void computeEstado_stockUno_minimoUno_esBajoStock() {
        assertEquals(EstadoProducto.BAJO_STOCK, ProductoUtils.computeEstado(1, 1, null));
    }

    @Test
    void computeEstado_fechaVencidaAyer_esVencido() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        assertEquals(EstadoProducto.VENCIDO, ProductoUtils.computeEstado(10, 5, ayer));
    }

    @Test
    void computeEstado_vencido_tienePreferenciaSobreAgotado() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        assertEquals(EstadoProducto.VENCIDO, ProductoUtils.computeEstado(0, 5, ayer));
    }

    @Test
    void computeEstado_vencido_tienePreferenciaSobreBajoStock() {
        LocalDate ayer = LocalDate.now().minusDays(1);
        assertEquals(EstadoProducto.VENCIDO, ProductoUtils.computeEstado(3, 5, ayer));
    }

    @Test
    void computeEstado_fechaHoy_noEsVencido() {
        // isBefore(now()) → false when date == today
        LocalDate hoy = LocalDate.now();
        assertNotEquals(EstadoProducto.VENCIDO, ProductoUtils.computeEstado(10, 5, hoy));
    }

    @Test
    void computeEstado_fechaManana_noEsVencido() {
        LocalDate manana = LocalDate.now().plusDays(1);
        assertNotEquals(EstadoProducto.VENCIDO, ProductoUtils.computeEstado(10, 5, manana));
    }

    // ── calcularStockNuevo ────────────────────────────────────────────────────

    @Test
    void calcularStockNuevo_entrada_suma() {
        assertEquals(15, ProductoUtils.calcularStockNuevo("entrada", 10, 5));
    }

    @Test
    void calcularStockNuevo_salida_resta() {
        assertEquals(5, ProductoUtils.calcularStockNuevo("salida", 10, 5));
    }

    @Test
    void calcularStockNuevo_salida_clampsEnCero() {
        // MovimientoService lo previene antes, pero calcularStockNuevo nunca devuelve negativo
        assertEquals(0, ProductoUtils.calcularStockNuevo("salida", 3, 10));
    }

    @Test
    void calcularStockNuevo_salida_exacto_resultaCero() {
        assertEquals(0, ProductoUtils.calcularStockNuevo("salida", 5, 5));
    }

    @Test
    void calcularStockNuevo_ajuste_estableceValorAbsoluto() {
        assertEquals(25, ProductoUtils.calcularStockNuevo("ajuste", 10, 25));
    }

    @Test
    void calcularStockNuevo_ajuste_puede_bajarStock() {
        assertEquals(1, ProductoUtils.calcularStockNuevo("ajuste", 50, 1));
    }

    @Test
    void calcularStockNuevo_transferencia_noModificaStock() {
        assertEquals(10, ProductoUtils.calcularStockNuevo("transferencia", 10, 5));
    }

    @Test
    void calcularStockNuevo_tipoCaseSensitivo_funciona() {
        // Switch uses toLowerCase()
        assertEquals(15, ProductoUtils.calcularStockNuevo("ENTRADA", 10, 5));
    }

    @Test
    void calcularStockNuevo_tipoDesconocido_noModificaStock() {
        assertEquals(10, ProductoUtils.calcularStockNuevo("unknown", 10, 5));
    }
}
