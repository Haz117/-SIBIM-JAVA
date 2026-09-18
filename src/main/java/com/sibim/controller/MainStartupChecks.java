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

    /** Deletes leftover {@code sibim_*} export temp files from a previous session that
     *  crashed or was killed before its shutdown hook ({@code File.deleteOnExit()}, see
     *  ReporteService#tempFile) could run. Those PDFs/Excel files can hold patrimonial
     *  data (bienes, precios, custodios) and would otherwise sit in the OS temp dir
     *  indefinitely. Safe to always run: nothing in a NEW session holds a handle to a
     *  PREVIOUS session's export — and on Windows, deleting a file some other still-open
     *  process (e.g. a PDF viewer left open from last time) is still reading simply fails
     *  silently (File#delete() returns false), it doesn't throw. */
    void cleanupStaleTempFiles() {
        AppExecutor.submit(() -> {
            java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
            java.io.File[] stale = tmpDir.listFiles((dir, name) -> name.startsWith("sibim_"));
            if (stale == null) return;
            int deleted = 0;
            for (java.io.File f : stale) {
                if (f.delete()) deleted++;
            }
            if (deleted > 0)
                log.info("Limpieza de arranque: {} archivo(s) temporal(es) de sesiones anteriores eliminado(s)", deleted);
        });
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
