package com.sibim.util;

import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** "Registrar Movimiento" and other long forms grew past the bottom of the
 *  screen on a smaller monitor and their buttons were out of reach. */
class DialogoCabeEnPantallaTest extends ApplicationTest {

    private Dialog<ButtonType> dialog;

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    @AfterEach
    void cerrar() {
        if (dialog != null) interact(() -> dialog.close());
    }

    /** A form that can't shrink (fields one under another) ends up scrolling. */
    @Test
    void formularioMasAltoQueLaPantalla_scrolleaYLosBotonesSeVen() {
        Region alto = new Region();
        alto.setMinHeight(Region.USE_PREF_SIZE);
        alto.setPrefHeight(pantalla().getHeight() * 1.5);
        abrir(new VBox(new Label("Campo"), alto));

        assertDentroDeLaPantalla();
        assertInstanceOf(ScrollPane.class, dialog.getDialogPane().getContent(), "el contenido se desplaza");
    }

    /** "Registrar Movimiento": its own scroll above pinned buttons — that part shrinks. */
    @Test
    void contenidoConSuPropioScroll_soloSeEncoge() {
        Region alto = new Region();
        alto.setPrefHeight(pantalla().getHeight() * 1.5);
        ScrollPane formulario = new ScrollPane(alto);
        VBox.setVgrow(formulario, javafx.scene.layout.Priority.ALWAYS);
        VBox contenido = new VBox(formulario, new HBox(new Label("Guardar")));
        abrir(contenido);

        assertDentroDeLaPantalla();
        assertSame(contenido, dialog.getDialogPane().getContent(), "no se envuelve otra vez");
    }

    @Test
    void dialogoNormal_noCambia() {
        abrir(new VBox(new Label("Corto")));
        AtomicReference<Double> h = new AtomicReference<>();
        interact(() -> h.set(ventana().getHeight()));
        assertTrue(h.get() < 400, "un diálogo corto conserva su altura: " + h.get());
        assertFalse(dialog.getDialogPane().getContent() instanceof ScrollPane);
    }

    private void abrir(Node contenido) {
        interact(() -> {
            dialog = DialogUtil.createButtonDialog(480);
            dialog.getDialogPane().setContent(contenido);
            dialog.show();
        });
        WaitForAsyncUtils.waitForFxEvents();
        sleep(400);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void assertDentroDeLaPantalla() {
        interact(() -> {
            Window w = ventana();
            Rectangle2D zona = pantalla();
            assertTrue(w.getHeight() <= zona.getHeight(), "alto " + w.getHeight() + " > pantalla " + zona.getHeight());
            assertTrue(w.getY() >= zona.getMinY() - 1, "arriba de la pantalla: y=" + w.getY());
            assertTrue(w.getY() + w.getHeight() <= zona.getMaxY() + 1, "abajo de la pantalla: " + (w.getY() + w.getHeight()));
            Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
            Bounds b = ok.localToScreen(ok.getBoundsInLocal());
            assertTrue(b.getMaxY() <= w.getY() + w.getHeight() && b.getMaxY() <= zona.getMaxY() + 1,
                "el botón Aceptar queda fuera de la vista: " + b);
        });
    }

    private Window ventana() { return dialog.getDialogPane().getScene().getWindow(); }

    private static Rectangle2D pantalla() { return Screen.getPrimary().getVisualBounds(); }
}
