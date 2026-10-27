package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;

import java.util.List;
import com.sibim.util.FormatUtils;

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

    // Transferir en lote is Transferir arrastrando with the selection already chosen
    // (ProductosController#onTransferir): it takes the bienes out of their resguardo,
    // asks the motivo and can be undone, which a plain list of áreas did not.

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
        DialogUtil.conEncabezado(dlg, "mdi2b-badge-account-outline");
        dlg.showAndWait().map(String::trim).filter(s -> !s.isBlank()).ifPresent(nombre -> {
            table.lookupAll(".table-row-cell:selected")
                 .forEach(r -> AnimationUtils.flashClass(r, "row-success", 400));
            DialogUtil.runAsyncWithProgress(scene, "Actualizando resguardante…",
                () -> porCadaBien(sel, p -> { p.setResguardante(nombre); service.save(p); }),
                r -> {
                    onSuccess.run();
                    if (r.ok() > 0) NotificacionUtil.exito(scene, FormatUtils.plural(r.ok(), "bien asignado", "bienes asignados") + " a \"" + nombre + "\"");
                    avisarFallidos(scene, r, "actualizar");
                },
                e -> NotificacionUtil.errorConAccion(scene,
                        "No se pudo cambiar el resguardante", "Reintentar", onRetry)
            );
        });
    }

    /** Outcome of a bulk action applied bien by bien. */
    private record Resultado(int ok, List<String> errores) {}

    @FunctionalInterface
    private interface AccionPorBien { void aplicar(Producto p) throws Exception; }

    /** One bien that can't be processed (no access, changed by someone else,
     *  already in that área...) doesn't stop the others — and since the list
     *  is reloaded afterwards, "Reintentar" never applies twice what already
     *  went through, as stopping at the first error used to invite. */
    private static Resultado porCadaBien(List<Producto> bienes, AccionPorBien accion) {
        int ok = 0;
        List<String> errores = new java.util.ArrayList<>();
        for (Producto p : bienes) {
            try { accion.aplicar(p); ok++; }
            catch (Exception ex) { errores.add(p.getNombre() + ": " + ex.getMessage()); }
        }
        return new Resultado(ok, errores);
    }

    private static void avisarFallidos(Scene scene, Resultado r, String verbo) {
        if (r.errores().isEmpty()) return;
        NotificacionUtil.advertencia(scene, "No se pudo " + verbo + " " + FormatUtils.plural(r.errores().size(), "bien", "bienes") + ". "
            + r.errores().get(0) + (r.errores().size() > 1 ? " (y otros)" : ""));
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
        marcarEtiquetados(sel.stream().map(Producto::getId).toList(), scene, service, onSuccess, onRetry);
    }

    /**
     * Right after printing labels: offers to mark the printed bienes that aren't
     * yet as etiquetados, so "Sin etiquetar" in Alertas goes down as the labels
     * go on, without a separate step. Asks first because printing is not sticking
     * them on. Only Patrimonio (admin) marks; nothing is asked when all of them
     * already are.
     */
    static void ofrecerMarcarEtiquetados(
            List<Producto> impresos, Scene scene, ProductoService service, Runnable onSuccess) {
        if (!SessionManager.isAdmin()) return;
        List<String> ids = impresos.stream().filter(p -> !p.isEtiquetado()).map(Producto::getId).toList();
        if (ids.isEmpty()) return;
        String cuales = ids.size() == 1 ? "este bien" : "estos " + ids.size() + " bienes";
        if (!ConfirmacionUtil.confirmar("¿Marcar como etiquetados?",
                "Cuando las etiquetas de " + cuales + " ya estén pegadas, márcalos como etiquetados "
                + "para que dejen de contar en Alertas como \"Sin etiquetar\".\n\n"
                + "Si todavía no se pegan, puedes hacerlo después desde Bienes › Marcar como etiquetado.")) return;
        marcarEtiquetados(ids, scene, service, onSuccess,
            () -> ofrecerMarcarEtiquetados(impresos, scene, service, onSuccess));
    }

    private static void marcarEtiquetados(List<String> ids, Scene scene, ProductoService service,
                                          Runnable onSuccess, Runnable onRetry) {
        DialogUtil.runAsyncWithProgress(scene, "Actualizando etiquetado…",
            () -> { service.marcarEtiquetado(ids, true); return ids.size(); },
            count -> {
                onSuccess.run();
                NotificacionUtil.exito(scene, FormatUtils.plural(count, "bien marcado", "bienes marcados") + " como etiquetados");
            },
            e -> NotificacionUtil.errorConAccion(scene,
                    "No se pudo actualizar el etiquetado", "Reintentar", onRetry)
        );
    }
}
