package com.sibim.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductoTest {

    private Producto bienDepreciable(BigDecimal precioCompra, LocalDate fechaAdquisicion,
                                      Integer vidaUtilAnios, BigDecimal valorResidual) {
        Producto p = new Producto();
        p.setPrecioCompra(precioCompra);
        p.setFechaAdquisicion(fechaAdquisicion);
        p.setVidaUtilAnios(vidaUtilAnios);
        p.setValorResidual(valorResidual);
        return p;
    }

    // ── Datos incompletos → null ────────────────────────────────────────────

    @Test
    void getValorDepreciado_sinFechaAdquisicion_esNull() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), null, 5, BigDecimal.ZERO);
        assertNull(p.getValorDepreciado());
    }

    @Test
    void getValorDepreciado_sinVidaUtil_esNull() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now().minusYears(1), null, BigDecimal.ZERO);
        assertNull(p.getValorDepreciado());
    }

    @Test
    void getValorDepreciado_sinPrecioCompra_esNull() {
        Producto p = bienDepreciable(null, LocalDate.now().minusYears(1), 5, BigDecimal.ZERO);
        assertNull(p.getValorDepreciado());
    }

    @Test
    void getValorDepreciado_precioCompraCero_esNull() {
        Producto p = bienDepreciable(BigDecimal.ZERO, LocalDate.now().minusYears(1), 5, BigDecimal.ZERO);
        assertNull(p.getValorDepreciado());
    }

    @Test
    void getValorDepreciado_vidaUtilCero_esNull() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now().minusYears(1), 0, BigDecimal.ZERO);
        assertNull(p.getValorDepreciado());
    }

    // ── Casos con datos completos ───────────────────────────────────────────

    @Test
    void getValorDepreciado_fechaAdquisicionFutura_valorCompleto() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now().plusDays(10), 5, BigDecimal.ZERO);
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(p.getValorDepreciado()));
    }

    @Test
    void getValorDepreciado_mitadDeVidaUtil_aproximadamenteMitadDeValor() {
        // Adquirido hace 2.5 años, vida útil de 5 años, sin residual → ~50% depreciado
        Producto p = bienDepreciable(BigDecimal.valueOf(10000),
            LocalDate.now().minusDays(365 * 5 / 2), 5, BigDecimal.ZERO);
        BigDecimal valor = p.getValorDepreciado();
        // tolerancia de $50 por redondeo de días/años
        assertTrue(valor.subtract(BigDecimal.valueOf(5000)).abs().compareTo(BigDecimal.valueOf(50)) <= 0,
            "esperado ~5000, fue " + valor);
    }

    @Test
    void getValorDepreciado_totalmenteDepreciado_regresaValorResidual() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000),
            LocalDate.now().minusYears(20), 5, BigDecimal.valueOf(500));
        assertEquals(0, BigDecimal.valueOf(500).compareTo(p.getValorDepreciado()));
    }

    @Test
    void getValorDepreciadoEn_fechaFuturaProyectaMenosValor() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now(), 10, BigDecimal.ZERO);
        BigDecimal hoy = p.getValorDepreciadoEn(LocalDate.now());
        BigDecimal enCincoAnios = p.getValorDepreciadoEn(LocalDate.now().plusYears(5));
        assertEquals(-1, enCincoAnios.compareTo(hoy));
    }

    @Test
    void getValorDepreciadoEn_masAllaDeVidaUtil_clampAResidual() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now(), 3, BigDecimal.valueOf(1000));
        BigDecimal masAllaDeLaVidaUtil = p.getValorDepreciadoEn(LocalDate.now().plusYears(50));
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(masAllaDeLaVidaUtil));
    }

    // ── getPorcentajeDepreciado ──────────────────────────────────────────────

    @Test
    void getPorcentajeDepreciado_sinDatos_esNull() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), null, 5, BigDecimal.ZERO);
        assertNull(p.getPorcentajeDepreciado());
    }

    @Test
    void getPorcentajeDepreciado_fechaFutura_esCero() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now().plusDays(10), 5, BigDecimal.ZERO);
        assertEquals(0, p.getPorcentajeDepreciado());
    }

    @Test
    void getPorcentajeDepreciado_totalmenteDepreciado_es100() {
        Producto p = bienDepreciable(BigDecimal.valueOf(10000), LocalDate.now().minusYears(20), 5, BigDecimal.ZERO);
        assertEquals(100, p.getPorcentajeDepreciado());
    }
}
