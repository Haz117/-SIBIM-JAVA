package com.sibim.controller;

import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.LocalDate;

public class ReportesController {

    @FXML private VBox       periodCard;
    @FXML private GridPane   reportGrid;
    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private ProgressIndicator spinner;
    @FXML private Button btnPresetHoy;
    @FXML private Button btnPresetSemana;
    @FXML private Button btnPresetMes;
    @FXML private Button btnPresetAnio;
    @FXML private Button btnPresetTodo;
    @FXML private Label helpTiposReporte;

    private final ReporteService reporteService = new ReporteService();
    private boolean updatingFromPreset = false;

    @FXML
    public void initialize() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        desdeField.valueProperty().addListener((o, a, b) -> { if (!updatingFromPreset) clearPresetActive(); });
        hastaField.valueProperty().addListener((o, a, b) -> { if (!updatingFromPreset) clearPresetActive(); });
        onReportMes(); // default to current month
        if (helpTiposReporte != null) DialogUtil.enableClickToShowTooltip(helpTiposReporte);

        if (periodCard != null) AnimationUtils.fadeInUp(periodCard, 300,  0);
        if (reportGrid != null) AnimationUtils.staggeredFadeInUp(reportGrid.getChildren(), 300, 70);
    }

    private void setPresetActive(Button active) {
        for (Button b : new Button[]{btnPresetHoy, btnPresetSemana, btnPresetMes, btnPresetAnio, btnPresetTodo}) {
            if (b != null) b.getStyleClass().remove("btn-preset-active");
        }
        if (active != null) active.getStyleClass().add("btn-preset-active");
    }

    private void clearPresetActive() { setPresetActive(null); }

    private void applyPreset(Button source, LocalDate desde, LocalDate hasta) {
        updatingFromPreset = true;
        desdeField.setValue(desde);
        hastaField.setValue(hasta);
        updatingFromPreset = false;
        setPresetActive(source);
    }

    @FXML private void onReportHoy() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetHoy, hoy, hoy);
    }
    @FXML private void onReportSemana() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetSemana, hoy.minusDays(6), hoy);
    }
    @FXML private void onReportMes() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetMes, hoy.withDayOfMonth(1), hoy);
    }
    @FXML private void onReportAnio() {
        LocalDate hoy = LocalDate.now();
        applyPreset(btnPresetAnio, hoy.withDayOfYear(1), hoy);
    }
    @FXML private void onReportTodo() {
        applyPreset(btnPresetTodo, null, null);
    }

    // ─── Inventario ───
    @FXML private void onInventarioPdf(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportInventarioPdf(getDesde(), getHasta()));
    }
    @FXML private void onInventarioExcel(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportInventarioExcel(getDesde(), getHasta()));
    }
    @FXML private void onInventarioCsv(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportInventarioCsv(getDesde(), getHasta()));
    }

    // ─── Movimientos ───
    @FXML private void onMovimientosPdf(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportMovimientosPdf(getDesde(), getHasta()));
    }
    @FXML private void onMovimientosExcel(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportMovimientosExcel(getDesde(), getHasta()));
    }
    @FXML private void onMovimientosCsv(ActionEvent event) {
        if (!validarFechas()) return;
        exportar(event, () -> reporteService.exportMovimientosCsv(getDesde(), getHasta()));
    }

    // ─── Alertas ───
    @FXML private void onAlertasPdf(ActionEvent event)   { exportar(event, () -> reporteService.exportAlertasPdf()); }
    @FXML private void onAlertasExcel(ActionEvent event) { exportar(event, () -> reporteService.exportAlertasExcel()); }
    @FXML private void onAlertasCsv(ActionEvent event)   { exportar(event, () -> reporteService.exportAlertasCsv()); }

    // ─── Distribución ───
    @FXML private void onDistribucionPdf(ActionEvent event)   { exportar(event, () -> reporteService.exportDistribucionPdf()); }
    @FXML private void onDistribucionExcel(ActionEvent event) { exportar(event, () -> reporteService.exportDistribucionExcel()); }
    @FXML private void onDistribucionCsv(ActionEvent event)   { exportar(event, () -> reporteService.exportDistribucionCsv()); }

    private LocalDate getDesde() { return desdeField.getValue(); }
    private LocalDate getHasta() { return hastaField.getValue(); }

    private boolean validarFechas() {
        LocalDate desde = desdeField.getValue();
        LocalDate hasta  = hastaField.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            NotificacionUtil.advertencia(spinner.getScene(),
                "La fecha inicial debe ser anterior a la fecha final");
            AnimationUtils.shake(desdeField);
            return false;
        }
        return true;
    }

    private void exportar(ActionEvent event, ExportTask task) {
        Button source = event != null && event.getSource() instanceof Button b ? b : null;
        if (source != null) source.setDisable(true);
        spinner.setVisible(true);
        spinner.setManaged(true);
        DialogUtil.runAsync(
            task::run,
            file -> {
                if (source != null) source.setDisable(false);
                spinner.setVisible(false);
                spinner.setManaged(false);
                DialogUtil.showExportResultDialog(spinner.getScene(), file);
            },
            e -> {
                if (source != null) source.setDisable(false);
                spinner.setVisible(false);
                spinner.setManaged(false);
                NotificacionUtil.error(spinner.getScene(), "Error al generar el reporte");
            }
        );
    }

    @FunctionalInterface
    interface ExportTask {
        File run() throws Exception;
    }
}
