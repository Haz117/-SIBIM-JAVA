package com.sibim.controller;

import com.sibim.model.ConteoFisico;
import com.sibim.model.ConteoItem;
import com.sibim.repository.ConteoRepository;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

class ConteosDialog {

    private final ConteoRepository conteoRepo;

    ConteosDialog(ConteoRepository conteoRepo) {
        this.conteoRepo = conteoRepo;
    }

    void show(List<ConteoFisico> conteos) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2c-clipboard-list-outline", "Historial de Conteos Físicos",
            "Tomas de inventario físico realizadas",
            AppColors.CYAN, AppColors.CYAN_D);

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));
        if (conteos.isEmpty()) {
            Label empty = new Label("No se ha registrado ningún conteo físico todavía");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (ConteoFisico c : conteos) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(9, 14, 9, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(2);
            Label titulo = new Label(FormatUtils.formatDateTime(c.getCreadoEn()) + " · " + c.getUsuarioNombre());
            titulo.getStyleClass().add("dlg-detail-value");
            Label detalle = new Label(c.getTotalContados() + " bien(es) revisado(s) · "
                + c.getTotalDiscrepancias() + " diferencia(s)");
            detalle.getStyleClass().add("muted-sm");
            info.getChildren().addAll(titulo, detalle);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button btnDetalle = new Button("Ver detalle");
            btnDetalle.getStyleClass().add("btn-secondary");
            btnDetalle.setOnAction(e -> DialogUtil.runAsync(
                () -> conteoRepo.findItems(c.getId()),
                items -> showDetalleDialog(c, items),
                ex -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo cargar el detalle")
            ));

            row.getChildren().addAll(info, btnDetalle);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }

    private void showDetalleDialog(ConteoFisico conteo, List<ConteoItem> items) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2m-magnify", "Detalle del Conteo",
            FormatUtils.formatDateTime(conteo.getCreadoEn()) + " · " + conteo.getUsuarioNombre(),
            AppColors.CYAN, AppColors.CYAN_D);

        // Column widths must match data row cells; -1 means grow
        HBox colHeaders = new HBox();
        colHeaders.setPadding(new Insets(6, 14, 4, 14));
        colHeaders.setSpacing(0);
        String[] colTitles = { "Bien", "Área", "Sistema", "Contado", "Delta", "Ajustado" };
        double[] colWidths  = { -1, 120, 60, 60, 60, 80 };
        for (int i = 0; i < colTitles.length; i++) {
            Label lbl = new Label(colTitles[i]);
            lbl.getStyleClass().add("col-header");
            if (colWidths[i] < 0) {
                HBox.setHgrow(lbl, Priority.ALWAYS);
                lbl.setMaxWidth(Double.MAX_VALUE);
            } else {
                lbl.setPrefWidth(colWidths[i]);
            }
            colHeaders.getChildren().add(lbl);
        }

        VBox rows = new VBox(4);
        rows.setPadding(new Insets(4));
        if (items.isEmpty()) {
            Label empty = new Label("Sin ítems registrados en este conteo");
            empty.getStyleClass().add("muted");
            rows.getChildren().add(empty);
        }
        for (ConteoItem item : items) {
            int delta = item.getStockContado() - item.getStockSistema();
            HBox row = new HBox();
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(8, 14, 8, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            Label lNombre = new Label(item.getProductoNombre());
            lNombre.setWrapText(false);
            lNombre.getStyleClass().add("dlg-detail-value");
            HBox.setHgrow(lNombre, Priority.ALWAYS);
            lNombre.setMaxWidth(Double.MAX_VALUE);

            Label lArea = new Label(item.getArea() != null ? item.getArea() : "—");
            lArea.getStyleClass().add("muted-sm");
            lArea.setPrefWidth(120);

            Label lSistema = new Label(String.valueOf(item.getStockSistema()));
            lSistema.getStyleClass().add("muted");
            lSistema.setPrefWidth(60);

            Label lContado = new Label(String.valueOf(item.getStockContado()));
            lContado.getStyleClass().add("muted");
            lContado.setPrefWidth(60);

            Label lDelta = new Label((delta > 0 ? "+" : "") + delta);
            lDelta.getStyleClass().add(delta == 0 ? "muted-sm" : (delta > 0 ? "field-hint-ok" : "field-hint-error"));
            lDelta.setPrefWidth(60);

            Label lAjustado = new Label(item.isAjustado() ? "✓ Sí" : "No");
            lAjustado.getStyleClass().add(item.isAjustado() ? "field-hint-ok" : "muted-sm");
            lAjustado.setPrefWidth(80);

            row.getChildren().addAll(lNombre, lArea, lSistema, lContado, lDelta, lAjustado);
            if (delta != 0) row.getStyleClass().add("row-highlight-amber");
            rows.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(380);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(List.of(header, colHeaders, scroll), 260, 60);
        dialog.getDialogPane().setContent(new VBox(0, header, colHeaders, scroll));
        dialog.showAndWait();
    }
}
