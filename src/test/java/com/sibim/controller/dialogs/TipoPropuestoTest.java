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

    /** 18-nov-2026: «NEGRO» was the tipo of 6 sillas; «MUEBLE» was also «mueble», «MUBLE»… */
    @Test
    void unColorComoTipo_loDice_yLaListaEscribeElTipo() {
        ProductoTabPatrimonioFields f = campos("1.2.4.1.1.511", null);
        interact(() -> f.fTipoBien.setText("NEGRO"));
        assertTrue(f.lblTipoPropuesto.isVisible());
        assertTrue(f.lblTipoPropuesto.getText().contains("Parece un color"), f.lblTipoPropuesto.getText());
        assertTrue(f.lblTipoPropuesto.getStyleClass().contains("field-hint-warn"));

        assertTrue(f.btnTipos.isDisable(), "sin lista todavía");
        interact(() -> f.sugerirColorYTipo(List.of(), List.of("MUEBLE", "Camioneta", "Camión")));
        assertFalse(f.btnTipos.isDisable());
        assertEquals(List.of("MUEBLE", "Camioneta", "Camión"), f.btnTipos.getItems().stream().map(javafx.scene.control.MenuItem::getText).toList());
        interact(() -> f.btnTipos.getItems().get(1).fire());
        assertEquals("Camioneta", f.fTipoBien.getText());
        assertFalse(f.lblTipoPropuesto.isVisible());

        interact(() -> { f.fTipoBien.setText("camion"); f.escribirTipoComoLaLista(); });
        assertEquals("Camión", f.fTipoBien.getText(), "como lo escribe la lista");
        interact(() -> { f.fTipoBien.setText("3/2 ton"); f.escribirTipoComoLaLista(); });
        assertEquals("3/2 ton", f.fTipoBien.getText(), "lo que no está en la lista se queda");
        interact(() -> { f.fTipoBien.setText("mueble"); f.fTipoBien.setEditable(false); f.escribirTipoComoLaLista(); });
        assertEquals("mueble", f.fTipoBien.getText(), "un campo que no se puede cambiar no se toca");
        assertTrue(f.btnTipos.isDisable());
    }

    /** 18-nov-2026: muebles 5 years in 75 %: offered under an empty vida útil, one click. */
    @Test
    void vidaUtilVacia_ofreceLaDeLaMayoriaDeSuClave() {
        ProductoTabPatrimonioFields f = campos("1.2.4.1.1.511", null);
        interact(() -> f.ofrecerVidaComun(java.util.Map.of("1.2.4.1.1", 5)));
        assertTrue(f.lnkVidaComun.isVisible());
        assertTrue(f.lnkVidaComun.getText().contains("5 años"), f.lnkVidaComun.getText());
        interact(f.lnkVidaComun::fire);
        assertEquals(5, f.fVidaUtil.getValue());
        assertFalse(f.lnkVidaComun.isVisible(), "ya tiene");

        ProductoTabPatrimonioFields otra = campos("1.2.4.1.3.515", null);
        interact(() -> otra.ofrecerVidaComun(java.util.Map.of("1.2.4.1.1", 5)));
        assertFalse(otra.lnkVidaComun.isVisible(), "su clave no tiene mayoría");
        ProductoTabPatrimonioFields sinClave = campos(null, null);
        interact(() -> sinClave.ofrecerVidaComun(java.util.Map.of("1.2.4.1.1", 5)));
        assertFalse(sinClave.lnkVidaComun.isVisible(), "un bien nuevo sin categoría todavía no");
    }

    /** Picking the category of a new bien: the municipio's criterion first, CONAC where none. */
    @Test
    void laVidaAlElegirCategoria_sigueLaMayoriaDeSuClave() {
        assertEquals(5, ProductoDialogFactory.vidaPara("1.2.4.1.1.511", java.util.Map.of("1.2.4.1.1", 5)));
        assertEquals(10, ProductoDialogFactory.vidaPara("1.2.4.1.1.511", java.util.Map.of()), "sin mayoría: CONAC");
        assertEquals(3, ProductoDialogFactory.vidaPara("1.2.4.1.3.515", java.util.Map.of("1.2.4.1.1", 5)));
        assertNull(ProductoDialogFactory.vidaPara(null, java.util.Map.of("1.2.4.1.1", 5)));
    }

    /** 6 to 18-nov-2026: 136 of 729 colors typed by hand were exactly what the name said. */
    @Test
    void colorVacio_ofreceElQueDiceSuNombre() {
        ProductoTabPatrimonioFields f = campos("1.2.4.1.1.511", null);
        javafx.beans.property.SimpleStringProperty nombre = new javafx.beans.property.SimpleStringProperty("SILLA OPERATIVA COLOR NEGRO");
        javafx.beans.property.SimpleStringProperty desc = new javafx.beans.property.SimpleStringProperty("");
        interact(() -> f.ofrecerColorDelNombre(nombre, desc));
        assertTrue(f.lnkColorDelNombre.isVisible());
        assertEquals("Su nombre dice: NEGRO", f.lnkColorDelNombre.getText());
        interact(f.lnkColorDelNombre::fire);
        assertEquals("NEGRO", f.fColor.getText());
        assertFalse(f.lnkColorDelNombre.isVisible(), "ya tiene color");
        interact(() -> { f.fColor.setText(""); nombre.set("ESCRITORIO DE MADERA"); });
        assertFalse(f.lnkColorDelNombre.isVisible(), "el nombre no dice color");
    }
}
