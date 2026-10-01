package com.sibim.db.integration;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.ErroresEquipoService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Errors reported by the PCs are listed for the administrator; old ones expire. */
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class ErroresEquipoServiceIntegrationTest extends IntegrationTestBase {

    @AfterEach
    void salir() { SessionManager.logout(); }

    @Test
    void listaLosRecientesYBorraLosViejos() throws Exception {
        try (var c = DatabaseConfig.getConnection(); var st = c.createStatement()) {
            st.execute("DELETE FROM errores_equipo");
            st.execute("INSERT INTO errores_equipo (id, equipo, usuario, version, origen, mensaje) "
                + "VALUES ('e1', 'PC-CATASTRO', 'catastro', '1.0.0', 'com.sibim.X', 'Falló la exportación')");
            st.execute("INSERT INTO errores_equipo (id, equipo, mensaje, creado_en) "
                + "VALUES ('e2', 'PC-VIEJA', 'Antiguo', NOW() - INTERVAL '200 days')");
        }
        var servicio = new ErroresEquipoService();
        SessionManager.logout();
        assertThrows(SecurityException.class, () -> servicio.recientes(10));

        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Admin Test");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);

        var lista = servicio.recientes(10);
        assertEquals(1, lista.size(), "el de hace 200 días ya no se conserva");
        assertEquals("PC-CATASTRO", lista.get(0).equipo());
        assertEquals("1.0.0", lista.get(0).version());

        servicio.borrarTodos();
        assertTrue(servicio.recientes(10).isEmpty());
    }
}
