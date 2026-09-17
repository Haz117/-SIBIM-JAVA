package com.sibim.controller;

import com.sibim.controller.dialogs.MovimientoDetailDialog;
import com.sibim.model.Movimiento;
import com.sibim.service.MovimientoService;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Builds the right-click context menu for the Movimientos table — kept out of
 * {@link MovimientosController} since it's pure wiring with no state of its
 * own beyond what's passed in.
 */
final class MovimientosContextMenu {

    private MovimientosContextMenu() {}

    static ContextMenu build(
            TableView<Movimiento> table,
            boolean canDelete,
            MovimientoService movimientoService,
            Runnable onDelete,
            Runnable onDataChanged) {

        ContextMenu cm = new ContextMenu();

        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Movimiento sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) MovimientoDetailDialog.show(sel, table.getScene(), movimientoService, onDataChanged);
        });
        cm.getItems().add(cmDetalle);

        if (canDelete) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEliminar = new MenuItem("Eliminar");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEliminar.setOnAction(e -> onDelete.run());
            cm.getItems().add(cmEliminar);
        }

        return cm;
    }
}
