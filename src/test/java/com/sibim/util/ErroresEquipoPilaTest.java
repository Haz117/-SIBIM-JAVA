package com.sibim.util;

import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 26-oct and 5-nov-2026: «The start must be <= the end» on Backspace reached errores_equipo
 *  with JavaFX's first 12 steps only, not the field of SIBIM where it happened. */
class ErroresEquipoPilaTest {

    @Test
    void guardaLosPasosDeSibim_aunqueVenganDespuesDeLos12Primeros() {
        List<StackTraceElement> pasos = new ArrayList<>();
        for (int i = 0; i < 20; i++)
            pasos.add(new StackTraceElement("javafx.scene.control.TextInputControl", "paso" + i, "TextInputControl.java", i));
        pasos.add(new StackTraceElement("com.sibim.controller.dialogs.CampoFicticio", "alBorrar", "CampoFicticio.java", 42));
        for (int i = 0; i < 5; i++)
            pasos.add(new StackTraceElement("com.sun.glass.ui.View", "handle" + i, "View.java", i));
        IllegalArgumentException e = new IllegalArgumentException("The start must be <= the end");
        e.setStackTrace(pasos.toArray(new StackTraceElement[0]));

        String pila = ErroresEquipoAppender.pila(new ThrowableProxy(e));

        assertTrue(pila.startsWith("java.lang.IllegalArgumentException: The start must be <= the end"));
        assertTrue(pila.contains("TextInputControl.paso11("), "los 12 primeros");
        assertFalse(pila.contains("TextInputControl.paso12("));
        assertTrue(pila.contains("  … 8 más\n  at com.sibim.controller.dialogs.CampoFicticio.alBorrar(CampoFicticio.java:42)"), pila);
        assertTrue(pila.endsWith("  … 5 más\n"), pila);
    }
}
