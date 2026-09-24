package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.Resguardo;
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
        Map<String, List<Resguardo>> resguardosPorArea
    ) {}

    private final ProductoService  productoService;
    private final ResguardoService resguardoService;
    private final Logger           log;

    OrganigramaDataLoader(ProductoService productoService, ResguardoService resguardoService, Logger log) {
        this.productoService  = productoService;
        this.resguardoService = resguardoService;
        this.log              = log;
    }

    void load(Consumer<OrganigramaData> onSuccess, Consumer<Throwable> onError) {
        DialogUtil.runAsync(
            () -> {
                Map<String, List<Producto>> porArea = productoService.getAll().stream()
                    .filter(p -> p.getArea() != null && !p.getArea().isBlank())
                    .collect(Collectors.groupingBy(Producto::getArea));

                Map<String, List<Resguardo>> rsgPorArea = new HashMap<>();
                try {
                    resguardoService.getAll().stream()
                        .filter(r -> Resguardo.ESTADO_ACTIVO.equals(r.getEstado())
                            && r.getResguardanteArea() != null && !r.getResguardanteArea().isBlank())
                        .forEach(r -> rsgPorArea
                            .computeIfAbsent(r.getResguardanteArea(), k -> new ArrayList<>())
                            .add(r));
                } catch (Exception ignored) {
                    log.debug("Could not load resguardos for organigrama", ignored);
                }
                return new OrganigramaData(porArea, rsgPorArea);
            },
            onSuccess,
            onError
        );
    }
}
