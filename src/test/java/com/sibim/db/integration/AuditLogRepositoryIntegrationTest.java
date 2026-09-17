package com.sibim.db.integration;

import com.sibim.model.AuditLog;
import com.sibim.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogRepositoryIntegrationTest extends IntegrationTestBase {

    private final AuditLogRepository repo = new AuditLogRepository();

    private AuditLog buildEntry(String entidad, String accion) {
        AuditLog a = new AuditLog();
        a.setId(UUID.randomUUID().toString());
        a.setEntidad(entidad);
        a.setEntidadId(UUID.randomUUID().toString());
        a.setEntidadNombre("Bien " + accion);
        a.setAccion(accion);
        a.setDetalle("detalle de " + accion);
        a.setUsuarioId("test-admin");
        a.setUsuarioNombre("Admin Test");
        a.setCreadoEn(LocalDateTime.now());
        return a;
    }

    @Test
    void logOnline_persiste_findAll_retornaEntrada() throws SQLException {
        repo.logOnline(buildEntry("Producto", "CREAR"));

        List<AuditLog> all = repo.findAll(10);
        assertFalse(all.isEmpty(), "findAll debe retornar la entrada recién guardada");
        assertEquals("Producto", all.get(0).getEntidad());
        assertEquals("CREAR", all.get(0).getAccion());
    }

    @Test
    void findAll_limit_acotaResultados() throws SQLException {
        for (int i = 0; i < 5; i++) repo.logOnline(buildEntry("Producto", "EDITAR-" + i));

        List<AuditLog> result = repo.findAll(3);
        assertEquals(3, result.size(), "findAll con limit=3 debe retornar exactamente 3 registros");
    }

    @Test
    void findPaginated_offset_paginaCorrectamente() throws SQLException {
        for (int i = 0; i < 4; i++) repo.logOnline(buildEntry("Movimiento", "CREAR-" + i));

        List<AuditLog> pag1 = repo.findPaginated(2, 0, null, null, null, null, null, null);
        List<AuditLog> pag2 = repo.findPaginated(2, 2, null, null, null, null, null, null);

        assertEquals(2, pag1.size(), "primera página debe tener 2 registros");
        assertEquals(2, pag2.size(), "segunda página debe tener 2 registros");
        assertNotEquals(pag1.get(0).getId(), pag2.get(0).getId(), "páginas no deben solaparse");
    }

    @Test
    void countFiltrado_sinFiltros_retornaTotal() throws SQLException {
        repo.logOnline(buildEntry("Usuario", "CREAR"));
        repo.logOnline(buildEntry("Usuario", "EDITAR"));
        repo.logOnline(buildEntry("Usuario", "ELIMINAR"));

        int total = repo.countFiltrado(null, null, null, null, null, null);
        assertEquals(3, total);
    }

    @Test
    void countFiltrado_porEntidad_filtra() throws SQLException {
        repo.logOnline(buildEntry("Producto", "CREAR"));
        repo.logOnline(buildEntry("Producto", "EDITAR"));
        repo.logOnline(buildEntry("Movimiento", "CREAR"));

        int countProducto   = repo.countFiltrado(null, "Producto",   null, null, null, null);
        int countMovimiento = repo.countFiltrado(null, "Movimiento", null, null, null, null);

        assertEquals(2, countProducto,   "debe contar solo entradas de entidad=Producto");
        assertEquals(1, countMovimiento, "debe contar solo entradas de entidad=Movimiento");
    }

    @Test
    void findPaginated_conFiltroEntidad_retornaSoloCoincidencias() throws SQLException {
        repo.logOnline(buildEntry("Categoria", "CREAR"));
        repo.logOnline(buildEntry("Categoria", "EDITAR"));
        repo.logOnline(buildEntry("Reporte",   "EXPORTAR"));

        List<AuditLog> result = repo.findPaginated(10, 0, null, "Categoria", null, null, null, null);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(a -> "Categoria".equals(a.getEntidad())));
    }
}
