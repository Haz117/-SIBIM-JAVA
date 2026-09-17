package com.sibim.controller;

import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.ProductoFiltro;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProductosFilterStateTest {

    @Test
    void offset_calculaCorrectamente() {
        ProductosFilterState s = state(2, 25);
        assertEquals(50, s.offset());
    }

    @Test
    void offset_primeraPagina_esZero() {
        assertEquals(0, state(0, 10).offset());
    }

    @Test
    void hasActiveFilters_vacio_false() {
        ProductosFilterState s = new ProductosFilterState(
                null, null, null, null, null, false, null, null, 0, 20);
        assertFalse(s.hasActiveFilters());
    }

    @Test
    void hasActiveFilters_busquedaVacia_false() {
        ProductosFilterState s = new ProductosFilterState(
                "   ", null, null, null, null, false, null, null, 0, 20);
        assertFalse(s.hasActiveFilters());
    }

    @Test
    void hasActiveFilters_conBusqueda_true() {
        ProductosFilterState s = new ProductosFilterState(
                "laptop", null, null, null, null, false, null, null, 0, 20);
        assertTrue(s.hasActiveFilters());
    }

    @Test
    void hasActiveFilters_conEstado_true() {
        ProductosFilterState s = new ProductosFilterState(
                null, null, null, null, EstadoProducto.ACTIVO, false, null, null, 0, 20);
        assertTrue(s.hasActiveFilters());
    }

    @Test
    void hasActiveFilters_soloSinEtiquetar_true() {
        ProductosFilterState s = new ProductosFilterState(
                null, null, null, null, null, true, null, null, 0, 20);
        assertTrue(s.hasActiveFilters());
    }

    @Test
    void hasActiveFilters_conFechaDesde_true() {
        ProductosFilterState s = new ProductosFilterState(
                null, null, null, null, null, false, LocalDate.of(2025, 1, 1), null, 0, 20);
        assertTrue(s.hasActiveFilters());
    }

    @Test
    void toFiltro_mapeaTodosLosCampos() {
        LocalDate desde = LocalDate.of(2025, 3, 1);
        LocalDate hasta = LocalDate.of(2025, 9, 30);
        ProductosFilterState s = new ProductosFilterState(
                "silla", "cat1", "TICS", "Juan", EstadoProducto.ACTIVO, true, desde, hasta, 1, 10);

        ProductoFiltro f = s.toFiltro();

        assertEquals("silla", f.busqueda());
        assertEquals("cat1", f.categoriaId());
        assertEquals("TICS", f.area());
        assertEquals("Juan", f.resguardante());
        assertEquals(EstadoProducto.ACTIVO, f.estado());
        assertTrue(f.soloSinEtiquetar());
        assertFalse(f.incluirBaja());
        assertEquals(desde, f.desdeReg());
        assertEquals(hasta, f.hastaReg());
    }

    @Test
    void toFiltro_camposNulos_sePropagan() {
        ProductosFilterState s = new ProductosFilterState(
                null, null, null, null, null, false, null, null, 0, 20);
        ProductoFiltro f = s.toFiltro();
        assertNull(f.busqueda());
        assertNull(f.estado());
        assertFalse(f.soloSinEtiquetar());
        assertFalse(f.incluirBaja());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static ProductosFilterState state(int page, int size) {
        return new ProductosFilterState(null, null, null, null, null, false, null, null, page, size);
    }
}
