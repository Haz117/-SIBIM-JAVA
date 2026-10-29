package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Editing a bien without vida útil saved 5 years every time (107 bienes by
 *  28-oct-2026, a laptop and a frigobar among them): the field now stays empty
 *  and saves nothing until someone types it. */
class VidaUtilCampoTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    private ProductoTabPatrimonioFields campos(Integer vidaUtil) {
        Producto p = new Producto();
        p.setId("p-01"); p.setNombre("Frigobar"); p.setCodigo("PRES/38");
        p.setVidaUtilAnios(vidaUtil);
        ProductoTabPatrimonioFields[] f = new ProductoTabPatrimonioFields[1];
        interact(() -> f[0] = new ProductoTabPatrimonioFields(p, List.of(), List.of(), List.of(), List.of(), null));
        return f[0];
    }

    @Test
    void sinVidaUtil_seQuedaSinVidaUtil() {
        ProductoTabPatrimonioFields f = campos(null);
        assertEquals("", f.fVidaUtil.getEditor().getText());
        assertEquals("Sin definir", f.fVidaUtil.getEditor().getPromptText());
        assertNull(f.vidaUtil(), "no se inventa los 5 años");
        Integer[] nuevo = { 0 };
        interact(() -> nuevo[0] = new ProductoTabPatrimonioFields(null, List.of(), List.of(), List.of(), List.of(), null).vidaUtil());
        assertNull(nuevo[0], "un bien nuevo tampoco");
    }

    @Test
    void laQueTiene_seConserva_yLaEscritaSeGuarda() {
        ProductoTabPatrimonioFields f = campos(10);
        assertEquals(10, f.vidaUtil());
        interact(() -> f.fVidaUtil.getEditor().setText("3"));
        assertEquals(3, f.vidaUtil(), "lo escrito cuenta aunque no se haya dado Enter");
        interact(() -> f.fVidaUtil.getEditor().setText(""));
        assertNull(f.vidaUtil(), "borrarla la quita");
    }

    @Test
    void lasFlechas_empiezanEn5_yNoSalenDe1a100() {
        ProductoTabPatrimonioFields f = campos(null);
        interact(() -> f.fVidaUtil.increment());
        assertEquals(5, f.vidaUtil());
        interact(() -> f.fVidaUtil.getValueFactory().setValue(1));
        interact(() -> f.fVidaUtil.decrement());
        assertEquals(1, f.vidaUtil());
        interact(() -> f.fVidaUtil.getEditor().setText("250"));
        assertEquals(100, f.vidaUtil());
    }
}
