package com.sibim.controller;

import com.sibim.service.ReporteService;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;

public class ReportesController {

    @FXML private DatePicker desdeField;
    @FXML private DatePicker hastaField;
    @FXML private ProgressIndicator spinner;

    private final ReporteService reporteService = new ReporteService();

    @FXML
    public void initialize() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        onReportMes(); // default to current month
    }

    @FXML private void onReportHoy() {
        LocalDate hoy = LocalDate.now();
        desdeField.setValue(hoy); hastaField.setValue(hoy);
    }
    @FXML private void onReportSemana() {
        LocalDate hoy = LocalDate.now();
        desdeField.setValue(hoy.minusDays(6)); hastaField.setValue(hoy);
    }
    @FXML private void onReportMes() {
        LocalDate hoy = LocalDate.now();
        desdeField.setValue(hoy.withDayOfMonth(1)); hastaField.setValue(hoy);
    }
    @FXML private void onReportAnio() {
        LocalDate hoy = LocalDate.now();
        desdeField.setValue(hoy.withDayOfYear(1)); hastaField.setValue(hoy);
    }
    @FXML private void onReportTodo() {
        desdeField.setValue(null); hastaField.setValue(null);
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

    private LocalDate getDesde() { return desdeField.getValue(); }
    private LocalDate getHasta() { return hastaField.getValue(); }

    private boolean validarFechas() {
        LocalDate desde = desdeField.getValue();
        LocalDate hasta  = hastaField.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            NotificacionUtil.advertencia(spinner.getScene(),
                "La fecha inicial debe ser anterior a la fecha final");
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
                NotificacionUtil.exito(spinner.getScene(), "Reporte generado — " + file.getName());
                try { Desktop.getDesktop().open(file); }
                catch (Exception ignored) { /* file already notified */ }
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
