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

    /** A bien with nothing to transfer is reported before its resguardo is touched. */
    @Test
    void sinExistencia_noTocaSuResguardo() throws Exception {
        Producto p = bien("p-0", "PARQ/03");
        p.setStockActual(0);
        when(resguardos.getByProductoId("p-0")).thenReturn(List.of(vigente()));

        var r = service.transferir(List.of(p), "Comunicación Social", "reasignación", null, null);

        assertEquals(List.of("PARQ/03 Silla PARQ/03: " + MovimientoService.SIN_EXISTENCIA), r.errores());
        verify(resguardos, never()).quitarBien(any(), any(), any());
        verifyNoInteractions(movimientos);
    }

    /** If the transfer fails once the bien left its resguardo, the message says so. */
    @Test
    void siFallaDespuesDeQuitarloDelResguardo_loDice() throws Exception {
        Producto p = bien("p-1", "PARQ/12");
        when(resguardos.getByProductoId("p-1")).thenReturn(List.of(vigente()));
        when(movimientos.registrar(any(), any(), anyInt(), any(), any(), any()))
            .thenThrow(new java.sql.SQLException("Sin conexión"));

        var r = service.transferir(List.of(p), "Comunicación Social", "reasignación", null, null);

        assertEquals(1, r.errores().size());
        assertTrue(r.errores().get(0).contains("ya había salido del resguardo RSG-2026-000050"), r.errores().get(0));
        assertTrue(r.errores().get(0).contains("sigue en Parque Municipal"));
    }

    private static Movimiento hecho(String id, String productoId, String antes, String despues) {
        Movimiento m = movidoA(despues);
        m.setId(id); m.setProductoId(productoId); m.setProductoNombre("Silla " + antes);
        m.setCodigoAnterior(antes); m.setAreaOrigen("Parque Municipal"); m.setAreaDestino("Comunicación Social");
        return m;
    }

    /** Deshacer: the resguardo made for the batch goes first, then each transfer, and
     *  each bien returns to the resguardo it had left — reopened if it had emptied it. */
    @Test
    void deshacer_cancelaElResguardoNuevo_regresaLosNumeros_yLosRegresaASuResguardo() throws Exception {
        Resguardo nuevo = new Resguardo();
        nuevo.setId("r-nuevo"); nuevo.setNumero("RSG-2026-000060");
        ResguardoItem renglon = new ResguardoItem();
        renglon.setProductoId("p-1"); renglon.setProductoCodigo("PARQ/12");
        Movimiento m1 = hecho("m-1", "p-1", "PARQ/12", "COMS/07");
        Movimiento m2 = hecho("m-2", "p-2", "PARQ/13", "COMS/08");
        var lote = new TransferenciaRapidaService.Resultado(List.of(m1, m2), List.of(), nuevo,
            java.util.Map.of("p-1", new TransferenciaRapidaService.Salida(vigente(), renglon, true)));
        when(movimientos.deshacerTransferencia(m1)).thenReturn("PARQ/12");
        when(movimientos.deshacerTransferencia(m2)).thenReturn("PARQ/13");

        var d = service.deshacer(lote);

        var orden = inOrder(resguardos, movimientos);
        orden.verify(resguardos).cancelar("r-nuevo");
        orden.verify(movimientos).deshacerTransferencia(m1);
        orden.verify(resguardos).regresarBien(argThat(r -> "r-1".equals(r.getId())),
            argThat(it -> "PARQ/12".equals(it.getProductoCodigo()) && "Parque Municipal".equals(it.getArea())),
            eq("se deshizo su transferencia a Comunicación Social"), eq(true));
        orden.verify(movimientos).deshacerTransferencia(m2);
        verify(resguardos, times(1)).regresarBien(any(), any(), any(), anyBoolean());
        assertEquals(List.of("COMS/07 → PARQ/12", "COMS/08 → PARQ/13"), d.regresados());
        assertTrue(d.errores().isEmpty());
    }

    /** One of them lent since: nothing is touched, and it says why. */
    @Test
    void deshacer_siUnoYaNoPuedeRegresar_noSeDeshaceNada() throws Exception {
        Movimiento m1 = hecho("m-1", "p-prestado", "PARQ/12", "COMS/07");
        Movimiento m2 = hecho("m-2", "p-2", "PARQ/13", "COMS/08");
        Resguardo nuevo = new Resguardo();
        nuevo.setId("r-nuevo"); nuevo.setNumero("RSG-2026-000060");

        var d = service.deshacer(new TransferenciaRapidaService.Resultado(List.of(m1, m2), List.of(), nuevo));

        assertTrue(d.regresados().isEmpty());
        assertEquals(List.of("COMS/07 Silla PARQ/12: tiene un préstamo activo", "No se deshizo nada."), d.errores());
        verify(resguardos, never()).cancelar(any());
        verify(movimientos, never()).deshacerTransferencia(any());
    }

    /** Moved again after this transfer: only the latest movement can be taken back. */
    @Test
    void deshacer_conOtroMovimientoDespues_noSeDeshace() throws Exception {
        Movimiento m1 = hecho("m-1", "p-1", "PARQ/12", "COMS/07");
        Movimiento despues = new Movimiento();
        despues.setId("m-5"); despues.setEstado(Movimiento.ESTADO_APROBADO);
        when(movimientos.getByProducto("p-1")).thenReturn(List.of(despues, m1));

        var d = service.deshacer(new TransferenciaRapidaService.Resultado(List.of(m1), List.of(), null));

        assertTrue(d.errores().get(0).contains("tuvo otro movimiento después"), d.errores().get(0));
        verify(movimientos, never()).deshacerTransferencia(any());
    }
}
