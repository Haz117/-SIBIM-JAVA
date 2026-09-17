package com.sibim.controller;

import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Startup async checks — vencidos, préstamos y update checker.
 *  Extracted from MainController to keep initialize() focused. */
class MainStartupChecks {

    private static final Logger log = LoggerFactory.getLogger(MainStartupChecks.class);

    private final ProductoService productoService;
    private final PrestamoService prestamoService;

    MainStartupChecks(ProductoService productoService, PrestamoService prestamoService) {
        this.productoService = productoService;
        this.prestamoService = prestamoService;
    }

    void checkPrestamosOnStart(Scene scene) {
        DialogUtil.runAsync(
            () -> {
                int vencidos = prestamoService.getVencidos().size();
                int proximos = prestamoService.getProximosAVencer(3).size();
                return new int[]{vencidos, proximos};
            },
            counts -> {
                int vencidos = counts[0], proximos = counts[1];
                if (vencidos > 0)
                    NotificacionUtil.advertencia(scene,
                        vencidos + " préstamo(s) vencido(s) — revisa la sección Préstamos");
                else if (proximos > 0)
                    NotificacionUtil.advertencia(scene,
                        proximos + " préstamo(s) vencen en los próximos 3 días");
            },
            e -> log.debug("Startup préstamos check failed", e)
        );
    }

    void checkVencidosOnStart(Scene scene) {
        DialogUtil.runAsync(
            () -> productoService.getVencidosProximos(7),
            vencidos -> {
                if (!vencidos.isEmpty())
                    NotificacionUtil.advertencia(scene,
                        vencidos.size() + " bien(es) vence(n) en los próximos 7 días — revisa la sección Alertas");
            },
            e -> log.debug("Startup vencidos check failed", e)
        );
    }

    void checkForUpdate(Scene scene) {
        AppExecutor.submit(() -> {
            com.sibim.util.UpdateChecker.UpdateInfo info = com.sibim.util.UpdateChecker.checkForUpdate();
            if (info != null) {
                javafx.application.Platform.runLater(() ->
                    NotificacionUtil.exitoConAccion(scene,
                        "Nueva versión disponible: v" + info.latestVersion(),
                        "Ver actualización",
                        () -> {
                            try { java.awt.Desktop.getDesktop().browse(new java.net.URI(info.releaseUrl())); }
                            catch (Exception ex) { log.warn("No se pudo abrir el navegador", ex); }
                        }
                    )
                );
            }
        });
    }
}
