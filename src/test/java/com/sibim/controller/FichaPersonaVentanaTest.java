package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
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

/** A person, from wherever their name shows up. */
class FichaPersonaVentanaTest extends ApplicationTest {

    private Scene escena;

    @Override
    public void start(Stage stage) {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("ficha-prueba"); u.setUsername("prueba"); u.setNombre("Prueba"); u.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u);
        escena = new Scene(new Pane(), 500, 300);
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

    private static Producto bien(String id, String quien) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo("A/" + id); p.setNombre("Bien " + id); p.setResguardante(quien);
        p.setArea("Tesorería Municipal"); p.setStockActual(1);
        return p;
    }

    private DialogPane abierto() throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> Window.getWindows().stream()
            .anyMatch(w -> w.getScene().getRoot() instanceof DialogPane));
        WaitForAsyncUtils.waitForFxEvents();
        return (DialogPane) Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane)
            .findFirst().orElseThrow().getScene().getRoot();
    }

    /** Accents and capitals don't matter: it opens on that person, ready to print. */
    @Test
    void abreEnLaPersonaPedida_yLlevaASusBienes() throws Exception {
        List<Producto> bienes = List.of(bien("1", "LUIS SOTO"), bien("2", "ANA RUIZ"));
        List<String> vistos = new ArrayList<>();
        var personas = com.sibim.service.ResguardoService.porResguardante(List.<Resguardo>of(), bienes);
        Platform.runLater(() -> com.sibim.controller.dialogs.ResguardantePdfDialog.show(personas, "ana  ruíz",
            (p, elegidos, sueltos) -> { }, null, p -> vistos.add(p.nombre())));
        DialogPane panel = abierto();
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !panel.lookupAll(".rsg-tarjeta").isEmpty());
        assertTrue(panel.lookupAll(".org-area-name").stream().anyMatch(n -> n instanceof Label l && "ANA RUIZ".equals(l.getText())));
        assertFalse(panel.lookupButton(ButtonType.OK).isDisabled());

        Hyperlink ver = (Hyperlink) panel.lookupAll(".hyperlink").stream()
            .filter(n -> n instanceof Hyperlink h && h.getText().startsWith("Ver sus bienes")).findFirst().orElseThrow();
        Window ventana = panel.getScene().getWindow();
        interact(ver::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !vistos.isEmpty());
        assertEquals(List.of("ANA RUIZ"), vistos, "ya cerrada la ventana");
        assertFalse(ventana.isShowing());
    }

    @Test
    void sinPersonaPedida_abreSinNadieElegido() throws Exception {
        Platform.runLater(() -> FichaPersona.mostrar(List.<Resguardo>of(), List.of(bien("1", "ANA RUIZ")), null, escena));
        DialogPane panel = abierto();
        assertTrue(panel.lookupButton(ButtonType.OK).isDisabled());
    }

    /** One click on a name opens the person; a double click stays "ver detalle". */
    @Test
    void enLaTabla_unClicAbre_yElDobleClicNo() throws Exception {
        List<String> abiertos = new ArrayList<>();
        TableView<String> tabla = new TableView<>(FXCollections.observableArrayList("ANA RUIZ", "—"));
        TableColumn<String, String> col = new TableColumn<>("Resguardante");
        col.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()));
        col.setPrefWidth(300);
        FichaPersona.columnaDeNombre(col, (nombre, s) -> abiertos.add(nombre));
        tabla.getColumns().add(col);
        interact(() -> escena.setRoot(new StackPane(tabla)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(1, lookup(".celda-persona").queryAll().stream().filter(n -> n.isVisible() && !((Label) n).getText().isEmpty()).count(),
            "el guion no es una persona");
        Label nombre = (Label) lookup(".celda-persona").queryAll().stream()
            .filter(n -> "ANA RUIZ".equals(((Label) n).getText())).findFirst().orElseThrow();

        doubleClickOn(nombre);
        Thread.sleep(500);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(abiertos.isEmpty(), "doble clic: lo atiende la tabla");

        clickOn(nombre);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !abiertos.isEmpty());
        assertEquals(List.of("ANA RUIZ"), abiertos);
    }

    @Test
    void ctrlK_encuentraALaPersona_enCualquierOrden_yCuentaSusBienes() {
        List<Producto> bienes = List.of(bien("1", "ANA RUIZ SOTO"), bien("2", "ana ruiz soto"), bien("3", "LUIS SOTO"));
        var hallados = SearchPaletteDialog.personas(bienes, List.of(), "soto ruiz");
        assertEquals(1, hallados.size());
        assertEquals("ANA RUIZ SOTO", hallados.get(0).nombre());
        assertTrue(hallados.get(0).detalle().contains("2 bienes"), hallados.get(0).detalle());
        assertEquals(2, SearchPaletteDialog.personas(bienes, List.of(), "soto").size());
    }
}
