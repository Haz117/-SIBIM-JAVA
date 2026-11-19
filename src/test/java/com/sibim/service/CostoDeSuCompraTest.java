package com.sibim.service;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: bienes of one purchase with the same name and different costos — 7 without the
 *  IVA their twins have, others with a digit typed wrong, and centavos that are only rounding. */
class CostoDeSuCompraTest {

    private static Producto bien(String codigo, String nombre, String folio, String costo) {
        Producto p = new Producto();
        p.setId(codigo); p.setCodigo(codigo); p.setNombre(nombre); p.setFolioFiscal(folio);
        p.setPrecioCompra(new BigDecimal(costo));
        return p;
    }

    private static final String F1 = "A0000000-0000-4000-8000-000000000001", F2 = "A0000000-0000-4000-8000-000000000002",
        F3 = "A0000000-0000-4000-8000-000000000003";

    @Test
    void sinIva_conElNumeroMalTecleado_yLosCentavosNoCuentan() {
        List<Producto> todos = List.of(
            bien("PRUE/01", "ANAQUEL DE 5 REPISAS", F1, "3180.69"), bien("PRUE/02", "anaquel de 5 repisas", F1, "3180.69"),
            bien("PRUE/03", "ANAQUEL DE 5 REPISAS", F1, "3689.60"),
            // Another name in the same factura: its own costo.
            bien("PRUE/04", "SILLA DE VISITA", F1, "481.40"),
            bien("PRUE/05", "MESA DE TRABAJO", F2, "12905.00"), bien("PRUE/06", "MESA DE TRABAJO", F2, "12905.00"),
            bien("PRUE/07", "MESA DE TRABAJO", F2, "1905.00"),
            bien("PRUE/08", "SILLA", F3, "676.76"), bien("PRUE/09", "SILLA", F3, "676.77"));

        List<CostoDeSuCompra.Grupo> grupos = CostoDeSuCompra.grupos(todos);

        assertEquals(2, grupos.size(), "los centavos son redondeo: " + grupos);
        CostoDeSuCompra.Grupo iva = grupos.get(0);
        assertTrue(iva.faltaIva(), "primero los que no tienen el IVA");
        assertEquals(0, new BigDecimal("3689.60").compareTo(iva.propuesto()));
        assertEquals(List.of("PRUE/01", "PRUE/02"), iva.cambian(iva.propuesto()).stream().map(Producto::getCodigo).toList());
        assertEquals("2 × $3,180.69 · 1 × $3,689.60", iva.resumen());

        CostoDeSuCompra.Grupo mesa = grupos.get(1);
        assertFalse(mesa.faltaIva());
        assertEquals(0, new BigDecimal("12905.00").compareTo(mesa.propuesto()), "el que tienen más");
        assertEquals(3, CostoDeSuCompra.cuantos(grupos));

        var renglones = CostoDeSuCompra.renglones(Map.of(mesa, new BigDecimal("12905.00")));
        assertEquals(1, renglones.size());
        assertEquals("PRUE/07", renglones.get(0).bien().getCodigo());
        assertEquals(0, new BigDecimal("12905.00").compareTo(renglones.get(0).nuevo().getPrecioCompra()));
        assertEquals(List.of(EditarVarios.Dato.COSTO), renglones.get(0).cambian());
    }

    @Test
    void otraCompraOSinCosto_noSeJuntan() {
        List<Producto> todos = List.of(
            bien("PRUE/11", "LAPTOP", F1, "15000.00"), bien("PRUE/12", "LAPTOP", F2, "17400.00"),
            bien("PRUE/13", "LAPTOP", F3, "0"), bien("PRUE/14", "LAPTOP", F3, "15000.00"));
        assertTrue(CostoDeSuCompra.grupos(todos).isEmpty());
    }
}
