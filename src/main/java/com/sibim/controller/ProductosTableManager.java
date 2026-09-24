package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Handles all TableView column setup, cell factories, context-menu wiring,
 * table-listener wiring, empty-state placeholder, skeleton placeholder, and
 * pagination box setup — extracted from ProductosController.
 * Package-private; only used by ProductosController.
 */
class ProductosTableManager {

    private final TableView<Producto> table;
    private final TableColumn<Producto, String> colFoto;
    private final TableColumn<Producto, String> colNombre;
    private final TableColumn<Producto, String> colCodigo;
    private final TableColumn<Producto, String> colCategoria;
    private final TableColumn<Producto, String> colArea;
    private final TableColumn<Producto, Integer> colStock;
    private final TableColumn<Producto, Integer> colStockMin;
    private final TableColumn<Producto, Integer> colStockMax;
    private final TableColumn<Producto, String> colValor;
    private final TableColumn<Producto, String> colEstado;
    private final ComboBox<Integer> pageSizeBox;
    private final Label lblTotal;
    private final Label lblPage;
    private final Button btnPrev;
    private final Button btnNext;
    private final Label lblSeleccionados;
    private final Button btnMovimiento;
    private final Button btnQr;
    private final Button btnEditar;
    private final Button btnEliminar;
    private final MenuButton btnExportarSeleccion;

    private final Map<String, Image> thumbnailCache;
    private final Map<String, String> catIcon;
    private final Logger log;
    private final boolean canEdit;
    private final BooleanSupplier canEditSupplier;

    // Callbacks into the controller
    private final Runnable onEdit;
    private final Runnable onDelete;
    private final java.util.function.Consumer<Producto> showProductDetail;
    private final java.util.function.Consumer<Producto> showMovimientoTimeline;
    private final java.util.function.Consumer<List<Producto>> exportarEtiquetasQr;
    private final java.util.function.Consumer<List<Producto>> exportarEtiquetaFisica;
    private final java.util.function.Consumer<Producto> saveStockThresholds;
    private final Supplier<String> pendingHighlightIdSupplier;
    private final ProductosBulkBar bulkBarManager;
    private final Runnable pageSizeChanged; // called when page size box changes
    private final ReporteService reporteService;
    private final MovimientoService movimientoService;

    // State exposed back to the controller
    Label emptyStateMsg;
    Label emptyStateHint;
    Button btnEmptyLimpiar;
    VBox emptyStatePlaceholder;
    javafx.animation.Timeline skeletonPulse;

    ProductosTableManager(
            TableView<Producto> table,
            TableColumn<Producto, String> colFoto,
            TableColumn<Producto, String> colNombre,
            TableColumn<Producto, String> colCodigo,
            TableColumn<Producto, String> colCategoria,
            TableColumn<Producto, String> colArea,
            TableColumn<Producto, Integer> colStock,
            TableColumn<Producto, Integer> colStockMin,
            TableColumn<Producto, Integer> colStockMax,
            TableColumn<Producto, String> colValor,
            TableColumn<Producto, String> colEstado,
            ComboBox<Integer> pageSizeBox,
            Label lblTotal, Label lblPage,
            Button btnPrev, Button btnNext,
            Label lblSeleccionados,
            Button btnMovimiento, Button btnQr,
            Button btnEditar, Button btnEliminar,
            MenuButton btnExportarSeleccion,
            Map<String, Image> thumbnailCache,
            Map<String, String> catIcon,
            Logger log,
            boolean canEdit,
            BooleanSupplier canEditSupplier,
            Runnable onEdit,
            Runnable onDelete,
            java.util.function.Consumer<Producto> showProductDetail,
            java.util.function.Consumer<Producto> showMovimientoTimeline,
            java.util.function.Consumer<List<Producto>> exportarEtiquetasQr,
            java.util.function.Consumer<List<Producto>> exportarEtiquetaFisica,
            java.util.function.Consumer<Producto> saveStockThresholds,
            Supplier<String> pendingHighlightIdSupplier,
            ProductosBulkBar bulkBarManager,
            Runnable pageSizeChanged,
            ReporteService reporteService,
            MovimientoService movimientoService) {
        this.table = table;
        this.colFoto = colFoto;
        this.colNombre = colNombre;
        this.colCodigo = colCodigo;
        this.colCategoria = colCategoria;
        this.colArea = colArea;
        this.colStock = colStock;
        this.colStockMin = colStockMin;
        this.colStockMax = colStockMax;
        this.colValor = colValor;
        this.colEstado = colEstado;
        this.pageSizeBox = pageSizeBox;
        this.lblTotal = lblTotal;
        this.lblPage = lblPage;
        this.btnPrev = btnPrev;
        this.btnNext = btnNext;
        this.lblSeleccionados = lblSeleccionados;
        this.btnMovimiento = btnMovimiento;
        this.btnQr = btnQr;
        this.btnEditar = btnEditar;
        this.btnEliminar = btnEliminar;
        this.btnExportarSeleccion = btnExportarSeleccion;
        this.thumbnailCache = thumbnailCache;
        this.catIcon = catIcon;
        this.log = log;
        this.canEdit = canEdit;
        this.canEditSupplier = canEditSupplier;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        this.showProductDetail = showProductDetail;
        this.showMovimientoTimeline = showMovimientoTimeline;
        this.exportarEtiquetasQr = exportarEtiquetasQr;
        this.exportarEtiquetaFisica = exportarEtiquetaFisica;
        this.saveStockThresholds = saveStockThresholds;
        this.pendingHighlightIdSupplier = pendingHighlightIdSupplier;
        this.bulkBarManager = bulkBarManager;
        this.pageSizeChanged = pageSizeChanged;
        this.reporteService = reporteService;
        this.movimientoService = movimientoService;
    }

