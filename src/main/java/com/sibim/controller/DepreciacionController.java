package com.sibim.controller;

import com.sibim.controller.dialogs.ProductoDetailDialog;
import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
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
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
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
    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/depreciacion");

    /** Horizon (years) for the projected value trend chart. */
    private static final int HORIZONTE_ANIOS = 10;

    private final ProductoService productoService = new ProductoService();
    private final ReporteService reporteService = new ReporteService();
    private final MovimientoService movimientoService = new MovimientoService();

    /** Bienes con datos de depreciación completos (sin filtro de búsqueda/área).
     *  Se usa para el chart y los stat cards. Los exports usan table.getItems()
     *  para respetar el filtro activo. */
    private List<Producto> conDepreciacion = List.of();
    private javafx.animation.Timeline skeletonPulse;

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
    @FXML private HBox chipRow;
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
    @FXML private TableColumn<Producto, Integer> colDepBar;
    @FXML private TableColumn<Producto, String> colPct;
    @FXML private TableColumn<Producto, String> colFechaTotal;

    @FXML private VBox  rangoBox;
    @FXML private Label lblRangoTotal;

    /** true mientras la tarjeta "Totalmente depreciados" está activa como filtro. */
    private boolean filtroSoloTotalmente = false;

    @FXML
    private void initialize() {
        setupTable();
        setupFiltros();
        loadData();
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardCompra, statCardActual, statCardPct, statCardTotalmente), 300, 55);
        javafx.application.Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
        if (helpConcepto     != null) DialogUtil.enableClickToShowTooltip(helpConcepto);
        if (helpValorCompra  != null) DialogUtil.enableClickToShowTooltip(helpValorCompra);
        if (helpValorActual  != null) DialogUtil.enableClickToShowTooltip(helpValorActual);
        if (helpPct          != null) DialogUtil.enableClickToShowTooltip(helpPct);
        if (helpTotalmente   != null) DialogUtil.enableClickToShowTooltip(helpTotalmente);
    }

    private void setupFiltros() {
        // Restore sticky search
        if (searchField != null) {
            String saved = STICKY.get("search", "");
            if (!saved.isBlank()) searchField.setText(saved);
        }

        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        SearchUtils.setupSearchHistory("sibim/search-history/depreciacion", searchField, () -> aplicarFiltro());
        SearchUtils.debounce(searchField, 250, q -> {
            STICKY.put("search", q != null ? q : "");
            aplicarFiltro();
        });

        areaFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String a) { return a == null ? "Todas las áreas" : a; }
            @Override public String fromString(String s) { return null; }
        });
        areaFilter.valueProperty().addListener((obs, o, n) -> {
            STICKY.put("area", n != null ? n : "");
            aplicarFiltro();
        });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        FontIcon emptyIcon = new FontIcon("mdi2c-chart-line");
        emptyIcon.setIconSize(44);
        emptyIcon.getStyleClass().add("empty-icon-lg");
        Label emptyMsg  = new Label("No hay bienes depreciables registrados");
        emptyMsg.getStyleClass().add("empty-state-msg");
        Label emptyHint = new Label("Registra bienes con valor y vida útil en la sección Bienes");
        emptyHint.getStyleClass().add("empty-state-hint");
        javafx.scene.layout.VBox emptyState = new javafx.scene.layout.VBox(12, emptyIcon, emptyMsg, emptyHint);
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        emptyState.getStyleClass().add("empty-state-pane");
        table.setPlaceholder(emptyState);

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
        colRestante.setCellFactory(DialogUtil.badgeCellFactory(item -> {
            if (item == null || item.equals("—")) return null;
            if (item.equals("Cumplida")) return "cell-badge-danger";
            if (item.endsWith("meses")) return "cell-badge-warning";
            try {
                int anios = Integer.parseInt(item.replace(" años", ""));
                return anios <= 2 ? "cell-badge-warning" : "cell-badge-success";
            } catch (NumberFormatException ex) { return null; }
        }));
        colValorCompra.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getPrecioCompra())));
        colValorActual.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getValorDepreciado())));

        colDepBar.setCellValueFactory(c ->
            new javafx.beans.property.SimpleIntegerProperty(
                c.getValue().getPorcentajeDepreciado() != null
                    ? c.getValue().getPorcentajeDepreciado() : 0).asObject());
        colDepBar.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0);
            { pb.setMaxWidth(Double.MAX_VALUE); pb.getStyleClass().add("depr-bar-cell"); }
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                int pct = item;
                pb.setProgress(Math.min(pct / 100.0, 1.0));
                pb.getStyleClass().removeAll("depr-bar-low", "depr-bar-mid", "depr-bar-high", "depr-bar-full");
                pb.getStyleClass().add(
                    pct >= 100 ? "depr-bar-full" : pct >= 75 ? "depr-bar-high"
                               : pct >= 50 ? "depr-bar-mid" : "depr-bar-low");
                setGraphic(pb);
            }
        });

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

        skeletonPulse = AnimationUtils.buildSkeletonPlaceholder(table, 7);

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                showDetalle(table.getSelectionModel().getSelectedItem());
        });
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                Producto sel = table.getSelectionModel().getSelectedItem();
                if (sel != null) { showDetalle(sel); ev.consume(); }
            } else if (ev.getCode() == javafx.scene.input.KeyCode.F && ev.isControlDown()) {
                if (searchField != null) { searchField.requestFocus(); searchField.selectAll(); }
                ev.consume();
            }
        });

        ContextMenu cm = new ContextMenu();
        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showDetalle(sel);
        });
        MenuItem cmFicha = new MenuItem("Imprimir ficha técnica");
        cmFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmFicha.setOnAction(e -> {
            Producto sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            DialogUtil.runAsyncWithProgress(table.getScene(), "Generando ficha técnica…",
                () -> {
                    var movs = movimientoService.getByProducto(sel.getId());
                    return reporteService.exportFichaTecnica(sel, movs);
                },
                file -> DialogUtil.showExportResultDialog(table.getScene(), file),
                ex -> NotificacionUtil.error(table.getScene(), "No se pudo generar la ficha técnica")
            );
        });
        cm.getItems().addAll(cmDetalle, new SeparatorMenuItem(), cmFicha);
        table.setContextMenu(cm);
    }

    private void showDetalle(Producto p) {
        ProductoDetailDialog.show(p, table.getScene(), movimientoService, log);
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
        STICKY.put("search", ""); STICKY.put("area", "");
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
        AnimationUtils.staggerTableRows(table);
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
        updateChips(q, area);
    }

    private void updateChips(String q, String area) {
        if (chipRow == null) return;
        chipRow.getChildren().clear();
        boolean any = false;

        if (!q.isBlank()) {
            chipRow.getChildren().add(makeChip("Búsqueda: \"" + q + "\"", () -> {
                searchField.clear(); searchField.requestFocus();
            }));
            any = true;
        }
        if (area != null) {
            chipRow.getChildren().add(makeChip("Área: " + area, () -> areaFilter.setValue(null)));
            any = true;
        }
        if (filtroSoloTotalmente) {
            chipRow.getChildren().add(makeChip("Solo totalmente depreciados", this::onFiltrarTotalmenteDepreciados));
            any = true;
        }
        chipRow.setVisible(any);
        chipRow.setManaged(any);
    }

    private javafx.scene.layout.HBox makeChip(String texto, Runnable onClose) {
        Label lbl = new Label(texto);
        lbl.getStyleClass().add("filter-chip-label");
        Button btn = new Button("×");
        btn.getStyleClass().add("filter-chip-close");
        btn.setOnAction(e -> onClose.run());
        javafx.scene.layout.HBox chip = new javafx.scene.layout.HBox(5, lbl, btn);
        chip.getStyleClass().add("filter-chip-active");
        chip.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return chip;
    }

    @FXML
    private void onExportarPdf() {
        List<Producto> vista = new java.util.ArrayList<>(table.getItems());
        if (vista.isEmpty()) { NotificacionUtil.advertencia(table.getScene(), "No hay bienes visibles para exportar"); return; }
        exportar(() -> reporteService.exportDepreciacionPdf(vista));
    }

    @FXML
    private void onExportarExcel() {
        List<Producto> vista = new java.util.ArrayList<>(table.getItems());
        if (vista.isEmpty()) { NotificacionUtil.advertencia(table.getScene(), "No hay bienes visibles para exportar"); return; }
        exportar(() -> reporteService.exportDepreciacionExcel(vista));
    }

    @FXML
    private void onExportarCsv() {
        List<Producto> vista = new java.util.ArrayList<>(table.getItems());
        if (vista.isEmpty()) { NotificacionUtil.advertencia(table.getScene(), "No hay bienes visibles para exportar"); return; }
        exportar(() -> reporteService.exportDepreciacionCsv(vista));
    }

    @FXML
    private void onExportarFichas() {
        List<Producto> lista = new java.util.ArrayList<>(table.getItems());
        if (lista.isEmpty()) {
            NotificacionUtil.advertencia(table.getScene(), "No hay bienes visibles para generar fichas");
            return;
        }
        if (lista.size() > 100) {
            NotificacionUtil.advertencia(table.getScene(),
                "Hay " + lista.size() + " bienes — filtra primero para reducir el lote (máx. 100 por exportación)");
            return;
        }
        DialogUtil.runAsyncWithProgress(table.getScene(),
            "Generando " + lista.size() + " ficha(s)…",
            () -> reporteService.exportFichasTecnicasMasivas(lista, movimientoService),
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            ex -> {
                log.error("Error al exportar fichas masivas", ex);
                NotificacionUtil.error(table.getScene(), "No se pudieron generar las fichas técnicas");
            });
    }

    private void exportar(java.util.concurrent.Callable<java.io.File> task) {
        DialogUtil.runAsyncWithProgress(table.getScene(), "Generando reporte…",
            task,
            file -> DialogUtil.showExportResultDialog(table.getScene(), file),
            e -> {
                log.error("No se pudo exportar la depreciación", e);
                if (table.getScene() != null)
                    NotificacionUtil.errorConAccion(table.getScene(),
                        "Error al generar el reporte", "Reintentar", () -> exportar(task));
            });
    }

    private void stopSkeleton() {
        if (skeletonPulse != null) { skeletonPulse.stop(); skeletonPulse = null; }
        FontIcon icon = new FontIcon("mdi2c-chart-line");
        icon.setIconSize(44);
        icon.getStyleClass().add("empty-icon-lg");
        javafx.scene.layout.VBox emptyState = new javafx.scene.layout.VBox(12,
            icon, lblPlaceholderMsg, lblPlaceholderHint);
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        emptyState.getStyleClass().add("empty-state-pane");
        table.setPlaceholder(emptyState);
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        Task<List<Producto>> task = new Task<>() {
            @Override protected List<Producto> call() throws Exception {
                return productoService.getAll();
            }
            @Override protected void succeeded() {
                stopSkeleton();
                conDepreciacion = getValue().stream()
                    .filter(p -> p.getValorDepreciado() != null)
                    .toList();
                int sinDatos = getValue().size() - conDepreciacion.size();
                updateStats(conDepreciacion, sinDatos);
                updateChart(conDepreciacion);
                String areaPrevia = areaFilter.getValue() != null
                    ? areaFilter.getValue()
                    : STICKY.get("area", "");
                var areas = new TreeSet<String>();
                for (Producto p : conDepreciacion) if (p.getArea() != null) areas.add(p.getArea());
                areaFilter.getItems().setAll(areas);
                areaFilter.getItems().add(0, null);
                areaFilter.setValue(!areaPrevia.isBlank() && areas.contains(areaPrevia) ? areaPrevia : null);
                aplicarFiltro();
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
            }
            @Override protected void failed() {
                stopSkeleton();
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                log.error("No se pudo cargar la depreciación", getException());
                if (table.getScene() != null)
                    NotificacionUtil.errorConAccion(table.getScene(), "No se pudo cargar la depreciación", "Reintentar", DepreciacionController.this::loadData);
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
        updateRangos(bienes);

        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(900));
        pop.setOnFinished(e -> {
            if (statCardCompra     != null) AnimationUtils.statCardPop(statCardCompra);
            if (statCardActual     != null) AnimationUtils.statCardPop(statCardActual);
            if (statCardPct        != null) AnimationUtils.statCardPop(statCardPct);
            if (statCardTotalmente != null) AnimationUtils.statCardPop(statCardTotalmente);
        });
        pop.play();
    }

    private void updateRangos(List<Producto> bienes) {
        if (rangoBox == null) return;
        rangoBox.getChildren().clear();
        int total = bienes.size();
        if (lblRangoTotal != null)
            lblRangoTotal.setText(total + (total == 1 ? " bien con datos" : " bienes con datos"));
        if (total == 0) return;

        int r1 = 0, r2 = 0, r3 = 0, r4 = 0;
        for (Producto p : bienes) {
            int pct = p.getPorcentajeDepreciado() != null ? p.getPorcentajeDepreciado() : 0;
            if      (pct <  25) r1++;
            else if (pct <  50) r2++;
            else if (pct < 100) r3++;
            else                r4++;
        }

        record Rango(String label, String pbClass, int count) {}
        List<Rango> rangos = List.of(
            new Rango("0 – 24%",    "status-pb-green", r1),
            new Rango("25 – 49%",   "status-pb-amber",  r2),
            new Rango("50 – 99%",   "status-pb-amber",  r3),
            new Rango("100%+",      "status-pb-red",   r4));

        int idx = 0;
        for (Rango r : rangos) {
            Label nameLbl = new Label(r.label());
            nameLbl.getStyleClass().add("depr-range-label");

            javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0);
            pb.getStyleClass().addAll("status-pb", r.pbClass());
            pb.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(pb, javafx.scene.layout.Priority.ALWAYS);

            Label countLbl = new Label("0 bienes");
            countLbl.getStyleClass().add("depr-range-count");

            HBox row = new HBox(10, nameLbl, pb, countLbl);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            rangoBox.getChildren().add(row);

            double target = (double) r.count() / total;
            long rCount = r.count();
            int delay = 150 + idx * 80;
            javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(delay));
            wait.setOnFinished(ev -> {
                javafx.animation.Timeline tl = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(pb.progressProperty(), 0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(800),
                        new javafx.animation.KeyValue(pb.progressProperty(), target,
                            javafx.animation.Interpolator.EASE_BOTH)));
                tl.play();
                AnimationUtils.animateCount(countLbl, rCount, 750, v -> v + " bienes");
            });
            wait.play();
            idx++;
        }
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
