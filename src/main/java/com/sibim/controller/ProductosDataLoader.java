package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.ProductoService;
import com.sibim.util.AppExecutor;
import javafx.concurrent.Task;

import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Executes the two background Task&lt;Void&gt; blocks extracted from
 * ProductosController: a full loadData() (fetches stats + resguardantes)
 * and a lighter loadPage() (page + count only).
 * Package-private; only used by ProductosController.
 */
class ProductosDataLoader {

    /** Carries the full result of a loadData() call. */
    record LoadDataResult(
            List<Producto> pageData,
            int count,
            ProductoRepository.InventarioStats stats,
            List<String> resguardantes) {}

    /** Carries the result of a loadPage() call. */
    record LoadPageResult(List<Producto> page, int count) {}

    private final ProductoService productoService;

    ProductosDataLoader(ProductoService productoService) {
        this.productoService = productoService;
    }

    /**
     * Builds and submits the full-load Task.
     *
     * @param busqueda        search term (already lowercased)
     * @param catId           category id or null
     * @param area            area name or null
     * @param resguardante    resguardante or null
     * @param estado          estado or null
     * @param filterSinEtiquetar  whether to restrict to unlabeled items
     * @param pageSize        page size
     * @param offset          row offset
     * @param desdeReg        from-date or null
     * @param hastaReg        to-date or null
     * @param onSuccess       called on the FX thread with the full result
     * @param onFailure       called on the FX thread with the exception
     */
    void loadData(
            String busqueda, String catId, String area, String resguardante,
            EstadoProducto estado, boolean filterSinEtiquetar,
            int pageSize, int offset,
            LocalDate desdeReg, LocalDate hastaReg,
            Consumer<LoadDataResult> onSuccess,
            Consumer<Throwable> onFailure) {

        Task<LoadDataResult> task = new Task<>() {
            @Override protected LoadDataResult call() throws Exception {
                List<String> resguardantes = productoService.getResguardantes();
                int count = productoService.countFiltrado(busqueda, catId, area, resguardante,
                    estado, filterSinEtiquetar, desdeReg, hastaReg);
                List<Producto> pageData = productoService.getPaginated(busqueda, catId, area,
                    resguardante, estado, filterSinEtiquetar, pageSize, offset, desdeReg, hastaReg);
                ProductoRepository.InventarioStats stats = productoService.getStats();
                return new LoadDataResult(pageData, count, stats, resguardantes);
            }
            @Override protected void succeeded() { onSuccess.accept(getValue()); }
            @Override protected void failed()    { onFailure.accept(getException()); }
        };
        AppExecutor.submit(task);
    }

    /**
     * Builds and submits a page-only Task (no stats, no resguardantes).
     *
     * @param busqueda        search term (already lowercased)
     * @param catId           category id or null
     * @param area            area name or null
     * @param resguardante    resguardante or null
     * @param estado          estado or null
     * @param filterSinEtiquetar  whether to restrict to unlabeled items
     * @param pageSize        page size
     * @param offset          row offset
     * @param desdeReg        from-date or null
     * @param hastaReg        to-date or null
     * @param onSuccess       called on the FX thread with (page list, count)
     * @param onFailure       called on the FX thread with the exception
     */
    void loadPage(
            String busqueda, String catId, String area, String resguardante,
            EstadoProducto estado, boolean filterSinEtiquetar,
            int pageSize, int offset,
            LocalDate desdeReg, LocalDate hastaReg,
            Consumer<LoadPageResult> onSuccess,
            Consumer<Throwable> onFailure) {

        Task<LoadPageResult> task = new Task<>() {
            @Override protected LoadPageResult call() throws Exception {
                List<Producto> page = productoService.getPaginated(busqueda, catId, area,
                    resguardante, estado, filterSinEtiquetar, pageSize, offset, desdeReg, hastaReg);
                int count = productoService.countFiltrado(busqueda, catId, area, resguardante,
                    estado, filterSinEtiquetar, desdeReg, hastaReg);
                return new LoadPageResult(page, count);
            }
            @Override protected void succeeded() { onSuccess.accept(getValue()); }
            @Override protected void failed()    { onFailure.accept(getException()); }
        };
        AppExecutor.submit(task);
    }
}
