package com.sibim.util;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reader types like a keyboard: by the time its Enter says "this was a
 * scan", the código is already in whichever field had the focus. In the conteo
 * físico that was the search box (the list filtered down to nothing while
 * bienes kept being marked out of sight) or a count.
 */
class BarcodeScannerTecleadoTest extends ApplicationTest {

    private TextField buscador;
    private Spinner<Integer> contado;
    private Button boton;

    @Override
    public void start(Stage stage) {
        buscador = new TextField();
        contado = new Spinner<>(0, 1000, 1);
        contado.setEditable(true);
        boton = new Button("Finalizar");
        stage.setScene(new Scene(new VBox(buscador, contado, boton), 260, 140));
        stage.show();
    }

    @Test
    void loQueTecleoElLector_seQuitaDelBuscador() {
        interact(() -> {
            buscador.setText("silla");
            buscador.insertText(5, "SGM/07");
            BarcodeScanner.quitarTecleado(buscador, "SGM/07");
        });
        assertEquals("silla", buscador.getText());
    }

    @Test
    void loQueTecleoElLector_seQuitaDelContado() {
        interact(() -> {
            contado.getEditor().insertText(1, "7501");
            BarcodeScanner.quitarTecleado(contado, "7501");
        });
        assertEquals("1", contado.getEditor().getText());
    }

    @Test
    void siElCampoYaNoTieneLoTecleado_noSeBorraNada() {
        interact(() -> {
            buscador.setText("escritorio");
            buscador.positionCaret(10);
            BarcodeScanner.quitarTecleado(buscador, "SGM/07");
            BarcodeScanner.quitarTecleado(boton, "SGM/07");   // a control with no text: nothing to do
            BarcodeScanner.quitarTecleado(null, "SGM/07");
        });
        assertEquals("escritorio", buscador.getText());
    }
}
