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

    /** @param onVerTodo filters the screen to the entry's bien (or resguardo…) */
    static ContextMenu build(TableView<AuditLog> table, Consumer<AuditLog> onVerDetalle, Consumer<AuditLog> onVerTodo) {
        ContextMenu cm = new ContextMenu();

        MenuItem cmDetalle = new MenuItem("Ver el detalle, con cada cambio");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            AuditLog sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onVerDetalle.accept(sel);
        });

        MenuItem cmTodo = new MenuItem("Ver todo lo de este bien");
        cmTodo.setGraphic(new FontIcon("mdi2h-history"));
        cmTodo.setOnAction(e -> {
            AuditLog sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) onVerTodo.accept(sel);
        });

        MenuItem cmCopiar = new MenuItem("Copiar");
        cmCopiar.setGraphic(new FontIcon("mdi2c-content-copy"));
        cmCopiar.setOnAction(e -> {
            AuditLog sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            ClipboardContent cc = new ClipboardContent();
            cc.putString(AuditoriaDetailDialog.textoParaCopiar(sel, AuditoriaColumnSetup.lectura(sel)));
            Clipboard.getSystemClipboard().setContent(cc);
            Scene s = table.getScene();
            if (s != null) NotificacionUtil.exito(s, "Copiado al portapapeles");
        });

        // «Ver todo lo de SSPCR/19», with the número of the row it was opened on.
        cm.setOnShowing(e -> {
            AuditLog sel = table.getSelectionModel().getSelectedItem();
            boolean hay = sel != null && sel.getEntidadId() != null && !sel.getEntidadId().isBlank();
            cmTodo.setVisible(hay);
            if (hay) {
                String numero = AuditoriaColumnSetup.lectura(sel).numero();
                cmTodo.setText("Ver todo lo de " + (numero != null ? numero
                    : EtiquetasAuditoria.entidadSingular(sel.getEntidad()).toLowerCase() + " " + sel.getEntidadNombre()));
            }
        });

        cm.getItems().addAll(cmDetalle, cmTodo, new SeparatorMenuItem(), cmCopiar);
        return cm;
    }
}
