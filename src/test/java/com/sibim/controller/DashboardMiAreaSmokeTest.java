package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Inicio of an área account starts with "Pendientes de tu área": what
 *  its bienes lack (photo, ubicación, estado físico), each a click away. */
class DashboardMiAreaSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("smoke-dir-mi-area");
        u.setUsername("direccion");
        u.setNombre("Dirección de prueba");
        u.setRol(Rol.DIRECCION);
        u.setArea("Tesorería Municipal");
        SessionManager.setCurrentUser(u);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/dashboard.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void unaDireccion_veSusPendientes_yCadaUnoAbreAlgo() {
        VBox seccion = lookup("#miAreaSection").query();
        assertTrue(seccion.isVisible() && seccion.isManaged());
        FlowPane tarjetas = (FlowPane) seccion.getChildren().get(1);
        for (int i = 0; i < 40 && tarjetas.getChildren().isEmpty(); i++) sleep(100);
        assertFalse(tarjetas.getChildren().isEmpty(), "una tarjeta por pendiente, o \"Todo al día\"");
        for (Node t : tarjetas.getChildren()) {
            boolean todoAlDia = t.getStyleClass().contains("quick-action-card-static");
            assertEquals(!todoAlDia, t.getOnMouseClicked() != null, "cada pendiente se abre con un clic (y con Enter)");
        }
    }

    /** A dirección doesn't see amounts: no "Valor patrimonial" card showing
     *  "—"; the other three figures share the row instead of leaving a gap. */
    @Test
    void unaDireccion_noVeLaTarjetaDeValor_yLasOtrasTresLlenanLaFila() {
        javafx.scene.layout.GridPane cifras = lookup("#statsGrid").query();
        assertTrue(lookup("#cardValorTotal").tryQuery().isEmpty());
        assertEquals(3, cifras.getChildren().size());
        assertEquals(3, cifras.getColumnConstraints().size());
        for (Node tarjeta : cifras.getChildren()) {
            Integer columna = javafx.scene.layout.GridPane.getColumnIndex(tarjeta);
            assertTrue(columna != null && columna < 3, "sin hueco donde estaba la tarjeta de valor");
        }
    }

    @Test
    void losPendientes_sonLoQueFaltaDeVerdad() {
        com.sibim.model.Producto completo = new com.sibim.model.Producto();
        completo.setFotoUrl("https://x/foto.jpg");
        completo.setUbicacion("Oficina 2");
        completo.setEstadoFisico("BUENO");
        com.sibim.model.Producto vacio = new com.sibim.model.Producto();
        vacio.setUbicacion("   ");
        for (MiAreaPanel.Pendiente p : MiAreaPanel.PENDIENTES) {
            assertFalse(p.falta().test(completo), p.titulo());
            assertTrue(p.falta().test(vacio), p.titulo());
        }
    }
}
