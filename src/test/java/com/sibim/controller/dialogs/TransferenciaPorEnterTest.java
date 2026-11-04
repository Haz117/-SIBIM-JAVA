package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** In Transferir, a código or the number on the old label and Enter marks that bien,
 *  not the first one on the list that merely contains what was typed. */
class TransferenciaPorEnterTest {

    private static Producto p(String id, String codigo, String descripcion) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Silla"); p.setDescripcion(descripcion);
        return p;
    }

    @Test
    void elCodigoOElNumeroAnteriorCompleto_ganaAlPrimeroQueLoContiene() {
        List<Producto> visibles = List.of(
            p("a", "TES/12", "Inventario anterior: PRU-TES-120."),
            p("b", "TES/120", "Inventario anterior: PRU-TES-12."),
            p("c", "TES/121", "Inventario anterior: PRU-TES-12."));

        assertEquals("b", TransferenciaArrastreDialog.porEnter(visibles, Set.of(), "tes/120").getId());
        assertEquals("b", TransferenciaArrastreDialog.porEnter(visibles, Set.of(), "PRU-TES-12").getId(),
            "el número anterior completo, no PRU-TES-120");
        assertEquals("c", TransferenciaArrastreDialog.porEnter(visibles, Set.of("b"), "PRU-TES-12").getId(),
            "repetido: el siguiente que no se ha marcado");
        assertEquals("a", TransferenciaArrastreDialog.porEnter(visibles, Set.of(), "silla").getId(),
            "lo demás, el primero de la lista como antes");
    }
}
