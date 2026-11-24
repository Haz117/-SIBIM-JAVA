package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import com.sibim.util.TutorialOverlay;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

/** «Repisa» in the side menu (24-nov-2026): what the window's height doesn't hold goes to «Más»,
 *  in its order; nothing is behind a scroll bar; the page in use always stays; «Más» leaves when
 *  everything fits again. */
class RepisaLateralTest extends ControllerSmokeTestBase {

    private Stage stage;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        System.setProperty(SidebarSections.PREFS_NODE_PROPERTY, "sibim/test/sidebar/repisa");
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        Preferences.userNodeForPackage(TutorialOverlay.class).putBoolean("tutorial.v6." + adminUser().getUsername(), true);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/main.fxml")).load();
        Scene scene = new Scene(root, 1500, 1000);           // wide: the menu with its text, not the rail
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.setWidth(1500);
        stage.setHeight(1000);
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

    private ScrollPane menu() { return lookup(".sidebar-scroll").queryAs(ScrollPane.class); }

    private boolean cabe() {
        ScrollPane sp = menu();
        return sp.getContent().getLayoutBounds().getHeight() <= sp.getViewportBounds().getHeight() + 0.5;
    }

    private void alto(double h) {
        interact(() -> stage.setHeight(h));
        WaitForAsyncUtils.waitForFxEvents();
        sleep(400);                                            // the debounce of the menu's own changes
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static List<String> enMas(RepisaLateral r) {
        return r.enMas().stream().map(e -> e.item().view()).toList();
    }

    @Test
    void loQueNoCabeVaAMas_enSuOrden_laActivaSeQueda_yRegresaAlCrecer() throws Exception {
        RepisaLateral repisa = MainController.getInstance().repisa();
        Node fila = lookup(".nav-mas-fila").query();
        alto(1000);
        assertFalse(fila.isManaged(), "todo cabe: sin «Más»");
        assertTrue(cabe());

        alto(600);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, fila::isManaged);
        assertTrue(cabe(), "sin barra de desplazamiento");
        List<String> ocultas = enMas(repisa);
        assertFalse(ocultas.isEmpty());
        List<String> orden = List.of("dashboard", "organigrama", "productos", "categorias", "movimientos", "alertas", "conteo",
            "reportes", "depreciacion", "resguardos", "bajas", "prestamos", "comodatos", "actas", "configuracion", "auditoria");
        assertEquals(orden.subList(orden.size() - ocultas.size(), orden.size()), ocultas, "las de abajo, en su orden");
        assertFalse(ocultas.contains("dashboard"), "la página en uso se queda");
        String cuenta = from(fila).lookup(".nav-mas-cuenta").queryLabeled().getText();
        assertEquals(String.valueOf(ocultas.size()), cuenta);

        // Opening one that was in «Más»: it comes to the menu (and something else leaves).
        interact(() -> MainController.getInstance().navigateToView("auditoria"));
        WaitForAsyncUtils.waitForFxEvents();
        sleep(400);
        WaitForAsyncUtils.waitForFxEvents();
        Button auditoria = MainController.getInstance().getNavButton("auditoria");
        assertTrue(auditoria.getStyleClass().contains("nav-active"));
        assertFalse(repisa.estaEnMas(auditoria), "la página en uso siempre se ve");
        assertFalse(enMas(repisa).contains("auditoria"));
        assertTrue(cabe());

        alto(1000);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !fila.isManaged());
        assertTrue(enMas(repisa).isEmpty(), "todo regresa a su lugar");
        assertTrue(cabe());
    }

    @Test
    void elTutorialSenalaMas_cuandoLaOpcionEstaAhi() throws Exception {
        alto(560);
        RepisaLateral repisa = MainController.getInstance().repisa();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !repisa.enMas().isEmpty());
        String ultima = repisa.enMas().get(repisa.enMas().size() - 1).item().view();
        assertSame(repisa.boton(), MainController.getInstance().getNavButton(ultima));
    }

    @Test
    void unArea_nuncaVeEnMasLoQueNoLeToca() throws Exception {
        interact(() -> {
            // The admin's window of start() stops before the área's replaces it: left running, its Inicio
            // went on refreshing after the demo was switched off by later tests and, retrying a database
            // that isn't there, held the connection's lock (the suite of 24-nov-2026 stopped there).
            MainController.getInstance().stopTimers();
            Usuario area = new Usuario();
            area.setId("area-repisa"); area.setUsername("area-repisa"); area.setNombre("Área de prueba"); area.setRol(Rol.DIRECCION);
            SessionManager.setCurrentUser(area);
            Preferences.userNodeForPackage(TutorialOverlay.class).putBoolean("tutorial.v6.area-repisa", true);
            try {
                Parent root = new FXMLLoader(getClass().getResource("/fxml/main.fxml")).load();
                Scene s = new Scene(root, 1500, 420);
                s.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
                stage.setScene(s);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        alto(420);
        RepisaLateral repisa = MainController.getInstance().repisa();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !repisa.enMas().isEmpty());
        assertTrue(repisa.enMas().stream().noneMatch(e -> e.item().adminOnly()), repisa.enMas().toString());
        assertTrue(cabe());
        Preferences.userNodeForPackage(TutorialOverlay.class).remove("tutorial.v6.area-repisa");
    }
}
