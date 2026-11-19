package com.sibim.util;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/** 18-nov-2026: arriving at the Bienes search box with Tab opened «Búsquedas recientes», and
 *  the open list kept every key, Tab included: the keyboard could not leave the box. */
class HistorialBusquedaTecladoTest extends ApplicationTest {

    private static final String CLAVE = "sibim/prueba-historial-teclado";
    private TextField buscar;

    @Override
    public void start(Stage stage) {
        buscar = new TextField();
        SearchUtils.setupSearchHistory(CLAVE, buscar, null);
        stage.setScene(new Scene(new VBox(new Button("Antes"), buscar, new Button("Después")), 400, 200));
        stage.show();
    }

    @AfterAll
    static void limpiar() throws Exception {
        Preferences.userRoot().node(CLAVE).removeNode();
    }

    private boolean listaAbierta() {
        return Window.getWindows().stream().anyMatch(w -> w instanceof ContextMenu && w.isShowing());
    }

    @Test
    void conTabNoSeAbreSola_conFlechaAbajoSi_yTabLaCierra() {
        interact(() -> { buscar.requestFocus(); buscar.setText("silla ejecutiva"); buscar.fireEvent(new javafx.event.ActionEvent()); });
        interact(() -> buscar.getParent().requestFocus());
        WaitForAsyncUtils.waitForFxEvents();

        interact(buscar::requestFocus);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(listaAbierta(), "llegar al buscador no abre la lista");

        interact(() -> Event.fireEvent(buscar, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(listaAbierta(), "↓ abre las búsquedas recientes");

        ContextMenu lista = (ContextMenu) Window.getWindows().stream().filter(w -> w instanceof ContextMenu && w.isShowing()).findFirst().orElseThrow();
        interact(() -> Event.fireEvent(lista.getScene().getRoot(), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(listaAbierta(), "Tab la cierra");
    }
}
