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

/** «Valor residual: el 15 % del costo» against the real schema (19-nov-2026: 285 of the 296
 *  valores residuales were the 15 % of the costo and 1,588 bienes with a costo had $0): only the
 *  ones at $0 with a costo take it, and Deshacer leaves at $0 the ones still at their 15 %. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValorResidualIntegrationTest extends IntegrationTestBase {

    private final ProductoRepository productos = new ProductoRepository();

    @BeforeEach
    void datos() throws SQLException {
        sql("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6') ON CONFLICT DO NOTHING",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, valor_residual) "
                + "VALUES ('silla', 'SILLA', 'PRUE/01', 'cat-1', 'Área de prueba', 'pieza', 2681.53, 0)",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, valor_residual) "
                + "VALUES ('mesa', 'MESA', 'PRUE/02', 'cat-1', 'Área de prueba', 'pieza', 1000, 0)",
            // Its own valor residual: left alone.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, valor_residual) "
                + "VALUES ('propio', 'ANAQUEL', 'PRUE/03', 'cat-1', 'Área de prueba', 'pieza', 1000, 80)",
            // Without a costo: nothing to take the 15 % of.
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, precio_compra, valor_residual) "
                + "VALUES ('sinCosto', 'ARCHIVERO', 'PRUE/04', 'cat-1', 'Área de prueba', 'pieza', 0, 0)");
    }

    @Test
    void elQuincePorCientoALosDeCeroConCosto_yDeshacerSoloLosQueSiguenAsi() throws Exception {
        List<Producto> todos = List.of(productos.findById("silla").orElseThrow(), productos.findById("mesa").orElseThrow(),
            productos.findById("propio").orElseThrow(), productos.findById("sinCosto").orElseThrow());
        assertEquals(List.of("silla", "mesa"), CalidadDatosService.sinResidual(todos).stream().map(Producto::getId).toList());
        CalidadDatosService servicio = new CalidadDatosService();

        List<String> ids = servicio.ponerResidual(todos);

        assertEquals(java.util.Set.of("silla", "mesa"), new java.util.HashSet<>(ids));
        assertEquals("402.23", valor("SELECT valor_residual FROM products WHERE id = 'silla'"), "al centavo, como el formulario");
        assertEquals("150.00", valor("SELECT valor_residual FROM products WHERE id = 'mesa'"));
        assertEquals("80.00", valor("SELECT valor_residual FROM products WHERE id = 'propio'"));
        assertEquals("0.00", valor("SELECT valor_residual FROM products WHERE id = 'sinCosto'"));
        assertEquals("1", valor("SELECT count(*) FROM audit_log WHERE detalle LIKE 'Valor residual: el 15 \\% de su costo en 2 bienes%'"));

        // Somebody types another valor residual in the mesa before Deshacer.
        sql("UPDATE products SET valor_residual = 99 WHERE id = 'mesa'");
        assertEquals(1, servicio.quitarResidual(ids));
        assertEquals("0.00", valor("SELECT valor_residual FROM products WHERE id = 'silla'"));
        assertEquals("99.00", valor("SELECT valor_residual FROM products WHERE id = 'mesa'"), "lo que se cambió después se queda");
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
