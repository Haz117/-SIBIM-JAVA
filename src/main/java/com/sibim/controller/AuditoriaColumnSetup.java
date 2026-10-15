package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.util.DialogUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
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
            new SimpleStringProperty(c.getValue().getEntidad() != null
                ? AuditoriaController.etiquetaEntidad(c.getValue().getEntidad()) : "—"));

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
        // A compact pill with the action in words ("Inicio de sesión", "Baja"),
        // not a colored block filling the whole cell with "login" or "baja".
        colAccion.setCellFactory(col -> new TableCell<>() {
            private final Label pill = new Label();
            { pill.getStyleClass().add("audit-pill"); setContentDisplay(ContentDisplay.GRAPHIC_ONLY); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || "—".equals(item)) { setGraphic(null); setText(null); return; }
                pill.setText(AuditoriaController.etiquetaAccion(item));
                pill.getStyleClass().removeAll("audit-pill-verde", "audit-pill-guinda", "audit-pill-dorado", "audit-pill-neutro");
                pill.getStyleClass().add(AuditoriaController.tonoAccion(item));
                setText(null);
                setGraphic(pill);
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
