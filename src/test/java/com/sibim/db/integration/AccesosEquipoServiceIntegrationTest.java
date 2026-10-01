package com.sibim.db.integration;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.service.AccesosEquipoService;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Each online sign-in is noted per PC; the administrator sees who is missing. */
@org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
class AccesosEquipoServiceIntegrationTest extends IntegrationTestBase {

    @AfterEach
    void salir() { SessionManager.logout(); }

    @Test
    void anotaElEquipoYSoloElAdministradorLoConsulta() throws Exception {
        var servicio = new AccesosEquipoService();
        SessionManager.logout();
        assertThrows(SecurityException.class, servicio::porCuenta);

        Usuario admin = new Usuario();
        admin.setId("test-admin");
        admin.setNombre("Admin Test");
        admin.setRol(Rol.ADMIN);
        SessionManager.setCurrentUser(admin);

        var antes = servicio.porCuenta();
        assertEquals(1, antes.size());
        assertTrue(antes.get(0).equipos().isEmpty(), "nadie ha entrado todavía");

        servicio.registrar("test-admin");
        servicio.registrar("test-admin");   // el mismo equipo no se duplica
        var despues = servicio.porCuenta();
        assertEquals(1, despues.get(0).equipos().size());
        assertEquals(AccesosEquipoService.esteEquipo(), despues.get(0).equipos().get(0).nombre());
        assertNotNull(despues.get(0).equipos().get(0).ultimoAcceso());
    }
}
