package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.ReporteService;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.scene.Scene;

import java.util.List;

/**
 * Static helpers that handle every "export" action on the Bienes screen.
 *
 * None of these methods need @FXML access beyond the scene (obtained from
 * {@code table.getScene()} by the caller) and the list of products to export.
 * Receiving {@code scene} as a parameter makes the logic testable and keeps
 * {@link ProductosController} free of repeated export boilerplate.
 */
final class ProductosExporter {

    private ProductosExporter() {}

    // ── Full-inventory exports ──────────────────────────────────────────────

    static void exportCsv(Scene scene, ReporteService reporteService, Runnable onRetry) {
        DialogUtil.runAsyncWithProgress(scene, "Generando CSV…",
            () -> reporteService.exportInventarioCsv(null, null),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.errorConAccion(scene,
                    "No se pudo exportar el CSV", "Reintentar", onRetry)
        );
    }

    static void exportExcel(Scene scene, ReporteService reporteService, Runnable onRetry) {
        DialogUtil.runAsyncWithProgress(scene, "Generando Excel…",
            () -> reporteService.exportInventarioExcel(null, null),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.errorConAccion(scene,
                    "No se pudo exportar el Excel", "Reintentar", onRetry)
        );
    }

    // ── Selection exports ───────────────────────────────────────────────────

    static void exportSeleccionCsv(
            Scene scene, List<Producto> seleccion, ReporteService reporteService, Runnable onRetry) {
        if (seleccion.isEmpty()) return;
        DialogUtil.runAsyncWithProgress(scene, "Generando CSV…",
            () -> reporteService.exportInventarioCsv(seleccion),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.errorConAccion(scene,
                    "No se pudo exportar el CSV", "Reintentar", onRetry)
        );
    }

    static void exportSeleccionExcel(
            Scene scene, List<Producto> seleccion, ReporteService reporteService, Runnable onRetry) {
        if (seleccion.isEmpty()) return;
        DialogUtil.runAsyncWithProgress(scene, "Generando Excel…",
            () -> reporteService.exportInventarioExcel(seleccion),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.errorConAccion(scene,
                    "No se pudo exportar el Excel", "Reintentar", onRetry)
        );
    }

    // ── QR label export ─────────────────────────────────────────────────────

    static void exportEtiquetasQr(
            Scene scene, List<Producto> productos, ReporteService reporteService) {
        DialogUtil.runAsyncWithProgress(scene, "Generando etiquetas QR…",
            () -> reporteService.exportEtiquetasQrPdf(productos),
            file -> {
                if (file == null) {
                    NotificacionUtil.advertencia(scene, "No se generaron etiquetas");
                    return;
                }
                DialogUtil.showExportResultDialog(scene, file);
            },
            e -> NotificacionUtil.error(scene, "No se pudo generar las etiquetas QR")
        );
    }

    // ── Resguardo PDF (bulk selection) ──────────────────────────────────────

    static void exportResguardoPdf(
            Scene scene, List<Producto> seleccionados, ReporteService reporteService) {
        if (seleccionados.isEmpty()) return;
        String resguardante = seleccionados.stream()
            .map(Producto::getResguardante)
            .filter(r -> r != null && !r.isBlank())
            .findFirst().orElse("Sin resguardante");
        String area = seleccionados.stream()
            .map(Producto::getArea)
            .filter(a -> a != null && !a.isBlank())
            .findFirst().orElse("");
        DialogUtil.runAsync(
            () -> reporteService.exportarResguardoPdf(resguardante, area, seleccionados),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "Error al generar el resguardo PDF")
        );
    }
}
