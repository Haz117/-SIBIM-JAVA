package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.DashboardService;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Panel ejecutivo para tesorería/presidencia: % del patrimonio ya
 *  depreciado y qué bienes cumplen su vida útil este año (candidatos a
 *  baja) — el Dashboard operativo no muestra esta vista de alto nivel. */
public final class PanelEjecutivoDialog {

    private PanelEjecutivoDialog() {}

    public static void show(Scene ownerScene, DashboardService dashboardService) {
        DialogUtil.runAsyncWithProgress(ownerScene, "Calculando panel ejecutivo…",
            dashboardService::calcularResumenEjecutivo,
            resumen -> build(ownerScene, resumen),
            ex -> NotificacionUtil.error(ownerScene, "No se pudo calcular el panel ejecutivo"));
    }

    private static void build(Scene ownerScene, DashboardService.ResumenEjecutivo r) {
        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(600);
        dlg.getDialogPane().setPrefHeight(560);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        VBox content = new VBox(0);
        HBox header = DialogUtil.gradientHeader("mdi2c-chart-donut", "Panel Ejecutivo",
            "Depreciación patrimonial y proyección de bajas",
            "#4C1D95", "#3730A3");
        content.getChildren().add(header);

        VBox body = new VBox(18);
        body.setPadding(new Insets(18, 22, 18, 22));

        // ── Resumen de depreciación ──
        GridPane stats = new GridPane();
        stats.setHgap(24); stats.setVgap(10);
        addStat(stats, 0, "Valor de compra total", FormatUtils.formatCurrency(r.valorCompraTotal()));
        addStat(stats, 1, "Valor en libros actual", FormatUtils.formatCurrency(r.valorDepreciadoTotal()));
        addStat(stats, 2, "Depreciación acumulada", String.format("%.1f%%", r.porcentajeDepreciado()));

        Label barLbl = new Label("Patrimonio depreciado vs. vigente");
        barLbl.getStyleClass().add("muted-sm");
        ProgressBar bar = new ProgressBar(r.porcentajeDepreciado() / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("toast-countdown");
        VBox barBox = new VBox(4, barLbl, bar);

        Label coverage = new Label(r.bienesConDatosDepreciacion() + " de " + r.totalBienes()
            + " bienes activos tienen datos completos de depreciación (fecha de adquisición y vida útil)"
            + (r.totalBienes() > r.bienesConDatosDepreciacion()
                ? " — el resto no se incluye en este cálculo." : "."));
        coverage.getStyleClass().add("muted-sm");
        coverage.setWrapText(true);

        VBox depreciacionSection = new VBox(12, stats, barBox, coverage);

        // ── Proyección de bajas ──
        Label finVidaTitle = new Label("BIENES QUE CUMPLEN SU VIDA ÚTIL ESTE AÑO ("
            + r.bienesFinVidaUtilEsteAnio() + ")");
        finVidaTitle.getStyleClass().add("dash-section-label");

        VBox finVidaList = new VBox(0);
        finVidaList.getStyleClass().add("timeline-list");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        List<Producto> candidatos = r.bienesFinVidaUtil();
        if (candidatos.isEmpty()) {
            Label empty = new Label("Ningún bien activo cumple su vida útil este año.");
            empty.getStyleClass().add("muted-sm");
            empty.setPadding(new Insets(8, 0, 0, 0));
            finVidaList.getChildren().add(empty);
        } else {
            for (Producto p : candidatos) {
                HBox row = new HBox(10);
                row.getStyleClass().add("timeline-row");
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8, 4, 8, 4));

                FontIcon ico = new FontIcon("mdi2a-alert-circle-outline");
                ico.setIconSize(16);
                ico.getStyleClass().add("timeline-icon");

                VBox details = new VBox(1);
                Label nombreLbl = new Label(p.getNombre() + (p.getCodigo() != null ? "  [" + p.getCodigo() + "]" : ""));
                nombreLbl.getStyleClass().add("timeline-detail");
                var finVidaUtil = p.getFechaAdquisicion().plusYears(p.getVidaUtilAnios());
                Label detLbl = new Label((p.getArea() != null ? p.getArea() : "—")
                    + "  ·  vida útil termina " + finVidaUtil.format(fmt));
                detLbl.getStyleClass().add("muted-sm");
                details.getChildren().addAll(nombreLbl, detLbl);
                HBox.setHgrow(details, Priority.ALWAYS);
                row.getChildren().addAll(ico, details);
                finVidaList.getChildren().add(row);
            }
        }

        body.getChildren().addAll(depreciacionSection, new javafx.scene.control.Separator(),
            finVidaTitle, finVidaList);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        content.getChildren().add(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        dlg.getDialogPane().setContent(content);
        com.sibim.util.AnimationUtils.staggeredFadeInUp(java.util.List.of(header, body), 260, 60);
        dlg.showAndWait();
    }

    private static void addStat(GridPane grid, int col, String label, String value) {
        Label lblLabel = new Label(label);
        lblLabel.getStyleClass().add("muted-sm");
        Label lblValue = new Label(value);
        lblValue.getStyleClass().add("transfer-cel-title");
        VBox box = new VBox(2, lblValue, lblLabel);
        grid.add(box, col, 0);
    }
}
