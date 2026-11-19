package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/** Configuración › «Ver actividad»: the last entries of the bitácora, as a list. */
class AuditoriaDialog {

    void show(List<AuditLog> entries) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2h-history", "Historial de Auditoría",
            "Cambios en bienes, categorías y usuarios — últimos " + entries.size() + " registros",
            AppColors.SLATE, AppColors.SLATE_D);

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));
        if (entries.isEmpty()) {
            Label empty = new Label("Sin actividad registrada todavía");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (AuditLog a : entries) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(9, 14, 9, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(2);
            // The same words and the same reading of the changes as the Auditoría screen.
            Label titulo = new Label(EtiquetasAuditoria.accion(a.getAccion()) + " — " + AuditoriaColumnSetup.sobre(a));
            titulo.getStyleClass().add("dlg-detail-value");
            String cambio = AuditoriaColumnSetup.lectura(a).resumen();
            Label detalle = new Label((cambio.isBlank() ? "" : cambio + " · ")
                + a.getUsuarioNombre() + " · " + FormatUtils.formatDateTime(a.getCreadoEn()));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            info.getChildren().addAll(titulo, detalle);
            HBox.setHgrow(info, Priority.ALWAYS);
            row.getChildren().add(info);
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
}
