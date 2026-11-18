package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.CalidadDatosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** «Folio fiscal a su lugar» against the real schema: the UUID typed in «Factura» moves to
 *  «Folio fiscal», a bien with its own folio is left alone, and Deshacer puts it back. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FolioFiscalIntegrationTest extends IntegrationTestBase {

    private final ProductoRepository productos = new ProductoRepository();

    @BeforeEach
    void datos() throws SQLException {
        sql("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6') ON CONFLICT DO NOTHING",
            // As the C-2 list brought them: the UUID in the factura column.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, numero_factura) "
                + "VALUES ('camara', 'CAMARA BALA', 'SSPC/06', 'cat-1', 'Seguridad Pública — C-2', 'pieza', ' 3822637c-8b6a-410a-b7c9-6c29c9eeb359 ')",
            // Its own folio already: nothing moves.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, numero_factura, folio_fiscal) "
                + "VALUES ('conFolio', 'UPS', 'SSPC/09', 'cat-1', 'Seguridad Pública — C-2', 'pieza', "
                + "'11111111-2222-3333-4444-555555555555', 'AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE')",
            // A real factura number: nothing moves.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, numero_factura) "
                + "VALUES ('normal', 'ESCRITORIO', 'DJ/16', 'cat-1', 'Dirección Jurídica', 'pieza', 'A 667')");
    }

    @Test
    void elUuidPasaAFolioFiscal_yDeshacerLoRegresa() throws Exception {
        List<Producto> todos = List.of(productos.findById("camara").orElseThrow(),
            productos.findById("conFolio").orElseThrow(), productos.findById("normal").orElseThrow());
        List<Producto> estos = CalidadDatosService.folioEnFactura(todos);
        assertEquals(List.of("camara"), estos.stream().map(Producto::getId).toList());
        List<Producto> antes = estos.stream().map(Producto::copia).toList();
        CalidadDatosService servicio = new CalidadDatosService();

        assertEquals(1, servicio.pasarFolioFiscal(todos));
        assertEquals("3822637C-8B6A-410A-B7C9-6C29C9EEB359", valor("SELECT folio_fiscal FROM products WHERE id = 'camara'"));
        assertNull(valor("SELECT numero_factura FROM products WHERE id = 'camara'"));
        assertEquals("AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", valor("SELECT folio_fiscal FROM products WHERE id = 'conFolio'"));
        assertEquals("A 667", valor("SELECT numero_factura FROM products WHERE id = 'normal'"));
        assertEquals("1", valor("SELECT count(*) FROM audit_log WHERE detalle LIKE '%pasó a «Folio fiscal»%'"));

        assertEquals(1, servicio.regresarFolioFiscal(antes));
        assertEquals(" 3822637c-8b6a-410a-b7c9-6c29c9eeb359 ", valor("SELECT numero_factura FROM products WHERE id = 'camara'"));
        assertNull(valor("SELECT folio_fiscal FROM products WHERE id = 'camara'"));
    }

    /** Only the end of the UUID in «Folio fiscal» takes the whole one of a bien of its purchase;
     *  one somebody changed in between is left alone, and Deshacer puts the end back. */
    @Test
    void elFinalDelFolioSeCompletaConElDeSuCompra_yDeshacerLoRegresa() throws Exception {
        sql("INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, folio_fiscal, proveedor, fecha_adquisicion, precio_compra) "
                + "VALUES ('ventilador', 'VENTILADOR', 'VEN/05', 'cat-1', 'Área de prueba', 'pieza', ' O1329 ', 'PROVEEDOR FICTICIO', '2026-10-07', 1900.06)",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, folio_fiscal, proveedor, fecha_adquisicion, precio_compra) "
                + "VALUES ('hermano', 'VENTILADOR', 'VEN/25', 'cat-1', 'Otra área de prueba', 'pieza', 'B0000000-0000-4000-8000-000000001329', 'PROVEEDOR FICTICIO', '2026-10-07', 1900.06)",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, folio_fiscal, proveedor, fecha_adquisicion, precio_compra) "
                + "VALUES ('cambiado', 'VENTILADOR', 'VEN/26', 'cat-1', 'Área de prueba', 'pieza', '01329', 'PROVEEDOR FICTICIO', '2026-10-07', 1900.06)");
        List<Producto> todos = List.of(productos.findById("ventilador").orElseThrow(), productos.findById("hermano").orElseThrow(),
            productos.findById("cambiado").orElseThrow());
        var folios = CalidadDatosService.foliosDeSuCompra(todos, todos);
        assertEquals(2, folios.size());
        // Somebody writes the folio of VEN/26 by hand before «Completar».
        sql("UPDATE products SET folio_fiscal = 'C0000000-0000-4000-8000-000000000026' WHERE id = 'cambiado'");
        CalidadDatosService servicio = new CalidadDatosService();

        var hecho = servicio.corregirFolios(folios);

        assertEquals(List.of("ventilador"), hecho.stream().map(c -> c.id()).toList());
        assertEquals("B0000000-0000-4000-8000-000000001329", valor("SELECT folio_fiscal FROM products WHERE id = 'ventilador'"));
        assertEquals("C0000000-0000-4000-8000-000000000026", valor("SELECT folio_fiscal FROM products WHERE id = 'cambiado'"));
        assertEquals("1", valor("SELECT count(*) FROM audit_log WHERE detalle LIKE '%VEN/05 (de VEN/25)%'"));

        assertEquals(1, servicio.regresarFolios(hecho));
        assertEquals(" O1329 ", valor("SELECT folio_fiscal FROM products WHERE id = 'ventilador'"));
    }

    /** The same folio written wrong in other bienes takes the correction of one; Deshacer puts
     *  it back as it was written. */
    @Test
    void elMismoFolioMalEscritoSeCorrigeEnLosDemas() throws Exception {
        String malo = "EA2C2E50-0000-4000-8000-00000000017";
        sql("INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, folio_fiscal) "
                + "VALUES ('impresora1', 'IMPRESORA', 'IMP/01', 'cat-1', 'Área de prueba', 'pieza', '" + malo + "')",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, folio_fiscal) "
                + "VALUES ('impresora2', 'IMPRESORA', 'IMP/02', 'cat-1', 'Otra área de prueba', 'pieza', '" + malo + " ')");
        CalidadDatosService servicio = new CalidadDatosService();

        var hecho = servicio.corregirIguales(List.of(productos.findById("impresora2").orElseThrow()),
            "EA2C2E50-0000-4000-8000-000000000017", "IMP/01");

        assertEquals(1, hecho.size());
        assertEquals("EA2C2E50-0000-4000-8000-000000000017", valor("SELECT folio_fiscal FROM products WHERE id = 'impresora2'"));
        assertEquals(malo, valor("SELECT folio_fiscal FROM products WHERE id = 'impresora1'"), "solo los que se le pasan");
        assertEquals("1", valor("SELECT count(*) FROM audit_log WHERE detalle LIKE '%mismo folio fiscal mal escrito que IMP/01%IMP/02%'"));

        assertEquals(1, servicio.regresarFolios(hecho));
        assertEquals(malo + " ", valor("SELECT folio_fiscal FROM products WHERE id = 'impresora2'"));
    }

    private void sql(String... sentencias) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            for (String s : sentencias) st.execute(s);
        }
    }

    private String valor(String consulta) throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(consulta)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
