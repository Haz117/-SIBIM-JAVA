package com.sibim.controller;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The bienes in a resguardo's detail: the name used to take the whole table and leave
 *  Código and Valor as thin lines at the edge, so the code couldn't be read. */
class DetalleResguardoTablaTest extends ApplicationTest {

    private Stage ventana;

    @Override
    public void start(Stage stage) {
        ventana = stage;
    }

    @BeforeEach
    void entrar() {
        DatabaseConfig.setDemoMode(true);
        usuario(Rol.ADMIN);
    }

    @AfterEach
    void salir() {
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    private static void usuario(Rol rol) {
        Usuario u = new Usuario();
        u.setId("u"); u.setNombre("Prueba"); u.setRol(rol);
        SessionManager.setCurrentUser(u);
    }

    private static ResguardoItem renglon(String area) {
        ResguardoItem i = new ResguardoItem();
        i.setProductoCodigo("TES/20");
        i.setProductoNombre("ESCRITORIO CON CAJONES. MEDIDAS: ANCHO 120 CM X 75.3 DE ALTO X 60 DE PROFUNDIDAD, COLOR NOGAL");
        i.setArea(area);
        i.setValorUnitario(new BigDecimal("1500"));
        return i;
    }

    private TableView<ResguardoItem> mostrar(ResguardoItem item, String areaDelResguardo) {
        TableView<ResguardoItem> tabla = ResguardosController.tablaDeBienes(FXCollections.observableArrayList(item), areaDelResguardo);
        interact(() -> {
            ventana.setScene(new Scene(tabla, 716, 220));
            ventana.show();
        });
        WaitForAsyncUtils.waitForFxEvents();
        return tabla;
    }

    private static List<String> titulos(TableView<ResguardoItem> t) {
        return t.getColumns().stream().map(TableColumn::getText).toList();
    }

    @Test
    void elCodigoYElValorTienenSuAncho_yElNombreTomaElResto() {
        TableView<ResguardoItem> t = mostrar(renglon("Tesorería"), "Tesorería");
        assertEquals(List.of("Código", "Bien", "Valor"), titulos(t), "el área del resguardo no se repite");
        assertTrue(t.getColumns().get(0).getWidth() >= 100, "Código: " + t.getColumns().get(0).getWidth());
        assertTrue(t.getColumns().get(2).getWidth() >= 100, "Valor: " + t.getColumns().get(2).getWidth());
        double total = t.getColumns().stream().mapToDouble(TableColumn::getWidth).sum();
        assertTrue(t.getColumns().get(1).getWidth() > 300, "Bien: " + t.getColumns().get(1).getWidth());
        assertTrue(total <= t.getWidth(), "sin barra horizontal: " + total + " de " + t.getWidth());
    }

    @Test
    void unBienDeOtraArea_loDice() {
        assertEquals(List.of("Código", "Bien", "Área", "Valor"), titulos(mostrar(renglon("Contraloría"), "Tesorería")));
    }

    @Test
    void sinPermisoDeVerImportes_noHayColumnaDeValor() {
        usuario(Rol.DIRECCION);
        assertEquals(List.of("Código", "Bien"), titulos(mostrar(renglon("Tesorería"), "Tesorería")));
    }
}
