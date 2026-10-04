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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** The forms too: "+ Agregar" in Nuevo resguardo was squeezed to "…" by the picker next to it. */
    @Test
    void formulariosPrincipales_sinBotonesCortados() {
        MainController mc = MainController.getInstance();
        List<String> cortados = new ArrayList<>();
        String[][] formularios = {
            {"productos", "#btnNuevoBien"}, {"movimientos", "#btnNuevo"}, {"resguardos", "#btnNuevo"}};
        for (String[] f : formularios) {
            interact(() -> mc.navigateToView(f[0]));
            sleep(1200);
            javafx.scene.control.Button abrir = (javafx.scene.control.Button) scene.lookup(f[1]);
            if (abrir == null || !visible(abrir)) continue;
            javafx.application.Platform.runLater(abrir::fire);   // showAndWait blocks inside the FX thread
            sleep(3000);
            for (javafx.stage.Window w : new ArrayList<>(javafx.stage.Window.getWindows())) {
                if (w.getScene() == scene || !w.isShowing() || w instanceof javafx.stage.PopupWindow) continue;
                javafx.application.Platform.runLater(() -> {
                    for (Node n : w.getScene().getRoot().lookupAll(".button")) revisar("formulario de " + f[0], n, cortados);
                    w.hide();
                });
                sleep(700);
            }
        }
        assertTrue(cortados.isEmpty(), "Botones con el texto cortado en formularios:\n  " + String.join("\n  ", cortados));
    }

    /**
     * Typing in the bien picker, key by key as a person does: the list must open and narrow down,
     * a second word must be accepted (the space bar used to pick the first result and wipe the
     * text), accents must not matter, and Enter must choose the match.
     */
    @Test
    void buscadorDeBienes_filtraAlEscribir() {
        assertEquals("ok", escribirEnElBuscador("resguardos"), "Nuevo resguardo");
        assertEquals("ok", escribirEnElBuscador("movimientos"), "Registrar movimiento");
    }

    @SuppressWarnings("unchecked")
    private String escribirEnElBuscador(String vista) {
        MainController mc = MainController.getInstance();
        interact(() -> mc.navigateToView(vista));
        sleep(1200);
        javafx.scene.control.Button abrir = (javafx.scene.control.Button) scene.lookup("#btnNuevo");
        javafx.application.Platform.runLater(abrir::fire);
        sleep(3000);
        String resultado = "no se abrió el formulario";
        for (javafx.stage.Window w : new ArrayList<>(javafx.stage.Window.getWindows())) {
            if (w.getScene() == scene || !w.isShowing() || w instanceof javafx.stage.PopupWindow) continue;
            javafx.scene.control.ComboBox<Object>[] ref = new javafx.scene.control.ComboBox[1];
            javafx.application.Platform.runLater(() -> {
                for (Node n : w.getScene().getRoot().lookupAll(".combo-box"))
                    if (n instanceof javafx.scene.control.ComboBox<?> c && c.getPromptText() != null
                            && c.getPromptText().startsWith("Seleccionar bien"))
                        ref[0] = (javafx.scene.control.ComboBox<Object>) c;
                if (ref[0] != null) ref[0].getEditor().requestFocus();
            });
            sleep(600);
            javafx.scene.control.ComboBox<Object> combo = ref[0];
            if (combo == null) { resultado = "no se encontró el buscador de bienes"; continue; }
            int todos = combo.getItems().size();
            for (char ch : "camara da".toCharArray()) {
                javafx.application.Platform.runLater(() -> teclear(combo.getEditor(), ch));
                sleep(200);
            }
            String texto = combo.getEditor().getText();
            int filtrados = combo.getItems().size();
            boolean abierta = combo.isShowing();
            javafx.application.Platform.runLater(() -> pulsar(combo.getEditor(), javafx.scene.input.KeyCode.ENTER));
            sleep(400);
            Object elegido = combo.getValue();
            String elegidoTxt = elegido == null ? "" : combo.getConverter().toString(elegido);
            resultado = !"camara da".equals(texto) ? "el texto tecleado se alteró: '" + texto + "'"
                : !abierta ? "la lista no se abre al escribir"
                : filtrados == 0 || filtrados >= todos ? "la lista no se filtra (" + filtrados + " de " + todos + ")"
                : !elegidoTxt.contains("Dahua") ? "Enter no eligió la coincidencia: '" + elegidoTxt + "'"
                : "ok";
            javafx.application.Platform.runLater(w::hide);
            sleep(600);
        }
        return resultado;
    }

    private static void teclear(Node destino, char ch) {
        javafx.scene.input.KeyCode codigo = ch == ' ' ? javafx.scene.input.KeyCode.SPACE
            : javafx.scene.input.KeyCode.getKeyCode(String.valueOf(ch).toUpperCase());
        javafx.event.Event.fireEvent(destino, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
            "", "", codigo, false, false, false, false));
        javafx.event.Event.fireEvent(destino, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_TYPED,
            String.valueOf(ch), "", javafx.scene.input.KeyCode.UNDEFINED, false, false, false, false));
        javafx.event.Event.fireEvent(destino, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED,
            "", "", codigo, false, false, false, false));
    }

    private static void pulsar(Node destino, javafx.scene.input.KeyCode codigo) {
        javafx.event.Event.fireEvent(destino, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
            "", "", codigo, false, false, false, false));
        javafx.event.Event.fireEvent(destino, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_RELEASED,
            "", "", codigo, false, false, false, false));
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
