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
 * Tests validation logic in ComodatoService.crear().
 * All asserted exceptions are thrown before any repository call, so
 * demo mode is sufficient (no live DB required).
 */
class ComodatoServiceValidationTest {

    private ComodatoService service;

    // Arbitrary valid values to use when a field is NOT the one under test
    private static final String  VALID_PRODUCTO_ID     = "p-01";
    private static final String  VALID_ENTIDAD         = "Cruz Roja Municipal";
    private static final String  VALID_CONTACTO        = "Juan Pérez";
    private static final LocalDate VALID_FECHA_INICIO  = LocalDate.now();
    private static final LocalDate VALID_FECHA_FIN     = LocalDate.now().plusMonths(6);

    @BeforeEach
    void setUp() {
        DatabaseConfig.setDemoMode(true);
        // Admin has unrestricted area access — needed by ProductoRepository.findById
        // which checks accessible areas.
        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Test Admin");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);

        service = new ComodatoService();
    }

    @AfterEach
    void tearDown() {
        DatabaseConfig.setDemoMode(false);
        SessionManager.logout();
    }

    @Test
    void testCrear_entidadReceptoraBlank_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                "   ",           // blank entidad — should throw
                VALID_CONTACTO, null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("entidad"),
            "El mensaje debe mencionar 'entidad': " + ex.getMessage());
    }

    @Test
    void testCrear_entidadReceptoraNula_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                null,            // null entidad — should throw
                VALID_CONTACTO, null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("entidad"),
            "El mensaje debe mencionar 'entidad': " + ex.getMessage());
    }

    @Test
    void testCrear_contactoNombreBlank_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD,
                "  ",            // blank contacto — should throw
                null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("contacto"),
            "El mensaje debe mencionar 'contacto': " + ex.getMessage());
    }

    @Test
    void testCrear_fechaFinBeforeFechaInicio_throws() {
        LocalDate inicio = LocalDate.now();
        LocalDate fin    = inicio.minusDays(1);   // one day BEFORE inicio — invalid

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                inicio, fin
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("fecha"),
            "El mensaje debe mencionar 'fecha': " + ex.getMessage());
    }

    @Test
    void testCrear_fechaFinEqualsFechaInicio_throws() {
        // "fin" equal to "inicio" is also invalid — fechaFin must be AFTER fechaInicio
        LocalDate same = LocalDate.now();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                same, same
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("fecha"),
            "El mensaje debe mencionar 'fecha': " + ex.getMessage());
    }

    @Test
    void testCrear_productoIdNull_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                null,            // null productoId — should throw first
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("bien") ||
                   ex.getMessage().toLowerCase().contains("producto"),
            "El mensaje debe mencionar 'bien' o 'producto': " + ex.getMessage());
    }

    @Test
    void testCrear_productoIdBlank_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                "   ",           // blank productoId
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                VALID_FECHA_INICIO, VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("bien") ||
                   ex.getMessage().toLowerCase().contains("producto"),
            "El mensaje debe mencionar 'bien' o 'producto': " + ex.getMessage());
    }

    @Test
    void testCrear_fechaInicioNull_throws() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.crear(
                VALID_PRODUCTO_ID,
                VALID_ENTIDAD, VALID_CONTACTO,
                null, null, null, null,
                null,            // null fechaInicio — should throw
                VALID_FECHA_FIN
            )
        );
        assertTrue(ex.getMessage().toLowerCase().contains("inicio") ||
                   ex.getMessage().toLowerCase().contains("fecha"),
            "El mensaje debe mencionar 'inicio' o 'fecha': " + ex.getMessage());
    }
}
