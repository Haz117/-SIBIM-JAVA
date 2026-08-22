package com.sibim.service;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Aggregates all data needed by the dashboard in a single service call,
 * replacing direct repository access from DashboardController.
 */
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    private final ProductoRepository    productoRepo    = new ProductoRepository();
    private final MovimientoRepository  movimientoRepo  = new MovimientoRepository();

    public Resumen cargarResumen() throws SQLException {
        var stats      = productoRepo.getStats();
        var catValores = productoRepo.getValorPorCategoria();
        var agotados   = productoRepo.findAgotados();
        var bajoStock  = productoRepo.findBajoStock();
        var movHoy     = movimientoRepo.findToday();
        var movSemana  = movimientoRepo.findLastNDays(7);
        log.debug("Dashboard: {} bienes, {} categorías, {} movs hoy",
            stats.total(), stats.categorias(), movHoy.size());
        return new Resumen(stats, catValores, agotados, bajoStock, movHoy, movSemana);
    }

    public record Resumen(
            ProductoRepository.ProductoStats stats,
            List<ProductoRepository.CategoriaValor> catValores,
            List<Producto> agotados,
            List<Producto> bajoStock,
            List<Movimiento> movHoy,
            List<Movimiento> movSemana) {}
}
