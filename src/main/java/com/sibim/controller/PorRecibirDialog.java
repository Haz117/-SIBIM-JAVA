package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.service.MovimientoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/** Transfers already applied that the receiving área still has to confirm
 *  it physically has — same layout as {@link PendientesDialog}. */
class PorRecibirDialog {

    private final MovimientoService service;
    private final Runnable onDataRefresh;

    PorRecibirDialog(MovimientoService service, Runnable onDataRefresh) {
        this.service       = service;
        this.onDataRefresh = onDataRefresh;
    }

    void show(List<Movimiento> porRecibir) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2i-inbox-arrow-down-outline",
            "Bienes por recibir",
            "Transferencias hacia tu área: confirma cuando tengas el bien físicamente",
            AppColors.PURPLE, AppColors.PURPLE_D);

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));

        if (porRecibir.isEmpty()) {
            Label empty = new Label("No hay bienes por recibir");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }

        for (Movimiento m : porRecibir) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(3);
            Label titulo = new Label(m.getProductoNombre()
                + (m.getCodigoNuevo() != null ? "  [" + m.getCodigoNuevo() + "]" : ""));
            titulo.getStyleClass().add("dlg-detail-value");
            titulo.setWrapText(true);
            Label detalle = new Label(
                "De " + (m.getAreaOrigen() != null ? m.getAreaOrigen() : "—") + " → " + m.getAreaDestino()
                + " · " + FormatUtils.formatDateTime(m.getCreadoEn())
                + (m.getMotivo() != null && !m.getMotivo().isBlank() ? " · " + m.getMotivo() : ""));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            info.getChildren().addAll(titulo, detalle);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button btnRecibir = new Button("Confirmar recepción");
            btnRecibir.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
            btnRecibir.setContentDisplay(ContentDisplay.LEFT);
            btnRecibir.getStyleClass().add("btn-primary");
            btnRecibir.setMinWidth(Region.USE_PREF_SIZE);
            btnRecibir.setOnAction(e -> {
                if (!ConfirmacionUtil.confirmar("Confirmar recepción",
                        "¿Confirmas que " + m.getAreaDestino() + " ya tiene \"" + m.getProductoNombre()
                        + "\"?\n\nQuedará registrado con tu nombre y la fecha de hoy.")) return;
                btnRecibir.setDisable(true);
                DialogUtil.runAsync(
                    () -> service.confirmarRecepcion(m),
                    () -> {
                        list.getChildren().remove(row);
                        onDataRefresh.run();
                        NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                            "Recepción de \"" + m.getProductoNombre() + "\" confirmada");
                    },
                    ex -> {
                        btnRecibir.setDisable(false);
                        NotificacionUtil.error(dialog.getDialogPane().getScene(),
                            ex instanceof MovimientoService.ValidationException
                                ? ex.getMessage() : "No se pudo confirmar la recepción");
                    }
                );
            });

            row.getChildren().addAll(info, btnRecibir);
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
