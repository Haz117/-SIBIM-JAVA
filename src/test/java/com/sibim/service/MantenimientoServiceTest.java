package com.sibim.service;

import com.sibim.repository.AuditLogRepository;
import com.sibim.repository.ProductoMantenimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MantenimientoService is now a thin mapping layer over
 * ProductoMantenimientoRepository (table producto_mantenimiento, V13) — the
 * actual storage logic lives (and is exercised against a real DB) in
 * ProductoMantenimientoRepositoryIntegrationTest. These tests only cover the
 * record-mapping and delegation this class still does itself.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MantenimientoServiceTest {

    @Mock ProductoMantenimientoRepository mockRepo;
    @Mock AuditLogRepository mockAudit;

    private MantenimientoService service;

    @BeforeEach
    void setUp() {
        service = new MantenimientoService(mockRepo, mockAudit);
    }

    // ── getAlertas() ─────────────────────────────────────────────────────────

    @Test
    void getAlertas_sinEntradas_retornaListaVacia() {
        when(mockRepo.findByProducto("p1")).thenReturn(List.of());

        assertTrue(service.getAlertas("p1").isEmpty());
    }

    @Test
    void getAlertas_mapeaCamposDelRepositorio() {
        when(mockRepo.findByProducto("p1")).thenReturn(List.of(
            new ProductoMantenimientoRepository.Alerta(
                "a-1", "p1", "Cambiar aceite", LocalDate.of(2026, 12, 1), false)));

        List<MantenimientoService.Alerta> alertas = service.getAlertas("p1");

        assertEquals(1, alertas.size());
        assertEquals("a-1", alertas.get(0).id());
        assertEquals("Cambiar aceite", alertas.get(0).descripcion());
        assertEquals(LocalDate.of(2026, 12, 1), alertas.get(0).fecha());
        assertFalse(alertas.get(0).completada());
    }

    @Test
    void getAlertas_completada_preservaFlag() {
        when(mockRepo.findByProducto("p1")).thenReturn(List.of(
            new ProductoMantenimientoRepository.Alerta(
                "a-2", "p1", "Revisión general", LocalDate.of(2026, 6, 15), true)));

        assertTrue(service.getAlertas("p1").get(0).completada());
    }

    @Test
    void getAlertas_delegaElProductoIdCorrecto() {
        service.getAlertas("p1");
        verify(mockRepo).findByProducto("p1");
    }

    // ── agregarAlerta() ───────────────────────────────────────────────────────

    @Test
    void agregarAlerta_delegaAlRepositorio() throws Exception {
        service.agregarAlerta("p1", "Cambiar aceite", LocalDate.of(2026, 12, 1));
        verify(mockRepo).agregar("p1", "Cambiar aceite", LocalDate.of(2026, 12, 1));
    }

    // ── marcarCompletada() ────────────────────────────────────────────────────

    @Test
    void marcarCompletada_delegaElIdCorrecto() throws Exception {
        service.marcarCompletada("a-2");
        verify(mockRepo).marcarCompletada("a-2");
    }

    // ── eliminarAlerta() ──────────────────────────────────────────────────────

    @Test
    void eliminarAlerta_delegaElIdCorrecto() throws Exception {
        service.eliminarAlerta("a-3");
        verify(mockRepo).eliminar("a-3");
    }

    // ── getProximasGlobal() ───────────────────────────────────────────────────

    @Test
    void getProximasGlobal_mapeaATriosDeString() {
        when(mockRepo.findProximas(365)).thenReturn(List.of(
            new ProductoMantenimientoRepository.AlertaGlobal(
                "p1", "Mantenimiento urgente", LocalDate.of(2020, 1, 1))));

        List<String[]> proximas = service.getProximasGlobal(365);

        assertEquals(1, proximas.size());
        assertEquals("p1", proximas.get(0)[0]);
        assertEquals("Mantenimiento urgente", proximas.get(0)[1]);
        assertEquals("2020-01-01", proximas.get(0)[2]);
    }

    @Test
    void getProximasGlobal_sinResultados_retornaListaVacia() {
        when(mockRepo.findProximas(7)).thenReturn(List.of());
        assertTrue(service.getProximasGlobal(7).isEmpty());
    }

    @Test
    void getProximasGlobal_delegaLosDiasCorrectos() {
        service.getProximasGlobal(30);
        verify(mockRepo).findProximas(30);
    }
}
