package com.sibim.controller.dialogs;

import com.sibim.db.offline.SyncService;
import com.sibim.db.offline.SyncService.OutboxEntry;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.EmptyStateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * Shows all permanently-discarded outbox rows so an admin can review what
 * failed to sync and why, and optionally clear the entries once reviewed.
 * "Discarded" rows are permanent failures: the server returned "not found",
 * or the user explicitly chose to discard a conflict. Without this dialog
 * those rows were silently lost with no user visibility.
 */
public final class OutboxErrorsDialog {

    private OutboxErrorsDialog() {}

    public static void show() {
        List<OutboxEntry> entries = SyncService.getDiscarded();

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Errores de sincronización offline");

        ButtonType cerrarType  = new ButtonType("Cerrar",  ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType limpiarType = new ButtonType("Limpiar todo", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(limpiarType, cerrarType);

        Button limpiarBtn = (Button) dialog.getDialogPane().lookupButton(limpiarType);
        limpiarBtn.setDisable(entries.isEmpty());
        limpiarBtn.getStyleClass().add("btn-danger");
        limpiarBtn.setGraphic(new FontIcon("mdi2d-delete-sweep-outline"));

        HBox header = DialogUtil.gradientHeader("mdi2s-sync-alert",
            "Errores de sincronización offline",
            entries.isEmpty()
                ? "Todos los cambios offline se sincronizaron correctamente"
                : entries.size() + " cambio(s) descartados permanentemente — no llegaron al servidor",
            AppColors.WARNING, AppColors.WARNING_D);

        VBox content = new VBox(12);
        content.setPadding(new Insets(4, 0, 4, 0));

        if (entries.isEmpty()) {
            content.getChildren().add(
                EmptyStateUtil.build("mdi2c-check-circle-outline",
                    "Sin errores de sincronización",
                    "Todos los cambios offline se sincronizaron correctamente"));
        } else {
            Label hint = new Label("Revisa el error de cada fila antes de limpiar — "
                + "una vez limpiados no se podrán recuperar.");
            hint.getStyleClass().add("muted-sm");
            hint.setWrapText(true);
            content.getChildren().add(hint);
            content.getChildren().add(buildTable(entries));
        }

        VBox root = new VBox(0, header, content);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(720);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        if (!entries.isEmpty()) AnimationUtils.fadeInUp(content, 250, 0);

        dialog.setResultConverter(bt -> bt);
        dialog.showAndWait().ifPresent(bt -> {
            if (bt == limpiarType) {
                boolean ok = ConfirmacionUtil.confirmar(
                    "¿Limpiar todos los errores?",
                    "Se eliminarán " + entries.size() + " registro(s) de la cola local. "
                    + "Esta acción no se puede deshacer.");
                if (ok) SyncService.clearDiscarded();
            }
        });
    }

    private static TableView<OutboxEntry> buildTable(List<OutboxEntry> entries) {
        TableView<OutboxEntry> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setPrefHeight(320);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<OutboxEntry, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setPrefWidth(110);
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tableLabel()));

        TableColumn<OutboxEntry, String> colOp = new TableColumn<>("Operación");
        colOp.setPrefWidth(110);
        colOp.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().operacion()));

        TableColumn<OutboxEntry, String> colEntidad = new TableColumn<>("Bien / Registro");
        colEntidad.setPrefWidth(160);
        colEntidad.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().entityLabel()));

        TableColumn<OutboxEntry, String> colError = new TableColumn<>("Error");
        colError.setPrefWidth(220);
        colError.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().error() != null ? c.getValue().error() : "—"));
        colError.setCellFactory(col -> {
            TableCell<OutboxEntry, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setText(null); setTooltip(null); return; }
                    setText(item);
                    setTooltip(new Tooltip(item));
                }
            };
            cell.setWrapText(false);
            return cell;
        });

        TableColumn<OutboxEntry, String> colFecha = new TableColumn<>("Capturado");
        colFecha.setPrefWidth(140);
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().createdAt() != null ? c.getValue().createdAt().replace("T", " ") : "—"));

        table.getColumns().addAll(colTipo, colOp, colEntidad, colError, colFecha);
        table.getItems().addAll(entries);
        table.setPlaceholder(EmptyStateUtil.build(
            "mdi2c-check-circle-outline", "Sin errores", ""));
        return table;
    }
}