    /** Full table setup — called once from initialize(). */
    void setup() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        ProductosColumnSetup.configureFoto(colFoto, table, thumbnailCache, log);
        ProductosColumnSetup.configureNombre(colNombre);
        ProductosColumnSetup.configureCodigo(colCodigo);
        ProductosColumnSetup.configureCategoria(colCategoria, catIcon);
        ProductosColumnSetup.configureArea(colArea);
        ProductosColumnSetup.configureStockYValor(colStock, colValor);
        ProductosColumnSetup.configureStockMinMax(colStockMin, colStockMax, saveStockThresholds);
        ProductosColumnSetup.configureEstado(colEstado);
        ProductosColumnSetup.configureRowFactory(table, pendingHighlightIdSupplier);
        setupTableListeners();
        table.setContextMenu(ProductosContextMenu.build(
            table, canEdit, reporteService, movimientoService, log,
            showProductDetail, showMovimientoTimeline, exportarEtiquetasQr,
            exportarEtiquetaFisica, onEdit, onDelete));
        setupEmptyState();
        setupPagination();
        // Right-click on header → toggle secondary columns
        DialogUtil.setupColumnVisibilityMenu("bienes.cols", table,
            List.of(colFoto, colNombre, colStock, colEstado));
    }

    private void setupTableListeners() {
        // Ctrl/Shift-click to pick several rows for "Exportar seleccionados" —
        // Editar/Dar de baja stay single-item actions (see the listener below).
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Selection → enable/disable action buttons
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Producto>) c -> {
            int n = table.getSelectionModel().getSelectedItems().size();
            if (btnMovimiento != null && canEdit) btnMovimiento.setDisable(n != 1);
            if (btnQr        != null) btnQr.setDisable(n != 1);
            if (btnEditar   != null && canEdit) btnEditar.setDisable(n != 1);
            if (btnEliminar != null && canEdit) btnEliminar.setDisable(n != 1);
            if (btnExportarSeleccion != null) btnExportarSeleccion.setDisable(n == 0);
            updateSelectionLabel(lblSeleccionados, n);
            bulkBarManager.update(n);
        });
        if (btnMovimiento != null && canEdit) btnMovimiento.setDisable(true);
        if (btnQr        != null) btnQr.setDisable(true);
        if (btnEditar   != null && canEdit) btnEditar.setDisable(true);
        if (btnEliminar != null && canEdit) btnEliminar.setDisable(true);
        if (btnExportarSeleccion != null) btnExportarSeleccion.setDisable(true);

        // JavaFX doesn't show tooltips on disabled nodes by default —
        // Tooltip.install() uses a separate mechanism that works regardless.
        if (btnEditar        != null && canEdit) Tooltip.install(btnEditar,        new Tooltip("Selecciona un bien para editarlo"));
        if (btnEliminar      != null && canEdit) Tooltip.install(btnEliminar,      new Tooltip("Selecciona un bien para darlo de baja"));
        if (btnMovimiento    != null && canEdit) Tooltip.install(btnMovimiento,    new Tooltip("Selecciona un bien para registrar un movimiento"));
        if (btnExportarSeleccion != null)        Tooltip.install(btnExportarSeleccion, new Tooltip("Selecciona uno o más bienes para exportarlos"));

        // Delete key on table — only when exactly one row is selected, same
        // as the "Dar de baja" button (a formal baja needs a motivo per bien,
        // it doesn't make sense as a bulk action from a bare Delete keypress).
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.DELETE
                    && canEdit && table.getSelectionModel().getSelectedItems().size() == 1) {
                onDelete.run(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume();
            }
        });

        // Double-click: edit if allowed, otherwise show detail
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                if (canEdit) onEdit.run();
                else showProductDetail.accept(table.getSelectionModel().getSelectedItem());
            }
        });
    }

    private void setupEmptyState() {
        // Smart empty state (set programmatically so we can update the message)
        FontIcon emptyIcon = new FontIcon("mdi2p-package-variant");
        emptyIcon.setIconSize(52);
        emptyIcon.getStyleClass().add("empty-icon-lg");
        emptyStateMsg = new Label("No hay bienes registrados en el sistema");
        emptyStateMsg.getStyleClass().add("empty-state-msg");
        btnEmptyLimpiar = new Button("Limpiar filtros");
        btnEmptyLimpiar.getStyleClass().add("btn-secondary");
        btnEmptyLimpiar.setOnAction(e -> {
            // delegate the action to the controller via a reference obtained at wire-time
            // — we use the pageSizeChanged slot which isn't needed here, so instead
            // we store an explicit "onClearFilters" reference passed in via the helper below
            onClearFilters.run();
        });
        btnEmptyLimpiar.setVisible(false); btnEmptyLimpiar.setManaged(false);
        emptyStateHint = new Label(canEdit ? "Presiona Ctrl+N para agregar el primer bien" : "");
        emptyStateHint.getStyleClass().add("empty-state-hint");
        VBox emptyState = new VBox(12, emptyIcon, emptyStateMsg, btnEmptyLimpiar, emptyStateHint);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.getStyleClass().add("empty-state-pane");
        emptyState.setMaxWidth(380);
        emptyState.setPadding(new Insets(32, 24, 32, 24));
        // Spring-in when placeholder becomes visible; reset transforms when hidden
        emptyState.visibleProperty().addListener((obs, wasVisible, isVisible) -> {
            if (isVisible && !wasVisible) AnimationUtils.springIn(emptyState);
            else if (!isVisible) { emptyState.setOpacity(1); emptyState.setScaleX(1); emptyState.setScaleY(1); }
        });
        emptyStatePlaceholder = emptyState;
        table.setPlaceholder(emptyState);
    }

    // Set by the controller right after construction — avoids a circular
    // constructor dependency for onClearFilters.
    private Runnable onClearFilters = () -> {};

    void setOnClearFilters(Runnable r) { this.onClearFilters = r; }

    VBox buildSkeletonPlaceholder() {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        for (int i = 0; i < 7; i++) {
            Label bar = new Label();
            bar.getStyleClass().add("skeleton");
            bar.setPrefHeight(44);
            bar.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(bar);
        }
        skeletonPulse = new Timeline(
            new KeyFrame(Duration.millis(0),   new KeyValue(box.opacityProperty(), 0.7)),
            new KeyFrame(Duration.millis(800),  new KeyValue(box.opacityProperty(), 0.4)),
            new KeyFrame(Duration.millis(1600), new KeyValue(box.opacityProperty(), 0.7))
        );
        skeletonPulse.setCycleCount(Timeline.INDEFINITE);
        skeletonPulse.play();
        return box;
    }

    private void setupPagination() {
        pageSizeBox.setItems(FXCollections.observableArrayList(25, 50, 100, 250, 500, Integer.MAX_VALUE));
        pageSizeBox.setConverter(new javafx.util.StringConverter<>() {
            public String toString(Integer n)   { return n == null ? "" : n == Integer.MAX_VALUE ? "Todos" : String.valueOf(n); }
            public Integer fromString(String s) { return "Todos".equals(s) ? Integer.MAX_VALUE : Integer.parseInt(s); }
        });
        pageSizeBox.setValue(25);
        pageSizeBox.setOnAction(e -> pageSizeChanged.run());
    }

    void updateTablePage(ObservableList<Producto> filteredData, int currentPage,
                         int pageSize, int totalFiltered) {
        PaginationUtils.updatePageServer(table, filteredData, currentPage, pageSize, totalFiltered,
            lblTotal, lblPage, btnPrev, btnNext, "resultado", "resultados");
    }

    private static void updateSelectionLabel(Label lbl, int n) {
        if (lbl == null) return;
        if (n > 0) {
            lbl.setText("· " + n + (n == 1 ? " seleccionado" : " seleccionados"));
            lbl.setVisible(true);
            lbl.setManaged(true);
        } else {
            lbl.setVisible(false);
            lbl.setManaged(false);
        }
    }
}
