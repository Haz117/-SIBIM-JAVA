package com.sibim.db.integration;

import com.sibim.model.Categoria;
import com.sibim.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link CategoriaRepository} against a real (embedded)
 * PostgreSQL database.
 */
class CategoriaRepositoryIntegrationTest extends IntegrationTestBase {

    private final CategoriaRepository repo = new CategoriaRepository();

    private Categoria buildCategoria(String id, String nombre) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        c.setDescripcion("Descripción de prueba");
        c.setColor("#3B82F6");
        c.setIcono("📦");
        return c;
    }

    @Test
    void saveOnline_newCategoria_persistsToDb() throws SQLException {
        String id = UUID.randomUUID().toString();
        Categoria cat = buildCategoria(id, "Mobiliario");
        repo.saveOnline(cat);

        Optional<Categoria> found = repo.findById(id);

        assertTrue(found.isPresent(), "findById debe encontrar la categoría recién guardada");
        assertEquals("Mobiliario", found.get().getNombre());
        assertEquals("#3B82F6", found.get().getColor());
    }

    @Test
    void saveOnline_updateExisting_updatesFields() throws SQLException {
        String id = UUID.randomUUID().toString();
        Categoria cat = buildCategoria(id, "Electronico");
        repo.saveOnline(cat);

        cat.setNombre("Equipo de Cómputo");
        cat.setColor("#10B981");
        repo.saveOnline(cat);

        Optional<Categoria> updated = repo.findById(id);
        assertTrue(updated.isPresent());
        assertEquals("Equipo de Cómputo", updated.get().getNombre());
        assertEquals("#10B981", updated.get().getColor());

        List<Categoria> all = repo.findAll();
        long count = all.stream().filter(c -> id.equals(c.getId())).count();
        assertEquals(1, count, "No debe existir más de una fila para el mismo id");
    }

    @Test
    void findAll_returnsAllCategories() throws SQLException {
        repo.saveOnline(buildCategoria(UUID.randomUUID().toString(), "Cat A"));
        repo.saveOnline(buildCategoria(UUID.randomUUID().toString(), "Cat B"));
        repo.saveOnline(buildCategoria(UUID.randomUUID().toString(), "Cat C"));

        List<Categoria> cats = repo.findAll();

        assertEquals(3, cats.size(), "findAll debe retornar las tres categorías insertadas");
    }

    @Test
    void tieneProductos_noProducts_returnsFalse() throws SQLException {
        String id = UUID.randomUUID().toString();
        repo.saveOnline(buildCategoria(id, "Vacía"));

        assertFalse(repo.tieneProductos(id),
            "Una categoría sin productos no debe retornar true en tieneProductos");
    }

    @Test
    void deleteOnline_removesCategoria() throws SQLException {
        String id = UUID.randomUUID().toString();
        repo.saveOnline(buildCategoria(id, "Para Eliminar"));

        assertTrue(repo.findById(id).isPresent(), "La categoría debe existir antes de eliminar");

        repo.deleteOnline(id);

        assertTrue(repo.findById(id).isEmpty(), "findById debe retornar vacío tras deleteOnline");
    }

    @Test
    void findById_unknownId_returnsEmpty() throws SQLException {
        Optional<Categoria> result = repo.findById(UUID.randomUUID().toString());
        assertTrue(result.isEmpty(), "findById con id inexistente debe retornar Optional.empty()");
    }
}
