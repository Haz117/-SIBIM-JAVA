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

                Map<String, List<Prestamo>> prestPorArea = new HashMap<>();
                try {
                    prestamoService.getActivos().stream()
                        .filter(p -> p.getAreaDestino() != null && !p.getAreaDestino().isBlank())
                        .forEach(p -> prestPorArea
                            .computeIfAbsent(p.getAreaDestino(), k -> new ArrayList<>())
                            .add(p));
                } catch (Exception ignored) {
                    log.debug("Could not load prestamos for organigrama", ignored);
                }

                // Build a flat lookup of productoId -> area for cross-referencing comodatos
                Map<String, String> productoIdToArea = new HashMap<>();
                porArea.forEach((area, prods) ->
                    prods.forEach(p -> productoIdToArea.put(p.getId(), area)));

                Map<String, List<Comodato>> comodPorArea = new HashMap<>();
                try {
                    comodatoService.getVigentes().stream()
                        .filter(c -> c.getProductoId() != null)
                        .forEach(c -> {
                            String area = productoIdToArea.get(c.getProductoId());
                            if (area != null) {
                                comodPorArea
                                    .computeIfAbsent(area, k -> new ArrayList<>())
                                    .add(c);
                            }
                        });
                } catch (Exception ignored) {
                    log.debug("Could not load comodatos for organigrama", ignored);
                }

                return new OrganigramaData(porArea, rsgPorArea, prestPorArea, comodPorArea);
            },
            onSuccess,
            onError
        );
    }
}
