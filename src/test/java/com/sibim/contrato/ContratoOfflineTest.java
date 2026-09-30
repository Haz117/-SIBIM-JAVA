package com.sibim.contrato;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ContratoInventario} against the offline SQLite store (surefire points
 *  user.home at target/test-home, so this never touches a real ~/.sibim). */
class ContratoOfflineTest implements ContratoInventario {

    @BeforeEach
    void offline() {
        DatabaseConfig.setOfflineMode(true);
        SessionManager.setCurrentUser(ContratoDemoTest.admin());
    }

    @AfterEach
    void salir() {
        SessionManager.logout();
        DatabaseConfig.setOfflineMode(false);
    }

    /** Offline the request is still recorded (and synced later), but resolving
     *  it needs the server: another PC may have moved the bien meanwhile. It
     *  must say so right away instead of timing out against PostgreSQL. */
    @Test
    @Override
    public void transferenciaDeNoAdmin_quedaPendienteHastaQueElAdminAprueba() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        Movimiento[] pendiente = new Movimiento[1];
        comoDireccion(AREA_A, () -> pendiente[0] = movimientos()
            .registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "contrato", null, AREA_B));
        assertTrue(pendiente[0].isPendiente());
        assertTrue(movimientos().getPendientesTransferencias().stream()
            .anyMatch(m -> m.getId().equals(pendiente[0].getId())));

        long inicio = System.nanoTime();
        MovimientoService.ValidationException e = assertThrows(MovimientoService.ValidationException.class,
            () -> movimientos().aprobarTransferencia(pendiente[0].getId()));
        assertTrue(e.getMessage().contains("requiere conexión"));
        assertTrue(System.nanoTime() - inicio < 2_000_000_000L, "responde de inmediato, sin esperar al servidor");
        assertEquals(AREA_A, releer(p.getId()).getArea());
    }

    @Test
    @Override
    public void transferenciaRechazada_noMueveElBien() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        Movimiento[] pendiente = new Movimiento[1];
        comoDireccion(AREA_A, () -> pendiente[0] = movimientos()
            .registrar(p.getId(), TipoMovimiento.TRANSFERENCIA, 1, "contrato", null, AREA_B));
        assertThrows(MovimientoService.ValidationException.class,
            () -> movimientos().rechazarTransferencia(pendiente[0].getId(), "no procede"));
        assertTrue(movimientos().getPendientesTransferencias().stream()
            .anyMatch(m -> m.getId().equals(pendiente[0].getId())), "sigue pendiente");
    }

    /** Préstamos/comodatos are only on the server, so offline a baja can't be
     *  checked against them: it is refused at once instead of being queued blind. */
    @Test
    @Override
    public void baja_sacaAlBienDelInventarioActivoYReactivarLoRegresa() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        ProductoService.ValidationException e = assertThrows(ProductoService.ValidationException.class,
            () -> productos().darDeBaja(p.getId(), "contrato"));
        assertTrue(e.getMessage().contains("requiere conexión"));
        assertFalse(releer(p.getId()).isDadoDeBaja());
    }

    @Test
    @Override
    public void movimiento_sobreBienDadoDeBaja_seRechaza() throws Exception {
        // Offline a baja can't be registered (see above), but one can arrive
        // from the server's copy: movements on it are still refused.
        Producto p = alta(AREA_A, 4, 0);
        com.sibim.db.offline.OfflineStore.darDeBajaProducto(p.getId(), "baja en el servidor");
        assertThrows(MovimientoService.ValidationException.class,
            () -> movimientos().registrar(p.getId(), TipoMovimiento.ENTRADA, 1, "contrato", null));
    }

    @Test
    void cambiarResguardante_sinConexion_seRechaza() throws Exception {
        Producto p = alta(AREA_A, 1, 0);
        Producto edit = releer(p.getId());
        edit.setResguardante("Otra Persona");
        assertThrows(ProductoService.ValidationException.class, () -> productos().save(edit));
    }
}
