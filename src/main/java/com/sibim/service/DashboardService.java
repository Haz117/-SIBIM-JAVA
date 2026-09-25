package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.util.AppExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/**
 * Aggregates all data needed by the dashboard in a single service call.
 * All 6 repository queries run in parallel on virtual threads so the total
 * load time equals the slowest query, not the sum of all six.
 */
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ProductoRepository   productoRepo;
    private final MovimientoRepository movimientoRepo;

    public DashboardService() { this(new ProductoRepository(), new MovimientoRepository()); }
    DashboardService(ProductoRepository productoRepo, MovimientoRepository movimientoRepo) {
        this.productoRepo   = productoRepo;
        this.movimientoRepo = movimientoRepo;
    }

    public Resumen cargarResumen() throws SQLException {
        int anioActual   = LocalDate.now().getYear();
        int anioAnterior = anioActual - 1;
        ExecutorService exec = AppExecutor.pool();
        var fStats        = async(() -> productoRepo.getStats(),                  exec);
        var fCatValores   = async(() -> productoRepo.getValorPorCategoria(),      exec);
        var fAgotados     = async(() -> productoRepo.findAgotados(),              exec);
        var fBajoStock    = async(() -> productoRepo.findBajoStock(),             exec);
        var fProximasRev  = async(() -> productoRepo.findProximasRevisiones(30),  exec);
        var fMovHoy       = async(() -> movimientoRepo.findToday(),               exec);
        var fMovSemana    = async(() -> movimientoRepo.findLastNDays(7),          exec);
        var fMovMensual      = async(() -> movimientoRepo.findMonthlyStats(6),       exec);
        var fMovMensualValor = async(() -> movimientoRepo.findMonthlyValorStats(6),  exec);
        var fByArea          = async(() -> productoRepo.countByArea(5),              exec);
        var fMovsActual      = async(() -> movimientoRepo.countByAnio(anioActual),   exec);
        var fMovsAnterior    = async(() -> movimientoRepo.countByAnio(anioAnterior), exec);

        try {
            CompletableFuture.allOf(
                fStats, fCatValores, fAgotados, fBajoStock, fProximasRev,
                fMovHoy, fMovSemana, fMovMensual, fMovMensualValor, fByArea,
                fMovsActual, fMovsAnterior).join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof SQLException sql) throw sql;
            throw new RuntimeException(cause);
        }

        var stats  = fStats.join();
        var movHoy = fMovHoy.join();
        log.debug("Dashboard (paralelo): {} bienes, {} categorías, {} movs hoy",
            stats.total(), stats.categorias(), movHoy.size());
        return new Resumen(stats, fCatValores.join(), fAgotados.join(),
                           fBajoStock.join(), fProximasRev.join(), movHoy, fMovSemana.join(),
                           fMovMensual.join(), fMovMensualValor.join(),
                           fByArea.join(), fMovsActual.join(), fMovsAnterior.join());
    }

    // Wraps a checked-exception supplier into a CompletableFuture.
    private static <T> CompletableFuture<T> async(SqlSupplier<T> s, ExecutorService exec) {
        return CompletableFuture.supplyAsync(() -> {
            try { return s.get(); }
            catch (SQLException e) { throw new CompletionException(e); }
        }, exec);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> { T get() throws SQLException; }

    // ── Cache ────────────────────────────────────────────────────────────────

    private static final long CACHE_TTL_MS = 30_000;
    private static volatile Resumen cachedResumen = null;
    private static volatile long    cacheTimestamp = 0;

    /** Returns cached data if fresh, otherwise fetches and caches. */
    public Resumen getCachedOrFetch() throws SQLException {
        long now = System.currentTimeMillis();
        if (cachedResumen != null && (now - cacheTimestamp) < CACHE_TTL_MS)
            return cachedResumen;
        Resumen r = cargarResumen();
        cachedResumen  = r;
        cacheTimestamp = System.currentTimeMillis();
        return r;
    }

    public void invalidateCache() { cacheTimestamp = 0; }

    /** Panel ejecutivo (tesorería/presidencia): % del patrimonio ya
     *  depreciado y qué bienes cumplen su vida útil este año — a diferencia
     *  de {@link #cargarResumen}, esto recorre el inventario completo (la
     *  depreciación se calcula en Java por bien, no hay agregado en SQL), así
     *  que se calcula bajo demanda en vez de cada 30 s. */
    public ResumenEjecutivo calcularResumenEjecutivo() throws SQLException {
        List<Producto> activos = productoRepo.findAll(false);
        BigDecimal valorCompraTotal = BigDecimal.ZERO;
        BigDecimal valorDepreciadoTotal = BigDecimal.ZERO;
        int conDatosDepreciacion = 0;
        int finVidaUtilEsteAnio = 0;
        List<Producto> bienesFinVidaUtil = new ArrayList<>();
        int anioActual = LocalDate.now().getYear();

        for (Producto p : activos) {
            BigDecimal valorDep = p.getValorDepreciado();
            if (valorDep == null) continue;
            conDatosDepreciacion++;
            valorCompraTotal = valorCompraTotal.add(p.getPrecioCompra());
            valorDepreciadoTotal = valorDepreciadoTotal.add(valorDep);

            LocalDate finVidaUtil = p.getFechaAdquisicion().plusYears(p.getVidaUtilAnios());
            if (finVidaUtil.getYear() == anioActual) {
                finVidaUtilEsteAnio++;
                bienesFinVidaUtil.add(p);
            }
        }

        double porcentajeDepreciado = valorCompraTotal.signum() > 0
            ? valorCompraTotal.subtract(valorDepreciadoTotal)
                .divide(valorCompraTotal, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue()
            : 0.0;

        return new ResumenEjecutivo(activos.size(), conDatosDepreciacion, valorCompraTotal,
            valorDepreciadoTotal, porcentajeDepreciado, finVidaUtilEsteAnio, bienesFinVidaUtil);
    }

    public record ResumenEjecutivo(
            int totalBienes,
            int bienesConDatosDepreciacion,
            BigDecimal valorCompraTotal,
            BigDecimal valorDepreciadoTotal,
            double porcentajeDepreciado,
            int bienesFinVidaUtilEsteAnio,
            List<Producto> bienesFinVidaUtil) {}

    public record Resumen(
            ProductoRepository.ProductoStats stats,
            List<ProductoRepository.CategoriaValor> catValores,
            List<Producto> agotados,
            List<Producto> bajoStock,
            List<Producto> proximasRevisiones,
            List<Movimiento> movHoy,
            List<Movimiento> movSemana,
            List<MovimientoRepository.MonthlyStats> movMensual,
            List<MovimientoRepository.MonthlyValorStats> movMensualValor,
            LinkedHashMap<String, Long> byArea,
            long movsAnioActual,
            long movsAnioAnterior) {}
}
