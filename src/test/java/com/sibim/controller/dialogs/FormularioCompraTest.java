package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 12-nov-2026: «Datos Patrimoniales» starts with the purchase, in the order of the factura,
 *  and the costo moved there from the other tab. */
class FormularioCompraTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    @Test
    void laCompraVaPrimero_yElCostoEntreLaFechaYLaVidaUtil_tambienConTab() {
        Producto p = new Producto();
        p.setId("p-01"); p.setNombre("Silla"); p.setCodigo("PRES/03"); p.setPrecioCompra(new java.math.BigDecimal("1845.00"));
        ProductoTabStockFields[] st = new ProductoTabStockFields[1];
        ProductoTabPatrimonioFields[] pt = new ProductoTabPatrimonioFields[1];
        interact(() -> {
            st[0] = new ProductoTabStockFields(p);
            pt[0] = new ProductoTabPatrimonioFields(p, List.of(), List.of(), List.of(), List.of(), null);
            pt[0].ponerCosto(st[0].etiquetaCosto, st[0].cajaCosto);
        });
        List<Node> hijos = pt[0].grid.getChildren();
        int factura = hijos.indexOf(pt[0].fNumeroFactura), folio = hijos.indexOf(pt[0].fFolioFiscal),
            proveedor = hijos.indexOf(pt[0].fProveedor), fecha = hijos.indexOf(pt[0].fFechaAdq),
            costo = hijos.indexOf(st[0].cajaCosto), vida = hijos.indexOf(pt[0].fVidaUtil), color = hijos.indexOf(pt[0].fColor);
        assertTrue(factura < folio && folio < proveedor && proveedor < fecha && fecha < costo && costo < vida && vida < color,
            "Tab sigue el orden de la factura: " + List.of(factura, folio, proveedor, fecha, costo, vida, color));
        assertEquals(javafx.scene.layout.GridPane.getRowIndex(pt[0].fFechaAdq) + 1, javafx.scene.layout.GridPane.getRowIndex(st[0].cajaCosto));
        assertFalse(st[0].grid.lookupAll(".text-field").contains(st[0].fPrecioC), "el costo ya no está en la otra pestaña");
    }
}
