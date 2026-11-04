package com.sibim.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bien in a renglón of the printed resguardo: the name loaded from the Excel files
 *  is the description cut with "…", and printing both said everything twice. */
class ReporteResguardoConceptoTest {

    @Test
    void conUnNombreCortado_saleSoloLaDescripcionCompleta() {
        assertEquals("INVENTARIO ANTERIOR: PRU-045. LAMPARA DE ESCRITORIO DE PRUEBA MODELO X100 CON FOCO",
            ReporteResguardoService.concepto("LAMPARA DE ESCRITORIO DE…",
                "Inventario anterior: PRU-045. Lampara de escritorio de prueba modelo X100 con foco"));
        assertEquals("SILLA TUBULAR TAPIZADA", ReporteResguardoService.concepto("Silla tubular...", "Silla tubular tapizada"));
    }

    @Test
    void conUnaDescripcionQueDiceOtraCosa_salenLasDos() {
        assertEquals("ESCRITORIO - CON TRES CAJONES", ReporteResguardoService.concepto("Escritorio", "Con tres cajones"));
    }

    @Test
    void sinDescripcion_soloElNombre() {
        assertEquals("ESCRITORIO", ReporteResguardoService.concepto("Escritorio", null));
        assertEquals("ESCRITORIO", ReporteResguardoService.concepto("Escritorio", "  "));
    }

    @Test
    void unaDescripcionIgualAlNombre_noSeRepite() {
        assertEquals("ESCRITORIO", ReporteResguardoService.concepto("Escritorio", "ESCRITORIO"));
    }
}
