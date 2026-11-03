package com.sibim.controller.dialogs;

import com.sibim.model.Resguardo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Transferring to someone who already holds a resguardo in that área offers adding
 *  to it: the newest one in force of that person (written the same, any case or
 *  accent), in that área only. */
class TransferenciaSuResguardoTest {

    private static Resguardo r(String numero, String quien, String area, String estado, int dia) {
        Resguardo r = new Resguardo();
        r.setId(numero); r.setNumero(numero); r.setResguardanteNombre(quien); r.setResguardanteArea(area);
        r.setEstado(estado); r.setCreadoEn(LocalDateTime.of(2026, 11, dia, 10, 0));
        return r;
    }

    @Test
    void elMasNuevoVigenteDeEsaPersonaEnEsaArea() {
        List<Resguardo> vigentes = List.of(
            r("RSG-1", "TANIA EJEMPLO FICTICIO", "Sistema DIF Municipal", Resguardo.ESTADO_ACTIVO, 1),
            r("RSG-2", "Tania Ejemplo Fictício", "Sistema DIF Municipal", Resguardo.ESTADO_ACTIVO, 2),
            r("RSG-3", "TANIA EJEMPLO FICTICIO", "Sistema DIF Municipal", Resguardo.ESTADO_CANCELADO, 3),
            r("RSG-4", "TANIA EJEMPLO FICTICIO", "Contraloría Municipal", Resguardo.ESTADO_ACTIVO, 4));

        assertEquals("RSG-2", TransferenciaArrastreDialog.suyoVigente(vigentes, " tania ejemplo ficticio ", "Sistema DIF Municipal")
            .orElseThrow().getNumero(), "el más nuevo vigente, escrito igual sin importar mayúsculas ni acentos");
        assertEquals("RSG-4", TransferenciaArrastreDialog.suyoVigente(vigentes, "TANIA EJEMPLO FICTICIO", "Contraloría Municipal")
            .orElseThrow().getNumero());
        assertTrue(TransferenciaArrastreDialog.suyoVigente(vigentes, "TANIA EJEMPLO FICTICIO", "Parque Municipal").isEmpty());
        assertTrue(TransferenciaArrastreDialog.suyoVigente(vigentes, "OTRA PERSONA DISTINTA", "Sistema DIF Municipal").isEmpty());
        assertTrue(TransferenciaArrastreDialog.suyoVigente(vigentes, " ", "Sistema DIF Municipal").isEmpty());
    }
}
