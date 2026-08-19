package com.sibim.session;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    @AfterEach
    void clearSession() {
        SessionManager.logout();
    }

    // ── Sin usuario ───────────────────────────────────────────────────────────

    @Test
    void sinUsuario_noEstaLogueado() {
        assertFalse(SessionManager.isLoggedIn());
    }

    @Test
    void sinUsuario_noEsAdmin() {
        assertFalse(SessionManager.isAdmin());
    }

    @Test
    void sinUsuario_isAreaAccessible_retornaFalse() {
        assertFalse(SessionManager.isAreaAccessible("Cualquier area"));
    }

    @Test
    void sinUsuario_getAccessibleAreas_retornaSetVacio() {
        Set<String> areas = SessionManager.getAccessibleAreas();
        assertNotNull(areas);
        assertTrue(areas.isEmpty());
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @Test
    void admin_isAdmin_retornaTrue() {
        SessionManager.setCurrentUser(usuario(Rol.ADMIN, null));
        assertTrue(SessionManager.isAdmin());
        assertFalse(SessionManager.isSecretario());
        assertFalse(SessionManager.isDireccion());
    }

    @Test
    void admin_getAccessibleAreas_retornaNull() {
        SessionManager.setCurrentUser(usuario(Rol.ADMIN, null));
        assertNull(SessionManager.getAccessibleAreas());
    }

    @Test
    void admin_isAreaAccessible_siempreTrue() {
        SessionManager.setCurrentUser(usuario(Rol.ADMIN, null));
        assertTrue(SessionManager.isAreaAccessible("Area X"));
        assertTrue(SessionManager.isAreaAccessible("Area inventada que no existe"));
        assertTrue(SessionManager.isAreaAccessible(""));
    }

    // ── Secretario ────────────────────────────────────────────────────────────

    @Test
    void secretario_accedeAAreaPropia() {
        SessionManager.setCurrentUser(usuario(Rol.SECRETARIO, "Secretaria General Municipal"));
        assertTrue(SessionManager.isAreaAccessible("Secretaria General Municipal"));
    }

    @Test
    void secretario_accedeADireccionesDeSubArea() {
        SessionManager.setCurrentUser(usuario(Rol.SECRETARIO, "Secretaria General Municipal"));
        // Definido en Areas.java bajo Secretaria General Municipal
        assertTrue(SessionManager.isAreaAccessible("Direccion de Recursos Humanos"));
        assertTrue(SessionManager.isAreaAccessible("Direccion de Administracion"));
    }

    @Test
    void secretario_noAccedeAOtraSecretaria() {
        SessionManager.setCurrentUser(usuario(Rol.SECRETARIO, "Secretaria General Municipal"));
        assertFalse(SessionManager.isAreaAccessible("Secretaria de Finanzas y Tesoreria"));
    }

    @Test
    void secretario_noAccedeADireccionDeOtraSecretaria() {
        SessionManager.setCurrentUser(usuario(Rol.SECRETARIO, "Secretaria General Municipal"));
        assertFalse(SessionManager.isAreaAccessible("Direccion de Seguridad Publica"));
    }

    // ── Dirección ─────────────────────────────────────────────────────────────

    @Test
    void direccion_soloAccedeAAreaPropia() {
        SessionManager.setCurrentUser(usuario(Rol.DIRECCION, "Direccion de Recursos Humanos"));
        assertTrue(SessionManager.isAreaAccessible("Direccion de Recursos Humanos"));
    }

    @Test
    void direccion_noAccedeASecretariaPadre() {
        SessionManager.setCurrentUser(usuario(Rol.DIRECCION, "Direccion de Recursos Humanos"));
        assertFalse(SessionManager.isAreaAccessible("Secretaria General Municipal"));
    }

    @Test
    void direccion_noAccedeAOtraDireccion() {
        SessionManager.setCurrentUser(usuario(Rol.DIRECCION, "Direccion de Recursos Humanos"));
        assertFalse(SessionManager.isAreaAccessible("Direccion de Administracion"));
    }

    @Test
    void direccion_getAccessibleAreas_soloUna() {
        SessionManager.setCurrentUser(usuario(Rol.DIRECCION, "Direccion X"));
        Set<String> areas = SessionManager.getAccessibleAreas();
        assertNotNull(areas);
        assertEquals(1, areas.size());
        assertTrue(areas.contains("Direccion X"));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_limpiaSesion() {
        SessionManager.setCurrentUser(usuario(Rol.ADMIN, null));
        assertTrue(SessionManager.isLoggedIn());
        SessionManager.logout();
        assertFalse(SessionManager.isLoggedIn());
        assertNull(SessionManager.getCurrentUser());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Usuario usuario(Rol rol, String area) {
        Usuario u = new Usuario();
        u.setId("u-test");
        u.setNombre("Usuario de Prueba");
        u.setRol(rol);
        u.setArea(area);
        return u;
    }
}
