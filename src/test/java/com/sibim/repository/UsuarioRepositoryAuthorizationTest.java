package com.sibim.repository;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

/** Valida que completarCambioPassword rechaza cambios de contraseña
 *  en cuentas ajenas, y que solo Admins pueden hacer reset. */
public class UsuarioRepositoryAuthorizationTest {

    private UsuarioRepository repo;
    private Usuario currentUser;
    private Usuario anotherUser;
    private Usuario adminUser;

    @BeforeEach
    public void setUp() {
        repo = new UsuarioRepository();
        currentUser = new Usuario();
        currentUser.setId("user-1");
        currentUser.setUsername("usuario");
        currentUser.setRol(Rol.SECRETARIO);

        anotherUser = new Usuario();
        anotherUser.setId("user-2");
        anotherUser.setUsername("otro");
        anotherUser.setRol(Rol.SECRETARIO);

        adminUser = new Usuario();
        adminUser.setId("admin-id");
        adminUser.setUsername("admin");
        adminUser.setRol(Rol.ADMIN);
    }

    @Test
    public void testCompletarCambioPassword_RequiresOwnAccount() throws SQLException {
        SessionManager.setCurrentUser(currentUser);
        assertThrows(SecurityException.class,
            () -> repo.completarCambioPassword(anotherUser.getId(), "new_hash"),
            "No se puede cambiar la contraseña de otra cuenta");
    }

    @Test
    public void testCompletarCambioPassword_AllowsOwnAccount() throws SQLException {
        SessionManager.setCurrentUser(currentUser);
        // No debe lanzar SecurityException — puede que falle por persistencia,
        // pero el permiso debe ser aceptado. Se acepta SQLException.
        try {
            repo.completarCambioPassword(currentUser.getId(), "new_hash");
        } catch (SecurityException e) {
            fail("El usuario no debe recibir SecurityException para su propia cuenta: " + e.getMessage());
        }
    }

    @Test
    public void testUpdatePassword_RequiresAdmin() throws SQLException {
        SessionManager.setCurrentUser(currentUser);
        assertThrows(SecurityException.class,
            () -> repo.updatePassword(anotherUser.getId(), "reset_hash"),
            "Solo un Admin puede resetear contraseñas");
    }

    @Test
    public void testUpdatePassword_AllowsAdmin() throws SQLException {
        SessionManager.setCurrentUser(adminUser);
        try {
            repo.updatePassword(anotherUser.getId(), "reset_hash");
        } catch (SecurityException e) {
            fail("Un Admin no debe recibir SecurityException: " + e.getMessage());
        }
    }
}
