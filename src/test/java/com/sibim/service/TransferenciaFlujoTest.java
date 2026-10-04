package com.sibim.service;

import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A transfer: Patrimonio registers it and the área that receives the bien
 *  confirms it has it. The áreas register no movement of any kind and no longer
 *  request transfers (what they need is asked of Finanzas, outside SIBIM). */
class TransferenciaFlujoTest {

    private final MovimientoService service = new MovimientoService();
    private Producto bien;
    private String destino;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        bien = DemoDataStore.findAllProductos(null).stream()
            .filter(p -> !p.isDadoDeBaja() && p.getStockActual() > 0 && p.getArea() != null
                && Areas.getAllAreaNames().contains(p.getArea()))
            .findFirst().orElseThrow();
        destino = Areas.getAllAreaNames().stream().filter(a -> !a.equals(bien.getArea())).findFirst().orElseThrow();
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
        DemoDataStore.reiniciar();
        DatabaseConfig.setDemoMode(false);
    }

    @Test
    void flujoCompleto_patrimonioTransfiereYElAreaRecibe() throws Exception {
        String origen = bien.getArea();
        entrarComo(Rol.ADMIN, null);
        Movimiento transferencia = service.registrar(bien.getId(), TipoMovimiento.TRANSFERENCIA,
            bien.getStockActual(), "Reasignación", null, destino);
        assertFalse(transferencia.isPendiente(), "la de Patrimonio no espera aprobación");
        assertEquals(destino, DemoDataStore.findProductoById(bien.getId()).orElseThrow().getArea());

        entrarComo(Rol.DIRECCION, origen);   // el área que entregó no puede confirmar
        assertTrue(service.getPorRecibir().isEmpty());
        Movimiento porRecibir = DemoDataStore.findPorRecibir(null).stream()
            .filter(m -> m.getId().equals(transferencia.getId())).findFirst().orElseThrow();
        assertThrows(MovimientoService.ValidationException.class, () -> service.confirmarRecepcion(porRecibir));

        entrarComo(Rol.DIRECCION, destino);
        assertEquals(1, service.getPorRecibir().size(), "al área que recibe le aparece por recibir");
        service.confirmarRecepcion(service.getPorRecibir().get(0));
        assertTrue(service.getPorRecibir().isEmpty());
        assertNotNull(porRecibir.getRecibidoEn());
        assertEquals("Usuario " + destino, porRecibir.getRecibidoPor());
        assertThrows(MovimientoService.ValidationException.class, () -> service.confirmarRecepcion(porRecibir),
            "no se confirma dos veces");
    }

    @Test
    void transferenciaDePatrimonio_quedaPorRecibirParaElSecretarioDelArea() throws Exception {
        entrarComo(Rol.ADMIN, null);
        service.registrar(bien.getId(), TipoMovimiento.TRANSFERENCIA, bien.getStockActual(), "Directa", null, destino);
        entrarComo(Rol.SECRETARIO, destino);
        assertEquals(1, service.getPorRecibir().size());
    }

    /** Neither a secretario nor a dirección registers a movement, and a transfer
     *  is no longer something they can request: it used to be saved as pending. */
    @Test
    void lasAreas_noRegistranNingunMovimiento_niSolicitanTransferencias() throws Exception {
        for (Rol rol : new Rol[]{ Rol.SECRETARIO, Rol.DIRECCION }) {
            entrarComo(rol, bien.getArea());
            for (TipoMovimiento t : new TipoMovimiento[]{ TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA, TipoMovimiento.AJUSTE }) {
                assertThrows(MovimientoService.ValidationException.class,
                    () -> service.registrar(bien.getId(), t, 1, "x", null), rol + ": " + t + " es solo de Patrimonio");
            }
            MovimientoService.ValidationException e = assertThrows(MovimientoService.ValidationException.class,
                () -> service.registrar(bien.getId(), TipoMovimiento.TRANSFERENCIA, bien.getStockActual(), "x", null, destino),
                rol + ": tampoco una transferencia");
            assertEquals(MovimientoService.SOLO_ADMIN_MOVIMIENTOS, e.getMessage());
        }
        assertEquals(bien.getArea(), DemoDataStore.findProductoById(bien.getId()).orElseThrow().getArea(),
            "el bien sigue donde estaba");
        entrarComo(Rol.ADMIN, null);
        assertTrue(service.getPendientesTransferencias().isEmpty(), "ninguna solicitud quedó en espera");
    }

    private static void entrarComo(Rol rol, String area) {
        Usuario u = new Usuario();
        u.setId("u-" + rol + "-" + area);
        u.setUsername("prueba");
        u.setNombre("Usuario " + (area != null ? area : "Patrimonio"));
        u.setRol(rol);
        u.setArea(area);
        u.setActivo(true);
        SessionManager.setCurrentUser(u);
    }
}
