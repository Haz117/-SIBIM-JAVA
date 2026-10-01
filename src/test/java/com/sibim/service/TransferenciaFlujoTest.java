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

/** Área solicita → Patrimonio aprueba → el área que recibe confirma (demo mode, in memory). */
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
    void flujoCompleto_solicitaApruebaYRecibe() throws Exception {
        entrarComo(Rol.DIRECCION, bien.getArea());
        Movimiento solicitud = service.registrar(bien.getId(), TipoMovimiento.TRANSFERENCIA,
            bien.getStockActual(), "Reasignación", null, destino);
        assertTrue(solicitud.isPendiente(), "la solicitud de un área espera a Patrimonio");
        assertEquals(bien.getArea(), DemoDataStore.findProductoById(bien.getId()).orElseThrow().getArea(),
            "el bien no se mueve hasta que Patrimonio aprueba");
        assertEquals(1, service.getPendientesTransferencias().stream()
            .filter(m -> m.getId().equals(solicitud.getId())).count(), "el área ve su propia solicitud");
        assertThrows(SecurityException.class, () -> service.aprobarTransferencia(solicitud.getId()));

        entrarComo(Rol.ADMIN, null);
        service.aprobarTransferencia(solicitud.getId());
        assertEquals(destino, DemoDataStore.findProductoById(bien.getId()).orElseThrow().getArea());

        entrarComo(Rol.DIRECCION, bien.getArea());   // el área que entregó no puede confirmar
        assertTrue(service.getPorRecibir().isEmpty());
        Movimiento aprobada = DemoDataStore.findPorRecibir(null).stream()
            .filter(m -> m.getId().equals(solicitud.getId())).findFirst().orElseThrow();
        assertThrows(MovimientoService.ValidationException.class, () -> service.confirmarRecepcion(aprobada));

        entrarComo(Rol.DIRECCION, destino);
        assertEquals(1, service.getPorRecibir().size(), "al área que recibe le aparece por recibir");
        service.confirmarRecepcion(service.getPorRecibir().get(0));
        assertTrue(service.getPorRecibir().isEmpty());
        assertNotNull(aprobada.getRecibidoEn());
        assertEquals("Usuario " + destino, aprobada.getRecibidoPor());
        assertThrows(MovimientoService.ValidationException.class, () -> service.confirmarRecepcion(aprobada),
            "no se confirma dos veces");
    }

    @Test
    void transferenciaDirectaDePatrimonio_tambienQuedaPorRecibir() throws Exception {
        entrarComo(Rol.ADMIN, null);
        service.registrar(bien.getId(), TipoMovimiento.TRANSFERENCIA, bien.getStockActual(), "Directa", null, destino);
        entrarComo(Rol.SECRETARIO, destino);
        assertEquals(1, service.getPorRecibir().size());
    }

    @Test
    void unArea_soloSolicitaTransferencias() {
        entrarComo(Rol.DIRECCION, bien.getArea());
        for (TipoMovimiento t : new TipoMovimiento[]{ TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA, TipoMovimiento.AJUSTE }) {
            assertThrows(MovimientoService.ValidationException.class,
                () -> service.registrar(bien.getId(), t, 1, "x", null), t + " es solo de Patrimonio");
        }
    }

    @Test
    void unArea_noTransfiereBienesDeOtraArea() {
        entrarComo(Rol.DIRECCION, destino);
        assertThrows(MovimientoService.ValidationException.class, () -> service.registrar(bien.getId(),
            TipoMovimiento.TRANSFERENCIA, bien.getStockActual(), "x", null, destino.equals("Archivo Municipal")
                ? "Parque Municipal" : "Archivo Municipal"));
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
