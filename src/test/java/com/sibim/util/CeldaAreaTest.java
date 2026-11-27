package com.sibim.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 27-nov-2026: the subáreas of Seguridad Pública all read «Seguridad Pública ...» in Bienes. */
class CeldaAreaTest {

    @Test void unaSubareaGuardaSuNombre() {
        assertEquals(List.of("Seguridad Pública — Armamento", "S. Pública — Armamento", "Armamento"),
            CeldaArea.formas("Seguridad Pública — Armamento"));
        assertEquals(List.of("Tesorería — Cuenta Pública", "Tes. — Cuenta Pública", "Cuenta Pública"),
            CeldaArea.formas("Tesorería — Cuenta Pública"));
        assertEquals(List.of("Secretaría de Obras Públicas — Talleres", "S. O. Públicas — Talleres", "Talleres"),
            CeldaArea.formas("Secretaría de Obras Públicas — Talleres"), "sin la inicial de «de»");
    }

    @Test void unAreaSinSubareaSoloTieneSuNombre() {
        assertEquals(List.of("Comunicación Social y Marketing Digital"),
            CeldaArea.formas("Comunicación Social y Marketing Digital"));
    }
}
