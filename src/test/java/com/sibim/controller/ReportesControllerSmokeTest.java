package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ReportesControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Parent root = new FXMLLoader(getClass().getResource("/fxml/reportes.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertEquals(5, lookup("#listaListados").<javafx.scene.layout.VBox>query().getChildren().size(), "cinco listados");
        assertEquals(5, lookup("#listaFormatos").<javafx.scene.layout.VBox>query().getChildren().size(), "cinco formatos oficiales");
        assertNotNull(lookup("#btnPresetHoy").query(),    "btnPresetHoy debe existir");
        assertNotNull(lookup("#desdeField").query(),      "desdeField debe existir");
        assertNotNull(lookup("#spinner").query(),         "spinner debe existir");
    }

    @Test
    void etiquetaDePeriodo_diceQueAbarcaElReporte() {
        java.time.LocalDate a = java.time.LocalDate.of(2026, 9, 1), b = java.time.LocalDate.of(2026, 9, 30);
        assertEquals("Todo el historial", ReportesController.describirPeriodo(null, null));
        assertEquals("01/09/2026 – 30/09/2026", ReportesController.describirPeriodo(a, b));
        assertEquals("01/09/2026", ReportesController.describirPeriodo(a, a));
        assertEquals("Desde 01/09/2026", ReportesController.describirPeriodo(a, null));
        assertEquals("Hasta 30/09/2026", ReportesController.describirPeriodo(null, b));
    }

    /** Each report offers its formats: three for the listings, PDF only for the
     *  official forms, two variants of the dictamen. */
    @Test
    void cadaReporte_ofreceSusFormatos() {
        java.util.List<Integer> formatos = new java.util.ArrayList<>();
        for (String lista : new String[]{"#listaListados", "#listaFormatos"})
            for (javafx.scene.Node fila : lookup(lista).<javafx.scene.layout.VBox>query().getChildren()) {
                javafx.scene.Node boton = ((javafx.scene.layout.HBox) fila).getChildren().get(2);
                formatos.add(boton instanceof javafx.scene.control.MenuButton m ? m.getItems().size() : 1);
            }
        assertEquals(java.util.List.of(3, 3, 3, 3, 3, 1, 1, 1, 1, 2), formatos);
    }

    /** Only Inventario and Movimientos follow the period, and their chip says which one. */
    @Test
    void etiquetaDePeriodo_soloEnLosReportesQueLoUsan_ySigueLasFechas() {
        java.util.Set<javafx.scene.Node> chips = lookup("#listaListados").query().lookupAll(".dash-section-badge");
        assertEquals(2, chips.size());
        assertEquals(0, lookup("#listaFormatos").query().lookupAll(".dash-section-badge").size());
        javafx.scene.control.Label chip = (javafx.scene.control.Label) chips.iterator().next();
        assertEquals("Todo el historial", chip.getText());

        javafx.scene.control.DatePicker desde = lookup("#desdeField").query();
        javafx.scene.control.Button todo = lookup("#btnPresetTodo").query();
        interact(() -> desde.setValue(java.time.LocalDate.of(2026, 9, 1)));
        assertEquals("Desde 01/09/2026", chip.getText());
        org.junit.jupiter.api.Assertions.assertFalse(todo.getStyleClass().contains("btn-preset-active"),
            "una fecha elegida a mano desmarca el atajo");

        interact(todo::fire);
        assertEquals("Todo el historial", chip.getText());
        org.junit.jupiter.api.Assertions.assertNull(desde.getValue());
    }

    /** Alt+1-5 belong to this page only: they are taken back when it leaves the window. */
    @Test
    void atajosDePeriodo_seRetiranAlSalirDeLaPantalla() {
        javafx.scene.Scene scene = lookup("#periodCard").query().getScene();
        long alt = scene.getAccelerators().keySet().stream()
            .filter(k -> k instanceof javafx.scene.input.KeyCodeCombination c
                && c.getAlt() == javafx.scene.input.KeyCombination.ModifierValue.DOWN).count();
        assertEquals(5, alt);
        interact(() -> scene.setRoot(new javafx.scene.layout.Pane()));
        assertEquals(0, scene.getAccelerators().size());
    }
}
