package com.sibim.db.integration;

import com.sibim.repository.PriceHistoryRepository;
import com.sibim.repository.PriceHistoryRepository.PriceHistoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PriceHistoryRepositoryIntegrationTest extends IntegrationTestBase {

    private final PriceHistoryRepository repo = new PriceHistoryRepository();

    private static final String CAT_ID     = "cat-price-test";
    private static final String PRODUCT_ID = UUID.randomUUID().toString(); // must be valid UUID

    @BeforeEach
    void insertarProducto() throws SQLException {
        try (Connection c = getConnection()) {
            // Category
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6')")) {
                ps.setString(1, CAT_ID);
                ps.setString(2, "Categoría Price Test");
                ps.executeUpdate();
            }
            // Product — PriceHistory.save() uses ?::uuid for producto_id, so the id must be a UUID string
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products (id, nombre, codigo, categoria_id, area, unidad) " +
                    "VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, PRODUCT_ID);
                ps.setString(2, "Producto Price Test");
                ps.setString(3, "PRT-001");
                ps.setString(4, CAT_ID);
                ps.setString(5, "Área Test");
                ps.setString(6, "pieza");
                ps.executeUpdate();
            }
        }
    }

    @Test
    void save_y_findByProducto_retornaEntrada() {
        repo.save(PRODUCT_ID, "precio_compra",
            BigDecimal.valueOf(800), BigDecimal.valueOf(1000),
            null, "Admin Test");

        List<PriceHistoryEntry> result = repo.findByProducto(PRODUCT_ID);

        assertEquals(1, result.size(), "debe haber una entrada de historial");
        assertEquals("precio_compra", result.get(0).campo());
        assertEquals(0, BigDecimal.valueOf(800).compareTo(result.get(0).valorAnterior()));
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(result.get(0).valorNuevo()));
        assertEquals("Admin Test", result.get(0).usuarioNombre());
    }

    @Test
    void save_multiplesEntradas_retornaEnOrdenDescendente() throws InterruptedException {
        repo.save(PRODUCT_ID, "precio_compra", BigDecimal.valueOf(500), BigDecimal.valueOf(600), null, "U1");
        // small delay so created_at timestamps differ
        Thread.sleep(10);
        repo.save(PRODUCT_ID, "precio_venta",  BigDecimal.valueOf(700), BigDecimal.valueOf(800), null, "U2");

        List<PriceHistoryEntry> result = repo.findByProducto(PRODUCT_ID);

        assertEquals(2, result.size());
        // most recent first
        assertEquals("precio_venta",  result.get(0).campo(), "la entrada más reciente debe ser primera");
        assertEquals("precio_compra", result.get(1).campo());
    }

    @Test
    void findByProducto_productoSinHistorial_retornaVacio() {
        String otroId = UUID.randomUUID().toString();
        List<PriceHistoryEntry> result = repo.findByProducto(otroId);

        assertNotNull(result);
        assertTrue(result.isEmpty(), "producto sin historial debe retornar lista vacía");
    }

    @Test
    void save_valorAnteriorNulo_persisteCorrectamente() {
        // anterior=null is valid for first-time pricing
        repo.save(PRODUCT_ID, "precio_compra", null, BigDecimal.valueOf(1200), null, "Admin");

        List<PriceHistoryEntry> result = repo.findByProducto(PRODUCT_ID);
        assertEquals(1, result.size());
        assertNull(result.get(0).valorAnterior(), "valor_anterior nulo debe mapearse como null");
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(result.get(0).valorNuevo()));
    }
}
