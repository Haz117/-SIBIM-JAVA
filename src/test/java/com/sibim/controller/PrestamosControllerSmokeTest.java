package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrestamosControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Parent root = new FXMLLoader(getClass().getResource("/fxml/prestamos.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertNotNull(lookup("#table").query(),       "table debe existir");
        assertNotNull(lookup("#btnNuevo").query(),    "btnNuevo debe existir");
        assertNotNull(lookup("#btnKanban").query(),   "btnKanban debe existir");
        assertNotNull(lookup("#estadoFilter").query(),"estadoFilter debe existir");
    }

    @Test
    void btnDevolver_deshabilitado_sinSeleccion() {
        // Sin fila seleccionada, devolver no debe estar disponible
        javafx.scene.control.Button btn = lookup("#btnDevolver").queryAs(javafx.scene.control.Button.class);
        assertNotNull(btn, "btnDevolver debe existir");
        org.junit.jupiter.api.Assertions.assertTrue(btn.isDisabled(),
            "btnDevolver debe estar deshabilitado cuando no hay selección");
    }
}
