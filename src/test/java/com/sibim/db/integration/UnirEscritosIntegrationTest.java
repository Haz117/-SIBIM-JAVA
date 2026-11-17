package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.Dato;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Against the real schema: «Unir lo escrito de dos formas» for a marca, «Poner MUEBLE» and
 *  «Tomar el color del nombre», each with its undo, which leaves alone what changed since. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UnirEscritosIntegrationTest extends IntegrationTestBase {

    @BeforeEach
    void datos() throws SQLException {
        sql("INSERT INTO categories (id, nombre, color) VALUES ('cat-1', 'Mobiliario', '#3B82F6') ON CONFLICT DO NOTHING",
            "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad, marca, clave_armonizada) VALUES "
                + "('a', 'MOTOSIERRA', 'SERM/1', 'cat-1', 'Servicios Municipales', 'pieza', 'STIHL', '1.2.4.6.7.567'), "
                + "('b', 'MOTOSIERRA', 'SERM/2', 'cat-1', 'Servicios Municipales', 'pieza', 'STIHL', '1.2.4.6.7.567'), "
                + "('c', 'DESBROZADORA', 'SERM/3', 'cat-1', 'Servicios Municipales', 'pieza', 'STHIL', '1.2.4.6.7.567'), "
                + "('d', 'SILLA OPERATIVA COLOR NEGRO', 'PRES/4', 'cat-1', 'Despacho de Presidencia', 'pieza', NULL, '1.2.4.1.1.511'), "
                + "('e', 'CAMIONETA', 'PRES/5', 'cat-1', 'Despacho de Presidencia', 'pieza', NULL, '1.2.4.4.1.541')");
    }

    @Test
    void unaMarca_seUne_yDeshacerLaRegresa() throws Exception {
        CalidadDatosService servicio = new CalidadDatosService();
        var repetidos = CalidadDatosService.escritosDeDosFormas(new ProductoRepository().findAll());
        assertEquals(1, repetidos.size(), repetidos.toString());
        assertEquals("STHIL", repetidos.get(0).variante());

        var hecho = servicio.unirEscritos(repetidos);
        assertEquals("STIHL", valor("SELECT marca FROM products WHERE id = 'c'"));

        sql("UPDATE products SET marca = 'HUSQVARNA' WHERE id = 'c'");
        assertEquals(0, servicio.separarEscritos(hecho), "lo que alguien cambió después no se toca");
        assertEquals("HUSQVARNA", valor("SELECT marca FROM products WHERE id = 'c'"));
    }

    @Test
    void mueble_soloAlQueNoEsVehiculo_yDeshacerLoQuita() throws Exception {
        CalidadDatosService servicio = new CalidadDatosService();
        List<Producto> estos = CalidadDatosService.sinTipoConSugerencia(new ProductoRepository().findAll());
        assertEquals(4, estos.size(), "todos menos la camioneta");

        List<String> ids = servicio.asignarTextoIds(Dato.TIPO, "MUEBLE", estos);
        assertEquals(4, ids.size());
        assertEquals("4", valor("SELECT count(*) FROM products WHERE tipo_bien = 'MUEBLE'"));
        assertNull(valor("SELECT tipo_bien FROM products WHERE id = 'e'"));

        sql("UPDATE products SET tipo_bien = 'HERRAMIENTA' WHERE id = 'a'");
        assertEquals(3, servicio.quitarTexto(Dato.TIPO, "MUEBLE", ids));
        assertEquals("HERRAMIENTA", valor("SELECT tipo_bien FROM products WHERE id = 'a'"));
        assertEquals("0", valor("SELECT count(*) FROM products WHERE tipo_bien = 'MUEBLE'"));
    }

    @Test
    void colorDelNombre_seGuarda_yDeshacerLoQuita() throws Exception {
        CalidadDatosService servicio = new CalidadDatosService();
        Map<Producto, String> propuestos = CalidadDatosService.coloresDelNombre(new ProductoRepository().findAll());
        assertEquals(List.of("NEGRO"), List.copyOf(propuestos.values()));

        Map<Producto, String> elegidos = new LinkedHashMap<>(propuestos);
        var hecho = servicio.asignarTextos(Dato.COLOR, elegidos);
        assertEquals("NEGRO", valor("SELECT color FROM products WHERE id = 'd'"));

        assertEquals(1, servicio.quitarTextos(Dato.COLOR, hecho));
        assertNull(valor("SELECT color FROM products WHERE id = 'd'"));
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
