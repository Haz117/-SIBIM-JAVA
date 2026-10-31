package com.sibim.controller;

import com.sibim.model.Producto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Helpers of the "Resguardo general" dialog: who it proposes as titular and the
 *  order its bienes are printed in. */
class ResguardoGeneralAreaTest {

    private static Producto bien(String codigo, String resguardante) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setResguardante(resguardante);
        return p;
    }

    /** The search finds a person without accents and in any order, and answers
     *  "¿en qué resguardo está este bien?" by its código. */
    @Test
    void buscarResguardo_sinAcentos_yPorCodigoDeBien() {
        com.sibim.model.Resguardo r = new com.sibim.model.Resguardo();
        r.setResguardanteNombre("MARÍA LÓPEZ CRUZ"); r.setNumero("RSG-2026-000001"); r.setResguardanteArea("Tesorería Municipal");
        com.sibim.model.ResguardoItem silla = new com.sibim.model.ResguardoItem();
        silla.setProductoCodigo("TES/20"); silla.setProductoNombre("SILLA TUBULAR");
        r.setItems(List.of(silla));
        com.sibim.model.Resguardo otro = new com.sibim.model.Resguardo();
        otro.setResguardanteNombre("LUIS SOTO"); otro.setNumero("RSG-2026-000002");
        List<com.sibim.model.Resguardo> todos = List.of(r, otro);
        for (String q : List.of("lopez maria", "tes/20", "silla", "tesoreria", "000001"))
            assertEquals(List.of(r), com.sibim.util.DialogUtil.coincidencias(todos, ResguardosController::textoDeBusqueda, q), q);
    }

    @Test
    void titularProbable_esQuienTieneMasBienesDelArea() {
        assertEquals("LIC. JAIME ROSALES", ResguardosController.titularProbable(List.of(
            bien("SGM/01", "LIC. JAIME ROSALES"), bien("SGM/02", "ANA LÓPEZ"),
            bien("SGM/03", "LIC. JAIME ROSALES"), bien("SGM/04", null))));
        assertEquals("", ResguardosController.titularProbable(List.of(bien("SGM/01", " "))));
    }

    /** The titular typed in Configuración › Áreas wins over who holds the most bienes. */
    @Test
    void titularDe_prefiereElCapturado() {
        List<Producto> bienes = List.of(bien("SGM/01", "ANA LÓPEZ"));
        assertEquals("LIC. JAIME ROSALES", ResguardosController.titularDe("Secretaría General Municipal",
            java.util.Map.of("Secretaría General Municipal", "LIC. JAIME ROSALES"), bienes));
        assertEquals("ANA LÓPEZ", ResguardosController.titularDe("Tesorería Municipal",
            java.util.Map.of("Secretaría General Municipal", "LIC. JAIME ROSALES"), bienes));
    }

    @Test
    void numeroDeCodigo_ordenaPorElNumeroDelArea() {
        assertEquals(7, ResguardosController.numeroDeCodigo(bien("SGM/07", null)));
        assertEquals(110, ResguardosController.numeroDeCodigo(bien("SSPAO/110", null)));
        assertEquals(Integer.MAX_VALUE, ResguardosController.numeroDeCodigo(bien("IMP-0004", null)));
        assertEquals(Integer.MAX_VALUE, ResguardosController.numeroDeCodigo(bien(null, null)));
    }
}
