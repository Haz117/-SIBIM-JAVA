package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import static org.junit.jupiter.api.Assertions.*;

class BajasControllerSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        SessionManager.setCurrentUser(adminUser());
        new ProductoService().darDeBaja("p-01", "Deterioro · Solicitud de baja: OF-12/2026");
        Parent root = new FXMLLoader(getClass().getResource("/fxml/bajas.fxml")).load();
        stage.setScene(new Scene(root, 1600, 900));
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        DemoDataStore.reiniciar();
    }

    @Test
    void listaLasBajasConSuFolioDeSolicitud() {
        WaitForAsyncUtils.waitForFxEvents();
        TableView<Producto> table = lookup(".data-table").queryTableView();
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(table.getItems().stream().anyMatch(p -> "p-01".equals(p.getId())));
        Producto p = table.getItems().stream().filter(x -> "p-01".equals(x.getId())).findFirst().orElseThrow();
        assertEquals("OF-12/2026", BajasController.folioSolicitud(p));
        assertEquals("Deterioro", BajasController.motivo(p));
    }

    @Test
    void elAdminVeReactivar() {
        Button b = lookup(n -> n instanceof Button x && "Reactivar".equals(x.getText())).queryButton();
        assertTrue(b.isVisible());
    }
}
