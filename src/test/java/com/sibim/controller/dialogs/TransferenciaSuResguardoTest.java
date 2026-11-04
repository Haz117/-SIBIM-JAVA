package com.sibim.controller.dialogs;

import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Transferring to someone who already holds a resguardo in that área offers adding
 *  to it: of that person's in force in that área (written the same but for case,
 *  accents, a title or the order of the words), the one with the most bienes. */
class TransferenciaSuResguardoTest {

    private static Resguardo r(String numero, String quien, String area, String estado, int dia, int bienes) {
        Resguardo r = new Resguardo();
        r.setId(numero); r.setNumero(numero); r.setResguardanteNombre(quien); r.setResguardanteArea(area);
        r.setEstado(estado); r.setCreadoEn(LocalDateTime.of(2026, 11, dia, 10, 0));
        List<ResguardoItem> items = new ArrayList<>();
        for (int i = 0; i < bienes; i++) items.add(new ResguardoItem());
        r.setItems(items);
        return r;
    }

    private static String suyo(List<Resguardo> vigentes, String persona, String area) {
        return TransferenciaArrastreDialog.suyoVigente(vigentes, persona, area).map(Resguardo::getNumero).orElse(null);
    }

    @Test
    void elVigenteDeEsaPersonaEnEsaArea() {
        List<Resguardo> vigentes = List.of(
            r("RSG-1", "TANIA EJEMPLO FICTICIO", "Sistema DIF Municipal", Resguardo.ESTADO_ACTIVO, 1, 1),
            r("RSG-2", "Tania Ejemplo Fictício", "Sistema DIF Municipal", Resguardo.ESTADO_ACTIVO, 2, 1),
            r("RSG-3", "TANIA EJEMPLO FICTICIO", "Sistema DIF Municipal", Resguardo.ESTADO_CANCELADO, 3, 9),
            r("RSG-4", "TANIA EJEMPLO FICTICIO", "Contraloría Municipal", Resguardo.ESTADO_ACTIVO, 4, 1));

        assertEquals("RSG-2", suyo(vigentes, " tania ejemplo ficticio ", "Sistema DIF Municipal"),
            "de iguales, el más nuevo vigente, escrito igual sin importar mayúsculas ni acentos");
        assertEquals("RSG-4", suyo(vigentes, "TANIA EJEMPLO FICTICIO", "Contraloría Municipal"));
        assertNull(suyo(vigentes, "TANIA EJEMPLO FICTICIO", "Parque Municipal"));
        assertNull(suyo(vigentes, "OTRA PERSONA DISTINTA", "Sistema DIF Municipal"));
        assertNull(suyo(vigentes, " ", "Sistema DIF Municipal"));
        assertNull(suyo(vigentes, "LIC.", "Sistema DIF Municipal"), "solo un título no es nadie");
    }

    /** 3-nov-2026: four bienes transferred one at a time to a person who had a resguardo of
     *  31 there; each went into one more of one bien, the last one under "LIC." instead of "L.D.". */
    @Test
    void elDeMasBienes_yConOtroTituloEsLaMismaPersona() {
        List<Resguardo> vigentes = List.of(
            r("RSG-136", "L.D. ANA EJEMPLO FICTICIO", "Contraloría Municipal", Resguardo.ESTADO_ACTIVO, 1, 31),
            r("RSG-139", "L.D. ANA EJEMPLO FICTICIO", "Contraloría Municipal", Resguardo.ESTADO_ACTIVO, 2, 3),
            r("RSG-350", "L.D. ANA EJEMPLO FICTICIO", "Contraloría Municipal", Resguardo.ESTADO_ACTIVO, 3, 1),
            r("RSG-351", "L.D. ANA EJEMPLO FICTICIO", "Parque Municipal", Resguardo.ESTADO_ACTIVO, 4, 40));

        assertEquals("RSG-136", suyo(vigentes, "L.D. ANA EJEMPLO FICTICIO", "Contraloría Municipal"),
            "el de más bienes, no el último de uno solo");
        assertEquals("RSG-136", suyo(vigentes, "LIC. ANA EJEMPLO FICTICIO", "Contraloría Municipal"),
            "con otro título es ella: no empieza otra grafía");
        assertEquals("RSG-136", suyo(vigentes, "EJEMPLO FICTICIO ANA", "Contraloría Municipal"));
        assertEquals(3, TransferenciaArrastreDialog.suyosVigentes(vigentes, "LIC.ANA EJEMPLO FICTICIO", "Contraloría Municipal").size());
        assertNull(suyo(vigentes, "L.D. ANA EJEMPLO", "Contraloría Municipal"), "con una palabra menos es otra persona");
    }
}
