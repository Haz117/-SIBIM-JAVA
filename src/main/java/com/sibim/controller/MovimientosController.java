package com.sibim.controller;

import com.sibim.controller.dialogs.MovimientoDialogFactory;
import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.repository.ProductoRepository;
import com.sibim.service.MovimientoService;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.PaginationUtils;
import com.sibim.util.SearchUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.util.List;

public class MovimientosController {

    private static final Logger log = LoggerFactory.getLogger(MovimientosController.class);

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private HBox tipoChipsBar;
    @FXML private DatePicker desdeFilter;
    @FXML private DatePicker hastaFilter;
    @FXML private TableView<Movimiento> table;
    @FXML private TableColumn<Movimiento, String> colProducto;
    @FXML private TableColumn<Movimiento, String> colTipo;
    @FXML private TableColumn<Movimiento, Integer> colCantidad;
    @FXML private TableColumn<Movimiento, String> colStock;
    @FXML private TableColumn<Movimiento, String> colMotivo;
    @FXML private TableColumn<Movimiento, String> colReferencia;
    @FXML private TableColumn<Movimiento, String> colUsuario;
    @FXML private TableColumn<Movimiento, String> colFecha;
    @FXML private Label lblTotal;
    @FXML private ProgressIndicator spinner;
    @FXML private Button btnNuevo;
    @FXML private Button btnPresetHoy;
    @FXML private Button btnPresetSemana;
    @FXML private Button btnPresetMes;
    @FXML private ComboBox<Integer> pageSizeBox;
    @FXML private Label lblPage;
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label lblStatTotalMov;
    @FXML private Label lblStatEntradas;
    @FXML private Label lblStatSalidas;
    @FXML private Label lblStatAjustes;
    @FXML private Button btnDelete;
    @FXML private Button btnClearSearch;

    private final MovimientoService movimientoService = new MovimientoService();
    private final ProductoRepository productoRepo = new ProductoRepository();
    private final ReporteService reporteService = new ReporteService();

    private ObservableList<Movimiento> allData = FXCollections.observableArrayList();
    private ObservableList<Movimiento> filteredData = FXCollections.observableArrayList();
    private int currentPage = 0;
    private int pageSize = 25;
    private ToggleGroup tipoChipGroup;
    private Label emptyStateMsg;
    private Label emptyStateHint;
    private Button btnEmptyLimpiar;

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        setupTipoChips();
        setupPagination();

