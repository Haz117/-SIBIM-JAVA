package com.sibim.controller.dialogs;

import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.ConteoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.session.SessionManager;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** A physical inventory count (toma de inventario): walks the user through
 *  every active bien in their scope, lets them enter what they actually
 *  counted, highlights discrepancies against the system's stock, and — on
 *  confirmation — persists the whole session (every item reviewed, matched
 *  or not — see {@link ConteoRepository}) and reconciles discrepancies by
 *  registering an AJUSTE movement for each one (so the correction is itself
 *  an audited movement, not a silent stock edit). */
public final class ConteoFisicoDialog {

    private ConteoFisicoDialog() {}

    private record Row(Producto producto, Spinner<Integer> contado) {}

    public static void show(List<Producto> productos, MovimientoService movimientoService, Runnable onReconciled) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(640);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("📋", "Conteo Físico de Inventario",
            "Captura lo contado y compáralo contra el sistema — " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            "#0891B2", "#0E7490");

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));
        List<Row> rows = new ArrayList<>();

        Label colHeaders = new Label("BIEN                                                  SISTEMA   CONTADO");
        colHeaders.getStyleClass().add("nav-section-label");

        for (Producto p : productos) {
            HBox row = new HBox(10);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(8, 12, 8, 12));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(1);
            Label nombre = new Label(p.getNombre() + "  [" + p.getCodigo() + "]");
            nombre.getStyleClass().add("dlg-detail-value");
            Label area = new Label(p.getArea());
            area.getStyleClass().add("muted-sm");
            info.getChildren().addAll(nombre, area);
            HBox.setHgrow(info, Priority.ALWAYS);

            Label sistema = new Label(String.valueOf(p.getStockActual()));
            sistema.getStyleClass().add("dlg-stock-val");
            sistema.setMinWidth(50);

            Spinner<Integer> contado = new Spinner<>(0, Integer.MAX_VALUE, p.getStockActual());
            contado.setEditable(true);
            contado.setPrefWidth(90);
            DialogUtil.commitOnFocusLoss(contado);

            Label delta = new Label();
            delta.setMinWidth(60);
            Runnable updateDelta = () -> {
                int diff = contado.getValue() - p.getStockActual();
                delta.setText(diff == 0 ? "✓ igual" : (diff > 0 ? "+" + diff : String.valueOf(diff)));
                delta.getStyleClass().removeAll("dlg-stock-new-ok", "dlg-stock-new-warn");
                delta.getStyleClass().add(diff == 0 ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
            };
            updateDelta.run();
            contado.valueProperty().addListener((o, a, b) -> updateDelta.run());

            row.getChildren().addAll(info, sistema, contado, delta);
            list.getChildren().add(row);
            rows.add(new Row(p, contado));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(380);
        scroll.getStyleClass().add("page-scroll");

        Label summary = new Label(productos.isEmpty()
            ? "No hay bienes activos en tu área para contar."
            : "Ajusta la columna \"Contado\" y presiona \"Finalizar conteo\" para guardarlo.");
        summary.getStyleClass().add("muted-sm");
        summary.setWrapText(true);

        Button btnFinalizar = new Button("✓  Finalizar conteo");
        btnFinalizar.getStyleClass().add("btn-primary");
        btnFinalizar.setDisable(productos.isEmpty());
        HBox actions = new HBox(10, summary, btnFinalizar);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(10, 4, 4, 4));
        HBox.setHgrow(summary, Priority.ALWAYS);

        btnFinalizar.setOnAction(e -> {
            List<Row> discrepancias = rows.stream()
                .filter(r -> r.contado().getValue() != r.producto().getStockActual())
                .toList();
            if (!discrepancias.isEmpty() && !ConfirmacionUtil.confirmar("Finalizar conteo",
                    "Se guardará el conteo y se registrarán " + discrepancias.size()
                        + " movimiento(s) de ajuste para igualar el sistema a lo contado.\n¿Continuar?"))
                return;

            btnFinalizar.setDisable(true);
            String motivo = "Conteo físico del " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            new Thread(() -> {
                int ok = 0, fail = 0;
                List<ConteoItem> items = new ArrayList<>();
                for (Row r : rows) {
                    boolean esDiscrepancia = r.contado().getValue() != r.producto().getStockActual();
                    boolean ajustado = false;
                    if (esDiscrepancia) {
                        try {
                            movimientoService.registrar(r.producto().getId(), TipoMovimiento.AJUSTE,
                                r.contado().getValue(), motivo, "Conteo físico");
                            ajustado = true;
                            ok++;
                        } catch (Exception ex) {
                            fail++;
                        }
                    }
                    ConteoItem item = new ConteoItem();
                    item.setProductoId(r.producto().getId());
                    item.setProductoNombre(r.producto().getNombre());
                    item.setArea(r.producto().getArea());
                    item.setStockSistema(r.producto().getStockActual());
                    item.setStockContado(r.contado().getValue());
                    item.setAjustado(ajustado);
                    items.add(item);
                }

                ConteoFisico conteo = new ConteoFisico();
                if (SessionManager.getCurrentUser() != null) {
                    conteo.setUsuarioId(SessionManager.getCurrentUser().getId());
                    conteo.setUsuarioNombre(SessionManager.getCurrentUser().getNombre());
                } else {
                    conteo.setUsuarioNombre("Sistema");
                }
                conteo.setTotalContados(items.size());
                conteo.setTotalDiscrepancias(discrepancias.size());
                conteo.setItems(items);

                boolean saved = true;
                try {
                    new ConteoRepository().guardar(conteo);
                } catch (Exception ex) {
                    saved = false;
                }

                int okFinal = ok, failFinal = fail;
                boolean savedFinal = saved;
                javafx.application.Platform.runLater(() -> {
                    String base = discrepancias.isEmpty()
                        ? "Conteo guardado — sin diferencias en " + items.size() + " bien(es)"
                        : okFinal + " ajuste(s) aplicado(s)" + (failFinal > 0 ? ", " + failFinal + " fallaron" : "");
                    if (!savedFinal) base += " (no se pudo guardar el registro del conteo)";
                    if (failFinal > 0 || !savedFinal) NotificacionUtil.advertencia(dialog.getDialogPane().getScene(), base);
                    else NotificacionUtil.exito(dialog.getDialogPane().getScene(), base);
                    if (onReconciled != null) onReconciled.run();
                    dialog.close();
                });
            }).start();
        });

        dialog.getDialogPane().setContent(new VBox(0, header, colHeaders, scroll, actions));
        dialog.showAndWait();
    }
}
