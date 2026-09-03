package com.sibim.controller.dialogs;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;

public final class MovimientoTimelineDialog {

    private MovimientoTimelineDialog() {}

    public static void show(Producto p, Scene ownerScene, MovimientoService movimientoService) {
        DialogUtil.runAsyncWithProgress(ownerScene, "Cargando historial…",
            () -> movimientoService.getByProducto(p.getId()),
            movs -> {
                Dialog<ButtonType> dlg = new Dialog<>();
                DialogUtil.applyOwner(dlg);
                dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                dlg.getDialogPane().setPrefWidth(560);
                dlg.getDialogPane().setPrefHeight(520);
                DialogUtil.applyStylesheet(dlg.getDialogPane());

                VBox content = new VBox(0);
                content.getStyleClass().add("timeline-root");
                HBox header = DialogUtil.gradientHeader(
                    "mdi2h-history", "Historial de movimientos",
                    p.getNombre() + "  ·  " + movs.size() + " registro(s)",
                    "#475569", "#334155");
                content.getChildren().add(header);

                ScrollPane scroll = new ScrollPane();
                scroll.setFitToWidth(true);
                scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
                VBox list = new VBox(0);
                list.getStyleClass().add("timeline-list");
                list.setPadding(new Insets(8, 16, 16, 16));

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                if (movs.isEmpty()) {
                    Label empty = new Label("Sin movimientos registrados");
                    empty.getStyleClass().add("muted-sm");
                    empty.setPadding(new Insets(24, 0, 0, 0));
                    list.getChildren().add(empty);
                } else {
                    for (Movimiento m : movs.stream()
                            .sorted((a, b) -> b.getCreadoEn().compareTo(a.getCreadoEn())).toList()) {
                        String iconLit = switch (m.getTipo()) {
                            case ENTRADA       -> "mdi2a-arrow-down-circle-outline";
                            case SALIDA        -> "mdi2a-arrow-up-circle-outline";
                            case AJUSTE        -> "mdi2a-adjust";
                            case TRANSFERENCIA -> "mdi2s-swap-horizontal";
                            default            -> "mdi2c-circle-outline";
                        };
                        String badgeClass = switch (m.getTipo()) {
                            case ENTRADA       -> "audit-pill-green";
                            case SALIDA        -> "audit-pill-red";
                            case AJUSTE        -> "audit-pill-blue";
                            case TRANSFERENCIA -> "audit-pill-purple";
                            default            -> "audit-pill-orange";
                        };
                        HBox row = new HBox(10);
                        row.getStyleClass().add("timeline-row");
                        row.setAlignment(Pos.CENTER_LEFT);
                        row.setPadding(new Insets(8, 4, 8, 4));

                        FontIcon ico = new FontIcon(iconLit);
                        ico.setIconSize(16);
                        ico.getStyleClass().add("timeline-icon");

                        Label tipo = new Label(m.getTipo().getEtiqueta());
                        tipo.getStyleClass().addAll("audit-pill", badgeClass);
                        tipo.setMinWidth(90);

                        VBox details = new VBox(1);
                        Label fechaLbl = new Label(
                            m.getCreadoEn() != null ? m.getCreadoEn().format(fmt) : "—");
                        fechaLbl.getStyleClass().add("muted-sm");
                        Label detLbl = new Label(
                            (m.getCantidad() > 0 ? "+" : "") + m.getCantidad()
                            + "  →  stock: " + m.getStockAnterior() + " → " + m.getStockNuevo()
                            + (m.getMotivo() != null && !m.getMotivo().isBlank() ? "  ·  " + m.getMotivo() : "")
                            + "  ·  " + (m.getUsuarioNombre() != null ? m.getUsuarioNombre() : "—"));
                        detLbl.getStyleClass().add("timeline-detail");
                        detLbl.setWrapText(true);
                        details.getChildren().addAll(fechaLbl, detLbl);
                        HBox.setHgrow(details, Priority.ALWAYS);
                        row.getChildren().addAll(ico, tipo, details);
                        list.getChildren().add(row);
                    }
                }
                scroll.setContent(list);
                scroll.setPrefHeight(430);
                content.getChildren().add(scroll);
                VBox.setVgrow(scroll, Priority.ALWAYS);
                dlg.getDialogPane().setContent(content);
                dlg.showAndWait();
            },
            ex -> NotificacionUtil.error(ownerScene, "No se pudo cargar el historial"));
    }
}
