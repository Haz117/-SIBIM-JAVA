package com.sibim.contrato;

import com.sibim.db.DatabaseConfig;
import com.sibim.db.DemoDataStore;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** {@link ContratoInventario} against the in-memory demo store. */
class ContratoDemoTest implements ContratoInventario {

    @BeforeEach
    void demo() {
        DatabaseConfig.setDemoMode(true);
        DemoDataStore.reiniciar();
        SessionManager.setCurrentUser(admin());
    }

    @AfterEach
    void salir() {
        SessionManager.logout();
        DatabaseConfig.setDemoMode(false);
        DemoDataStore.reiniciar();
    }

    static Usuario admin() {
        Usuario u = new Usuario();
        u.setId("contrato-admin");
        u.setUsername("contrato");
        u.setNombre("Admin Contrato");
        u.setRol(Rol.ADMIN);
        return u;
    }
}
