package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bienes opened with an área already chosen — coming from Organigrama, Reportes
 * or Alertas, or a filter remembered from the last session. The filtered load
 * used to win the race against the first load, which was then dropped: the
 * summary cards stayed on "—" for good.
 */
class ProductosFiltroInicialTest extends ControllerSmokeTestBase {

    private static final String AREA = "Tesorería Municipal";

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        SessionManager.setCurrentUser(adminUser());
        NavigationContext.setPendingAreaFilter(AREA);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/productos.fxml")).load();
        stage.setScene(new Scene(root, 1600, 900));
        stage.show();
        stage.toFront();
    }

    @Test
    void conAreaYaElegida_cargaElResumenYLaTablaFiltrada() {
        sleep(3000);   // both loads and the count-up animation
        ComboBox<String> area = lookup("#areaFilter").query();
        assertEquals(AREA, area.getValue());

        Label total = lookup("#lblStatTotal").query();
        assertNotEquals("—", total.getText(), "las tarjetas de resumen deben cargarse aunque haya un filtro inicial");
        assertTrue(Long.parseLong(total.getText().replaceAll("\\D", "")) > 0);

        TableView<com.sibim.model.Producto> tabla = lookup("#table").query();
        assertTrue(!tabla.getItems().isEmpty(), "los datos demo tienen bienes en " + AREA);
        assertTrue(tabla.getItems().stream().allMatch(p -> AREA.equals(p.getArea())), "solo bienes del área elegida");
    }
}
