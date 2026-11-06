package com.sibim.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** How the width is shared so that every title is whole and every column in view. */
class ColumnasLegiblesTest {

    private static ColumnasLegibles.Columna col(double original, double titulo) {
        return col(original, original, titulo);
    }

    private static ColumnasLegibles.Columna col(double original, double pref, double titulo) {
        return new ColumnasLegibles.Columna(null, original, pref, Double.MAX_VALUE, titulo);
    }

    private static double[] minimos(List<ColumnasLegibles.Columna> cols, double disponible) {
        double metas = 0, titulos = 0;
        for (var c : cols) { metas += c.meta(); titulos += Math.min(c.titulo(), c.meta()); }
        return ColumnasLegibles.minimos(cols, disponible, metas, titulos);
    }

    private static double suma(double[] xs) {
        double s = 0;
        for (double x : xs) s += x;
        return s;
    }

    @Test
    void conEspacio_cadaColumnaTieneSuTituloYLoQuePidioLaPantalla() {
        double[] m = minimos(List.of(col(160, 80), col(60, 95)), 1000);
        assertEquals(160, m[0], 0.01, "Nombre conserva su mínimo");
        assertEquals(95, m[1], 0.01, "«Cantidad» ya no se corta");
    }

    @Test
    void sinEspacio_lasDeTextoCedenLoQueSobraDeSuTitulo_yNadaSeSaleDeLaTabla() {
        // Bienes at 1366: the free-text columns asked for 160 each; the short ones need their titles.
        var cols = List.of(col(160, 60), col(160, 60), col(60, 95), col(60, 90));
        double[] m = minimos(cols, 400);
        assertEquals(400, suma(m), 0.5, "cabe justo en la tabla");
        assertEquals(95, m[2], 0.01, "el título corto entero");
        assertEquals(90, m[3], 0.01);
        assertTrue(m[0] >= 60 && m[0] < 160, "la de texto cede, sin bajar de su título: " + m[0]);
    }

    @Test
    void niLosTitulosCaben_laTablaQuedaComoLaEscribioLaPantalla() {
        double[] m = minimos(List.of(col(100, 300), col(100, 300)), 400);
        assertArrayEquals(new double[]{100, 100}, m, 0.01);
    }

    @Test
    void losAnchosLlenanLaTablaExacta_sinBajarDelMinimo() {
        var cols = List.of(col(60, 220, 60), col(90, 90, 90), col(60, 170, 100));
        double[] mins = {60, 90, 100};
        double[] w = ColumnasLegibles.anchos(cols, mins, 600);
        assertEquals(600, suma(w), 0.5, "ni falta ni sobra: la última columna queda a la vista");
        for (int i = 0; i < w.length; i++) assertTrue(w[i] >= mins[i], "columna " + i + ": " + w[i]);
        assertTrue(w[0] > w[1], "la que pidió más ancho (Nombre) se lleva más: " + w[0]);
    }
}
