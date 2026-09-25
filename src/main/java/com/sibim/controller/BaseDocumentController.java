package com.sibim.controller;

import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.EmptyStateUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;

/**
 * Abstract base for document controllers (Resguardos, Préstamos, Comodatos, Actas).
 *
 * Template:  initialize() → setupTableBase() → setupColumns() → onInitialize()
 *                         → setupButtonState() → loadData()
 *
 * Subclasses supply: type parameter T, column factories, async fetch, stats
 * update, PDF delegate, and error message.  Everything else is inherited.
 */
public abstract class BaseDocumentController<T> {

    @FunctionalInterface
    public interface ListExporter { File export() throws Exception; }

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @FXML protected VBox          rootPane;
    @FXML protected TableView<T>  table;
    @FXML protected Button        btnExportarPdf;
    @FXML protected ProgressIndicator spinner;
    @FXML protected VBox          resumenBox;
    @FXML protected Button        btnToggleResumen;
    @FXML protected Label         helpResumen;
    @FXML protected TextField     searchField;
    @FXML protected HBox          filterBar;

    protected DatePicker dpDesde, dpHasta;
    protected Label lblCount;

    private int lastFiltered = 0;
    private int lastTotal    = 0;

    protected final ObservableList<T> data = FXCollections.observableArrayList();

    // ── Template entry point ─────────────────────────────────────────────────

    @FXML
    public void initialize() {
        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible(getClass().getSimpleName() + ".resumen.colapsado",
                btnToggleResumen, resumenBox, "Mostrar resumen", "Ocultar resumen");
        if (helpResumen != null) DialogUtil.enableClickToShowTooltip(helpResumen);
        setupTableBase();
        setupColumns();
        setupSortPersistence();
        onInitialize();
        setupColumnVisibilityButton();
        setupButtonState();
        loadData();
    }

    // ── Abstract hooks ───────────────────────────────────────────────────────

    protected abstract String emptyStateIcon();
    protected abstract String emptyStateTitle();
    protected String emptyStateSubtitle() { return ""; }
    protected abstract void setupColumns();
    protected abstract void onInitialize();
    protected abstract List<T> fetchAll() throws Exception;
    protected abstract void onDataLoaded(List<T> list);
    protected abstract File doExportPdf(T item) throws Exception;
    protected abstract String getLoadErrorMessage();

    // ── Overridable hooks ────────────────────────────────────────────────────

    protected void onTableDoubleClick(T item) { onExportarPdf(); }

    protected void addContextMenuItems(ContextMenu cm) {}

    protected ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem miPdf = new MenuItem("Exportar PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().add(miPdf);
        addContextMenuItems(cm);
        return cm;
    }

    /** No-op default — subclasses override to re-filter their data list. */
    protected void applyFilter() {}

    // ── Implemented common methods ───────────────────────────────────────────

