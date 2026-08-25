package com.sibim.repository;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.AuditLog;
import com.sibim.model.ConteoFisico;
import com.sibim.model.Usuario;
import com.sibim.model.enums.Rol;
import com.sibim.session.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogRepositoryOfflineTest {

    @AfterEach
    void tearDown() {
        DatabaseConfig.setOfflineMode(false);
        DatabaseConfig.setDemoMode(false);
        SessionManager.logout();
    }

    @Test
    void findAll_readsOfflineAuditTrail_whenAppIsOffline() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        SessionManager.setCurrentUser(buildAdmin());

        AuditLogRepository repo = new AuditLogRepository();
        repo.log("producto", "prod-offline-test", "Laptop", "crear", "Creado desde prueba offline");

        List<AuditLog> entries = repo.findAll(10);

        assertFalse(entries.isEmpty());
        assertTrue(entries.stream().anyMatch(a ->
            "producto".equals(a.getEntidad())
                && "prod-offline-test".equals(a.getEntidadId())
                && "Laptop".equals(a.getEntidadNombre())
                && "crear".equals(a.getAccion())
        ));
    }

    @Test
    void guardarConteo_registraAuditoriaOffline() throws Exception {
        DatabaseConfig.setOfflineMode(true);
        SessionManager.setCurrentUser(buildAdmin());

        ConteoFisico conteo = new ConteoFisico();
        conteo.setUsuarioId("offline-admin");
        conteo.setUsuarioNombre("Admin Offline");
        conteo.setTotalContados(4);
        conteo.setTotalDiscrepancias(1);

        new ConteoRepository().guardar(conteo);

        assertTrue(new AuditLogRepository().findAll(50).stream().anyMatch(a ->
            "conteo".equals(a.getEntidad())
                && conteo.getId().equals(a.getEntidadId())
                && "crear".equals(a.getAccion())
        ));
    }

    private Usuario buildAdmin() {
        Usuario u = new Usuario();
        u.setId("offline-admin");
        u.setNombre("Admin Offline");
        u.setRol(Rol.ADMIN);
        return u;
    }
}
