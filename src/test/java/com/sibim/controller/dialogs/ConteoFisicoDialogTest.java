package com.sibim.controller.dialogs;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The parts of the conteo físico that don't need a window. */
class ConteoFisicoDialogTest {

    /** A bien nobody marked as seen is not recorded as found. */
    @Test
    void bienNoVisto_seGuardaComoSinRevisar() {
        assertEquals("ENCONTRADO", ConteoFisicoDialog.estadoGuardado(true, "ENCONTRADO"));
        assertEquals("FALTANTE", ConteoFisicoDialog.estadoGuardado(true, "FALTANTE"));
        assertEquals(ConteoFisicoDialog.SIN_REVISAR, ConteoFisicoDialog.estadoGuardado(false, "ENCONTRADO"));
    }

    /** The draft keeps count, estado, note and whether the bien was seen —
     *  for as many bienes as the conteo has (it lived in a registry value of
     *  8 192 characters, which about a hundred bienes filled). */
    @Test
    void borrador_idaYVuelta_conMuchosBienes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            if (i > 0) sb.append(';');
            sb.append(ConteoFisicoDialog.serializar("bien-" + i, i % 3, i % 2 == 0 ? "ENCONTRADO" : "MAL_ESTADO",
                i == 7 ? "Pata rota; falta | tornillo = 2" : "", i % 5 == 0));
        }
        assertTrue(sb.length() > 8192);

        var leido = ConteoFisicoDialog.parsearBorrador(sb.toString());
        assertEquals(3000, leido.size());
        assertEquals("Pata rota; falta | tornillo = 2", leido.get("bien-7").nota());
        assertEquals(1, leido.get("bien-7").contado());
        assertEquals(ConteoFisicoDialog.EstadoConteo.MAL_ESTADO, leido.get("bien-7").estado());
        assertTrue(leido.get("bien-5").verificado());
        assertFalse(leido.get("bien-7").verificado());
    }

    /** A draft saved by an earlier version (no "seen" mark) still opens. */
    @Test
    void borradorDeUnaVersionAnterior_seSigueLeyendo() {
        Map<String, ?> leido = ConteoFisicoDialog.parsearBorrador("a=2|FALTANTE|;b=1|ENCONTRADO|");
        assertEquals(2, leido.size());
        assertTrue(ConteoFisicoDialog.parsearBorrador("").isEmpty());
        assertTrue(ConteoFisicoDialog.parsearBorrador(null).isEmpty());
    }
}
