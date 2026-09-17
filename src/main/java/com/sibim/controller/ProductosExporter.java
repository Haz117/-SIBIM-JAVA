package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.ReporteService;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.QrUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Optional;

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

    // ── Single QR label preview/save ────────────────────────────────────────

    /** Shows a preview dialog for one bien's QR with an optional "Guardar PNG". */
    static void showQrDialog(Scene scene, Producto producto, Logger log) {
        Image qrImg = QrUtils.generateQr(producto.getCodigo(), 300);
        if (qrImg == null) { NotificacionUtil.error(scene, "No se pudo generar el QR"); return; }

        ButtonType savePng = new ButtonType("Guardar PNG", ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Código QR — " + producto.getNombre());
        dlg.initOwner(scene.getWindow());
        dlg.getDialogPane().getButtonTypes().addAll(savePng, ButtonType.CLOSE);
        dlg.getDialogPane().getStylesheets().addAll(scene.getStylesheets());

        ImageView iv = new ImageView(qrImg);
        iv.setFitWidth(260); iv.setFitHeight(260); iv.setPreserveRatio(true);
        Label lblCodigo = new Label(producto.getCodigo());
        lblCodigo.getStyleClass().add("dlg-detail-value");
        Label lblNombre = new Label(producto.getNombre());
        lblNombre.getStyleClass().add("muted-sm");

        VBox content = new VBox(8, iv, lblCodigo, lblNombre);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(content);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isPresent() && result.get() == savePng) {
            FileChooser fc = new FileChooser();
            fc.setTitle("Guardar código QR como imagen");
            fc.setInitialFileName("QR_" + producto.getCodigo() + ".png");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imagen PNG (*.png)", "*.png"));
            File dest = fc.showSaveDialog(scene.getWindow());
            if (dest != null) {
                try {
                    QrUtils.saveAsPng(qrImg, dest);
                    DialogUtil.showExportResultDialog(scene, dest);
                } catch (Exception e) {
                    log.error("Error guardando QR para {}", producto.getCodigo(), e);
                    NotificacionUtil.error(scene, "No se pudo guardar el QR");
                }
            }
        }
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
