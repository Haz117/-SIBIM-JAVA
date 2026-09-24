package com.sibim.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Producto.getValorDepreciadoEn(LocalDate) and getPorcentajeDepreciado().
 *
 * All tests are pure in-memory; no database connection required. The straight-line
 * formula under test is:
 *   depreciable = precioCompra - valorResidual
 *   depAnual    = depreciable / vidaUtilAnios
 *   depAcum     = depAnual * diasTranscurridos / 365
 *   resultado   = precioCompra - depAcum  (clamped to residual when fully depreciated)
 */
class ProductoDepreciacionTest {

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Producto productoBase(BigDecimal precioCompra,
                                         Integer vidaUtilAnios,
                                         BigDecimal valorResidual,
                                         LocalDate fechaAdquisicion) {
        Producto p = new Producto();
        p.setPrecioCompra(precioCompra);
        p.setVidaUtilAnios(vidaUtilAnios);
        p.setValorResidual(valorResidual);
        p.setFechaAdquisicion(fechaAdquisicion);
        return p;
    }

    // ── tests ────────────────────────────────────────────────────────────────

    /** precioCompra=10000, vidaUtil=10 años, residual=0, fechaAdq=hace 365 días → ≈9000 */
    @Test
    void depreciacion_datosCompletos_calculaCorrectamente() {
        LocalDate hace365 = LocalDate.now().minusDays(365);
        Producto p = productoBase(
                BigDecimal.valueOf(10000),
                10,
                BigDecimal.ZERO,
                hace365);

        BigDecimal resultado = p.getValorDepreciadoEn(LocalDate.now());

        assertNotNull(resultado);
        // depAnual = 10000/10 = 1000; depAcum = 1000*365/365 = 1000; valor = 9000
        assertEquals(0, resultado.compareTo(BigDecimal.valueOf(9000)),
                "Se esperaba ~9000 pero fue: " + resultado);
    }

    /** fechaAdq hace 15 años, vidaUtil=10, residual=500 → ya depreciado → devuelve 500 */
    @Test
    void depreciacion_totalmenteDepreciado_devuelveResidual() {
        LocalDate hace15Anios = LocalDate.now().minusYears(15);
        Producto p = productoBase(
                BigDecimal.valueOf(5000),
                10,
                BigDecimal.valueOf(500),
                hace15Anios);

        BigDecimal resultado = p.getValorDepreciadoEn(LocalDate.now());

        assertNotNull(resultado);
        assertEquals(0, resultado.compareTo(BigDecimal.valueOf(500)),
                "Bien completamente depreciado debe devolver el valor residual 500");
    }

    /** fechaAdq=hoy → diasTranscurridos=0 → devuelve precioCompra */
    @Test
    void depreciacion_sinDepreciar_devuelvePrecioCompra() {
        Producto p = productoBase(
                BigDecimal.valueOf(8000),
                5,
                BigDecimal.ZERO,
                LocalDate.now());

        BigDecimal resultado = p.getValorDepreciadoEn(LocalDate.now());

        assertNotNull(resultado);
        assertEquals(0, resultado.compareTo(BigDecimal.valueOf(8000)),
                "Bien recién adquirido debe devolver precioCompra=" + 8000);
    }

    /** precioCompra=null → null */
    @Test
    void depreciacion_sinPrecioCompra_devuelveNull() {
        Producto p = productoBase(null, 10, BigDecimal.ZERO, LocalDate.now().minusYears(1));

        assertNull(p.getValorDepreciadoEn(LocalDate.now()));
    }

    /** fechaAdquisicion=null → null */
    @Test
    void depreciacion_sinFechaAdquisicion_devuelveNull() {
        Producto p = productoBase(BigDecimal.valueOf(5000), 10, BigDecimal.ZERO, null);

        assertNull(p.getValorDepreciadoEn(LocalDate.now()));
    }

    /** vidaUtilAnios=null → null */
    @Test
    void depreciacion_sinVidaUtil_devuelveNull() {
        Producto p = productoBase(
                BigDecimal.valueOf(5000),
                null,
                BigDecimal.ZERO,
                LocalDate.now().minusYears(1));

        assertNull(p.getValorDepreciadoEn(LocalDate.now()));
    }

    /** vidaUtilAnios=0 → null */
    @Test
    void depreciacion_vidaUtilCero_devuelveNull() {
        Producto p = productoBase(
                BigDecimal.valueOf(5000),
                0,
                BigDecimal.ZERO,
                LocalDate.now().minusYears(1));

        assertNull(p.getValorDepreciadoEn(LocalDate.now()));
    }

    /**
     * precioCompra=10000, residual=1000, vidaUtil=9 años, fechaAdq=hace 1314 días (≈3.6 años).
     * depreciable=9000, depAnual=1000, depAcum=1000*1314/365≈3600, valor≈6400.
     * Tolerancia ±5 para cubrir diferencias de redondeo del BigDecimal de 10 decimales.
     */
    @Test
    void depreciacion_conValorResidual_reduceLaBase() {
        LocalDate hace1314Dias = LocalDate.now().minusDays(1314);
        Producto p = productoBase(
                BigDecimal.valueOf(10000),
                9,
                BigDecimal.valueOf(1000),
                hace1314Dias);

        BigDecimal resultado = p.getValorDepreciadoEn(LocalDate.now());

        assertNotNull(resultado);
        double val = resultado.doubleValue();
        assertTrue(val >= 6395 && val <= 6405,
                "Se esperaba ~6400 (±5) pero fue: " + resultado);
    }

    /**
     * precioCompra=10000, vidaUtil=10, fechaAdq=hace 1826 días (≈5 años).
     * getPorcentajeDepreciado() debe estar entre 45 y 55 %.
     */
    @Test
    void getPorcentajeDepreciado_mitadVida_cercano50() {
        LocalDate hace5Anios = LocalDate.now().minusDays(1826);
        Producto p = productoBase(
                BigDecimal.valueOf(10000),
                10,
                BigDecimal.ZERO,
                hace5Anios);

        Integer porcentaje = p.getPorcentajeDepreciado();

        assertNotNull(porcentaje);
        assertTrue(porcentaje >= 45 && porcentaje <= 55,
                "Se esperaba entre 45 y 55 % pero fue: " + porcentaje);
    }

    /** Datos incompletos → getPorcentajeDepreciado() devuelve null */
    @Test
    void getPorcentajeDepreciado_sinDatos_devuelveNull() {
        Producto p = new Producto();
        // precioCompra, fechaAdquisicion y vidaUtilAnios no establecidos

        assertNull(p.getPorcentajeDepreciado());
    }
}
