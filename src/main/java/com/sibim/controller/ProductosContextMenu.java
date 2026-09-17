package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteService;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the right-click context menu for the Bienes table — kept out of
 * {@link ProductosController} since it's pure wiring with no state of its
 * own beyond what's passed in.
 */
final class ProductosContextMenu {

    private ProductosContextMenu() {}

    static ContextMenu build(
            TableView<Producto> table,
            boolean canEdit,
            ReporteService reporteService,
            MovimientoService movimientoService,
            Logger log,
            Consumer<Producto> showDetail,
            Consumer<Producto> showTimeline,
            Consumer<List<Producto>> exportEtiquetasQr,
            Consumer<List<Producto>> exportEtiquetaFisica,
            Runnable onEdit,
            Runnable onDelete) {

        ContextMenu cm = new ContextMenu();

        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showDetail.accept(sel);
        });
        cm.getItems().add(cmDetalle);

        MenuItem cmFicha = new MenuItem("Imprimir ficha técnica");
        cmFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmFicha.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DialogUtil.runAsyncWithProgress(table.getScene(), "Generando ficha…",
                    () -> reporteService.exportFichaTecnica(sel, movimientoService.getByProducto(sel.getId())),
                    file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                    ex -> { log.error("Error ficha técnica", ex); NotificacionUtil.error(table.getScene(), "No se pudo generar la ficha técnica"); });
            }
        });

        MenuItem cmHistorial = new MenuItem("Ver historial de movimientos");
        cmHistorial.setGraphic(new FontIcon("mdi2h-history"));
        cmHistorial.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showTimeline.accept(sel);
        });

        MenuItem cmEtiquetaQr = new MenuItem("Imprimir etiqueta QR");
        cmEtiquetaQr.setGraphic(new FontIcon("mdi2q-qrcode"));
        cmEtiquetaQr.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) exportEtiquetasQr.accept(List.of(sel));
        });

        MenuItem cmEtiquetaFisica = new MenuItem("Imprimir etiqueta física");
        cmEtiquetaFisica.setGraphic(new FontIcon("mdi2t-tag-outline"));
        cmEtiquetaFisica.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) exportEtiquetaFisica.accept(List.of(sel));
        });

        cm.getItems().add(new SeparatorMenuItem());
        cm.getItems().addAll(cmFicha, cmHistorial, cmEtiquetaQr, cmEtiquetaFisica);

        if (canEdit) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEditar = new MenuItem("Editar");
            cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
            MenuItem cmEliminar = new MenuItem("Dar de baja");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEditar.setOnAction(e -> onEdit.run());
            cmEliminar.setOnAction(e -> onDelete.run());
            cm.getItems().addAll(cmEditar, cmEliminar);
        }

        return cm;
    }
}
