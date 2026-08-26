package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeSet;

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
    @FXML private VBox statCardTotalmente;
    @FXML private Label lblStatCompra;
    @FXML private Label lblStatActual;
    @FXML private Label lblStatPct;
    @FXML private Label lblStatSinDatos;
    @FXML private Label lblStatTotalmente;
    @FXML private Label helpConcepto;
    @FXML private Label helpValorCompra;
    @FXML private Label helpValorActual;
    @FXML private Label helpPct;
    @FXML private Label helpTotalmente;
    @FXML private LineChart<String, Number> chartTendencia;
    @FXML private Label lblTotalFiltrado;
    @FXML private TextField searchField;
    @FXML private Button btnClearSearch;
    @FXML private ComboBox<String> areaFilter;
    @FXML private Button btnClearFilters;
    @FXML private Label lblPlaceholderMsg;
    @FXML private Label lblPlaceholderHint;
    @FXML private TableView<Producto> table;
    @FXML private TableColumn<Producto, String> colNombre;
    @FXML private TableColumn<Producto, String> colCategoria;
    @FXML private TableColumn<Producto, String> colArea;
    @FXML private TableColumn<Producto, String> colFechaAdq;
    @FXML private TableColumn<Producto, String> colVidaUtil;
    @FXML private TableColumn<Producto, String> colRestante;
    @FXML private TableColumn<Producto, String> colValorCompra;
    @FXML private TableColumn<Producto, String> colValorActual;
    @FXML private TableColumn<Producto, String> colPct;
    @FXML private TableColumn<Producto, String> colFechaTotal;

    /** true mientras la tarjeta "Totalmente depreciados" está activa como filtro. */
    private boolean filtroSoloTotalmente = false;

    @FXML
    private void initialize() {
        setupTable();
        setupFiltros();
        loadData();
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardCompra, statCardActual, statCardPct, statCardTotalmente), 300, 55);
        if (helpConcepto     != null) DialogUtil.enableClickToShowTooltip(helpConcepto);
        if (helpValorCompra  != null) DialogUtil.enableClickToShowTooltip(helpValorCompra);
        if (helpValorActual  != null) DialogUtil.enableClickToShowTooltip(helpValorActual);
        if (helpPct          != null) DialogUtil.enableClickToShowTooltip(helpPct);
        if (helpTotalmente   != null) DialogUtil.enableClickToShowTooltip(helpTotalmente);
    }

    private void setupFiltros() {
        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        SearchUtils.debounce(searchField, 250, q -> aplicarFiltro());

        areaFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String a) { return a == null ? "Todas las áreas" : a; }
            @Override public String fromString(String s) { return null; }
        });
        areaFilter.valueProperty().addListener((obs, o, n) -> aplicarFiltro());
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colNombre.setCellValueFactory(new PropertyValueFactory<>("nombre"));
        colCategoria.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCategoriaNombre() != null
                ? c.getValue().getCategoriaNombre() : ""));
        colArea.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getArea() != null ? c.getValue().getArea() : "—"));
        colFechaAdq.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDate(c.getValue().getFechaAdquisicion())));
        colVidaUtil.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getVidaUtilAnios() + " años"));
        colRestante.setCellValueFactory(c ->
            new SimpleStringProperty(vidaUtilRestante(c.getValue())));
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
        colFechaTotal.setCellValueFactory(c -> {
            Producto p = c.getValue();
            LocalDate fecha = fechaDepreciacionTotal(p);
            return new SimpleStringProperty(fecha != null ? FormatUtils.formatDate(fecha) : "—");
        });

        colPct.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().setAll(List.<TableColumn<Producto, ?>>of(colPct));
    }

    /** Años (o meses, si falta menos de uno) que le quedan a un bien antes de
     *  llegar a su valor residual, contando desde hoy. */
    private static String vidaUtilRestante(Producto p) {
        LocalDate fechaTotal = fechaDepreciacionTotal(p);
        if (fechaTotal == null) return "—";
        LocalDate hoy = LocalDate.now();
        if (!fechaTotal.isAfter(hoy)) return "Cumplida";
        long meses = java.time.temporal.ChronoUnit.MONTHS.between(hoy, fechaTotal);
        return meses < 12 ? meses + " meses" : (meses / 12) + " años";
    }

    /** Fecha en la que el bien llega a su valor residual (fin de su vida útil). */
    private static LocalDate fechaDepreciacionTotal(Producto p) {
        if (p.getFechaAdquisicion() == null || p.getVidaUtilAnios() == null || p.getVidaUtilAnios() <= 0)
            return null;
        return p.getFechaAdquisicion().plusYears(p.getVidaUtilAnios());
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    @FXML
    private void onFiltrarTotalmenteDepreciados() {
        filtroSoloTotalmente = !filtroSoloTotalmente;
        if (statCardTotalmente != null)
            statCardTotalmente.pseudoClassStateChanged(
                javafx.css.PseudoClass.getPseudoClass("selected"), filtroSoloTotalmente);
        aplicarFiltro();
    }

    @FXML
    private void onClearFiltros() {
        searchField.clear();
        areaFilter.setValue(null);
        if (filtroSoloTotalmente) onFiltrarTotalmenteDepreciados();
        else aplicarFiltro();
    }

    private void aplicarFiltro() {
        String q = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String area = areaFilter.getValue();
        List<Producto> mostrar = conDepreciacion.stream()
            .filter(p -> !filtroSoloTotalmente
                || (p.getPorcentajeDepreciado() != null && p.getPorcentajeDepreciado() >= 100))
            .filter(p -> area == null || area.equals(p.getArea()))
            .filter(p -> q.isBlank()
                || p.getNombre().toLowerCase().contains(q)
                || (p.getCategoriaNombre() != null && p.getCategoriaNombre().toLowerCase().contains(q))
                || (p.getArea() != null && p.getArea().toLowerCase().contains(q)))
            .toList();
        table.setItems(FXCollections.observableArrayList(mostrar));
        if (lblTotalFiltrado != null)
            lblTotalFiltrado.setText(mostrar.size() + (mostrar.size() == 1 ? " bien" : " bienes"));
        // The table's placeholder is generic "no data at all" text — without
        // this, filtering (search/área/tarjeta) down to zero results looks
        // identical to a genuinely empty inventory, which is misleading:
        // the chart above still shows real data while the table implies
        // there's none whatsoever.
        if (lblPlaceholderMsg != null && lblPlaceholderHint != null) {
            boolean filtroActivo = filtroSoloTotalmente || area != null || !q.isBlank();
            if (mostrar.isEmpty() && filtroActivo && !conDepreciacion.isEmpty()) {
                lblPlaceholderMsg.setText("Ningún bien coincide con el filtro actual");
                lblPlaceholderHint.setText("Prueba con \"Limpiar filtros\" arriba de la tabla");
            } else {
                lblPlaceholderMsg.setText("No hay bienes con datos de depreciación");
                lblPlaceholderHint.setText("Captura fecha de adquisición y vida útil al editar un bien");
            }
        }
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
                    NotificacionUtil.errorConAccion(table.getScene(),
                        "Error al generar el reporte", "Reintentar", () -> exportar(task));
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
                String areaPrevia = areaFilter.getValue();
                var areas = new TreeSet<String>();
                for (Producto p : conDepreciacion) if (p.getArea() != null) areas.add(p.getArea());
                areaFilter.getItems().setAll(areas);
                areaFilter.getItems().add(0, null);
                areaFilter.setValue(areaPrevia != null && areas.contains(areaPrevia) ? areaPrevia : null);
                aplicarFiltro();
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
            }
            @Override protected void failed() {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                log.error("No se pudo cargar la depreciación", getException());
                if (table.getScene() != null)
                    NotificacionUtil.errorConAccion(table.getScene(), "No se pudo cargar la depreciación", "Reintentar", this::loadData);
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
        long totalmenteDepreciados = bienes.stream()
            .filter(p -> { Integer pct = p.getPorcentajeDepreciado(); return pct != null && pct >= 100; })
            .count();

        if (lblStatCompra != null) AnimationUtils.animateCount(lblStatCompra, totalCompra.longValue(), 700,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        if (lblStatActual != null) AnimationUtils.animateCount(lblStatActual, totalActual.longValue(), 880,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        if (lblStatPct != null) AnimationUtils.animateCount(lblStatPct, pctPromedio, 580, v -> v + "%");
        if (lblStatSinDatos != null) lblStatSinDatos.setText(sinDatos == 0
            ? "todos los bienes con datos completos"
            : sinDatos + " bien(es) sin datos de depreciación");
        if (lblStatTotalmente != null) AnimationUtils.animateCount(lblStatTotalmente, totalmenteDepreciados, 700);

        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(900));
        pop.setOnFinished(e -> {
            if (statCardCompra     != null) AnimationUtils.statCardPop(statCardCompra);
            if (statCardActual     != null) AnimationUtils.statCardPop(statCardActual);
            if (statCardPct        != null) AnimationUtils.statCardPop(statCardPct);
            if (statCardTotalmente != null) AnimationUtils.statCardPop(statCardTotalmente);
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
