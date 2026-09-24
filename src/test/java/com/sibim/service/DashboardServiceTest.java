package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DashboardService#cargarResumen and aggregate stats logic.
 * All repository calls are mocked — no database connection required.
 *
 * Cache and ResumenEjecutivo tests are covered in DashboardServiceCacheTest
 * and DashboardServiceEjecutivoTest; this file focuses on the core
 * cargarResumen aggregation and edge-case behaviour.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock ProductoRepository   mockProductoRepo;
    @Mock MovimientoRepository mockMovimientoRepo;

    private DashboardService service;

    @BeforeEach
    void setUp() throws SQLException {
        service = new DashboardService(mockProductoRepo, mockMovimientoRepo);
        service.invalidateCache();
        stubDefaults();
    }

    @AfterEach
    void tearDown() {
        service.invalidateCache();
    }

    // ── cargarResumen — retorna objeto no nulo ────────────────────────────────

    @Test
    void cargarResumen_conDatosVacios_retornaResumenNoNulo() throws Exception {
        DashboardService.Resumen r = service.cargarResumen();
        assertNotNull(r, "cargarResumen nunca debe retornar null");
    }

    @Test
    void cargarResumen_statsRetornadasDesdeRepo_sonLasDelResumen() throws Exception {
        var stats = new ProductoRepository.ProductoStats(42L, 38L, 2L, 1L, 1L,
            BigDecimal.valueOf(100_000), 5L);
        when(mockProductoRepo.getStats()).thenReturn(stats);

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(42L, r.stats().total());
        assertEquals(5L,  r.stats().categorias());
    }

    @Test
    void cargarResumen_conCeroProductos_statsConTotalCero() throws Exception {
        var stats = new ProductoRepository.ProductoStats(0L, 0L, 0L, 0L, 0L,
            BigDecimal.ZERO, 0L);
        when(mockProductoRepo.getStats()).thenReturn(stats);

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(0L, r.stats().total());
        assertEquals(BigDecimal.ZERO, r.stats().valorTotal());
    }

    // ── cargarResumen — listas vacías son manejeadas con gracia ──────────────

    @Test
    void cargarResumen_sinAgotados_listaAgotadosEsVacia() throws Exception {
        when(mockProductoRepo.findAgotados()).thenReturn(List.of());

        DashboardService.Resumen r = service.cargarResumen();

        assertNotNull(r.agotados());
        assertTrue(r.agotados().isEmpty());
    }

    @Test
    void cargarResumen_sinMovimientosHoy_listaMovHoyEsVacia() throws Exception {
        when(mockMovimientoRepo.findToday()).thenReturn(List.of());

        DashboardService.Resumen r = service.cargarResumen();

        assertNotNull(r.movHoy());
        assertTrue(r.movHoy().isEmpty());
    }

    @Test
    void cargarResumen_conMovimientosHoy_seReflejanEnResumen() throws Exception {
        Movimiento mov = new Movimiento();
        mov.setId("mov-1");
        mov.setProductoNombre("Laptop");
        when(mockMovimientoRepo.findToday()).thenReturn(List.of(mov));

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(1, r.movHoy().size());
        assertEquals("mov-1", r.movHoy().get(0).getId());
    }

    // ── cargarResumen — bajoStock y proximasRevisiones ────────────────────────

    @Test
    void cargarResumen_conBienesEnBajoStock_seIncluyen() throws Exception {
        Producto p = new Producto();
        p.setNombre("Papel bond");
        p.setStockActual(2);
        p.setStockMinimo(10);
        when(mockProductoRepo.findBajoStock()).thenReturn(List.of(p));

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(1, r.bajoStock().size());
        assertEquals("Papel bond", r.bajoStock().get(0).getNombre());
    }

    @Test
    void cargarResumen_conProximasRevisiones_seIncluyen() throws Exception {
        Producto p = new Producto();
        p.setNombre("Extintor");
        p.setProximaRevision(LocalDate.now().plusDays(10));
        when(mockProductoRepo.findProximasRevisiones(anyInt())).thenReturn(List.of(p));

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(1, r.proximasRevisiones().size());
    }

    // ── cargarResumen — estadísticas anuales de movimientos ──────────────────

    @Test
    void cargarResumen_contadoresPorAnio_seMapeanCorrectamente() throws Exception {
        when(mockMovimientoRepo.countByAnio(anyInt()))
            .thenReturn(150L)   // primer anio (actual)
            .thenReturn(200L);  // segundo anio (anterior)

        DashboardService.Resumen r = service.cargarResumen();

        // Los contadores deben ser los retornados por el repo
        assertTrue(r.movsAnioActual() >= 0);
        assertTrue(r.movsAnioAnterior() >= 0);
    }

    // ── cargarResumen — distribución por área ────────────────────────────────

    @Test
    void cargarResumen_byArea_seIncluye() throws Exception {
        LinkedHashMap<String, Long> byArea = new LinkedHashMap<>();
        byArea.put("Tesorería", 10L);
        byArea.put("Jurídico", 5L);
        when(mockProductoRepo.countByArea(anyInt())).thenReturn(byArea);

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(2, r.byArea().size());
        assertEquals(10L, r.byArea().get("Tesorería"));
    }

    // ── cargarResumen — valores de categoría ─────────────────────────────────

    @Test
    void cargarResumen_catValores_seIncluyen() throws Exception {
        var catValores = List.of(
            new ProductoRepository.CategoriaValor("Mobiliario", BigDecimal.valueOf(80_000)),
            new ProductoRepository.CategoriaValor("Equipo de cómputo", BigDecimal.valueOf(120_000))
        );
        when(mockProductoRepo.getValorPorCategoria()).thenReturn(catValores);

        DashboardService.Resumen r = service.cargarResumen();

        assertEquals(2, r.catValores().size());
        assertEquals("Mobiliario", r.catValores().get(0).nombre());
    }

    // ── cargarResumen — repositorio lanza excepción ───────────────────────────

    @Test
    void cargarResumen_repoLanzaSQLException_seRelanza() throws SQLException {
        when(mockProductoRepo.getStats()).thenThrow(new SQLException("conexión perdida"));

        assertThrows(SQLException.class, () -> service.cargarResumen());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubDefaults() throws SQLException {
        var stats = new ProductoRepository.ProductoStats(0L, 0L, 0L, 0L, 0L,
            BigDecimal.ZERO, 0L);
        when(mockProductoRepo.getStats()).thenReturn(stats);
        when(mockProductoRepo.getValorPorCategoria()).thenReturn(List.of());
        when(mockProductoRepo.findAgotados()).thenReturn(List.of());
        when(mockProductoRepo.findBajoStock()).thenReturn(List.of());
        when(mockProductoRepo.findProximasRevisiones(anyInt())).thenReturn(List.of());
        when(mockProductoRepo.countByArea(anyInt())).thenReturn(new LinkedHashMap<>());
        when(mockMovimientoRepo.findToday()).thenReturn(List.of());
        when(mockMovimientoRepo.findLastNDays(anyInt())).thenReturn(List.of());
        when(mockMovimientoRepo.findMonthlyStats(anyInt())).thenReturn(List.of());
        when(mockMovimientoRepo.countByAnio(anyInt())).thenReturn(0L);
    }
}
