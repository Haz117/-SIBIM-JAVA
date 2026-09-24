package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.repository.MovimientoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.util.AppExecutor;
import javafx.concurrent.Task;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

class MovimientosDataLoader {

    record LoadDataResult(
        List<String> categorias,
        List<Movimiento> page,
        int count,
        MovimientoRepository.MovimientoStats stats
    ) {}

    record LoadPageResult(List<Movimiento> page, int count) {}

    private final MovimientoService movimientoService;

    MovimientosDataLoader(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    void loadData(LocalDate desde, LocalDate hasta, String query, String tipo, String categoria,
                  int limit, int offset,
                  Consumer<LoadDataResult> onSuccess, Consumer<Throwable> onError) {
        AppExecutor.submit(new Task<LoadDataResult>() {
            @Override protected LoadDataResult call() throws Exception {
                List<String> cats  = movimientoService.getCategorias(desde, hasta);
                List<Movimiento> p = movimientoService.getPaginated(desde, hasta, query, tipo, categoria, limit, offset);
                int cnt            = movimientoService.countFiltrado(desde, hasta, query, tipo, categoria);
                MovimientoRepository.MovimientoStats st = movimientoService.getStats(desde, hasta);
                return new LoadDataResult(cats, p, cnt, st);
            }
            @Override protected void succeeded() { onSuccess.accept(getValue()); }
            @Override protected void failed()    { onError.accept(getException()); }
        });
    }

    void loadPage(LocalDate desde, LocalDate hasta, String query, String tipo, String categoria,
                  int limit, int offset,
                  Consumer<LoadPageResult> onSuccess, Consumer<Throwable> onError) {
        AppExecutor.submit(new Task<LoadPageResult>() {
            @Override protected LoadPageResult call() throws Exception {
                List<Movimiento> p = movimientoService.getPaginated(desde, hasta, query, tipo, categoria, limit, offset);
                int cnt            = movimientoService.countFiltrado(desde, hasta, query, tipo, categoria);
                return new LoadPageResult(p, cnt);
            }
            @Override protected void succeeded() { onSuccess.accept(getValue()); }
            @Override protected void failed()    { onError.accept(getException()); }
        });
    }
}
