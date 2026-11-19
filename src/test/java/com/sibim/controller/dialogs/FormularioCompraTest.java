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
        // Some go in a box with the link under them (18-nov-2026: «Es el folio fiscal…», «Como la
        // mayoría de su clave…»): their place is the box's.
        java.util.function.Function<Node, Integer> lugar = n -> {
            while (n != null && n.getParent() != pt[0].grid) n = n.getParent();
            return hijos.indexOf(n);
        };
        int factura = lugar.apply(pt[0].fNumeroFactura), folio = lugar.apply(pt[0].fFolioFiscal),
            proveedor = lugar.apply(pt[0].fProveedor), fecha = lugar.apply(pt[0].fFechaAdq),
            costo = lugar.apply(st[0].cajaCosto), vida = lugar.apply(pt[0].fVidaUtil);
        assertTrue(factura >= 0 && factura < folio && folio < proveedor && proveedor < fecha && fecha < costo && costo < vida,
            "Tab sigue el orden de la factura: " + List.of(factura, folio, proveedor, fecha, costo, vida));
        // 17-nov-2026: «El bien» is its own group, beside «Compra» on a wide screen; Tab still goes
        // from the purchase to the bien, then where it is, its maintenance and the vehicle's papers.
        boolean colorEnElBien = false;
        for (Node n = pt[0].fColor; n != null; n = n.getParent()) if (n == pt[0].gridBien) colorEnElBien = true;
        assertTrue(colorEnElBien, "el color va en «El bien» (con «Su nombre dice…» debajo)");
        List<Node> orden = new java.util.ArrayList<>();
        recorrer(pt[0].vista, orden);
        int compra = orden.indexOf(pt[0].grid), bien = orden.indexOf(pt[0].gridBien), donde = orden.indexOf(pt[0].gridDonde),
            mant = orden.indexOf(pt[0].gridMant), veh = orden.indexOf(pt[0].gridVeh);
        assertTrue(compra >= 0 && compra < bien && bien < donde && donde < mant && mant < veh,
            "Compra, El bien, Dónde está, Mantenimiento, Vehículos: " + List.of(compra, bien, donde, mant, veh));
        assertEquals(javafx.scene.layout.GridPane.getRowIndex(pt[0].fFechaAdq) + 1, javafx.scene.layout.GridPane.getRowIndex(st[0].cajaCosto));
        assertFalse(st[0].grid.lookupAll(".text-field").contains(st[0].fPrecioC), "el costo ya no está en la otra pestaña");
    }

    /** 19-nov-2026: 285 of 296 valores residuales are the 15 % of the costo, worked out bien by bien. */
    @Test
    void elValorResidual_esEl15PorCientoDelCosto_ySigueAlCosto_peroNoAlQueSeEscribioDistinto() {
        com.sibim.model.Usuario admin = new com.sibim.model.Usuario();
        admin.setId("formulario-admin"); admin.setUsername("formulario"); admin.setRol(com.sibim.model.enums.Rol.ADMIN);
        com.sibim.model.Usuario antes = com.sibim.session.SessionManager.getCurrentUser();
        com.sibim.session.SessionManager.setCurrentUser(admin);
        try {
            Producto p = new Producto();
            p.setId("p-02"); p.setNombre("Silla"); p.setCodigo("PRES/04");
            p.setPrecioCompra(new java.math.BigDecimal("1000.00")); p.setValorResidual(java.math.BigDecimal.ZERO);
            ProductoTabStockFields[] st = new ProductoTabStockFields[1];
            ProductoTabPatrimonioFields[] pt = new ProductoTabPatrimonioFields[1];
            interact(() -> {
                st[0] = new ProductoTabStockFields(p);
                pt[0] = new ProductoTabPatrimonioFields(p, List.of(), List.of(), List.of(), List.of(), null);
                pt[0].ponerCosto(st[0].etiquetaCosto, st[0].cajaCosto);
                pt[0].ofrecerResidual(st[0].fPrecioC);
            });
            assertEquals(0, new java.math.BigDecimal(pt[0].fValorResidual.getText()).compareTo(new java.math.BigDecimal("150.00")),
                "con $0 se escribe el 15 % al abrir: " + pt[0].fValorResidual.getText());
            assertTrue(pt[0].lblResidualPropuesto.isVisible(), "dice que lo escribió SIBIM");
            assertFalse(pt[0].lnkResidual.isVisible(), "la liga sobra: ya es el 15 %");

            interact(() -> st[0].fPrecioC.setText("1160.00"));   // «Sumarle el IVA»
            assertEquals("174.00", pt[0].fValorResidual.getText(), "sigue al costo");

            interact(() -> pt[0].fValorResidual.setText("50"));
            assertFalse(pt[0].lblResidualPropuesto.isVisible());
            interact(() -> st[0].fPrecioC.setText("2000"));
            assertEquals("50", pt[0].fValorResidual.getText(), "el que se escribió distinto no se toca");
            assertTrue(pt[0].lnkResidual.isVisible() && pt[0].lnkResidual.getText().contains("300.00"),
                "queda a un clic: " + pt[0].lnkResidual.getText());

            // A bien that already had its 15 % says nothing, but follows a new costo.
            Producto q = new Producto();
            q.setId("p-03"); q.setNombre("Mesa"); q.setCodigo("PRES/05");
            q.setPrecioCompra(new java.math.BigDecimal("200.00")); q.setValorResidual(new java.math.BigDecimal("30.00"));
            interact(() -> {
                st[0] = new ProductoTabStockFields(q);
                pt[0] = new ProductoTabPatrimonioFields(q, List.of(), List.of(), List.of(), List.of(), null);
                pt[0].ponerCosto(st[0].etiquetaCosto, st[0].cajaCosto);
                pt[0].ofrecerResidual(st[0].fPrecioC);
            });
            assertEquals("30.00", pt[0].fValorResidual.getText());
            assertFalse(pt[0].lblResidualPropuesto.isVisible());
            interact(() -> st[0].fPrecioC.setText("232.00"));
            assertEquals("34.80", pt[0].fValorResidual.getText());

            // The 15 % worked out from the factura's unit price, some centavos off: opened, it stays;
            // with a new costo, it follows.
            Producto r = new Producto();
            r.setId("p-04"); r.setNombre("Laptop"); r.setCodigo("PRES/06");
            r.setPrecioCompra(new java.math.BigDecimal("26678.00")); r.setValorResidual(new java.math.BigDecimal("4001.73"));
            interact(() -> {
                st[0] = new ProductoTabStockFields(r);
                pt[0] = new ProductoTabPatrimonioFields(r, List.of(), List.of(), List.of(), List.of(), null);
                pt[0].ponerCosto(st[0].etiquetaCosto, st[0].cajaCosto);
                pt[0].ofrecerResidual(st[0].fPrecioC);
            });
            assertEquals("4001.73", pt[0].fValorResidual.getText(), "al abrir no se toca");
            assertFalse(pt[0].lnkResidual.isVisible(), "ya es el 15 %, con centavos de diferencia");
            interact(() -> st[0].fPrecioC.setText("30946.48"));
            assertEquals("4641.97", pt[0].fValorResidual.getText());
        } finally {
            com.sibim.session.SessionManager.setCurrentUser(antes);
        }
    }

    /** The nodes in the order Tab visits them (the scene graph's, depth first). */
    private static void recorrer(Node n, List<Node> orden) {
        orden.add(n);
        if (n instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) recorrer(c, orden);
    }
}
