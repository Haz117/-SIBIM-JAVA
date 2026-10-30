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

    private ReportesController controller;

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/reportes.fxml"));
        Parent root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void nodosClavePresentes() {
        assertEquals(7, lookup("#listaListados").<javafx.scene.layout.VBox>query().getChildren().size(), "siete listados");
        assertEquals(5, lookup("#listaFormatos").<javafx.scene.layout.VBox>query().getChildren().size(), "cinco formatos oficiales");
        assertNotNull(lookup("#btnPresetHoy").query(),    "btnPresetHoy debe existir");
        assertNotNull(lookup("#desdeField").query(),      "desdeField debe existir");
        assertNotNull(lookup("#spinner").query(),         "spinner debe existir");
        org.junit.jupiter.api.Assertions.assertTrue(lookup("#categoriaCard").query().isVisible(),
            "Patrimonio ve la gráfica de valor por categoría");
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

    /** Each report offers its formats: three for the listings (the Inventario General's
     *  PDF alphabetical or by área), con/sin fotos for the inventario por área, PDF
     *  only for the official forms, two variants of the dictamen. */
    @Test
    void cadaReporte_ofreceSusFormatos() {
        java.util.List<Integer> formatos = new java.util.ArrayList<>();
        for (String lista : new String[]{"#listaListados", "#listaFormatos"})
            for (javafx.scene.Node fila : lookup(lista).<javafx.scene.layout.VBox>query().getChildren()) {
                javafx.scene.Node boton = ((javafx.scene.layout.HBox) fila).getChildren().get(2);
                formatos.add(boton instanceof javafx.scene.control.MenuButton m ? m.getItems().size() : 1);
            }
        assertEquals(java.util.List.of(1, 4, 2, 3, 3, 3, 3, 1, 2, 1, 1, 2), formatos);
    }

    /** The resguardos of a person, without going to Resguardos: first row of the page,
     *  a few letters show who, what they hold, and Enter generates the PDF. */
    @Test
    void resguardante_primeraFila_tecleaYEnter_generaElPdf() throws Exception {
        javafx.scene.layout.HBox fila = (javafx.scene.layout.HBox)
            lookup("#listaListados").<javafx.scene.layout.VBox>query().getChildren().get(0);
        assertEquals("Resguardos por resguardante", ((javafx.scene.control.Label) fila.lookup(".report-title")).getText());
        // The demo's bienes have no resguardante: the row's button would only say so.
        com.sibim.model.Producto bien = new com.sibim.model.Producto();
        bien.setId("p-20"); bien.setCodigo("CON/20"); bien.setNombre("Silla"); bien.setStockActual(1);
        bien.setResguardante("Ana García"); bien.setArea("Contraloría");
        com.sibim.model.Resguardo cancelado = new com.sibim.model.Resguardo();
        cancelado.setId("r-1"); cancelado.setNumero("RSG-2026-000001"); cancelado.setResguardanteNombre("Beto Luna");
        cancelado.setEstado(com.sibim.model.Resguardo.ESTADO_CANCELADO);
        Scene escena = fila.getScene();
        java.util.Set<javafx.stage.Window> antes = new java.util.HashSet<>(javafx.stage.Window.getWindows());
        // runLater: the dialog waits for its answer, and this thread has to go on to give it.
        interact(() -> javafx.application.Platform.runLater(() -> controller.elegirPersona(java.util.List.of(cancelado), java.util.List.of(bien), escena)));
        try {
            org.testfx.util.WaitForAsyncUtils.waitFor(15, java.util.concurrent.TimeUnit.SECONDS, () ->
                javafx.stage.Window.getWindows().stream().anyMatch(v -> v.getScene().getRoot() instanceof javafx.scene.control.DialogPane));
            javafx.scene.control.DialogPane panel = dialogoAbierto();
            javafx.stage.Window ventana = panel.getScene().getWindow();
            javafx.scene.control.TextField buscar = (javafx.scene.control.TextField) panel.lookup("#buscarPersona");
            javafx.scene.control.Button generar = (javafx.scene.control.Button) panel.lookupButton(javafx.scene.control.ButtonType.OK);
            org.junit.jupiter.api.Assertions.assertTrue(generar.isDisabled(), "sin persona no hay qué generar");

            interact(() -> buscar.setText("luna"));
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            assertEquals(1, panel.lookupAll(".rsg-tarjeta").size(), "se ve lo que tiene quien coincide");
            org.junit.jupiter.api.Assertions.assertTrue(generar.isDisabled(), "un cancelado no se marca solo");
            enter(buscar);
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            org.junit.jupiter.api.Assertions.assertTrue(ventana.isShowing(), "Enter sin nada marcado no hace nada");

            interact(() -> buscar.setText("garcia"));
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            org.junit.jupiter.api.Assertions.assertFalse(generar.isDisabled(), "la primera coincidencia, sin importar el acento");
            enter(buscar);
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            org.junit.jupiter.api.Assertions.assertFalse(ventana.isShowing(), "Enter genera el PDF");
            // The export ends in its own window; that is what the row promised.
            org.testfx.util.WaitForAsyncUtils.waitFor(30, java.util.concurrent.TimeUnit.SECONDS, () ->
                javafx.stage.Window.getWindows().stream().anyMatch(v -> v.getScene().getRoot() instanceof javafx.scene.control.DialogPane));
        } finally {
            interact(() -> new java.util.ArrayList<>(javafx.stage.Window.getWindows()).stream()
                .filter(v -> !antes.contains(v)).forEach(javafx.stage.Window::hide));
        }
    }

    private static javafx.scene.control.DialogPane dialogoAbierto() {
        return javafx.stage.Window.getWindows().stream()
            .filter(v -> v.getScene().getRoot() instanceof javafx.scene.control.DialogPane)
            .map(v -> (javafx.scene.control.DialogPane) v.getScene().getRoot())
            .findFirst().orElseThrow();
    }

    /** A real key press on that node, whatever window the person using the PC has in front. */
    private void enter(javafx.scene.Node nodo) {
        for (javafx.event.EventType<javafx.scene.input.KeyEvent> tipo : java.util.List.of(
                javafx.scene.input.KeyEvent.KEY_PRESSED, javafx.scene.input.KeyEvent.KEY_RELEASED))
            interact(() -> javafx.event.Event.fireEvent(nodo, new javafx.scene.input.KeyEvent(tipo, "", "",
                javafx.scene.input.KeyCode.ENTER, false, false, false, false)));
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
