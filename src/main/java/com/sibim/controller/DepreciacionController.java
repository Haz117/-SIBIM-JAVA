package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Fleet-wide view of asset depreciation — complements the per-asset panel
 *  in {@link ProductosController}'s detail dialog with a summary of the
 *  whole active inventory: totals, a trend chart of projected book value,
 *  and a sortable table. Read-only — editing depreciation data still
 *  happens on the Productos screen. */
public class DepreciacionController {

    private static final Logger log = LoggerFactory.getLogger(DepreciacionController.class);

    /** Horizon (years) for the projected value trend chart. */
    private static final int HORIZONTE_ANIOS = 10;

    private final ProductoService productoService = new ProductoService();
    private final ReporteService reporteService = new ReporteService();

    /** Lo que la tabla está mostrando ahora mismo — exportar reusa esta misma
     *  lista en vez de re-consultar, así el archivo coincide con la pantalla. */
    private List<Producto> conDepreciacion = List.of();

    @FXML private ProgressIndicator spinner;
    @FXML private VBox statCardCompra;
    @FXML private VBox statCardActual;
    @FXML private VBox statCardPct;
    @FXML private Label lblStatCompra;
    @FXML private Label lblStatActual;
    @FXML private Label lblStatPct;
    @FXML private Label lblStatSinDatos;
    @FXML private Label helpValorCompra;
    @FXML private Label helpValorActual;
    @FXML private Label helpPct;
    @FXML private LineChart<String, Number> chartTendencia;
    @FXML private TableView<Producto> table;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colFechaAdq;
    @FXML private TableColumn<Producto, String> colVidaUtil;
    @FXML private TableColumn<Producto, String> colValorCompra;
    @FXML private TableColumn<Producto, String> colValorActual;
    @FXML private TableColumn<Producto, String> colPct;

    @FXML
    private void initialize() {
        setupTable();
        loadData();
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardCompra, statCardActual, statCardPct), 300, 55);
        if (helpValorCompra != null) DialogUtil.enableClickToShowTooltip(helpValorCompra);
        if (helpValorActual != null) DialogUtil.enableClickToShowTooltip(helpValorActual);
        if (helpPct         != null) DialogUtil.enableClickToShowTooltip(helpPct);
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colCategoria.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCategoriaNombre() != null
                ? c.getValue().getCategoriaNombre() : ""));
        colFechaAdq.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDate(c.getValue().getFechaAdquisicion())));
        colVidaUtil.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getVidaUtilAnios() + " años"));
        colValorCompra.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getPrecioCompra())));
        colValorActual.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getValorDepreciado())));

        colPct.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getPorcentajeDepreciado() + "% depreciado"));
        colPct.setCellFactory(DialogUtil.badgeCellFactory(item -> {
            int pct = Integer.parseInt(item.substring(0, item.indexOf('%')));
            return pct >= 90 ? "cell-badge-danger" : pct >= 50 ? "cell-badge-warning" : "cell-badge-success";
        }));
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    @FXML
    private void onExportarPdf() {
        exportar(() -> reporteService.exportDepreciacionPdf(conDepreciacion));
    }

    @FXML
    private void onExportarExcel() {
        exportar(() -> reporteService.exportDepreciacionExcel(conDepreciacion));
    }

    private void exportar(java.util.concurrent.Callable<java.io.File> task) {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        DialogUtil.runAsync(task,
            file -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                DialogUtil.showExportResultDialog(table.getScene(), file);
            },
            e -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                log.error("No se pudo exportar la depreciación", e);
                if (table.getScene() != null)
                    NotificacionUtil.error(table.getScene(), "Error al generar el reporte");
            });
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        Task<List<Producto>> task = new Task<>() {
            @Override protected List<Producto> call() throws Exception {
                return productoService.getAll();
            }
            @Override protected void succeeded() {
                conDepreciacion = getValue().stream()
                    .filter(p -> p.getValorDepreciado() != null)
                    .toList();
                int sinDatos = getValue().size() - conDepreciacion.size();
                updateStats(conDepreciacion, sinDatos);
                updateChart(conDepreciacion);
                table.setItems(FXCollections.observableArrayList(conDepreciacion));
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
            }
            @Override protected void failed() {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                log.error("No se pudo cargar la depreciación", getException());
                if (table.getScene() != null)
                    NotificacionUtil.error(table.getScene(), "No se pudo cargar la información de depreciación.");
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    private void updateStats(List<Producto> bienes, int sinDatos) {
        BigDecimal totalCompra = bienes.stream()
            .map(Producto::getPrecioCompra)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalActual = bienes.stream()
            .map(Producto::getValorDepreciado)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        int pctPromedio = bienes.isEmpty() ? 0 : bienes.stream()
            .mapToInt(Producto::getPorcentajeDepreciado)
            .sum() / bienes.size();

        if (lblStatCompra != null) AnimationUtils.animateCount(lblStatCompra, totalCompra.longValue(), 700,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        if (lblStatActual != null) AnimationUtils.animateCount(lblStatActual, totalActual.longValue(), 880,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        if (lblStatPct != null) AnimationUtils.animateCount(lblStatPct, pctPromedio, 580, v -> v + "%");
        if (lblStatSinDatos != null) lblStatSinDatos.setText(sinDatos == 0
            ? "todos los bienes con datos completos"
            : sinDatos + " bien(es) sin datos de depreciación");

        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(900));
        pop.setOnFinished(e -> {
            if (statCardCompra != null) AnimationUtils.statCardPop(statCardCompra);
            if (statCardActual != null) AnimationUtils.statCardPop(statCardActual);
            if (statCardPct    != null) AnimationUtils.statCardPop(statCardPct);
        });
        pop.play();
    }

    private void updateChart(List<Producto> bienes) {
        chartTendencia.getData().clear();
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Valor proyectado del activo fijo");
        LocalDate hoy = LocalDate.now();
        for (int y = 0; y <= HORIZONTE_ANIOS; y++) {
            LocalDate fecha = hoy.plusYears(y);
            BigDecimal total = bienes.stream()
                .map(p -> p.getValorDepreciadoEn(fecha))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
            serie.getData().add(new XYChart.Data<>(y == 0 ? "Hoy" : "+" + y + "a", total));
        }
        chartTendencia.getData().add(serie);
    }
}
