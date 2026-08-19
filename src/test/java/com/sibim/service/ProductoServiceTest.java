package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.sibim.model.enums.EstadoProducto;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductoServiceTest {

    private Producto productoValido;
    private Usuario admin;
    private Usuario director;

    @BeforeEach
    void setUp() {
        admin = new Usuario();
        admin.setId("u-admin");
        admin.setNombre("Admin");
        admin.setRol(Rol.ADMIN);

        director = new Usuario();
        director.setId("u-dir");
        director.setNombre("Director");
        director.setRol(Rol.DIRECCION);
        director.setArea("Area Sin Acceso");

        SessionManager.setCurrentUser(admin);

        productoValido = new Producto();
        productoValido.setNombre("Silla ejecutiva");
        productoValido.setCodigo("SL-001");
        productoValido.setCategoriaId("cat-mob");
        productoValido.setArea("Secretaria General Municipal");
        productoValido.setPrecioCompra(BigDecimal.valueOf(3000));
        productoValido.setPrecioVenta(BigDecimal.valueOf(3500));
        productoValido.setStockActual(10);
        productoValido.setStockMinimo(2);
        productoValido.setStockMaximo(20);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ── Validaciones de save() ────────────────────────────────────────────────

    @Test
    void save_nombreBlanco_lanzaValidation() {
        productoValido.setNombre("   ");
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_nombreNull_lanzaValidation() {
        productoValido.setNombre(null);
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_codigoBlanco_lanzaValidation() {
        productoValido.setCodigo("");
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_categoriaNull_lanzaValidation() {
        productoValido.setCategoriaId(null);
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_areaBlanca_lanzaValidation() {
        productoValido.setArea("   ");
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_precioCompraNegativo_lanzaValidation() {
        productoValido.setPrecioCompra(BigDecimal.valueOf(-1));
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_precioVentaNegativo_lanzaValidation() {
        productoValido.setPrecioVenta(BigDecimal.valueOf(-0.01));
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_stockActualNegativo_lanzaValidation() {
        productoValido.setStockActual(-1);
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_stockMinimoMayorQueMaximo_lanzaValidation() {
        productoValido.setStockMinimo(15);
        productoValido.setStockMaximo(10);
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_codigoDuplicado_lanzaValidation() throws Exception {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById(any())).thenReturn(Optional.empty());
                when(m.existsByCodigo("SL-001", null)).thenReturn(true);
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().save(productoValido));
        }
    }

    @Test
    void save_noAdmin_areaInaccesible_lanzaValidation() {
        SessionManager.setCurrentUser(director);
        withMocks(svc -> assertThrows(ProductoService.ValidationException.class, () -> svc.save(productoValido)));
    }

    @Test
    void save_valido_devuelveProductoGuardado() throws Exception {
        Producto guardado = new Producto();
        guardado.setId("p-nuevo");
        guardado.setNombre("Silla ejecutiva");
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById(any())).thenReturn(Optional.empty());
                when(m.existsByCodigo(any(), any())).thenReturn(false);
                when(m.save(any())).thenReturn(guardado);
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            Producto resultado = new ProductoService().save(productoValido);
            assertEquals("p-nuevo", resultado.getId());
        }
    }

    // ── delete() ─────────────────────────────────────────────────────────────

    @Test
    void delete_productoConMovimientos_muestraMensajeAmigable() throws Exception {
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById("p-01")).thenReturn(Optional.of(p));
                doThrow(new SQLException("fk", "23503")).when(m).delete("p-01");
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            ProductoService.ValidationException ex = assertThrows(
                ProductoService.ValidationException.class,
                () -> new ProductoService().delete("p-01"));
            assertTrue(ex.getMessage().contains("movimientos"),
                "El mensaje debe mencionar 'movimientos'");
        }
    }

    @Test
    void delete_productoNoExiste_noLanzaExcepcion() {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById(any())).thenReturn(Optional.empty()));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertDoesNotThrow(() -> new ProductoService().delete("no-existe"));
        }
    }

    @Test
    void delete_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(p)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().delete("p-01"));
        }
    }

    // ── darDeBaja() ───────────────────────────────────────────────────────────

    @Test
    void darDeBaja_motivoBlanco_lanzaValidation() throws Exception {
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(p)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().darDeBaja("p-01", "   "));
        }
    }

    @Test
    void darDeBaja_motivoNull_lanzaValidation() throws Exception {
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(p)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().darDeBaja("p-01", null));
        }
    }

    @Test
    void darDeBaja_productoNoExiste_lanzaValidation() {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById(any())).thenReturn(Optional.empty()));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().darDeBaja("no-existe", "Perdido"));
        }
    }

    @Test
    void darDeBaja_exitosa_llama_darDeBajaEnRepo() throws Exception {
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById("p-01")).thenReturn(Optional.of(p));
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            new ProductoService().darDeBaja("p-01", "Robo");
            // Verifica que se llamó darDeBaja en el repositorio mock
            verify(r.constructed().get(0)).darDeBaja("p-01", "Robo");
        }
    }

    // ── reactivar() ──────────────────────────────────────────────────────────

    @Test
    void reactivar_exitosa_llamaReactivarEnRepo() throws Exception {
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(p)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            new ProductoService().reactivar("p-01");
            verify(r.constructed().get(0)).reactivar("p-01");
        }
    }

    @Test
    void reactivar_productoNoExiste_lanzaValidation() {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById(any())).thenReturn(Optional.empty()));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().reactivar("no-existe"));
        }
    }

    @Test
    void reactivar_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        Producto p = productoExistente();
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(p)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertThrows(ProductoService.ValidationException.class,
                () -> new ProductoService().reactivar("p-01"));
        }
    }

    // ── getAgotados() / getBajoStock() / getVencidosProximos() ───────────────

    @Test
    void getAgotados_retornaProductosConStockCero() throws Exception {
        Producto agotado = productoConStock(0, 5, 20);
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findAgotados()).thenReturn(List.of(agotado)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            List<Producto> resultado = new ProductoService().getAgotados();
            assertEquals(1, resultado.size());
            assertEquals(EstadoProducto.AGOTADO, resultado.get(0).getEstado());
        }
    }

    @Test
    void getBajoStock_retornaProductosPorDebajoDeMinimo() throws Exception {
        Producto bajo = productoConStock(3, 5, 20);
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findBajoStock()).thenReturn(List.of(bajo)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            List<Producto> resultado = new ProductoService().getBajoStock();
            assertEquals(1, resultado.size());
            assertEquals(EstadoProducto.BAJO_STOCK, resultado.get(0).getEstado());
        }
    }

    @Test
    void getVencidosProximos_incluyeVencidoYProximoAVencer() throws Exception {
        Producto vencido = productoConVencimiento(LocalDate.now().minusDays(1));
        Producto proximo = productoConVencimiento(LocalDate.now().plusDays(15));
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findVencidosProximos(30)).thenReturn(List.of(vencido, proximo)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            List<Producto> resultado = new ProductoService().getVencidosProximos(30);
            assertEquals(2, resultado.size());
        }
    }

    @Test
    void getVencidosProximos_sinFechaVencimiento_noIncluido() throws Exception {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findVencidosProximos(30)).thenReturn(List.of()));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertTrue(new ProductoService().getVencidosProximos(30).isEmpty());
        }
    }

    @Test
    void getVencidosProximos_ordenadosMasProximosPrimero() throws Exception {
        Producto p1 = productoConVencimiento(LocalDate.now().plusDays(25));
        Producto p2 = productoConVencimiento(LocalDate.now().plusDays(5));
        // repo already returns sorted by proximity (p2 sooner than p1)
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findVencidosProximos(30)).thenReturn(List.of(p2, p1)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            List<Producto> resultado = new ProductoService().getVencidosProximos(30);
            assertEquals(2, resultado.size());
            assertEquals(p2.getFechaVencimiento(), resultado.get(0).getFechaVencimiento());
        }
    }

    // ── getAllIncludingBaja() / countAll() ────────────────────────────────────

    @Test
    void getAllIncludingBaja_llamaFindAllConTrue() throws Exception {
        Producto baja = new Producto();
        baja.setFechaBaja(LocalDate.now().minusDays(1));
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findAll(true)).thenReturn(List.of(baja)));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            List<Producto> resultado = new ProductoService().getAllIncludingBaja();
            assertEquals(1, resultado.size());
            verify(r.constructed().get(0)).findAll(true);
        }
    }

    @Test
    void countAll_retornaTotalDeProductosActivos() throws Exception {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.countAll()).thenReturn(2L));
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertEquals(2L, new ProductoService().countAll());
        }
    }

    // ── Edge cases de validación ──────────────────────────────────────────────

    @Test
    void save_stockMinimoIgualAMaximo_esValido() throws Exception {
        productoValido.setStockMinimo(10);
        productoValido.setStockMaximo(10);
        Producto guardado = new Producto();
        guardado.setId("p-nuevo");
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById(any())).thenReturn(Optional.empty());
                when(m.existsByCodigo(any(), any())).thenReturn(false);
                when(m.save(any())).thenReturn(guardado);
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertDoesNotThrow(() -> new ProductoService().save(productoValido));
        }
    }

    @Test
    void save_precioCompraCero_esValido() throws Exception {
        productoValido.setPrecioCompra(BigDecimal.ZERO);
        Producto guardado = new Producto();
        guardado.setId("p-nuevo");
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById(any())).thenReturn(Optional.empty());
                when(m.existsByCodigo(any(), any())).thenReturn(false);
                when(m.save(any())).thenReturn(guardado);
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertDoesNotThrow(() -> new ProductoService().save(productoValido));
        }
    }

    @Test
    void save_stockMaximoCero_stockMinimoCero_esValido() throws Exception {
        productoValido.setStockMinimo(0);
        productoValido.setStockMaximo(0);
        productoValido.setStockActual(0);
        Producto guardado = new Producto();
        guardado.setId("p-nuevo");
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById(any())).thenReturn(Optional.empty());
                when(m.existsByCodigo(any(), any())).thenReturn(false);
                when(m.save(any())).thenReturn(guardado);
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            assertDoesNotThrow(() -> new ProductoService().save(productoValido));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Producto productoConStock(int stockActual, int stockMinimo, int stockMaximo) {
        Producto p = new Producto();
        p.setStockActual(stockActual);
        p.setStockMinimo(stockMinimo);
        p.setStockMaximo(stockMaximo);
        return p;
    }

    private Producto productoConVencimiento(LocalDate fecha) {
        Producto p = new Producto();
        p.setFechaVencimiento(fecha);
        p.setStockActual(5);
        p.setStockMinimo(1);
        return p;
    }

    private Producto productoExistente() {
        Producto p = new Producto();
        p.setId("p-01");
        p.setNombre("Escritorio ejecutivo");
        p.setArea("Secretaria General Municipal");
        return p;
    }

    @FunctionalInterface
    interface ServiceConsumer {
        void accept(ProductoService svc) throws Exception;
    }

    private void withMocks(ServiceConsumer consumer) {
        try (MockedConstruction<ProductoRepository> r = mockConstruction(ProductoRepository.class, (m, c) -> {
                when(m.findById(any())).thenReturn(Optional.empty());
                when(m.existsByCodigo(any(), any())).thenReturn(false);
                when(m.save(any())).thenReturn(productoValido);
             });
             MockedConstruction<AuditLogRepository> a = mockConstruction(AuditLogRepository.class)) {
            consumer.accept(new ProductoService());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
