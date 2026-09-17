package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the darDeBaja overloads in ProductoService, focusing on:
 *  1. The motivo-blank guard (existing behaviour preserved by the refactor).
 *  2. The full dictamen-field overload succeeds in demo mode (no live DB needed).
 *
 * Uses demo mode + an admin session so SessionManager.isAreaAccessible() returns
 * true for all areas (demo products belong to SGM, RH, ADM, etc.).
 */
class ProductoBajaDictamenTest {

    /** Demo product that is active (stock > 0, no fecha_baja) and lives in SGM area.
     *  Defined in DemoDataStore: "Escritorio ejecutivo de madera", código MB-001. */
    private static final String DEMO_PRODUCTO_ID = "p-01";

    private ProductoService service;

    @BeforeEach
    void setUp() {
        DatabaseConfig.setDemoMode(true);
        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Test Admin");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);

        service = new ProductoService();
    }

    @AfterEach
    void tearDown() {
        DatabaseConfig.setDemoMode(false);
        SessionManager.logout();
    }

    // ── Guard: motivo blank ───────────────────────────────────────────────────

    @Test
    void testDarDeBaja_motivoBlank_throws() {
        ProductoService.ValidationException ex = assertThrows(
            ProductoService.ValidationException.class,
            () -> service.darDeBaja(DEMO_PRODUCTO_ID, "   ")
        );
        assertTrue(ex.getMessage().toLowerCase().contains("motivo"),
            "El mensaje debe mencionar 'motivo': " + ex.getMessage());
    }

    @Test
    void testDarDeBaja_motivoNull_throws() {
        ProductoService.ValidationException ex = assertThrows(
            ProductoService.ValidationException.class,
            () -> service.darDeBaja(DEMO_PRODUCTO_ID, null)
        );
        assertTrue(ex.getMessage().toLowerCase().contains("motivo"),
            "El mensaje debe mencionar 'motivo': " + ex.getMessage());
    }

    @Test
    void testDarDeBaja_motivoBlank_fullOverload_throws() {
        ProductoService.ValidationException ex = assertThrows(
            ProductoService.ValidationException.class,
            () -> service.darDeBaja(
                DEMO_PRODUCTO_ID,
                "",                    // blank motivo — should throw
                "Donación",
                "Dictamen 2026-01",
                "ACTA-001",
                LocalDate.of(2026, 1, 15)
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("motivo"),
            "El mensaje debe mencionar 'motivo': " + ex.getMessage());
    }

    // ── Full overload with dictamen fields succeeds in demo mode ─────────────

    @Test
    void testDarDeBaja_conDictamen_sinExcepcion() {
        // In demo mode, darDeBaja delegates to DemoDataStore.darDeBajaProducto()
        // which accepts any motivo and ignores the extra dictamen fields —
        // the important thing is that no exception is thrown from the service layer.
        assertDoesNotThrow(() ->
            service.darDeBaja(
                DEMO_PRODUCTO_ID,
                "Deterioro irreparable",
                "Donación",
                "El bien presenta deterioro severo por uso",
                "ACTA-SIB-2026-004",
                LocalDate.of(2026, 3, 10)
            ),
            "darDeBaja con todos los campos de dictamen no debe lanzar excepción en modo demo"
        );
    }

    @Test
    void testDarDeBaja_sinDictamen_sinExcepcion() {
        // Simple one-argument overload: no dictamen fields
        assertDoesNotThrow(() ->
            service.darDeBaja(DEMO_PRODUCTO_ID, "Pérdida por siniestro"),
            "darDeBaja sin campos de dictamen no debe lanzar excepción en modo demo"
        );
    }

    // ── Product not found ─────────────────────────────────────────────────────

    @Test
    void testDarDeBaja_productoInexistente_throws() {
        ProductoService.ValidationException ex = assertThrows(
            ProductoService.ValidationException.class,
            () -> service.darDeBaja("id-que-no-existe", "Motivo válido")
        );
        assertNotNull(ex.getMessage());
    }
}
