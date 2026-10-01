package com.sibim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

/** The daily backup's password is sealed on this PC; turning it off removes it. */
class RespaldoAutomaticoTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void activarYDesactivar() throws Exception {
        RespaldoAutomatico.desactivar();
        assertFalse(RespaldoAutomatico.activado());

        RespaldoAutomatico.activar("una-clave-larga".toCharArray());
        assertTrue(RespaldoAutomatico.activado());
        assertTrue(RespaldoAutomatico.carpeta().toString().contains("respaldos"));

        RespaldoAutomatico.desactivar();
        assertFalse(RespaldoAutomatico.activado());
        assertNull(RespaldoAutomatico.ultimo(), "sin respaldos hechos todavía");
    }
}
