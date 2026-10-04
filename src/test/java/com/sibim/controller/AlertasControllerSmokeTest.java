package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** The summary cards are the tabs: the chosen one brings its table to the panel. */
    @Test
    void tarjetas_cambianLaTablaDelPanel() {
        sleep(1500);   // demo data loads off the FX thread
        Label titulo = lookup("#lblPanelTitulo").query();
        // Demo data has an expired warranty, so the screen opens on Garantías.
        assertEquals("Garantías por vencer", titulo.getText());
        assertTrue(lookup("#tableGarantias").query().isVisible());

        elegir("#statCardPendientesSum");
        assertEquals("Pendientes patrimoniales", titulo.getText());
        assertFalse(lookup("#tableGarantias").query().isVisible(), "solo se ve la tabla de la categoría elegida");
        assertTrue(lookup("#tablePatrimoniales").query().isVisible());
        assertTrue(lookup("#panelExtras").query().isVisible(), "Pendientes trae sus vistas por área / por bien");

        elegir("#statCardComodatosSum");
        assertEquals("Comodatos vencidos", titulo.getText());
        assertTrue(lookup("#tableComodatos").query().isVisible());
        assertFalse(lookup("#panelExtras").query().isVisible());

        elegir("#statCardMantenimientoSum");
        assertTrue(lookup("#tableMantenimiento").query().isVisible());
    }

    /** The search narrows every category down, not only the one on screen. */
    @Test
    void buscador_filtraTodasLasCategorias() {
        sleep(1500);
        TableView<?> garantias = lookup("#tableGarantias").query();
        TableView<?> pendientes = lookup("#tablePatrimoniales").query();
        TextField buscador = lookup("#searchField").query();
        Label conteo = lookup("#lblPanelConteo").query();
        int todasGarantias = garantias.getItems().size();
        int todosPendientes = pendientes.getItems().size();
        assertTrue(todasGarantias > 0 && todosPendientes > 0, "los datos demo traen garantías y pendientes");

        interact(() -> buscador.setText("no-existe-zzz"));
        sleep(700);
        assertEquals(0, garantias.getItems().size());
        assertEquals(0, pendientes.getItems().size());
        elegir("#statCardPendientesSum");
        assertEquals("0 / " + todosPendientes, conteo.getText());

        interact(buscador::clear);
        sleep(700);
        assertEquals(todasGarantias, garantias.getItems().size());
        assertEquals(todosPendientes, pendientes.getItems().size());
        assertEquals(todosPendientes + " bienes", conteo.getText());
    }

    private void elegir(String tarjeta) {
        Node n = lookup(tarjeta).query();
        interact(() -> n.getOnMouseClicked().handle(null));
    }
}
