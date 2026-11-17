package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «MUEBLE» was typed by hand 351 times from 28-oct to 13-nov-2026: an empty tipo de bien
 *  comes written from the CONAC account, says so, and never overwrites what someone wrote. */
class TipoPropuestoTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    private ProductoTabPatrimonioFields campos(String clave, String tipo) {
        Producto p = null;
        if (clave != null || tipo != null) {
            p = new Producto();
            p.setId("p-1"); p.setNombre("SILLA"); p.setCodigo("PRES/1");
            p.setClaveArmonizada(clave);
            p.setTipoBien(tipo);
        }
        Producto bien = p;
        ProductoTabPatrimonioFields[] f = new ProductoTabPatrimonioFields[1];
        interact(() -> f[0] = new ProductoTabPatrimonioFields(bien, List.of(), List.of(), List.of(), List.of(), null));
        return f[0];
    }

    @Test
    void sinTipo_vieneEscritoPorSuClave_yLoDice() {
        ProductoTabPatrimonioFields f = campos("1.2.4.1.1.511", null);
        assertEquals("MUEBLE", f.fTipoBien.getText());
        assertTrue(f.lblTipoPropuesto.isVisible());
        interact(() -> f.fTipoBien.setText("OFICINA"));
        assertFalse(f.lblTipoPropuesto.isVisible(), "ya no es el propuesto");
    }

    @Test
    void elQueTiene_seQueda_yUnVehiculoNoLleva() {
        assertEquals("Camioneta", campos("1.2.4.4.1.541", "Camioneta").fTipoBien.getText());
        assertEquals("OFICINA", campos("1.2.4.1.1.511", "OFICINA").fTipoBien.getText());
        ProductoTabPatrimonioFields vehiculo = campos("1.2.4.4.1.541", null);
        assertEquals("", vehiculo.fTipoBien.getText());
        assertFalse(vehiculo.lblTipoPropuesto.isVisible());
    }

    @Test
    void sigueALaClave_mientrasNadieEscribaOtro() {
        ProductoTabPatrimonioFields f = campos(null, null);
        assertEquals("", f.fTipoBien.getText(), "un bien nuevo sin categoría todavía no");
        interact(() -> f.fClaveArm.setText("1.2.4.1.3.515"));
        assertEquals("MUEBLE", f.fTipoBien.getText(), "al elegir la categoría");
        interact(() -> f.fClaveArm.setText("1.2.4.4.1.541"));
        assertEquals("", f.fTipoBien.getText(), "era el propuesto: un vehículo no lleva MUEBLE");
        interact(() -> f.fTipoBien.setText("Camioneta"));
        interact(() -> f.fClaveArm.setText("1.2.4.1.1.511"));
        assertEquals("Camioneta", f.fTipoBien.getText(), "lo escrito no se cambia");
    }
}
