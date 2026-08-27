package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.MovimientoRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovimientoServiceTest {

    @Mock MovimientoRepository mockMovimientoRepo;
    @Mock ProductoRepository   mockProductoRepo;
    @Mock AuditLogRepository   mockAuditRepo;

    private MovimientoService service;
    private Producto producto;
    private Usuario admin;
    private Usuario director;

    @BeforeEach
    void setUp() throws Exception {
        producto = new Producto();
        producto.setId("p-01"); producto.setNombre("Escritorio ejecutivo");
        producto.setArea("Secretaria General Municipal");
        producto.setStockActual(18); producto.setStockMinimo(5); producto.setStockMaximo(30);
        producto.setPrecioCompra(BigDecimal.valueOf(4500)); producto.setPrecioVenta(BigDecimal.valueOf(5000));

        admin = new Usuario(); admin.setId("u-admin"); admin.setNombre("Admin"); admin.setRol(Rol.ADMIN);
        director = new Usuario(); director.setId("u-dir"); director.setNombre("Director");
        director.setRol(Rol.DIRECCION); director.setArea("Area Sin Acceso");

        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(producto));
        when(mockProductoRepo.findById(argThat(id -> !"p-01".equals(id)))).thenReturn(Optional.empty());
        when(mockMovimientoRepo.addMovimientoAtomic(any(), any())).thenReturn(new Movimiento());

        service = new MovimientoService(mockMovimientoRepo, mockProductoRepo, mockAuditRepo);
    }

    @AfterEach
    void tearDown() { SessionManager.logout(); }

    // ── Validaciones de cantidad ──────────────────────────────────────────────

    @Test void registrar_entradaCantidadCero_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.ENTRADA, 0, "test", null));
    }

    @Test void registrar_entradaCantidadNegativa_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.ENTRADA, -1, "test", null));
    }

    @Test void registrar_salidaCantidadMayorQueStock_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.SALIDA, 100, "Retiro", null));
    }

    @Test void registrar_salidaCantidadExacta_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        when(mockMovimientoRepo.addMovimientoAtomic(any(), any())).thenReturn(esperado);
        assertSame(esperado, service.registrar("p-01", TipoMovimiento.SALIDA, 18, "Retiro total", null));
    }

    @Test void registrar_ajusteCantidadCero_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.AJUSTE, 0, "Corrección", null));
    }

    // ── Validaciones de transferencia ─────────────────────────────────────────

    @Test void registrar_transferenciaSinAreaDestino_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 5, "Traspaso", null, null));
    }

    @Test void registrar_transferenciaAreaDestinoBlanca_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 5, "Traspaso", null, "   "));
    }

    @Test void registrar_transferenciaMismaArea_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 1, "Traspaso",
                null, "Secretaria General Municipal"));
    }

    // ── Validación de acceso por área ─────────────────────────────────────────

    @Test void registrar_usuarioSinAccesoAlArea_lanzaValidation() {
        SessionManager.setCurrentUser(director);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.ENTRADA, 5, "test", null));
    }

    @Test void registrar_productoNoExiste_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("no-existe", TipoMovimiento.ENTRADA, 5, "test", null));
    }

    // ── Éxito ─────────────────────────────────────────────────────────────────

    @Test void registrar_entrada_exitosa_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        when(mockMovimientoRepo.addMovimientoAtomic(any(), any())).thenReturn(esperado);
        assertSame(esperado, service.registrar("p-01", TipoMovimiento.ENTRADA, 5, "Compra", "REF-01"));
    }

    @Test void registrar_ajuste_exitoso_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        when(mockMovimientoRepo.addMovimientoAtomic(any(), any())).thenReturn(esperado);
        assertSame(esperado, service.registrar("p-01", TipoMovimiento.AJUSTE, 20, "Conteo físico", null));
    }

    // ── eliminar() ───────────────────────────────────────────────────────────

    @Test void eliminar_admin_llama_deleteAtomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01"));
        service.eliminar("mov-01");
        verify(mockMovimientoRepo).deleteMovimientoAtomic("mov-01");
    }

    @Test void eliminar_movimientoNoExiste_noLanzaExcepcion() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findProductoIdById(any())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.eliminar("no-existe"));
    }

    @Test void eliminar_noAdmin_areaInaccesible_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        when(mockMovimientoRepo.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01"));
        assertThrows(MovimientoService.ValidationException.class, () -> service.eliminar("mov-01"));
    }

    @Test void eliminar_noAdmin_areaAccesible_llama_deleteAtomic() throws Exception {
        Producto pPropio = new Producto(); pPropio.setId("p-01"); pPropio.setArea("Area Sin Acceso");
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(pPropio));
        when(mockMovimientoRepo.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01"));
        SessionManager.setCurrentUser(director);
        service.eliminar("mov-01");
        verify(mockMovimientoRepo).deleteMovimientoAtomic("mov-01");
    }

    @Test void eliminar_noAdmin_productoHuerfano_lanzaValidation() throws Exception {
        SessionManager.setCurrentUser(director);
        when(mockMovimientoRepo.findProductoIdById("mov-01")).thenReturn(Optional.of("p-deleted"));
        when(mockProductoRepo.findById("p-deleted")).thenReturn(Optional.empty());
        assertThrows(MovimientoService.ValidationException.class, () -> service.eliminar("mov-01"));
    }

    @Test void eliminar_admin_productoHuerfano_permitido() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findProductoIdById("mov-01")).thenReturn(Optional.of("p-deleted"));
        when(mockProductoRepo.findById("p-deleted")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.eliminar("mov-01"));
        verify(mockMovimientoRepo).deleteMovimientoAtomic("mov-01");
    }

    // ── eliminar() — bloqueo de PENDIENTE ────────────────────────────────────

    @Test void eliminar_movimientoPendiente_throwsValidation() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findEstadoById("mov-pend"))
            .thenReturn(Optional.of(Movimiento.ESTADO_PENDIENTE));
        var ex = assertThrows(MovimientoService.ValidationException.class,
            () -> service.eliminar("mov-pend"));
        assertTrue(ex.getMessage().toLowerCase().contains("pendiente"));
    }

    @Test void eliminar_movimientoPendiente_noLlamaDeleteAtomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findEstadoById(any()))
            .thenReturn(Optional.of(Movimiento.ESTADO_PENDIENTE));
        assertThrows(MovimientoService.ValidationException.class, () -> service.eliminar("mov-pend"));
        verify(mockMovimientoRepo, never()).deleteMovimientoAtomic(any());
    }

    @Test void eliminar_movimientoAprobado_procedeNormal() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findEstadoById("mov-ok")).thenReturn(Optional.of(Movimiento.ESTADO_APROBADO));
        when(mockMovimientoRepo.findProductoIdById("mov-ok")).thenReturn(Optional.of("p-01"));
        assertDoesNotThrow(() -> service.eliminar("mov-ok"));
        verify(mockMovimientoRepo).deleteMovimientoAtomic("mov-ok");
    }

    @Test void eliminar_estadoVacio_procedeNormal() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findEstadoById(any())).thenReturn(Optional.empty());
        when(mockMovimientoRepo.findProductoIdById("mov-x")).thenReturn(Optional.of("p-01"));
        assertDoesNotThrow(() -> service.eliminar("mov-x"));
        verify(mockMovimientoRepo).deleteMovimientoAtomic("mov-x");
    }
}
