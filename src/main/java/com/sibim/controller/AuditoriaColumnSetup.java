package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.util.DialogUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;

/** Static column-setup helpers extracted from AuditoriaController.
 *  Package-private — only used by AuditoriaController. */
class AuditoriaColumnSetup {

    private AuditoriaColumnSetup() {}

    static void configure(
            TableView<AuditLog> table,
            TableColumn<AuditLog, String> colFecha,
            TableColumn<AuditLog, String> colEntidad,
            TableColumn<AuditLog, String> colNombre,
            TableColumn<AuditLog, String> colAccion,
            TableColumn<AuditLog, String> colUsuario,
            TableColumn<AuditLog, String> colDetalle,
            DateTimeFormatter fechaFmt,
            Preferences sticky) {

        colFecha.setCellValueFactory(c -> {
            if (c.getValue().getCreadoEn() == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(c.getValue().getCreadoEn().format(fechaFmt));
        });

        colEntidad.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntidad() != null ? c.getValue().getEntidad() : "—"));

        colNombre.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getEntidadNombre() != null ? c.getValue().getEntidadNombre() : "—"));
        colNombre.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });

        colAccion.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getAccion() != null ? c.getValue().getAccion() : "—"));
        colAccion.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("audit-pill-green", "audit-pill-red", "audit-pill-blue",
                                          "audit-pill-orange", "audit-pill-slate", "audit-pill-purple");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                String cls = switch (item.toLowerCase()) {
                    case "login"              -> "audit-pill-green";
                    case "logout"             -> "audit-pill-slate";
                    case "save", "edicion",
                         "edición", "alta"   -> "audit-pill-blue";
                    case "delete", "baja",
                         "login_fallido"      -> "audit-pill-red";
                    case "conteo"             -> "audit-pill-purple";
                    default                   -> "audit-pill-orange";
                };
                getStyleClass().add(cls);
            }
        });

        colUsuario.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getUsuarioNombre() != null ? c.getValue().getUsuarioNombre() : "—"));

        colDetalle.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getDetalle() != null ? c.getValue().getDetalle() : "—"));
        colDetalle.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            {
                tip.setWrapText(true);
                tip.setMaxWidth(400);
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });

        DialogUtil.persistTableSort(table, sticky, "sort");
        DialogUtil.persistColumnWidths(table, sticky, "colW");
    }
}