    protected void setupTableBase() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(data);
        table.setPlaceholder(EmptyStateUtil.build(emptyStateIcon(), emptyStateTitle(), emptyStateSubtitle()));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                onTableDoubleClick(table.getSelectionModel().getSelectedItem());
        });
        table.setContextMenu(buildContextMenu());
        table.getSelectionModel().getSelectedItems()
            .addListener((ListChangeListener<T>) c -> updateCountDisplay());

        lblCount = new Label();
        lblCount.getStyleClass().add("table-count-label");
        lblCount.setVisible(false);
        lblCount.managedProperty().bind(lblCount.visibleProperty());   // hidden = no empty gap above the table
        if (rootPane != null) {
            VBox.setMargin(lblCount, new Insets(0, 0, 2, 4));
            int idx = rootPane.getChildren().indexOf(table);
            if (idx >= 0) rootPane.getChildren().add(idx, lblCount);
            else rootPane.getChildren().add(lblCount);
        }

        if (rootPane != null) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.isControlDown() && ev.getCode() == KeyCode.F && searchField != null) {
                    searchField.requestFocus();
                    searchField.selectAll();
                    ev.consume();
                }
            });
        }
    }

    protected void updateCount(int filtered, int total) {
        lastFiltered = filtered;
        lastTotal    = total;
        updateCountDisplay();
        if (filtered == 0 && total > 0 && isFilterActive()) {
            String q = searchField != null ? searchField.getText() : "";
            table.setPlaceholder(EmptyStateUtil.buildNoResults(q, this::clearFilters));
        } else {
            table.setPlaceholder(
                EmptyStateUtil.build(emptyStateIcon(), emptyStateTitle(), emptyStateSubtitle()));
        }
    }

    protected boolean isFilterActive() {
        if (searchField != null && searchField.getText() != null
                && !searchField.getText().isBlank()) return true;
        if (dpDesde != null && dpDesde.getValue() != null) return true;
        if (dpHasta != null && dpHasta.getValue() != null) return true;
        return false;
    }

    protected void clearFilters() {
        if (searchField != null) searchField.clear();
        if (dpDesde != null) dpDesde.setValue(null);
        if (dpHasta != null) dpHasta.setValue(null);
    }

    private void updateCountDisplay() {
        if (lblCount == null) return;
        int sel = table.getSelectionModel().getSelectedItems().size();
        if (lastTotal == 0 && sel == 0) { lblCount.setVisible(false); return; }
        if (sel > 1) {
            lblCount.setText(sel + " seleccionados de " + lastFiltered);
        } else {
            lblCount.setText(lastFiltered == lastTotal
                ? lastTotal + " registro" + (lastTotal != 1 ? "s" : "")
                : "Mostrando " + lastFiltered + " de " + lastTotal);
        }
        lblCount.setVisible(true);
    }

    /** Returns the rows to export: the current multi-selection if >1 row is selected,
     *  otherwise the full filtered list visible in the table. */
    protected List<T> exportTarget() {
        var sel = table.getSelectionModel().getSelectedItems();
        return sel.size() > 1 ? new ArrayList<>(sel) : new ArrayList<>(data);
    }

    protected void setupButtonState() {
        if (btnExportarPdf != null)
            btnExportarPdf.disableProperty().bind(
                table.getSelectionModel().selectedItemProperty().isNull());
    }

    protected void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        AppExecutor.submit(() -> {
            try {
                List<T> list = fetchAll();
                Platform.runLater(() -> {
                    onDataLoaded(list);
                    AnimationUtils.staggerTableRows(table);
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                });
            } catch (Exception e) {
                log.error("Error al cargar datos en {}", getClass().getSimpleName(), e);
                Platform.runLater(() -> {
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                    Scene scene = rootPane != null ? rootPane.getScene() : null;
                    if (scene != null) NotificacionUtil.error(scene, getLoadErrorMessage());
                });
            }
        });
    }

    @FXML protected void onRefresh() { loadData(); }

    @FXML
    protected void onExportarPdf() {
        T sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        exportarPdfAsync(sel, rootPane.getScene());
    }

    protected void exportarPdfAsync(T item, Scene scene) {
        DialogUtil.runAsync(
            () -> doExportPdf(item),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF")
        );
    }

    @FXML
    protected void onExportarPdfLote() {
        List<T> sel = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (sel.size() < 2) { onExportarPdf(); return; }
        Scene scene = rootPane != null ? rootPane.getScene() : null;
        if (scene == null) return;
        DialogUtil.runAsync(
            () -> exportarLoteZip(sel),
            file -> DialogUtil.showExportResultDialog(scene, file),
            e  -> NotificacionUtil.error(scene, "No se pudo exportar el lote")
        );
    }

    private File exportarLoteZip(List<T> items) throws Exception {
        File zip = File.createTempFile("sibim_lote_", ".zip");
        try (ZipOutputStream zos =
                new ZipOutputStream(new FileOutputStream(zip))) {
            int n = 0;
            for (T item : items) {
                try {
                    File pdf = doExportPdf(item);
                    if (pdf == null) continue;
                    String entryName = pdf.getName().isEmpty()
                        ? "documento_" + (++n) + ".pdf" : pdf.getName();
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(pdf.toPath(), zos);
                    zos.closeEntry();
                } catch (Exception ignored) { /* skip failed item */ }
            }
        }
        return zip;
    }

    protected void addLoteExportItem(ContextMenu cm) {
        MenuItem mi = new MenuItem("Exportar selección como ZIP");
        mi.setGraphic(new FontIcon("mdi2a-archive-arrow-down-outline"));
        mi.setOnAction(e -> onExportarPdfLote());
        mi.disableProperty().bind(
            Bindings.size(
                table.getSelectionModel().getSelectedItems()).lessThan(2));
        if (!cm.getItems().isEmpty()) cm.getItems().add(new SeparatorMenuItem());
        cm.getItems().add(mi);
    }

    // ── Sort persistence ──────────────────────────────────────────────────────

    protected void setupSortPersistence() {
        Preferences p = getFilterPrefsNode();
        String savedText = p.get("sortCol", "");
        String savedDir  = p.get("sortDir", "");
        if (!savedText.isBlank()) {
            table.getColumns().stream()
                .filter(c -> savedText.equals(c.getText()))
                .findFirst()
                .ifPresent(col -> {
                    col.setSortType(TableColumn.SortType.DESCENDING.name().equals(savedDir)
                        ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
                    table.getSortOrder().setAll(col);
                });
        }
        table.getSortOrder().addListener(
            (ListChangeListener<TableColumn<T, ?>>) change -> {
                if (table.getSortOrder().isEmpty()) {
                    p.remove("sortCol"); p.remove("sortDir");
                } else {
                    TableColumn<T, ?> col = table.getSortOrder().get(0);
                    p.put("sortCol", col.getText() != null ? col.getText() : "");
                    p.put("sortDir", col.getSortType().name());
                }
            });
    }

    // ── Column visibility picker ──────────────────────────────────────────────

    protected void setupColumnVisibilityButton() {
        if (filterBar == null) return;
        List<TableColumn<T, ?>> nameable = table.getColumns().stream()
            .filter(c -> c.getText() != null && !c.getText().isBlank())
            .toList();
        if (nameable.isEmpty()) return;
        MenuButton btnCols = new MenuButton();
        btnCols.setGraphic(new FontIcon("mdi2t-table-column"));
        btnCols.getStyleClass().add("btn-ghost");
        btnCols.setTooltip(new Tooltip("Mostrar / ocultar columnas"));
        for (TableColumn<T, ?> col : nameable) {
            CheckMenuItem mi = new CheckMenuItem(col.getText());
            mi.setSelected(col.isVisible());
            col.visibleProperty().addListener((obs, o, n) -> mi.setSelected(n));
            mi.selectedProperty().addListener((obs, o, n) -> col.setVisible(n));
            btnCols.getItems().add(mi);
        }
        Separator vs = new Separator();
        vs.setOrientation(javafx.geometry.Orientation.VERTICAL);
        filterBar.getChildren().addAll(vs, btnCols);
    }

    // ── Filter infrastructure ─────────────────────────────────────────────────

    protected Preferences getFilterPrefsNode() {
        String name = getClass().getSimpleName().toLowerCase().replace("controller", "");
        return Preferences.userRoot().node("sibim/filters/" + name);
    }

    protected void setupDateFilterBar(ListExporter excelFn, ListExporter csvFn) {
        dpDesde = new DatePicker(); dpDesde.setPromptText("Desde"); dpDesde.setPrefWidth(130);
        dpDesde.setConverter(FormatUtils.datePickerConverter());
        dpHasta = new DatePicker(); dpHasta.setPromptText("Hasta"); dpHasta.setPrefWidth(130);
        dpHasta.setConverter(FormatUtils.datePickerConverter());
        dpDesde.valueProperty().addListener((obs, o, n) -> applyFilter());
        dpHasta.valueProperty().addListener((obs, o, n) -> applyFilter());
        Button btnLimpiar = new Button();
        btnLimpiar.setGraphic(new FontIcon("mdi2c-close-circle-outline"));
        btnLimpiar.getStyleClass().add("btn-ghost");
        btnLimpiar.setTooltip(new Tooltip("Limpiar filtros de fecha"));
        btnLimpiar.setOnAction(e -> { dpDesde.setValue(null); dpHasta.setValue(null); });
        if (filterBar != null) {
            Separator vs = new Separator();
            vs.setOrientation(javafx.geometry.Orientation.VERTICAL);
            filterBar.getChildren().addAll(vs, dpDesde, dpHasta, btnLimpiar);
            if (excelFn != null || csvFn != null) {
                Separator vs2 = new Separator();
                vs2.setOrientation(javafx.geometry.Orientation.VERTICAL);
                MenuButton btnExp = new MenuButton();
                btnExp.setGraphic(new FontIcon("mdi2d-download"));
                btnExp.getStyleClass().add("btn-secondary");
                btnExp.setTooltip(new Tooltip("Exportar lista actual"));
                if (excelFn != null) {
                    MenuItem miExcel = new MenuItem("Exportar Excel");
                    miExcel.setGraphic(new FontIcon("mdi2m-microsoft-excel"));
                    miExcel.setOnAction(e -> doListExport(excelFn, "No se pudo exportar el Excel"));
                    btnExp.getItems().add(miExcel);
                }
                if (csvFn != null) {
                    MenuItem miCsv = new MenuItem("Exportar CSV");
                    miCsv.setGraphic(new FontIcon("mdi2f-file-delimited-outline"));
                    miCsv.setOnAction(e -> doListExport(csvFn, "No se pudo exportar el CSV"));
                    btnExp.getItems().add(miCsv);
                }
                filterBar.getChildren().addAll(vs2, btnExp);
            }
        }
    }

    private void doListExport(ListExporter fn, String errMsg) {
        Scene scene = filterBar != null ? filterBar.getScene()
            : rootPane != null ? rootPane.getScene() : null;
        DialogUtil.runAsync(fn::export,
            file -> DialogUtil.showExportResultDialog(scene, file),
            ex   -> NotificacionUtil.error(scene, errMsg));
    }

    protected void setupSearchListener() {
        if (searchField == null) return;
        String key = "sibim/search-history/"
            + getClass().getSimpleName().toLowerCase().replace("controller", "");
        SearchUtils.setupSearchHistory(key, searchField, this::applyFilter);
        SearchUtils.debounce(searchField, 260, q -> {
            getFilterPrefsNode().put("search", q != null ? q : "");
            applyFilter();
        });
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                if (searchField.getText() != null && !searchField.getText().isBlank()) {
                    searchField.clear();
                } else {
                    table.requestFocus();
                    table.getSelectionModel().clearSelection();
                }
                ev.consume();
            }
        });
    }

    protected void restoreFilterPrefs() {
        Preferences p = getFilterPrefsNode();
        String savedSearch = p.get("search", "");
        if (!savedSearch.isBlank() && searchField != null) searchField.setText(savedSearch);
        String savedDesde = p.get("desde", "");
        if (!savedDesde.isBlank() && dpDesde != null) {
            try { dpDesde.setValue(LocalDate.parse(savedDesde)); } catch (Exception ignored) {}
        }
        String savedHasta = p.get("hasta", "");
        if (!savedHasta.isBlank() && dpHasta != null) {
            try { dpHasta.setValue(LocalDate.parse(savedHasta)); } catch (Exception ignored) {}
        }
    }

    protected void saveFilterPrefs(String q, LocalDate desde, LocalDate hasta) {
        Preferences p = getFilterPrefsNode();
        p.put("search", q     != null ? q               : "");
        p.put("desde",  desde != null ? desde.toString() : "");
        p.put("hasta",  hasta != null ? hasta.toString() : "");
    }

    protected void saveFilterPrefs(String q, String estado,
                                    LocalDate desde, LocalDate hasta) {
        Preferences p = getFilterPrefsNode();
        p.put("search", q      != null ? q               : "");
        p.put("estado", estado != null ? estado           : "Todos");
        p.put("desde",  desde  != null ? desde.toString() : "");
        p.put("hasta",  hasta  != null ? hasta.toString() : "");
    }

    protected void restoreEstadoFilter(ComboBox<String> combo) {
        if (combo == null) return;
        String saved = getFilterPrefsNode().get("estado", "Todos");
        if (combo.getItems().contains(saved)) combo.setValue(saved);
    }
}
