package com.sibim.controller;

import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import java.io.File;
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
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            File[] stale = tmpDir.listFiles((dir, name) -> name.startsWith("sibim_"));
            if (stale == null) return;
            int deleted = 0;
            for (File f : stale) {
                if (f.delete()) deleted++;
            }
            if (deleted > 0)
                log.info("Limpieza de arranque: {} archivo(s) temporal(es) de sesiones anteriores eliminado(s)", deleted);
        });
    }

    /** A newer version published by Patrimonio (Configuración › Publicar
     *  actualización) is offered once per start; installing it downloads the
     *  installer from the database, runs it and closes SIBIM. */
    void checkForUpdate(Scene scene) {
        var servicio = new com.sibim.service.ActualizacionService();
        AppExecutor.submit(() -> {
            try {
                servicio.disponible().ifPresent(v -> Platform.runLater(() ->
                    NotificacionUtil.exitoConAccion(scene,
                        "Nueva versión disponible: v" + v.version(),
                        "Instalar",
                        () -> instalar(servicio, v, scene))));
            } catch (Exception e) {
                log.debug("No se pudo revisar si hay actualizaciones: {}", e.getMessage());
            }
        });
    }

    private void instalar(com.sibim.service.ActualizacionService servicio,
                          com.sibim.service.ActualizacionService.Version v, Scene scene) {
        String notas = v.notas() != null && !v.notas().isBlank() ? "\n\nNovedades:\n" + v.notas() : "";
        if (!com.sibim.util.ConfirmacionUtil.confirmar("Actualizar SIBIM",
                "Se descargará la versión " + v.version() + " (" + (v.tamano() / (1024 * 1024)) + " MB) y se abrirá "
                + "su instalador. SIBIM se cerrará: guarda lo que estés capturando." + notas)) return;
        File destino = new File(System.getProperty("java.io.tmpdir"), v.archivo());
        DialogUtil.runAsyncWithProgress(scene, "Descargando la versión " + v.version() + "…",
            () -> { servicio.descargar(v, destino, null); return destino; },
            instalador -> {
                try {
                    new ProcessBuilder(instalador.getAbsolutePath()).start();
                    log.info("Instalador de la versión {} iniciado; cerrando SIBIM", v.version());
                    Platform.exit();
                    System.exit(0);
                } catch (Exception e) {
                    log.error("No se pudo abrir el instalador {}", instalador, e);
                    NotificacionUtil.error(scene, "No se pudo abrir el instalador. Está en " + instalador.getAbsolutePath());
                }
            },
            e -> NotificacionUtil.error(scene, "No se pudo descargar la actualización: " + e.getMessage()));
    }
}
