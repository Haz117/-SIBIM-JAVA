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
        List<Comodato> comodatosVencidos,
        List<AlertasPatrimonialesSection.Pendiente> pendientesPatrimoniales
    ) {}

    private final ProductoService productoService;
    private final ComodatoService comodatoService;

    AlertasDataLoader(ProductoService productoService, ComodatoService comodatoService) {
        this.productoService = productoService;
        this.comodatoService = comodatoService;
    }

    void load(Consumer<AlertasResult> onSuccess, Consumer<Exception> onError) {
        DialogUtil.runAsync(
            () -> {
                comodatoService.actualizarVencidos();
                return new AlertasResult(
                    productoService.getAgotados(),
                    productoService.getBajoStock(),
                    productoService.getVencidosProximos(30),
                    productoService.getProximasRevisiones(30),
                    comodatoService.getVencidos(),
                    AlertasPatrimonialesSection.pendientes(productoService.getAll())
                );
            },
            onSuccess,
            onError
        );
    }
}
