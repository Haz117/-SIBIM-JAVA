package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Reportes as a dirección sees it: its own listings and the baja formats, PDF only. */
class ReportesAreasSmokeTest extends ControllerSmokeTestBase {

    @Override
    public void start(Stage stage) throws Exception {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("smoke-dir-1");
        u.setUsername("direccion");
        u.setNombre("Dirección de prueba");
        u.setRol(Rol.DIRECCION);
        u.setArea("Tesorería Municipal");
        SessionManager.setCurrentUser(u);
        Parent root = new FXMLLoader(getClass().getResource("/fxml/reportes.fxml")).load();
        stage.setScene(new Scene(root, 1280, 800));
        stage.show();
        stage.toFront();
    }

    @Test
    void unaDireccion_veSusListadosYLosFormatosDeBaja_soloEnPdf() {
        List<String> titulos = new ArrayList<>();
        for (String lista : new String[]{"#listaListados", "#listaFormatos"})
            for (Node fila : lookup(lista).<VBox>query().getChildren()) {
                titulos.add(((Label) fila.lookup(".report-title")).getText());
                Node boton = ((HBox) fila).getChildren().get(2);
                if (boton instanceof MenuButton m)
                    m.getItems().forEach(it -> assertFalse(it.getText().matches("(?i).*(excel|csv).*"),
                        "las áreas no exportan hojas de cálculo: " + it.getText()));
            }
        assertEquals(List.of("Inventario General", "Distribución por Área", "Alertas y pendientes",
            "Solicitud de Baja", "Dictamen Técnico de Baja"), titulos);
    }
}
