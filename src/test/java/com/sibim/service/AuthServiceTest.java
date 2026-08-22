package com.sibim.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.UsuarioRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @BeforeEach
    void reset() throws Exception {
        // Limpia el rate-limiter entre tests (campo estático privado)
        Field f = AuthService.class.getDeclaredField("fallidos");
        f.setAccessible(true);
        ((ConcurrentHashMap<?, ?>) f.get(null)).clear();
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false); // usa BCrypt real, sin DemoDataStore
    }

    @AfterEach
    void cleanup() {
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
    }

    // ── Inputs nulos / vacíos ─────────────────────────────────────────────────

    @Test
    void login_usuarioNull_lanzaAuthException() {
        assertThrows(AuthService.AuthException.class,
            () -> new AuthService().login(null, "cualquier"));
    }

    @Test
    void login_usuarioBlank_lanzaAuthException() {
        assertThrows(AuthService.AuthException.class,
            () -> new AuthService().login("   ", "cualquier"));
    }

    @Test
    void login_passwordNull_lanzaAuthException() {
        assertThrows(AuthService.AuthException.class,
            () -> new AuthService().login("usuario", null));
    }

    @Test
    void login_passwordBlank_lanzaAuthException() {
        assertThrows(AuthService.AuthException.class,
            () -> new AuthService().login("usuario", ""));
    }

    // ── Usuario no existe ─────────────────────────────────────────────────────

    @Test
    void login_usuarioInexistente_lanzaAuthException() throws Exception {
        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername(anyString())).thenReturn(Optional.empty()))) {
            AuthService svc = new AuthService();
            AuthService.AuthException ex = assertThrows(AuthService.AuthException.class,
                () -> svc.login("nadie", "cualquier"));
            assertTrue(ex.getMessage().contains("incorrectos"));
        }
    }

    // ── Contraseña incorrecta ─────────────────────────────────────────────────

    @Test
    void login_contrasenhaIncorrecta_lanzaAuthException() throws Exception {
        // factor 4 para que el test sea rápido
        String hash = BCrypt.withDefaults().hashToString(4, "correcta".toCharArray());
        Usuario u = usuario(hash);

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername("testuser")).thenReturn(Optional.of(u)))) {
            AuthService svc = new AuthService();
            AuthService.AuthException ex = assertThrows(AuthService.AuthException.class,
                () -> svc.login("testuser", "incorrecta"));
            assertTrue(ex.getMessage().contains("incorrectos"));
        }
    }

    // ── Login exitoso ─────────────────────────────────────────────────────────

    @Test
    void login_exitoso_devuelveUsuario() throws Exception {
        String hash = BCrypt.withDefaults().hashToString(4, "buena123".toCharArray());
        Usuario u = usuario(hash);

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername("testuser")).thenReturn(Optional.of(u)))) {
            AuthService svc = new AuthService();
            Usuario result = svc.login("testuser", "buena123");
            assertEquals(u.getId(), result.getId());
            assertNotNull(SessionManager.getCurrentUser());
            assertEquals(u.getId(), SessionManager.getCurrentUser().getId());
        }
    }

    @Test
    void login_exitoso_setaSesion() throws Exception {
        String hash = BCrypt.withDefaults().hashToString(4, "pass".toCharArray());
        Usuario u = usuario(hash);

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername("testuser")).thenReturn(Optional.of(u)))) {
            assertNull(SessionManager.getCurrentUser());
            new AuthService().login("testuser", "pass");
            assertNotNull(SessionManager.getCurrentUser());
        }
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    @Test
    void login_5FallosSeguidos_el6toBloqueaConMensajeDeMinutos() throws Exception {
        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername(anyString())).thenReturn(Optional.empty()))) {
            AuthService svc = new AuthService();
            for (int i = 0; i < 5; i++) {
                assertThrows(AuthService.AuthException.class, () -> svc.login("victima", "mal"));
            }
            AuthService.AuthException lockout = assertThrows(AuthService.AuthException.class,
                () -> svc.login("victima", "cualquier"));
            assertTrue(lockout.getMessage().contains("minuto"),
                "El mensaje de bloqueo debe indicar cuántos minutos esperar");
        }
    }

    @Test
    void login_rateLimiting_noAfectaOtrosUsuarios() throws Exception {
        String hash = BCrypt.withDefaults().hashToString(4, "clave".toCharArray());
        Usuario otro = usuario(hash);
        otro.setUsername("otro");

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> {
                    when(mock.findByUsername("victima")).thenReturn(Optional.empty());
                    when(mock.findByUsername("otro")).thenReturn(Optional.of(otro));
                })) {
            AuthService svc = new AuthService();
            // Bloquear "victima"
            for (int i = 0; i < 5; i++) {
                assertThrows(AuthService.AuthException.class, () -> svc.login("victima", "x"));
            }
            // "otro" usuario diferente — no debe estar bloqueado
            assertDoesNotThrow(() -> svc.login("otro", "clave"));
        }
    }

    @Test
    void login_exitoso_limpiContadorFallos() throws Exception {
        String hash = BCrypt.withDefaults().hashToString(4, "buena".toCharArray());
        Usuario u = usuario(hash);

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername(anyString()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(u)))) {  // 4to intento exitoso
            AuthService svc = new AuthService();
            for (int i = 0; i < 3; i++) {
                assertThrows(AuthService.AuthException.class, () -> svc.login("user", "x"));
            }
            // Login exitoso limpia el contador
            assertDoesNotThrow(() -> svc.login("user", "buena"));
        }
    }

    // ── Modo Demo ─────────────────────────────────────────────────────────────

    @Test
    void login_modoDemo_passwordCorrecto_autenticaExitosamente() throws Exception {
        DatabaseConfig.setDemoMode(true);
        // superusuario/admin123456 está en DemoDataStore.DEMO_PASSWORDS
        Usuario u = new Usuario();
        u.setId("u-admin");
        u.setUsername("superusuario");
        u.setNombre("Administrador");
        u.setRol(com.sibim.model.enums.Rol.ADMIN);

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername("superusuario")).thenReturn(Optional.of(u)))) {
            Usuario result = new AuthService().login("superusuario", "admin123456");
            assertEquals("u-admin", result.getId());
            assertNotNull(SessionManager.getCurrentUser());
        }
    }

    @Test
    void login_modoDemo_passwordIncorrecto_lanzaAuthException() throws Exception {
        DatabaseConfig.setDemoMode(true);
        Usuario u = new Usuario();
        u.setId("u-admin");
        u.setUsername("superusuario");
        u.setRol(com.sibim.model.enums.Rol.ADMIN);

        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername("superusuario")).thenReturn(Optional.of(u)))) {
            AuthService.AuthException ex = assertThrows(AuthService.AuthException.class,
                () -> new AuthService().login("superusuario", "incorrecta"));
            assertTrue(ex.getMessage().contains("incorrectos"));
        }
    }

    @Test
    void login_modoDemo_usuarioInexistente_lanzaAuthException() throws Exception {
        DatabaseConfig.setDemoMode(true);
        try (MockedConstruction<UsuarioRepository> ignored = mockConstruction(UsuarioRepository.class,
                (mock, ctx) -> when(mock.findByUsername(anyString())).thenReturn(Optional.empty()))) {
            assertThrows(AuthService.AuthException.class,
                () -> new AuthService().login("fantasma", "x"));
        }
    }

    // ── hashPassword ──────────────────────────────────────────────────────────

    @Test
    void hashPassword_produceBcryptValido() {
        String hash = new AuthService().hashPassword("mi_clave_segura");
        assertTrue(hash.startsWith("$2a$"), "Debe ser formato BCrypt");
        assertTrue(BCrypt.verifyer().verify("mi_clave_segura".toCharArray(), hash).verified);
    }

    @Test
    void hashPassword_mismaClave_producesHashesDistintos() {
        AuthService svc = new AuthService();
        String h1 = svc.hashPassword("clave");
        String h2 = svc.hashPassword("clave");
        assertNotEquals(h1, h2, "BCrypt es salted — cada hash debe ser único");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Usuario usuario(String hash) {
        Usuario u = new Usuario();
        u.setId("u-test");
        u.setUsername("testuser");
        u.setPasswordHash(hash);
        u.setNombre("Test User");
        u.setRol(Rol.ADMIN);
        return u;
    }
}
