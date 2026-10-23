package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDialogFactory;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Categoria;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
}
