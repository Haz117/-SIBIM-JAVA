package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MovimientoServiceValidationTest {

    // p-01 exists in DemoDataStore: stock=18, area="Secretaria General Municipal"
    private static final String PROD_ID  = "p-01";
    private static final String AREA_SGM = "Secretaria General Municipal";

    private final MovimientoService service = new MovimientoService();

    @BeforeEach
    void setUp() {
        DatabaseConfig.setDemoMode(true);
        SessionManager.setCurrentUser(new Usuario(
            "test-admin", "admin.test", "hash",
            "Admin Test", "Administrador", Rol.ADMIN, null,
            LocalDateTime.now()));
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    void registrar_productoInexistente_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("no-existe", TipoMovimiento.ENTRADA, 5, "test", null));
        assertTrue(ex.getMessage().toLowerCase().contains("producto"));
    }

    @Test
    void registrar_cantidadCero_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.ENTRADA, 0, "test", null));
        assertTrue(ex.getMessage().contains("cantidad") || ex.getMessage().contains("cero"));
    }

    @Test
    void registrar_cantidadNegativa_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.SALIDA, -3, "test", null));
        assertTrue(ex.getMessage().contains("cantidad") || ex.getMessage().contains("cero"));
    }

    @Test
    void registrar_salidaMayorQueStock_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.SALIDA, 9999, "test", null));
        assertTrue(ex.getMessage().contains("stock") || ex.getMessage().contains("disponible"));
    }

    @Test
    void registrar_transferenciaAreaDestinoNula_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.TRANSFERENCIA, 1, "test", null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("destino"));
    }

    @Test
    void registrar_transferenciaAreaDestinoBlank_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.TRANSFERENCIA, 1, "test", null, "  "));
        assertTrue(ex.getMessage().toLowerCase().contains("destino"));
    }

    @Test
    void registrar_transferenciaAreaIgualOrigen_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.TRANSFERENCIA, 1, "test", null, AREA_SGM));
        assertTrue(ex.getMessage().toLowerCase().contains("distinta") || ex.getMessage().toLowerCase().contains("destino"));
    }

    @Test
    void registrar_ajusteCantidadCero_throwsValidation() {
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar(PROD_ID, TipoMovimiento.AJUSTE, 0, "test", null));
        assertTrue(ex.getMessage().contains("ajuste") || ex.getMessage().contains("cero"));
    }
}
