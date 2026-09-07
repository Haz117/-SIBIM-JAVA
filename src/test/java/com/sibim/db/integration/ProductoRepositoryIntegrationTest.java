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

    // ── etiquetado / findStats ───────────────────────────────────────────────

    /**
     * findStats().sinEtiquetar must count only products where etiquetado = FALSE.
     * Saves two products: one etiquetado, one not — expects sinEtiquetar = 1.
     */
    @Test
    void findStats_sinEtiquetarCount_correcta() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Stats");
        }

        Producto noEtiquetado = buildProducto(UUID.randomUUID().toString(), "Mesa Sin Etiqueta", "MSE-001");
        noEtiquetado.setEtiquetado(false);
        repo.saveOnline(noEtiquetado);

        Producto etiquetado = buildProducto(UUID.randomUUID().toString(), "Silla Etiquetada", "SET-001");
        etiquetado.setEtiquetado(true);
        repo.saveOnline(etiquetado);

        ProductoRepository.InventarioStats stats = repo.findStats();

        assertEquals(2, stats.total(), "total debe ser 2");
        assertEquals(1, stats.sinEtiquetar(),
            "sinEtiquetar debe ser 1 — solo el bien con etiquetado=false");
    }

    /**
     * findStats() must count a product as sinEtiquetar = 0 when all products
     * are etiquetado = true.
     */
    @Test
    void findStats_todosEtiquetados_sinEtiquetarEsCero() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Stats");
        }

        Producto p = buildProducto(UUID.randomUUID().toString(), "Computadora", "COM-001");
        p.setEtiquetado(true);
        repo.saveOnline(p);

        ProductoRepository.InventarioStats stats = repo.findStats();

        assertEquals(0, stats.sinEtiquetar(), "sinEtiquetar debe ser 0 cuando todos están etiquetados");
    }

    /**
     * etiquetado field must round-trip through saveOnline → findById correctly.
     */
    @Test
    void saveOnline_etiquetadoTrue_persisteYRegresa() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Etiquetado");
        }

        String id = UUID.randomUUID().toString();
        Producto p = buildProducto(id, "Impresora", "IMP-001");
        p.setEtiquetado(true);
        repo.saveOnline(p);

        Optional<Producto> found = repo.findById(id);

        assertTrue(found.isPresent());
        assertTrue(found.get().isEtiquetado(), "etiquetado=true debe persistir en la BD");
    }

    // ── findFotos / saveFotos ────────────────────────────────────────────────

    /**
     * saveFotos() must persist URLs in the given order; findFotos() must return
     * them back in the same order.
     */
    @Test
    void saveFotos_persistsInOrder() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Fotos");
        }

        String id = UUID.randomUUID().toString();
        repo.saveOnline(buildProducto(id, "Laptop", "LAP-001"));

        List<String> fotos = List.of(
            "https://supabase.co/storage/foto_0.jpg",
            "https://supabase.co/storage/foto_1.jpg",
            "https://supabase.co/storage/foto_2.jpg"
        );
        repo.saveFotos(id, fotos);

        List<String> result = repo.findFotos(id);

        assertEquals(3, result.size(), "debe haber 3 fotos");
        assertEquals("https://supabase.co/storage/foto_0.jpg", result.get(0));
        assertEquals("https://supabase.co/storage/foto_1.jpg", result.get(1));
        assertEquals("https://supabase.co/storage/foto_2.jpg", result.get(2));
    }

    /**
     * saveFotos() called a second time must replace the previous set of photos,
     * not append to it.
     */
    @Test
    void saveFotos_replacesPreviousFotos() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Fotos Replace");
        }

        String id = UUID.randomUUID().toString();
        repo.saveOnline(buildProducto(id, "Monitor", "MON-001"));

        repo.saveFotos(id, List.of("https://example.com/vieja_0.jpg", "https://example.com/vieja_1.jpg"));
        repo.saveFotos(id, List.of("https://example.com/nueva_0.jpg"));

        List<String> result = repo.findFotos(id);

        assertEquals(1, result.size(), "las fotos anteriores deben haber sido reemplazadas");
        assertEquals("https://example.com/nueva_0.jpg", result.get(0));
    }

    /**
     * saveFotos() with an empty list must remove all existing photos.
     */
    @Test
    void saveFotos_listaVacia_eliminaFotosExistentes() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Fotos Delete");
        }

        String id = UUID.randomUUID().toString();
        repo.saveOnline(buildProducto(id, "Teclado", "TEC-001"));

        repo.saveFotos(id, List.of("https://example.com/foto.jpg"));
        repo.saveFotos(id, List.of());

        List<String> result = repo.findFotos(id);

        assertTrue(result.isEmpty(), "no deben quedar fotos tras guardar lista vacía");
    }

    /**
     * findFotos() on a product with no photos must return an empty list,
     * never null or throw.
     */
    @Test
    void findFotos_sinFotos_retornaListaVacia() throws SQLException {
        try (Connection c = getConnection()) {
            insertCategoria(c, CAT_ID, "Categoria Sin Fotos");
        }

        String id = UUID.randomUUID().toString();
        repo.saveOnline(buildProducto(id, "Escritorio", "ESC-002"));

        List<String> result = repo.findFotos(id);

        assertNotNull(result, "findFotos nunca debe retornar null");
        assertTrue(result.isEmpty(), "producto sin fotos debe retornar lista vacía");
    }
}
