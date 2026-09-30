package com.sibim.service;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.repository.AreaRepository;
import com.sibim.repository.AuditLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AreaServiceTest {

    /** In-memory stand-in for the areas table. */
    static class RepoFalso extends AreaRepository {
        AreaCatalog catalogo = AreaCatalog.PREDETERMINADO;
        String firma = "44|t0";
        int cargas;
        boolean falla;

        @Override public AreaCatalog cargar() throws SQLException {
            if (falla) throw new SQLException("sin conexión");
            cargas++;
            return catalogo;
        }
        @Override public String firma() throws SQLException {
            if (falla) throw new SQLException("sin conexión");
            return firma;
        }
    }

    private final RepoFalso repo = new RepoFalso();
    private final AreaService service = new AreaService(repo, new AuditLogRepository() {
        @Override public void log(String e, String id, String n, String a, String d) { }
    });

    @BeforeEach
    void online() {
        DatabaseConfig.setDemoMode(false);
        DatabaseConfig.setOfflineMode(false);
        Areas.usar(AreaCatalog.PREDETERMINADO);
    }

    @AfterEach
    void restaurar() {
        DatabaseConfig.setOfflineMode(false);
        Areas.usar(AreaCatalog.PREDETERMINADO);
    }

    @Test
    void refrescar_sinCambios_noVuelveACargar() {
        service.cargarCatalogo();
        int cargas = repo.cargas;
        assertFalse(service.refrescarSiCambio());
        assertEquals(cargas, repo.cargas, "misma firma: una sola consulta ligera, sin recargar");
    }

    @Test
    void refrescar_conCambioEnOtraPC_usaElCatalogoNuevo() {
        service.cargarCatalogo();
        List<AreaCatalog.Entrada> l = new ArrayList<>(AreaCatalog.PREDETERMINADO.entradas());
        l.add(new AreaCatalog.Entrada("Dirección Nueva", AreaCatalog.Grupo.DIRECCION,
            "Secretaría de Planeación", "DNUE"));
        repo.catalogo = new AreaCatalog(l);
        repo.firma = "45|t1";

        assertTrue(service.refrescarSiCambio());
        assertTrue(Areas.catalogo().buscar("Dirección Nueva").isPresent());
        assertFalse(service.refrescarSiCambio(), "ya está al día");
    }

    @Test
    void refrescar_offlineOBaseCaida_noHaceNadaNiLanza() {
        DatabaseConfig.setOfflineMode(true);
        assertFalse(service.refrescarSiCambio());
        DatabaseConfig.setOfflineMode(false);
        repo.falla = true;
        assertFalse(service.refrescarSiCambio());
        assertSame(AreaCatalog.PREDETERMINADO, Areas.catalogo());
    }
}
