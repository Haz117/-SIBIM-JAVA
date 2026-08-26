package com.sibim.service;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/** Valida que solo Admins pueden aprobar/rechazar transferencias,
 *  independientemente de cómo se invoque el servicio. */
public class MovimientoServiceAuthorizationTest {

    private MovimientoService service;
    private Usuario adminUser;
    private Usuario secretarioUser;

    @BeforeEach
    public void setUp() {
        service = new MovimientoService();
        adminUser = new Usuario();
        adminUser.setId("admin-id");
        adminUser.setUsername("admin");
        adminUser.setRol(Rol.ADMIN);
        adminUser.setArea(null);

        secretarioUser = new Usuario();
        secretarioUser.setId("secretario-id");
        secretarioUser.setUsername("secretario");
        secretarioUser.setRol(Rol.SECRETARIO);
        secretarioUser.setArea("Tesorería");
    }

    @Test
    public void testAprobarTransferencia_RequiresAdmin() throws SQLException {
        SessionManager.setCurrentUser(secretarioUser);
        assertThrows(SecurityException.class,
            () -> service.aprobarTransferencia("movement-id"),
            "Un Secretario no puede aprobar transferencias");
    }

    @Test
    public void testRechazarTransferencia_RequiresAdmin() throws SQLException {
        SessionManager.setCurrentUser(secretarioUser);
        assertThrows(SecurityException.class,
            () -> service.rechazarTransferencia("movement-id"),
            "Un Secretario no puede rechazar transferencias");
    }

    @Test
    public void testAprobarTransferencia_AllowsAdmin() {
        SessionManager.setCurrentUser(adminUser);
        // El guard de autorización debe pasar; cualquier otra excepción (BD offline) es aceptable.
        try {
            service.aprobarTransferencia("movement-id");
        } catch (SecurityException e) {
            fail("Un Admin no debe recibir SecurityException: " + e.getMessage());
        } catch (Exception ignored) {}
    }

    @Test
    public void testRechazarTransferencia_AllowsAdmin() {
        SessionManager.setCurrentUser(adminUser);
        try {
            service.rechazarTransferencia("movement-id");
        } catch (SecurityException e) {
            fail("Un Admin no debe recibir SecurityException: " + e.getMessage());
        } catch (Exception ignored) {}
    }
}
