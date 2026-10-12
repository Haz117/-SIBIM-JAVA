package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import com.sibim.util.TutorialOverlay;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On a narrow window (a 1366-px laptop, or a larger screen with Windows scaled
 * to 125 %) with the side menu open, no screen may be laid out wider than the
 * window. The bottom bar of Bienes did not fit and refused to shrink, so the
 * whole page — cards, table, "Nuevo Bien" — ran off the right edge.
 */
class PaginaNoSeSaleDeLaVentanaTest extends ControllerSmokeTestBase {

    private static final List<String> VISTAS = List.of(
        "productos", "movimientos", "resguardos", "prestamos", "comodatos", "bajas", "depreciacion", "auditoria");

    private Scene scene;

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty(SidebarSections.PREFS_NODE_PROPERTY, "sibim/test/sidebar/ancho");
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Preferences.userNodeForPackage(TutorialOverlay.class).putBoolean("tutorial.v6." + adminUser().getUsername(), true);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/main.fxml")).load();
        scene = new Scene(root, 1266, 790);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setScene(scene);
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
    void conElMenuLateralAbierto_ningunaPantallaSeSaleDeLaVentana() {
        MainController mc = MainController.getInstance();
        sleep(600);
        List<String> salidas = new ArrayList<>();
        for (String vista : VISTAS) {
            interact(() -> mc.navigateToView(vista));
            sleep(1500);
            /* The window is narrow, so the menu folds itself: open it as a person would. */
            interact(() -> {
                if (scene.lookup("#sidebar").getLayoutBounds().getWidth() < 150)
                    ((Button) scene.lookup("#btnToggleSidebar")).fire();
            });
            sleep(700);
            interact(() -> {
                /* The state that needs the most room: a row selected and long counters. */
                for (Node n : scene.getRoot().lookupAll(".table-view"))
                    if (n instanceof TableView<?> t && !t.getItems().isEmpty()) t.getSelectionModel().select(0);
                for (Node n : scene.getRoot().lookupAll(".bottom-action-bar .label"))
                    if (n instanceof Label l && l.isVisible() && l.getText() != null && !l.getText().isBlank())
                        l.setText(l.getText() + " — 2,516 de 2,516 en total");
            });
            sleep(700);
            interact(() -> {
                /* Against the window itself: the container grows along with a page
                   that refuses to shrink, so comparing those two would never fail. */
                Node area = scene.lookup("#contentArea");
                double fin = area.localToScene(area.getLayoutBounds()).getMaxX();
                if (fin > scene.getWidth() + 4)   // a couple of pixels of rounding and border
                    salidas.add(vista + ": el contenido termina en " + Math.round(fin) + " px y la ventana mide "
                        + Math.round(scene.getWidth()));
            });
        }
        assertTrue(salidas.isEmpty(), "Pantallas más anchas que la ventana a 1266 px:\n  " + String.join("\n  ", salidas));
    }
}
