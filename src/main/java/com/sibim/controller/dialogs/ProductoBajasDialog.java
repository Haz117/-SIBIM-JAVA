package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/** Dialog listing all bienes dados de baja, with per-row reactivation.
 *  Extracted from ProductosController to keep it under 700 lines. */
public final class ProductoBajasDialog {

    private ProductoBajasDialog() {}

    /**
     * @param bajas           productos with {@code isDadoDeBaja() == true}
     * @param productoService service used to call {@code reactivar}
     * @param onReactivar     callback run after a successful reactivation (e.g. reload table)
     */
    public static void show(List<Producto> bajas, ProductoService productoService, Runnable onReactivar) {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(560);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2d-delete-circle-outline", "Bienes Dados de Baja",
            "Fuera del inventario activo — su historial se conserva",
            "#EF4444", "#B91C1C");

        VBox list = new VBox(8);
        list.setPadding(new Insets(4, 4, 4, 4));
        if (bajas.isEmpty()) {
            Label empty = new Label("No hay bienes dados de baja");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
        for (Producto p : bajas) {
            HBox row = new HBox(12);
            row.getStyleClass().add("dlg-detail-header");
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            VBox info = new VBox(2);
            Label nombre = new Label(p.getNombre() + "  [" + p.getCodigo() + "]");
            nombre.getStyleClass().add("dlg-detail-name");
            Label detalle = new Label(p.getArea() + " · baja: " + FormatUtils.formatDate(p.getFechaBaja())
                + (p.getMotivoBaja() != null && !p.getMotivoBaja().isBlank() ? " · " + p.getMotivoBaja() : ""));
            detalle.getStyleClass().add("muted-sm");
            detalle.setWrapText(true);
            info.getChildren().addAll(nombre, detalle);
            HBox.setHgrow(info, Priority.ALWAYS);

            Button btnReactivar = new Button("Reactivar");
            btnReactivar.setGraphic(new FontIcon("mdi2r-restore"));
            btnReactivar.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
            btnReactivar.getStyleClass().add("btn-secondary");
            btnReactivar.setOnAction(e -> {
                DialogUtil.runAsync(
                    () -> productoService.reactivar(p.getId()),
                    () -> {
                        list.getChildren().remove(row);
                        onReactivar.run();
                        NotificacionUtil.exito(dialog.getDialogPane().getScene(),
                            "Bien \"" + p.getNombre() + "\" reactivado");
                    },
                    e2 -> NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se pudo reactivar el bien")
                );
            });
            row.getChildren().addAll(info, btnReactivar);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        // Was "page-scroll" (the app's full-page gray background) — inside a
        // white dialog card that read as a mismatched gray panel bolted on.
        scroll.getStyleClass().add("dlg-tabs-scroll");

        if (!list.getChildren().isEmpty())
            AnimationUtils.staggeredFadeInUp(new java.util.ArrayList<>(list.getChildren()), 240, 40);
        dialog.getDialogPane().setContent(new VBox(0, header, scroll));
        dialog.showAndWait();
    }
}
