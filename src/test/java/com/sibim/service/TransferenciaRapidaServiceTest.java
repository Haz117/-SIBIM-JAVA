package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.model.ResguardoItem;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** "Transferir arrastrando": each bien leaves its resguardo, is transferred, and the
 *  ones that moved go into one resguardo of whoever receives them. */
class TransferenciaRapidaServiceTest {

    private final MovimientoService movimientos = mock(MovimientoService.class);
    private final ResguardoService resguardos = mock(ResguardoService.class);
    private Optional<String> prestado = Optional.empty();
    private TransferenciaRapidaService service;

    @BeforeEach
    void setUp() {
        entrarComo(Rol.ADMIN);
        service = new TransferenciaRapidaService(movimientos, resguardos,
            id -> "p-prestado".equals(id) ? Optional.of("tiene un préstamo activo") : prestado);
    }

    @AfterEach
    void salir() { SessionManager.logout(); }

    private static void entrarComo(Rol rol) {
        Usuario u = new Usuario();
        u.setId("u-1"); u.setNombre("Patrimonio"); u.setRol(rol);
        SessionManager.setCurrentUser(u);
    }

    private static Producto bien(String id, String codigo) {
        Producto p = new Producto();
        p.setId(id); p.setCodigo(codigo); p.setNombre("Silla " + codigo); p.setArea("Parque Municipal");
        p.setStockActual(1); p.setPrecioCompra(new BigDecimal("900"));
        return p;
    }

    private static Movimiento movidoA(String codigoNuevo) {
        Movimiento m = new Movimiento();
        m.setTipo(TipoMovimiento.TRANSFERENCIA);
        m.setCodigoNuevo(codigoNuevo);
        return m;
    }

    private static Resguardo vigente() {
        Resguardo r = new Resguardo();
        r.setId("r-1"); r.setNumero("RSG-2026-000050"); r.setEstado(Resguardo.ESTADO_ACTIVO);
        return r;
    }

    @Test
    void conResguardo_loQuitaPrimero_loTransfiere_yLeCreaResguardoAlNuevo() throws Exception {
        Producto p = bien("p-1", "PARQ/12");
        when(resguardos.getByProductoId("p-1")).thenReturn(List.of(vigente()));
        when(movimientos.registrar("p-1", TipoMovimiento.TRANSFERENCIA, 1, "reasignación", null, "Comunicación Social"))
            .thenReturn(movidoA("COMS/07"));
        Resguardo nuevo = new Resguardo();
        when(resguardos.crear(eq("Ana López"), eq("Auxiliar"), eq("Comunicación Social"), anyList(), anyString())).thenReturn(nuevo);

        var r = service.transferir(List.of(p), "Comunicación Social", " reasignación ", "Ana López", "Auxiliar");

        var orden = inOrder(resguardos, movimientos);
        orden.verify(resguardos).quitarBien(any(), argThat(it -> "p-1".equals(it.getProductoId())),
            eq("transferencia a Comunicación Social: reasignación"));
        orden.verify(movimientos).registrar("p-1", TipoMovimiento.TRANSFERENCIA, 1, "reasignación", null, "Comunicación Social");
        @SuppressWarnings("unchecked") ArgumentCaptor<List<ResguardoItem>> items = ArgumentCaptor.forClass(List.class);
        orden.verify(resguardos).crear(eq("Ana López"), eq("Auxiliar"), eq("Comunicación Social"), items.capture(), anyString());
        assertEquals("COMS/07", items.getValue().get(0).getProductoCodigo(), "el resguardo nuevo lleva el código nuevo");
        assertSame(nuevo, r.resguardo());
        assertEquals(1, r.hechos().size());
        assertTrue(r.errores().isEmpty());
    }

    @Test
    void unBienPrestado_noSeTocaYLosDemasSiguen() throws Exception {
        when(resguardos.getByProductoId(anyString())).thenReturn(List.of());
        when(movimientos.registrar(eq("p-2"), any(), anyInt(), anyString(), any(), anyString())).thenReturn(movidoA("COMS/08"));

        var r = service.transferir(List.of(bien("p-prestado", "PARQ/01"), bien("p-2", "PARQ/02")),
            "Comunicación Social", "reasignación", null, null);

        verify(movimientos, never()).registrar(eq("p-prestado"), any(), anyInt(), anyString(), any(), anyString());
        verify(resguardos, never()).quitarBien(any(), any(), anyString());
        verify(resguardos, never()).crear(any(), any(), any(), anyList(), any());
        assertEquals(1, r.hechos().size());
        assertEquals(1, r.errores().size());
        assertTrue(r.errores().get(0).contains("PARQ/01") && r.errores().get(0).contains("préstamo"));
    }

    @Test
    void siFallaUnaTransferencia_elResguardoNuevoSoloLlevaLosQueSeMovieron() throws Exception {
        when(resguardos.getByProductoId(anyString())).thenReturn(List.of());
        when(movimientos.registrar(eq("p-1"), any(), anyInt(), anyString(), any(), anyString()))
            .thenThrow(new MovimientoService.ValidationException("Este bien ya tiene una transferencia pendiente de aprobación"));
        when(movimientos.registrar(eq("p-2"), any(), anyInt(), anyString(), any(), anyString())).thenReturn(movidoA("COMS/08"));

        var r = service.transferir(List.of(bien("p-1", "PARQ/01"), bien("p-2", "PARQ/02")),
            "Comunicación Social", "reasignación", "Ana López", "");

        @SuppressWarnings("unchecked") ArgumentCaptor<List<ResguardoItem>> items = ArgumentCaptor.forClass(List.class);
        verify(resguardos).crear(eq("Ana López"), any(), eq("Comunicación Social"), items.capture(), anyString());
        assertEquals(List.of("p-2"), items.getValue().stream().map(ResguardoItem::getProductoId).toList());
        assertEquals(1, r.errores().size());
    }

    @Test
    void soloPatrimonio_yConMotivo() {
        assertThrows(IllegalArgumentException.class,
            () -> service.transferir(List.of(bien("p-1", "PARQ/01")), "Comunicación Social", " ", null, null));
        entrarComo(Rol.DIRECCION);
        assertThrows(SecurityException.class,
            () -> service.transferir(List.of(bien("p-1", "PARQ/01")), "Comunicación Social", "x", null, null));
        verifyNoInteractions(movimientos, resguardos);
    }
}
