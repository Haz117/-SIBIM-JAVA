package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var fStats      = async(() -> productoRepo.getStats(),              exec);
            var fCatValores = async(() -> productoRepo.getValorPorCategoria(),  exec);
            var fAgotados   = async(() -> productoRepo.findAgotados(),          exec);
            var fBajoStock  = async(() -> productoRepo.findBajoStock(),         exec);
            var fMovHoy     = async(() -> movimientoRepo.findToday(),           exec);
            var fMovSemana  = async(() -> movimientoRepo.findLastNDays(7),      exec);
            var fMovMensual = async(() -> movimientoRepo.findMonthlyStats(6),   exec);

            try {
                CompletableFuture.allOf(
                    fStats, fCatValores, fAgotados, fBajoStock,
                    fMovHoy, fMovSemana, fMovMensual).join();
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
                               fBajoStock.join(), movHoy, fMovSemana.join(), fMovMensual.join());
        }
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

    public record Resumen(
            ProductoRepository.ProductoStats stats,
            List<ProductoRepository.CategoriaValor> catValores,
            List<Producto> agotados,
            List<Producto> bajoStock,
            List<Movimiento> movHoy,
            List<Movimiento> movSemana,
            List<MovimientoRepository.MonthlyStats> movMensual) {}
}
