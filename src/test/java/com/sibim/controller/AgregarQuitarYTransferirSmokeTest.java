package com.sibim.controller;

import com.sibim.controller.dialogs.MovimientoDialogFactory;
import com.sibim.controller.dialogs.TransferenciaArrastreDialog;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Movimientos has two actions: "Transferir arrastrando" and "Agregar o quitar".
 *  The general form (entrada, salida, ajuste o transferencia) is gone. */
class AgregarQuitarYTransferirSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        stage.setScene(new Scene(new StackPane(), 400, 300));
        stage.show();
    }

    @Test
    @SuppressWarnings("unchecked")
    void agregarOQuitar_soloSumaOResta() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        // Even asked for a transfer (an old shortcut), it opens on Agregar.
        Platform.runLater(() -> MovimientoDialogFactory.show(productos, null, TipoMovimiento.TRANSFERENCIA));
        ComboBox<TipoMovimiento> tipo = (ComboBox<TipoMovimiento>) esperar(n -> n instanceof ComboBox<?> c
            && !c.getItems().isEmpty() && c.getItems().get(0) instanceof TipoMovimiento);
        assertEquals(List.of(TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA), tipo.getItems());
        assertEquals(TipoMovimiento.ENTRADA, tipo.getValue());
        assertEquals("Agregar (suma a la cantidad)", tipo.getConverter().toString(TipoMovimiento.ENTRADA));
        interact(() -> ((Stage) tipo.getScene().getWindow()).close());
    }

    /** The "Agregar o quitar" button opens it with no tipo at all. */
    @Test
    @SuppressWarnings("unchecked")
    void agregarOQuitar_abreSinTipoElegido() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        Platform.runLater(() -> MovimientoDialogFactory.show(productos, null, null));
        ComboBox<TipoMovimiento> tipo = (ComboBox<TipoMovimiento>) esperar(n -> n instanceof ComboBox<?> c
            && !c.getItems().isEmpty() && c.getItems().get(0) instanceof TipoMovimiento);
        assertEquals(TipoMovimiento.ENTRADA, tipo.getValue());
        interact(() -> ((Stage) tipo.getScene().getWindow()).close());
    }

    /** Bienes › Transferir opens the board with the selected bienes already chosen. */
    @Test
    @SuppressWarnings("unchecked")
    void transferirDesdeBienes_abreConLosBienesElegidos() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        Producto uno = productos.stream().filter(p -> p.getArea() != null && p.getFechaBaja() == null).findFirst().orElseThrow();
        List<Producto> mismaArea = productos.stream()
            .filter(p -> uno.getArea().equals(p.getArea()) && p.getFechaBaja() == null).limit(2).toList();
        List<String> ids = mismaArea.stream().map(Producto::getId).toList();

        Platform.runLater(() -> TransferenciaArrastreDialog.show(productos, ids, () -> {}));
        ListView<Producto> lista = (ListView<Producto>) esperar(n -> n instanceof ListView<?> l
            && l.getStyleClass().contains("trf-lista") && !l.getItems().isEmpty());
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        assertEquals(ids.size(), lookup(".trf-lista .check-box").queryAll().stream()
            .filter(c -> ((javafx.scene.control.CheckBox) c).isSelected()).count(), "ya salen marcados");
        assertTrue(lista.getItems().stream().allMatch(p -> uno.getArea().equals(p.getArea())), "su área es el origen");
        assertTrue(lookup(n -> n instanceof javafx.scene.control.Label l && l.isVisible()
            && l.getText() != null && l.getText().contains("de otra área")).tryQuery().isEmpty());
        interact(() -> ((Stage) lista.getScene().getWindow()).close());
    }

    /** Chosen from two áreas: the board opens on the first and says the rest go apart. */
    @Test
    void transferirDesdeBienes_deDosAreas_avisaQueLosOtrosVanAparte() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        List<Producto> activos = productos.stream().filter(p -> p.getArea() != null && p.getFechaBaja() == null).toList();
        Producto a = activos.get(0);
        Producto b = activos.stream().filter(p -> !a.getArea().equals(p.getArea())).findFirst().orElseThrow();

        Platform.runLater(() -> TransferenciaArrastreDialog.show(productos, List.of(a.getId(), b.getId()), () -> {}));
        Node aviso = esperar(n -> n instanceof javafx.scene.control.Label l && l.isVisible()
            && l.getText() != null && l.getText().contains("de otra área"));
        assertTrue(((javafx.scene.control.Label) aviso).getText().startsWith("1 de los bienes que elegiste es"));
        interact(() -> ((Stage) aviso.getScene().getWindow()).close());
    }

    /** Opened from Movimientos: step 1 lists the áreas with bienes, the cards are
     *  grouped by secretaría and stay dimmed until a bien is selected; after a
     *  transfer it is listed at the bottom with its números. */
    @Test
    @SuppressWarnings("unchecked")
    void transferir_eligeOrigenDeLaLista_yAnotaLoTransferido() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        Platform.runLater(() -> TransferenciaArrastreDialog.show(productos, () -> {}));
        try {
            ListView<String> areas = (ListView<String>) esperar(n -> n instanceof ListView<?> l
                && l.getStyleClass().contains("trf-areas") && l.isVisible() && !l.getItems().isEmpty());
            esperar(n -> n.getStyleClass().contains("trf-grupo"));
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            ListView<Producto> lista = (ListView<Producto>) lookup(".trf-lista").query();
            assertFalse(lista.isVisible(), "los bienes salen hasta elegir el origen");
            assertFalse(lookup(".trf-tarjeta").queryAll().isEmpty());
            assertTrue(lookup(".trf-tarjeta").queryAll().stream()
                .allMatch(t -> t.getStyleClass().contains("trf-tarjeta-inactiva")), "sin bienes elegidos, esperan");
            List<String> grupos = lookup(".trf-grupo").queryAll().stream()
                .map(n -> ((javafx.scene.control.Label) n).getText()).toList();
            assertEquals(com.sibim.config.Areas.PRESIDENCIA.toUpperCase(), grupos.get(0));
            assertTrue(grupos.contains(com.sibim.config.Areas.secretarias().get(0).nombre().toUpperCase()));

            String area = areas.getItems().get(0);
            interact(() -> {
                areas.getSelectionModel().select(0);
                areas.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
            });
            assertTrue(lista.isVisible());
            assertFalse(areas.isVisible());
            assertTrue(lista.getItems().stream().allMatch(p -> area.equals(p.getArea())));
            assertTrue(lookup(".org-area-name").queryAll().stream()
                .noneMatch(n -> area.equals(((javafx.scene.control.Label) n).getText())), "su propia área no es destino");

            Producto bien = lista.getItems().stream().filter(p -> p.getStockActual() > 0).findFirst().orElseThrow();
            javafx.scene.control.TextField buscarBien = (javafx.scene.control.TextField) lookup(n ->
                n instanceof javafx.scene.control.TextField t && t.getPromptText() != null
                    && t.getPromptText().startsWith("Código y Enter")).query();
            marcarConEnter(buscarBien, bien.getCodigo());
            assertEquals("", buscarBien.getText(), "queda listo para el siguiente código");
            Node destino = lookup(".trf-tarjeta").queryAll().iterator().next();
            assertFalse(destino.getStyleClass().contains("trf-tarjeta-inactiva"));

            Platform.runLater(() -> destino.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null)));
            javafx.scene.control.TextField motivo = (javafx.scene.control.TextField) esperar(n ->
                n instanceof javafx.scene.control.TextField t && "Ej. reasignación, cambio de oficina".equals(t.getPromptText()));
            interact(() -> motivo.setText("Prueba"));
            // Before confirming it shows the número it will take there.
            assertTrue(lookup(".trf-codigo-nuevo").tryQuery().isPresent());
            javafx.scene.control.Button ok = (javafx.scene.control.Button) lookup(n ->
                n instanceof javafx.scene.control.Button b && "Transferir".equals(b.getText())).query();
            Platform.runLater(ok::fire);

            javafx.scene.control.Label titulo = (javafx.scene.control.Label) esperar(n ->
                n instanceof javafx.scene.control.Label l && l.getText() != null
                    && l.getText().startsWith("Transferidos en esta ventana"));
            assertEquals("Transferidos en esta ventana: 1 bien", titulo.getText());
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            List<String> textos = lookup(".trf-hecho .label").queryAll().stream()
                .map(n -> ((javafx.scene.control.Labeled) n).getText()).toList();
            assertTrue(textos.stream().anyMatch(t -> t != null && t.startsWith(bien.getCodigo() + " → ")),
                bien.getCodigo() + " en " + textos);

            // Dropped on the wrong área: Deshacer takes it back with its own número.
            javafx.scene.control.Button deshacer = (javafx.scene.control.Button) lookup(n ->
                n instanceof javafx.scene.control.Button b && "Deshacer".equals(b.getText())).query();
            Platform.runLater(deshacer::fire);
            javafx.scene.control.Button si = (javafx.scene.control.Button) esperar(n ->
                n instanceof javafx.scene.control.Button b && "Confirmar".equals(b.getText()));
            Platform.runLater(si::fire);
            esperar(n -> n instanceof javafx.scene.control.Label l && "Deshecho".equals(l.getText()));
            assertEquals("Transferidos en esta ventana: 0 bienes", titulo.getText());
            org.testfx.util.WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () ->
                lista.getItems().stream().anyMatch(p -> p.getId().equals(bien.getId()) && p.getCodigo().equals(bien.getCodigo())));
        } finally {
            interact(() -> new java.util.ArrayList<>(javafx.stage.Window.getWindows()).stream()
                .filter(w -> w instanceof Stage s && s.getTitle() != null && s.getTitle().startsWith("Transferir"))
                .forEach(w -> ((Stage) w).close()));
            com.sibim.db.DemoDataStore.reiniciar();
        }
    }

    /** In production every transfer was one bien with its own resguardo: the list needed
     *  Ctrl + click and a search lost what was selected. One click ticks, a search keeps
     *  the ticks, and all of them go in one batch with one resguardo. */
    @Test
    @SuppressWarnings("unchecked")
    void transferir_lasMarcasSobrevivenALaBusqueda_yVanJuntas() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        String area = productos.stream().filter(p -> p.getArea() != null && p.getFechaBaja() == null && p.getStockActual() > 0)
            .collect(java.util.stream.Collectors.groupingBy(Producto::getArea)).entrySet().stream()
            .filter(e -> e.getValue().size() >= 3).map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        List<Producto> delArea = productos.stream()
            .filter(p -> area.equals(p.getArea()) && p.getFechaBaja() == null && p.getStockActual() > 0).limit(3).toList();
        Platform.runLater(() -> TransferenciaArrastreDialog.show(productos, () -> {}));
        try {
            ListView<String> areas = (ListView<String>) esperar(n -> n instanceof ListView<?> l
                && l.getStyleClass().contains("trf-areas") && l.isVisible() && !l.getItems().isEmpty());
            interact(() -> {
                areas.getSelectionModel().select(area);
                areas.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
            });
            // Step 2 shows once the área's bienes are in: waited for, not queried at once.
            javafx.scene.control.TextField buscarBien = (javafx.scene.control.TextField) esperar(n ->
                n instanceof javafx.scene.control.TextField t && t.getPromptText() != null
                    && t.getPromptText().startsWith("Código y Enter") && t.isVisible());
            marcarConEnter(buscarBien, delArea.get(0).getCodigo());

            // A click on the row ticks it, with no key held down.
            ListView<Producto> lista = (ListView<Producto>) lookup(".trf-lista").query();
            interact(() -> lista.scrollTo(delArea.get(1)));
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            Node fila = lookup(".trf-lista .cnt-fila").queryAll().stream()
                .filter(n -> n.lookupAll(".trf-codigo").stream().anyMatch(c ->
                    delArea.get(1).getCodigo().equals(((javafx.scene.control.Label) c).getText()))).findFirst().orElseThrow();
            interact(() -> fila.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, true, null)));
            assertTrue(textoVisible("2 marcados"), "dos marcados");

            // The third is searched: the first two aren't on the list now, and stay ticked.
            interact(() -> buscarBien.setText(delArea.get(2).getCodigo()));
            org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
            assertTrue(textoVisible("2 marcados · "), "dice que hay marcas fuera de la búsqueda");
            marcarConEnter(buscarBien, delArea.get(2).getCodigo());
            assertTrue(textoVisible("3 marcados"));

            Node destino = lookup(".trf-tarjeta").queryAll().iterator().next();
            Platform.runLater(() -> destino.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null)));
            javafx.scene.control.TextField motivo = (javafx.scene.control.TextField) esperar(n ->
                n instanceof javafx.scene.control.TextField t && "Ej. reasignación, cambio de oficina".equals(t.getPromptText()));
            interact(() -> motivo.setText("Prueba"));
            javafx.scene.control.Button ok = (javafx.scene.control.Button) lookup(n ->
                n instanceof javafx.scene.control.Button b && "Transferir".equals(b.getText())).query();
            Platform.runLater(ok::fire);
            esperar(n -> n instanceof javafx.scene.control.Label l && "Transferidos en esta ventana: 3 bienes".equals(l.getText()));
            org.testfx.util.WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () -> !textoVisible("marcados"));
            // The list is read again in the background after the transfer.
            try {
                org.testfx.util.WaitForAsyncUtils.waitFor(10, java.util.concurrent.TimeUnit.SECONDS, () ->
                    lista.getItems().stream().noneMatch(p -> delArea.stream().anyMatch(d -> d.getId().equals(p.getId()))));
            } catch (java.util.concurrent.TimeoutException noSalieron) { /* the assert below says it */ }
            assertTrue(lista.getItems().stream().noneMatch(p -> delArea.stream().anyMatch(d -> d.getId().equals(p.getId()))),
                "los tres salieron de la lista");
        } finally {
            interact(() -> new java.util.ArrayList<>(javafx.stage.Window.getWindows()).stream()
                .filter(w -> w instanceof Stage s && s.getTitle() != null && s.getTitle().startsWith("Transferir"))
                .forEach(w -> ((Stage) w).close()));
            com.sibim.db.DemoDataStore.reiniciar();
        }
    }

    private void marcarConEnter(javafx.scene.control.TextField buscar, String codigo) {
        interact(() -> {
            buscar.setText(codigo);
            buscar.fireEvent(new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                "", "", javafx.scene.input.KeyCode.ENTER, false, false, false, false));
        });
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean textoVisible(String inicio) {
        return lookup(n -> n instanceof javafx.scene.control.Label l && l.isVisible()
            && l.getText() != null && l.getText().startsWith(inicio)).tryQuery().isPresent();
    }

    /** Looking up a secretaría on the right shows its whole group. */
    @Test
    void transferir_buscarSecretaria_muestraSusAreas() throws Exception {
        List<Producto> productos = new ProductoService().getAll();
        com.sibim.config.Areas.SecretariaInfo sec = com.sibim.config.Areas.secretarias().get(0);
        Platform.runLater(() -> TransferenciaArrastreDialog.show(productos, () -> {}));
        javafx.scene.control.TextField buscar = (javafx.scene.control.TextField) esperar(n ->
            n instanceof javafx.scene.control.TextField t && t.getPromptText() != null
                && t.getPromptText().startsWith("Buscar el área o la secretaría"));
        interact(() -> buscar.setText(sec.nombre()));
        org.testfx.util.WaitForAsyncUtils.waitForFxEvents();
        List<String> tarjetas = lookup(".trf-tarjeta .org-area-name").queryAll().stream()
            .map(n -> ((javafx.scene.control.Label) n).getText()).toList();
        assertTrue(tarjetas.containsAll(sec.direcciones()), "todas sus direcciones: " + tarjetas);
        interact(() -> ((Stage) buscar.getScene().getWindow()).close());
    }

    /** The first node {@code que} matches, waiting up to 10 s for it to show. */
    private Node esperar(java.util.function.Predicate<Node> que) {
        for (int i = 0; i < 100; i++) {
            java.util.Optional<Node> n = lookup(que::test).tryQuery();
            if (n.isPresent()) return n.get();
            sleep(100);
        }
        return lookup(que::test).query();
    }
}
