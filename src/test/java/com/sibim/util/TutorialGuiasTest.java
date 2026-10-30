package com.sibim.util;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The guides: each one must point at things that exist, and only offer an account what it
 *  can do; and the overlay that shows them must keep answering the keyboard. */
class TutorialGuiasTest extends ApplicationTest {

    private StackPane raiz;

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        raiz = new StackPane();
        Scene s = new Scene(raiz, 1280, 800);
        s.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
        stage.setScene(s);
        stage.show();
    }

    @AfterEach
    void salir() {
        interact(() -> {
            raiz.getScene().getRoot().requestFocus();
            javafx.event.Event.fireEvent(raiz, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false));
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(350, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private static void entrarComo(Rol rol) {
        Usuario u = new Usuario();
        u.setId("guias-" + rol); u.setUsername("guias-" + rol); u.setNombre("Ana Prueba"); u.setRol(rol);
        SessionManager.setCurrentUser(u);
    }

    private static List<TutorialGuias.Guia> todas() {
        List<TutorialGuias.Guia> g = new ArrayList<>(TutorialGuias.disponibles());
        g.add(TutorialGuias.novedades());
        return g;
    }

    /** A guide naming an icon or a button that isn't there would fail at the worst moment. */
    @Test
    void cadaPaso_tieneIconoValido_yLoQueMarcaExisteEnSuPantalla() throws Exception {
        for (Rol rol : new Rol[]{Rol.ADMIN, Rol.DIRECCION}) {
            entrarComo(rol);
            for (TutorialGuias.Guia g : todas()) {
                assertFalse(g.pasos().isEmpty(), g.id());
                new FontIcon(g.icon());
                for (TutorialGuias.Paso p : g.pasos()) {
                    new FontIcon(p.icon());
                    assertTrue(p.bullets().length > 0, p.title());
                    for (String b : p.bullets()) assertFalse(b.isBlank(), p.title());
                    if (p.foco() != null && p.foco().startsWith("#")) {
                        assertNotNull(p.navigateId(), p.title() + ": lo que marca está en una pantalla");
                        String id = p.foco().substring(1).split(" ")[0];
                        try (InputStream in = getClass().getResourceAsStream("/fxml/" + p.navigateId() + ".fxml")) {
                            String fxml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            assertTrue(fxml.contains("fx:id=\"" + id + "\""), p.title() + ": no existe " + id + " en " + p.navigateId());
                        }
                    }
                    if (p.accion() != null) assertNotNull(p.accionTexto(), p.title());
                }
            }
        }
    }

    @Test
    void unArea_soloVeLoQuePuedeHacer() {
        entrarComo(Rol.DIRECCION);
        List<String> ids = TutorialGuias.disponibles().stream().map(TutorialGuias.Guia::id).toList();
        assertTrue(ids.containsAll(List.of("general", "mis-bienes", "resguardo-persona", "pedir-baja", "recibir")), ids.toString());
        for (String deAdmin : List.of("registrar-resguardo", "conteo", "completar", "personas-series", "etiquetas", "lote", "transferir", "baja", "formatos"))
            assertFalse(ids.contains(deAdmin), deAdmin);
        String texto = todas().stream().flatMap(g -> g.pasos().stream()).flatMap(p -> java.util.Arrays.stream(p.bullets())).reduce("", String::concat);
        assertFalse(texto.contains("Registrar como resguardo"), "un área no ve ese botón");
    }

    @Test
    void nadaDiceLoQueYaCambio() {
        entrarComo(Rol.ADMIN);
        String texto = todas().stream().flatMap(g -> g.pasos().stream()).flatMap(p -> java.util.Arrays.stream(p.bullets())).reduce("", (a, b) -> a + "\n" + b);
        for (String viejo : List.of("Crea las categorías PRIMERO", "kanban", "Saltar tutorial", "Cargar los que tiene a su nombre", "Guardar borrador"))
            assertFalse(texto.contains(viejo), viejo);
    }

    private long textos(String contenido) {
        return raiz.lookupAll(".label").stream()
            .filter(n -> n instanceof Label l && l.getText() != null && l.getText().contains(contenido) && n.isVisible()).count();
    }

    @Test
    void elMenu_abreUnaGuia_lasFlechasAvanzan_yEscCierra() throws Exception {
        entrarComo(Rol.ADMIN);
        Platform.runLater(() -> TutorialOverlay.mostrarMenu(raiz, false));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> raiz.lookup(".tutorial-menu") != null);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(TutorialGuias.disponibles().size() + 1, raiz.lookupAll(".tutorial-guia").size());

        // F2 again with it open: it stacked a second one on top.
        interact(() -> TutorialOverlay.mostrarMenu(raiz, false));
        assertEquals(1, raiz.lookupAll(".tutorial-menu").size());

        Node conteo = raiz.lookupAll(".tutorial-guia").stream().filter(n -> "conteo".equals(n.getUserData())).findFirst().orElseThrow();
        interact(() -> conteo.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
            javafx.scene.input.MouseButton.PRIMARY, 1, false, false, false, false, true, false, false, true, false, false, null)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> raiz.lookup(".tutorial-card") != null);
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(raiz.lookup(".tutorial-menu"), "el menú se cierra al abrir la guía");
        assertTrue(textos("PASO 1 DE 4") > 0);
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);

        // On the scene, not on the card: a screen that opens takes the focus off it.
        interact(() -> raiz.requestFocus());
        interact(() -> javafx.event.Event.fireEvent(raiz, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT, false, false, false, false)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> textos("PASO 2 DE 4") > 0);
        WaitForAsyncUtils.sleep(600, TimeUnit.MILLISECONDS);
        interact(() -> javafx.event.Event.fireEvent(raiz, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> raiz.lookup(".tutorial-card") == null);
    }

    /** The step with something to try says so, and trying it remembers the guide as seen. */
    @Test
    void elUltimoPaso_ofreceProbarlo_yQuedaMarcadaComoVista() throws Exception {
        entrarComo(Rol.ADMIN);
        boolean[] abierta = {false};
        TutorialGuias.Paso paso = new TutorialGuias.Paso("mdi2b-book-open-outline", "#A2232D", "#5A141A", "Prueba", null, null, null,
            "Abrir la prueba", () -> abierta[0] = true, new String[]{"Algo que hacer"});
        TutorialGuias.Guia guia = new TutorialGuias.Guia("prueba-" + System.nanoTime(), "Prueba", "Prueba", "mdi2b-book-open-outline", List.of(paso));
        Platform.runLater(() -> TutorialOverlay.buildAndShow(raiz, guia));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> raiz.lookup(".tutorial-btn-probar") != null);
        WaitForAsyncUtils.waitForFxEvents();
        Button probar = (Button) raiz.lookup(".tutorial-btn-probar");
        assertTrue(probar.isVisible());
        assertEquals("Probarlo ahora: Abrir la prueba", probar.getText());
        interact(probar::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> abierta[0]);
        assertNull(raiz.lookup(".tutorial-card"), "la guía se quita para dejar ver la ventana");
    }
}
