package com.sibim.controller.dialogs;

import com.sibim.service.ErroresEquipoService;
import com.sibim.service.ErroresEquipoService.ErrorEquipo;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** For the administrator: the errors the áreas' PCs reported, newest first. */
public final class ErroresEquipoDialog {

    private ErroresEquipoDialog() {}

    private static final int LIMITE = 200;

    public static void show(Scene scene) {
        DialogUtil.runAsync(
            () -> new ErroresEquipoService().recientes(LIMITE),
            ErroresEquipoDialog::mostrar,
            e -> NotificacionUtil.error(scene, e instanceof IllegalStateException ? e.getMessage()
                : "No se pudieron cargar los errores de los equipos"));
    }

    private static void mostrar(List<ErrorEquipo> errores) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(760);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2a-alert-circle-outline",
            "Errores de los equipos",
            errores.isEmpty() ? "Ninguna computadora ha reportado errores"
                : errores.size() + " reporte(s) — se conservan " + 90 + " días",
            AppColors.DANGER, AppColors.DANGER_D);

        VBox list = new VBox(6);
        list.setPadding(new Insets(4));
        if (errores.isEmpty()) {
            Label vacio = new Label("Cuando SIBIM falle en alguna computadora, el error aparecerá aquí con el "
                + "equipo, la cuenta y la versión instalada.");
            vacio.getStyleClass().add("muted");
            vacio.setWrapText(true);
            list.getChildren().add(vacio);
        }
        for (ErrorEquipo e : errores) {
            Label detalle = new Label(
                (e.origen() != null ? e.origen() + "\n" : "") + (e.detalle() != null ? e.detalle() : "Sin detalle técnico"));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            TitledPane fila = new TitledPane(
                e.equipo() + (e.usuario() != null ? " · @" + e.usuario() : "")
                    + (e.version() != null ? " · v" + e.version() : "")
                    + " · " + FormatUtils.formatDateTime(e.creadoEn()) + "\n" + e.mensaje(),
                detalle);
            fila.setExpanded(false);
            fila.setAnimated(false);
            list.getChildren().add(fila);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(440);
        scroll.getStyleClass().add("dlg-tabs-scroll");

        VBox contenido = new VBox(0, header, scroll);
        if (!errores.isEmpty()) {
            Button borrar = new Button("Borrar todos");
            borrar.getStyleClass().add("btn-ghost");
            borrar.setOnAction(ev -> {
                if (!ConfirmacionUtil.confirmar("Borrar reportes", "¿Borrar todos los errores reportados?")) return;
                DialogUtil.runAsync(
                    () -> { new ErroresEquipoService().borrarTodos(); return null; },
                    ok -> dialog.close(),
                    ex -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudieron borrar"));
            });
            HBox pie = new HBox(borrar);
            pie.setPadding(new Insets(8, 6, 0, 6));
            contenido.getChildren().add(pie);
        }
        AnimationUtils.staggeredFadeInUp(List.of(header, scroll), 260, 70);
        dialog.getDialogPane().setContent(contenido);
        dialog.showAndWait();
    }
}
