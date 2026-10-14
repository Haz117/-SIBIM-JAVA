package com.sibim.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Categorías shows a category's color by name ("Azul oscuro"), not "#1D4ED8". */
class NombreColorTest {

    @Test
    void nombresQueLaGenteDice() {
        assertEquals("Rojo", CategoriasController.nombreColor("#DC2626"));
        assertEquals("Rojo oscuro", CategoriasController.nombreColor("#6F1820"));
        assertEquals("Azul", CategoriasController.nombreColor("#2563EB"));
        assertEquals("Verde", CategoriasController.nombreColor("#16A34A"));
        assertEquals("Naranja", CategoriasController.nombreColor("#D97706"));
        assertEquals("Morado", CategoriasController.nombreColor("#7C3AED"));
        assertEquals("Gris", CategoriasController.nombreColor("#64748B"));
        assertEquals("Negro", CategoriasController.nombreColor("#0F172A"));
        assertEquals("Blanco", CategoriasController.nombreColor("#FFFFFF"));
        assertEquals("no-es-color", CategoriasController.nombreColor("no-es-color"));
    }
}
