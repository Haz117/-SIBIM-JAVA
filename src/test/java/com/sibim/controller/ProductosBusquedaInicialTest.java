package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** "Ver sus bienes", from a person: Bienes opens on that search, once. */
class ProductosBusquedaInicialTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        SessionManager.setCurrentUser(adminUser());
        NavigationContext.setPendingBusqueda("ANA RUIZ");
        Parent root = new FXMLLoader(getClass().getResource("/fxml/productos.fxml")).load();
        stage.setScene(new Scene(root, 1600, 900));
        stage.show();
        stage.toFront();
    }

    @Test
    void abreConLaBusquedaPedida_yNoSeQuedaParaLaSiguiente() {
        TextField buscar = lookup("#searchField").query();
        assertEquals("ANA RUIZ", buscar.getText());
        assertNull(NavigationContext.consumePendingBusqueda());
    }
}
