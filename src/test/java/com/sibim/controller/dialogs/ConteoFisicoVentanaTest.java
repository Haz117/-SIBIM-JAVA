package com.sibim.controller.dialogs;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.MovimientoService;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The conteo window itself: the whole inventory opens (one set of controls per bien
 *  ran out of memory on 3,060), and a click on a row says the bien is there. */
class ConteoFisicoVentanaTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("conteo-prueba-ventana"); u.setUsername("prueba"); u.setNombre("Prueba"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane(), 200, 100));
        stage.show();
    }

    @AfterEach
    void salir() throws Exception {
        interact(() -> new ArrayList<>(Window.getWindows()).stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane).forEach(Window::hide));
        java.nio.file.Files.deleteIfExists(ConteoFisicoDialog.archivoBorrador());
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private static List<Producto> bienes(int n) {
        List<Producto> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Producto p = new Producto();
            p.setId("c-" + i); p.setCodigo((i % 2 == 0 ? "TES/" : "OP/") + i); p.setNombre("Bien " + i);
            p.setArea(i % 2 == 0 ? "Tesorería Municipal" : "Secretaría de Obras Públicas");
            p.setStockActual(i == 0 ? 5 : 1);
            out.add(p);
        }
        return out;
    }

    private DialogPane abrir(List<Producto> bienes) throws Exception {
        Platform.runLater(() -> ConteoFisicoDialog.show(bienes, new MovimientoService(), () -> {}));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        return (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
    }

    @SuppressWarnings("unchecked")
    private static ListView<String> grupos(DialogPane panel) {
        return (ListView<String>) panel.lookup(".trf-areas");
    }

    private static Button finalizar(DialogPane panel) {
        return (Button) panel.lookupAll(".btn-primary").stream()
            .filter(n -> n instanceof Button b && "Finalizar conteo".equals(b.getText())).findFirst().orElseThrow();
    }

    @Test
    void todoElInventario_abre_ySoloDibujaLasFilasDeLaPantalla() throws Exception {
        DialogPane panel = abrir(bienes(3060));
        ListView<String> grupos = grupos(panel);
        assertEquals("Todo el inventario", grupos.getItems().get(0));
        interact(() -> grupos.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        long filas = panel.lookupAll(".cnt-fila").size();
        assertTrue(filas > 0, "se ve la lista");
        assertTrue(filas < 60, "no una por bien: " + filas);
    }

    @Test
    void alAbrir_nadaElegido_noSeFinaliza_yUnClicEnLaFilaLaMarcaVista() throws Exception {
        DialogPane panel = abrir(bienes(6));
        assertTrue(finalizar(panel).isDisabled(), "no se finaliza un conteo donde nadie vio nada");
        assertEquals(0, panel.lookupAll(".cnt-fila").size(), "primero se elige qué contar");

        ListView<String> grupos = grupos(panel);
        interact(() -> grupos.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(3, panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).count(), "solo los de esa área");

        Node fila = panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).toList().get(1);
        interact(() -> fila.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, true, false, false, null)));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookupAll(".cnt-fila-vista").size() >= 1, "queda marcada");
        assertFalse(finalizar(panel).isDisabled(), "ya hay qué finalizar");
    }

    @Test
    void soloUnLote_muestraLaCantidad_ylasPiezasSueltasNo() throws Exception {
        DialogPane panel = abrir(bienes(4));
        ListView<String> grupos = grupos(panel);
        interact(() -> grupos.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        long conCantidad = panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible)
            .filter(fila -> fila.lookup(".spinner").getParent().isVisible()).count();
        assertEquals(1, conCantidad, "TES/0 es un lote de 5; TES/2 es una pieza");
    }

    @Test
    void alCerrar_elAvanceQuedaEnElBorrador() throws Exception {
        DialogPane panel = abrir(bienes(4));
        ListView<String> grupos = grupos(panel);
        interact(() -> grupos.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        Node fila = panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).toList().get(0);
        interact(() -> fila.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, true, false, false, null)));
        WaitForAsyncUtils.waitForFxEvents();
        // The window's X, the way out that used to ask; the Close button goes the same way.
        Window ventana = panel.getScene().getWindow();
        interact(() -> ventana.fireEvent(new javafx.stage.WindowEvent(ventana, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(ventana.isShowing());
        java.nio.file.Path borrador = ConteoFisicoDialog.archivoBorrador();
        assertTrue(java.nio.file.Files.exists(borrador), "cerrar no pierde lo capturado");
        var guardado = ConteoFisicoDialog.parsearBorrador(java.nio.file.Files.readAllLines(borrador).get(1));
        assertEquals(1, guardado.size(), "solo lo capturado, no los bienes sin tocar");
        assertTrue(guardado.values().iterator().next().verificado());
    }

    /** Finalizing is about what is chosen: the conteo of Tesorería is saved and leaves
     *  the draft, what was marked in Obras Públicas stays there to go on with. */
    @Test
    void alFinalizar_loDeOtraAreaSigueEnElBorrador() throws Exception {
        boolean[] avisado = {false};
        Platform.runLater(() -> ConteoFisicoDialog.show(bienes(4), new MovimientoService(), () -> avisado[0] = true));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane panel = (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
        ListView<String> grupos = grupos(panel);
        for (String area : List.of("Secretaría de Obras Públicas", "Tesorería Municipal")) {
            interact(() -> grupos.getSelectionModel().select(area));
            WaitForAsyncUtils.waitForFxEvents();
            Node fila = panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).toList().get(0);
            interact(() -> fila.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null)));
            WaitForAsyncUtils.waitForFxEvents();
        }
        Button finalizar = finalizar(panel);
        // runLater: the summary waits for its answer on this same thread.
        Platform.runLater(finalizar::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane resumen = (DialogPane) Window.getWindows().stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel)
            .findFirst().orElseThrow().getScene().getRoot();
        Platform.runLater(() -> ((Button) resumen.lookupButton(resumen.getButtonTypes().get(0))).fire());
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> avisado[0]);
        WaitForAsyncUtils.waitForFxEvents();

        java.nio.file.Path borrador = ConteoFisicoDialog.archivoBorrador();
        assertTrue(java.nio.file.Files.exists(borrador), "lo de Obras Públicas no se pierde");
        var guardado = ConteoFisicoDialog.parsearBorrador(java.nio.file.Files.readAllLines(borrador).get(1));
        assertEquals(java.util.Set.of("c-1"), guardado.keySet(), "Tesorería ya salió del borrador");
    }

    /** The label still stuck on the bienes carries the old number: read in "Escanear
     *  etiqueta" it marks its bien, and an old number two bienes had marks one, then the other. */
    @Test
    void escanearElNumeroAnterior_marcaElBien_yLosRepetidosUnoTrasOtro() throws Exception {
        List<Producto> bienes = bienes(6);
        bienes.get(0).setDescripcion("Inventario anterior: PRU-TES-01. Escritorio");
        bienes.get(2).setDescripcion("Inventario anterior: PRU-TES-01.");
        bienes.get(4).setDescripcion("Inventario anterior: PRU- TES-02.");
        DialogPane panel = abrir(bienes);
        ListView<String> grupos = grupos(panel);
        interact(() -> grupos.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        Button escanear = (Button) panel.lookupAll(".button").stream()
            .filter(n -> n instanceof Button b && "Escanear etiqueta".equals(b.getText())).findFirst().orElseThrow();
        Platform.runLater(escanear::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane scan = (DialogPane) Window.getWindows().stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel)
            .findFirst().orElseThrow().getScene().getRoot();
        javafx.scene.control.TextField campo = (javafx.scene.control.TextField) scan.lookup(".text-field");
        Button marcar = (Button) scan.lookupAll(".btn-primary").stream()
            .filter(n -> n instanceof Button b && "Marcar como visto".equals(b.getText())).findFirst().orElseThrow();
        Label resultado = (Label) scan.lookupAll(".muted-sm").stream().filter(n -> n instanceof Label).findFirst().orElseThrow();

        List<String> leidos = new ArrayList<>();
        for (String leido : List.of("pru-tes-01", "PRU-TES-01", "PRU-TES-02")) {
            interact(() -> { campo.setText(leido); marcar.fire(); });
            leidos.add(resultado.getText());
        }
        assertTrue(leidos.get(0).startsWith("✓ TES/0 (antes pru-tes-01)"), leidos.get(0));
        assertTrue(leidos.get(1).startsWith("✓ TES/2 (antes PRU-TES-01)"), "el otro que traía el mismo: " + leidos.get(1));
        assertTrue(leidos.get(2).startsWith("✓ TES/4"), "aunque se escribió con un espacio: " + leidos.get(2));
        assertTrue(leidos.get(2).contains("(3 de 3)"), leidos.get(2));
        interact(() -> { campo.setText("PRU-TES-03"); marcar.fire(); });
        assertTrue(resultado.getText().contains("no está en este conteo"), resultado.getText());
    }

    @Test
    void alMarcarFaltante_laCuentaSigue_yRegresaSiFueUnError() {
        var f = ConteoFisicoDialog.EstadoConteo.FALTANTE;
        var e = ConteoFisicoDialog.EstadoConteo.ENCONTRADO;
        var m = ConteoFisicoDialog.EstadoConteo.MAL_ESTADO;
        assertEquals(0, ConteoFisicoDialog.contadoAlCambiarEstado(e, f, 3, 3));
        assertEquals(3, ConteoFisicoDialog.contadoAlCambiarEstado(f, e, 0, 3));
        assertEquals(2, ConteoFisicoDialog.contadoAlCambiarEstado(e, m, 2, 3), "otra incidencia no toca la cuenta");
    }
}
