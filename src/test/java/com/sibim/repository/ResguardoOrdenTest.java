package com.sibim.repository;

import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The renglones of a resguardo print by código, whatever order they were chosen or stored in. */
class ResguardoOrdenTest {

    private static ResguardoItem item(String codigo) {
        ResguardoItem it = new ResguardoItem();
        it.setProductoCodigo(codigo);
        return it;
    }

    private static List<String> codigos(Resguardo r) {
        return r.getItems().stream().map(ResguardoItem::getProductoCodigo).toList();
    }

    @Test
    void ordenar_porPrefijoYNumero_losSinCodigoAlFinal_aunqueLaListaNoSeaModificable() {
        Resguardo r = new Resguardo();
        // Unmodifiable, as a caller may pass it.
        r.setItems(java.util.stream.Stream.of(item("RCE/10"), item(null), item("MM/4"), item("RCE/2"), item("MM/26")).toList());

        ResguardoRepository.ordenar(r);

        assertEquals(Arrays.asList("MM/4", "MM/26", "RCE/2", "RCE/10", null), codigos(r));
    }

    @Test
    void ordenar_sinRenglonesOConUno_noFalla() {
        Resguardo vacio = new Resguardo();
        vacio.setItems(List.of());
        ResguardoRepository.ordenar(vacio);
        assertEquals(List.of(), codigos(vacio));

        Resguardo uno = new Resguardo();
        uno.setItems(List.of(item("MM/01")));
        ResguardoRepository.ordenar(uno);
        assertEquals(List.of("MM/01"), codigos(uno));
    }
}
