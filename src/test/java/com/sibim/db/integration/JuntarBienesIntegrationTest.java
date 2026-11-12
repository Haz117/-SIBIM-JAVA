package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.repository.JuntarBienesRepository.Huella;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.JuntarBienes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/** «Son el mismo bien» against the real schema: what moves, what leaves, and the undo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JuntarBienesIntegrationTest extends IntegrationTestBase {

    private final ProductoRepository productos = new ProductoRepository();

    @BeforeEach
    void datos() throws SQLException {
        sql("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6') ON CONFLICT DO NOTHING",
            // The one that stays: in Efraín's resguardo, with a photo, no serie.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, resguardante, foto_url, descripcion) "
                + "VALUES ('queda', 'ESCRITORIO', 'DJ/16', 'cat-1', 'Dirección Jurídica', 'pieza', 5250, 'EFRAIN', 'q.jpg', 'Inventario anterior: PM-DJ-016.')",
            // The copy: alone in an old resguardo, with serie, factura and history.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, resguardante, numero_serie, numero_factura, descripcion) "
                + "VALUES ('sale', 'ESCRITORIO', 'DGOB/18', 'cat-1', 'Dirección de Gobierno', 'pieza', 5250, 'LUIS', 'SER-123', 'F-77', 'Inventario anterior: SM-2471.')",
            "INSERT INTO product_fotos (id, producto_id, foto_url, orden) VALUES ('f-sale', 'sale', 's.jpg', 0)",
            "INSERT INTO price_history (producto_id, campo, valor_anterior, valor_nuevo) VALUES ('sale', 'precio_compra', 1, 5250)",
            "INSERT INTO producto_mantenimiento (id, producto_id, descripcion, fecha) VALUES ('m-1', 'sale', 'Revisión', DATE '2026-11-01')",
            "INSERT INTO movements (id, producto_id, tipo, cantidad, stock_anterior, stock_nuevo, motivo, usuario_id, usuario_nombre) "
                + "VALUES ('mov-1', 'sale', 'transferencia', 1, 1, 1, 'REASIGNACIÓN', 'test-admin', 'Admin Test')",
            "INSERT INTO resguardos (id, numero, resguardante_nombre, resguardante_area, estado) VALUES ('r-q', 'RSG-1', 'EFRAIN', 'Dirección Jurídica', 'ACTIVO')",
            "INSERT INTO resguardos (id, numero, resguardante_nombre, resguardante_area, estado) VALUES ('r-s', 'RSG-2', 'LUIS', 'Dirección de Gobierno', 'ACTIVO')",
            "INSERT INTO resguardo_items (id, resguardo_id, producto_id, producto_nombre, producto_codigo) VALUES ('i-q', 'r-q', 'queda', 'ESCRITORIO', 'DJ/16')",
            "INSERT INTO resguardo_items (id, resguardo_id, producto_id, producto_nombre, producto_codigo) VALUES ('i-s', 'r-s', 'sale', 'ESCRITORIO', 'DGOB/18')",
            // An old resguardo of the copy, no longer in force: its line keeps naming the bien.
            "INSERT INTO resguardos (id, numero, resguardante_nombre, resguardante_area, estado) VALUES ('r-viejo', 'RSG-0', 'PEDRO', 'Dirección de Gobierno', 'CANCELADO')",
            "INSERT INTO resguardo_items (id, resguardo_id, producto_id, producto_nombre, producto_codigo) VALUES ('i-viejo', 'r-viejo', 'sale', 'ESCRITORIO', 'DGOB/18')");
    }

    @Test
    void elQueSeQuedaTomaLoQueLeFaltaYElHistorial_yDeshacerLosRegresa() throws Exception {
        JuntarBienes servicio = new JuntarBienes();
        Producto queda = productos.findById("queda").orElseThrow(), sale = productos.findById("sale").orElseThrow();

        Huella h = servicio.juntar(queda, sale, "revisado en físico");

        assertNull(valor("SELECT id FROM products WHERE id = 'sale'"), "la copia se quita");
        assertEquals("SER-123", valor("SELECT numero_serie FROM products WHERE id = 'queda'"));
        assertEquals("F-77", valor("SELECT numero_factura FROM products WHERE id = 'queda'"));
        assertEquals("q.jpg", valor("SELECT foto_url FROM products WHERE id = 'queda'"), "su propia foto no se toca");
        assertTrue(valor("SELECT descripcion FROM products WHERE id = 'queda'").contains("SM-2471"), "el número viejo queda anotado");
        assertEquals("queda", valor("SELECT producto_id FROM product_fotos WHERE id = 'f-sale'"));
        assertEquals("queda", valor("SELECT producto_id FROM producto_mantenimiento WHERE id = 'm-1'"));
        assertEquals("1", valor("SELECT count(*) FROM price_history WHERE producto_id = 'queda'"));
        assertTrue(valor("SELECT motivo FROM movements WHERE id = 'mov-1'").contains("DGOB/18"));
        assertEquals("queda", valor("SELECT producto_id FROM movements WHERE id = 'mov-1'"));
        assertEquals("CANCELADO", valor("SELECT estado FROM resguardos WHERE id = 'r-s'"), "su resguardo quedó vacío");
        assertEquals("ACTIVO", valor("SELECT estado FROM resguardos WHERE id = 'r-q'"));
        assertNull(valor("SELECT id FROM resguardo_items WHERE id = 'i-s'"));
        assertEquals("queda", valor("SELECT producto_id FROM resguardo_items WHERE id = 'i-viejo'"), "el resguardo viejo no pierde su bien");
        assertEquals("CANCELADO", valor("SELECT estado FROM resguardos WHERE id = 'r-viejo'"));
        assertEquals("2", valor("SELECT count(*) FROM audit_log WHERE entidad_id IN ('queda', 'sale')"));

        servicio.deshacer(h);

        assertEquals("DGOB/18", valor("SELECT codigo FROM products WHERE id = 'sale'"));
        assertEquals("SER-123", valor("SELECT numero_serie FROM products WHERE id = 'sale'"));
        assertNull(valor("SELECT numero_serie FROM products WHERE id = 'queda'"));
        assertEquals("Inventario anterior: PM-DJ-016.", valor("SELECT descripcion FROM products WHERE id = 'queda'"));
        assertEquals("sale", valor("SELECT producto_id FROM product_fotos WHERE id = 'f-sale'"));
        assertEquals("sale", valor("SELECT producto_id FROM producto_mantenimiento WHERE id = 'm-1'"));
        assertEquals("REASIGNACIÓN", valor("SELECT motivo FROM movements WHERE id = 'mov-1'"));
        assertEquals("ACTIVO", valor("SELECT estado FROM resguardos WHERE id = 'r-s'"));
        assertEquals("sale", valor("SELECT producto_id FROM resguardo_items WHERE id = 'i-s'"));
        assertEquals("sale", valor("SELECT producto_id FROM resguardo_items WHERE id = 'i-viejo'"));
    }

    @Test
    void siElQueSeQuedaNoTieneResguardoTomaElLugarDelOtro() throws Exception {
        sql("DELETE FROM resguardo_items WHERE id = 'i-q'");
        JuntarBienes servicio = new JuntarBienes();
        Producto queda = productos.findById("queda").orElseThrow(), sale = productos.findById("sale").orElseThrow();

        Huella h = servicio.juntar(queda, sale, "");

        assertEquals("queda", valor("SELECT producto_id FROM resguardo_items WHERE id = 'i-s'"));
        assertEquals("DJ/16", valor("SELECT producto_codigo FROM resguardo_items WHERE id = 'i-s'"));
        assertEquals("ACTIVO", valor("SELECT estado FROM resguardos WHERE id = 'r-s'"));
        assertEquals("LUIS", valor("SELECT resguardante FROM products WHERE id = 'queda'"), "lo dice el resguardo firmado");

        servicio.deshacer(h);

        assertEquals("sale", valor("SELECT producto_id FROM resguardo_items WHERE id = 'i-s'"));
        assertEquals("EFRAIN", valor("SELECT resguardante FROM products WHERE id = 'queda'"));
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
