package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

class AuditoriaDialog {

    void show(List<AuditLog> entries) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(600);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2h-history", "Historial de Auditoría",
            "Cambios en bienes, categorías y usuarios — últimos " + entries.size() + " registros",
            "#475569", "#334155");

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
            Label titulo = new Label(accionEtiqueta(a.getAccion()) + " — " + entidadEtiqueta(a.getEntidad())
                + (a.getEntidadNombre() != null ? " \"" + a.getEntidadNombre() + "\"" : ""));
            titulo.getStyleClass().add("dlg-detail-value");
            Label detalle = new Label((a.getDetalle() != null ? a.getDetalle() + " · " : "")
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

    private static String accionEtiqueta(String accion) {
        if (accion == null) return "—";
        return switch (accion) {
            case "crear"      -> "Creado";
            case "actualizar" -> "Actualizado";
            case "eliminar"   -> "Eliminado";
            case "baja"       -> "Dado de baja";
            case "reactivar"  -> "Reactivado";
            case "login"      -> "Inicio de sesión";
            case "logout"     -> "Cierre de sesión";
            default           -> accion;
        };
    }

    private static String entidadEtiqueta(String entidad) {
        if (entidad == null) return "—";
        return switch (entidad) {
            case "producto"  -> "Bien";
            case "categoria" -> "Categoría";
            case "usuario"   -> "Usuario";
            case "sesion"    -> "Sesión";
            default          -> entidad;
        };
    }
}
