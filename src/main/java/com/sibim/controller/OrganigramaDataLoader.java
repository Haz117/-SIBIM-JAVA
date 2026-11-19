package com.sibim.controller;

import com.sibim.model.Comodato;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
import com.sibim.service.ComodatoService;
import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ResguardoService;
import com.sibim.util.DialogUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

class OrganigramaDataLoader {

    record OrganigramaData(
        Map<String, List<Producto>> productosPorArea,
        Map<String, List<Resguardo>> resguardosPorArea,
        Map<String, List<Prestamo>> prestamosPorArea,
        Map<String, List<Comodato>> comodatosPorArea,
        Map<String, com.sibim.service.AvanceAreasService.Avance> avance,
        /** The bienes in a resguardo in force: the rest are «sin resguardo». */
        java.util.Set<String> enResguardo
    ) {}

    private final ProductoService  productoService;
    private final ResguardoService resguardoService;
    private final PrestamoService  prestamoService;
    private final ComodatoService  comodatoService;
    private final Logger           log;

    OrganigramaDataLoader(ProductoService productoService, ResguardoService resguardoService,
                          PrestamoService prestamoService, ComodatoService comodatoService,
                          Logger log) {
        this.productoService  = productoService;
        this.resguardoService = resguardoService;
        this.prestamoService  = prestamoService;
        this.comodatoService  = comodatoService;
        this.log              = log;
    }

    void load(Consumer<OrganigramaData> onSuccess, Consumer<Exception> onError) {
        DialogUtil.runAsync(
            () -> {
                long inicio = System.nanoTime();
                // The five reads are independent: at the same time, the screen
                // waits for the slowest instead of their sum.
                java.util.concurrent.CompletableFuture<List<Resguardo>> resguardos;
                java.util.concurrent.CompletableFuture<List<Prestamo>> prestamos;
                java.util.concurrent.CompletableFuture<List<Comodato>> comodatos;
                java.util.concurrent.CompletableFuture<Map<String, java.time.LocalDateTime>> accesos;
                java.util.concurrent.CompletableFuture<java.util.Set<String>> cuentas;
                Map<String, List<Producto>> porArea;
                List<Producto> todos;
                try (var paralelo = java.util.concurrent.Executors.newFixedThreadPool(5)) {
                    resguardos = java.util.concurrent.CompletableFuture.supplyAsync(() -> leer(resguardoService::getAll, "resguardos"), paralelo);
                    prestamos  = java.util.concurrent.CompletableFuture.supplyAsync(() -> leer(prestamoService::getActivos, "préstamos"), paralelo);
                    comodatos  = java.util.concurrent.CompletableFuture.supplyAsync(() -> leer(comodatoService::getVigentes, "comodatos"), paralelo);
                    accesos    = java.util.concurrent.CompletableFuture.supplyAsync(this::accesos, paralelo);
                    cuentas    = java.util.concurrent.CompletableFuture.supplyAsync(this::cuentas, paralelo);
                    todos = productoService.getAll();
                    porArea = todos.stream()
                        .filter(p -> p.getArea() != null && !p.getArea().isBlank())
                        .collect(Collectors.groupingBy(Producto::getArea));
                }

                java.util.Set<String> enResguardo = new java.util.HashSet<>();
                resguardos.join().stream()
                    .filter(r -> Resguardo.ESTADO_ACTIVO.equals(r.getEstado()) && r.getItems() != null)
                    .forEach(r -> r.getItems().forEach(it -> enResguardo.add(it.getProductoId())));

                Map<String, List<Resguardo>> rsgPorArea = new HashMap<>();
                resguardos.join().stream()
                    .filter(r -> Resguardo.ESTADO_ACTIVO.equals(r.getEstado())
                        && r.getResguardanteArea() != null && !r.getResguardanteArea().isBlank())
                    .forEach(r -> rsgPorArea
                        .computeIfAbsent(r.getResguardanteArea(), k -> new ArrayList<>())
                        .add(r));

                Map<String, List<Prestamo>> prestPorArea = new HashMap<>();
                prestamos.join().stream()
                    .filter(p -> p.getAreaDestino() != null && !p.getAreaDestino().isBlank())
                    .forEach(p -> prestPorArea
                        .computeIfAbsent(p.getAreaDestino(), k -> new ArrayList<>())
                        .add(p));

                // Build a flat lookup of productoId -> area for cross-referencing comodatos
                Map<String, String> productoIdToArea = new HashMap<>();
                porArea.forEach((area, prods) ->
                    prods.forEach(p -> productoIdToArea.put(p.getId(), area)));

                Map<String, List<Comodato>> comodPorArea = new HashMap<>();
                comodatos.join().stream()
                    .filter(c -> c.getProductoId() != null)
                    .forEach(c -> {
                        String area = productoIdToArea.get(c.getProductoId());
                        if (area != null) {
                            comodPorArea
                                .computeIfAbsent(area, k -> new ArrayList<>())
                                .add(c);
                        }
                    });

                // How long the screen waits with the real inventory: the basis for
                // deciding whether it must load by área instead of everything at once.
                log.info("Organigrama cargado: {} bienes en {} áreas, {} ms",
                    productoIdToArea.size(), porArea.size(), (System.nanoTime() - inicio) / 1_000_000);
                var avance = com.sibim.service.AvanceAreasService.calcular(todos, accesos.join(),
                    cuentas.join(), com.sibim.config.Areas.getAllAreaNames());
                return new OrganigramaData(porArea, rsgPorArea, prestPorArea, comodPorArea, avance, enResguardo);
            },
            onSuccess,
            onError
        );
    }

    /** Who entered SIBIM, for phase 1; without it the avance still shows phases 2 and 3. */
    private Map<String, java.time.LocalDateTime> accesos() {
        try {
            return new com.sibim.service.AvanceAreasService().accesosPorArea();
        } catch (Exception e) {
            log.debug("No se pudieron leer los accesos por área para el organigrama", e);
            return Map.of();
        }
    }

    /** Which áreas have an account of their own, for phase 1; null when it can't be read. */
    private java.util.Set<String> cuentas() {
        try {
            return new com.sibim.service.AvanceAreasService().areasConCuenta();
        } catch (Exception e) {
            log.debug("No se pudieron leer las cuentas por área para el organigrama", e);
            return null;
        }
    }

    @FunctionalInterface
    interface Lectura<T> { List<T> leer() throws Exception; }

    /** Documents only add badges to the organigrama: one that can't be read
     *  (offline, no permission) leaves its badges out, it doesn't stop the screen. */
    private <T> List<T> leer(Lectura<T> lectura, String que) {
        try {
            return lectura.leer();
        } catch (Exception e) {
            log.debug("No se pudieron leer los {} para el organigrama", que, e);
            return List.of();
        }
    }
}
