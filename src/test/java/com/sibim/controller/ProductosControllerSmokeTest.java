package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Producto;
import com.sibim.session.SessionManager;
import com.sibim.util.ResponsiveHeader;
import javafx.scene.Node;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProductosControllerSmokeTest extends ControllerSmokeTestBase {

    private Parent root;
    private Stage stage;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        SessionManager.setCurrentUser(adminUser());
        root = new FXMLLoader(getClass().getResource("/fxml/productos.fxml")).load();
        ResponsiveHeader.installAll(root);   // MainController does this on navigation
        stage.setScene(new Scene(root, 1600, 900));
        stage.show();
        stage.toFront();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        DemoDataStore.reiniciar();
    }

    private TableView<Producto> tabla() throws Exception {
        TableView<Producto> t = lookup("#table").queryTableView();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !t.getItems().isEmpty());
        return t;
    }

    @Test
    void cargaLosBienesDelModoDemo() throws Exception {
        assertFalse(tabla().getItems().isEmpty());
    }

    @Test
    void etiquetaSeHabilitaAlSeleccionar_yOfreceElFormatoOficial() throws Exception {
        TableView<Producto> t = tabla();
        MenuButton etiqueta = lookup("#btnEtiqueta").queryAs(MenuButton.class);
        assertTrue(etiqueta.isDisabled(), "sin selección no hay nada que etiquetar");
        interact(() -> t.getSelectionModel().select(0));
        assertFalse(etiqueta.isDisabled());
        assertTrue(etiqueta.getItems().stream().anyMatch(i -> i.getText().contains("formato oficial")));
    }

    @Test
    void fichaOfreceFichaTecnicaEInventarioFotografico() throws Exception {
        TableView<Producto> t = tabla();
        MenuButton ficha = lookup("#btnFicha").queryAs(MenuButton.class);
        assertTrue(ficha.isDisabled());
        interact(() -> t.getSelectionModel().select(0));
        assertFalse(ficha.isDisabled());
        assertTrue(ficha.getItems().stream().anyMatch(i -> i.getText().startsWith("Ficha técnica")));
        assertTrue(ficha.getItems().stream().anyMatch(i -> i.getText().startsWith("Inventario fotográfico")));
    }

    /** At a narrow width with a row selected ("1 seleccionado" appears), every
     *  bottom-bar button must show its full label or just its icon — never "…". */
    @Test
    void barraInferiorAngosta_sinBotonesRecortados() throws Exception {
        TableView<Producto> t = tabla();
        interact(() -> stage.setWidth(1050));
        interact(() -> t.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();

        HBox barra = lookup(".bottom-action-bar").queryAs(HBox.class);
        for (Node n : barra.getChildren()) {
            if (!(n instanceof ButtonBase b) || !b.isVisible() || b.getText() == null || b.getText().isBlank()) continue;
            if (b.getContentDisplay() == ContentDisplay.GRAPHIC_ONLY) continue;
            Node texto = b.lookup(".text");
            String mostrado = texto instanceof javafx.scene.text.Text tx ? tx.getText() : b.getText();
            assertEquals(b.getText(), mostrado, "botón recortado en la barra inferior: " + b.getText());
        }
    }
}
