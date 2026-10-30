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
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The screens that fix the data loaded from the Excel files, opened for real. */
class RevisarDatosVentanasTest extends ApplicationTest {

    private Scene escena;

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("revisar-prueba"); u.setUsername("prueba"); u.setNombre("Prueba"); u.setRol(Rol.ADMIN);
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

    private static Producto bien(String id, String codigo, String quien, String area) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Bien " + codigo); p.setResguardante(quien);
        p.setArea(area); p.setStockActual(1);
        return p;
    }

    private DialogPane abierto() throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        return (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
    }

    private static long visibles(DialogPane panel, String clase) {
        return panel.lookupAll(clase).stream().filter(Node::isVisible).count();
    }

    private void clic(Node n) {
        interact(() -> n.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
            false, false, false, false, true, false, false, true, false, false, null)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ── Completar datos ───────────────────────────────────────────────────────

    @Test
    void completar_leeLoQueSeEscribe() {
        assertEquals(new BigDecimal("1250.50"), CompletarDatosDialog.leerPrecio("$1,250.50"));
        assertEquals(new BigDecimal("80"), CompletarDatosDialog.leerPrecio(" 80 "));
        assertNull(CompletarDatosDialog.leerPrecio("0"));
        assertNull(CompletarDatosDialog.leerPrecio("-5"));
        assertNull(CompletarDatosDialog.leerPrecio("abc"));
        assertEquals(10, CompletarDatosDialog.leerVidaUtil(" 10 "));
        assertNull(CompletarDatosDialog.leerVidaUtil("0"));
        assertNull(CompletarDatosDialog.leerVidaUtil("101"));
        assertNull(CompletarDatosDialog.leerVidaUtil("diez"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completar_soloMuestraLoQueFaltaDelArea_yCambiaConElDato() throws Exception {
        Producto conPrecio = bien("1", "TES/1", "Ana", "Tesorería Municipal");
        conPrecio.setPrecioCompra(new BigDecimal("100"));
        Producto sinPrecio = bien("2", "TES/2", "Ana", "Tesorería Municipal");
        Producto deOtra = bien("3", "OP/3", "Ana", "Secretaría de Obras Públicas");
        Platform.runLater(() -> CompletarDatosDialog.show(List.of(conPrecio, sinPrecio, deOtra), null));
        DialogPane panel = abierto();
        assertEquals(0, visibles(panel, ".cnt-fila"), "primero se elige el área");

        ListView<String> areas = (ListView<String>) panel.lookup(".trf-areas");
        interact(() -> areas.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, visibles(panel, ".cnt-fila"), "solo TES/2 no tiene precio");

        ToggleButton sinFecha = (ToggleButton) panel.lookupAll(".filter-chip").stream()
            .filter(n -> n instanceof ToggleButton t && t.getText().startsWith("Sin fecha")).findFirst().orElseThrow();
        assertEquals("Sin fecha (2)", sinFecha.getText());
        interact(sinFecha::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, visibles(panel, ".cnt-fila"));
        assertEquals(2, panel.lookupAll(".date-picker").stream().filter(n -> n.getParent().isVisible() && n.isVisible()).count());
    }

    /** An área that changes under the list must not restart it mid-capture. */
    @Test
    @SuppressWarnings("unchecked")
    void completar_unValorEscritoYEnter_seGuarda_yLaFilaSigueAhi() throws Exception {
        Producto a = bien("1", "TES/1", "Ana", "Tesorería Municipal");
        a.setCategoriaId("cat");
        Producto b = bien("2", "TES/2", "Ana", "Tesorería Municipal");
        b.setCategoriaId("cat");
        Platform.runLater(() -> CompletarDatosDialog.show(List.of(a, b), null));
        DialogPane panel = abierto();
        ListView<String> areas = (ListView<String>) panel.lookup(".trf-areas");
        interact(() -> areas.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        Node fila = panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).toList().get(0);
        TextField campo = (TextField) fila.lookup(".form-input");
        interact(() -> campo.setText("0"));
        for (var tipo : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED))
            interact(() -> javafx.event.Event.fireEvent(campo, new KeyEvent(tipo, "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Tesorería Municipal", areas.getSelectionModel().getSelectedItem(), "sigue en su área");
        assertEquals(2, visibles(panel, ".cnt-fila"), "un valor malo no quita la fila");
        assertTrue(panel.lookupAll(".cell-badge-danger").stream().anyMatch(Node::isVisible), "dice que no se guardó");
    }

    // ── Unir personas ─────────────────────────────────────────────────────────

    @Test
    void unir_proponeElNombreConMasBienes_yMuestraLasDosFormas() throws Exception {
        List<Producto> bienes = List.of(bien("1", "A/1", "RUIZ ANA", "Tesorería Municipal"),
            bien("2", "A/2", "ANA RUIZ", "Tesorería Municipal"), bien("3", "A/3", "ANA RUIZ", "Contraloría Municipal"));
        Platform.runLater(() -> UnirPersonasDialog.show(bienes, List.of(), null));
        DialogPane panel = abierto();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> visibles(panel, ".rsg-tarjeta") == 3);
        RadioButton elegido = (RadioButton) panel.lookupAll(".radio-button").stream()
            .filter(n -> n instanceof RadioButton r && r.isSelected()).findFirst().orElseThrow();
        assertEquals("ANA RUIZ", elegido.getText());
        assertTrue(panel.lookupAll(".field-hint-warn").stream().anyMatch(Node::isVisible), "avisa si están en dos áreas");

        // Neither spelling may be the right one: it can be typed, and empty it can't be saved.
        Button unir = (Button) panel.lookupAll(".btn-primary").stream()
            .filter(n -> n instanceof Button x && x.getText().startsWith("Son la misma")).findFirst().orElseThrow();
        assertFalse(unir.isDisabled());
        TextField otro = (TextField) panel.lookup("#otroNombre");
        assertTrue(otro.isDisabled(), "hasta elegir esa opción");
        RadioButton escribir = (RadioButton) panel.lookupAll(".radio-button").stream()
            .filter(n -> n instanceof RadioButton r && r.getText().startsWith("Ninguno")).findFirst().orElseThrow();
        interact(escribir::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(otro.isDisabled());
        interact(() -> otro.setText("  "));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(unir.isDisabled(), "sin nombre no hay qué unir");
        interact(() -> otro.setText("Ana  Ruiz  Soto"));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(unir.isDisabled());
    }

    @Test
    void unir_sinCasos_loDice() throws Exception {
        Platform.runLater(() -> UnirPersonasDialog.show(List.of(bien("1", "A/1", "ANA RUIZ", "Tesorería Municipal")), List.of(), null));
        DialogPane panel = abierto();
        assertEquals(0, visibles(panel, ".rsg-tarjeta"));
        assertTrue(panel.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText() != null && b.getText().startsWith("Son la misma"))
            .allMatch(Node::isDisabled));
    }

    // ── Etiquetas ─────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void etiquetas_muestraLasDelArea_yMarcarHabilitaImprimir() throws Exception {
        Producto hecho = bien("1", "TES/1", "Ana", "Tesorería Municipal");
        hecho.setEtiquetado(true);
        Producto falta = bien("2", "TES/2", "Ana", "Tesorería Municipal");
        Platform.runLater(() -> EtiquetasAreaDialog.show(List.of(hecho, falta), null));
        DialogPane panel = abierto();
        ListView<String> areas = (ListView<String>) panel.lookup(".trf-areas");
        interact(() -> areas.getSelectionModel().select(0));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, visibles(panel, ".cnt-fila"), "de entrada, las que faltan");
        MenuButton imprimir = (MenuButton) panel.lookup(".menu-button");
        assertTrue(imprimir.isDisabled());
        Node fila = panel.lookupAll(".cnt-fila").stream().filter(Node::isVisible).findFirst().orElseThrow();
        clic(fila);
        assertFalse(imprimir.isDisabled());
    }

    // ── Resguardos en lote ────────────────────────────────────────────────────

    @Test
    void lote_registraCadaPersona_conSusBienes_yUnoQueFallaNoDetieneALosDemas() throws Exception {
        List<Producto> bienes = List.of(bien("1", "A/1", "ANA RUIZ", "Tesorería Municipal"), bien("2", "A/2", "ANA RUIZ", "Tesorería Municipal"),
            bien("3", "A/3", "LUIS SOTO", "Contraloría Municipal"));
        List<String> llamados = new ArrayList<>();
        int[] terminados = {-1};
        Platform.runLater(() -> ResguardosEnLoteDialog.show(bienes, List.of(), escena, (p, items) -> {
            synchronized (llamados) { llamados.add(p.nombre() + ":" + items.size()); }
            if (p.nombre().startsWith("LUIS")) throw new IllegalArgumentException("ya está en otro resguardo");
            Resguardo r = new Resguardo();
            r.setId("r"); r.setNumero("RSG-2026-000099"); r.setResguardanteNombre(p.nombre()); r.setEstado(Resguardo.ESTADO_ACTIVO);
            ResguardoItem it = new ResguardoItem(); it.setProductoCodigo("A/1"); it.setProductoNombre("Bien");
            r.setItems(List.of(it));
            return r;
        }, n -> terminados[0] = n));
        DialogPane panel = abierto();
        Button registrar = (Button) panel.lookupAll(".btn-primary").stream()
            .filter(n -> n instanceof Button b && b.getText().startsWith("Registrar")).findFirst().orElseThrow();
        assertTrue(registrar.isDisabled(), "sin marcar a nadie");
        Hyperlink todas = (Hyperlink) panel.lookupAll(".hyperlink").stream()
            .filter(n -> n instanceof Hyperlink h && h.getText().startsWith("Marcar")).findFirst().orElseThrow();
        interact(todas::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(registrar.isDisabled());

        // runLater: the confirmation waits for its answer on this same thread.
        Platform.runLater(registrar::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane confirmar = (DialogPane) Window.getWindows().stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel)
            .findFirst().orElseThrow().getScene().getRoot();
        ButtonType si = confirmar.getButtonTypes().stream()
            .filter(t -> t.getButtonData().isDefaultButton()).findFirst().orElseThrow();
        Platform.runLater(() -> ((Button) confirmar.lookupButton(si)).fire());

        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> terminados[0] >= 0);
        synchronized (llamados) {
            assertEquals(List.of("ANA RUIZ:2", "LUIS SOTO:1"), llamados, "uno por persona, con sus bienes");
        }
        assertEquals(1, terminados[0], "el que falló no cuenta ni detiene a los demás");

        // The notice of who failed, then the signed-PDF window: each waits for its button.
        for (int i = 0; i < 2; i++) {
            WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Window.getWindows().stream()
                .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel && d != confirmar));
            WaitForAsyncUtils.waitForFxEvents();
            DialogPane aviso = (DialogPane) Window.getWindows().stream()
                .filter(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel && d != confirmar)
                .findFirst().orElseThrow().getScene().getRoot();
            if (i == 0) assertTrue(aviso.getContentText().contains("LUIS SOTO"), "dice quién no se registró");
            Platform.runLater(() -> ((Button) aviso.lookupButton(aviso.getButtonTypes().get(0))).fire());
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> aviso.getScene() == null || !aviso.getScene().getWindow().isShowing());
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    @Test
    void lote_quienTieneOtroNombre_noSePuedeMarcar() throws Exception {
        List<Producto> bienes = List.of(bien("1", "A/1", "ANA RUIZ", "Tesorería Municipal"), bien("2", "A/2", "RUIZ ANA", "Tesorería Municipal"),
            bien("3", "A/3", "LUIS SOTO", "Contraloría Municipal"));
        Platform.runLater(() -> ResguardosEnLoteDialog.show(bienes, List.of(), escena, (p, i) -> null, n -> {}));
        DialogPane panel = abierto();
        Hyperlink todas = (Hyperlink) panel.lookupAll(".hyperlink").stream()
            .filter(n -> n instanceof Hyperlink h && h.getText().startsWith("Marcar")).findFirst().orElseThrow();
        interact(todas::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, panel.lookupAll(".check-box").stream().filter(n -> n.isVisible() && n instanceof CheckBox c && c.isSelected()).count(),
            "solo LUIS SOTO; las otras dos se unen primero");
        assertEquals(2, panel.lookupAll(".check-box").stream().filter(n -> n.isVisible() && n.isDisabled()).count());
    }

    // ── Series ────────────────────────────────────────────────────────────────

    @Test
    void series_muestraLaRepetida_conSusBienes_yAvisaSiNoEsUnaSerie() throws Exception {
        Producto a = bien("1", "A/1", null, "Tesorería Municipal"); a.setNumeroSerie("S/N");
        Producto b = bien("2", "A/2", null, "Tesorería Municipal"); b.setNumeroSerie("s/n");
        Producto c = bien("3", "A/3", null, "Tesorería Municipal"); c.setNumeroSerie("UNICA");
        Platform.runLater(() -> SeriesRepetidasDialog.show(List.of(a, b, c), null));
        DialogPane panel = abierto();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> visibles(panel, ".cnt-fila") == 2);
        assertTrue(panel.lookupAll(".field-hint-warn").stream().anyMatch(Node::isVisible));
    }
}
