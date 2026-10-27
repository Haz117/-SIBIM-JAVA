package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import com.sibim.util.TutorialOverlay;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/** The icon rail on a short window (a 1366×768 laptop, or with the demo strip):
 *  the last icons were behind a scroll bar, the bottom one cut in half. Its buttons
 *  get less padding only then; on a tall window it looks as it always did. */
class RielCabeEnVentanaBajaTest extends ControllerSmokeTestBase {

    private Stage stage;
    private Scene scene;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        System.setProperty(SidebarSections.PREFS_NODE_PROPERTY, "sibim/test/sidebar/riel");
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Preferences.userNodeForPackage(TutorialOverlay.class).putBoolean("tutorial.v6." + adminUser().getUsername(), true);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/main.fxml")).load();
        scene = new Scene(root, 1270, 700);          // narrow: the side menu folds to the rail
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setScene(scene);
        // The test stage is shared: an earlier test may have left it wider than
        // the width where the menu folds to the rail.
        stage.setWidth(1270);
        stage.setHeight(700);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        MainController mc = MainController.getInstance();
        if (mc != null) mc.stopTimers();
        System.clearProperty(SidebarSections.PREFS_NODE_PROPERTY);
        Preferences.userRoot().node("sibim/test").removeNode();
        super.stop();
    }

    @Test
    void enVentanaBaja_cabenTodosLosIconos_yEnAltaSeVeComoSiempre() throws Exception {
        javafx.scene.Node sidebar = lookup(".sidebar").query();
        ScrollPane riel = lookup(".sidebar-scroll").queryAs(ScrollPane.class);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> sidebar.getStyleClass().contains("sidebar-collapsed"));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> sidebar.getStyleClass().contains("sidebar-baja"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(riel.getContent().getLayoutBounds().getHeight() <= riel.getViewportBounds().getHeight() + 0.5,
            "todos los íconos a la vista, sin barra de desplazamiento");

        interact(() -> stage.setHeight(1000));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !sidebar.getStyleClass().contains("sidebar-baja"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(riel.getContent().getLayoutBounds().getHeight() <= riel.getViewportBounds().getHeight() + 0.5);
    }
}
