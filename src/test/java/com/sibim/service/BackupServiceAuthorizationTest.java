package com.sibim.service;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.fail;

/** Valida que solo Admins pueden ejecutar backup y restore. */
public class BackupServiceAuthorizationTest {

    private BackupService service;
    private Usuario adminUser;
    private Usuario secretarioUser;
    private File tempFile;

    @BeforeEach
    public void setUp() {
        service = new BackupService();
        adminUser = new Usuario();
        adminUser.setId("admin-id");
        adminUser.setUsername("admin");
        adminUser.setRol(Rol.ADMIN);

        secretarioUser = new Usuario();
        secretarioUser.setId("secretario-id");
        secretarioUser.setUsername("secretario");
        secretarioUser.setRol(Rol.SECRETARIO);
        secretarioUser.setArea("Tesorería");

        tempFile = new File(System.getProperty("java.io.tmpdir"), "backup_test.json");
    }

    @Test
    public void testBackup_RequiresAdmin() throws SQLException {
        SessionManager.setCurrentUser(secretarioUser);
        assertThrows(SecurityException.class,
            () -> service.backup(tempFile),
            "Un Secretario no puede ejecutar backup");
    }

    @Test
    public void testRestore_RequiresAdmin() throws SQLException {
        SessionManager.setCurrentUser(secretarioUser);
        assertThrows(SecurityException.class,
            () -> service.restore(tempFile),
            "Un Secretario no puede ejecutar restore");
    }

    @Test
    public void testBackup_AllowsAdmin() throws SQLException {
        SessionManager.setCurrentUser(adminUser);
        // No debe lanzar SecurityException — puede que falle por conexión offline,
        // pero el permiso debe ser aceptado. Se acepta IOException o SQLException.
        try {
            service.backup(tempFile);
        } catch (SecurityException e) {
            fail("Un Admin no debe recibir SecurityException: " + e.getMessage());
        }
    }

    @Test
    public void testRestore_AllowsAdmin() throws SQLException {
        SessionManager.setCurrentUser(adminUser);
        try {
            service.restore(tempFile);
        } catch (SecurityException e) {
            fail("Un Admin no debe recibir SecurityException: " + e.getMessage());
        }
    }
}
