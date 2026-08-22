package com.sibim.db.integration;

import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ConteoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ConteoRepository} against a real (embedded)
 * PostgreSQL database.
 */
class ConteoRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String AREA = "Secretaría de Administración";
    private static final String CAT_ID = "cat-conteo-test";

    private final ConteoRepository repo = new ConteoRepository();
    private final ProductoRepository productoRepo = new ProductoRepository();

    private void insertCategoria(Connection c, String id, String nombre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6')")) {
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
    }

    private Producto buildProducto(String id, String nombre) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCodigo("CTN-" + id.substring(0, 6));
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(100));
        p.setPrecioVenta(BigDecimal.valueOf(120));
        p.setStockActual(10);
        p.setStockMinimo(1);
        p.setStockMaximo(50);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(AREA);
        p.setResguardante("Resguardante Test");
        return p;
    }

    private ConteoFisico buildConteo(String productoId, String productoNombre) {
        ConteoFisico c = new ConteoFisico();
        c.setId(UUID.randomUUID().toString());
        c.setUsuarioId("test-admin");
        c.setUsuarioNombre("Admin Test");
        c.setTotalContados(1);
        c.setTotalDiscrepancias(0);
        c.setCreadoEn(LocalDateTime.now());

        ConteoItem item = new ConteoItem();
        item.setId(UUID.randomUUID().toString());
        item.setConteoId(c.getId());
        item.setProductoId(productoId);
        item.setProductoNombre(productoNombre);
        item.setArea(AREA);
        item.setStockSistema(10);
        item.setStockContado(10);
        item.setAjustado(false);
        c.setItems(List.of(item));
        return c;
    }

    @Test
    void guardarOnline_persistsConteoAndItems() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Conteo");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, "Silla de Oficina"));

        ConteoFisico conteo = buildConteo(pid, "Silla de Oficina");
        String conteoId = conteo.getId();
        repo.guardarOnline(conteo);

        List<ConteoFisico> all = repo.findAll(50);
        long found = all.stream().filter(c -> conteoId.equals(c.getId())).count();
        assertEquals(1, found, "El conteo debe aparecer en findAll tras guardarOnline");
    }

    @Test
    void findItems_returnsItemsForConteo() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Conteo");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, "Escritorio"));

        ConteoFisico conteo = buildConteo(pid, "Escritorio");
        String conteoId = conteo.getId();
        String itemId   = conteo.getItems().get(0).getId();
        repo.guardarOnline(conteo);

        List<ConteoItem> items = repo.findItems(conteoId);
        assertEquals(1, items.size(), "Debe haber exactamente 1 ítem para este conteo");
        assertEquals(itemId, items.get(0).getId());
        assertEquals(pid, items.get(0).getProductoId());
        assertEquals(10, items.get(0).getStockSistema());
    }

    @Test
    void guardarOnline_conteoWithDiscrepancy_setsAjustado() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Conteo");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, "Monitor"));

        ConteoFisico conteo = buildConteo(pid, "Monitor");
        // Simulate discrepancy: system said 10 but user counted 8
        ConteoItem item = conteo.getItems().get(0);
        item.setStockContado(8);
        item.setAjustado(true);
        conteo.setTotalDiscrepancias(1);
        repo.guardarOnline(conteo);

        List<ConteoItem> items = repo.findItems(conteo.getId());
        assertEquals(1, items.size());
        assertEquals(8, items.get(0).getStockContado());
        assertTrue(items.get(0).isAjustado(), "El ítem con discrepancia debe tener ajustado=true");
    }

    @Test
    void findAll_respectsLimit() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Cat Conteo");
        }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid, "Laptop"));

        for (int i = 0; i < 5; i++) {
            repo.guardarOnline(buildConteo(pid, "Laptop"));
        }

        List<ConteoFisico> limited = repo.findAll(3);
        assertEquals(3, limited.size(), "findAll(3) debe retornar máximo 3 conteos");
    }
}
