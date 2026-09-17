package com.sibim.controller;

import com.sibim.model.AuditLog;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Single-entry detail dialog for an audit log row — extracted from
 *  AuditoriaController since it's self-contained beyond the entry itself. */
final class AuditoriaDetailDialog {

    private AuditoriaDetailDialog() {}

    static void show(AuditLog entry, DateTimeFormatter fechaFmt) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(480);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader(
            "mdi2m-magnify-scan", "Detalle del registro",
            entry.getEntidad() != null ? entry.getEntidad().toUpperCase() : "AUDITORÍA",
            "#4338CA", "#3730A3");

        GridPane g = new GridPane();
        g.setHgap(16); g.setVgap(8);
        g.setPadding(new Insets(16, 22, 16, 22));

        String[][] rows = {
            { "Fecha",    entry.getCreadoEn() != null ? entry.getCreadoEn().format(fechaFmt) : "—" },
            { "Entidad",  entry.getEntidad()       != null ? entry.getEntidad()       : "—" },
            { "Elemento", entry.getEntidadNombre() != null ? entry.getEntidadNombre() : "—" },
            { "Acción",   entry.getAccion()        != null ? entry.getAccion()        : "—" },
            { "Usuario",  entry.getUsuarioNombre() != null ? entry.getUsuarioNombre() : "—" },
        };
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("dlg-detail-label"); k.setMinWidth(90);
            Label v = new Label(rows[i][1]);
            v.getStyleClass().add("dlg-detail-value");
            g.add(k, 0, i); g.add(v, 1, i);
        }
        // Detalle field — may be long, use a TextArea
        if (entry.getDetalle() != null && !entry.getDetalle().isBlank()) {
            Label kDet = new Label("Detalle");
            kDet.getStyleClass().add("dlg-detail-label"); kDet.setMinWidth(90);
            TextArea ta = new TextArea(entry.getDetalle());
            ta.setEditable(false); ta.setWrapText(true); ta.setPrefRowCount(4);
            ta.getStyleClass().add("audit-detail-area");
            g.add(kDet, 0, rows.length); g.add(ta, 1, rows.length);
            GridPane.setHgrow(ta, Priority.ALWAYS);
        }

        VBox content = new VBox(0, header, g);
        dlg.getDialogPane().setContent(content);
        AnimationUtils.staggeredFadeInUp(List.of(header, g), 260, 70);
        dlg.showAndWait();
    }
}
