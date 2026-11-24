package com.sibim.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The color a bien's name already says, with names of the inventory on 13-nov-2026. */
class ColorEnNombreTest {

    @Test
    void laFraseColor_conSuMatizOSegundoColor() {
        assertEquals("NEGRO", ColorEnNombre.de("SILLA DE VISITA PARA OFICINA, TAPIZ EN TELA COLOR NEGRO, ESTRUCTURA DE ACERO", null));
        assertEquals("NEGRO", ColorEnNombre.de("SILLA SECRETARIAL CON PISTON, COLOR NEGRA", null), "en masculino, como se captura");
        assertEquals("AZUL MARINO", ColorEnNombre.de("SILLA COLOR AZUL MARINO TELA", null));
        assertEquals("CAFE CLARO", ColorEnNombre.de("SILLÓN INDIVIDUAL RECLINABLE COLOR CAFÉ CLARO", null), "sin acento");
        assertEquals("VERDE LIMON", ColorEnNombre.de("SILLA PLEGADIZA DE COLOR VERDE LIMÓN", null));
        assertEquals("CAOBA / NEGRO", ColorEnNombre.de("ESCRITORIO 120X60 CM COLOR CAOBA/NEGRO", null));
        assertEquals("CAOBA / NEGRO", ColorEnNombre.de("ESCRITORIO 60 COLOR CAOBA / NEGRO", null));
        assertEquals("NEGRO", ColorEnNombre.de("SILLA COLOR NEGRO CON BRAZOS", null), "«CON BRAZOS» no es color");
    }

    @Test
    void colorDeImpresion_noEsElColorDelBien() {
        assertNull(ColorEnNombre.de("IMPRESORA LASER COLOR", null));
        assertEquals("NEGRO", ColorEnNombre.de("MULTIFUNCIONAL A COLOR, COLOR NEGRO", null), "la segunda frase sí es");
    }

    @Test
    void unaPalabraSuelta_salvoLasQueSuelenSerOtraCosa() {
        assertEquals("BLANCO", ColorEnNombre.de("PIZARRON BLANCO REFORZADO MEDIANO", null));
        assertEquals("GRIS", ColorEnNombre.de("BANCA GRISES FIJA, DE ACERO", null));
        assertEquals("NEGRO / CROMADO", ColorEnNombre.de("SILLAS NEGRAS CROMADAS", null));
        assertEquals("NEGRO", ColorEnNombre.de("MESA DE TRABAJO NEGRO CON CAFÉ PATAS METALICAS", null), "café suelto no cuenta");
        assertNull(ColorEnNombre.de("CAFETERA PARA CAFE 12 TAZAS", null));
        assertNull(ColorEnNombre.de("COPAS PARA VINO", null));
    }

    @Test
    void laLuzOLaLineaBlanca_noSonElColor() {
        assertNull(ColorEnNombre.de("DOMO PT IP 4 MEGAPIXEL/4 MM/LUZ BLANCA 30 MTS", null));
        assertNull(ColorEnNombre.de("REFRIGERADOR LINEA BLANCA", null));
        assertNull(ColorEnNombre.de("CUBETA DE PINTURA BLANCA", null));
    }

    @Test
    void tambienEnLaDescripcion_ySinNadaNoInventa() {
        assertEquals("GRIS", ColorEnNombre.de("ANAQUEL", "ANAQUEL DE 5 ENTREPAÑOS COLOR GRIS"));
        assertEquals("BEIGE", ColorEnNombre.de("ARCHIVERO METALICO", "4 GAVETAS COLOR BEIGE"));
        assertNull(ColorEnNombre.de("ESCRITORIO EJECUTIVO", "DE MADERA"));
        assertNull(ColorEnNombre.de(null, null));
    }

    /** 24-nov-2026: 30 bienes without color said «COLOR PERAL», «COLOR MADERA» or «COLOR MIEL»;
     *  without «COLOR», «DE MADERA» is what it is made of. */
    @Test
    void peralMaderaYMiel_soloDespuesDeColor() {
        assertEquals("PERAL", ColorEnNombre.de("MODULO EN L FABRICADO EN MELAMINA EN COLOR PERAL.", null));
        assertEquals("MADERA", ColorEnNombre.de("MUEBLE DE FORMAICA PARA COMPUTADORA DE COLOR MADERA", null));
        assertEquals("MIEL", ColorEnNombre.de("LIBRERO COLOR MIEL", null));
        assertEquals("NEGRO", ColorEnNombre.de("SILLA DE MADERA COLOR NEGRO", null));
        assertNull(ColorEnNombre.de("MESA DE MADERA", null));
        assertFalse(ColorEnNombre.esColor("MADERA"));
    }

    /** 18-nov-2026: a color was the tipo de bien of 7 bienes. */
    @Test
    void unTextoQueEsUnColor_porSuPrimeraPalabra() {
        assertTrue(ColorEnNombre.esColor("NEGRO"));
        assertTrue(ColorEnNombre.esColor("NEGRO ERGONÓMICO"));
        assertTrue(ColorEnNombre.esColor(" blanca"));
        assertFalse(ColorEnNombre.esColor("MUEBLE"));
        assertFalse(ColorEnNombre.esColor("CAFETERA"));
        assertFalse(ColorEnNombre.esColor("CAFE"), "solo es color después de «COLOR»");
        assertFalse(ColorEnNombre.esColor("Camioneta"));
        assertFalse(ColorEnNombre.esColor(""));
        assertFalse(ColorEnNombre.esColor(null));
    }
}
