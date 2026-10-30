package com.sibim.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bien in a renglón of the printed resguardo: the name loaded from the Excel files
 *  is the description cut with "…", and printing both said everything twice. */
class ReporteResguardoConceptoTest {

    @Test
    void conUnNombreCortado_saleSoloLaDescripcionCompleta() {
        assertEquals("INVENTARIO ANTERIOR: SSP-045. CAMARA DE VIDEO BODY CAM MODELO SCW7 CON BATERIA",
            ReporteResguardoService.concepto("CAMARA DE VIDEO BODY CAM…",
                "Inventario anterior: SSP-045. Camara de video body cam modelo SCW7 con bateria"));
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
