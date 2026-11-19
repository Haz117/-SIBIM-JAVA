package com.sibim.controller;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 18-nov-2026: Alertas › Pendientes said «2961» (every bien waiting for its área to confirm
 *  its label) and not the 256 without resguardo, which are Patrimonio's to do. */
class AlertasPendientesTest {

    private static Producto bien(String id, String area, String quien, boolean etiquetado) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo("PRU/" + id); p.setNombre("Bien " + id); p.setArea(area);
        p.setResguardante(quien); p.setEtiquetado(etiquetado); p.setStockActual(1);
        return p;
    }

    @Test
    void sinResguardoPrimero_yElNumeroEsLoDePatrimonio() {
        Producto conTodo = bien("1", "Área A", "PERSONA FICTICIA", true);
        Producto soloEtiqueta = bien("2", "Área A", "PERSONA FICTICIA", false);
        Producto sinResguardo = bien("3", "Área B", "PERSONA FICTICIA", false);
        Producto sinNadie = bien("4", "Área B", null, false);
        Producto deBaja = bien("5", "Área B", null, false);
        deBaja.setFechaBaja(LocalDate.of(2026, 10, 1));

        var p = AlertasPatrimonialesSection.pendientes(List.of(conTodo, soloEtiqueta, sinResguardo, sinNadie, deBaja), Set.of("1", "2"));

        assertEquals(3, p.size(), "el de baja no es pendiente; el completo tampoco");
        assertEquals(2, AlertasPatrimonialesSection.contar(p, AlertasPatrimonialesSection.SIN_RESGUARDO));
        assertEquals(1, AlertasPatrimonialesSection.contar(p, AlertasPatrimonialesSection.SIN_RESGUARDANTE));
        assertEquals(3, AlertasPatrimonialesSection.contar(p, AlertasPatrimonialesSection.SIN_ETIQUETAR));
        assertEquals(2, AlertasPatrimonialesSection.dePatrimonio(p), "la etiqueta la confirman las áreas");

        var porArea = AlertasPatrimonialesSection.porArea(p);
        assertEquals("Área B", porArea.get(0).area(), "primero la que tiene más sin resguardo");
        assertEquals(2, porArea.get(0).sinResguardo());
    }

    @Test
    void sinPoderLeerLosResguardos_nadieQuedaSinResguardo() {
        var p = AlertasPatrimonialesSection.pendientes(List.of(bien("1", "Área A", "PERSONA FICTICIA", true)), null);
        assertTrue(p.isEmpty());
    }
}
