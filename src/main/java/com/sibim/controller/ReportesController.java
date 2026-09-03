package com.sibim.controller;

import com.sibim.repository.ProductoRepository;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

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
    @FXML private BarChart<String, Number> areaChart;
    @FXML private CategoryAxis  chartXAxis;
    @FXML private NumberAxis    chartYAxis;
    @FXML private ProgressIndicator chartSpinner;

    private final ReporteService    reporteService  = new ReporteService();
    private final ProductoRepository productoRepo   = new ProductoRepository();
    private boolean updatingFromPreset = false;

    @FXML
    public void initialize() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        desdeField.setConverter(com.sibim.util.FormatUtils.datePickerConverter());
        hastaField.setConverter(com.sibim.util.FormatUtils.datePickerConverter());
        desdeField.valueProperty().addListener((o, a, b) -> { if (!updatingFromPreset) clearPresetActive(); });
        hastaField.valueProperty().addListener((o, a, b) -> { if (!updatingFromPreset) clearPresetActive(); });
        onReportMes(); // default to current month
        if (helpTiposReporte != null) DialogUtil.enableClickToShowTooltip(helpTiposReporte);

        if (periodCard != null) AnimationUtils.fadeInUp(periodCard, 300,  0);
        if (reportGrid != null) AnimationUtils.staggeredFadeInUp(reportGrid.getChildren(), 300, 70);
        loadAreaChart();
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

    private void loadAreaChart() {
        if (areaChart == null) return;
        if (chartSpinner != null) { chartSpinner.setVisible(true); chartSpinner.setManaged(true); }
        AppExecutor.submit(() -> {
            try {
                Map<String, Long> counts = productoRepo.findAll().stream()
                    .collect(Collectors.groupingBy(
                        p -> p.getArea() != null && !p.getArea().isBlank() ? p.getArea() : "Sin área",
                        Collectors.counting()));
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                counts.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Long>>comparingByValue().reversed())
                    .limit(12)
                    .forEach(e -> series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue())));
                Platform.runLater(() -> {
                    areaChart.getData().setAll(series);
                    if (chartSpinner != null) { chartSpinner.setVisible(false); chartSpinner.setManaged(false); }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (chartSpinner != null) { chartSpinner.setVisible(false); chartSpinner.setManaged(false); }
                });
            }
        });
    }

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
        Scene scene = spinner.getScene();
        if (scene == null) return;
        DialogUtil.runAsyncWithProgress(scene, "Generando reporte…",
            task::run,
            file -> {
                if (file == null) {
                    NotificacionUtil.advertencia(scene,
                        "Sin datos para el período seleccionado. Prueba con un rango diferente o elige «Todo el tiempo».");
                    return;
                }
                DialogUtil.showExportResultDialog(scene, file);
            },
            e -> NotificacionUtil.errorConAccion(scene,
                "Error al generar el reporte", "Reintentar", () -> exportar(null, task))
        );
    }

    @FunctionalInterface
    interface ExportTask {
        File run() throws Exception;
    }
}
