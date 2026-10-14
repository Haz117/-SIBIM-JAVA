package com.sibim.session;

import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A shared área account asks who uses it once per PC, and the person can
 *  change during the session without the names piling up in the records. */
class OperadorRecordadoTest {

    @AfterEach
    void salir() { SessionManager.logout(); }

    private static Usuario cuenta() {
        Usuario u = new Usuario();
        u.setId("cuenta-catastro-" + System.nanoTime());
        u.setNombre("Dirección de Catastro");
        u.setRol(Rol.DIRECCION);
        u.setArea("Dirección de Catastro");
        return u;
    }

    @Test
    void cambiarDeOperador_reemplaza_noAcumulaNombres() {
        Usuario u = cuenta();
        SessionManager.setCurrentUser(u);
        assertNull(SessionManager.getOperador());

        SessionManager.setOperador("Juan Pérez");
        assertEquals("Juan Pérez · Dirección de Catastro", u.getNombre());
        assertEquals("Juan Pérez", SessionManager.getOperador());

        SessionManager.setOperador("Ana López");
        assertEquals("Ana López · Dirección de Catastro", u.getNombre(), "no \"Ana · Juan · Catastro\"");
        assertEquals("Ana López", SessionManager.getOperador());
        assertEquals("Dirección de Catastro", SessionManager.getNombreCuenta());
    }

    @Test
    void elNombreSeRecuerdaPorCuenta() {
        Usuario u = cuenta();
        assertNull(OperadorRecordado.de(u.getId()), "la primera vez en esta PC se pregunta");
        OperadorRecordado.recordar(u.getId(), "  Juan Pérez ");
        assertEquals("Juan Pérez", OperadorRecordado.de(u.getId()));
        assertNull(OperadorRecordado.de(cuenta().getId()), "otra cuenta no lo hereda");
        OperadorRecordado.recordar(u.getId(), "J");
        assertEquals("Juan Pérez", OperadorRecordado.de(u.getId()), "un nombre de menos de 3 letras no se guarda");
    }
}
