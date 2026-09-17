package com.sibim.service;

import com.sibim.db.DatabaseConfig;
import com.sibim.model.AuditLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for ReporteAuditoriaService.
 *
 * exportAuditoriaPdf(List, ...) and exportAuditoriaCsv(List) never touch a
 * repository directly, so the service is built with its public no-arg
 * constructor — no repo mocking needed. Demo mode is enabled so orgName()
 * (used in the PDF header) reads from ConfiguracionRepository's in-memory demo
 * values instead of a real Postgres connection, which isn't available in this
 * test environment. exportAuditoriaPdf() (no-arg overload) is not covered
 * here: it instantiates PrestamoRepository/ResguardoRepository with `new`
 * inside the method body, and ReporteAuditoriaService exposes no constructor
 * to substitute them, so exercising it would require a live DB.
 *
 * Every test asserts the returned File exists on disk and is non-empty, or
 * (for an empty log list, per source) that the method returns null.
 */
class ReporteAuditoriaServiceTest {

    private ReporteAuditoriaService service;
    private AuditLog logCreacion;
    private AuditLog logEdicion;
    private AuditLog logSinDetalle;

    @BeforeAll
    static void enableDemoMode() { DatabaseConfig.setDemoMode(true); }

    @AfterAll
    static void disableDemoMode() { DatabaseConfig.setDemoMode(false); }

    @BeforeEach
    void setUp() {
        service = new ReporteAuditoriaService();

        logCreacion = new AuditLog();
        logCreacion.setId("log-01"); logCreacion.setEntidad("Producto"); logCreacion.setEntidadId("p-01");
        logCreacion.setEntidadNombre("Laptop HP Elite"); logCreacion.setAccion("CREAR");
        logCreacion.setDetalle("Alta de bien"); logCreacion.setUsuarioId("u-01");
        logCreacion.setUsuarioNombre("Admin Test"); logCreacion.setCreadoEn(LocalDateTime.now());

        logEdicion = new AuditLog();
        logEdicion.setId("log-02"); logEdicion.setEntidad("Producto"); logEdicion.setEntidadId("p-01");
        logEdicion.setEntidadNombre("Laptop HP Elite"); logEdicion.setAccion("EDITAR");
        logEdicion.setDetalle("Cambio de área"); logEdicion.setUsuarioId("u-02");
        logEdicion.setUsuarioNombre("Juan Pérez"); logEdicion.setCreadoEn(LocalDateTime.now().minusDays(1));

        // entidadNombre and detalle left null → exercises the service's null-safe "" fallback
        logSinDetalle = new AuditLog();
        logSinDetalle.setId("log-03"); logSinDetalle.setEntidad("Categoria"); logSinDetalle.setEntidadId("c-01");
        logSinDetalle.setAccion("ELIMINAR"); logSinDetalle.setUsuarioNombre("Admin Test");
        logSinDetalle.setCreadoEn(LocalDateTime.now().minusHours(3));
    }

    // ── exportAuditoriaPdf(List<AuditLog>, ...) ────────────────────────────────

    @Test void exportAuditoriaPdf_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportAuditoriaPdf(
            List.of(logCreacion, logEdicion, logSinDetalle), null, null, null, null), ".pdf");
    }

    @Test void exportAuditoriaPdf_withFiltersAndDateRange_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportAuditoriaPdf(
            List.of(logCreacion, logEdicion), "Laptop", "Producto",
            LocalDate.now().minusDays(7), LocalDate.now()), ".pdf");
    }

    @Test void exportAuditoriaPdf_emptyList_returnsNull() throws Exception {
        assertNull(service.exportAuditoriaPdf(List.of(), null, null, null, null));
    }

    // ── exportAuditoriaCsv(List<AuditLog>) ─────────────────────────────────────

    @Test void exportAuditoriaCsv_withData_createsNonEmptyFile() throws Exception {
        assertFileProduced(service.exportAuditoriaCsv(List.of(logCreacion, logEdicion, logSinDetalle)), ".csv");
    }

    @Test void exportAuditoriaCsv_emptyList_returnsNull() throws Exception {
        assertNull(service.exportAuditoriaCsv(List.of()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void assertFileProduced(File file, String expectedExtension) {
        assertNotNull(file);
        assertTrue(file.exists(), "El archivo debe existir: " + file.getAbsolutePath());
        assertTrue(file.length() > 0, "El archivo no debe estar vacío");
        assertTrue(file.getName().endsWith(expectedExtension),
            "Se esperaba extensión " + expectedExtension + " pero fue: " + file.getName());
    }
}
