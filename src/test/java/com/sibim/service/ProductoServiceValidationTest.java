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

class ProductoServiceValidationTest {

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

    @Test
    void save_nullNombre_throwsValidation() {
        Producto p = validProducto();
        p.setNombre(null);
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("nombre"));
    }

    @Test
    void save_blankNombre_throwsValidation() {
        Producto p = validProducto();
        p.setNombre("   ");
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("nombre"));
    }

    @Test
    void save_nullCodigo_throwsValidation() {
        Producto p = validProducto();
        p.setCodigo(null);
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("codigo") || ex.getMessage().contains("código"));
    }

    @Test
    void save_nullCategoria_throwsValidation() {
        Producto p = validProducto();
        p.setCategoriaId(null);
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("categoria") || ex.getMessage().contains("categoría"));
    }

    @Test
    void save_nullArea_throwsValidation() {
        Producto p = validProducto();
        p.setArea(null);
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("area") || ex.getMessage().contains("área"));
    }

    @Test
    void save_negativePrecioCompra_throwsValidation() {
        Producto p = validProducto();
        p.setPrecioCompra(new BigDecimal("-1"));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("precio") || ex.getMessage().contains("compra"));
    }

    @Test
    void save_negativePrecioVenta_throwsValidation() {
        Producto p = validProducto();
        p.setPrecioVenta(new BigDecimal("-0.01"));
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("precio") || ex.getMessage().contains("venta"));
    }

    @Test
    void save_negativeStock_throwsValidation() {
        Producto p = validProducto();
        p.setStockActual(-1);
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("stock"));
    }

    @Test
    void save_stockMinimoMayorQueMaximo_throwsValidation() {
        Producto p = validProducto();
        p.setStockMinimo(20);
        p.setStockMaximo(5);
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.save(p));
        assertTrue(ex.getMessage().contains("minimo") || ex.getMessage().contains("mínimo"));
    }

    private Producto validProducto() {
        Producto p = new Producto();
        p.setNombre("Bien de Prueba Unit");
        p.setCodigo("TEST-UNIT-999");
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
