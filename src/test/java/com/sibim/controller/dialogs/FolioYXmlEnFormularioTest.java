package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.FacturaXml;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The folio fiscal in the bien's form: an O is a zero as it is typed, and the factura's XML
 *  fills what the bien lacks (13 to 17-nov-2026: 34 of 308 folios typed by hand were left wrong). */
class FolioYXmlEnFormularioTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new StackPane(), 200, 100));
        stage.show();
    }

    private ProductoTabPatrimonioFields campos(Producto p) {
        ProductoTabPatrimonioFields[] f = new ProductoTabPatrimonioFields[1];
        interact(() -> f[0] = new ProductoTabPatrimonioFields(p, List.of(), List.of(), List.of(), List.of(), null));
        return f[0];
    }

    private static FacturaXml.Factura factura() throws Exception {
        return FacturaXml.leer(new ByteArrayInputStream(com.sibim.util.FacturaXmlTest.CFDI_40.strip().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void alEscribir_laOEsCero_yLaIoLaLSonUno() {
        ProductoTabPatrimonioFields f = campos(null);
        interact(() -> f.fFolioFiscal.appendText("aaa1ec97-00b3-4co7-b2e2-e1a11491d3el"));
        assertEquals("AAA1EC97-00B3-4C07-B2E2-E1A11491D3E1", f.fFolioFiscal.getText());
    }

    @Test
    void elXml_llenaLoQueFalta_cambiaElFolioMalEscrito_yDiceLoQueDejo() throws Exception {
        Producto p = new Producto();
        p.setId("x"); p.setCodigo("TES/90"); p.setNombre("SILLA SEMI EJECUTIVA");
        p.setFolioFiscal("AAA1EC97-00B3-4C07-B2E2-E1A11491D3E");   // a character short
        p.setProveedor("OTRO PROVEEDOR");
        ProductoTabPatrimonioFields f = campos(p);
        TextField costo = new TextField("");

        interact(() -> f.aplicarFactura(unchecked(), costo, "SILLA SEMI EJECUTIVA", false));

        assertEquals("A1B2C3D4-0000-4000-8000-000000000001", f.fFolioFiscal.getText());
        assertEquals("A 667", f.fNumeroFactura.getText());
        assertEquals(LocalDate.of(2023, 12, 11), f.fFechaAdq.getValue());
        assertEquals("OTRO PROVEEDOR", f.fProveedor.getText(), "lo que ya tenía, se queda");
        assertEquals("2400.01", costo.getText(), "el concepto de la silla, con su IVA");
        assertTrue(f.lblXml.isVisible());
        assertTrue(f.lblXml.getText().startsWith("Del XML: folio fiscal, factura A 667, fecha 11/12/2023 y costo $2,400.01 con IVA."),
            f.lblXml.getText());
        assertTrue(f.lblXml.getText().contains("Se dejó como estaba el proveedor (la factura dice PAPELERÍA DE EJEMPLO SA DE CV)"),
            f.lblXml.getText());
    }

    @Test
    void sinSaberCualConcepto_noPoneCosto_niConOtraMoneda() throws Exception {
        assertNull(ProductoTabPatrimonioFields.conceptoDelBien(factura().conceptos(), "VENTILADOR DE TORRE"));
        assertEquals("ESCRITORIO EJECUTIVO EN L",
            ProductoTabPatrimonioFields.conceptoDelBien(factura().conceptos(), "Escritorio ejecutivo en L").descripcion());

        ProductoTabPatrimonioFields f = campos(null);
        TextField costo = new TextField("");
        var usd = FacturaXml.leer(new ByteArrayInputStream(com.sibim.util.FacturaXmlTest.CFDI_40.strip()
            .replace("Moneda=\"MXN\"", "Moneda=\"USD\"").getBytes(StandardCharsets.UTF_8)));
        interact(() -> f.aplicarFactura(usd, costo, "SILLA SEMI EJECUTIVA", false));
        assertEquals("", costo.getText());
        assertTrue(f.lblXml.getText().contains("el costo: la factura está en USD"), f.lblXml.getText());
    }

    @Test
    void alGuardar_soloPreguntaPorUnFolioMalEscritoNuevo() {
        assertTrue(ProductoDialogFactory.folioConfirmado("A1B2C3D4-0000-4000-8000-000000000001", null), "uno bien, no pregunta");
        assertTrue(ProductoDialogFactory.folioConfirmado("", null), "vacío, no pregunta");
        Producto p = new Producto();
        p.setFolioFiscal("AAA1EC97-00B3-4C07-B2E2-E1A11491D3E");
        assertTrue(ProductoDialogFactory.folioConfirmado("aaa1ec97-00b3-4c07-b2e2-e1a11491d3e", p), "el que ya tenía no se pregunta otra vez");
    }

    private static FacturaXml.Factura unchecked() {
        try { return factura(); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** 6 to 18-nov-2026: 246 folios fiscales moved by hand from «Factura» to «Folio fiscal». */
    @Test
    void elFolioEnFactura_sePasaASuLugarConUnClic() {
        Producto p = new Producto();
        p.setId("p-folio"); p.setNombre("SILLA"); p.setCodigo("PRU/1");
        p.setNumeroFactura("a0000000-0000-4000-8000-000000000001");
        ProductoTabPatrimonioFields f = campos(p);
        assertTrue(f.lnkFolioALugar.isVisible());

        interact(f.lnkFolioALugar::fire);
        assertEquals("A0000000-0000-4000-8000-000000000001", f.fFolioFiscal.getText());
        assertEquals("", f.fNumeroFactura.getText());
        assertFalse(f.lnkFolioALugar.isVisible(), "ya está en su lugar");

        Producto conAmbos = new Producto();
        conAmbos.setId("p-ambos"); conAmbos.setNombre("SILLA"); conAmbos.setCodigo("PRU/2");
        conAmbos.setNumeroFactura("B 5630");
        assertFalse(campos(conAmbos).lnkFolioALugar.isVisible(), "un número de factura se queda");
    }
}
