package com.sibim.util;

import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 5-nov-2026: the filters can be typed in to find an área or a person faster.
 *  The fields are driven directly, so the test doesn't depend on window focus. */
class FiltroEscribibleTest extends ApplicationTest {

    private ComboBox<String> filtro;
    private ComboBox<String> persona;
    private final List<String> cambios = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        filtro = new ComboBox<>();
        filtro.getItems().setAll("Dirección de Catastro", "Contraloría Municipal", "Sistema DIF Municipal");
        FiltroEscribible.instalar(filtro, "Todas las áreas");
        filtro.valueProperty().addListener((o, a, b) -> cambios.add(String.valueOf(b)));
        persona = new ComboBox<>();
        persona.getItems().setAll("ANA RUIZ SOTO", "JUAN PÉREZ LUNA", "ROSA LUNA GIL");
        persona.setEditable(true);
        FiltroEscribible.sugerirAlEscribir(persona);
        stage.setScene(new Scene(new VBox(filtro, persona), 420, 300));
        stage.show();
    }

    @Test
    void vacioEsTodas_yAlEscribirSeAcortaLaLista() {
        assertNull(filtro.getValue());
        assertEquals("Todas las áreas", filtro.getEditor().getPromptText());
        assertEquals("", filtro.getEditor().getText());

        interact(() -> filtro.getEditor().setText("contra"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("Contraloría Municipal"), filtro.getItems());

        interact(() -> filtro.getEditor().setText("municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, filtro.getItems().size(), "cada palabra, sin acentos");
        assertEquals(3, FiltroEscribible.opciones(filtro).size(), "las opciones siguen completas");
    }

    @Test
    void elegirYLuegoBorrar_regresaATodas() {
        interact(() -> filtro.setValue("Sistema DIF Municipal"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Sistema DIF Municipal", filtro.getEditor().getText());
        interact(() -> filtro.getEditor().clear());
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(filtro.getValue(), "el campo vacío es «Todas»");
    }

    @Test
    void opcionesNuevas_conservanLaElegidaSiSigue() {
        interact(() -> filtro.setValue("Contraloría Municipal"));
        interact(() -> FiltroEscribible.opciones(filtro, List.of("Contraloría Municipal", "Tesorería Municipal")));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Contraloría Municipal", filtro.getValue());
        assertEquals(List.of("Contraloría Municipal", "Tesorería Municipal"), FiltroEscribible.opciones(filtro));
        interact(() -> FiltroEscribible.opciones(filtro, List.of("Tesorería Municipal")));
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(filtro.getValue(), "la que ya no está se suelta");
    }

    /** The screens install it after their data loads, when the combo is already
     *  on screen (skinned, editable): its field's prompt is bound by then. */
    @Test
    void instalarEnUnoYaEnPantalla_yVolverAInstalar() {
        ComboBox<String> enPantalla = new ComboBox<>();
        interact(() -> {
            ((VBox) filtro.getParent()).getChildren().add(enPantalla);
            enPantalla.setEditable(true);
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(enPantalla.getSkin(), "la prueba necesita el combo ya dibujado");
        interact(() -> {
            enPantalla.getItems().setAll("Bienes Muebles", "Vehículos");
            FiltroEscribible.instalar(enPantalla, "Categoría…");
        });
        interact(() -> {
            enPantalla.getItems().setAll("Bienes Muebles", "Vehículos", "Activos Biológicos");
            FiltroEscribible.instalar(enPantalla, "Categoría…");
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("Categoría…", enPantalla.getEditor().getPromptText());
        assertEquals(3, FiltroEscribible.opciones(enPantalla).size());
    }

    @Test
    void personaNueva_seQueda_yLasConocidasSeSugieren() {
        interact(() -> persona.getEditor().setText("luna"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("JUAN PÉREZ LUNA", "ROSA LUNA GIL"), persona.getItems());
        assertEquals("luna", persona.getEditor().getText(), "lo escrito se queda");
        interact(() -> persona.getEditor().setText("MARIO NUEVO"));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(persona.getItems().isEmpty());
        assertEquals("MARIO NUEVO", persona.getEditor().getText(), "una persona que no está en la lista también se puede escribir");
    }
}
