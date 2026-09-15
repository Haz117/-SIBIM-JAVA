package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for MovimientosController — this controller has no test coverage
 * despite being one of the largest in the app (paginación, filtros, chips de
 * tipo, exportación en un solo archivo de 1000+ líneas). This does not cover
 * that surface exhaustively; it only guards the load path (table gets
 * populated from demo data) and the most basic filter interaction, so a
 * future refactor has at least one tripwire against an obviously broken
 * screen.
 */
class MovimientosControllerSmokeTest extends ApplicationTest {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(new Usuario(
            "test-admin", "admin.test", "hash",
            "Admin Test", "Administrador", Rol.ADMIN, null,
            LocalDateTime.now()));

        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/fxml/movimientos.fxml"));
        Parent root = loader.load();
        stage.setScene(new Scene(root, 1200, 760));
        stage.show();
        stage.toFront();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private TableView<?> table() {
        return lookup(".table-view").queryTableView();
    }

    // TestFX keeps a single Stage/controller instance alive across every
    // @Test in this class (start() runs once), so tests that mutate shared
    // UI state (like typing into the search field) must clean up after
    // themselves — otherwise a later test sees the mutated state instead of
    // a fresh load.

    @Test
    void alCargar_tablaYEstadisticasSePueblanConDatosDemo() throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
            () -> !table().getItems().isEmpty());
        assertFalse(table().getItems().isEmpty(),
            "La tabla de movimientos debe poblarse con datos demo al cargar");

        Label lblTotal = lookup("#lblStatTotalMov").queryAs(Label.class);
        assertNotNull(lblTotal);
        assertFalse(lblTotal.getText().isBlank(),
            "La tarjeta de total de movimientos debe mostrar un valor tras cargar");
    }

    @Test
    void buscar_filtraLaTablaYLimpiarLaRestaura() throws Exception {
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
            () -> !table().getItems().isEmpty());
        int totalInicial = table().getItems().size();
        assertTrue(totalInicial > 0, "Debia haber datos antes de filtrar");

        TextField search = lookup("#searchField").queryAs(TextField.class);
        try {
            interact(() -> search.setText("producto-que-no-existe-xyz-123"));
            WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> table().getItems().isEmpty());
            assertTrue(table().getItems().isEmpty(),
                "Una busqueda sin coincidencias debe dejar la tabla vacia");
        } finally {
            // Restore shared state for any test that runs after this one.
            interact(() -> search.setText(""));
            WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> !table().getItems().isEmpty());
        }
    }
}
