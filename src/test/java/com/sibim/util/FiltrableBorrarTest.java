package com.sibim.util;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** A PC reported "The start must be <= the end" from Backspace in a type-to-filter
 *  picker: the list was replaced in the middle of the deletion, and the field was
 *  still deleting over the text it had before. The field is driven directly (the
 *  same deletePreviousChar Backspace calls), so the test doesn't depend on window
 *  focus. */
class FiltrableBorrarTest extends ApplicationTest {

    private final List<Throwable> errores = new CopyOnWriteArrayList<>();
    private Thread.UncaughtExceptionHandler anterior;
    private ComboBox<String> combo;

    @Override
    public void start(Stage stage) {
        anterior = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> errores.add(e));
        combo = new ComboBox<>();
        DialogUtil.makeFilterable(combo, List.of("Secretaría General Municipal", "Secretaría de Obras Públicas",
            "Contraloría Municipal", "Instancia Municipal de la Mujer"));
        stage.setScene(new Scene(new VBox(combo), 420, 300));
        stage.show();
    }

    @AfterEach
    void restaurar() { Thread.setDefaultUncaughtExceptionHandler(anterior); }

    @Test
    void borrarTrasElegir_noRompeElCampo_yConservaLoEscrito() {
        interact(() -> {
            combo.setValue("Contraloría Municipal");
            TextField editor = combo.getEditor();
            editor.end();
            for (int i = 0; i < 12; i++) editor.deletePreviousChar();
        });
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> combo.getEditor().insertText(combo.getEditor().getText().length(), "muj"));
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> { for (int i = 0; i < 3; i++) combo.getEditor().deletePreviousChar(); });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(errores.isEmpty(), "excepciones al borrar: " + errores);
        assertEquals("Contralor", combo.getEditor().getText(), "lo escrito se conserva");
        assertNull(combo.getValue(), "editar suelta la opción elegida");
        assertTrue(combo.getItems().contains("Contraloría Municipal"), "la lista sigue al texto");
    }
}
