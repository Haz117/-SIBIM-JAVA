package com.sibim.controller.dialogs;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Picking bienes for a baja format and the person for Cambiar resguardante. */
class ElegirDialogosVentanaTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("elegir-prueba"); u.setUsername("prueba"); u.setNombre("Prueba"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        stage.setScene(new Scene(new Pane(), 200, 100));
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

    private static List<Producto> inventario() {
        return List.of(bien("p-10", "TES/10", "Ana García"), bien("p-2", "TES/2", "ANA GARCIA"), bien("p-3", "OP/3", "Beto Luna"));
    }

    private DialogPane panelAbierto() throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        return (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
    }

    private void enter(Node nodo) {
        for (var tipo : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED))
            interact(() -> javafx.event.Event.fireEvent(nodo, new KeyEvent(tipo, "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void bienes_codigoYEnter_marcaYDejaLaBusquedaLista_ySalenPorNumero() throws Exception {
        AtomicReference<List<Producto>> elegidos = new AtomicReference<>();
        Platform.runLater(() -> elegidos.set(ElegirBienesDialog.show("Solicitud de baja", "prueba", inventario())));
        DialogPane panel = panelAbierto();
        TextField buscar = (TextField) panel.lookup("#buscarBien");
        Button generar = (Button) panel.lookupButton(ButtonType.OK);
        assertTrue(generar.isDisabled());

        interact(() -> buscar.setText("tes/10"));
        WaitForAsyncUtils.waitForFxEvents();
        enter(buscar);
        assertEquals("", buscar.getText(), "lista para el siguiente");
        assertTrue(panel.getScene().getWindow().isShowing(), "Enter con texto elige, no genera");
        assertFalse(generar.isDisabled());

        interact(() -> buscar.setText("tes/2"));
        WaitForAsyncUtils.waitForFxEvents();
        enter(buscar);
        enter(buscar);   // with nothing typed, Enter generates
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> elegidos.get() != null);
        assertEquals(List.of("TES/2", "TES/10"), elegidos.get().stream().map(Producto::getCodigo).toList());
    }

    @Test
    void bienes_alCancelar_noDevuelveNada_yNoOfreceBajas() throws Exception {
        Producto baja = bien("p-9", "TES/9", "Ana García");
        baja.setFechaBaja(java.time.LocalDate.of(2026, 10, 1));
        AtomicReference<List<Producto>> elegidos = new AtomicReference<>();
        Platform.runLater(() -> elegidos.set(ElegirBienesDialog.show("Solicitud de baja", "prueba", List.of(baja, bien("p-1", "TES/1", null)))));
        DialogPane panel = panelAbierto();
        assertEquals(1, panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).count(), "ya dado de baja no se ofrece");
        interact(((Button) panel.lookupButton(ButtonType.CANCEL))::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> elegidos.get() != null);
        assertTrue(elegidos.get().isEmpty());
    }

    @Test
    void persona_usaElNombreComoLoTieneElInventario() throws Exception {
        AtomicReference<Optional<String>> elegida = new AtomicReference<>();
        Platform.runLater(() -> elegida.set(ElegirPersonaDialog.show(inventario(), 3)));
        DialogPane panel = panelAbierto();
        TextField buscar = (TextField) panel.lookup("#buscarPersona");
        assertTrue(((Button) panel.lookupButton(ButtonType.OK)).isDisabled(), "primero se elige a alguien");
        interact(() -> buscar.setText("garcia"));
        WaitForAsyncUtils.waitForFxEvents();
        interact(((Button) panel.lookupButton(ButtonType.OK))::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> elegida.get() != null);
        assertEquals(Optional.of("Ana García"), elegida.get(), "no «garcia» ni una tercera forma de escribirlo");
    }

    @Test
    void persona_nuevaSoloSiNadieCoincide() throws Exception {
        AtomicReference<Optional<String>> elegida = new AtomicReference<>();
        Platform.runLater(() -> elegida.set(ElegirPersonaDialog.show(inventario(), 1)));
        DialogPane panel = panelAbierto();
        TextField buscar = (TextField) panel.lookup("#buscarPersona");
        interact(() -> buscar.setText("Carla  Nueva"));
        WaitForAsyncUtils.waitForFxEvents();
        Button nueva = (Button) panel.lookupAll(".btn-secondary").stream()
            .filter(n -> n instanceof Button b && b.getText() != null && b.getText().startsWith("Persona nueva")).findFirst().orElseThrow();
        assertTrue(nueva.isVisible());
        Button asignar = (Button) panel.lookupButton(ButtonType.OK);
        assertTrue(asignar.isDisabled(), "hasta decir que sí es nueva");
        interact(nueva::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(asignar.isDisabled());
        interact(asignar::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> elegida.get() != null);
        assertEquals(Optional.of("Carla Nueva"), elegida.get());

        interact(() -> {});
        AtomicReference<Optional<String>> otra = new AtomicReference<>();
        Platform.runLater(() -> otra.set(ElegirPersonaDialog.show(inventario(), 1)));
        DialogPane panel2 = panelAbierto();
        TextField buscar2 = (TextField) panel2.lookup("#buscarPersona");
        interact(() -> buscar2.setText("garcia"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel2.lookupAll(".btn-secondary").stream()
            .filter(n -> n instanceof Button b && b.getText() != null && b.getText().startsWith("Persona nueva"))
            .noneMatch(Node::isVisible), "si alguien coincide no invita a escribirlo de otra forma");
    }
}
