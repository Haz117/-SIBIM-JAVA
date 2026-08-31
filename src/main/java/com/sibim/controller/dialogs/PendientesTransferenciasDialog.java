package com.sibim.controller.dialogs;

import com.sibim.model.Movimiento;
import com.sibim.service.MovimientoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/** Dialog that lists pending transfer movements for admin approval.
 *  Extracted from MovimientosController to keep it under 700 lines. */
public final class PendientesTransferenciasDialog {

    private PendientesTransferenciasDialog() {}

    public static void show(List<Movimiento> pendientes,
                            javafx.scene.Scene scene,
                            MovimientoService movimientoService,
                            Runnable onRefresh,
                            Runnable onLoadPendientesCount) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(660);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2t-timer-sand", "Transferencias Pendientes de Aprobación",
            "Solicitudes de traslado que requieren tu autorización",
            "#D97706", "#B45309");

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));

        if (pendientes.isEmpty()) {
            Label empty = new Label("No hay transferencias pendientes");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }

        for (Movimiento m : pendientes) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(3);
            Label titulo = new Label(
                (m.getProductoNombre() != null ? m.getProductoNombre() : "—") + "  ·  " + (m.getAreaOrigen() != null ? m.getAreaOrigen() : "—") + " → " + (m.getAreaDestino() != null ? m.getAreaDestino() : "—"));
            titulo.getStyleClass().add("dlg-detail-value");
            Label detalle = new Label(
                "Solicitado por " + m.getUsuarioNombre() + " · " + FormatUtils.formatDateTime(m.getCreadoEn())
                + (m.getMotivo() != null && !m.getMotivo().isBlank() ? " · " + m.getMotivo() : ""));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            info.getChildren().addAll(titulo, detalle);
            HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);

            Button btnAprobar  = new Button("Aprobar");
            Button btnRechazar = new Button("Rechazar");
            btnAprobar.setGraphic(new FontIcon("mdi2c-check-circle-outline"));
            btnRechazar.setGraphic(new FontIcon("mdi2c-close-circle-outline"));
            btnAprobar.setContentDisplay(ContentDisplay.LEFT);
            btnRechazar.setContentDisplay(ContentDisplay.LEFT);
            btnAprobar.getStyleClass().add("btn-primary");
            btnRechazar.getStyleClass().add("btn-danger");

            btnAprobar.setOnAction(e -> {
                btnAprobar.setDisable(true); btnRechazar.setDisable(true);
                DialogUtil.runAsync(
                    () -> movimientoService.aprobarTransferencia(m.getId()),
                    () -> {
                        list.getChildren().remove(row);
                        onRefresh.run(); onLoadPendientesCount.run();
                        NotificacionUtil.exitoTransferencia(dialog.getDialogPane().getScene(),
                            m.getProductoNombre(), m.getAreaOrigen(), m.getAreaDestino());
                    },
                    ex -> {
                        btnAprobar.setDisable(false); btnRechazar.setDisable(false);
                        NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo aprobar la transferencia");
                    }
                );
            });

            btnRechazar.setOnAction(e -> {
                if (!ConfirmacionUtil.confirmar("Rechazar transferencia",
                        "¿Rechazar la transferencia de \"" + m.getProductoNombre() + "\"?")) return;
                btnAprobar.setDisable(true); btnRechazar.setDisable(true);
                DialogUtil.runAsync(
                    () -> movimientoService.rechazarTransferencia(m.getId()),
                    () -> {
                        list.getChildren().remove(row);
                        onRefresh.run(); onLoadPendientesCount.run();
                        NotificacionUtil.info(dialog.getDialogPane().getScene(),
                            "Transferencia de \"" + m.getProductoNombre() + "\" rechazada");
                    },
                    ex -> {
                        btnAprobar.setDisable(false); btnRechazar.setDisable(false);
                        NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo rechazar la transferencia");
                    }
                );
            });

            HBox actions = new HBox(8, btnAprobar, btnRechazar);
            actions.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().addAll(info, actions);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }
}
