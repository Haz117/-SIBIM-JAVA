package com.sibim.service;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 18-nov-2026: the vida útil typed by hand follows the municipio's own criterion, steady per
 *  CONAC account (muebles 5 years in 75 %, cómputo 3 in 85 %), not CONAC's table. */
class VidaUtilDeSuClaveTest {

    private int n;

    private List<Producto> varios(int cuantos, String clave, Integer anios) {
        List<Producto> out = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            Producto p = new Producto();
            p.setId("p" + (++n)); p.setCodigo("PRU/" + n); p.setNombre("BIEN");
            p.setClaveArmonizada(clave); p.setVidaUtilAnios(anios);
            out.add(p);
        }
        return out;
    }

    @Test
    void laClaveCorta_sonSusCincoPrimerasPartes() {
        assertEquals("1.2.4.1.1", CalidadDatosService.claveCorta(" 1.2.4.1.1.511 "));
        assertEquals("1.2.4.8", CalidadDatosService.claveCorta("1.2.4.8"));
        assertNull(CalidadDatosService.claveCorta(" "));
    }

    @Test
    void laMayoriaDeSuClave_conDiezOMasYSieteDeCadaDiez() {
        List<Producto> bienes = new ArrayList<>(varios(15, "1.2.4.1.1.511", 5));
        bienes.addAll(varios(4, "1.2.4.1.1.512", 10));
        bienes.addAll(varios(9, "1.2.4.1.3.515", 3));          // only 9: not enough
        bienes.addAll(varios(6, "1.2.4.1.9.519", 3));
        bienes.addAll(varios(6, "1.2.4.1.9.519", 10));         // split: no majority
        bienes.addAll(varios(20, "1.2.4.4.1.541", null));      // without vida útil: nothing to learn
        Producto deBaja = varios(1, "1.2.4.1.3.515", 3).get(0);
        deBaja.setFechaBaja(java.time.LocalDate.of(2026, 10, 1));
        bienes.add(deBaja);

        assertEquals(Map.of("1.2.4.1.1", 5), CalidadDatosService.vidaUtilDeSuClave(bienes),
            "15 de 19 dicen 5 años en muebles; los demás no llegan");
    }

    /** 18-nov-2026, Patrimonio: «Completar datos» proposes her criterion, CONAC only where there
     *  is no majority. */
    @Test
    void laPropuestaDeCompletarDatos_sigueLaMayoriaDeSuClave_yCONACDondeNoHay() {
        Producto mueble = varios(1, "1.2.4.1.1.511", null).get(0);
        Producto computo = varios(1, "1.2.4.1.3.515", null).get(0);
        Producto yaTiene = varios(1, "1.2.4.1.1.511", 10).get(0);
        var p = CalidadDatosService.propuestaVidaUtil(List.of(mueble, computo, yaTiene), Map.of("1.2.4.1.1", 5));
        assertEquals(List.of(mueble), p.get(5), "muebles: la mayoría de su clave, no los 10 de CONAC");
        assertEquals(List.of(computo), p.get(3), "cómputo sin mayoría: la tabla CONAC");
        assertNull(p.get(10));
    }
}
