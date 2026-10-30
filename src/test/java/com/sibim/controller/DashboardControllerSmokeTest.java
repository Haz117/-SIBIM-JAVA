package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DashboardControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Parent root = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    /** Patrimonio sees what is left, each card with its count; DashboardMiAreaSmokeTest
     *  covers that an área doesn't. */
    @Test
    void porHacer_muestraTarjetas() throws Exception {
        javafx.scene.Node seccion = lookup("#porHacerSection").query();
        org.testfx.util.WaitForAsyncUtils.waitFor(20, java.util.concurrent.TimeUnit.SECONDS, seccion::isVisible);
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        org.junit.jupiter.api.Assertions.assertFalse(lookup(".por-hacer-tarjeta").queryAll().isEmpty());
    }

    @Test
    void nodosClavePresentes() {
        assertNotNull(lookup("#lblTotalBienes").query(),    "lblTotalBienes debe existir");
        assertNotNull(lookup("#btnRefreshDash").query(),    "btnRefreshDash debe existir");
        assertNotNull(lookup("#rootScrollPane").query(),    "rootScrollPane debe existir");
        assertNotNull(lookup("#tablaReciente").query(),     "tablaReciente debe existir");
    }
}
