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
        Map<String, List<Comodato>> comodatosPorArea
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
                // The four reads are independent: at the same time, the screen
                // waits for the slowest instead of their sum.
                java.util.concurrent.CompletableFuture<List<Resguardo>> resguardos;
                java.util.concurrent.CompletableFuture<List<Prestamo>> prestamos;
                java.util.concurrent.CompletableFuture<List<Comodato>> comodatos;
                Map<String, List<Producto>> porArea;
                try (var paralelo = java.util.concurrent.Executors.newFixedThreadPool(3)) {
                    resguardos = java.util.concurrent.CompletableFuture.supplyAsync(() -> leer(resguardoService::getAll, "resguardos"), paralelo);
                    prestamos  = java.util.concurrent.CompletableFuture.supplyAsync(() -> leer(prestamoService::getActivos, "préstamos"), paralelo);
                    comodatos  = java.util.concurrent.CompletableFuture.supplyAsync(() -> leer(comodatoService::getVigentes, "comodatos"), paralelo);
                    porArea = productoService.getAll().stream()
                        .filter(p -> p.getArea() != null && !p.getArea().isBlank())
                        .collect(Collectors.groupingBy(Producto::getArea));
                }

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

                return new OrganigramaData(porArea, rsgPorArea, prestPorArea, comodPorArea);
            },
            onSuccess,
            onError
        );
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
