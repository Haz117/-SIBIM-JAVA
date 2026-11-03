package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDialogFactory;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Categoria;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testfx.util.WaitForAsyncUtils;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Nuevo bien has one row of buttons: Cancelar next to Siguiente. The dialog's
 *  own button bar held only Cancelar and read as a second footer. */
class NuevoBienDialogSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(adminUser());
        stage.setScene(new Scene(new StackPane(), 400, 300));
        stage.show();
    }

    @Test
    void cancelarVaJuntoASiguiente_sinSegundaBarra() {
        Categoria cat = new Categoria("cat-prueba", "Mobiliario", null, "#8B5CF6", null, null);
        Platform.runLater(() -> ProductoDialogFactory.show(null, List.of(cat), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null)));
        for (int i = 0; i < 50 && lookup(".stepper-nav-bar").tryQuery().isEmpty(); i++) sleep(100);

        HBox nav = lookup(".stepper-nav-bar").query();
        Button cancelar = nav.getChildren().stream()
            .filter(n -> n instanceof Button b && "Cancelar".equals(b.getText()))
            .map(n -> (Button) n).findFirst()
            .orElseThrow(() -> new AssertionError("Cancelar debe estar en la barra de Siguiente"));
        Region barraDelDialogo = lookup(".button-bar").query();
        assertFalse(barraDelDialogo.isVisible());
        assertEquals(0, barraDelDialogo.getHeight(), 0.5, "la barra oculta no ocupa espacio");

        interact(cancelar::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(lookup(".stepper-nav-bar").tryQuery().isEmpty(), "Cancelar cierra la ventana");
    }

    /** The clave armonizada is the category's CONAC code: picking a category
     *  fills it in and picking another follows it, but one typed by hand stays. */
    @Test
    @SuppressWarnings("unchecked")
    void laClaveArmonizadaSaleDeLaCategoria() {
        Categoria muebles = new Categoria("cat-511", "Muebles de Oficina", null, "#8B5CF6", null, null);
        muebles.setCodigoConac("1.2.4.1.1.511");
        Categoria computo = new Categoria("cat-515", "Equipo de Cómputo", null, "#8B5CF6", null, null);
        computo.setCodigoConac("1.2.4.1.3.515");
        Platform.runLater(() -> ProductoDialogFactory.show(null, List.of(muebles, computo), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null)));
        // The dialog's tabs are laid out after its button bar shows: waiting for
        // the bar alone found no fields now and then (in CI too).
        ComboBox<Categoria> categoria = (ComboBox<Categoria>) esperar(n -> n instanceof ComboBox<?> c
            && "Buscar categoría…".equals(c.getPromptText()));
        TextField clave = (TextField) esperar(n -> n instanceof TextField t && "Ej. 1.2.4.1.1.511".equals(t.getPromptText()));
        assertEquals("", clave.getText());

        interact(() -> categoria.setValue(muebles));
        assertEquals("1.2.4.1.1.511", clave.getText());
        interact(() -> categoria.setValue(computo));
        assertEquals("1.2.4.1.3.515", clave.getText(), "sigue a la categoría");

        interact(() -> clave.setText("1.2.4.1.3.515.9"));
        interact(() -> categoria.setValue(muebles));
        assertEquals("1.2.4.1.3.515.9", clave.getText(), "la escrita a mano se respeta");

        // The vida útil follows the category's CONAC parameter the same way.
        javafx.scene.control.Spinner<Integer> vida = (javafx.scene.control.Spinner<Integer>) esperar(n ->
            n instanceof javafx.scene.control.Spinner<?> s && "Sin definir".equals(s.getEditor().getPromptText()));
        assertEquals(10, vida.getValue(), "muebles: 10 años");
        interact(() -> categoria.setValue(computo));
        assertEquals(3, vida.getValue(), "cómputo: 3 años");
        interact(() -> vida.getValueFactory().setValue(4));
        interact(() -> categoria.setValue(muebles));
        assertEquals(4, vida.getValue(), "la escrita a mano se respeta");

        interact(() -> ((Stage) categoria.getScene().getWindow()).close());
    }

    /** Opening a bien without vida útil leaves it without one: the CONAC suggestion
     *  is for someone picking a category, or every edit would change it again. */
    @Test
    @SuppressWarnings("unchecked")
    void editarUnBienSinVidaUtil_noLeSugiereNinguna() {
        Categoria computo = new Categoria("cat-515", "Equipo de Cómputo", null, "#8B5CF6", null, null);
        computo.setCodigoConac("1.2.4.1.3.515");
        com.sibim.model.Producto laptop = new com.sibim.model.Producto();
        laptop.setId("p-laptop"); laptop.setNombre("Laptop Dell"); laptop.setCodigo("SGM/34");
        laptop.setCategoriaId("cat-515"); laptop.setCategoriaNombre("Equipo de Cómputo");
        laptop.setClaveArmonizada("1.2.4.1.3.515"); laptop.setArea("Secretaría General Municipal");
        Platform.runLater(() -> ProductoDialogFactory.show(laptop, List.of(computo), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null)));
        javafx.scene.control.Spinner<Integer> vida = (javafx.scene.control.Spinner<Integer>) esperar(n ->
            n instanceof javafx.scene.control.Spinner<?> s && "Sin definir".equals(s.getEditor().getPromptText()));
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(vida.getValue(), "sin vida útil sigue sin vida útil");
        interact(() -> ((Stage) vida.getScene().getWindow()).close());
    }

    /** "Nuevo bien" from the list filtered to an área starts in that área and
     *  says which número the bien will get, so the next one of an área is
     *  registered from that área. */
    @Test
    @SuppressWarnings("unchecked")
    void nuevoBienDesdeUnArea_empiezaEnEsaAreaYDiceSuNumero() throws Exception {
        String area = "Dirección de Gobierno";
        String siguiente = new com.sibim.service.ProductoService().siguienteCodigo(area);
        Categoria cat = new Categoria("cat-prueba", "Mobiliario", null, "#8B5CF6", null, null);
        Platform.runLater(() -> ProductoDialogFactory.show(null, List.of(cat), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null), area));
        ComboBox<String> fArea = (ComboBox<String>) esperar(n -> n instanceof ComboBox<?> c
            && "Área responsable".equals(c.getPromptText()));
        assertEquals(area, fArea.getValue());
        TextField codigo = (TextField) esperar(n -> n instanceof TextField t && t.getPromptText() != null
            && t.getPromptText().startsWith("Se asignará DGOB/"));
        assertEquals("Se asignará " + siguiente + " al guardar", codigo.getPromptText());

        interact(() -> ((Stage) fArea.getScene().getWindow()).close());
    }

    private static com.sibim.model.Producto bienCompleto() {
        com.sibim.model.Producto p = new com.sibim.model.Producto();
        p.setId("p-serie"); p.setNombre("Escritorio"); p.setCodigo("SGM/34");
        p.setCategoriaId("cat-prueba"); p.setCategoriaNombre("Mobiliario");
        p.setArea(com.sibim.config.Areas.getAllAreaNames().iterator().next());
        p.setUnidad(com.sibim.model.enums.UnidadMedida.PIEZA);
        return p;
    }

    private Button enBarra(String texto) {
        return lookup(".stepper-nav-bar").queryAs(HBox.class).getChildren().stream()
            .filter(n -> n instanceof Button b && texto.equals(b.getText())).map(n -> (Button) n).findFirst().orElse(null);
    }

    /** Correcting one datum took Siguiente, Siguiente, Guardar. Editing, Guardar is there
     *  from the start, and the series goes on in the same section. */
    @Test
    void editar_guardaDesdeCualquierSeccion_ySigueConElDeAbajo() throws Exception {
        Categoria cat = new Categoria("cat-prueba", "Mobiliario", null, "#8B5CF6", null, null);
        ProductoDialogFactory.Seguir seguir = new ProductoDialogFactory.Seguir("Guardar y abrir SGM/35", "El de abajo", 2);
        java.util.concurrent.atomic.AtomicReference<java.util.Optional<com.sibim.model.Producto>> res = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> res.set(ProductoDialogFactory.show(bienCompleto(), List.of(cat), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null), null, seguir)));
        for (int i = 0; i < 50 && lookup(".stepper-nav-bar").tryQuery().isEmpty(); i++) sleep(100);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(null, enBarra("Siguiente"), "editar no va por pasos");
        Button guardar = enBarra("Guardar cambios");
        Button otro = enBarra("Guardar y abrir SGM/35");
        assertFalse(guardar.isDisabled());
        assertEquals(2, lookup(".dlg-stepper").queryAs(javafx.scene.control.TabPane.class).getSelectionModel().getSelectedIndex(),
            "abre en la sección en la que se venía trabajando");

        // Something missing: neither way of saving is available.
        TextField nombre = (TextField) esperar(n -> n instanceof TextField t && "Nombre descriptivo del bien".equals(t.getPromptText()));
        interact(() -> nombre.setText(""));
        assertTrue(guardar.isDisabled() && otro.isDisabled());
        assertFalse(seguir.pedido());
        interact(() -> nombre.setText("Escritorio grande"));

        interact(() -> lookup(".dlg-stepper").queryAs(javafx.scene.control.TabPane.class).getSelectionModel().select(1));
        interact(otro::fire);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(seguir.pedido());
        assertEquals(1, seguir.pestana(), "el siguiente abre en la sección de ahora");
        assertEquals("Escritorio grande", res.get().orElseThrow().getNombre());
    }

    /** A new bien goes through the steps; at the last one it can go on to another,
     *  and that one opens with what they share and without what identifies a bien. */
    @Test
    void nuevo_alFinalPuedeSeguirConOtro() throws Exception {
        Categoria cat = new Categoria("cat-prueba", "Mobiliario", null, "#8B5CF6", null, null);
        com.sibim.model.Producto plantilla = bienCompleto();
        plantilla.setId(null); plantilla.setNombre(""); plantilla.setCodigo(""); plantilla.setUbicacion("Oficina 2");
        ProductoDialogFactory.Seguir seguir = new ProductoDialogFactory.Seguir("Guardar y registrar otro", "Otro igual", 0);
        Platform.runLater(() -> ProductoDialogFactory.show(plantilla, List.of(cat), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null), null, seguir));
        for (int i = 0; i < 50 && lookup(".stepper-nav-bar").tryQuery().isEmpty(); i++) sleep(100);
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(lookup(n -> n instanceof javafx.scene.control.Label l && l.getText() != null
            && l.getText().startsWith("Se conservaron el área")).tryQuery().isPresent(), "dice qué se conservó");
        ComboBox<?> categoria = (ComboBox<?>) esperar(n -> n instanceof ComboBox<?> c && "Buscar categoría…".equals(c.getPromptText()));
        assertEquals(cat, categoria.getValue());
        TextField nombre = (TextField) esperar(n -> n instanceof TextField t && "Nombre descriptivo del bien".equals(t.getPromptText()));
        assertEquals("", nombre.getText());

        Button siguiente = enBarra("Siguiente");
        Button otro = enBarra("Guardar y registrar otro");
        assertFalse(otro.isVisible(), "todavía no: faltan pasos");
        interact(siguiente::fire);
        interact(siguiente::fire);
        assertTrue(otro.isVisible());
        assertTrue(otro.isDisabled(), "sin nombre no se puede guardar");
        interact(() -> ((Stage) nombre.getScene().getWindow()).close());
    }

    /** "SIN SERIE" saved before shows as "No tiene" ticked; "sin marca" typed ticks it on
     *  leaving the field; both are saved empty. A resguardante typed with another spelling
     *  of someone in the inventory gets "¿Es …?" and a link that writes that spelling. */
    @Test
    void noTiene_yNombreParecido() throws Exception {
        Categoria cat = new Categoria("cat-prueba", "Mobiliario", null, "#8B5CF6", null, null);
        com.sibim.model.Producto p = bienCompleto();
        p.setNumeroSerie("SIN SERIE");
        java.util.concurrent.atomic.AtomicReference<java.util.Optional<com.sibim.model.Producto>> res = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> res.set(ProductoDialogFactory.show(p, List.of(cat), new HashMap<>(),
            LoggerFactory.getLogger("prueba"), List.of(),
            new ProductoDialogFactory.Extras(List.of(), List.of(), List.of(), List.of(), null,
                List.of("TANIA EJEMPLO FICTICIO")), null, null)));
        TextField marca = (TextField) esperar(n -> n instanceof TextField t && "Ej. HP, Dell, Brother…".equals(t.getPromptText()));
        TextField serie = (TextField) lookup(n -> n instanceof TextField t && "Número de serie o placa".equals(t.getPromptText())).query();
        TextField quien = (TextField) lookup(n -> n instanceof TextField t && t.getPromptText() != null
            && t.getPromptText().startsWith("Persona responsable")).query();
        javafx.scene.control.CheckBox sinSerie = (javafx.scene.control.CheckBox) ((HBox) serie.getParent()).getChildren().get(1);
        javafx.scene.control.CheckBox sinMarca = (javafx.scene.control.CheckBox) ((HBox) marca.getParent()).getChildren().get(1);
        assertTrue(sinSerie.isSelected(), "«SIN SERIE» guardado sale como No tiene");
        assertEquals("", serie.getText());
        assertTrue(serie.isDisabled());

        // The field takes focus only in the section on screen.
        javafx.scene.control.TabPane pasos = lookup(".dlg-stepper").queryAs(javafx.scene.control.TabPane.class);
        interact(() -> pasos.getTabs().stream().filter(t -> {
                for (Node n = marca; n != null; n = n.getParent()) if (n == t.getContent()) return true;
                return false;
            }).findFirst().ifPresent(t -> pasos.getSelectionModel().select(t)));
        WaitForAsyncUtils.waitForFxEvents();
        interact(() -> { marca.requestFocus(); marca.setText("sin marca"); });
        WaitForAsyncUtils.waitForFxEvents();
        interact(quien::requestFocus);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(sinMarca.isSelected(), "escrito a mano, se marca al salir del campo");

        com.sibim.controller.dialogs.AvisoNombreParecido aviso = (com.sibim.controller.dialogs.AvisoNombreParecido)
            lookup(n -> n instanceof com.sibim.controller.dialogs.AvisoNombreParecido).query();
        interact(() -> quien.setText("LIC. TANYA EJEMPLO FICTICIO"));
        assertTrue(aviso.isVisible());
        assertEquals("TANIA EJEMPLO FICTICIO", aviso.sugerido());
        interact(() -> aviso.getChildren().stream().filter(n -> n instanceof javafx.scene.control.Hyperlink)
            .map(n -> (javafx.scene.control.Hyperlink) n).findFirst().orElseThrow().fire());
        assertEquals("TANIA EJEMPLO FICTICIO", quien.getText(), "el enlace escribe el nombre que ya existe");
        assertFalse(aviso.isVisible());

        interact(() -> enBarra("Guardar cambios").fire());
        WaitForAsyncUtils.waitForFxEvents();
        com.sibim.model.Producto guardado = res.get().orElseThrow();
        assertNull(guardado.getMarca());
        assertNull(guardado.getNumeroSerie());
        assertEquals("TANIA EJEMPLO FICTICIO", guardado.getResguardante());
    }

    /** The first node {@code que} matches, waiting up to 10 s for it to show. */
    private Node esperar(java.util.function.Predicate<Node> que) {
        for (int i = 0; i < 100; i++) {
            java.util.Optional<Node> n = lookup(que::test).tryQuery();
            if (n.isPresent()) return n.get();
            sleep(100);
        }
        return lookup(que::test).query();   // fails with TestFX's own message
    }
}
