package com.sibim.util;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 18-nov-2026: three category icons in Categorías were 2.15:1 to 2.43:1 on white. */
class AppColorsLegibleTest {

    @Test
    void unColorClaro_seOscureceHasta3a1_conSuTono() {
        for (String claro : new String[]{"#F59E0B", "#06B6D4", "#10B981"}) {
            String legible = AppColors.legibleSobreBlanco(claro);
            assertNotEquals(claro, legible);
            assertTrue(AppColors.contrasteConBlanco(Color.web(legible)) >= 3.3, claro + " → " + legible);
            assertEquals(Color.web(claro).getHue(), Color.web(legible).getHue(), 3, "el mismo tono");
        }
    }

    @Test
    void unColorQueYaSeLee_seQuedaComoEstaba() {
        assertEquals("#4338CA", AppColors.legibleSobreBlanco("#4338CA"));
        assertEquals("#9F2241", AppColors.legibleSobreBlanco("#9F2241"));
        assertEquals("rojo raro", AppColors.legibleSobreBlanco("rojo raro"));
    }
}
