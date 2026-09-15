package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResguardosControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Parent root = new FXMLLoader(getClass().getResource("/fxml/resguardos.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertNotNull(lookup("#table").query(),        "table debe existir");
        assertNotNull(lookup("#btnNuevo").query(),     "btnNuevo debe existir");
        assertNotNull(lookup("#searchField").query(),  "searchField debe existir");
        assertNotNull(lookup("#spinner").query(),      "spinner debe existir");
    }

    @Test
    void btnCancelar_deshabilitado_sinSeleccion() {
        javafx.scene.control.Button btn = lookup("#btnCancelar").queryAs(javafx.scene.control.Button.class);
        assertNotNull(btn, "btnCancelar debe existir");
        assertTrue(btn.isDisabled(), "btnCancelar debe estar deshabilitado cuando no hay selección");
    }
}
