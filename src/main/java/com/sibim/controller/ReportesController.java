package com.sibim.controller;

import com.sibim.service.ReporteService;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
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
    @FXML private void onInventarioPdf() {
        if (!validarFechas()) return;
        exportar(() -> reporteService.exportInventarioPdf(getDesde(), getHasta()));
    }
    @FXML private void onInventarioExcel() {
        if (!validarFechas()) return;
        exportar(() -> reporteService.exportInventarioExcel(getDesde(), getHasta()));
    }
    @FXML private void onInventarioCsv() {
        if (!validarFechas()) return;
        exportar(() -> reporteService.exportInventarioCsv(getDesde(), getHasta()));
    }

    // ─── Movimientos ───
    @FXML private void onMovimientosPdf() {
        if (!validarFechas()) return;
        exportar(() -> reporteService.exportMovimientosPdf(getDesde(), getHasta()));
    }
    @FXML private void onMovimientosExcel() {
        if (!validarFechas()) return;
        exportar(() -> reporteService.exportMovimientosExcel(getDesde(), getHasta()));
    }
    @FXML private void onMovimientosCsv() {
        if (!validarFechas()) return;
        exportar(() -> reporteService.exportMovimientosCsv(getDesde(), getHasta()));
    }

    // ─── Alertas ───
    @FXML private void onAlertasPdf()   { exportar(() -> reporteService.exportAlertasPdf()); }
    @FXML private void onAlertasExcel() { exportar(() -> reporteService.exportAlertasExcel()); }
    @FXML private void onAlertasCsv()   { exportar(() -> reporteService.exportAlertasCsv()); }

    // ─── Distribución ───
    @FXML private void onDistribucionPdf()   { exportar(() -> reporteService.exportDistribucionPdf()); }
    @FXML private void onDistribucionExcel() { exportar(() -> reporteService.exportDistribucionExcel()); }

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

    private void exportar(ExportTask task) {
        spinner.setVisible(true);
        spinner.setManaged(true);
        new Thread(() -> {
            try {
                File file = task.run();
                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    spinner.setManaged(false);
                    NotificacionUtil.exito(spinner.getScene(), "Reporte generado — " + file.getName());
                    try { Desktop.getDesktop().open(file); }
                    catch (Exception ignored) { /* file already notified */ }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    spinner.setVisible(false);
                    spinner.setManaged(false);
                    NotificacionUtil.error(spinner.getScene(), "Error al generar el reporte");
                });
            }
        }).start();
    }

    @FunctionalInterface
    interface ExportTask {
        File run() throws Exception;
    }
}
