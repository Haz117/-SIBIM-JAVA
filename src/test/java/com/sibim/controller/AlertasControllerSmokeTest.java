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

class AlertasControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Parent root = new FXMLLoader(getClass().getResource("/fxml/alertas.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertTrue(lookup("#tableAgotados").queryAll().isEmpty(), "ya no hay tabla de agotados: no es una tienda");
        assertNotNull(lookup("#tableMantenimiento").query(), "tableMantenimiento debe existir");
        assertNotNull(lookup("#tableGarantias").query(),  "tableGarantias debe existir");
        assertNotNull(lookup("#searchField").query(),     "searchField debe existir");
        assertNotNull(lookup("#spinner").query(),         "spinner debe existir");
    }
}
