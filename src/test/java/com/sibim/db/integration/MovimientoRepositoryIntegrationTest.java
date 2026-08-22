package com.sibim.db.integration;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link MovimientoRepository} against a real (embedded)
 * PostgreSQL database.
 */
class MovimientoRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String AREA = "Secretaría de Finanzas";
    private static final String CAT_ID = "cat-mov-test";

    private final MovimientoRepository repo = new MovimientoRepository();
    private final ProductoRepository productoRepo = new ProductoRepository();

    private void insertCategoria(Connection c, String id, String nombre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6')")) {
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
    }

    private Producto buildProducto(String id, int stockInicial) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto Movimiento Test");
        p.setCodigo("MOV-TEST-" + id.substring(0, 6));
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(500));
        p.setPrecioVenta(BigDecimal.valueOf(600));
        p.setStockActual(stockInicial);
        p.setStockMinimo(1);
        p.setStockMaximo(100);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(AREA);
        p.setResguardante("Resguardante Test");
        return p;
    }

    private Movimiento buildMovimiento(String productoId, TipoMovimiento tipo, int cantidad) {
        Movimiento m = new Movimiento();
        m.setProductoId(productoId);
        m.setTipo(tipo);
        m.setCantidad(cantidad);
        m.setMotivo("Prueba integración");
        m.setUsuarioId("test-admin");
        m.setUsuarioNombre("Admin Test");
        return m;
    }

    @Test
    void addMovimientoAtomicOnline_entrada_incrementsStock() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Movimiento");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, 10));

        Movimiento m = buildMovimiento(pid, TipoMovimiento.ENTRADA, 5);
        Movimiento saved = repo.addMovimientoAtomicOnline(m, null);

        assertNotNull(saved.getId(), "El movimiento debe tener id asignado");
        assertEquals(10, saved.getStockAnterior(), "stockAnterior debe ser 10");
        assertEquals(15, saved.getStockNuevo(), "stockNuevo debe ser 10+5=15");

        // Verify stock actually updated in products table
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT stock_actual FROM products WHERE id = ?")) {
            ps.setString(1, pid);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(15, rs.getInt("stock_actual"));
            }
        }
    }

    @Test
    void addMovimientoAtomicOnline_salida_decrementsStock() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Movimiento");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, 20));

        Movimiento m = buildMovimiento(pid, TipoMovimiento.SALIDA, 7);
        Movimiento saved = repo.addMovimientoAtomicOnline(m, null);

        assertEquals(20, saved.getStockAnterior());
        assertEquals(13, saved.getStockNuevo());
    }

    @Test
    void findAll_returnsMovimientosForCurrentUser() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Movimiento");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, 50));

        repo.addMovimientoAtomicOnline(buildMovimiento(pid, TipoMovimiento.ENTRADA, 3), null);
        repo.addMovimientoAtomicOnline(buildMovimiento(pid, TipoMovimiento.ENTRADA, 2), null);

        // Admin session → no area filter → returns all movements
        List<Movimiento> movs = repo.findAll();
        assertEquals(2, movs.size(), "findAll debe retornar los dos movimientos insertados");
    }

    @Test
    void findByProducto_returnsOnlyThatProductsMovements() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Movimiento");
        }
        String pid1 = UUID.randomUUID().toString();
        String pid2 = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid1, 30));
        productoRepo.saveOnline(buildProducto(pid2, 30));

        repo.addMovimientoAtomicOnline(buildMovimiento(pid1, TipoMovimiento.ENTRADA, 1), null);
        repo.addMovimientoAtomicOnline(buildMovimiento(pid1, TipoMovimiento.ENTRADA, 2), null);
        repo.addMovimientoAtomicOnline(buildMovimiento(pid2, TipoMovimiento.SALIDA,  5), null);

        List<Movimiento> pid1Movs = repo.findByProducto(pid1);
        assertEquals(2, pid1Movs.size(), "Solo deben aparecer los 2 movimientos del producto 1");
        assertTrue(pid1Movs.stream().allMatch(m -> pid1.equals(m.getProductoId())));
    }
}
