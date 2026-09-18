package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.MovimientoService;
import com.sibim.util.AppColors;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bulk "reponer todos los agotados" dialog extracted from AlertasController —
 *  self-contained beyond the dependencies passed in. */
final class AlertasReponerDialog {

    private AlertasReponerDialog() {}

    static void showBulk(
            List<Producto> agotados,
            MovimientoService movimientoService,
            Scene scene,
            Logger log,
            Runnable onReloaded) {
        if (agotados.isEmpty()) return;

        Map<String, Spinner<Integer>> spinners = new LinkedHashMap<>();

        VBox rows = new VBox(6);
        rows.setPadding(new Insets(4, 8, 4, 8));
        for (Producto p : agotados) {
            Spinner<Integer> sp = new Spinner<>(1, 9_999, 1, 1);
            sp.setEditable(true);
            sp.setPrefWidth(90);

            Label nameLbl = new Label(p.getNombre());
            nameLbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(nameLbl, Priority.ALWAYS);

            Label codLbl = new Label(p.getCodigo());
            codLbl.getStyleClass().add("codigo-cell");

            Label stockLbl = new Label("Stock: 0");
            stockLbl.getStyleClass().add("stock-low");

            HBox row = new HBox(10, nameLbl, codLbl, stockLbl, sp);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("batch-reponer-row");
            rows.getChildren().add(row);
            spinners.put(p.getId(), sp);
        }

        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(agotados.size() * 52 + 16, 320));
        scroll.getStyleClass().add("edge-to-edge");

        HBox header = DialogUtil.gradientHeader(
            "mdi2p-package-variant",
            "Reponer todos los bienes agotados",
            agotados.size() + " bienes · ingresa la cantidad de entrada para cada uno",
            AppColors.INDIGO, AppColors.PRIMARY);

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.setTitle("Reposición masiva");
        dlg.getDialogPane().setPrefWidth(530);
        dlg.getDialogPane().setContent(new VBox(0, header, scroll));

        ButtonType btnConfirmar = new ButtonType("Registrar entradas", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(btnConfirmar, ButtonType.CANCEL);
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(btnConfirmar);
        okBtn.getStyleClass().add("btn-primary");

        dlg.showAndWait().ifPresent(result -> {
            if (result != btnConfirmar) return;

            List<Object[]> entradas = new ArrayList<>();
            for (Producto p : agotados) {
                Spinner<Integer> sp = spinners.get(p.getId());
                int qty = 1;
                try { qty = Math.max(1, Integer.parseInt(sp.getEditor().getText().trim())); }
                catch (NumberFormatException ignored) { qty = sp.getValue(); }
                entradas.add(new Object[]{p.getId(), p.getNombre(), qty});
            }

            AppExecutor.submit(() -> {
                int ok = 0, fail = 0;
                List<String> errores = new ArrayList<>();
                for (Object[] entry : entradas) {
                    try {
                        movimientoService.registrar((String) entry[0], TipoMovimiento.ENTRADA,
                            (int) entry[2], "Reposición masiva desde Alertas", null);
                        ok++;
                    } catch (Exception ex) {
                        fail++;
                        errores.add((String) entry[1]);
                        log.error("Error al reponer {}: {}", entry[1], ex.getMessage(), ex);
                    }
                }
                final int finalOk = ok, finalFail = fail;
                Platform.runLater(() -> {
                    if (scene == null) return;
                    if (finalFail == 0) {
                        NotificacionUtil.info(scene,
                            finalOk + (finalOk == 1 ? " entrada registrada" : " entradas registradas") + " correctamente");
                    } else if (finalOk > 0) {
                        NotificacionUtil.advertencia(scene,
                            finalOk + " registradas, " + finalFail + " con error");
                    } else {
                        NotificacionUtil.error(scene, "No se pudo registrar ninguna entrada");
                    }
                    onReloaded.run();
                });
            });
        });
    }
}
