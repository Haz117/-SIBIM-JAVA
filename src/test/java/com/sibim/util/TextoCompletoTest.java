package com.sibim.util;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.*;

/** 18-nov-2026: at 1366 px every screen cut text with «…» (names, áreas, resguardantes). */
class TextoCompletoTest extends ApplicationTest {

    private static final String LARGO = "CAMARA DUAL LIGHT BALA IP 4 MEGAPIXELES, LENTE 2.8 MM, ANGULO DE VISION 104°";
    private Label etiqueta;

    @Override
    public void start(Stage stage) {
        etiqueta = new Label(LARGO);
        etiqueta.setMaxWidth(120);
        StackPane raiz = new StackPane(etiqueta);
        stage.setScene(new Scene(raiz, 400, 100));
        stage.show();
    }

    @Test
    void cortado_diceSuTextoEntero_yAlCaberYaNo() {
        interact(() -> TextoCompleto.revisar(etiqueta));
        assertTrue(TextoCompleto.cortado(etiqueta));
        assertNotNull(etiqueta.getTooltip());
        assertEquals(LARGO, etiqueta.getTooltip().getText());

        interact(() -> { etiqueta.setText("CORTO"); etiqueta.applyCss(); etiqueta.getParent().layout(); });
        interact(() -> TextoCompleto.revisar(etiqueta));
        assertNull(etiqueta.getTooltip(), "ya cabe");
    }

    @Test
    void elTooltipQueYaTeniaSeQueda() {
        Tooltip propio = new Tooltip("Lo que la pantalla explica");
        interact(() -> { etiqueta.setText(LARGO); etiqueta.setTooltip(propio); etiqueta.applyCss(); etiqueta.getParent().layout(); });
        interact(() -> TextoCompleto.revisar(etiqueta));
        assertSame(propio, etiqueta.getTooltip());
    }
}
