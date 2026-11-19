package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DepreciacionControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Parent root = new FXMLLoader(getClass().getResource("/fxml/depreciacion.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertNotNull(lookup("#table").query(),          "table debe existir");
        assertNotNull(lookup("#searchField").query(),    "searchField debe existir");
        assertNotNull(lookup("#btnExportar").query(),   "btnExportar debe existir");
        assertNotNull(lookup("#spinner").query(),        "spinner debe existir");
    }

    /** 18-nov-2026: sorted as text, «100%» went between «16%» and «43%». */
    @Test
    void elPorcentajeSeOrdenaComoNumero() {
        java.util.List<String> celdas = new java.util.ArrayList<>(java.util.List.of("43%", "100%", "—", "16%", "85%", "9%"));
        celdas.sort(java.util.Comparator.comparingInt(DepreciacionController::porcentaje).reversed());
        assertEquals(java.util.List.of("100%", "85%", "43%", "16%", "9%", "—"), celdas);
    }
}
