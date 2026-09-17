package com.sibim.controller;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;

/**
 * Owns the show/hide animation and enabled state of the "N bienes
 * seleccionados" bulk-action bar on the Bienes screen. Extracted from
 * {@link ProductosController} since it's self-contained UI state driven
 * purely by the current selection count.
 */
final class ProductosBulkBar {

    private final HBox bulkBar;
    private final Label lblBulkCount;
    private final Button btnBulkArea;
    private final Button btnBulkResguardante;
    private final Button btnBulkMarcarEtiquetado;
    private final Button btnComparar;
    private final BooleanSupplier canEdit;

    private boolean visible = false;

    ProductosBulkBar(
            HBox bulkBar,
            Label lblBulkCount,
            Button btnBulkArea,
            Button btnBulkResguardante,
            Button btnBulkMarcarEtiquetado,
            Button btnComparar,
            BooleanSupplier canEdit) {
        this.bulkBar = bulkBar;
        this.lblBulkCount = lblBulkCount;
        this.btnBulkArea = btnBulkArea;
        this.btnBulkResguardante = btnBulkResguardante;
        this.btnBulkMarcarEtiquetado = btnBulkMarcarEtiquetado;
        this.btnComparar = btnComparar;
        this.canEdit = canEdit;
    }

    void update(int n) {
        if (bulkBar == null) return;
        boolean show = n >= 2;
        boolean edit = canEdit.getAsBoolean();
        if (lblBulkCount != null && show)
            lblBulkCount.setText(n + " bienes seleccionados");
        if (btnBulkArea != null) { btnBulkArea.setVisible(edit); btnBulkArea.setManaged(edit); }
        if (btnBulkResguardante != null) { btnBulkResguardante.setVisible(edit); btnBulkResguardante.setManaged(edit); }
        if (btnBulkMarcarEtiquetado != null) { btnBulkMarcarEtiquetado.setVisible(edit); btnBulkMarcarEtiquetado.setManaged(edit); }
        if (btnComparar != null) { btnComparar.setVisible(n == 2); btnComparar.setManaged(n == 2); }
        if (show == visible) return;
        visible = show;
        if (show) {
            bulkBar.setOpacity(0);
            bulkBar.setTranslateY(12);
            bulkBar.setVisible(true);
            bulkBar.setManaged(true);
            FadeTransition ft = new FadeTransition(Duration.millis(180), bulkBar);
            ft.setToValue(1);
            TranslateTransition tt = new TranslateTransition(Duration.millis(180), bulkBar);
            tt.setToY(0);
            new ParallelTransition(ft, tt).play();
        } else {
            FadeTransition ft = new FadeTransition(Duration.millis(140), bulkBar);
            ft.setToValue(0);
            ft.setOnFinished(e -> { bulkBar.setVisible(false); bulkBar.setManaged(false); });
            ft.play();
        }
    }
}
