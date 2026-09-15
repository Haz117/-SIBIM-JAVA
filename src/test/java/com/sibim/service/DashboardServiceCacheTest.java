package com.sibim.service;

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
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceCacheTest {

    @Mock ProductoRepository   mockProductoRepo;
    @Mock MovimientoRepository mockMovimientoRepo;

    private DashboardService service;

    @BeforeEach
    void setUp() throws SQLException {
        service = new DashboardService(mockProductoRepo, mockMovimientoRepo);
        service.invalidateCache();
        stubRepos();
    }

    @AfterEach
    void tearDown() {
        service.invalidateCache();
    }

    // ── Caché: hit en segunda llamada ─────────────────────────────────────────

    @Test
    void getCachedOrFetch_llamadaDosVeces_repoSoloSeEjecunaUnaVez() throws Exception {
        service.getCachedOrFetch();
        service.getCachedOrFetch();

        verify(mockProductoRepo, times(1)).getStats();
    }

    @Test
    void getCachedOrFetch_primeraLlamada_retornaResumenNoNulo() throws Exception {
        DashboardService.Resumen r = service.getCachedOrFetch();
        assertNotNull(r);
    }

    @Test
    void getCachedOrFetch_segundaLlamada_retornaMismoObjeto() throws Exception {
        DashboardService.Resumen primera = service.getCachedOrFetch();
        DashboardService.Resumen segunda = service.getCachedOrFetch();
        assertSame(primera, segunda);
    }

    // ── invalidateCache ───────────────────────────────────────────────────────

    @Test
    void invalidateCache_forzaNuevaConsultaAlRepo() throws Exception {
        service.getCachedOrFetch();
        service.invalidateCache();
        service.getCachedOrFetch();

        verify(mockProductoRepo, times(2)).getStats();
    }

    @Test
    void invalidateCache_dosVeces_noLanzaExcepcion() {
        assertDoesNotThrow(() -> {
            service.invalidateCache();
            service.invalidateCache();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubRepos() throws SQLException {
        var stats = new ProductoRepository.ProductoStats(0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, 0L);
        when(mockProductoRepo.getStats()).thenReturn(stats);
        when(mockProductoRepo.getValorPorCategoria()).thenReturn(List.of());
        when(mockProductoRepo.findAgotados()).thenReturn(List.of());
        when(mockProductoRepo.findBajoStock()).thenReturn(List.of());
        when(mockProductoRepo.countByArea(anyInt())).thenReturn(new LinkedHashMap<>());
        when(mockMovimientoRepo.findToday()).thenReturn(List.of());
        when(mockMovimientoRepo.findLastNDays(anyInt())).thenReturn(List.of());
        when(mockMovimientoRepo.findMonthlyStats(anyInt())).thenReturn(List.of());
        when(mockMovimientoRepo.countByAnio(anyInt())).thenReturn(0L);
    }
}
