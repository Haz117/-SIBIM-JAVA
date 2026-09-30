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
