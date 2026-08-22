package com.sibim.db.integration;

import com.sibim.model.Producto;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ProductoRepository} against a real (embedded)
 * PostgreSQL database.
 *
 * Data isolation: IntegrationTestBase.truncateAll() runs before every test,
 * so each test starts with an empty schema. A category row is inserted via the
 * JDBC helper below (not via CategoriaRepository) so these tests have no
 * dependency on other repository classes.
 *
 * Session: the ADMIN session seeded in IntegrationTestBase means
 * SessionManager.getAccessibleAreas() returns null (no area filter), so
 * findAll() returns all active products regardless of area.
 */
class ProductoRepositoryIntegrationTest extends IntegrationTestBase {

    private static final String AREA = "Secretaria General Municipal";
    private static final String CAT_ID = "cat-integ-test";

    private final ProductoRepository repo = new ProductoRepository();

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Inserts a category directly via JDBC. Tests must call this before
     * inserting any product that references the category via FK.
     */
    private void insertCategoria(Connection c, String id, String nombre) throws SQLException {
        String sql = "INSERT INTO categories (id, nombre, color) VALUES (?, ?, '#3B82F6')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, nombre);
            ps.executeUpdate();
        }
    }

    /**
     * Builds a fully-populated, valid {@link Producto} ready for
     * {@link ProductoRepository#saveOnline}. The category with {@code CAT_ID}
     * must exist in the DB before calling saveOnline on the returned instance.
     */
    private Producto buildProducto(String id, String nombre, String codigo) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setCodigo(codigo);
        p.setDescripcion("Descripcion de prueba");
        p.setCategoriaId(CAT_ID);
        p.setPrecioCompra(BigDecimal.valueOf(1000));
        p.setPrecioVenta(BigDecimal.valueOf(1200));
        p.setStockActual(5);
        p.setStockMinimo(1);
        p.setStockMaximo(10);
        p.setUnidad(UnidadMedida.fromCodigo("pieza"));
        p.setArea(AREA);
        p.setResguardante("Resguardante Prueba");
        return p;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * A new product saved via saveOnline() must be retrievable through
     * findById() with the correct nombre and stock values.
     */
    @Test
    void saveOnline_newProduct_persistsToDb() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Integracion");
        }

        String id = UUID.randomUUID().toString();
        Producto producto = buildProducto(id, "Escritorio de Madera", "ESC-001");
        repo.saveOnline(producto);

        Optional<Producto> found = repo.findById(id);

        assertTrue(found.isPresent(), "findById debe encontrar el producto recien guardado");
        assertEquals("Escritorio de Madera", found.get().getNombre());
        assertEquals(5, found.get().getStockActual());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(found.get().getPrecioCompra()),
            "precioCompra debe coincidir");
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(found.get().getPrecioVenta()),
            "precioVenta debe coincidir");
    }

    /**
     * Saving a product a second time with the same id (ON CONFLICT DO UPDATE)
     * must update the mutable fields — specifically nombre — without creating
     * a duplicate row.
     */
    @Test
    void saveOnline_updateExisting_updatesFields() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Integracion");
        }

        String id = UUID.randomUUID().toString();
        Producto original = buildProducto(id, "Silla Plastica", "SIL-001");
        repo.saveOnline(original);

        // Mutate and save again — same id, different nombre
        original.setNombre("Silla Ergonomica");
        repo.saveOnline(original);

        Optional<Producto> updated = repo.findById(id);
        assertTrue(updated.isPresent(), "El producto debe seguir existiendo tras la actualizacion");
        assertEquals("Silla Ergonomica", updated.get().getNombre(),
            "El nombre debe haber sido actualizado por el ON CONFLICT DO UPDATE");

        // Ensure no phantom duplicate
        List<Producto> all = repo.findAll();
        long count = all.stream().filter(p -> id.equals(p.getId())).count();
        assertEquals(1, count, "No debe existir mas de una fila para el mismo id");
    }

    /**
     * findAll() must return only products without a fecha_baja (active inventory).
     * A product given a baja must not appear.
     */
    @Test
    void findAll_returnsOnlyActive() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Integracion");
        }

        String idActivo = UUID.randomUUID().toString();
        String idBaja   = UUID.randomUUID().toString();

        repo.saveOnline(buildProducto(idActivo, "Producto Activo",    "ACT-001"));
        repo.saveOnline(buildProducto(idBaja,   "Producto Dado Baja", "BAJ-001"));

        // Soft-delete the second product directly in the DB (darDeBajaOnline
        // could also be used, but direct SQL avoids another repository call in scope)
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE products SET fecha_baja = CURRENT_DATE WHERE id = ?")) {
            ps.setString(1, idBaja);
            ps.executeUpdate();
        }

        List<Producto> activos = repo.findAll();

        long countActivo = activos.stream().filter(p -> idActivo.equals(p.getId())).count();
        long countBaja   = activos.stream().filter(p -> idBaja.equals(p.getId())).count();

        assertEquals(1, countActivo, "El producto activo debe aparecer en findAll()");
        assertEquals(0, countBaja,   "El producto dado de baja NO debe aparecer en findAll()");
    }

    /**
     * findById() with a random UUID that has no matching row must return an
     * empty Optional — never throw and never return a populated object.
     */
    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Producto> result = repo.findById(UUID.randomUUID().toString());
        assertTrue(result.isEmpty(),
            "findById con un id inexistente debe retornar Optional.empty()");
    }
}
