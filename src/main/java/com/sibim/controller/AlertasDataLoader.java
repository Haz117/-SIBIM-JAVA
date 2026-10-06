package com.sibim.controller;

import com.sibim.model.Comodato;
import com.sibim.model.Producto;
import com.sibim.service.ComodatoService;
import com.sibim.service.ProductoService;
import com.sibim.util.DialogUtil;

import java.util.List;
import java.util.function.Consumer;

class AlertasDataLoader {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AlertasDataLoader.class);

    record AlertasResult(
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
                // Marking comodatos as vencidos is a write; the alerts are reads.
                // If only that write fails, the screen still has what to show.
                try { comodatoService.actualizarVencidos(); }
                catch (Exception e) { log.warn("No se pudo actualizar el estado de los comodatos vencidos: {}", e.getMessage()); }
                return new AlertasResult(
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
