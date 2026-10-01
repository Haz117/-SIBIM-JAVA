package com.sibim.controller;

import com.sibim.model.Producto;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/** Builds the right-click context menus for the three Alertas tables —
 *  kept out of {@link AlertasController} since it's pure wiring with no
 *  state of its own beyond what's passed in. */
class AlertasContextMenus {

    private AlertasContextMenus() {}

    static ContextMenu buildGarantias(
            TableView<Producto> table,
            Consumer<Producto> onVerDetalle,
            Consumer<Producto> onFicha) {

        ContextMenu cm = new ContextMenu();

        MenuItem miDetalle = new MenuItem("Ver detalle");
        miDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        miDetalle.setOnAction(e -> withSelection(table, onVerDetalle));

        MenuItem miFicha = new MenuItem("Imprimir ficha técnica");
        miFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        miFicha.setOnAction(e -> withSelection(table, onFicha));

        cm.getItems().addAll(miDetalle, new SeparatorMenuItem(), miFicha);
        return cm;
    }

    private static void withSelection(TableView<Producto> table, Consumer<Producto> action) {
        Producto sel = table.getSelectionModel().getSelectedItem();
        if (sel != null) action.accept(sel);
    }
}
