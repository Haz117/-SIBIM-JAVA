package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** From 13 to 17-nov-2026, 173 of 437 edits typed again exactly what the bien before had:
 *  «Copiar de …» fills what is empty with the bien saved just before, and puts it back. */
class CopiarAnteriorTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    @AfterEach
    void limpiar() {
        BienAnterior.olvidar();
        SessionManager.logout();
    }

    private static Producto anterior() {
        Producto a = new Producto();
        a.setId("ant"); a.setCodigo("TES/24"); a.setNombre("ARCHIVERO METALICO 5 GAVETAS");
        a.setNumeroFactura("3954"); a.setFolioFiscal("9D23097F-A88A-4ADB-B318-300000000001");
        a.setProveedor("PAPELERIA DE EJEMPLO SA DE CV"); a.setFechaAdquisicion(LocalDate.of(2024, 3, 14));
        a.setColor("GRIS"); a.setTipoBien("MUEBLE"); a.setVidaUtilAnios(10);
        return a;
    }

    private ProductoTabPatrimonioFields campos(Producto este, Producto anterior) {
        ProductoTabPatrimonioFields[] f = new ProductoTabPatrimonioFields[1];
        interact(() -> {
            f[0] = new ProductoTabPatrimonioFields(este, List.of(), List.of(), List.of(), List.of(), null);
            f[0].ofrecerAnterior(anterior, este != null ? este.getId() : null);
        });
        return f[0];
    }

    @Test
    void llenaSoloLoVacio_yQuitarLoRegresa() {
        Producto este = new Producto();
        este.setId("est"); este.setCodigo("TES/90"); este.setNombre("ARCHIVERO METALICO 5 GAVETAS");
        este.setProveedor("OTRO PROVEEDOR");
        este.setClaveArmonizada("1.2.4.1.1.511");   // its tipo comes proposed: MUEBLE
        ProductoTabPatrimonioFields f = campos(este, anterior());

        assertTrue(f.lnkAnterior.isVisible());
        assertEquals("Copiar de TES/24: factura, folio fiscal, fecha, color y vida útil  (Ctrl+D)", f.lnkAnterior.getText(),
            "ni el proveedor que ya tiene ni el tipo, que ya dice lo mismo");

        interact(f::copiarAnterior);
        assertEquals("3954", f.fNumeroFactura.getText());
        assertEquals("9D23097F-A88A-4ADB-B318-300000000001", f.fFolioFiscal.getText());
        assertEquals("OTRO PROVEEDOR", f.fProveedor.getText(), "lo escrito se queda");
        assertEquals(LocalDate.of(2024, 3, 14), f.fFechaAdq.getValue());
        assertEquals("GRIS", f.fColor.getText());
        assertEquals(10, f.vidaUtil());
        assertTrue(f.lnkAnterior.getText().startsWith("Quitar lo copiado de TES/24 (5 datos)"), f.lnkAnterior.getText());

        interact(f::copiarAnterior);
        assertEquals("", f.fNumeroFactura.getText());
        assertEquals("", f.fFolioFiscal.getText());
        assertNull(f.fFechaAdq.getValue());
        assertEquals("", f.fColor.getText());
        assertNull(f.vidaUtil());
        assertEquals("OTRO PROVEEDOR", f.fProveedor.getText());
        assertTrue(f.lnkAnterior.getText().startsWith("Copiar de TES/24"));
    }

    @Test
    void elTipoPropuesto_cedeAlDelAnterior() {
        Producto este = new Producto();
        este.setId("est2"); este.setCodigo("SERM/9"); este.setClaveArmonizada("1.2.4.6.7.567");
        Producto a = anterior();
        a.setTipoBien("HERRAMIENTA");
        ProductoTabPatrimonioFields f = campos(este, a);
        assertEquals("MUEBLE", f.fTipoBien.getText());
        interact(f::copiarAnterior);
        assertEquals("HERRAMIENTA", f.fTipoBien.getText());
        interact(f::copiarAnterior);
        assertEquals("MUEBLE", f.fTipoBien.getText(), "regresa a lo que proponía");
    }

    @Test
    void noSeOfrece_sinAnterior_aSiMismo_niConTodoLleno() {
        assertFalse(campos(null, null).lnkAnterior.isVisible());
        Producto a = anterior();
        Producto mismo = a.copia();
        mismo.setNumeroFactura(null);
        assertFalse(campos(mismo, a).lnkAnterior.isVisible(), "el que se acaba de guardar no se copia a sí mismo");
        Producto lleno = anterior();
        lleno.setId("otro");
        assertFalse(campos(lleno, a).lnkAnterior.isVisible(), "no le falta nada");
    }

    @Test
    void soloElDeLaMismaCuenta() {
        Usuario u1 = new Usuario(); u1.setId("u1"); u1.setUsername("patrimonio"); u1.setRol(Rol.ADMIN);
        Usuario u2 = new Usuario(); u2.setId("u2"); u2.setUsername("otra"); u2.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(u1);
        BienAnterior.recordar(anterior());
        assertEquals("TES/24", BienAnterior.get().getCodigo());
        SessionManager.setCurrentUser(u2);
        assertNull(BienAnterior.get());
    }
}
