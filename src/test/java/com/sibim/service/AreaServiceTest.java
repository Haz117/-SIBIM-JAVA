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

    private static com.sibim.model.Usuario admin() {
        com.sibim.model.Usuario u = new com.sibim.model.Usuario();
        u.setId("area-admin");
        u.setUsername("admin");
        u.setRol(com.sibim.model.enums.Rol.ADMIN);
        return u;
    }

    @Test
    void moverDireccion_enDemo_cambiaElPadreSoloEnMemoria() throws SQLException {
        DatabaseConfig.setDemoMode(true);
        com.sibim.session.SessionManager.setCurrentUser(admin());
        try {
            String direccion = Areas.direccionesPresidencia().get(0);
            String destino = Areas.secretarias().get(0).nombre();
            service.moverDireccion(direccion, destino);
            assertEquals(destino, Areas.catalogo().buscar(direccion).orElseThrow().padre());
            assertFalse(Areas.direccionesPresidencia().contains(direccion));
            assertEquals(0, repo.cargas, "demo: no toca la base");
        } finally {
            com.sibim.session.SessionManager.logout();
            DatabaseConfig.setDemoMode(false);
        }
    }

    @Test
    void moverDireccion_rechazaLoQueNoEsDireccionYPadresInvalidos() {
        DatabaseConfig.setDemoMode(true);
        com.sibim.session.SessionManager.setCurrentUser(admin());
        try {
            String secretaria = Areas.secretarias().get(0).nombre();
            String direccion = Areas.direccionesPresidencia().get(0);
            assertThrows(IllegalArgumentException.class,
                () -> service.moverDireccion(secretaria, Areas.PRESIDENCIA));
            assertThrows(IllegalArgumentException.class,
                () -> service.moverDireccion(direccion, Areas.autonomos().get(0)),
                "una dirección no puede depender de un organismo autónomo");
            assertSame(AreaCatalog.PREDETERMINADO, Areas.catalogo());
        } finally {
            com.sibim.session.SessionManager.logout();
            DatabaseConfig.setDemoMode(false);
        }
    }

    @Test
    void moverDireccion_cambiaQueSecretarioLaVe() throws SQLException {
        DatabaseConfig.setDemoMode(true);
        String direccion = Areas.secretarias().get(0).direcciones().get(0);
        String origen = Areas.secretarias().get(0).nombre();
        String destino = Areas.secretarias().get(1).nombre();
        try {
            com.sibim.session.SessionManager.setCurrentUser(admin());
            service.moverDireccion(direccion, destino);

            com.sibim.session.SessionManager.setCurrentUser(secretario(destino));
            assertTrue(com.sibim.session.SessionManager.isAreaAccessible(direccion),
                "el secretario de destino ya la ve");
            com.sibim.session.SessionManager.setCurrentUser(secretario(origen));
            assertFalse(com.sibim.session.SessionManager.isAreaAccessible(direccion),
                "el secretario de origen deja de verla");
        } finally {
            com.sibim.session.SessionManager.logout();
            DatabaseConfig.setDemoMode(false);
        }
    }

    private static com.sibim.model.Usuario secretario(String area) {
        com.sibim.model.Usuario u = admin();
        u.setRol(com.sibim.model.enums.Rol.SECRETARIO);
        u.setArea(area);
        return u;
    }
}
