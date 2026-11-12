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
