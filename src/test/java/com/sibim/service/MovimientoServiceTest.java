package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MovimientoServiceTest {

    private Producto producto;
    private Usuario admin;
    private Usuario director;

    @BeforeEach
    void setUp() {
        producto = new Producto();
        producto.setId("p-01");
        producto.setNombre("Escritorio ejecutivo");
        producto.setArea("Secretaria General Municipal");
        producto.setStockActual(18);
        producto.setStockMinimo(5);
        producto.setStockMaximo(30);
        producto.setPrecioCompra(BigDecimal.valueOf(4500));
        producto.setPrecioVenta(BigDecimal.valueOf(5000));

        admin = new Usuario();
        admin.setId("u-admin");
        admin.setNombre("Admin");
        admin.setRol(Rol.ADMIN);

        director = new Usuario();
        director.setId("u-dir");
        director.setNombre("Director");
        director.setRol(Rol.DIRECCION);
        director.setArea("Area Sin Acceso");
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    // ── Validaciones de cantidad ──────────────────────────────────────────────

    @Test
    void registrar_entradaCantidadCero_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.ENTRADA, 0, "test", null))
        );
    }

    @Test
    void registrar_entradaCantidadNegativa_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.ENTRADA, -1, "test", null))
        );
    }

    @Test
    void registrar_salidaCantidadMayorQueStock_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.SALIDA, 100, "Retiro", null))
        );
    }

    @Test
    void registrar_salidaCantidadExacta_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        try (MockedConstruction<ProductoRepository> pr = repoConProducto();
             MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.addMovimientoAtomic(any(), any())).thenReturn(esperado))) {
            MovimientoService svc = new MovimientoService();
            Movimiento resultado = svc.registrar("p-01", TipoMovimiento.SALIDA, 18, "Retiro total", null);
            assertSame(esperado, resultado);
        }
    }

    @Test
    void registrar_ajusteCantidadCero_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.AJUSTE, 0, "Corrección", null))
        );
    }

    // ── Validaciones de transferencia ─────────────────────────────────────────

    @Test
    void registrar_transferenciaSinAreaDestino_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 5, "Traspaso", null, null))
        );
    }

    @Test
    void registrar_transferenciaAreaDestinoBlanca_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 5, "Traspaso", null, "   "))
        );
    }

    @Test
    void registrar_transferenciaMismaArea_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 1, "Traspaso",
                    null, "Secretaria General Municipal"))  // área = área del producto
        );
    }

    // ── Validación de acceso por área ─────────────────────────────────────────

    @Test
    void registrar_usuarioSinAccesoAlArea_lanzaValidation() {
        SessionManager.setCurrentUser(director); // área = "Area Sin Acceso"
        withMockedRepos(repoConProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("p-01", TipoMovimiento.ENTRADA, 5, "test", null))
        );
    }

    @Test
    void registrar_productoNoExiste_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        withMockedRepos(repoSinProducto(), repoMovimiento(), svc ->
            assertThrows(MovimientoService.ValidationException.class,
                () -> svc.registrar("no-existe", TipoMovimiento.ENTRADA, 5, "test", null))
        );
    }

    // ── Éxito ─────────────────────────────────────────────────────────────────

    @Test
    void registrar_entrada_exitosa_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        try (MockedConstruction<ProductoRepository> pr = repoConProducto();
             MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.addMovimientoAtomic(any(), any())).thenReturn(esperado))) {
            Movimiento resultado = new MovimientoService().registrar("p-01", TipoMovimiento.ENTRADA, 5, "Compra", "REF-01");
            assertSame(esperado, resultado);
        }
    }

    @Test
    void registrar_ajuste_exitoso_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        try (MockedConstruction<ProductoRepository> pr = repoConProducto();
             MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.addMovimientoAtomic(any(), any())).thenReturn(esperado))) {
            Movimiento resultado = new MovimientoService().registrar("p-01", TipoMovimiento.AJUSTE, 20, "Conteo físico", null);
            assertSame(esperado, resultado);
        }
    }

    // ── eliminar() ───────────────────────────────────────────────────────────

    @Test
    void eliminar_admin_llama_deleteAtomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01")));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(producto)))) {
            new MovimientoService().eliminar("mov-01");
            verify(mr.constructed().get(0)).deleteMovimientoAtomic("mov-01");
        }
    }

    @Test
    void eliminar_movimientoNoExiste_noLanzaExcepcion() {
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findProductoIdById(any())).thenReturn(Optional.empty()));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class)) {
            assertDoesNotThrow(() -> new MovimientoService().eliminar("no-existe"));
        }
    }

    @Test
    void eliminar_noAdmin_areaInaccesible_lanzaValidation() {
        SessionManager.setCurrentUser(director); // área = "Area Sin Acceso"
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01")));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(producto)))) {
            assertThrows(MovimientoService.ValidationException.class,
                () -> new MovimientoService().eliminar("mov-01"));
        }
    }

    @Test
    void eliminar_noAdmin_areaAccesible_llama_deleteAtomic() throws Exception {
        Producto pPropio = new Producto();
        pPropio.setId("p-01");
        pPropio.setArea("Area Sin Acceso"); // misma área que director
        SessionManager.setCurrentUser(director);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01")));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(pPropio)))) {
            new MovimientoService().eliminar("mov-01");
            verify(mr.constructed().get(0)).deleteMovimientoAtomic("mov-01");
        }
    }

    @Test
    void eliminar_noAdmin_productoHuerfano_lanzaValidation() {
        // El movimiento existe pero su producto fue eliminado — no-admin bloqueado.
        SessionManager.setCurrentUser(director);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findProductoIdById("mov-01")).thenReturn(Optional.of("p-deleted")));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-deleted")).thenReturn(Optional.empty()))) {
            assertThrows(MovimientoService.ValidationException.class,
                () -> new MovimientoService().eliminar("mov-01"));
        }
    }

    @Test
    void eliminar_admin_productoHuerfano_permitido() throws Exception {
        // Admin puede eliminar movimientos huérfanos.
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findProductoIdById("mov-01")).thenReturn(Optional.of("p-deleted")));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-deleted")).thenReturn(Optional.empty()))) {
            assertDoesNotThrow(() -> new MovimientoService().eliminar("mov-01"));
            verify(mr.constructed().get(0)).deleteMovimientoAtomic("mov-01");
        }
    }

    // ── eliminar() — bloqueo de PENDIENTE ────────────────────────────────────

    @Test
    void eliminar_movimientoPendiente_throwsValidation() {
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findEstadoById("mov-pend"))
                    .thenReturn(Optional.of(com.sibim.model.Movimiento.ESTADO_PENDIENTE)));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class)) {
            var ex = assertThrows(MovimientoService.ValidationException.class,
                () -> new MovimientoService().eliminar("mov-pend"));
            assertTrue(ex.getMessage().toLowerCase().contains("pendiente"),
                "El mensaje debe mencionar 'pendiente'");
        }
    }

    @Test
    void eliminar_movimientoPendiente_noLlamaDeleteAtomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> when(m.findEstadoById(any()))
                    .thenReturn(Optional.of(com.sibim.model.Movimiento.ESTADO_PENDIENTE)));
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class)) {
            assertThrows(MovimientoService.ValidationException.class,
                () -> new MovimientoService().eliminar("mov-pend"));
            verify(mr.constructed().get(0), never()).deleteMovimientoAtomic(any());
        }
    }

    @Test
    void eliminar_movimientoAprobado_procedeNormal() throws Exception {
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> {
                    when(m.findEstadoById("mov-ok"))
                        .thenReturn(Optional.of(com.sibim.model.Movimiento.ESTADO_APROBADO));
                    when(m.findProductoIdById("mov-ok")).thenReturn(Optional.of("p-01"));
                });
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(producto)))) {
            assertDoesNotThrow(() -> new MovimientoService().eliminar("mov-ok"));
            verify(mr.constructed().get(0)).deleteMovimientoAtomic("mov-ok");
        }
    }

    @Test
    void eliminar_estadoVacio_procedeNormal() throws Exception {
        // findEstadoById vacío = movimiento no existe o sin estado → no bloquear
        SessionManager.setCurrentUser(admin);
        try (MockedConstruction<MovimientoRepository> mr = mockConstruction(MovimientoRepository.class,
                (m, c) -> {
                    when(m.findEstadoById(any())).thenReturn(Optional.empty());
                    when(m.findProductoIdById("mov-x")).thenReturn(Optional.of("p-01"));
                });
             MockedConstruction<ProductoRepository> pr = mockConstruction(ProductoRepository.class,
                (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(producto)))) {
            assertDoesNotThrow(() -> new MovimientoService().eliminar("mov-x"));
            verify(mr.constructed().get(0)).deleteMovimientoAtomic("mov-x");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private MockedConstruction<ProductoRepository> repoConProducto() {
        return mockConstruction(ProductoRepository.class,
            (m, c) -> when(m.findById("p-01")).thenReturn(Optional.of(producto)));
    }

    private MockedConstruction<ProductoRepository> repoSinProducto() {
        return mockConstruction(ProductoRepository.class,
            (m, c) -> when(m.findById(anyString())).thenReturn(Optional.empty()));
    }

    private MockedConstruction<MovimientoRepository> repoMovimiento() {
        return mockConstruction(MovimientoRepository.class);
    }

    @FunctionalInterface
    interface ServiceConsumer {
        void accept(MovimientoService svc) throws Exception;
    }

    private void withMockedRepos(MockedConstruction<ProductoRepository> pr,
                                  MockedConstruction<MovimientoRepository> mr,
                                  ServiceConsumer consumer) {
        try (pr; mr) {
            consumer.accept(new MovimientoService());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
