package com.sibim.controller;

import com.sibim.model.enums.EstadoProducto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProductosChipsManager.parseEstado(), extracted from
 * ProductosController during the chips/filter refactor.
 */
class ProductosChipsManagerTest {

    @Test
    void parseEstado_null_returnsNull() {
        assertNull(ProductosChipsManager.parseEstado(null));
    }

    @Test
    void parseEstado_todos_returnsNull() {
        assertNull(ProductosChipsManager.parseEstado("Todos"));
    }

    @Test
    void parseEstado_todos_isCaseInsensitive() {
        assertNull(ProductosChipsManager.parseEstado("todos"));
        assertNull(ProductosChipsManager.parseEstado("TODOS"));
    }

    @Test
    void parseEstado_matchesEachEtiqueta() {
        for (EstadoProducto estado : EstadoProducto.values()) {
            assertEquals(estado, ProductosChipsManager.parseEstado(estado.getEtiqueta()));
        }
    }

    @Test
    void parseEstado_isCaseInsensitive() {
        assertEquals(EstadoProducto.AGOTADO, ProductosChipsManager.parseEstado("agotado"));
        assertEquals(EstadoProducto.BAJO_STOCK, ProductosChipsManager.parseEstado("bajo stock"));
        assertEquals(EstadoProducto.VENCIDO, ProductosChipsManager.parseEstado("VENCIDO"));
    }

    @Test
    void parseEstado_unknownLabel_returnsNull() {
        assertNull(ProductosChipsManager.parseEstado("No existe"));
    }

    @Test
    void parseEstado_blank_returnsNull() {
        assertNull(ProductosChipsManager.parseEstado(""));
    }
}
