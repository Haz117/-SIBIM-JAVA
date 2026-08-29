package com.sibim.controller;

import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class MainControllerNavigationTest {

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // toolkit already running (e.g. started by a TestFX class earlier in the suite)
        }
    }

    @Test
    void resolveNavigationButton_returnsExpectedButton_forKnownAndUnknownViews() {
        Button dashboard = new Button();
        Button organigrama = new Button();
        Button productos = new Button();
        Button categorias = new Button();
        Button movimientos = new Button();
        Button alertas = new Button();
        Button reportes = new Button();
        Button configuracion = new Button();
        Button depreciacion = new Button();

        assertSame(configuracion, MainController.resolveNavigationButton(
            "configuracion", dashboard, organigrama, productos, categorias,
            movimientos, alertas, reportes, configuracion, depreciacion, dashboard));

        assertSame(dashboard, MainController.resolveNavigationButton(
            "vista_inexistente", dashboard, organigrama, productos, categorias,
            movimientos, alertas, reportes, configuracion, depreciacion, dashboard));
    }
}
