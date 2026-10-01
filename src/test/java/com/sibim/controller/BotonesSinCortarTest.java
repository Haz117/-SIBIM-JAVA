package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import com.sibim.util.TutorialOverlay;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No button on any screen may show its label cut to "…" on a common laptop
 * (1366×768). That is how "Res…", "Restau…" and "Ver historial de cambi…"
 * shipped: nothing failed, they just did not fit. Buttons that collapse to an
 * icon on purpose (ResponsiveHeader) draw no text and pass.
 */
class BotonesSinCortarTest extends ControllerSmokeTestBase {

    private static final List<String> VISTAS = List.of(
        "dashboard", "organigrama", "productos", "categorias", "movimientos", "alertas", "reportes",
        "depreciacion", "resguardos", "prestamos", "comodatos", "actas", "bajas", "configuracion", "auditoria");

    private Scene scene;

    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty(SidebarSections.PREFS_NODE_PROPERTY, "sibim/test/sidebar/botones");
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Preferences.userNodeForPackage(TutorialOverlay.class).putBoolean("tutorial.v6." + adminUser().getUsername(), true);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/main.fxml")).load();
        scene = new Scene(root, 1366, 768);
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
    void ningunBotonMuestraSuTextoCortado() {
        MainController mc = MainController.getInstance();
        List<String> cortados = new ArrayList<>();
        for (String vista : VISTAS) {
            interact(() -> mc.navigateToView(vista));
            sleep(1200);
            interact(() -> {
                for (Node n : scene.getRoot().lookupAll(".button")) revisar(vista, n, cortados);
                for (Node n : scene.getRoot().lookupAll(".menu-button")) revisar(vista, n, cortados);
            });
        }
        assertTrue(cortados.isEmpty(), "Botones con el texto cortado a 1366×768:\n  " + String.join("\n  ", cortados));
    }

    private static void revisar(String vista, Node n, List<String> cortados) {
        if (!(n instanceof ButtonBase b) || !visible(b)) return;
        String texto = b.getText();
        if (texto == null || texto.isBlank()) return;
        for (Node hijo : b.lookupAll(".text")) {
            if (!(hijo instanceof Text t) || !t.isVisible() || t.getText() == null) continue;
            String mostrado = t.getText();
            if (!mostrado.equals(texto) && (mostrado.endsWith("...") || mostrado.endsWith("…")))
                cortados.add(vista + ": \"" + texto + "\" se ve como \"" + mostrado + "\"");
        }
    }

    private static boolean visible(Node n) {
        for (Node p = n; p != null; p = p.getParent())
            if (!p.isVisible() || !p.isManaged()) return false;
        return n.getScene() != null;
    }
}