        boolean canCreate = SessionManager.isAdmin() || SessionManager.isSecretario();
        btnNuevo.setVisible(canCreate);
        btnNuevo.setManaged(canCreate);
        if (rootPane != null && canCreate) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.N && ev.isControlDown()) {
                    onNuevoMovimiento(); ev.consume();
                }
            });
        }

        // Selection → enable/disable delete button
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (btnDelete != null) btnDelete.setDisable(sel == null);
        });

        // Delete key on table
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.DELETE
                    && table.getSelectionModel().getSelectedItem() != null) {
                onDelete(); ev.consume();
            }
        });

        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                showMovimientoDetail(table.getSelectionModel().getSelectedItem());
        });

        // Context menu
        ContextMenu cm = new ContextMenu();
        MenuItem cmDetalle = new MenuItem("👁  Ver detalle");
        cmDetalle.setOnAction(e -> {
            Movimiento sel = table.getSelectionModel().getSelectedItem();
            if (sel != null) showMovimientoDetail(sel);
        });
        cm.getItems().add(cmDetalle);
        boolean canDelete = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (canDelete) {
            cm.getItems().add(new SeparatorMenuItem());
            MenuItem cmEliminar = new MenuItem("✕  Eliminar");
            cmEliminar.setOnAction(e -> onDelete());
            cm.getItems().add(cmEliminar);
        }
        table.setContextMenu(cm);

        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        loadData();
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : ""));
        colProducto.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTipo().getEtiqueta()));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colStock.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getStockAnterior() + " → " + c.getValue().getStockNuevo()));
        colStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null); setText(null);
                if (empty || item == null || getTableRow() == null) return;
                Movimiento m = getTableRow().getItem();
                if (m == null) return;
                int antes = m.getStockAnterior(), despues = m.getStockNuevo();
                Label lblAntes = new Label(String.valueOf(antes));
                lblAntes.getStyleClass().add("stock-before");
                String arrowClass = antes < despues ? "stock-arrow-up" : (antes > despues ? "stock-arrow-down" : "stock-arrow-neutral");
                String arrowSym   = antes < despues ? "↑" : (antes > despues ? "↓" : "·");
                Label lblArrow = new Label(arrowSym);
                lblArrow.getStyleClass().add(arrowClass);
                String despuesClass = despues <= 0 ? "stock-after-empty" : (despues < antes ? "stock-after-warn" : "stock-after-ok");
                Label lblDespues = new Label(String.valueOf(despues));
                lblDespues.getStyleClass().add(despuesClass);
                HBox box = new HBox(4, lblAntes, lblArrow, lblDespues);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        colMotivo.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getMotivo() != null ? c.getValue().getMotivo() : ""));
        colMotivo.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
        colReferencia.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getReferencia() != null ? c.getValue().getReferencia() : ""));
        colReferencia.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("codigo-cell");
                if (empty || item == null || item.isBlank()) { setText(null); return; }
                setText(item);
                getStyleClass().add("codigo-cell");
            }
        });
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsuarioNombre()));
        colFecha.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDateTime(c.getValue().getCreadoEn())));

        // Tipo badge cell
        colTipo.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Entrada"       -> "cell-badge-success";
            case "Salida"        -> "cell-badge-danger";
            case "Ajuste"        -> "cell-badge-warning";
            case "Transferencia" -> "cell-badge-blue";
            default              -> "cell-badge-purple";
        }));

        // Cantidad coloring based on tipo
        colCantidad.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(null); getStyleClass().removeAll("stock-ok","stock-low","stock-warn");
                if (empty || value == null) return;
                setText(String.valueOf(value));
                if (getTableRow() != null && getTableRow().getItem() != null) {
                    String tipo = ((Movimiento) getTableRow().getItem()).getTipo().getEtiqueta();
                    if ("Entrada".equals(tipo)) getStyleClass().add("stock-ok");
                    else if ("Salida".equals(tipo)) getStyleClass().add("stock-low");
                    else getStyleClass().add("stock-warn");
                }
            }
        });

        // Row tints by movement type
        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Movimiento m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("row-entrada","row-salida","row-ajuste","row-transferencia");
                if (!empty && m != null) {
                    String clase = switch (m.getTipo().getEtiqueta()) {
                        case "Entrada"       -> "row-entrada";
                        case "Salida"        -> "row-salida";
                        case "Ajuste"        -> "row-ajuste";
                        case "Transferencia" -> "row-transferencia";
                        default              -> null;
                    };
                    if (clase != null) getStyleClass().add(clase);
                }
            }
        });

        table.getSortOrder().clear();
        colFecha.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(colFecha);

        // Smart empty state
        Label emptyIcon = new Label("↕");
        emptyIcon.getStyleClass().add("empty-icon-lg");
        emptyStateMsg = new Label("No hay movimientos en el sistema");
        emptyStateMsg.getStyleClass().add("empty-state-msg");
        btnEmptyLimpiar = new Button("Limpiar filtros");
        btnEmptyLimpiar.getStyleClass().add("btn-secondary");
        btnEmptyLimpiar.setOnAction(e -> {
            searchField.clear();
            desdeFilter.setValue(null);
            hastaFilter.setValue(null);
            setActivePreset(null);
            if (tipoChipGroup != null)
                tipoChipGroup.getToggles().stream()
                    .filter(t -> "Todos".equals(((ToggleButton) t).getText()))
                    .findFirst().ifPresent(t -> t.setSelected(true));
            currentPage = 0;
            applyFilters();
        });
        btnEmptyLimpiar.setVisible(false); btnEmptyLimpiar.setManaged(false);
        boolean canAddMov = SessionManager.isAdmin() || SessionManager.isSecretario();
        emptyStateHint = new Label(canAddMov ? "Presiona Ctrl+N para registrar el primer movimiento" : "");
        emptyStateHint.getStyleClass().add("empty-state-hint");
        javafx.scene.layout.VBox emptyState = new javafx.scene.layout.VBox(8, emptyIcon, emptyStateMsg, btnEmptyLimpiar, emptyStateHint);
        emptyState.setAlignment(javafx.geometry.Pos.CENTER);
        table.setPlaceholder(emptyState);
    }

    private void setupTipoChips() {
        tipoChipGroup = new ToggleGroup();
        String[][] chips = {
            {"Todos",         null},
            {"Entrada",       "filter-chip-green"},
            {"Salida",        "filter-chip-danger"},
            {"Ajuste",        "filter-chip-amber"},
            {"Transferencia", "filter-chip-teal"}
        };
        for (String[] entry : chips) {
            ToggleButton chip = new ToggleButton(entry[0]);
            chip.setToggleGroup(tipoChipGroup);
            chip.getStyleClass().add("filter-chip");
            if (entry[1] != null) chip.getStyleClass().add(entry[1]);
            if ("Todos".equals(entry[0])) chip.setSelected(true);
            chip.setOnAction(e -> { currentPage = 0; applyFilters(); });
            tipoChipsBar.getChildren().add(chip);
        }
    }

    private void setupFilters() {
        SearchUtils.debounce(searchField, 280, q -> { currentPage = 0; applyFilters(); });
        desdeFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; applyFilters(); setActivePreset(null); });
        hastaFilter.valueProperty().addListener((o, a, b) -> { currentPage = 0; applyFilters(); setActivePreset(null); });
    }

    private void setupPagination() {
        pageSizeBox.setItems(FXCollections.observableArrayList(10, 25, 50, 100));
        pageSizeBox.setValue(25);
        pageSizeBox.setOnAction(e -> {
            pageSize = pageSizeBox.getValue();
            currentPage = 0;
            updateTablePage();
        });
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        new Thread(new Task<List<Movimiento>>() {
            @Override protected List<Movimiento> call() throws Exception { return movimientoService.getAll(); }
            @Override protected void succeeded() {
                allData.setAll(getValue());
                currentPage = 0;
                applyFilters();
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
            }
            @Override protected void failed() {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.error(table.getScene(), "No se pudo cargar los movimientos");
            }
        }).start();
    }

    private String getSelectedTipo() {
        if (tipoChipGroup == null) return "Todos";
        Toggle t = tipoChipGroup.getSelectedToggle();
        return t == null ? "Todos" : ((ToggleButton) t).getText();
    }

    private void applyFilters() {
        if (table == null) return;
        if (desdeFilter != null && hastaFilter != null
            && desdeFilter.getValue() != null && hastaFilter.getValue() != null
            && desdeFilter.getValue().isAfter(hastaFilter.getValue())) {
            NotificacionUtil.advertencia(table.getScene(), "La fecha inicial debe ser anterior a la fecha final");
            return;
        }
        String query = searchField != null ? searchField.getText().toLowerCase() : "";
        String tipo = getSelectedTipo();
        LocalDate desde = desdeFilter != null ? desdeFilter.getValue() : null;
        LocalDate hasta = hastaFilter != null ? hastaFilter.getValue() : null;

        filteredData.setAll(allData.stream()
            .filter(m -> query.isBlank()
                || (m.getProductoNombre() != null && m.getProductoNombre().toLowerCase().contains(query))
                || (m.getReferencia() != null && m.getReferencia().toLowerCase().contains(query))
                || (m.getMotivo() != null && m.getMotivo().toLowerCase().contains(query)))
            .filter(m -> "Todos".equals(tipo) || m.getTipo().getEtiqueta().equals(tipo))
            .filter(m -> desde == null || !m.getCreadoEn().toLocalDate().isBefore(desde))
            .filter(m -> hasta == null || !m.getCreadoEn().toLocalDate().isAfter(hasta))
            .toList());

        boolean hasFilters = !query.isBlank() || !tipo.equals("Todos") || desde != null || hasta != null;
        if (emptyStateMsg != null)
            emptyStateMsg.setText(hasFilters
                ? "No se encontraron movimientos con esos filtros"
                : "No hay movimientos registrados en el sistema");
        if (btnEmptyLimpiar != null) {
            btnEmptyLimpiar.setVisible(hasFilters);
            btnEmptyLimpiar.setManaged(hasFilters);
        }
        if (emptyStateHint != null) {
            boolean canAddMov = SessionManager.isAdmin() || SessionManager.isSecretario();
            emptyStateHint.setVisible(!hasFilters && canAddMov);
            emptyStateHint.setManaged(!hasFilters && canAddMov);
        }

        updateMovStats();
        updateTablePage();
    }

    private void updateMovStats() {
        long total    = filteredData.size();
        long entradas = filteredData.stream().filter(m -> "Entrada".equals(m.getTipo().getEtiqueta())).count();
        long salidas  = filteredData.stream().filter(m -> "Salida".equals(m.getTipo().getEtiqueta())).count();
        long ajustes  = filteredData.stream().filter(m ->
            "Ajuste".equals(m.getTipo().getEtiqueta()) || "Transferencia".equals(m.getTipo().getEtiqueta())).count();
        if (lblStatTotalMov != null) lblStatTotalMov.setText(String.valueOf(total));
        if (lblStatEntradas != null) lblStatEntradas.setText(String.valueOf(entradas));
        if (lblStatSalidas  != null) lblStatSalidas.setText(String.valueOf(salidas));
        if (lblStatAjustes  != null) lblStatAjustes.setText(String.valueOf(ajustes));
    }

    private void updateTablePage() {
        PaginationUtils.updatePage(table, filteredData, currentPage, pageSize,
            lblTotal, lblPage, btnPrev, btnNext, "movimiento", "movimientos");
    }

    @FXML private void onPrev() { if (currentPage > 0) { currentPage--; updateTablePage(); } }
    @FXML private void onNext() { currentPage++; updateTablePage(); }
    @FXML private void onRefresh() { loadData(); }

    @FXML private void onPresetHoy() {
        LocalDate hoy = LocalDate.now();
        desdeFilter.setValue(hoy); hastaFilter.setValue(hoy);
        setActivePreset(btnPresetHoy);
    }
    @FXML private void onPresetSemana() {
        LocalDate hoy = LocalDate.now();
        desdeFilter.setValue(hoy.minusDays(6)); hastaFilter.setValue(hoy);
        setActivePreset(btnPresetSemana);
    }
    @FXML private void onPresetMes() {
        LocalDate hoy = LocalDate.now();
        desdeFilter.setValue(hoy.withDayOfMonth(1)); hastaFilter.setValue(hoy);
        setActivePreset(btnPresetMes);
    }
    @FXML private void onLimpiarFechas() {
        desdeFilter.setValue(null); hastaFilter.setValue(null);
        setActivePreset(null);
    }

    private void setActivePreset(Button active) {
        for (Button b : new Button[]{btnPresetHoy, btnPresetSemana, btnPresetMes}) {
            if (b == null) continue;
            b.getStyleClass().remove("btn-preset-active");
            if (b == active) b.getStyleClass().add("btn-preset-active");
        }
    }

    @FXML
    private void onNuevoMovimiento() { showMovimientoDialog(null, null); }

    @FXML
    private void onDelete() {
        Movimiento sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona un movimiento para eliminar");
            return;
        }
        if (!ConfirmacionUtil.confirmarEliminar("este movimiento")) return;
        DialogUtil.runAsync(
            () -> movimientoService.eliminar(sel.getId()),
            () -> {
                allData.remove(sel);
                applyFilters();
                NotificacionUtil.exito(table.getScene(), "Movimiento eliminado");
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof MovimientoService.ValidationException ? e.getMessage() : "No se pudo eliminar el movimiento")
        );
    }

    @FXML
    private void onExportCsv() {
        DialogUtil.runAsync(
            () -> reporteService.exportMovimientosCsv(
                desdeFilter != null ? desdeFilter.getValue() : null,
                hastaFilter != null ? hastaFilter.getValue() : null),
            file -> {
                NotificacionUtil.exito(table.getScene(), "CSV exportado correctamente");
                openFile(file);
            },
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el CSV")
        );
    }

    @FXML
    private void onExportExcel() {
        DialogUtil.runAsync(
            () -> reporteService.exportMovimientosExcel(
                desdeFilter != null ? desdeFilter.getValue() : null,
                hastaFilter != null ? hastaFilter.getValue() : null),
            file -> {
                NotificacionUtil.exito(table.getScene(), "Excel exportado correctamente");
                openFile(file);
            },
            e -> NotificacionUtil.error(table.getScene(), "No se pudo exportar el Excel")
        );
    }

    public void showMovimientoDialog(String preProductoId, TipoMovimiento preTipo) {
        showMovimientoDialog(preProductoId, preTipo, null);
    }

    private void showMovimientoDialog(String preProductoId, TipoMovimiento preTipo, MovimientoDialogFactory.Result retryFrom) {
        try {
            List<Producto> productos = productoRepo.findAll();
            MovimientoDialogFactory.show(productos, preProductoId, preTipo, retryFrom).ifPresent(r ->
                DialogUtil.runAsync(
                    () -> movimientoService.registrar(
                        r.producto().getId(), r.tipo(), r.cantidad(), r.motivo(), r.referencia(), r.areaDestino()),
                    m -> {
                        if (table != null) {
                            allData.add(0, m);
                            currentPage = 0;
                            applyFilters();
                            if (table.getScene() != null)
                                NotificacionUtil.exito(table.getScene(), "Movimiento registrado correctamente");
                        }
                    },
                    e -> {
                        // Reopen pre-filled with what the user entered (r has
                        // it all) instead of just erroring and losing it —
                        // same reasoning as ProductosController#showProductDialog.
                        if (table != null && table.getScene() != null)
                            NotificacionUtil.error(table.getScene(),
                                (e instanceof MovimientoService.ValidationException ? e.getMessage() : "No se pudo registrar el movimiento")
                                    + " — revisa los datos e inténtalo de nuevo");
                        Platform.runLater(() -> showMovimientoDialog(preProductoId, preTipo, r));
                    }
                ));
        } catch (Exception e) {
            log.error("Error al abrir el formulario de movimiento", e);
            if (table != null && table.getScene() != null)
                NotificacionUtil.error(table.getScene(), "Error al abrir el formulario. Verifica la conexión a la base de datos.");
        }
    }

    private void openFile(File file) {
        try { Desktop.getDesktop().open(file); }
        catch (Exception e) {
            log.error("No se pudo abrir el archivo {}", file, e);
            if (table != null && table.getScene() != null)
                NotificacionUtil.error(table.getScene(), "No se pudo abrir el archivo");
        }
    }

    private void showMovimientoDetail(Movimiento m) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(470);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        String tipoIcon = switch (m.getTipo()) {
            case ENTRADA       -> "⬆";
            case SALIDA        -> "⬇";
            case AJUSTE        -> "⇄";
            case TRANSFERENCIA -> "→";
        };
        String color1 = switch (m.getTipo()) {
            case ENTRADA       -> "#059669";
            case SALIDA        -> "#DC2626";
            case AJUSTE        -> "#D97706";
            case TRANSFERENCIA -> "#2563EB";
        };
        String color2 = switch (m.getTipo()) {
            case ENTRADA       -> "#047857";
            case SALIDA        -> "#B91C1C";
            case AJUSTE        -> "#B45309";
            case TRANSFERENCIA -> "#1D4ED8";
        };

        HBox header = DialogUtil.gradientHeader(tipoIcon,
            m.getTipo().getEtiqueta() + "  —  " + m.getCantidad() + " uds.",
            m.getProductoNombre(),
            color1, color2);

        GridPane grid = DialogUtil.formGrid(120);
        int r = 0;

        // Stock change row
        HBox stockRow = new HBox(10);
        stockRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label antes = new Label(String.valueOf(m.getStockAnterior()));
        antes.getStyleClass().add("dlg-stock-val");
        Label arrowLbl = new Label("→");
        boolean up = m.getStockNuevo() > m.getStockAnterior();
        boolean down = m.getStockNuevo() < m.getStockAnterior();
        arrowLbl.getStyleClass().add(up ? "dlg-stock-arrow-up" : down ? "dlg-stock-arrow-down" : "dlg-stock-arrow");
        Label despues = new Label(String.valueOf(m.getStockNuevo()));
        despues.getStyleClass().add(m.getStockNuevo() <= 0 ? "dlg-stock-new-empty"
            : up ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
        stockRow.getChildren().addAll(antes, arrowLbl, despues);

        Label fProducto = new Label(m.getProductoNombre());
        fProducto.setWrapText(true);
        Label fMotivo    = new Label(m.getMotivo()     != null && !m.getMotivo().isBlank()     ? m.getMotivo()     : "—");
        Label fRef       = new Label(m.getReferencia() != null && !m.getReferencia().isBlank() ? m.getReferencia() : "—");
        Label fUsuario   = new Label(m.getUsuarioNombre());
        Label fFecha     = new Label(FormatUtils.formatDateTime(m.getCreadoEn()));

        for (Label l : new Label[]{fProducto, fMotivo, fRef, fUsuario, fFecha})
            l.getStyleClass().add("dlg-detail-value");

        grid.add(DialogUtil.fieldLabel("Bien"),           0, r); grid.add(fProducto, 1, r++);
        grid.add(DialogUtil.fieldLabel("Stock"),          0, r); grid.add(stockRow,  1, r++);
        if (m.getTipo() == TipoMovimiento.TRANSFERENCIA && m.getAreaDestino() != null) {
            HBox areaRow = new HBox(8);
            areaRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Label areaOrigenLbl = new Label(m.getAreaOrigen() != null ? m.getAreaOrigen() : "—");
            areaOrigenLbl.getStyleClass().add("dlg-detail-value");
            Label areaArrow = new Label("→");
            areaArrow.getStyleClass().add("dlg-stock-arrow");
            Label areaDestinoLbl = new Label(m.getAreaDestino());
            areaDestinoLbl.getStyleClass().add("dlg-detail-value");
            areaRow.getChildren().addAll(areaOrigenLbl, areaArrow, areaDestinoLbl);
            grid.add(DialogUtil.fieldLabel("Área"), 0, r); grid.add(areaRow, 1, r++);
        }
        grid.add(DialogUtil.fieldLabel("Motivo"),         0, r); grid.add(fMotivo,   1, r++);
        grid.add(DialogUtil.fieldLabel("Referencia"),     0, r); grid.add(fRef,      1, r++);
        grid.add(DialogUtil.fieldLabel("Registrado por"), 0, r); grid.add(fUsuario,  1, r++);
        grid.add(DialogUtil.fieldLabel("Fecha"),          0, r); grid.add(fFecha,    1, r);

        dialog.getDialogPane().setContent(new VBox(0, header, grid));
        dialog.showAndWait();
    }
}
