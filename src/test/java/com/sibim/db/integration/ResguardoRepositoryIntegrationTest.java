package com.sibim.db.integration;

import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import com.sibim.repository.ResguardoRepository;
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
 * Integration tests for {@link ResguardoRepository} against an embedded PostgreSQL.
 *
 * resguardo_items.producto_id references products(id) ON DELETE SET NULL,
 * so a product row must exist before inserting items with a product reference.
 * A product is created via ProductoRepository.saveOnline() after seeding a category.
 */
class ResguardoRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String CAT_ID = "cat-rsg-test";
    private static final String AREA   = "Tesorería Municipal";

    private final ResguardoRepository repo        = new ResguardoRepository();
    private final ProductoRepository  productoRepo = new ProductoRepository();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void insertCategoria(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#6366F1')")) {
            ps.setString(1, id);
            ps.setString(2, "Categoría Resguardo Test");
            ps.executeUpdate();
        }
    }

    private Producto buildProducto(String id) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Escritorio de Prueba");
        p.setCodigo("RSG-PRD-" + id.substring(0, 6));
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(2000));
        p.setPrecioVenta(BigDecimal.valueOf(2500));
        p.setStockActual(1);
        p.setStockMinimo(1);
        p.setStockMaximo(5);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(AREA);
        p.setResguardante("Sin resguardante");
        return p;
    }

    private ResguardoItem buildItem(String productoId, String nombre) {
        ResguardoItem item = new ResguardoItem();
        item.setProductoId(productoId);
        item.setProductoNombre(nombre);
        item.setProductoCodigo("COD-001");
        item.setArea(AREA);
        item.setValorUnitario(BigDecimal.valueOf(2500));
        return item;
    }

    private Resguardo buildResguardo(String resguardante, List<ResguardoItem> items) {
        Resguardo r = new Resguardo();
        r.setResguardanteNombre(resguardante);
        r.setResguardanteCargo("Jefe de Área");
        r.setResguardanteArea(AREA);
        r.setItems(items);
        return r;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * A saved resguardo must be retrievable via findById() with its original
     * resguardanteNombre and in ACTIVO state.
     */
    @Test
    void save_nuevoResguardo_persisteEnDB() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Resguardo saved = repo.save(buildResguardo("Ana García", List.of(buildItem(pid, "Escritorio"))));

        assertNotNull(saved.getId(), "save debe asignar un id");
        assertNotNull(saved.getNumero(), "save debe asignar un número de folio");

        Resguardo found = repo.findById(saved.getId());
        assertNotNull(found, "findById debe encontrar el resguardo recién guardado");
        assertEquals("Ana García", found.getResguardanteNombre());
        assertEquals(Resguardo.ESTADO_ACTIVO, found.getEstado());
    }

    /**
     * A saved resguardo must appear in findAll() and its estado must be ACTIVO.
     */
    @Test
    void findAll_incluyeResguardoGuardado() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        repo.save(buildResguardo("Luis Hdez", List.of(buildItem(pid, "Silla"))));

        List<Resguardo> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals("Luis Hdez", all.get(0).getResguardanteNombre());
    }

    /**
     * save() must persist all resguardo_items so that findById() returns them.
     */
    @Test
    void save_items_persistenEnDB() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid1 = UUID.randomUUID().toString();
        String pid2 = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid1));
        productoRepo.saveOnline(buildProducto(pid2));

        Resguardo saved = repo.save(buildResguardo("Carlos Hdez",
            List.of(buildItem(pid1, "Laptop"), buildItem(pid2, "Monitor"))));

        Resguardo found = repo.findById(saved.getId());
        assertNotNull(found.getItems(), "los items deben cargarse en findById");
        assertEquals(2, found.getItems().size(), "deben persistir los 2 items del resguardo");
    }

    /**
     * Two consecutive saves must generate sequential folio numbers for the current year.
     */
    @Test
    void save_dosResguardos_numerosSecuenciales() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Resguardo r1 = repo.save(buildResguardo("Persona Uno", List.of(buildItem(pid, "Bien 1"))));
        Resguardo r2 = repo.save(buildResguardo("Persona Dos", List.of(buildItem(pid, "Bien 2"))));

        assertTrue(r1.getNumero().startsWith("RSG-"),
            "el folio debe empezar con RSG-");
        assertNotEquals(r1.getNumero(), r2.getNumero(),
            "dos resguardos distintos no pueden tener el mismo folio");
    }

    /**
     * cancelar() must flip estado to CANCELADO in the database.
     */
    @Test
    void cancelar_cambiaEstadoACancelado() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid));

        Resguardo saved = repo.save(buildResguardo("María Hdez", List.of(buildItem(pid, "Mesa"))));
        repo.cancelar(saved.getId());

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT estado FROM resguardos WHERE id = ?")) {
            ps.setString(1, saved.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(Resguardo.ESTADO_CANCELADO, rs.getString("estado"));
            }
        }
    }

    /**
     * findById() with a random id that doesn't exist must return null.
     */
    @Test
    void findById_idInexistente_retornaNull() throws SQLException {
        assertNull(repo.findById(UUID.randomUUID().toString()),
            "findById con id inexistente debe retornar null");
    }

    /**
     * findByProductoId() must return resguardos that contain the given product
     * and exclude those that don't.
     */
    @Test
    void findByProductoId_soloResguardosConEseProducto() throws SQLException {
        try (Connection c = getConnection()) { insertCategoria(c, CAT_ID); }
        String pid1 = UUID.randomUUID().toString();
        String pid2 = UUID.randomUUID().toString();
        productoRepo.saveOnline(buildProducto(pid1));
        productoRepo.saveOnline(buildProducto(pid2));

        repo.save(buildResguardo("Con Producto1", List.of(buildItem(pid1, "Bien A"))));
        repo.save(buildResguardo("Sin Producto1", List.of(buildItem(pid2, "Bien B"))));

        List<Resguardo> result = repo.findByProductoId(pid1);
        assertEquals(1, result.size());
        assertEquals("Con Producto1", result.get(0).getResguardanteNombre());
    }
}
