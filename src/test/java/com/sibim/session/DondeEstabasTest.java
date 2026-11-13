package com.sibim.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** After a session closed for inactivity, the same person goes back to where they were, once. */
class DondeEstabasTest {

    @Test
    void laMismaCuenta_regresaUnaSolaVez_conSuBusqueda() {
        DondeEstabas.recordar("u-1", "productos", "  silla ");
        var lugar = DondeEstabas.tomar("u-1").orElseThrow();
        assertEquals("productos", lugar.vista());
        assertEquals("silla", lugar.busqueda());
        assertTrue(DondeEstabas.tomar("u-1").isEmpty(), "solo una vez");
    }

    @Test
    void otraCuenta_noLlegaAlLugarDeOtro_ySeOlvida() {
        DondeEstabas.recordar("u-1", "resguardos", null);
        assertTrue(DondeEstabas.tomar("u-2").isEmpty());
        assertTrue(DondeEstabas.tomar("u-1").isEmpty(), "quien entró después borra el lugar");
    }

    @Test
    void sinPantalla_oSinCuenta_noSeRecuerdaNada() {
        DondeEstabas.recordar("u-1", "", "x");
        assertTrue(DondeEstabas.tomar("u-1").isEmpty());
        DondeEstabas.recordar(null, "productos", "x");
        assertTrue(DondeEstabas.tomar(null).isEmpty());
    }
}
