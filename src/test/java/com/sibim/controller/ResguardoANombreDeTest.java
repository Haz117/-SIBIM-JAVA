package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Nuevo resguardo › "Cargar los que tiene a su nombre": 90 of 316 people held
 *  bienes without a registered resguardo (28-oct-2026). It brings the bienes the
 *  inventory has in that name, however it was typed, except those already in a
 *  resguardo in force (saving would refuse them) and those dados de baja. */
class ResguardoANombreDeTest {

    private static Producto bien(String id, String codigo, String resguardante) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Bien " + codigo); p.setResguardante(resguardante);
        return p;
    }

    @Test
    void traeLosSuyosSinResguardoVigente_porNumero() {
        Producto enResguardo = bien("p-1", "TES/01", "Juan Pérez");
        Producto baja = bien("p-4", "TES/04", "Juan Pérez");
        baja.setFechaBaja(java.time.LocalDate.of(2026, 10, 1));
        Resguardo vigente = new Resguardo();
        vigente.setNumero("RSG-1"); vigente.setResguardanteNombre("Juan Pérez"); vigente.setEstado(Resguardo.ESTADO_ACTIVO);
        ResguardoItem it = new ResguardoItem(); it.setProductoId("p-1");
        vigente.setItems(List.of(it));
        List<Producto> bienes = List.of(enResguardo, bien("p-10", "TES/10", "JUAN PEREZ"),
            bien("p-2", "TES/2", "juan  pérez"), baja, bien("p-3", "TES/03", "Ana Ruiz"));

        assertEquals(List.of("TES/2", "TES/10"),
            ResguardosController.aNombreDe("  Juan Perez ", bienes, List.of(vigente)).stream().map(Producto::getCodigo).toList());
        assertTrue(ResguardosController.aNombreDe("Luis Soto", bienes, List.of(vigente)).isEmpty());
        assertTrue(ResguardosController.aNombreDe(" ", bienes, List.of(vigente)).isEmpty());
    }
}
