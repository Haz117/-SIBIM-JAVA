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
        int factura = hijos.indexOf(pt[0].fNumeroFactura), folio = hijos.indexOf(pt[0].fFolioFiscal.getParent()),
            proveedor = hijos.indexOf(pt[0].fProveedor), fecha = hijos.indexOf(pt[0].fFechaAdq),
            costo = hijos.indexOf(st[0].cajaCosto), vida = hijos.indexOf(pt[0].fVidaUtil);
        assertTrue(factura >= 0 && factura < folio && folio < proveedor && proveedor < fecha && fecha < costo && costo < vida,
            "Tab sigue el orden de la factura: " + List.of(factura, folio, proveedor, fecha, costo, vida));
        // 17-nov-2026: «El bien» is its own group, beside «Compra» on a wide screen; Tab still goes
        // from the purchase to the bien, then where it is, its maintenance and the vehicle's papers.
        assertTrue(pt[0].gridBien.getChildren().contains(pt[0].fColor));
        List<Node> orden = new java.util.ArrayList<>();
        recorrer(pt[0].vista, orden);
        int compra = orden.indexOf(pt[0].grid), bien = orden.indexOf(pt[0].gridBien), donde = orden.indexOf(pt[0].gridDonde),
            mant = orden.indexOf(pt[0].gridMant), veh = orden.indexOf(pt[0].gridVeh);
        assertTrue(compra >= 0 && compra < bien && bien < donde && donde < mant && mant < veh,
            "Compra, El bien, Dónde está, Mantenimiento, Vehículos: " + List.of(compra, bien, donde, mant, veh));
        assertEquals(javafx.scene.layout.GridPane.getRowIndex(pt[0].fFechaAdq) + 1, javafx.scene.layout.GridPane.getRowIndex(st[0].cajaCosto));
        assertFalse(st[0].grid.lookupAll(".text-field").contains(st[0].fPrecioC), "el costo ya no está en la otra pestaña");
    }

    /** The nodes in the order Tab visits them (the scene graph's, depth first). */
    private static void recorrer(Node n, List<Node> orden) {
        orden.add(n);
        if (n instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) recorrer(c, orden);
    }
}
