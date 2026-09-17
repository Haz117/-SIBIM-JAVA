package com.sibim.repository;

import com.sibim.model.enums.EstadoProducto;
import java.time.LocalDate;

/**
 * Encapsula todos los parámetros de búsqueda/paginación del inventario.
 * Reemplaza las firmas de 10+ parámetros en ProductoRepository y ProductoService.
 */
public record ProductoFiltro(
        String busqueda,
        String categoriaId,
        String area,
        String resguardante,
        EstadoProducto estado,
        boolean incluirBaja,
        boolean soloSinEtiquetar,
        LocalDate desdeReg,
        LocalDate hastaReg
) {
    public static ProductoFiltro vacio() {
        return new ProductoFiltro(null, null, null, null, null, false, false, null, null);
    }

    public ProductoFiltro conBusqueda(String b)           { return new ProductoFiltro(b, categoriaId, area, resguardante, estado, incluirBaja, soloSinEtiquetar, desdeReg, hastaReg); }
    public ProductoFiltro conCategoria(String c)          { return new ProductoFiltro(busqueda, c, area, resguardante, estado, incluirBaja, soloSinEtiquetar, desdeReg, hastaReg); }
    public ProductoFiltro conArea(String a)               { return new ProductoFiltro(busqueda, categoriaId, a, resguardante, estado, incluirBaja, soloSinEtiquetar, desdeReg, hastaReg); }
    public ProductoFiltro conResguardante(String r)       { return new ProductoFiltro(busqueda, categoriaId, area, r, estado, incluirBaja, soloSinEtiquetar, desdeReg, hastaReg); }
    public ProductoFiltro conEstado(EstadoProducto e)     { return new ProductoFiltro(busqueda, categoriaId, area, resguardante, e, incluirBaja, soloSinEtiquetar, desdeReg, hastaReg); }
    public ProductoFiltro conRango(LocalDate d, LocalDate h) { return new ProductoFiltro(busqueda, categoriaId, area, resguardante, estado, incluirBaja, soloSinEtiquetar, d, h); }
    public ProductoFiltro conSinEtiquetar(boolean v)      { return new ProductoFiltro(busqueda, categoriaId, area, resguardante, estado, incluirBaja, v, desdeReg, hastaReg); }
}
