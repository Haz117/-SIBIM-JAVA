package com.sibim.db.integration;

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

/** «Unir proveedores» against the real schema, and its undo. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnirProveedoresIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void datos() throws SQLException {
        sql("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6') ON CONFLICT DO NOTHING",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, proveedor) VALUES "
                + "('a', 'SILLA', 'PRES/03', 'cat-1', 'Despacho de Presidencia', 'pieza', 'QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION'), "
                + "('b', 'SILLA', 'PRES/04', 'cat-1', 'Despacho de Presidencia', 'pieza', 'QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION SA DE CV'), "
                + "('c', 'MESA', 'SGM/49', 'cat-1', 'Secretaría General Municipal', 'pieza', 'QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION')");
    }

    @Test
    void uneLasFormas_yDeshacerLasRegresa() throws Exception {
        CalidadDatosService servicio = new CalidadDatosService();
        var repetidos = CalidadDatosService.proveedoresRepetidos(new ProductoRepository().findAll());
        assertEquals(1, repetidos.size());

        var hecho = servicio.unirProveedores(repetidos);

        assertEquals("1", valor("SELECT count(DISTINCT proveedor) FROM products WHERE id IN ('a','b','c')"));
        assertEquals("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION SA DE CV", valor("SELECT proveedor FROM products WHERE id = 'c'"));
        assertEquals(2, hecho.get(0).ids().size());

        assertEquals(2, servicio.separarProveedores(hecho));
        assertEquals("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION", valor("SELECT proveedor FROM products WHERE id = 'a'"));
        assertEquals("QUI ASESORIA Y LOGISTICA EN COMERCIALIZACION SA DE CV", valor("SELECT proveedor FROM products WHERE id = 'b'"));
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
