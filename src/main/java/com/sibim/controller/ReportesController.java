package com.sibim.controller;

import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AccessibilityUtils;
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
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
    @FXML private Label helpPeriodo;
    @FXML private BarChart<String, Number> areaChart;
    @FXML private CategoryAxis  chartXAxis;
    @FXML private NumberAxis    chartYAxis;
    @FXML private ProgressIndicator chartSpinner;
    @FXML private VBox chartEmptyState;
    @FXML private BarChart<String, Number> categoriaChart;
    @FXML private CategoryAxis  categoriaChartXAxis;
    @FXML private NumberAxis    categoriaChartYAxis;
    @FXML private ProgressIndicator categoriaChartSpinner;
    @FXML private VBox categoriaChartEmptyState;

    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/reportes");

    private final ReporteService   reporteService  = ReporteService.getInstance();
    private final ProductoService  productoService = new ProductoService();
    private boolean updatingFromPreset = false;

    @FXML
    public void initialize() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        desdeField.setConverter(com.sibim.util.FormatUtils.datePickerConverter());
        hastaField.setConverter(com.sibim.util.FormatUtils.datePickerConverter());
        desdeField.valueProperty().addListener((o, a, b) -> { applyDateRangeStyle(); if (!updatingFromPreset) { clearPresetActive(); loadAreaChart(); loadCategoriaChart(); } });
        hastaField.valueProperty().addListener((o, a, b) -> { applyDateRangeStyle(); if (!updatingFromPreset) { clearPresetActive(); loadAreaChart(); loadCategoriaChart(); } });
        switch (STICKY.get("preset", "mes")) {
            case "hoy"    -> onReportHoy();
            case "semana" -> onReportSemana();
            case "anio"   -> onReportAnio();
            case "todo"   -> onReportTodo();
            default       -> onReportMes();
        }
        if (helpTiposReporte != null) DialogUtil.enableClickToShowTooltip(helpTiposReporte);
        if (helpPeriodo      != null) DialogUtil.enableClickToShowTooltip(helpPeriodo);

        if (periodCard != null) {
            AnimationUtils.fadeInUp(periodCard, 300, 0);
            periodCard.sceneProperty().addListener((obs, old, scene) -> {
                if (scene == null) return;
                // Alt+1-5 for date presets — Ctrl+1-5 is already claimed by
                // MainController for sidebar navigation (Ctrl+7 = Reportes).
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.ALT_DOWN), this::onReportHoy);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT2, KeyCombination.ALT_DOWN), this::onReportSemana);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT3, KeyCombination.ALT_DOWN), this::onReportMes);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT4, KeyCombination.ALT_DOWN), this::onReportAnio);
                scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DIGIT5, KeyCombination.ALT_DOWN), this::onReportTodo);
            });
        }
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
        String key = source == btnPresetHoy    ? "hoy"
                   : source == btnPresetSemana ? "semana"
                   : source == btnPresetAnio   ? "anio"
                   : source == btnPresetTodo   ? "todo"
                   : "mes";
        STICKY.put("preset", key);
        loadAreaChart();
        loadCategoriaChart();
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

    // ─── Bajas ───
    @FXML private void onBajasPdf(ActionEvent event)   { exportar(event, reporteService::exportBajasPdf); }
    @FXML private void onBajasExcel(ActionEvent event) { exportar(event, reporteService::exportBajasExcel); }
    @FXML private void onBajasCsv(ActionEvent event)   { exportar(event, reporteService::exportBajasCsv); }

    // ─── Auditoría consolidada ───
    @FXML private void onAuditoriaPdf(ActionEvent event) { exportar(event, () -> reporteService.exportAuditoriaPdf()); }

    // ─── Parque Vehicular V.6 ───
    @FXML private void onParqueVehicularPdf(ActionEvent event) {
        exportar(event, () -> {
            var vehiculos = productoService.getAll().stream()
                .filter(p -> p.getMarca() != null && !p.getMarca().isBlank())
                .toList();
            return reporteService.exportParqueVehicularPdf(vehiculos);
        });
    }

    // ─── Entrega-Recepción ANEXO V.4 ───
    @FXML private void onEntregaRecepcionPdf(ActionEvent event) {
        exportar(event, () -> {
            var bienes = productoService.getAll();
            return reporteService.exportEntregaRecepcionPdf(bienes);
        });
    }

    private void loadAreaChart() {
        if (areaChart == null) return;
        if (chartSpinner != null) { chartSpinner.setVisible(true); chartSpinner.setManaged(true); }
        LocalDate desde = getDesde(), hasta = getHasta();
        AppExecutor.submit(() -> {
            try {
                var counts = productoService.countByArea(20, desde, hasta);
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                counts.forEach((area, cnt) -> series.getData().add(new XYChart.Data<>(area, cnt)));
                Platform.runLater(() -> {
                    areaChart.getData().setAll(series);
                    if (chartSpinner != null) { chartSpinner.setVisible(false); chartSpinner.setManaged(false); }
                    boolean empty = series.getData().isEmpty();
                    if (chartEmptyState != null) { chartEmptyState.setVisible(empty); chartEmptyState.setManaged(empty); }
                    areaChart.setVisible(!empty);
                    areaChart.setManaged(!empty);
                    if (!empty) {
                        AnimationUtils.fadeInUp(areaChart, 350, 0);
                        // One extra pulse so the chart scene graph creates the bar nodes
                        Platform.runLater(() -> installBarClickHandlers(series));
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (chartSpinner != null) { chartSpinner.setVisible(false); chartSpinner.setManaged(false); }
                    if (chartEmptyState != null) { chartEmptyState.setVisible(true); chartEmptyState.setManaged(true); }
                    areaChart.setVisible(false);
                    areaChart.setManaged(false);
                });
            }
        });
    }

    private void loadCategoriaChart() {
        if (categoriaChart == null) return;
        if (categoriaChartSpinner != null) { categoriaChartSpinner.setVisible(true); categoriaChartSpinner.setManaged(true); }
        LocalDate desde = getDesde(), hasta = getHasta();
        AppExecutor.submit(() -> {
            try {
                var valores = productoService.getValorPorCategoria(20, desde, hasta);
                XYChart.Series<String, Number> series = new XYChart.Series<>();
                valores.forEach(cv -> series.getData().add(new XYChart.Data<>(cv.nombre(), cv.valor())));
                Platform.runLater(() -> {
                    categoriaChart.getData().setAll(series);
                    if (categoriaChartSpinner != null) { categoriaChartSpinner.setVisible(false); categoriaChartSpinner.setManaged(false); }
                    boolean empty = series.getData().isEmpty();
                    if (categoriaChartEmptyState != null) {
                        categoriaChartEmptyState.setVisible(empty);
                        categoriaChartEmptyState.setManaged(empty);
                    }
                    categoriaChart.setVisible(!empty);
                    categoriaChart.setManaged(!empty);
                    if (!empty) AnimationUtils.fadeInUp(categoriaChart, 350, 0);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (categoriaChartSpinner != null) { categoriaChartSpinner.setVisible(false); categoriaChartSpinner.setManaged(false); }
                    if (categoriaChartEmptyState != null) { categoriaChartEmptyState.setVisible(true); categoriaChartEmptyState.setManaged(true); }
                    categoriaChart.setVisible(false);
                    categoriaChart.setManaged(false);
                });
            }
        });
    }

    private LocalDate getDesde() { return desdeField.getValue(); }
    private LocalDate getHasta() { return hastaField.getValue(); }

    private void applyDateRangeStyle() {
        LocalDate desde = desdeField.getValue();
        LocalDate hasta = hastaField.getValue();
        boolean invalid = desde != null && hasta != null && desde.isAfter(hasta);
        if (invalid) {
            if (!hastaField.getStyleClass().contains("field-error")) hastaField.getStyleClass().add("field-error");
            if (!desdeField.getStyleClass().contains("field-error")) desdeField.getStyleClass().add("field-error");
        } else {
            hastaField.getStyleClass().remove("field-error");
            desdeField.getStyleClass().remove("field-error");
        }
    }

    private boolean validarFechas() {
        LocalDate desde = desdeField.getValue();
        LocalDate hasta  = hastaField.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            NotificacionUtil.advertencia(spinner.getScene(),
                "La fecha inicial debe ser anterior a la fecha final");
            AnimationUtils.shake(hastaField);
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

    private void installBarClickHandlers(XYChart.Series<String, Number> series) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            javafx.scene.Node node = data.getNode();
            if (node == null) continue;
            String area = data.getXValue();
            Tooltip.install(node, new Tooltip(area + "\nClic para ver en Bienes"));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(e -> {
                com.sibim.session.NavigationContext.setPendingAreaFilter(area);
                MainController mc = MainController.getInstance();
                if (mc != null) mc.navigateTo("productos");
            });
            AccessibilityUtils.asButton(node, area + " — ver en Bienes");
        }
    }

    @FunctionalInterface
    interface ExportTask {
        File run() throws Exception;
    }
}
