package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;

import java.util.List;

/**
 * Builds and shows the bulk-action dialogs for the Bienes (Productos) screen.
 *
 * All three static methods accept the selected items, the scene (for
 * notifications), the service, and callbacks for the success/failure paths.
 * They perform no @FXML access themselves — that lives in
 * {@link ProductosController} which calls these helpers from its thin
 * @FXML handler stubs.
 */
final class ProductosBulkDialog {

    private ProductosBulkDialog() {}

    // ── Transferir en lote ──────────────────────────────────────────────────

    /**
     * Shows a ChoiceDialog with all known areas and registers a TRANSFERENCIA
     * movement for every selected bien not already there — the same path as
     * a single transfer from Movimientos, so an admin's transfer applies at
     * once (with a new código for the destination área) and anyone else's
     * waits for approval. Rewriting {@code area} directly would skip the
     * approval, the código and the movement history.
     *
     * @param sel        the selected products (must be >= 2)
     * @param scene      scene used for theming, notifications, and progress
     * @param service    MovimientoService instance from the controller
     * @param table      table whose selected rows get a flash animation
     * @param onSuccess  called after all transfers were registered
     * @param onRetry    called when the user clicks "Reintentar" on error
     */
    static void showCambiarArea(
            List<Producto> sel,
            Scene scene,
            MovimientoService service,
            TableView<Producto> table,
            Runnable onSuccess,
            Runnable onRetry) {

        var areaNames = new java.util.ArrayList<>(Areas.getAllAreaNames());
        ChoiceDialog<String> dlg = new ChoiceDialog<>(areaNames.get(0), areaNames);
        dlg.setTitle("Transferir bienes");
        dlg.setHeaderText("Transferir " + sel.size() + " bienes seleccionados a otra área"
            + (SessionManager.isAdmin() ? "" : "\n(quedarán pendientes hasta que un administrador las apruebe)"));
        dlg.setContentText("Área destino:");
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait().ifPresent(area -> {
            List<Producto> aMover = sel.stream().filter(p -> !area.equals(p.getArea())).toList();
            if (aMover.isEmpty()) {
                NotificacionUtil.info(scene, "Los bienes seleccionados ya están en \"" + area + "\"");
                return;
            }
            table.lookupAll(".table-row-cell:selected")
                 .forEach(r -> AnimationUtils.flashClass(r, "row-success", 400));
            DialogUtil.runAsyncWithProgress(scene, "Registrando transferencias…",
                () -> {
                    for (Producto p : aMover) {
                        service.registrar(p.getId(), TipoMovimiento.TRANSFERENCIA,
                            Math.max(1, p.getStockActual()), "Transferencia en lote", null, area);
                    }
                    return aMover.size();
                },
                count -> {
                    onSuccess.run();
                    NotificacionUtil.exito(scene, SessionManager.isAdmin()
                        ? count + " bien(es) transferidos a \"" + area + "\""
                        : count + " solicitud(es) de transferencia a \"" + area + "\" pendientes de aprobación");
                },
                e -> NotificacionUtil.errorConAccion(scene,
                        "No se pudo registrar la transferencia: " + e.getMessage(), "Reintentar", onRetry)
            );
        });
    }

    // ── Cambiar resguardante en lote ────────────────────────────────────────

    /**
     * Shows a TextInputDialog for a new resguardante name.  On confirmation
     * saves every product and calls {@code onSuccess}.
     */
    static void showCambiarResguardante(
            List<Producto> sel,
            Scene scene,
            ProductoService service,
            TableView<Producto> table,
            Runnable onSuccess,
            Runnable onRetry) {

        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("Cambiar resguardante");
        dlg.setHeaderText("Nuevo resguardante para " + sel.size() + " bienes seleccionados");
        dlg.setContentText("Nombre:");
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.showAndWait().map(String::trim).filter(s -> !s.isBlank()).ifPresent(nombre -> {
            table.lookupAll(".table-row-cell:selected")
                 .forEach(r -> AnimationUtils.flashClass(r, "row-success", 400));
            DialogUtil.runAsyncWithProgress(scene, "Actualizando resguardante…",
                () -> {
                    for (Producto p : sel) { p.setResguardante(nombre); service.save(p); }
                    return sel.size();
                },
                count -> {
                    onSuccess.run();
                    NotificacionUtil.exito(scene, count + " bien(es) asignados a \"" + nombre + "\"");
                },
                e -> NotificacionUtil.errorConAccion(scene,
                        "No se pudo cambiar el resguardante", "Reintentar", onRetry)
            );
        });
    }

    // ── Marcar como etiquetado en lote ──────────────────────────────────────

    /**
     * Asks for confirmation (describing how many are already labelled), then
     * bulk-marks all selected products as etiquetado=true and calls
     * {@code onSuccess}.
     */
    static void showMarcarEtiquetado(
            List<Producto> sel,
            Scene scene,
            ProductoService service,
            TableView<Producto> table,
            Runnable onSuccess,
            Runnable onRetry) {

        long yaEtiquetados = sel.stream().filter(Producto::isEtiquetado).count();
        long sinEtiq = sel.size() - yaEtiquetados;
        String msg = sinEtiq == sel.size()
            ? "¿Marcar " + sel.size() + " bienes como etiquetados?"
            : "De los " + sel.size() + " seleccionados, " + sinEtiq
                + " aún no están etiquetados. ¿Marcar todos como etiquetados?";
        if (!ConfirmacionUtil.confirmar("Marcar como etiquetado", msg)) return;
        table.lookupAll(".table-row-cell:selected")
             .forEach(r -> AnimationUtils.flashClass(r, "row-success", 400));
        List<String> ids = sel.stream().map(Producto::getId).toList();
        DialogUtil.runAsyncWithProgress(scene, "Actualizando etiquetado…",
            () -> { service.marcarEtiquetado(ids, true); return ids.size(); },
            count -> {
                onSuccess.run();
                NotificacionUtil.exito(scene, count + " bien(es) marcados como etiquetados");
            },
            e -> NotificacionUtil.errorConAccion(scene,
                    "No se pudo actualizar el etiquetado", "Reintentar", onRetry)
        );
    }
}
