package com.sibim.controller;

import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.ProductoFiltro;

import java.time.LocalDate;

/**
 * Snapshot inmutable del estado de filtros en la pantalla de Bienes.
 * Capturado en el hilo FX antes de lanzar una Task en background.
 * Permite testar la lógica de filtros sin dependencia de JavaFX.
 */
record ProductosFilterState(
        String busqueda,
        String catId,
        String area,
        String resguardante,
        EstadoProducto estado,
        boolean soloSinEtiquetar,
        LocalDate desdeReg,
        LocalDate hastaReg,
        int currentPage,
        int pageSize
) {
    /** Offset de paginación calculado a partir de página actual y tamaño. */
    int offset() {
        return currentPage * pageSize;
    }

    /** True si hay al menos un filtro activo (usado para estado vacío). */
    boolean hasActiveFilters() {
        return (busqueda != null && !busqueda.isBlank())
                || catId != null
                || area != null
                || resguardante != null
                || estado != null
                || soloSinEtiquetar
                || desdeReg != null
                || hastaReg != null;
    }

    /** Converts this snapshot to a {@link ProductoFiltro} for repository queries. */
    ProductoFiltro toFiltro() {
        return new ProductoFiltro(busqueda, catId, area, resguardante, estado,
                false, soloSinEtiquetar, desdeReg, hastaReg);
    }
}
