package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.model.enums.TipoMovimiento;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** All the movements in production are transfers, and the column said "1 · 1" for each:
 *  it now says the número before and after and where the bien went. */
class MovimientosQueCambioTest extends ApplicationTest {

    private TableView<Movimiento> tabla;

    private static Movimiento movimiento(TipoMovimiento tipo) {
        Movimiento m = new Movimiento();
        m.setTipo(tipo);
        m.setStockAnterior(4);
        m.setStockNuevo(tipo == TipoMovimiento.ENTRADA ? 6 : 4);
        return m;
    }

    @Override
    public void start(Stage stage) {
        tabla = new TableView<>();
        TableColumn<Movimiento, String> col = new TableColumn<>("Qué cambió");
        col.setPrefWidth(260);
        MovimientosColumnSetup.configureStock(col);
        tabla.getColumns().add(col);
        stage.setScene(new Scene(tabla, 300, 200));
        stage.show();
    }

    private List<String> textos() {
        WaitForAsyncUtils.waitForFxEvents();
        return lookup(n -> n instanceof Label).queryAll().stream().map(n -> ((Label) n).getText()).toList();
    }

    @Test
    void unaTransferencia_diceSusNumerosYAdondeFue() {
        Movimiento t = movimiento(TipoMovimiento.TRANSFERENCIA);
        t.setCodigoAnterior("IMM/20");
        t.setCodigoNuevo("CONT/14");
        t.setAreaOrigen("Instancia Municipal de la Mujer");
        t.setAreaDestino("Contraloría Municipal");
        interact(() -> tabla.getItems().setAll(t));
        assertTrue(textos().containsAll(List.of("IMM/20 → CONT/14", "a Contraloría Municipal")), textos().toString());
    }

    @Test
    void lasDemas_siguenDiciendoLaCantidad() {
        interact(() -> tabla.getItems().setAll(movimiento(TipoMovimiento.ENTRADA)));
        assertTrue(textos().containsAll(List.of("4", "↑", "6")), textos().toString());
    }
}
