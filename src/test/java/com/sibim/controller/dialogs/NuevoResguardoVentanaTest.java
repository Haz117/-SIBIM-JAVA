package com.sibim.controller.dialogs;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Nuevo Resguardo: picking the person marks what they hold, Enter in a search never
 *  uses up a folio, and a failed save keeps the window with what was marked. */
class NuevoResguardoVentanaTest extends ApplicationTest {

    private Scene escena;
    private final List<List<ResguardoItem>> registrados = new ArrayList<>();
    private final AtomicReference<Resguardo> creado = new AtomicReference<>();
    private String fallaCon = null;

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("nuevo-resguardo-prueba"); u.setUsername("prueba"); u.setNombre("Prueba"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        escena = new Scene(new Pane(), 200, 100);
        stage.setScene(escena);
        stage.show();
    }

    @AfterEach
    void salir() {
        interact(() -> new ArrayList<>(Window.getWindows()).stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane).forEach(Window::hide));
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private static Producto bien(String id, String codigo, String quien) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Bien " + codigo); p.setResguardante(quien);
        p.setArea("Tesorería Municipal"); p.setStockActual(1);
        return p;
    }

    private DialogPane abrir(String persona) throws Exception {
        Resguardo vigente = new Resguardo();
        vigente.setId("r-1"); vigente.setNumero("RSG-2026-000001"); vigente.setResguardanteNombre("Beto Luna");
        vigente.setEstado(Resguardo.ESTADO_ACTIVO);
        ResguardoItem it = new ResguardoItem(); it.setProductoId("p-3");
        vigente.setItems(List.of(it));
        List<Producto> bienes = List.of(bien("p-2", "TES/2", "Ana García"), bien("p-10", "TES/10", "ANA GARCIA"),
            bien("p-3", "TES/3", "Beto Luna"), bien("p-4", "TES/4", "García Ana"));
        Platform.runLater(() -> NuevoResguardoDialog.show(bienes, List.of(vigente), persona, escena,
            (nombre, cargo, area, items, obs) -> {
                if (fallaCon != null) throw new IllegalArgumentException(fallaCon);
                registrados.add(items);
                Resguardo r = new Resguardo();
                r.setNumero("RSG-2026-000099"); r.setResguardanteNombre(nombre); r.setEstado(Resguardo.ESTADO_ACTIVO);
                return r;
            }, creado::set));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        return (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
    }

    private static Button guardar(DialogPane panel) { return (Button) panel.lookupButton(ButtonType.OK); }

    private static long filas(DialogPane panel, String clase) {
        return panel.lookupAll(clase).stream().filter(Node::isVisible).count();
    }

    @Test
    void alElegirLaPersona_susBienesSinResguardo_quedanMarcados_ySeRegistran() throws Exception {
        DialogPane panel = abrir("Ana García");
        assertEquals(2, filas(panel, ".rsg-fila-sel"), "los dos a su nombre, se haya escrito como se haya escrito");
        assertFalse(guardar(panel).isDisabled());

        interact(guardar(panel)::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> creado.get() != null);
        assertEquals(List.of("TES/2", "TES/10"), registrados.get(0).stream().map(ResguardoItem::getProductoCodigo).toList());
        assertFalse(panel.getScene() != null && panel.getScene().getWindow().isShowing(), "se cierra al guardar");
    }

    /** After saving, the screens ask about the PDF in a window of their own. Opened while
     *  this window's event loop was unwinding, the whole app stopped responding. */
    @Test
    void trasGuardar_seAbreOtraVentana_yElSistemaSigueRespondiendo() throws Exception {
        Producto b = bien("p-1", "TES/1", "Ana García");
        boolean[] preguntado = {false};
        Platform.runLater(() -> NuevoResguardoDialog.show(List.of(b), List.of(), "Ana García", escena,
            (nombre, cargo, area, items, obs) -> {
                Resguardo r = new Resguardo();
                r.setNumero("RSG-2026-000099");
                return r;
            },
            r -> {
                preguntado[0] = true;
                javafx.scene.control.Dialog<ButtonType> pregunta = new javafx.scene.control.Dialog<>();
                pregunta.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                pregunta.showAndWait();
            }));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane panel = (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
        interact(guardar(panel)::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> preguntado[0]);

        // A round trip through the FX thread: it never came back when the app froze.
        java.util.concurrent.CompletableFuture<Boolean> responde = new java.util.concurrent.CompletableFuture<>();
        Platform.runLater(() -> responde.complete(true));
        assertTrue(responde.get(10, TimeUnit.SECONDS));
        Platform.runLater(() -> new ArrayList<>(Window.getWindows()).stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane).forEach(Window::hide));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void sinPersona_noSeGuarda_yEnterEnLaBusquedaNoGastaUnFolio() throws Exception {
        DialogPane panel = abrir(null);
        assertTrue(guardar(panel).isDisabled());
        TextField buscar = (TextField) panel.lookup("#buscarPersona");
        interact(() -> buscar.setText("garcia"));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(guardar(panel).isDisabled(), "la primera coincidencia, sin importar el acento");
        for (var tipo : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED))
            interact(() -> javafx.event.Event.fireEvent(buscar,
                new KeyEvent(tipo, "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.getScene().getWindow().isShowing());
        assertTrue(registrados.isEmpty(), "registrar un resguardo pide el botón");
    }

    @Test
    void unBienEnOtroResguardoVigente_diceCual_yNoSeMarca() throws Exception {
        DialogPane panel = abrir("Ana García");
        interact(() -> panel.lookupAll(".filter-chip").stream()
            .filter(n -> n instanceof javafx.scene.control.ToggleButton t && t.getText().startsWith("Todos"))
            .forEach(n -> ((javafx.scene.control.ToggleButton) n).fire()));
        WaitForAsyncUtils.waitForFxEvents();
        Node ocupada = panel.lookup(".rsg-fila-ocupada");
        assertNotNull(ocupada, "TES/3 está en RSG-2026-000001");
        assertTrue(ocupada.lookup(".check-box").isDisabled());
    }

    /** Two words, in any order and without accents, narrow the list of bienes. */
    @Test
    void elBuscadorDeBienes_filtra_conDosPalabrasYSinAcentos() throws Exception {
        DialogPane panel = abrir("Ana García");
        interact(() -> panel.lookupAll(".filter-chip").stream()
            .filter(n -> n instanceof javafx.scene.control.ToggleButton t && t.getText().startsWith("Todos"))
            .forEach(n -> ((javafx.scene.control.ToggleButton) n).fire()));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(4, filas(panel, ".cnt-fila"));
        TextField buscar = (TextField) panel.lookup("#buscarBien");
        interact(() -> buscar.setText("garcia bien"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(3, filas(panel, ".cnt-fila"), "los tres a nombre de García, en cualquier orden");
        interact(() -> buscar.setText("tes/10"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, filas(panel, ".cnt-fila"));
    }

    @Test
    void conUnNombreQueNoEsta_ofreceRegistrarlaComoNueva() throws Exception {
        DialogPane panel = abrir(null);
        TextField buscar = (TextField) panel.lookup("#buscarPersona");
        interact(() -> buscar.setText("Carla   Nueva"));
        WaitForAsyncUtils.waitForFxEvents();
        Button nueva = (Button) panel.lookupAll(".btn-secondary").stream()
            .filter(n -> n instanceof Button b && b.getText() != null && b.getText().startsWith("Persona nueva")).findFirst().orElseThrow();
        assertTrue(nueva.isVisible());
        assertTrue(guardar(panel).isDisabled(), "aún sin bienes");
        interact(nueva::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Persona nueva: Carla Nueva", nueva.getText());
        assertEquals(1, panel.lookupAll(".org-area-name").stream()
            .filter(n -> n instanceof javafx.scene.control.Label l && "Carla Nueva".equals(l.getText())).count());
    }

    @Test
    void siFallaAlGuardar_laVentanaSigue_conLoMarcado() throws Exception {
        DialogPane panel = abrir("Ana García");
        fallaCon = "El bien TES/2 ya está en el resguardo vigente RSG-2026-000050";
        interact(guardar(panel)::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !guardar(panel).isDisabled());
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.getScene().getWindow().isShowing());
        assertEquals(2, filas(panel, ".rsg-fila-sel"));
        assertTrue(panel.lookupAll(".field-error-label").stream().anyMatch(n -> n.isVisible()
            && ((javafx.scene.control.Label) n).getText().contains("RSG-2026-000050")));
    }

    @Test
    void lasMismasPalabrasEnOtroOrden_sonProbablementeLaMismaPersona() {
        assertTrue(com.sibim.service.ResguardoService.mismasPalabras("ANA RUIZ SOTO", "Ruiz Soto  Ana"));
        assertFalse(com.sibim.service.ResguardoService.mismasPalabras("ANA RUIZ SOTO", "ANA RUIZ"));
    }

    @Test
    void conLaMismaPersonaEnOtroOrden_avisa_yPuedeAgregarSusBienes() throws Exception {
        DialogPane panel = abrir("Ana García");
        var enlace = panel.lookupAll(".hyperlink").stream()
            .filter(n -> n instanceof javafx.scene.control.Hyperlink h && h.getText().startsWith("Si es la misma")).findFirst().orElseThrow();
        assertTrue(enlace.isVisible(), "también aparece como García Ana");
        interact(((javafx.scene.control.Hyperlink) enlace)::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(3, filas(panel, ".rsg-fila-sel"));
    }
}
