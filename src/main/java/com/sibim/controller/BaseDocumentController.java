package com.sibim.controller;

import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

/**
 * Abstract base for document controllers (Resguardos, Préstamos, Actas).
 *
 * Template:  initialize() → setupTableBase() → setupColumns() → onInitialize()
 *                         → setupButtonState() → loadData()
 *
 * Subclasses supply: type parameter T, column factories, async fetch, stats
 * update, PDF delegate, and error message.  Everything else is inherited.
 */
public abstract class BaseDocumentController<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @FXML protected VBox     rootPane;
    @FXML protected TableView<T> table;
    @FXML protected Button       btnExportarPdf;
    @FXML protected ProgressIndicator spinner;
    @FXML protected VBox     resumenBox;
    @FXML protected Button   btnToggleResumen;
    @FXML protected Label    helpResumen;

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
        onInitialize();
        setupButtonState();
        loadData();
    }

    // ── Abstract hooks ───────────────────────────────────────────────────────

    /** Configure all TableColumn cell-value / cell-factories. */
    protected abstract void setupColumns();

    /**
     * Controller-specific init: permissions, search, filters, extra bindings.
     * Runs after column setup and before button state / data load.
     */
    protected abstract void onInitialize();

    /** Fetch all records on a background thread.  May throw. */
    protected abstract List<T> fetchAll() throws Exception;

    /**
     * Called on the FX thread after a successful fetch.
     * Must populate {@link #data} (directly or via filter) and update stat labels.
     */
    protected abstract void onDataLoaded(List<T> list);

    /** Generate the PDF for {@code item} on a background thread.  May throw. */
    protected abstract File doExportPdf(T item) throws Exception;

    /** Error text shown in the notification when loadData() fails. */
    protected abstract String getLoadErrorMessage();

    // ── Overridable hooks ────────────────────────────────────────────────────

    /**
     * Table double-click action.  Default: export PDF of selected row.
     * Prestamos overrides to show the detail dialog instead.
     */
    protected void onTableDoubleClick(T item) { onExportarPdf(); }

    /**
     * Add extra items to the context menu after the default "Exportar PDF" entry.
     * Override in subclasses that need additional actions (e.g. Resguardos adds
     * "Cancelar resguardo").  For a completely different order, override
     * {@link #buildContextMenu()} directly.
     */
    protected void addContextMenuItems(ContextMenu cm) {}

    /**
     * Build the table context menu.  The default puts "Exportar PDF" first and
     * then calls {@link #addContextMenuItems} for subclass additions.
     * Prestamos overrides the whole method to put "Registrar devolución" first.
     */
    protected ContextMenu buildContextMenu() {
        ContextMenu cm = new ContextMenu();
        MenuItem miPdf = new MenuItem("Exportar PDF");
        miPdf.setGraphic(new FontIcon("mdi2f-file-pdf-box"));
        miPdf.setOnAction(e -> onExportarPdf());
        cm.getItems().add(miPdf);
        addContextMenuItems(cm);
        return cm;
    }

    // ── Implemented common methods ───────────────────────────────────────────

    protected void setupTableBase() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(data);
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                onTableDoubleClick(table.getSelectionModel().getSelectedItem());
        });
        table.setContextMenu(buildContextMenu());
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
                    NotificacionUtil.error(rootPane.getScene(), getLoadErrorMessage());
                });
            }
        });
    }

    @FXML
    protected void onRefresh() { loadData(); }

    @FXML
    protected void onExportarPdf() {
        T sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        exportarPdfAsync(sel, rootPane.getScene());
    }

    protected void exportarPdfAsync(T item, Scene scene) {
        DialogUtil.runAsync(
            () -> doExportPdf(item),
            file -> {
                if (file == null) return;
                try { Desktop.getDesktop().open(file); }
                catch (Exception e) {
                    NotificacionUtil.advertencia(scene, "PDF generado: " + file.getAbsolutePath());
                }
            },
            e -> NotificacionUtil.error(scene, "No se pudo generar el PDF")
        );
    }
}
