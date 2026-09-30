package com.sibim.controller;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Areas.usar(AreaCatalog.PREDETERMINADO);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/configuracion.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void fxmlCarga_sinExcepcion() { /* llegar aquí = pass */ }

    @Test
    void seccionAreas_muestraElOrganigramaCompleto() {
        Set<Label> titulos = lookup(".card-section-title").queryAllAs(Label.class);
        assertTrue(titulos.stream().anyMatch(l -> "Áreas y prefijos".equals(l.getText())),
            "el Admin debe ver la sección Áreas y prefijos");

        TableView<?> tabla = lookup(".data-table").queryAllAs(TableView.class).stream()
            .filter(t -> t.getItems().size() == AreaCatalog.PREDETERMINADO.entradas().size())
            .findFirst().orElse(null);
        assertNotNull(tabla, "la tabla de áreas lista las 44 áreas");
        assertEquals(AreaCatalog.PREDETERMINADO.entradas(), tabla.getItems());
    }

    @Test
    void seccionAreas_enDemo_noSePuedeEditar() {
        Node nueva = lookup(n -> n instanceof Button b && "Nueva área".equals(b.getText())).query();
        assertNotNull(nueva);
        assertTrue(nueva.isDisabled(), "sin base de datos las áreas son solo lectura");
        assertTrue(lookup(n -> n instanceof Label l && l.getText() != null
            && l.getText().startsWith("Sin conexión a la base de datos")).tryQuery().isPresent());
    }
}
