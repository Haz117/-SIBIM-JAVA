package com.sibim.repository;

import com.sibim.model.enums.EstadoProducto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProductoFiltroTest {

    @Test
    void vacio_todosLosCamposNulos() {
        ProductoFiltro f = ProductoFiltro.vacio();
        assertNull(f.busqueda());
        assertNull(f.categoriaId());
        assertNull(f.area());
        assertNull(f.resguardante());
        assertNull(f.estado());
        assertFalse(f.incluirBaja());
        assertFalse(f.soloSinEtiquetar());
        assertNull(f.desdeReg());
        assertNull(f.hastaReg());
    }

    @Test
    void conBusqueda_soloModificaBusqueda() {
        ProductoFiltro base = new ProductoFiltro(null, "cat1", "TI", null, EstadoProducto.ACTIVO, false, false, null, null);
        ProductoFiltro result = base.conBusqueda("laptop");
        assertEquals("laptop", result.busqueda());
        assertEquals("cat1", result.categoriaId());
        assertEquals("TI", result.area());
        assertEquals(EstadoProducto.ACTIVO, result.estado());
    }

    @Test
    void conEstado_soloModificaEstado() {
        ProductoFiltro base = ProductoFiltro.vacio().conArea("PRES");
        ProductoFiltro result = base.conEstado(EstadoProducto.AGOTADO);
        assertEquals(EstadoProducto.AGOTADO, result.estado());
        assertEquals("PRES", result.area());
        assertNull(result.busqueda());
    }

    @Test
    void conRango_soloModificaFechas() {
        LocalDate desde = LocalDate.of(2025, 1, 1);
        LocalDate hasta = LocalDate.of(2025, 12, 31);
        ProductoFiltro base = ProductoFiltro.vacio().conBusqueda("monitor");
        ProductoFiltro result = base.conRango(desde, hasta);
        assertEquals("monitor", result.busqueda());
        assertEquals(desde, result.desdeReg());
        assertEquals(hasta, result.hastaReg());
    }

    @Test
    void conSinEtiquetar_soloModificaBandera() {
        ProductoFiltro base = ProductoFiltro.vacio().conArea("TICS");
        ProductoFiltro result = base.conSinEtiquetar(true);
        assertTrue(result.soloSinEtiquetar());
        assertEquals("TICS", result.area());
    }

    @Test
    void encadenamiento_buildsFiltroCorrectamente() {
        LocalDate desde = LocalDate.of(2024, 6, 1);
        ProductoFiltro f = ProductoFiltro.vacio()
            .conBusqueda("impresora")
            .conArea("PRES")
            .conEstado(EstadoProducto.BAJO_STOCK)
            .conRango(desde, null);

        assertEquals("impresora", f.busqueda());
        assertEquals("PRES", f.area());
        assertEquals(EstadoProducto.BAJO_STOCK, f.estado());
        assertEquals(desde, f.desdeReg());
        assertNull(f.hastaReg());
        assertFalse(f.incluirBaja());
        assertFalse(f.soloSinEtiquetar());
    }

    @Test
    void igualdad_mismosFiltrosIguales() {
        ProductoFiltro a = new ProductoFiltro("laptop", "c1", "TI", null, EstadoProducto.ACTIVO, false, false, null, null);
        ProductoFiltro b = new ProductoFiltro("laptop", "c1", "TI", null, EstadoProducto.ACTIVO, false, false, null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void igualdad_diferenteEstadoNoIgual() {
        ProductoFiltro a = ProductoFiltro.vacio().conEstado(EstadoProducto.ACTIVO);
        ProductoFiltro b = ProductoFiltro.vacio().conEstado(EstadoProducto.AGOTADO);
        assertNotEquals(a, b);
    }
}
