package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.UnidadMedida;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ProductoServiceLengthValidationTest {

    private final ProductoService service = new ProductoService();

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

    // ── nombre ────────────────────────────────────────────────────────────────

    @Test
    void save_nombre201Chars_throwsValidation() {
        Producto p = validProducto();
        p.setNombre("A".repeat(201));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("nombre") || ex.getMessage().contains("200"));
    }

    @Test
    void save_nombre200Chars_valido() {
        Producto p = validProducto();
        p.setNombre("A".repeat(200));
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── codigo ────────────────────────────────────────────────────────────────

    @Test
    void save_codigo51Chars_throwsValidation() {
        Producto p = validProducto();
        p.setCodigo("X".repeat(51));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("codigo") || ex.getMessage().contains("código")
            || ex.getMessage().contains("50"));
    }

    @Test
    void save_codigo50Chars_valido() {
        Producto p = validProducto();
        p.setCodigo("X".repeat(50));
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── descripcion ───────────────────────────────────────────────────────────

    @Test
    void save_descripcion1001Chars_throwsValidation() {
        Producto p = validProducto();
        p.setDescripcion("D".repeat(1001));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("descripcion") || ex.getMessage().contains("descripción")
            || ex.getMessage().contains("1000"));
    }

    @Test
    void save_descripcion1000Chars_valido() {
        Producto p = validProducto();
        p.setDescripcion("D".repeat(1000));
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── proveedor ─────────────────────────────────────────────────────────────

    @Test
    void save_proveedor201Chars_throwsValidation() {
        Producto p = validProducto();
        p.setProveedor("P".repeat(201));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("proveedor") || ex.getMessage().contains("200"));
    }

    // ── ubicacion ─────────────────────────────────────────────────────────────

    @Test
    void save_ubicacion201Chars_throwsValidation() {
        Producto p = validProducto();
        p.setUbicacion("U".repeat(201));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("ubicacion") || ex.getMessage().contains("ubicación")
            || ex.getMessage().contains("200"));
    }

    // ── resguardante ──────────────────────────────────────────────────────────

    @Test
    void save_resguardante201Chars_throwsValidation() {
        Producto p = validProducto();
        p.setResguardante("R".repeat(201));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("resguardante") || ex.getMessage().contains("200"));
    }

    // ── campos nulos opcionales aceptados ─────────────────────────────────────

    @Test
    void save_camposOpcionalesNulos_valido() {
        Producto p = validProducto();
        p.setDescripcion(null);
        p.setProveedor(null);
        p.setUbicacion(null);
        p.setResguardante(null);
        assertDoesNotThrow(() -> service.save(p));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Producto validProducto() {
        Producto p = new Producto();
        p.setNombre("Bien de Prueba Longitud");
        p.setCodigo("LEN-TEST-" + System.nanoTime() % 100000); // único por ejecución
        p.setCategoriaId("cat-mob");
        p.setArea("Direccion de Administracion");
        p.setUnidad(UnidadMedida.PIEZA);
        p.setPrecioCompra(BigDecimal.ZERO);
        p.setPrecioVenta(BigDecimal.ZERO);
        p.setStockActual(0);
        p.setStockMinimo(0);
        p.setStockMaximo(10);
        return p;
    }
}
