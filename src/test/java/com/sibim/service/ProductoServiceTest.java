package com.sibim.service;

import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.model.enums.Rol;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductoServiceTest {

    @Mock ProductoRepository mockProductoRepo;
    @Mock AuditLogRepository mockAuditRepo;

    private ProductoService service;
    private Producto productoValido;
    private Usuario admin;
    private Usuario director;

    @BeforeEach
    void setUp() throws Exception {
        admin = new Usuario();
        admin.setId("u-admin"); admin.setNombre("Admin"); admin.setRol(Rol.ADMIN);

        director = new Usuario();
        director.setId("u-dir"); director.setNombre("Director");
        director.setRol(Rol.DIRECCION); director.setArea("Area Sin Acceso");

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

        // Default happy-path stubs (lenient — not all tests will trigger every call)
        when(mockProductoRepo.findById(any())).thenReturn(Optional.empty());
        when(mockProductoRepo.existsByCodigo(any(), any())).thenReturn(false);
        when(mockProductoRepo.save(any())).thenReturn(productoValido);

        service = new ProductoService(mockProductoRepo, mockAuditRepo);
    }

    @AfterEach
    void tearDown() { SessionManager.logout(); }

    // ── Validaciones de save() ────────────────────────────────────────────────

    @Test void save_nombreBlanco_lanzaValidation() {
        productoValido.setNombre("   ");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_nombreNull_lanzaValidation() {
        productoValido.setNombre(null);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_codigoBlanco_lanzaValidation() {
        productoValido.setCodigo("");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_categoriaNull_lanzaValidation() {
        productoValido.setCategoriaId(null);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_areaBlanca_lanzaValidation() {
        productoValido.setArea("   ");
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_precioCompraNegativo_lanzaValidation() {
        productoValido.setPrecioCompra(BigDecimal.valueOf(-1));
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_precioVentaNegativo_lanzaValidation() {
        productoValido.setPrecioVenta(BigDecimal.valueOf(-0.01));
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_stockActualNegativo_lanzaValidation() {
        productoValido.setStockActual(-1);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_stockMinimoMayorQueMaximo_lanzaValidation() {
        productoValido.setStockMinimo(15);
        productoValido.setStockMaximo(10);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_codigoDuplicado_lanzaValidation() throws Exception {
        when(mockProductoRepo.existsByCodigo("SL-001", null)).thenReturn(true);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_noAdmin_areaInaccesible_lanzaValidation() {
        SessionManager.setCurrentUser(director);
        assertThrows(ProductoService.ValidationException.class, () -> service.save(productoValido));
    }

    @Test void save_valido_devuelveProductoGuardado() throws Exception {
        Producto guardado = new Producto(); guardado.setId("p-nuevo"); guardado.setNombre("Silla ejecutiva");
        when(mockProductoRepo.save(any())).thenReturn(guardado);
        assertEquals("p-nuevo", service.save(productoValido).getId());
    }

    // ── delete() ─────────────────────────────────────────────────────────────

    @Test void delete_productoConMovimientos_muestraMensajeAmigable() throws Exception {
        Producto p = productoExistente();
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(p));
        doThrow(new SQLException("fk", "23503")).when(mockProductoRepo).delete("p-01");
        var ex = assertThrows(ProductoService.ValidationException.class, () -> service.delete("p-01"));
        assertTrue(ex.getMessage().contains("movimientos"));
    }

    @Test void delete_productoNoExiste_noLanzaExcepcion() {
        assertDoesNotThrow(() -> service.delete("no-existe"));
    }

    @Test void delete_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.delete("p-01"));
    }

    // ── darDeBaja() ───────────────────────────────────────────────────────────

    @Test void darDeBaja_motivoBlanco_lanzaValidation() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.darDeBaja("p-01", "   "));
    }

    @Test void darDeBaja_motivoNull_lanzaValidation() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.darDeBaja("p-01", null));
    }

    @Test void darDeBaja_productoNoExiste_lanzaValidation() {
        assertThrows(ProductoService.ValidationException.class, () -> service.darDeBaja("no-existe", "Perdido"));
    }

    @Test void darDeBaja_exitosa_llama_darDeBajaEnRepo() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        service.darDeBaja("p-01", "Robo");
        verify(mockProductoRepo).darDeBaja("p-01", "Robo");
    }

    // ── reactivar() ──────────────────────────────────────────────────────────

    @Test void reactivar_exitosa_llamaReactivarEnRepo() throws Exception {
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        service.reactivar("p-01");
        verify(mockProductoRepo).reactivar("p-01");
    }

    @Test void reactivar_productoNoExiste_lanzaValidation() {
        assertThrows(ProductoService.ValidationException.class, () -> service.reactivar("no-existe"));
    }

    @Test void reactivar_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(productoExistente()));
        assertThrows(ProductoService.ValidationException.class, () -> service.reactivar("p-01"));
    }

    // ── getAgotados() / getBajoStock() / getVencidosProximos() ───────────────

    @Test void getAgotados_retornaProductosConStockCero() throws Exception {
        Producto ag = productoConStock(0, 5, 20);
        when(mockProductoRepo.findAgotados()).thenReturn(List.of(ag));
        List<Producto> r = service.getAgotados();
        assertEquals(1, r.size());
        assertEquals(EstadoProducto.AGOTADO, r.get(0).getEstado());
    }

    @Test void getBajoStock_retornaProductosPorDebajoDeMinimo() throws Exception {
        Producto bajo = productoConStock(3, 5, 20);
        when(mockProductoRepo.findBajoStock()).thenReturn(List.of(bajo));
        List<Producto> r = service.getBajoStock();
        assertEquals(1, r.size());
        assertEquals(EstadoProducto.BAJO_STOCK, r.get(0).getEstado());
    }

    @Test void getVencidosProximos_incluyeVencidoYProximoAVencer() throws Exception {
        Producto v = productoConVencimiento(LocalDate.now().minusDays(1));
        Producto p = productoConVencimiento(LocalDate.now().plusDays(15));
        when(mockProductoRepo.findVencidosProximos(30)).thenReturn(List.of(v, p));
        assertEquals(2, service.getVencidosProximos(30).size());
    }

    @Test void getVencidosProximos_sinFechaVencimiento_noIncluido() throws Exception {
        when(mockProductoRepo.findVencidosProximos(30)).thenReturn(List.of());
        assertTrue(service.getVencidosProximos(30).isEmpty());
    }

    @Test void getVencidosProximos_ordenadosMasProximosPrimero() throws Exception {
        Producto p1 = productoConVencimiento(LocalDate.now().plusDays(25));
        Producto p2 = productoConVencimiento(LocalDate.now().plusDays(5));
        when(mockProductoRepo.findVencidosProximos(30)).thenReturn(List.of(p2, p1));
        List<Producto> r = service.getVencidosProximos(30);
        assertEquals(p2.getFechaVencimiento(), r.get(0).getFechaVencimiento());
    }

    // ── getAllIncludingBaja() / countAll() ────────────────────────────────────

    @Test void getAllIncludingBaja_llamaFindAllConTrue() throws Exception {
        Producto baja = new Producto(); baja.setFechaBaja(LocalDate.now().minusDays(1));
        when(mockProductoRepo.findAll(true)).thenReturn(List.of(baja));
        List<Producto> r = service.getAllIncludingBaja();
        assertEquals(1, r.size());
        verify(mockProductoRepo).findAll(true);
    }

    @Test void countAll_retornaTotalDeProductosActivos() throws Exception {
        when(mockProductoRepo.countAll()).thenReturn(2L);
        assertEquals(2L, service.countAll());
    }

    // ── Edge cases de validación ──────────────────────────────────────────────

    @Test void save_stockMinimoIgualAMaximo_esValido() {
        productoValido.setStockMinimo(10); productoValido.setStockMaximo(10);
        assertDoesNotThrow(() -> service.save(productoValido));
    }

    @Test void save_precioCompraCero_esValido() {
        productoValido.setPrecioCompra(BigDecimal.ZERO);
        assertDoesNotThrow(() -> service.save(productoValido));
    }

    @Test void save_stockMaximoCero_stockMinimoCero_esValido() {
        productoValido.setStockMinimo(0); productoValido.setStockMaximo(0); productoValido.setStockActual(0);
        assertDoesNotThrow(() -> service.save(productoValido));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Producto productoConStock(int actual, int min, int max) {
        Producto p = new Producto(); p.setStockActual(actual); p.setStockMinimo(min); p.setStockMaximo(max);
        return p;
    }

    private Producto productoConVencimiento(LocalDate fecha) {
        Producto p = new Producto(); p.setFechaVencimiento(fecha); p.setStockActual(5); p.setStockMinimo(1);
        return p;
    }

    private Producto productoExistente() {
        Producto p = new Producto(); p.setId("p-01"); p.setNombre("Escritorio ejecutivo");
        p.setArea("Secretaria General Municipal");
        return p;
    }
}
