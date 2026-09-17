package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.util.NotificacionUtil;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

/** Builds the right-click context menu for the Auditoría table — kept out
 *  of {@link AuditoriaController} since it's pure wiring with no state of
 *  its own beyond what's passed in. */
final class AuditoriaContextMenu {

    private AuditoriaContextMenu() {}

    static ContextMenu build(TableView<AuditLog> table, Consumer<AuditLog> onVerDetalle) {
        ContextMenu cm = new ContextMenu();

        MenuItem cmDetalle = new MenuItem("Ver detalle completo");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            AuditLog sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onVerDetalle.accept(sel);
        });

        MenuItem cmCopiar = new MenuItem("Copiar detalle");
        cmCopiar.setGraphic(new FontIcon("mdi2c-content-copy"));
        cmCopiar.setOnAction(e -> {
            AuditLog sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getDetalle() == null) return;
            ClipboardContent cc = new ClipboardContent();
            cc.putString(sel.getDetalle());
            Clipboard.getSystemClipboard().setContent(cc);
            Scene s = table.getScene();
            if (s != null) NotificacionUtil.exito(s, "Detalle copiado al portapapeles");
        });

        cm.getItems().addAll(cmDetalle, new SeparatorMenuItem(), cmCopiar);
        return cm;
    }
}
