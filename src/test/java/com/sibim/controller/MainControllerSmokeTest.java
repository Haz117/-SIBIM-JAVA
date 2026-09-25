package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.session.SessionManager;
import com.sibim.util.TutorialOverlay;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loads the REAL main.fxml with its controller. main.fxml wires 15 nav buttons, four accordion
 * sections and a footer to MainController by fx:id / handler name; nothing else in the suite
 * loads it, so a typo there would otherwise only show up when someone starts the app.
 */
class MainControllerSmokeTest extends ControllerSmokeTestBase {

    /** Every sidebar destination the registry must expose (view names = /fxml/<view>.fxml, plus the dialog action). */
    private static final List<String> VIEWS = List.of(
        "dashboard", "organigrama", "productos", "categorias",
        "movimientos", "alertas", "reportes", "depreciacion", "conteo",
        "resguardos", "prestamos", "comodatos", "actas",
        "configuracion", "auditoria");

    private String prefsNode;

    @Override
    public void start(Stage stage) throws Exception {
        // Folding a section saves it in the user's Preferences: point it at a throw-away node so this
        // test neither depends on nor changes the developer's real sidebar layout.
        prefsNode = "sibim/test/sidebar/" + java.util.UUID.randomUUID();
        System.setProperty(SidebarSections.PREFS_NODE_PROPERTY, prefsNode);
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        // Mark the first-run tutorial as seen for this fake user so its overlay doesn't cover the UI.
        Preferences.userNodeForPackage(TutorialOverlay.class).putBoolean("tutorial.v6." + adminUser().getUsername(), true);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/main.fxml")).load();
        Scene scene = new Scene(root, 1280, 800);
        // The real stylesheet matters here: CSS (e.g. .sidebar-logo-area padding) overrides what the FXML declares.
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
        stage.toFront();
    }

    @Override
    public void stop() throws Exception {
        MainController mc = MainController.getInstance();
        if (mc != null) mc.stopTimers();      // clock / session guard / badge refresh would outlive the test
        System.clearProperty(SidebarSections.PREFS_NODE_PROPERTY);
        Preferences.userRoot().node(prefsNode).removeNode();
        Preferences.userRoot().node("sibim/test").removeNode();   // don't leave empty parents in the registry
        super.stop();
    }

    /** Every ApplicationTest method restarts the whole app (~6 s), so the wiring checks share one method. */
    @Test
    void fxmlWiring_isComplete() {
        MainController mc = MainController.getInstance();
        assertNotNull(mc, "MainController.initialize() must have run (fx:controller + all @FXML ids injected)");

        for (String view : VIEWS) {
            Button b = mc.getNavButton(view);
            assertNotNull(b, "no sidebar button registered for '" + view + "'");
            assertNotNull(b.getScene(), "'" + view + "' button is not part of the scene");
            assertNotNull(b.getOnAction(), "'" + view + "' button has no onAction handler (FXML handler name typo?)");
        }
        assertNull(mc.getNavButton("vista_inexistente"));

        for (String id : List.of("#secNavegacion", "#secOperaciones", "#secControl", "#secSistema"))
            assertNotNull(lookup(id).query(), id + " debe existir");
        // fresh user prefs: NAVEGACIÓN + OPERACIONES open, CONTROL PATRIMONIAL + SISTEMA folded
        assertTrue(items("#secNavegacion").isManaged());
        assertTrue(items("#secOperaciones").isManaged());
        assertFalse(items("#secControl").isManaged());
        assertFalse(items("#secSistema").isManaged());
    }

    @Test
    void navigating_marksTheButtonActive_andUnfoldsItsSection() {
        MainController mc = MainController.getInstance();
        VBox controlItems = items("#secControl");
        Button resguardos = mc.getNavButton("resguardos");

        interact(() -> mc.navigateToView("resguardos"));

        assertTrue(resguardos.getStyleClass().contains("nav-active"), "the opened page's button is highlighted");
        assertTrue(controlItems.isManaged() && controlItems.isVisible(),
            "its section unfolds so the highlighted button is actually on screen");
        assertTrue(lookup("#secControl").query().getStyleClass().contains("nav-section-active"));
    }

    @Test
    void clickingASectionHeader_foldsItsItems() {
        VBox navItems = items("#secNavegacion");
        assertTrue(navItems.isManaged());
        Node header = lookup("#secNavegacion").query().lookup(".nav-section-header");
        assertNotNull(header);

        interact(() -> Event.fireEvent(header, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
            MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null)));

        // the fold is animated (170 ms) unless animations are off: wait for it
        sleep(400);
        assertFalse(navItems.isManaged(), "a folded section takes no space");
    }

    /**
     * Regression: the collapse-to-icons toggle used to restore the logo padding from a hard-coded copy
     * of the FXML values, while the CSS overrides them — so after collapse + expand the header block
     * (and everything under it) sat ~12 px lower than at start.
     */
    @Test
    void collapsingAndExpandingTheSidebar_restoresTheSameLayout() {
        VBox sidebar = (VBox) lookup("#sidebar").query();
        Node logoArea = sidebar.lookup(".sidebar-logo-area");
        Node userBox = sidebar.lookup(".sidebar-user-box");
        Button toggle = (Button) lookup("#btnToggleSidebar").query();
        sleep(400);
        double logoHeight = logoArea.getBoundsInParent().getHeight();
        double userBoxY = userBox.getBoundsInParent().getMinY();

        interact(toggle::fire);
        sleep(700);
        assertTrue(sidebar.getStyleClass().contains("sidebar-collapsed"));
        assertTrue(userBox.isManaged(), "the account avatar stays in the icon rail");
        assertFalse(lookup("#userInfoVBox").query().isManaged(), "only the name/role text is hidden in the rail");

        interact(toggle::fire);
        sleep(700);
        assertFalse(sidebar.getStyleClass().contains("sidebar-collapsed"));
        assertEquals(logoHeight, logoArea.getBoundsInParent().getHeight(), 0.5, "logo block height after expanding");
        assertEquals(userBoxY, userBox.getBoundsInParent().getMinY(), 0.5, "user card position after expanding");
    }

    private VBox items(String sectionId) {
        return (VBox) ((VBox) lookup(sectionId).query()).getChildren().get(1);
    }
}
