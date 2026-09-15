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
        Button dashboard     = new Button();
        Button organigrama   = new Button();
        Button productos     = new Button();
        Button categorias    = new Button();
        Button movimientos   = new Button();
        Button alertas       = new Button();
        Button reportes      = new Button();
        Button configuracion = new Button();
        Button depreciacion  = new Button();
        Button fallback      = new Button();

        // All 9 switch-mapped views
        assertSame(dashboard,     call("dashboard",     dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(organigrama,   call("organigrama",   dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(productos,     call("productos",     dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(categorias,    call("categorias",    dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(movimientos,   call("movimientos",   dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(alertas,       call("alertas",       dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(reportes,      call("reportes",      dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(configuracion, call("configuracion", dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(depreciacion,  call("depreciacion",  dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));

        // Unknown view → fallback
        assertSame(fallback, call("vista_inexistente", dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));

        // resguardos / prestamos / actas / auditoria are intercepted by navigateToView()
        // before reaching resolveNavigationButton — all fall through to fallback here
        assertSame(fallback, call("resguardos", dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(fallback, call("prestamos",  dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(fallback, call("actas",      dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
        assertSame(fallback, call("auditoria",  dashboard, organigrama, productos, categorias, movimientos, alertas, reportes, configuracion, depreciacion, fallback));
    }

    private static Button call(String view,
                                Button dashboard, Button organigrama, Button productos,
                                Button categorias, Button movimientos, Button alertas,
                                Button reportes, Button configuracion, Button depreciacion,
                                Button fallback) {
        return MainController.resolveNavigationButton(view,
            dashboard, organigrama, productos, categorias,
            movimientos, alertas, reportes, configuracion, depreciacion, fallback);
    }
}
