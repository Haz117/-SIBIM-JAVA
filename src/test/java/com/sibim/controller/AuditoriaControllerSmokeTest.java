package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditoriaControllerSmokeTest extends ControllerSmokeTestBase {

    private AuditoriaController controlador;

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/auditoria.fxml"));
        Parent root = loader.load();
        controlador = loader.getController();
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
        assertNotNull(lookup("#btnRefresh").query(),     "btnRefresh debe existir");
        assertNotNull(lookup("#paginationBar").query(),  "paginationBar debe existir");
    }

    /** 19-nov-2026: the acciones are chips with their count; the first one says «Todas». */
    @Test
    void lasAccionesSonEtiquetasQueFiltran() {
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.layout.FlowPane chips = lookup("#chipsAccion").query();
        org.junit.jupiter.api.Assertions.assertFalse(chips.getChildren().isEmpty(), "hay etiquetas de acción");
        javafx.scene.control.Button todas = (javafx.scene.control.Button) chips.getChildren().get(0);
        org.junit.jupiter.api.Assertions.assertTrue(todas.getText().startsWith("Todas  ·  "), todas.getText());
        org.junit.jupiter.api.Assertions.assertTrue(todas.getStyleClass().contains("btn-preset-active"), "sin filtro, «Todas» va marcada");
    }

    /** «Ver todo lo de SSPCR/19»: one bien's history, with a chip that takes it away. */
    @Test
    void verTodoLoDeUnBien_yQuitarlo() {
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        com.sibim.model.AuditLog uno = new com.sibim.model.AuditLog();
        uno.setEntidad("producto"); uno.setEntidadId("bien-de-prueba"); uno.setEntidadNombre("SILLA");
        uno.setDetalle("Datos del bien actualizados · número PRUE/07 · Área de Prueba · Cambió: color (sin dato → NEGRO)");
        interact(() -> controlador.verTodoDe(uno));
        javafx.scene.control.Button solo = lookup("#btnSoloBien").query();
        org.junit.jupiter.api.Assertions.assertTrue(solo.isVisible());
        org.junit.jupiter.api.Assertions.assertEquals("Solo PRUE/07", solo.getText());
        org.junit.jupiter.api.Assertions.assertEquals("bien-de-prueba", controlador.filtro().entidadId());
        interact(solo::fire);
        org.junit.jupiter.api.Assertions.assertFalse(solo.isVisible());
        org.junit.jupiter.api.Assertions.assertNull(controlador.filtro().entidadId());
    }
}
