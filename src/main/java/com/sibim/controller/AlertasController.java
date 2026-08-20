package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.ProductoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class AlertasController {

    private static final Logger log = LoggerFactory.getLogger(AlertasController.class);

    @FXML private TableView<Producto> tableAgotados;
    @FXML private TableColumn<Producto, String> colAgNombre;
    @FXML private TableColumn<Producto, String> colAgCodigo;
    @FXML private TableColumn<Producto, String> colAgArea;
    @FXML private Label lblAgotadosCount;

    @FXML private TableView<Producto> tableBajoStock;
    @FXML private TableColumn<Producto, String> colBsNombre;
    @FXML private TableColumn<Producto, String> colBsCodigo;
    @FXML private TableColumn<Producto, Integer> colBsStock;
    @FXML private TableColumn<Producto, Integer> colBsMin;
    @FXML private Label lblBajoStockCount;

    @FXML private TableView<Producto> tableGarantias;
    @FXML private TableColumn<Producto, String> colGaNombre;
    @FXML private TableColumn<Producto, String> colGaCodigo;
    @FXML private TableColumn<Producto, String> colGaFecha;
    @FXML private TableColumn<Producto, String> colGaDias;
    @FXML private Label lblGarantiasCount;

    @FXML private Label lblSinAlertas;
    @FXML private javafx.scene.control.ProgressIndicator spinner;
    @FXML private Button btnReponerAgotado;
    @FXML private Button btnReponerBajoStock;
    @FXML private Button btnReponerTodos;
    @FXML private TextField searchField;
    @FXML private Button btnClearSearch;
    @FXML private Label lblActualizado;
    @FXML private VBox  sectionAgotados;
    @FXML private VBox  sectionBajoStock;
    @FXML private VBox  sectionGarantias;

    private final ProductoService productoService = new ProductoService();

    private List<Producto> allAgotados  = List.of();
    private List<Producto> allBajoStock = List.of();
    private List<Producto> allGarantias = List.of();
    private Timeline autoRefresh;

    @FXML
    public void initialize() {
        setupColumns();
        tableAgotados.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableBajoStock.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableGarantias.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        loadData();
        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(sectionAgotados, sectionBajoStock, sectionGarantias), 300, 65);
        javafx.application.Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
        autoRefresh = new Timeline(new KeyFrame(Duration.minutes(5), e -> loadData()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();
        tableAgotados.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (btnReponerAgotado != null) btnReponerAgotado.setDisable(s == null);
        });
        tableBajoStock.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (btnReponerBajoStock != null) btnReponerBajoStock.setDisable(s == null);
        });

        if (searchField != null) {
            if (btnClearSearch != null) {
                searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            }
            SearchUtils.debounce(searchField, 260, this::applySearch);
        }
        tableAgotados.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tableAgotados.getSelectionModel().getSelectedItem();
                if (sel != null) showProductoInfo(sel, true);
            }
        });
        tableBajoStock.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tableBajoStock.getSelectionModel().getSelectedItem();
                if (sel != null) showProductoInfo(sel, false);
            }
        });
        tableAgotados.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ENTER
                    && tableAgotados.getSelectionModel().getSelectedItem() != null) {
                onReponerAgotado(); ev.consume();
            }
        });
        tableBajoStock.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ENTER
                    && tableBajoStock.getSelectionModel().getSelectedItem() != null) {
                onSolicitarBajoStock(); ev.consume();
            }
        });

        // Context menu for tableAgotados
        ContextMenu cmAg = new ContextMenu();
        MenuItem miAgReponer = new MenuItem("⬆  Registrar Entrada");
        miAgReponer.setOnAction(e -> onReponerAgotado());
        cmAg.getItems().add(miAgReponer);
        tableAgotados.setContextMenu(cmAg);

        // Context menu for tableBajoStock
        ContextMenu cmBs = new ContextMenu();
        MenuItem miBsReponer = new MenuItem("⬆  Registrar Entrada");
        miBsReponer.setOnAction(e -> onSolicitarBajoStock());
        cmBs.getItems().add(miBsReponer);
        tableBajoStock.setContextMenu(cmBs);
    }

    private void setupColumns() {
        colAgNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colAgCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colAgArea.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getArea()));
        colAgArea.setCellFactory(col -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item); tip.setText(item); setTooltip(tip);
            }
        });

        colBsNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colBsCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colBsStock.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("stockActual"));
        colBsMin.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("stockMinimo"));

        colBsStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("stock-low", "stock-warn");
                if (empty || item == null) { setText(null); return; }
                setText(String.valueOf(item));
                Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                getStyleClass().add(p != null && item <= p.getStockMinimo() ? "stock-low" : "stock-warn");
            }
        });
        colBsMin.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("cell-muted");
                if (empty || item == null) { setText(null); return; }
                setText(String.valueOf(item));
                getStyleClass().add("cell-muted");
            }
        });

        colGaNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colGaCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        colGaFecha.setCellValueFactory(c ->
            new SimpleStringProperty(FormatUtils.formatDate(c.getValue().getFechaVencimiento())));
        colGaDias.setCellValueFactory(c -> {
            LocalDate fv = c.getValue().getFechaVencimiento();
            if (fv == null) return new SimpleStringProperty("—");
            long dias = ChronoUnit.DAYS.between(LocalDate.now(), fv);
            if (dias < 0) return new SimpleStringProperty("Vencido");
            if (dias == 0) return new SimpleStringProperty("Vence hoy");
            return new SimpleStringProperty(dias + " días");
        });
        colGaDias.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("days-critical", "days-warn");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                switch (item) {
                    case "Vencido", "Vence hoy" -> getStyleClass().add("days-critical");
                    default -> {
                        try {
                            if (Integer.parseInt(item.split(" ")[0]) <= 3)
                                getStyleClass().add("days-warn");
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
    }

    private record AlertasData(List<Producto> agotados, List<Producto> bajoStock, List<Producto> garantias) {}

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        DialogUtil.runAsync(
            () -> new AlertasData(
                productoService.getAgotados(),
                productoService.getBajoStock(),
                productoService.getVencidosProximos(7)),
            data -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                allAgotados  = data.agotados();
                allBajoStock = data.bajoStock();
                allGarantias = data.garantias();
                if (btnReponerTodos != null) btnReponerTodos.setDisable(data.agotados().isEmpty());
                String query = searchField != null ? searchField.getText() : "";
                applySearch(query);
                if (lblActualizado != null)
                    lblActualizado.setText("Actualizado " +
                        java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            },
            e -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                if (tableAgotados.getScene() != null)
                    NotificacionUtil.error(tableAgotados.getScene(), "No se pudo cargar las alertas de inventario");
            }
        );
    }

    private void applySearch(String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<Producto> filtAgotados  = filter(allAgotados,  q);
        List<Producto> filtBajoStock = filter(allBajoStock, q);
        List<Producto> filtGarantias = filter(allGarantias, q);
        tableAgotados.getItems().setAll(filtAgotados);
        tableBajoStock.getItems().setAll(filtBajoStock);
        tableGarantias.getItems().setAll(filtGarantias);
        long cntAg = filtAgotados.size(),  cntBs = filtBajoStock.size(), cntGa = filtGarantias.size();
        boolean noFilter = q.isBlank();
        long totAg = allAgotados.size(), totBs = allBajoStock.size(), totGa = allGarantias.size();
        AnimationUtils.animateCount(lblAgotadosCount,  cntAg, 480, v -> v + (noFilter ? " bienes" : " / " + totAg));
        AnimationUtils.animateCount(lblBajoStockCount, cntBs, 480, v -> v + (noFilter ? " bienes" : " / " + totBs));
        AnimationUtils.animateCount(lblGarantiasCount, cntGa, 480, v -> v + (noFilter ? " bienes" : " / " + totGa));
        javafx.animation.PauseTransition sectionPop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(510));
        sectionPop.setOnFinished(e -> {
            if (sectionAgotados  != null && !allAgotados.isEmpty())  AnimationUtils.statCardPop(sectionAgotados);
            if (sectionBajoStock != null && !allBajoStock.isEmpty()) AnimationUtils.statCardPop(sectionBajoStock);
            if (sectionGarantias != null && !allGarantias.isEmpty()) AnimationUtils.statCardPop(sectionGarantias);
        });
        sectionPop.play();
        boolean sinAlertas = allAgotados.isEmpty() && allBajoStock.isEmpty() && allGarantias.isEmpty();
        boolean wasVisible = lblSinAlertas.isVisible();
        lblSinAlertas.setVisible(sinAlertas);
        lblSinAlertas.setManaged(sinAlertas);
        if (sinAlertas && !wasVisible) AnimationUtils.springIn(lblSinAlertas);
    }

    private List<Producto> filter(List<Producto> source, String q) {
        if (q.isBlank()) return source;
        return source.stream()
            .filter(p -> p.getNombre().toLowerCase().contains(q)
                      || p.getCodigo().toLowerCase().contains(q)
                      || (p.getArea() != null && p.getArea().toLowerCase().contains(q)))
            .toList();
    }

    @FXML private void onClearSearch() {
        if (searchField != null) { searchField.clear(); searchField.requestFocus(); }
    }

    @FXML private void onRefresh() {
        loadData();
        if (tableAgotados.getScene() != null)
            NotificacionUtil.info(tableAgotados.getScene(), "Alertas actualizadas");
    }

    @FXML
    private void onReponerTodosAgotados() {
        if (allAgotados.isEmpty()) return;
        openMovimientoForm(allAgotados.get(0).getId(), TipoMovimiento.ENTRADA);
    }

    @FXML
    private void onReponerAgotado() {
        Producto sel = tableAgotados.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(tableAgotados.getScene(), "Selecciona un bien de la lista primero");
            return;
        }
        openMovimientoForm(sel.getId(), TipoMovimiento.ENTRADA);
    }

    @FXML
    private void onSolicitarBajoStock() {
        Producto sel = tableBajoStock.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(tableBajoStock.getScene(), "Selecciona un bien de la lista primero");
            return;
        }
        openMovimientoForm(sel.getId(), TipoMovimiento.ENTRADA);
    }

    /** Navigates to Movimientos and opens the form there (pre-filled with
     *  this producto/tipo) instead of using a bare {@code new
     *  MovimientosController()} — that instance never went through FXML
     *  injection, so its @FXML fields (table, etc.) were all null, silently
     *  breaking the dialog's own success/error feedback and racing this
     *  screen's loadData() against the movement's async save. */
    private void openMovimientoForm(String productoId, TipoMovimiento tipo) {
        MainController main = MainController.getInstance();
        if (main == null) {
            log.warn("No se pudo abrir el formulario de movimiento: MainController no disponible");
            return;
        }
        try {
            main.navigateTo("movimientos");
            if (main.getCurrentController() instanceof MovimientosController ctrl) {
                ctrl.showMovimientoDialog(productoId, tipo);
            }
        } catch (Exception e) {
            log.error("Error al abrir el formulario de movimiento desde Alertas", e);
            if (tableAgotados.getScene() != null)
                NotificacionUtil.error(tableAgotados.getScene(), "Error al abrir el formulario de movimiento");
        }
    }

    private void showProductoInfo(Producto p, boolean agotado) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(420);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        String color1 = agotado ? "#DC2626" : "#D97706";
        String color2 = agotado ? "#B91C1C" : "#B45309";
        String icon   = agotado ? "🚫" : "⚠";
        String sub    = agotado ? "Stock agotado — requiere reposición inmediata"
                                : "Stock actual por debajo del mínimo establecido";

        HBox header = DialogUtil.gradientHeader(icon, p.getNombre(), sub, color1, color2);

        GridPane grid = DialogUtil.formGrid(100);
        int r = 0;
        grid.add(DialogUtil.fieldLabel("Código"), 0, r);
        Label codLbl = new Label(p.getCodigo());
        codLbl.getStyleClass().add("codigo-cell");
        grid.add(codLbl, 1, r++);
        grid.add(DialogUtil.fieldLabel("Área"),       0, r); grid.add(new Label(p.getArea() != null ? p.getArea() : "—"), 1, r++);

        Label stockLbl = new Label(String.valueOf(p.getStockActual()));
        stockLbl.getStyleClass().add(agotado ? "stock-low" : "stock-warn");
        grid.add(DialogUtil.fieldLabel("Stock actual"), 0, r); grid.add(stockLbl, 1, r++);

        if (p.getStockMinimo() > 0) {
            grid.add(DialogUtil.fieldLabel("Stock mínimo"), 0, r);
            grid.add(new Label(String.valueOf(p.getStockMinimo())), 1, r++);
        }

        VBox content = new VBox(0, header, grid);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 260, 70);
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    /** Stops the auto-refresh timer. Must be called before this controller's view is discarded. */
    public void stopAutoRefresh() {
        if (autoRefresh != null) autoRefresh.stop();
    }
}
