package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDialogFactory;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Categoria;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Nuevo bien has one row of buttons: Cancelar next to Siguiente. The dialog's
 *  own button bar held only Cancelar and read as a second footer. */
class NuevoBienDialogSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        stage.setScene(new Scene(new StackPane(), 400, 300));
        stage.show();
    }

    @Test
    void cancelarVaJuntoASiguiente_sinSegundaBarra() {
        Categoria cat = new Categoria("cat-prueba", "Mobiliario", null, "#8B5CF6", null, null);
        Platform.runLater(() -> ProductoDialogFactory.show(null, List.of(cat), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null)));
        for (int i = 0; i < 50 && lookup(".stepper-nav-bar").tryQuery().isEmpty(); i++) sleep(100);

        HBox nav = lookup(".stepper-nav-bar").query();
        Button cancelar = nav.getChildren().stream()
            .filter(n -> n instanceof Button b && "Cancelar".equals(b.getText()))
            .map(n -> (Button) n).findFirst()
            .orElseThrow(() -> new AssertionError("Cancelar debe estar en la barra de Siguiente"));
        Region barraDelDialogo = lookup(".button-bar").query();
        assertFalse(barraDelDialogo.isVisible());
        assertEquals(0, barraDelDialogo.getHeight(), 0.5, "la barra oculta no ocupa espacio");

        interact(cancelar::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(lookup(".stepper-nav-bar").tryQuery().isEmpty(), "Cancelar cierra la ventana");
    }

    /** The clave armonizada is the category's CONAC code: picking a category
     *  fills it in and picking another follows it, but one typed by hand stays. */
    @Test
    @SuppressWarnings("unchecked")
    void laClaveArmonizadaSaleDeLaCategoria() {
        Categoria muebles = new Categoria("cat-511", "Muebles de Oficina", null, "#8B5CF6", null, null);
        muebles.setCodigoConac("1.2.4.1.1.511");
        Categoria computo = new Categoria("cat-515", "Equipo de Cómputo", null, "#8B5CF6", null, null);
        computo.setCodigoConac("1.2.4.1.3.515");
        Platform.runLater(() -> ProductoDialogFactory.show(null, List.of(muebles, computo), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null)));
        // The dialog's tabs are laid out after its button bar shows: waiting for
        // the bar alone found no fields now and then (in CI too).
        ComboBox<Categoria> categoria = (ComboBox<Categoria>) esperar(n -> n instanceof ComboBox<?> c
            && "Buscar categoría…".equals(c.getPromptText()));
        TextField clave = (TextField) esperar(n -> n instanceof TextField t && "Ej. 1.2.4.1.1.511".equals(t.getPromptText()));
        assertEquals("", clave.getText());

        interact(() -> categoria.setValue(muebles));
        assertEquals("1.2.4.1.1.511", clave.getText());
        interact(() -> categoria.setValue(computo));
        assertEquals("1.2.4.1.3.515", clave.getText(), "sigue a la categoría");

        interact(() -> clave.setText("1.2.4.1.3.515.9"));
        interact(() -> categoria.setValue(muebles));
        assertEquals("1.2.4.1.3.515.9", clave.getText(), "la escrita a mano se respeta");

        interact(() -> ((Stage) categoria.getScene().getWindow()).close());
    }

    /** The first node {@code que} matches, waiting up to 10 s for it to show. */
    private Node esperar(java.util.function.Predicate<Node> que) {
        for (int i = 0; i < 100; i++) {
            java.util.Optional<Node> n = lookup(que::test).tryQuery();
            if (n.isPresent()) return n.get();
            sleep(100);
        }
        return lookup(que::test).query();   // fails with TestFX's own message
    }
}
