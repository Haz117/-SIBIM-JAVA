package com.sibim.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The CONAC parameters for the categories production has (28-oct-2026). */
class VidaUtilConacTest {

    @Test
    void lasCuentasDeProduccion() {
        assertEquals(10, VidaUtilConac.anios("1.2.4.1.1.511"));   // muebles de oficina
        assertEquals(3,  VidaUtilConac.anios("1.2.4.1.3.515"));   // cómputo
        assertEquals(10, VidaUtilConac.anios("1.2.4.1.9.519"));   // otros mobiliarios (frigobar)
        assertEquals(3,  VidaUtilConac.anios("1.2.4.2.1.521"));   // audiovisuales
        assertEquals(5,  VidaUtilConac.anios("1.2.4.2.2.522"));   // deportivos
        assertEquals(3,  VidaUtilConac.anios("1.2.4.2.3.523"));   // cámaras
        assertEquals(5,  VidaUtilConac.anios("1.2.4.2.9.529"));
        assertEquals(5,  VidaUtilConac.anios("1.2.4.3.1.531"));   // médico
        assertEquals(5,  VidaUtilConac.anios("1.2.4.4.1.541"));   // vehículos
        assertEquals(5,  VidaUtilConac.anios("1.2.4.4.9.549"));
        assertEquals(10, VidaUtilConac.anios("1.2.4.6.3.563"));   // maquinaria de construcción
        assertEquals(10, VidaUtilConac.anios("1.2.4.6.7.567"));
    }

    @Test
    void sinValorUnico_noSugiereNada() {
        assertNull(VidaUtilConac.anios("1.2.4.5.1.551"), "defensa y seguridad la fija cada ente");
        assertNull(VidaUtilConac.anios(null));
        assertNull(VidaUtilConac.anios("1.2.4.10.1"), "1.2.4.1 no es prefijo de 1.2.4.10");
    }
}
