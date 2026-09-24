package com.sibim.controller;

import com.sibim.model.Comodato;
import com.sibim.model.Producto;
import com.sibim.service.ComodatoService;
import com.sibim.service.ProductoService;
import com.sibim.util.DialogUtil;

import java.util.List;
import java.util.function.Consumer;

class AlertasDataLoader {

    record AlertasResult(
        List<Producto> agotados,
        List<Producto> bajoStock,
        List<Producto> garantias,
        List<Producto> mantenimiento,
        List<Comodato> comodatosVencidos
    ) {}

    private final ProductoService productoService;
    private final ComodatoService comodatoService;

    AlertasDataLoader(ProductoService productoService, ComodatoService comodatoService) {
        this.productoService = productoService;
        this.comodatoService = comodatoService;
    }

    void load(Consumer<AlertasResult> onSuccess, Consumer<Throwable> onError) {
        DialogUtil.runAsync(
            () -> {
                comodatoService.actualizarVencidos();
                return new AlertasResult(
                    productoService.getAgotados(),
                    productoService.getBajoStock(),
                    productoService.getVencidosProximos(30),
                    productoService.getProximasRevisiones(30),
                    comodatoService.getVencidos()
                );
            },
            onSuccess,
            onError
        );
    }
}
