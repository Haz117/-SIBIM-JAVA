package com.sibim.controller.dialogs;

import com.sibim.model.Resguardo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Juntar resguardos lists whoever has two or more in force in one área — written the
 *  same in any case or accent — most first, each with their resguardos newest first. */
class JuntarResguardosAgruparTest {

    private static Resguardo r(String numero, String quien, String area, String estado, int dia) {
        Resguardo r = new Resguardo();
        r.setId(numero); r.setNumero(numero); r.setResguardanteNombre(quien); r.setResguardanteArea(area);
        r.setEstado(estado); r.setCreadoEn(LocalDateTime.of(2026, 10, dia, 10, 0));
        return r;
    }

    @Test
    void quienTieneDosOMasEnUnaArea_elQueMasPrimero() {
        String rm = "Recursos Materiales y Patrimonio", ct = "Contraloría Municipal";
        var grupos = JuntarResguardosDialog.agrupar(List.of(
            r("RSG-1", "JUAN EJEMPLO CRUZ", rm, Resguardo.ESTADO_ACTIVO, 1),
            r("RSG-2", "Juan Ejemplo Crúz", rm, Resguardo.ESTADO_ACTIVO, 5),
            r("RSG-3", "JUAN EJEMPLO CRUZ", rm, Resguardo.ESTADO_ACTIVO, 3),
            r("RSG-4", "JUAN EJEMPLO CRUZ", ct, Resguardo.ESTADO_ACTIVO, 2),
            r("RSG-5", "LAURA PRUEBA SOTO", ct, Resguardo.ESTADO_ACTIVO, 1),
            r("RSG-6", "LAURA PRUEBA SOTO", ct, Resguardo.ESTADO_ACTIVO, 2),
            r("RSG-7", "LAURA PRUEBA SOTO", ct, Resguardo.ESTADO_CANCELADO, 3),
            r("RSG-8", "MARIO SOLO UNO", ct, Resguardo.ESTADO_ACTIVO, 1)));

        assertEquals(2, grupos.size(), "uno solo en el área no se lista; los cancelados no cuentan");
        assertEquals(rm, grupos.get(0).area());
        assertEquals(List.of("RSG-2", "RSG-3", "RSG-1"), grupos.get(0).resguardos().stream().map(Resguardo::getNumero).toList());
        assertEquals("Juan Ejemplo Crúz", grupos.get(0).nombre(), "como lo escribe el más nuevo");
        assertEquals(List.of("RSG-6", "RSG-5"), grupos.get(1).resguardos().stream().map(Resguardo::getNumero).toList());
    }
}
