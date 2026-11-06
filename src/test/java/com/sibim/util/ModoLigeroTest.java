package com.sibim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** When SIBIM goes light by itself, and that the person's choice always wins. */
class ModoLigeroTest {

    @Test
    void sinAceleracionOLentaDetectada_seEnciendeSolo() {
        assertTrue(ModoLigero.decidir(null, true, false), "sin tarjeta de video");
        assertTrue(ModoLigero.decidir(null, false, true), "se vio lenta antes en esta computadora");
        assertFalse(ModoLigero.decidir(null, false, false), "una computadora normal se ve como siempre");
    }

    @Test
    void loQueEligeLaPersona_leGanaALaDeteccion() {
        assertFalse(ModoLigero.decidir(false, true, true), "lo apagó en Configuración");
        assertTrue(ModoLigero.decidir(true, false, false), "lo encendió en Configuración");
    }
}
