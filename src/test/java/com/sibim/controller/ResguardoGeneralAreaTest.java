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

    @Test
    void titularProbable_esQuienTieneMasBienesDelArea() {
        assertEquals("LIC. JAIME ROSALES", ResguardosController.titularProbable(List.of(
            bien("SGM/01", "LIC. JAIME ROSALES"), bien("SGM/02", "ANA LÓPEZ"),
            bien("SGM/03", "LIC. JAIME ROSALES"), bien("SGM/04", null))));
        assertEquals("", ResguardosController.titularProbable(List.of(bien("SGM/01", " "))));
    }

    @Test
    void numeroDeCodigo_ordenaPorElNumeroDelArea() {
        assertEquals(7, ResguardosController.numeroDeCodigo(bien("SGM/07", null)));
        assertEquals(110, ResguardosController.numeroDeCodigo(bien("SSPAO/110", null)));
        assertEquals(Integer.MAX_VALUE, ResguardosController.numeroDeCodigo(bien("IMP-0004", null)));
        assertEquals(Integer.MAX_VALUE, ResguardosController.numeroDeCodigo(bien(null, null)));
    }
}
