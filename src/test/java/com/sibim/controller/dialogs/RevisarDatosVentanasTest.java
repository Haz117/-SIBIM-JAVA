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
        assertEquals(2, visibles(panel, ".cnt-fila"), "al abrir se muestran los bienes de todas las áreas");

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

    /** Equipo de defensa: the CONAC leaves its vida útil to the municipio, and it is the
     *  same for many bienes; typed once for the list as it is filtered. */
    @Test
    @SuppressWarnings("unchecked")
    void completar_mismaVidaUtil_aLosDeLaListaFiltrada() throws Exception {
        Producto a = bien("1", "SSP/1", "Ana", "Tesorería Municipal");
        Producto b = bien("2", "SSP/2", "Ana", "Tesorería Municipal");
        Producto casco = bien("3", "SSP/3", "Ana", "Tesorería Municipal");
        a.setNombre("CHALECO BALISTICO"); b.setNombre("CHALECO BALISTICO"); casco.setNombre("CASCO TACTICO");
        for (Producto p : List.of(a, b, casco)) p.setClaveArmonizada("1.2.4.5.1.551");
        Platform.runLater(() -> CompletarDatosDialog.show(List.of(a, b, casco), com.sibim.service.CalidadDatosService.Dato.VIDA_UTIL, null));
        DialogPane panel = abierto();
        ListView<String> areas = (ListView<String>) panel.lookup(".trf-areas");
        interact(() -> areas.getSelectionModel().select("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();

        TextField anios = (TextField) panel.lookup("#aniosTodos");
        javafx.scene.layout.HBox barra = (javafx.scene.layout.HBox) anios.getParent();
        Label texto = (Label) barra.getChildren().get(0);
        assertTrue(barra.isVisible());
        assertTrue(texto.getText().contains("3 bienes") && texto.getText().contains("la decide el municipio"), texto.getText());

        TextField buscar = (TextField) panel.lookup("#buscarBien");
        interact(() -> buscar.setText("chaleco"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(texto.getText().contains("2 bienes"), texto.getText());

        interact(() -> anios.setText("cinco"));
        interact(() -> anios.fireEvent(new javafx.event.ActionEvent()));
        assertTrue(Window.getWindows().stream().noneMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel),
            "sin un número de años no pregunta nada");

        interact(() -> anios.setText("5"));
        Platform.runLater(() -> anios.fireEvent(new javafx.event.ActionEvent()));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane confirmar = (DialogPane) Window.getWindows().stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel)
            .findFirst().orElseThrow().getScene().getRoot();
        String pregunta = confirmar.lookupAll(".label").stream().filter(n -> n instanceof Label)
            .map(n -> ((Label) n).getText()).filter(java.util.Objects::nonNull).reduce("", String::concat);
        assertTrue(pregunta.contains("5 años a 2 bienes") && pregunta.contains("CHALECO BALISTICO (2)"), pregunta);
        ButtonType si = confirmar.getButtonTypes().stream()
            .filter(t -> t.getButtonData().isDefaultButton()).findFirst().orElseThrow();
        Platform.runLater(() -> ((Button) confirmar.lookupButton(si)).fire());

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> a.getVidaUtilAnios() != null && b.getVidaUtilAnios() != null);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(5, a.getVidaUtilAnios());
        assertEquals(5, b.getVidaUtilAnios());
        assertNull(casco.getVidaUtilAnios(), "el que no estaba en la lista no se toca");
        assertFalse(barra.isVisible(), "en la lista ya no queda a quién ponérsela");
        assertEquals(2, panel.lookupAll(".cell-badge-success").stream().filter(Node::isVisible).count(), "y siguen ahí, marcados");
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

    /** Answered wrong: "Deshacer" is right there, and the case goes back on the list. */
    @Test
    void unir_seDeshace_yElCasoRegresaALaLista() throws Exception {
        List<Producto> bienes = List.of(bien("1", "A/1", "RUIZ ANA", "Tesorería Municipal"),
            bien("2", "A/2", "ANA RUIZ", "Tesorería Municipal"), bien("3", "A/3", "ANA RUIZ", "Tesorería Municipal"));
        Platform.runLater(() -> UnirPersonasDialog.show(bienes, List.of(), null));
        DialogPane panel = abierto();
        // Its two spellings and "Ninguno de estos".
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> visibles(panel, ".rsg-tarjeta") == 3);
        @SuppressWarnings("unchecked")
        ListView<Object> lista = (ListView<Object>) panel.lookup(".trf-areas");
        assertEquals(1, lista.getItems().size());
        Button deshacer = (Button) panel.lookupAll(".button").stream()
            .filter(n -> n instanceof Button b && "Deshacer".equals(b.getText())).findFirst().orElseThrow();
        assertFalse(deshacer.getParent().isVisible(), "todavía no hay qué deshacer");

        Button unir = (Button) panel.lookupAll(".btn-primary").stream()
            .filter(n -> n instanceof Button x && x.getText().startsWith("Son la misma")).findFirst().orElseThrow();
        Platform.runLater(unir::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel));
        WaitForAsyncUtils.waitForFxEvents();
        DialogPane confirmar = (DialogPane) Window.getWindows().stream()
            .filter(w -> w.getScene().getRoot() instanceof DialogPane d && d != panel)
            .findFirst().orElseThrow().getScene().getRoot();
        ButtonType si = confirmar.getButtonTypes().stream()
            .filter(t -> t.getButtonData().isDefaultButton()).findFirst().orElseThrow();
        Platform.runLater(() -> ((Button) confirmar.lookupButton(si)).fire());

        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> deshacer.getParent().isVisible());
        // The list updates in its own turn of the FX thread: with the PC busy (24-nov-2026, the
        // suite beside two captures) it could be checked before.
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0, lista.getItems().size(), "ese caso ya quedó");

        interact(deshacer::fire);
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> !deshacer.getParent().isVisible());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, lista.getItems().size(), "regresa para contestarlo otra vez");
        assertSame(lista.getItems().get(0), lista.getSelectionModel().getSelectedItem());
        assertEquals(3, visibles(panel, ".rsg-tarjeta"));
    }

    /** From Inicio it opens on the dato somebody came to fill. */
    /** 24-nov-2026: a name at a time with «Cambiar datos»; here each name is one row, with what
     *  is already known written. */
    @Test
    @SuppressWarnings("unchecked")
    void grupos_unRenglonPorNombre_conLoQueYaSeSabe_yUnValorMaloNoSeGuarda() throws Exception {
        List<Producto> bienes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Producto p = bien(String.valueOf(i), "SSP/" + i, "Ana", "Seguridad Pública");
            p.setNombre("SILLA PLEGADIZA DE COLOR VERDE LIMON");
            p.setClaveArmonizada("1.2.4.1.1.511");
            bienes.add(p);
        }
        for (int i = 4; i <= 5; i++) {
            Producto p = bien(String.valueOf(i), "TES/" + i, "Ana", "Tesorería Municipal");
            p.setNombre("ESCUDO MOTIN");
            bienes.add(p);
        }
        bienes.add(bien("6", "TES/6", "Ana", "Tesorería Municipal"));
        Platform.runLater(() -> CompletarPorGruposDialog.show(bienes, null, null));
        DialogPane panel = abierto();
        assertEquals(2, visibles(panel, ".cnt-fila"), "un nombre con un solo bien no es grupo");
        java.util.function.Function<String, TextField> campo = texto -> (TextField) panel.lookupAll(".form-input").stream()
            .filter(n -> texto.equals(((TextField) n).getAccessibleText())).findFirst().orElseThrow(() -> new AssertionError(texto));
        assertEquals("VERDE LIMON", campo.apply("Color de SILLA PLEGADIZA DE COLOR VERDE LIMON").getText(), "lo dice su nombre");
        assertEquals("MUEBLE", campo.apply("Tipo de bien de SILLA PLEGADIZA DE COLOR VERDE LIMON").getText());
        TextField vida = campo.apply("Vida útil de SILLA PLEGADIZA DE COLOR VERDE LIMON");
        assertEquals("", vida.getText(), "la vida útil no se propone por su clave");

        interact(() -> vida.setText("0"));
        for (var tipo : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED))
            interact(() -> javafx.event.Event.fireEvent(vida, new KeyEvent(tipo, "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookupAll(".cell-badge-danger").stream().anyMatch(Node::isVisible), "dice qué está mal");
        assertEquals(2, visibles(panel, ".cnt-fila"));

        ComboBox<String> area = (ComboBox<String>) panel.lookupAll(".combo-box").stream().findFirst().orElseThrow();
        interact(() -> area.setValue("Tesorería Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, visibles(panel, ".cnt-fila"), "solo los grupos del área");
    }

    /** 24-nov-2026: 418 photos for 241 bienes, one form at a time. The next photo comes marked;
     *  → and Space mark another; a save that fails frees them and says so. */
    @Test
    @SuppressWarnings("unchecked")
    void fotosDeUnaCarpeta_laSiguienteVieneMarcada_yUnaQueFallaLasLibera(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        for (int i = 1; i <= 3; i++) {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
            javax.imageio.ImageIO.write(im, "jpg", dir.resolve("IMG_" + i + ".jpg").toFile());
        }
        Producto a = bien("1", "SSPAO/1", "Ana", "Seguridad Pública");
        a.setUbicacion("ALMACÉN");
        Producto b = bien("2", "SSPAO/2", "Ana", "Seguridad Pública");
        b.setUbicacion("ARMERÍA");
        Producto conFoto = bien("3", "SSPAO/3", "Ana", "Seguridad Pública");
        conFoto.setFotoUrl("https://x/a.jpg");
        Platform.runLater(() -> FotosDeCarpetaDialog.show(List.of(b, a, conFoto), null, dir.toFile(), null));
        DialogPane panel = abierto();
        ListView<Object> lista = (ListView<Object>) panel.lookupAll(".cnt-lista").stream().findFirst().orElseThrow();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> panel.lookupAll(".fdc-mini").size() == 3);
        assertEquals(2, lista.getItems().size(), "solo los que no tienen foto");
        assertEquals(1, visibles(panel, ".fdc-mini-marcada"), "la primera foto viene marcada para el primer bien");

        for (KeyCode k : List.of(KeyCode.RIGHT, KeyCode.SPACE))
            interact(() -> javafx.event.Event.fireEvent(lista, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", k, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, visibles(panel, ".fdc-mini-marcada"), "→ y Espacio marcan la segunda");

        // Without a database the save fails: the photos are free again and the bien says so.
        interact(() -> javafx.event.Event.fireEvent(lista, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> panel.lookupAll(".cell-badge-danger").stream().anyMatch(Node::isVisible));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, lista.getSelectionModel().getSelectedIndex(), "pasó al siguiente bien");
        assertTrue(panel.lookupAll(".fdc-usada-en").stream().noneMatch(Node::isVisible), "ninguna quedó como de otro bien");
    }

    /** 25-nov-2026: 95 photos of Seguridad Pública matched by hand by the serie the photo shows
     *  (engraved on the esposas, on a chaleco's label) or the name written on the box. */
    @Test
    @SuppressWarnings("unchecked")
    void fotosDeUnaCarpeta_buscaPorSerieYPorPersona_yLasMuestraJuntoALaFoto(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(im, "jpg", dir.resolve("IMG_1.jpg").toFile());
        Producto esposas = bien("1", "SSPAO/1", "PERSONA FICTICIA UNO", "Seguridad Pública");
        esposas.setNumeroSerie("G999001");
        Producto chaleco = bien("2", "SSPAO/2", "PERSONA PRUEBA DOS", "Seguridad Pública");
        chaleco.setNumeroSerie("AX9990001");
        Producto sinNada = bien("3", "SSPAO/3", null, "Seguridad Pública");
        assertEquals("Serie G999001 · PERSONA FICTICIA UNO", FotosDeCarpetaDialog.paraComparar(esposas));
        assertEquals("", FotosDeCarpetaDialog.paraComparar(sinNada));

        Platform.runLater(() -> FotosDeCarpetaDialog.show(List.of(esposas, chaleco, sinNada), null, dir.toFile(), null));
        DialogPane panel = abierto();
        ListView<Object> lista = (ListView<Object>) panel.lookupAll(".cnt-lista").stream().findFirst().orElseThrow();
        TextField buscar = (TextField) panel.lookupAll(".form-input").stream()
            .filter(n -> n instanceof TextField t && t.getPromptText() != null && t.getPromptText().contains("serie"))
            .findFirst().orElseThrow();
        Label comparar = (Label) panel.lookup(".fdc-comparar");
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> lista.getItems().size() == 3);
        assertTrue(comparar.isVisible());
        assertEquals("Serie G999001 · PERSONA FICTICIA UNO", comparar.getText(), "lo del primer bien, junto a su foto");

        interact(() -> buscar.setText("ax9990001"));
        assertEquals(1, lista.getItems().size(), "la serie que trae la foto lo encuentra");
        interact(() -> lista.getSelectionModel().select(0));
        assertEquals("Serie AX9990001 · PERSONA PRUEBA DOS", comparar.getText());
        interact(() -> buscar.setText("ficticia uno"));
        assertEquals(1, lista.getItems().size(), "el nombre escrito en la caja también");
        interact(() -> { buscar.setText(""); lista.getSelectionModel().select(2); });
        assertFalse(comparar.isVisible(), "sin serie ni resguardante no hay nada que comparar");
    }

    /** 24 and 26-nov-2026: 9 chalecos had as their photo the label of another chaleco; the form
     *  showed it at 110 × 80, with the serie on another tab. */
    @Test
    void fotoEnGrande_conSuSerie_yQuitarlaOHacerlaPrincipalCambiaLaGaleria(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        List<String> archivos = new ArrayList<>();
        for (int i = 1; i <= 2; i++) {
            java.nio.file.Path f = dir.resolve("foto" + i + ".png");
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(60, 40, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", f.toFile());
            archivos.add(f.toString());
        }
        FotoEnGrande.Pie pie = new FotoEnGrande.Pie("SSPAO/7 · CHALECO", "AL0000001", "ARMADURA FICTICIA GR-1", "PERSONA FICTICIA");
        assertEquals("Serie AL0000001  ·  ARMADURA FICTICIA GR-1  ·  PERSONA FICTICIA", pie.datos());
        assertEquals("", new FotoEnGrande.Pie("X", " ", null, "").datos());

        java.util.concurrent.atomic.AtomicReference<FotoEnGrande.Resultado> r = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> r.set(FotoEnGrande.show(escena.getWindow(), archivos, 1, pie)));
        DialogPane panel = abierto();
        assertEquals("Serie AL0000001  ·  ARMADURA FICTICIA GR-1  ·  PERSONA FICTICIA", ((Label) panel.lookup(".fdc-comparar")).getText());
        Button quitar = panel.lookupAll(".button").stream().filter(n -> n instanceof Button b && b.getText() != null
            && b.getText().startsWith("No es de este bien")).map(n -> (Button) n).findFirst().orElseThrow();
        Button principal = panel.lookupAll(".button").stream().filter(n -> n instanceof Button b && "Usar como principal".equals(b.getText()))
            .map(n -> (Button) n).findFirst().orElseThrow();
        assertTrue(principal.isVisible(), "la segunda foto puede pasar a principal");
        interact(() -> javafx.event.Event.fireEvent(panel, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.LEFT, false, false, false, false)));
        assertFalse(principal.isVisible(), "← va a la primera, que ya es la principal");
        interact(quitar::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> r.get() != null);
        assertEquals(new FotoEnGrande.Resultado(0, FotoEnGrande.Accion.QUITAR), r.get());

        // What it asked, applied to the form's photos.
        java.util.concurrent.atomic.AtomicReference<GaleriaFotos> g = new java.util.concurrent.atomic.AtomicReference<>();
        interact(() -> g.set(new GaleriaFotos(new ArrayList<>(archivos), new Dialog<Void>(), null)));
        interact(() -> g.get().aplicar(new FotoEnGrande.Resultado(1, FotoEnGrande.Accion.PRINCIPAL)));
        assertEquals(List.of(archivos.get(1), archivos.get(0)), g.get().fotos, "la elegida queda primero");
        interact(() -> g.get().aplicar(new FotoEnGrande.Resultado(0, FotoEnGrande.Accion.QUITAR)));
        assertEquals(List.of(archivos.get(0)), g.get().fotos, "la que no es de este bien sale");
    }

    /** 24-nov-2026: 214 facturas without the proveedor in any of their bienes; one row per factura. */
    @Test
    void porFactura_unRenglonPorFactura_conLoQueTienenSusOtrosBienes_yUnFolioMaloNoSeGuarda() throws Exception {
        List<Producto> bienes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Producto p = bien(String.valueOf(i), "OP/" + i, "Ana", "Secretaría de Obras Públicas");
            p.setNombre("REVOLVEDORA");
            p.setNumeroFactura("FAC 12345");
            bienes.add(p);
        }
        bienes.get(0).setProveedor("MAQUINARIA DEL CENTRO");
        Producto otra = bien("4", "TES/4", "Ana", "Tesorería Municipal");
        otra.setNumeroFactura("XYZ-98765");
        bienes.add(otra);
        bienes.add(bien("5", "TES/5", "Ana", "Tesorería Municipal"));
        Platform.runLater(() -> CompletarPorFacturaDialog.show(bienes, null, null));
        DialogPane panel = abierto();
        assertEquals(2, visibles(panel, ".cnt-fila"), "un bien sin factura no es renglón");
        java.util.function.Function<String, Node> control = texto -> panel.lookupAll(".form-input").stream()
            .filter(n -> texto.equals(n.getAccessibleText())).findFirst().orElseThrow(() -> new AssertionError(texto));
        assertEquals("MAQUINARIA DEL CENTRO", ((TextField) control.apply("Proveedor de la factura FAC 12345")).getText(),
            "lo tiene otro bien de su factura");
        TextField folio = (TextField) control.apply("Folio fiscal de la factura FAC 12345");
        interact(() -> folio.setText("3822637C-8B6A"));
        for (var tipo : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED))
            interact(() -> javafx.event.Event.fireEvent(folio, new KeyEvent(tipo, "", "", KeyCode.ENTER, false, false, false, false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookupAll(".cell-badge-danger").stream().anyMatch(n -> n.isVisible()
            && ((Label) n).getText().startsWith("El folio fiscal")), "dice qué tiene mal el folio");
    }

    /** 24-nov-2026: MUEBLE and the 15 % typed by hand while their tools waited in four cards. */
    @Test
    void loSeguro_cadaArregloConSuCasilla_yElTotalSigueALasMarcas() throws Exception {
        Producto mueble = bien("1", "TES/1", "Ana", "Tesorería Municipal");
        mueble.setNombre("ESCRITORIO COLOR NOGAL");
        mueble.setClaveArmonizada("1.2.4.1.1.511");
        Producto folio = bien("2", "SSPC/2", "Ana", "Seguridad Pública — C-2");
        folio.setNumeroFactura("3822637C-8B6A-410A-B1C2-0123456789AB");
        Platform.runLater(() -> LoSeguroDialog.show(List.of(mueble, folio), null));
        DialogPane panel = abierto();
        List<CheckBox> marcas = panel.lookupAll(".check-box").stream().map(n -> (CheckBox) n).toList();
        assertEquals(List.of("El folio fiscal, a su lugar", "MUEBLE a lo que no es vehículo", "El color que dice su nombre"),
            marcas.stream().map(CheckBox::getText).toList());
        assertTrue(marcas.stream().allMatch(CheckBox::isSelected), "vienen marcados");
        Label total = (Label) panel.lookupAll(".muted-sm").stream()
            .filter(n -> n instanceof Label l && l.getText().contains("cambio")).findFirst().orElseThrow();
        assertEquals("3 arreglos · 3 cambios", total.getText());
        interact(() -> marcas.get(1).setSelected(false));
        assertEquals("2 arreglos · 2 cambios", total.getText());
    }

    @Test
    void completar_abreEnElDatoPedido() throws Exception {
        List<Producto> bienes = List.of(bien("1", "A/1", "ANA RUIZ", "Tesorería Municipal"));
        Platform.runLater(() -> CompletarDatosDialog.show(bienes, com.sibim.service.CalidadDatosService.Dato.FECHA, null));
        DialogPane panel = abierto();
        ToggleButton fecha = (ToggleButton) panel.lookupAll(".filter-chip").stream()
            .filter(n -> n instanceof ToggleButton t && t.getText().toLowerCase().contains("fecha")).findFirst().orElseThrow();
        assertTrue(fecha.isSelected());
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

    /** 18-nov-2026: the per-row «Sí es» was out of Tab's reach; S answers the row with the
     *  focus and the list goes on to the next one. */
    @Test
    @SuppressWarnings("unchecked")
    void revisar_conLaTeclaS_respondeYSigueEnElSiguiente() throws Exception {
        List<Producto> demo = new com.sibim.service.ProductoService().getAll();
        String area = demo.stream().filter(p -> p.getFechaBaja() == null && !p.isRevisadoPorArea() && p.getArea() != null)
            .collect(java.util.stream.Collectors.groupingBy(Producto::getArea, java.util.stream.Collectors.counting()))
            .entrySet().stream().filter(e -> e.getValue() >= 2).map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
        Platform.runLater(() -> RevisarBienesDialog.show(area, RevisarBienesDialog.Vista.POR_REVISAR, null));
        DialogPane panel = abierto();
        TableView<Producto> tabla = (TableView<Producto>) panel.lookup(".revisar-tabla");
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> tabla.getItems().size() >= 2);
        int antes = tabla.getItems().size();
        Producto primero = tabla.getItems().get(0), segundo = tabla.getItems().get(1);

        interact(() -> { tabla.requestFocus(); tabla.getSelectionModel().clearAndSelect(0); });
        interact(() -> javafx.event.Event.fireEvent(tabla, new KeyEvent(KeyEvent.KEY_PRESSED, "s", "s", KeyCode.S, false, false, false, false)));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> tabla.getItems().size() == antes - 1);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(tabla.getItems().contains(primero), "ya no está por revisar");
        assertEquals(segundo.getId(), tabla.getSelectionModel().getSelectedItem().getId(), "sigue en el siguiente");
    }
}
