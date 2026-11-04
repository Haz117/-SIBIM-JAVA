package com.sibim.controller;

import com.sibim.config.AreaCatalog;
import com.sibim.db.DatabaseConfig;
import com.sibim.repository.ConfiguracionRepository;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Configuración › Áreas: the titular is typed in the list. This needs a connection in the
 *  real screen, so it is edited here with the saving mocked; demo mode doesn't allow it. */
class ConfigAreasTitularTest extends ApplicationTest {

    private final ConfiguracionRepository config = mock(ConfiguracionRepository.class);
    private int guardados = 0;
    private TableView<AreaCatalog.Entrada> tabla;

    @Override
    @SuppressWarnings("unchecked")
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(false);
        when(config.findAll()).thenReturn(Map.of());
        VBox ancla = new VBox();
        VBox raiz = new VBox(ancla);
        new ConfigAreasSectionBuilder(ancla, () -> guardados++, config).build();
        Scene escena = new Scene(raiz, 1100, 500);
        stage.setScene(escena);
        stage.show();
        tabla = (TableView<AreaCatalog.Entrada>) raiz.lookup("#cardAreas").lookup(".table-view");
    }

    @AfterEach
    void salir() {
        interact(() -> Window.getWindows().stream().filter(w -> w.getScene().getRoot() instanceof DialogPane).toList().forEach(Window::hide));
    }

    @SuppressWarnings("unchecked")
    private TableCell<AreaCatalog.Entrada, String> celda(int fila) {
        return lookup(".table-cell").queryAll().stream()
            .filter(n -> n instanceof TableCell<?, ?> c && c.getTableColumn() != null && "Titular".equals(c.getTableColumn().getText()) && c.getIndex() == fila)
            .map(n -> (TableCell<AreaCatalog.Entrada, String>) n).findFirst().orElseThrow();
    }

    /** A click fired at the node itself, not with the mouse: the robot clicks wherever the
     *  pointer is, and a window on top of the screen (a full-screen one) took them. */
    private void clic(Node n, int veces) {
        interact(() -> {
            javafx.geometry.Bounds b = n.getLayoutBounds();
            javafx.geometry.Point2D escena = n.localToScene(b.getCenterX(), b.getCenterY());
            javafx.geometry.Point2D pantalla = n.localToScreen(b.getCenterX(), b.getCenterY());
            for (var tipo : java.util.List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED))
                n.fireEvent(new MouseEvent(tipo, escena.getX(), escena.getY(), pantalla.getX(), pantalla.getY(),
                    MouseButton.PRIMARY, veces, false, false, false, false, tipo == MouseEvent.MOUSE_PRESSED,
                    false, false, false, false, true, null));
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Double click, as the mouse sends it: one click, then the second with a count of 2. */
    private TextField editar(int fila) throws Exception {
        clic(celda(fila), 1);
        clic(celda(fila), 2);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> celda(fila).isEditing());
        WaitForAsyncUtils.waitForFxEvents();
        return (TextField) celda(fila).getGraphic();
    }

    /** A key on the field being edited, not on whatever window has the keyboard. */
    private void tecla(Node campo, KeyCode tecla) {
        interact(() -> {
            campo.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", tecla, false, false, false, false));
            campo.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", tecla, false, false, false, false));
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String area(int fila) { return tabla.getItems().get(fila).nombre(); }

    private static boolean hayFormulario() {
        return Window.getWindows().stream().anyMatch(w -> w.getScene().getRoot() instanceof DialogPane);
    }

    @Test
    void sinTitular_muestraGuion_peroEmpiezaVacio_yEnterGuarda_sinAbrirElFormulario() throws Exception {
        assertEquals("—", celda(0).getText());
        TextField campo = editar(0);
        assertEquals("", campo.getText(), "el guion no se vuelve un nombre");
        assertFalse(hayFormulario(), "doble clic en Titular no abre el formulario");

        interact(() -> campo.setText("  ANA RUIZ "));
        tecla(campo, KeyCode.ENTER);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> guardados == 1);
        WaitForAsyncUtils.waitForFxEvents();
        verify(config).set("titular_area:" + area(0), "ANA RUIZ");
        assertEquals("ANA RUIZ", celda(0).getText());
        assertFalse(hayFormulario(), "el Enter que guarda no abre el formulario");
    }

    @Test
    void clicEnOtraFila_guardaLoEscrito() throws Exception {
        TextField campo = editar(1);
        interact(() -> campo.setText("LUIS SOTO"));
        clic(celda(3), 1);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> guardados == 1);
        verify(config).set("titular_area:" + area(1), "LUIS SOTO");
    }

    /** The cell gets reused for another área while scrolling: nothing may land on that one. */
    @Test
    void siLaListaSeDesplaza_noEscribeEnOtraArea() throws Exception {
        String editada = "titular_area:" + area(1);
        TextField campo = editar(1);
        interact(() -> campo.setText("LUIS SOTO"));
        int ultima = tabla.getItems().size() - 1;
        interact(() -> tabla.scrollTo(ultima));
        WaitForAsyncUtils.waitForFxEvents();
        clic(celda(ultima), 1);
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(400);
        verify(config, never()).set(argThat(clave -> !editada.equals(clave)), anyString());
    }

    @Test
    void escape_noGuarda_yRegresaElGuion() throws Exception {
        TextField campo = editar(2);
        interact(() -> campo.setText("NO VA"));
        tecla(campo, KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        verify(config, never()).set(anyString(), anyString());
        assertEquals("—", celda(2).getText());
    }

    @Test
    void sinCambios_noEscribeEnLaBase() throws Exception {
        tecla(editar(4), KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(300);
        verify(config, never()).set(anyString(), anyString());
    }
}
