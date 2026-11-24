package com.sibim.controller;

import com.sibim.model.Movimiento;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 19-nov-2026: Movimientos sorted its date as text («31/10/2026» above «13/11/2026»), Depreciación
 *  said «1 años», and its chart's axis said «30,000,000» in half the width. */
class MovimientosYDepreciacionTextosTest {

    private static LocalDateTime valor(TableColumn<Movimiento, LocalDateTime> col, LocalDateTime cuando) {
        Movimiento m = new Movimiento();
        m.setCreadoEn(cuando);
        return col.getCellValueFactory().call(new TableColumn.CellDataFeatures<>(null, col, m)).getValue();
    }

    @Test
    void cuando_ordenaComoFecha() {
        TableColumn<Movimiento, LocalDateTime> col = new TableColumn<>("Cuándo");
        MovimientosColumnSetup.configureFecha(col);
        LocalDateTime octubre = valor(col, LocalDateTime.of(2026, 10, 31, 9, 0));
        LocalDateTime noviembre = valor(col, LocalDateTime.of(2026, 11, 13, 14, 40));
        assertTrue(col.getComparator().compare(octubre, noviembre) < 0, "el 31 de octubre va antes que el 13 de noviembre");
    }

    @Test
    void elEstado_enPalabras() {
        assertEquals("Aprobado", MovimientosColumnSetup.estadoEnPalabras(Movimiento.ESTADO_APROBADO));
        assertEquals("Pendiente: espera que el área lo reciba", MovimientosColumnSetup.estadoEnPalabras(Movimiento.ESTADO_PENDIENTE));
        assertEquals("Rechazado", MovimientosColumnSetup.estadoEnPalabras(Movimiento.ESTADO_RECHAZADO));
    }

    @Test
    void depreciacion_montosCortosYSingulares() {
        assertEquals("$30 M", DepreciacionController.montoCorto(30_000_000));
        assertEquals("$7.5 M", DepreciacionController.montoCorto(7_500_000));
        assertEquals("$850 mil", DepreciacionController.montoCorto(850_000));
        assertEquals("$900", DepreciacionController.montoCorto(900));
        assertEquals("1 año", DepreciacionController.anios(1));
        assertEquals("3 años", DepreciacionController.anios(3));
        assertEquals("—", DepreciacionController.anios(null));
    }
}
