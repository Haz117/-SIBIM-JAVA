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

    /** Quitar every piece would leave a bien of 0 worth $0 that can't move: that is a baja. */
    @Test void registrar_quitarTodasLasPiezas_esUnaBaja() throws Exception {
        SessionManager.setCurrentUser(admin);
        assertEquals(MovimientoService.QUITAR_TODO,
            mensaje(() -> service.registrar("p-01", TipoMovimiento.SALIDA, 18, "Retiro total", null)));
        verify(mockMovimientoRepo, never()).addMovimientoAtomic(any(), any());
    }

    /** "Revertir" an entrada made by mistake takes the bien back to the 0 it had:
     *  that is not "Quitar", so the rule above does not stop it. */
    @Test void revertirUnaEntrada_puedeVolverACero() throws Exception {
        SessionManager.setCurrentUser(admin);
        producto.setStockActual(3);
        Movimiento entrada = new Movimiento();
        entrada.setId("m-entrada-01"); entrada.setProductoId("p-01");
        entrada.setTipo(TipoMovimiento.ENTRADA); entrada.setCantidad(3);
        service.revertirMovimiento(entrada, "capturada por error");
        verify(mockMovimientoRepo).addMovimientoAtomic(argThat(m -> m.getTipo() == TipoMovimiento.SALIDA
            && m.getCantidad() == 3 && m.getStockNuevo() == 0), any());
    }

    @Test void registrar_quitarAlgunas_llama_atomic() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento esperado = new Movimiento();
        when(mockMovimientoRepo.addMovimientoAtomic(any(), any())).thenReturn(esperado);
        assertSame(esperado, service.registrar("p-01", TipoMovimiento.SALIDA, 17, "Se rompió una", null));
    }

    @Test void registrar_ajusteNegativo_lanzaValidation() {
        SessionManager.setCurrentUser(admin);
        assertThrows(MovimientoService.ValidationException.class,
            () -> service.registrar("p-01", TipoMovimiento.AJUSTE, -1, "Corrección", null));
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

    @Test void eliminar_noAdmin_areaAccesible_igualSeRechaza() throws Exception {
        // Borrar un movimiento lo quita del historial: solo el admin puede;
        // los demás usan revertirMovimiento, que deja ambos registros.
        Producto pPropio = new Producto(); pPropio.setId("p-01"); pPropio.setArea("Area Sin Acceso");
        when(mockProductoRepo.findById("p-01")).thenReturn(Optional.of(pPropio));
        when(mockMovimientoRepo.findProductoIdById("mov-01")).thenReturn(Optional.of("p-01"));
        SessionManager.setCurrentUser(director);
        var ex = assertThrows(MovimientoService.ValidationException.class, () -> service.eliminar("mov-01"));
        assertTrue(ex.getMessage().contains("Revertir"));
        verify(mockMovimientoRepo, never()).deleteMovimientoAtomic(any());
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

    // ── Reglas agregadas el 2026-09-26 ────────────────────────────────────────

    private static final String DESTINO = "Dirección de Tecnologías de la Información";

    private String mensaje(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(MovimientoService.ValidationException.class, e).getMessage();
    }

    @Test void registrar_sobreBienDadoDeBaja_seRechaza() {
        SessionManager.setCurrentUser(admin);
        producto.setFechaBaja(java.time.LocalDate.now());
        assertEquals(MovimientoService.BIEN_DE_BAJA,
            mensaje(() -> service.registrar("p-01", TipoMovimiento.ENTRADA, 1, "x", null)));
    }

    @Test void transferencia_deBienSinExistencia_seRechaza() {
        SessionManager.setCurrentUser(admin);
        producto.setStockActual(0);
        assertEquals(MovimientoService.SIN_EXISTENCIA,
            mensaje(() -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 1, "x", null, DESTINO)));
        // Transferir arrastrando sends the bien's own existencia, 0 here.
        assertEquals(MovimientoService.SIN_EXISTENCIA,
            mensaje(() -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 0, "x", null, DESTINO)));
    }

    @Test void transferencia_conCantidadDistintaALaExistencia_seRechaza() {
        SessionManager.setCurrentUser(admin);
        assertTrue(mensaje(() -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 5, "x", null, DESTINO))
            .contains("bien completo"));
    }

    @Test void transferencia_aUnAreaQueNoExiste_seRechaza() {
        SessionManager.setCurrentUser(admin);
        assertTrue(mensaje(() -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 18, "x", null, "Área inventada"))
            .contains("no es un área"));
    }

    @Test void transferencia_conOtraPendiente_seRechaza() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento pendiente = new Movimiento();
        pendiente.setProductoId("p-01");
        when(mockMovimientoRepo.findPendientesTransferencias()).thenReturn(java.util.List.of(pendiente));
        assertTrue(mensaje(() -> service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 18, "x", null, DESTINO))
            .contains("pendiente"));
    }

    @Test void transferencia_valida_seRegistra() throws Exception {
        SessionManager.setCurrentUser(admin);
        when(mockMovimientoRepo.findPendientesTransferencias()).thenReturn(java.util.List.of());
        service.registrar("p-01", TipoMovimiento.TRANSFERENCIA, 18, "Reasignación", null, DESTINO);
        verify(mockMovimientoRepo).addMovimientoAtomic(any(), any());
    }

    @Test void ajuste_alMismoValor_seRechaza() {
        SessionManager.setCurrentUser(admin);
        assertTrue(mensaje(() -> service.registrar("p-01", TipoMovimiento.AJUSTE, 18, "x", null))
            .contains("no cambia"));
    }

    /** Auditoría tells what a movement did: a transfer read "1 uds (cantidad 1 → 1)". */
    @Test void auditoria_diceAreasNumeroYMotivo() {
        Movimiento t = new Movimiento();
        t.setTipo(TipoMovimiento.TRANSFERENCIA); t.setCantidad(1); t.setStockAnterior(1); t.setStockNuevo(1);
        t.setAreaOrigen("Contraloría Municipal"); t.setAreaDestino("Instancia Municipal de la Mujer");
        t.setCodigoAnterior("CONT/49"); t.setCodigoNuevo("IMM/41"); t.setMotivo("Reasignación ");
        assertEquals("Transferencia: Contraloría Municipal → Instancia Municipal de la Mujer · número CONT/49 → IMM/41"
            + " · Motivo: Reasignación", MovimientoService.detalleAuditoria(t, "CONT/49", "Contraloría Municipal"));

        Movimiento q = new Movimiento();
        q.setTipo(TipoMovimiento.SALIDA); q.setCantidad(2); q.setStockAnterior(20); q.setStockNuevo(18);
        q.setMotivo("Se rompieron"); q.setReferencia("Oficio 12");
        assertEquals("Quitar 2 piezas de SGM/07 (cantidad 20 → 18) · Motivo: Se rompieron · Referencia: Oficio 12",
            MovimientoService.detalleAuditoria(q, "SGM/07", "Secretaría General Municipal"));

        Movimiento a = new Movimiento();
        a.setTipo(TipoMovimiento.ENTRADA); a.setCantidad(1); a.setStockAnterior(18); a.setStockNuevo(19);
        assertEquals("Agregar 1 pieza a SGM/07 (cantidad 18 → 19)",
            MovimientoService.detalleAuditoria(a, "SGM/07", "Secretaría General Municipal"));
    }

    /** Deshacer (Transferir arrastrando): the transfer goes, the bien is back with its
     *  número, and Auditoría says what was undone. */
    @Test void deshacerTransferencia_borraElMovimientoYLoDejaEnAuditoria() throws Exception {
        SessionManager.setCurrentUser(admin);
        Movimiento t = new Movimiento();
        t.setId("m-9"); t.setProductoId("p-01"); t.setProductoNombre("Escritorio ejecutivo");
        t.setTipo(TipoMovimiento.TRANSFERENCIA); t.setMotivo("Reasignación");
        t.setAreaOrigen("Secretaria General Municipal"); t.setAreaDestino("Instancia Municipal de la Mujer");
        t.setCodigoAnterior("SGM/07"); t.setCodigoNuevo("IMM/41");
        producto.setCodigo("SGM/07");

        assertEquals("SGM/07", service.deshacerTransferencia(t));
        verify(mockMovimientoRepo).deleteMovimientoAtomic("m-9");
        verify(mockAuditRepo).log("movimiento", "m-9", "Escritorio ejecutivo", "transferencia_deshecha",
            "Se deshizo la transferencia Secretaria General Municipal → Instancia Municipal de la Mujer"
                + " · número IMM/41 → SGM/07 · Motivo de la transferencia: Reasignación");

        Movimiento entrada = new Movimiento();
        entrada.setTipo(TipoMovimiento.ENTRADA);
        assertThrows(MovimientoService.ValidationException.class, () -> service.deshacerTransferencia(entrada));
        SessionManager.setCurrentUser(director);
        assertThrows(MovimientoService.ValidationException.class, () -> service.deshacerTransferencia(t));
    }

    /** Auditoría of a deleted movement says what it was, not "bien 3f2a…, estado APROBADO". */
    @Test void eliminar_dejaEnAuditoriaQueMovimientoEra() throws Exception {
        SessionManager.setCurrentUser(admin);
        producto.setCodigo("SGM/07");
        Movimiento e = new Movimiento();
        e.setId("m-3"); e.setProductoId("p-01"); e.setProductoNombre("Escritorio ejecutivo");
        e.setTipo(TipoMovimiento.ENTRADA); e.setCantidad(2); e.setStockAnterior(16); e.setStockNuevo(18);
        e.setMotivo("Compra");
        when(mockMovimientoRepo.findEstadoById("m-3")).thenReturn(Optional.of(Movimiento.ESTADO_APROBADO));
        when(mockMovimientoRepo.findProductoIdById("m-3")).thenReturn(Optional.of("p-01"));
        when(mockMovimientoRepo.findByProducto("p-01")).thenReturn(java.util.List.of(e));

        service.eliminar("m-3");

        verify(mockAuditRepo).log("movimiento", "m-3", "Escritorio ejecutivo", "eliminar",
            "Movimiento eliminado: Agregar 2 piezas a SGM/07 (cantidad 16 → 18) · Motivo: Compra");
    }

    /** Taking a transfer back moves the bien: not while a document in the área that
     *  received it says who has it there. */
    @Test void eliminarODeshacerUnaTransferencia_conResguardoVigente_seRechaza() throws Exception {
        SessionManager.setCurrentUser(admin);
        MovimientoService conResguardo = new MovimientoService(mockMovimientoRepo, mockProductoRepo, mockAuditRepo,
            id -> Optional.of("está en el resguardo vigente RSG-2026-000070"));
        Movimiento t = new Movimiento();
        t.setId("m-4"); t.setProductoId("p-01"); t.setTipo(TipoMovimiento.TRANSFERENCIA);
        when(mockMovimientoRepo.findEstadoById("m-4")).thenReturn(Optional.of(Movimiento.ESTADO_APROBADO));
        when(mockMovimientoRepo.findProductoIdById("m-4")).thenReturn(Optional.of("p-01"));
        when(mockMovimientoRepo.findByProducto("p-01")).thenReturn(java.util.List.of(t));

        assertTrue(mensaje(() -> conResguardo.eliminar("m-4")).contains("RSG-2026-000070"));
        assertTrue(mensaje(() -> conResguardo.deshacerTransferencia(t)).contains("RSG-2026-000070"));
        verify(mockMovimientoRepo, never()).deleteMovimientoAtomic(any());
    }
}
