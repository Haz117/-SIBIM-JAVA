package com.sibim.db.integration;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UsuarioRepository} against an embedded PostgreSQL.
 *
 * IntegrationTestBase seeds an admin user ("test-admin") via truncateAll(),
 * so findAll() / findByUsername() will see at least that row.
 * The session is set to ADMIN so requireAdmin() guards pass.
 */
class UsuarioRepositoryIntegrationTest extends IntegrationTestBase {

    private final UsuarioRepository repo = new UsuarioRepository();

    // ── Helper ────────────────────────────────────────────────────────────────

    private Usuario buildUsuario(String username) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPasswordHash("$2a$10$placeholder");
        u.setNombre("Usuario " + username);
        u.setCargo("Secretario");
        u.setRol(Rol.SECRETARIO);
        u.setArea("Secretaría General");
        u.setActivo(true);
        return u;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    /**
     * save() with a new Usuario must assign an id and make the user
     * retrievable by username.
     */
    @Test
    void save_nuevoUsuario_persisteEnDB() throws SQLException {
        Usuario saved = repo.save(buildUsuario("juan.perez"));

        assertNotNull(saved.getId(), "save debe asignar un id UUID");
        Optional<Usuario> found = repo.findByUsername("juan.perez");
        assertTrue(found.isPresent(), "findByUsername debe encontrar el usuario recién guardado");
        assertEquals("Usuario juan.perez", found.get().getNombre());
    }

    /**
     * save() called a second time on the same id must update the existing row
     * (ON CONFLICT DO UPDATE) without creating a duplicate.
     */
    @Test
    void save_updateExisting_actualizaNombre() throws SQLException {
        Usuario saved = repo.save(buildUsuario("maria.garcia"));
        saved.setNombre("María García Actualizada");
        repo.save(saved);

        Optional<Usuario> found = repo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("María García Actualizada", found.get().getNombre());

        List<Usuario> all = repo.findAll();
        long count = all.stream().filter(u -> saved.getId().equals(u.getId())).count();
        assertEquals(1, count, "no debe haber filas duplicadas tras actualizar");
    }

    /**
     * findAll() must return the seeded admin plus any saved users.
     */
    @Test
    void findAll_incluyeAdminSeed() throws SQLException {
        repo.save(buildUsuario("extra.user"));
        List<Usuario> all = repo.findAll();
        assertTrue(all.size() >= 2, "debe incluir al menos el admin seed y el usuario guardado");
        assertTrue(all.stream().anyMatch(u -> "test-admin".equals(u.getId())),
            "el usuario admin seed debe aparecer en findAll");
    }

    /**
     * findByUsername() with an unknown name must return empty, never throw.
     */
    @Test
    void findByUsername_desconocido_retornaVacio() throws SQLException {
        Optional<Usuario> result = repo.findByUsername("no-existe-xyz");
        assertTrue(result.isEmpty());
    }

    /**
     * findById() with an unknown id must return empty, never throw.
     */
    @Test
    void findById_desconocido_retornaVacio() throws SQLException {
        Optional<Usuario> result = repo.findById("id-que-no-existe");
        assertTrue(result.isEmpty());
    }

    /**
     * existsByUsername() must return true only when a user with that username
     * exists and the excluded id is different.
     */
    @Test
    void existsByUsername_retornaCorrectamente() throws SQLException {
        Usuario u = repo.save(buildUsuario("lucia.hdez"));

        assertTrue(repo.existsByUsername("lucia.hdez", "otro-id"),
            "debe existir el username recién guardado");
        assertFalse(repo.existsByUsername("lucia.hdez", u.getId()),
            "debe retornar false cuando el único match es el propio usuario excluido");
        assertFalse(repo.existsByUsername("no-existe", null),
            "debe retornar false para un username que no existe");
    }

    /**
     * setActivo(false) must deactivate the user; setActivo(true) must reactivate.
     */
    @Test
    void setActivo_cambiaEstadoActivo() throws SQLException {
        Usuario u = repo.save(buildUsuario("carlos.lopez"));

        repo.setActivo(u.getId(), false);
        // Verify via raw findById — activo column should be false
        Optional<Usuario> desactivado = repo.findById(u.getId());
        assertTrue(desactivado.isPresent());
        assertFalse(desactivado.get().isActivo(), "el usuario debe quedar desactivado");

        repo.setActivo(u.getId(), true);
        Optional<Usuario> reactivado = repo.findById(u.getId());
        assertTrue(reactivado.isPresent());
        assertTrue(reactivado.get().isActivo(), "el usuario debe quedar reactivado");
    }

    /**
     * delete() must remove the user row; a subsequent findById must return empty.
     */
    @Test
    void delete_eliminaUsuario() throws SQLException {
        Usuario u = repo.save(buildUsuario("temp.user"));
        repo.delete(u.getId());

        Optional<Usuario> found = repo.findById(u.getId());
        assertTrue(found.isEmpty(), "el usuario eliminado no debe encontrarse por id");
    }
}
