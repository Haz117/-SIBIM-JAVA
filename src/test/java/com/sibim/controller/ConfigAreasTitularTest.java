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

    private TextField editar(int fila) throws Exception {
        Node n = celda(fila);
        clickOn(n);
        WaitForAsyncUtils.waitForFxEvents();
        doubleClickOn(n);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> celda(fila).isEditing());
        WaitForAsyncUtils.waitForFxEvents();
        return (TextField) celda(fila).getGraphic();
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
        type(KeyCode.ENTER);
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
        clickOn(celda(3));
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
        clickOn(celda(ultima));
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(400);
        verify(config, never()).set(argThat(clave -> !editada.equals(clave)), anyString());
    }

    @Test
    void escape_noGuarda_yRegresaElGuion() throws Exception {
        TextField campo = editar(2);
        interact(() -> campo.setText("NO VA"));
        type(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitForFxEvents();
        verify(config, never()).set(anyString(), anyString());
        assertEquals("—", celda(2).getText());
    }

    @Test
    void sinCambios_noEscribeEnLaBase() throws Exception {
        editar(4);
        type(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(300);
        verify(config, never()).set(anyString(), anyString());
    }
}
