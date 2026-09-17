package com.sibim.controller;

import com.sibim.model.Producto;
import org.kordamp.ikonli.javafx.FontIcon;
import com.sibim.model.enums.TipoMovimiento;
import com.sibim.service.EmailService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppExecutor;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
    @FXML private VBox  rootPane;
    @FXML private VBox  sectionAgotados;
    @FXML private VBox  sectionBajoStock;
    @FXML private VBox  sectionGarantias;
    @FXML private VBox  sectionMantenimiento;

    @FXML private TableView<Producto>           tableMantenimiento;
    @FXML private Button btnResetColumns;
    @FXML private TableColumn<Producto, String> colMantNombre;
    @FXML private TableColumn<Producto, String> colMantCodigo;
    @FXML private TableColumn<Producto, String> colMantArea;
    @FXML private TableColumn<Producto, String> colMantFecha;
    @FXML private TableColumn<Producto, String> colMantNotas;
    @FXML private Label lblMantenimientoCount;
    @FXML private Label helpAgotados;
    @FXML private Label helpBajoStock;
    @FXML private Label helpGarantias;
    @FXML private Label helpMantenimiento;
    @FXML private Label helpResumen;
    @FXML private VBox resumenBox;
    @FXML private Button btnToggleResumen;

    // ── Collapsible section headers ───────────────────────────────────
    @FXML private HBox headerAgotados;
    @FXML private HBox headerBajoStock;
    @FXML private HBox headerGarantias;
    @FXML private HBox headerMantenimiento;
    @FXML private VBox contentAgotados;
    @FXML private VBox contentBajoStock;
    @FXML private VBox contentGarantias;
    @FXML private VBox contentMantenimiento;
    @FXML private FontIcon chevronAgotados;
    @FXML private FontIcon chevronBajoStock;
    @FXML private FontIcon chevronGarantias;
    @FXML private FontIcon chevronMantenimiento;

    // ── Resumen rápido (stat cards al tope) ──────────────────────────
    @FXML private VBox    statCardAgotados;
    @FXML private VBox    statCardBajoStockSum;
    @FXML private VBox    statCardGarantiasSum;
    @FXML private Label   lblSumAgotados;
    @FXML private Label   lblSumBajoStock;
    @FXML private Label   lblSumGarantias;
    @FXML private Label   lblSumGarantiasDetalle;
    @FXML private javafx.scene.control.ProgressBar pbAgotados;
    @FXML private javafx.scene.control.ProgressBar pbBajoStock;
    @FXML private javafx.scene.control.ProgressBar pbGarantias;

    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/alertas");

    private final ProductoService productoService = new ProductoService();
    private final MovimientoService movimientoService = new MovimientoService();
    private final ReporteService reporteService = new ReporteService();

    private List<Producto> allAgotados      = List.of();
    private List<Producto> allBajoStock     = List.of();
    private List<Producto> allGarantias     = List.of();
    private List<Producto> allMantenimiento = List.of();
    private Timeline autoRefresh;
    private javafx.event.EventHandler<javafx.scene.input.KeyEvent> keyFilter;

    @FXML
    public void initialize() {
        setupColumns();
        tableAgotados.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableBajoStock.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableGarantias.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableAgotados.setPlaceholder(alertaOkNode("Sin bienes agotados"));
        tableBajoStock.setPlaceholder(alertaOkNode("Sin bienes con bajo stock"));
        tableGarantias.setPlaceholder(alertaOkNode("Sin garantías próximas a vencer"));
        if (tableMantenimiento != null) {
            tableMantenimiento.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tableMantenimiento.setPlaceholder(alertaOkNode("Sin revisiones en los próximos 30 días"));
        }
        if (btnResetColumns != null) {
            Runnable r1 = com.sibim.util.DialogUtil.captureColumnReset(tableAgotados, null);
            Runnable r2 = com.sibim.util.DialogUtil.captureColumnReset(tableBajoStock, null);
            Runnable r3 = com.sibim.util.DialogUtil.captureColumnReset(tableGarantias, null);
            Runnable r4 = tableMantenimiento != null
                ? com.sibim.util.DialogUtil.captureColumnReset(tableMantenimiento, null) : null;
            btnResetColumns.setOnAction(e -> { r1.run(); r2.run(); r3.run(); if (r4 != null) r4.run(); });
        }
        loadData();
        java.util.List<javafx.scene.Node> fadeNodes = new java.util.ArrayList<>(java.util.List.of(
            statCardAgotados, statCardBajoStockSum, statCardGarantiasSum,
            sectionAgotados, sectionBajoStock, sectionGarantias));
        if (sectionMantenimiento != null) fadeNodes.add(sectionMantenimiento);
        AnimationUtils.staggeredFadeInUp(fadeNodes, 250, 60);
        javafx.application.Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
        if (rootPane != null) {
            keyFilter = ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.F && ev.isControlDown()) {
                    if (searchField != null) { searchField.requestFocus(); searchField.selectAll(); }
                    ev.consume();
                }
            };
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, keyFilter);
        }
        autoRefresh = new Timeline(new KeyFrame(Duration.minutes(5), e -> loadData()));
        autoRefresh.setCycleCount(Timeline.INDEFINITE);
        autoRefresh.play();

        // These buttons register real ENTRADA movements (via
        // openMovimientoForm -> MovimientosController.showMovimientoDialog),
        // the same write action "Nuevo Movimiento" gates in Movimientos —
        // without this they were reachable by any logged-in user, including
        // DIRECCION, who can't even see "Nuevo Movimiento" there.
        boolean canWrite = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (btnReponerTodos != null) { btnReponerTodos.setVisible(canWrite); btnReponerTodos.setManaged(canWrite); }
        if (btnReponerAgotado != null) { btnReponerAgotado.setVisible(canWrite); btnReponerAgotado.setManaged(canWrite); }
        if (btnReponerBajoStock != null) { btnReponerBajoStock.setVisible(canWrite); btnReponerBajoStock.setManaged(canWrite); }

        tableAgotados.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (btnReponerAgotado != null && canWrite) btnReponerAgotado.setDisable(s == null);
        });
        tableBajoStock.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (btnReponerBajoStock != null && canWrite) btnReponerBajoStock.setDisable(s == null);
        });

        if (searchField != null) {
            if (btnClearSearch != null) {
                searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            }
            SearchUtils.setupSearchHistory("sibim/search-history/alertas", searchField, () -> applySearch(searchField.getText()));
            SearchUtils.debounce(searchField, 260, q -> { STICKY.put("search", q != null ? q : ""); applySearch(q); });
            String savedSearch = STICKY.get("search", "");
            if (!savedSearch.isBlank()) searchField.setText(savedSearch);
        }
        tableAgotados.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tableAgotados.getSelectionModel().getSelectedItem();
                if (sel != null) AlertasDialogs.showProductoInfo(sel, true);
            }
        });
        tableBajoStock.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tableBajoStock.getSelectionModel().getSelectedItem();
                if (sel != null) AlertasDialogs.showProductoInfo(sel, false);
            }
        });
        tableAgotados.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ENTER
                    && tableAgotados.getSelectionModel().getSelectedItem() != null) {
                onReponerAgotado(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tableAgotados.getSelectionModel().clearSelection(); ev.consume();
            }
        });
        tableBajoStock.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ENTER
                    && tableBajoStock.getSelectionModel().getSelectedItem() != null) {
                onSolicitarBajoStock(); ev.consume();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tableBajoStock.getSelectionModel().clearSelection(); ev.consume();
            }
        });
        tableGarantias.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tableGarantias.getSelectionModel().clearSelection(); ev.consume();
            }
        });

        tableAgotados.setContextMenu(AlertasContextMenus.buildAgotados(
            tableAgotados, canWrite,
            sel -> AlertasDialogs.showProductoInfo(sel, true),
            this::onReponerAgotado,
            this::darDeBajaDesdeAlertas,
            this::imprimirFicha));

        tableBajoStock.setContextMenu(AlertasContextMenus.buildBajoStock(
            tableBajoStock,
            sel -> AlertasDialogs.showProductoInfo(sel, false),
            this::onSolicitarBajoStock,
            this::imprimirFicha));

        // tableGarantias: same double-click / context-menu "ver detalle"
        // pattern as the other two tables — it was the only one without it.
        tableGarantias.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tableGarantias.getSelectionModel().getSelectedItem();
                if (sel != null) AlertasDialogs.showGarantiaInfo(sel);
            }
        });
        tableGarantias.setContextMenu(AlertasContextMenus.buildGarantias(
            tableGarantias, AlertasDialogs::showGarantiaInfo, this::imprimirFicha));

        for (Label badge : new Label[]{ helpAgotados, helpBajoStock, helpGarantias, helpMantenimiento, helpResumen }) {
            if (badge != null) DialogUtil.enableClickToShowTooltip(badge);
        }

        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible("alertas.resumen.colapsado", btnToggleResumen, resumenBox,
                "Mostrar resumen", "Ocultar resumen");

        setupCollapsibleSection("alertas.agotados.colapsado", headerAgotados, contentAgotados, chevronAgotados);
        setupCollapsibleSection("alertas.bajostock.colapsado", headerBajoStock, contentBajoStock, chevronBajoStock);
        setupCollapsibleSection("alertas.garantias.colapsado", headerGarantias, contentGarantias, chevronGarantias);
        setupCollapsibleSection("alertas.mantenimiento.colapsado", headerMantenimiento, contentMantenimiento, chevronMantenimiento);
    }

    /** Lets the user collapse/expand one of the 4 alert sections by clicking
     *  its colored header bar — this page stacks all 4 with no way to skip
     *  past the ones you don't care about, so a long list in "Agotados"
     *  pushes "Garantías"/"Mantenimiento" far down the scroll. State is
     *  remembered per section across restarts, same as the other
     *  collapsible sections in the app (see DialogUtil.makeCollapsible). */
    private void setupCollapsibleSection(String prefKey, HBox header, VBox content, FontIcon chevron) {
        if (header == null || content == null || chevron == null) return;
        boolean collapsed = STICKY.getBoolean(prefKey, false);
        applySectionCollapsed(content, chevron, collapsed);
        header.setCursor(javafx.scene.Cursor.HAND);
        header.setOnMouseClicked(e -> {
            boolean nowCollapsed = content.isVisible();
            applySectionCollapsed(content, chevron, nowCollapsed);
            STICKY.putBoolean(prefKey, nowCollapsed);
        });
    }

    private static void applySectionCollapsed(VBox content, FontIcon chevron, boolean collapsed) {
        content.setVisible(!collapsed);
        content.setManaged(!collapsed);
        chevron.setIconLiteral(collapsed ? "mdi2c-chevron-down" : "mdi2c-chevron-up");
    }

    private void setupColumns() {
        AlertasColumnSetup.configureAgotados(colAgNombre, colAgCodigo, colAgArea);
        AlertasColumnSetup.configureBajoStock(colBsNombre, colBsCodigo, colBsStock, colBsMin);
        AlertasColumnSetup.configureMantenimiento(colMantNombre, colMantCodigo, colMantArea, colMantFecha, colMantNotas);
        AlertasColumnSetup.configureGarantias(colGaNombre, colGaCodigo, colGaFecha, colGaDias);
    }

    private record AlertasData(List<Producto> agotados, List<Producto> bajoStock, List<Producto> garantias, List<Producto> mantenimiento) {}

    private void loadData() { loadData(false); }

    private void loadData(boolean showToast) {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        DialogUtil.runAsync(
            () -> new AlertasData(
                productoService.getAgotados(),
                productoService.getBajoStock(),
                productoService.getVencidosProximos(30),
                productoService.getProximasRevisiones(30)),
            data -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                allAgotados       = data.agotados();
                allBajoStock      = data.bajoStock();
                allGarantias      = data.garantias();
                allMantenimiento  = data.mantenimiento();
                if (tableMantenimiento != null) {
                    tableMantenimiento.getItems().setAll(allMantenimiento);
                    if (lblMantenimientoCount != null)
                        lblMantenimientoCount.setText(allMantenimiento.size() + " bienes");
                    if (sectionMantenimiento != null) {
                        sectionMantenimiento.setVisible(!allMantenimiento.isEmpty());
                        sectionMantenimiento.setManaged(!allMantenimiento.isEmpty());
                    }
                }
                final List<Producto> _ag = data.agotados(), _bs = data.bajoStock(), _ga = data.garantias();
                AppExecutor.submit(() -> new EmailService().enviarAlertas(_ag, _bs, _ga));
                // Solo avisa por la bandeja del sistema si el usuario no está viendo
                // la app ahora mismo — si la ventana está enfocada, ya está viendo
                // esta misma cifra en la sección "Agotados" de esta pantalla.
                javafx.stage.Stage primary = com.sibim.MainApp.getPrimaryStage();
                boolean appEnFoco = primary != null && primary.isFocused() && !primary.isIconified();
                if (!allAgotados.isEmpty() && !appEnFoco)
                    com.sibim.service.TrayService.notify("Alerta de inventario",
                        allAgotados.size() + " bien(es) agotado(s)");
                updateSumCards();
                if (btnReponerTodos != null) btnReponerTodos.setDisable(data.agotados().isEmpty());
                String query = searchField != null ? searchField.getText() : "";
                applySearch(query);
                if (lblActualizado != null)
                    lblActualizado.setText("Actualizado " +
                        com.sibim.util.FormatUtils.formatTime(java.time.LocalTime.now()));
                if (showToast && tableAgotados.getScene() != null)
                    NotificacionUtil.info(tableAgotados.getScene(), "Alertas actualizadas");
            },
            e -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                log.error("Error al cargar alertas", e);
                javafx.scene.Scene scene = tableAgotados.getScene();
                if (scene == null && com.sibim.MainApp.getPrimaryStage() != null)
                    scene = com.sibim.MainApp.getPrimaryStage().getScene();
                if (scene != null)
                    NotificacionUtil.errorConAccion(scene,
                        "No se pudo cargar las alertas de inventario", "Reintentar", () -> loadData(false));
            }
        );
    }

    private void applySearch(String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<Producto> filtAgotados  = filter(allAgotados,  q);
        List<Producto> filtBajoStock = filter(allBajoStock, q);
        List<Producto> filtGarantias = filter(allGarantias, q);
        if (!q.isBlank()) {
            tableAgotados.setPlaceholder(searchEmptyNode(q));
            tableBajoStock.setPlaceholder(searchEmptyNode(q));
            tableGarantias.setPlaceholder(searchEmptyNode(q));
        } else {
            tableAgotados.setPlaceholder(alertaOkNode("Sin bienes agotados"));
            tableBajoStock.setPlaceholder(alertaOkNode("Sin bienes con bajo stock"));
            tableGarantias.setPlaceholder(alertaOkNode("Sin garantías próximas a vencer"));
        }
        tableAgotados.getItems().setAll(filtAgotados);
        tableBajoStock.getItems().setAll(filtBajoStock);
        tableGarantias.getItems().setAll(filtGarantias);
        AnimationUtils.staggerTableRows(tableAgotados);
        AnimationUtils.staggerTableRows(tableBajoStock);
        AnimationUtils.staggerTableRows(tableGarantias);
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

    @FXML private void onRefresh() { loadData(true); }

    private boolean sinAlertas() {
        if (allAgotados.isEmpty() && allBajoStock.isEmpty() && allGarantias.isEmpty()) {
            NotificacionUtil.advertencia(tableAgotados.getScene(), "No hay alertas para exportar");
            return true;
        }
        return false;
    }

    private void exportar(String label, java.util.concurrent.Callable<java.io.File> task) {
        if (sinAlertas()) return;
        javafx.scene.Scene scene = tableAgotados.getScene();
        DialogUtil.runAsyncWithProgress(scene, label,
            task,
            file -> DialogUtil.showExportResultDialog(scene, file),
            ex -> NotificacionUtil.error(scene, "No se pudo exportar"));
    }

    @FXML private void onExportarPdf()   { exportar("Generando PDF…",   reporteService::exportAlertasPdf); }
    @FXML private void onExportarExcel() { exportar("Generando Excel…", reporteService::exportAlertasExcel); }

    @FXML
    private void onReponerTodosAgotados() {
        AlertasReponerDialog.showBulk(allAgotados, movimientoService, tableAgotados.getScene(), log, this::loadData);
    }

    @FXML
    private void onReponerAgotado() {
        openReponerForm(tableAgotados.getSelectionModel().getSelectedItem(), tableAgotados);
    }

    @FXML
    private void onSolicitarBajoStock() {
        openReponerForm(tableBajoStock.getSelectionModel().getSelectedItem(), tableBajoStock);
    }

    private void openReponerForm(Producto sel, TableView<Producto> source) {
        if (sel == null) {
            NotificacionUtil.advertencia(source.getScene(), "Selecciona un bien de la lista primero");
            return;
        }
        openMovimientoForm(sel.getId(), TipoMovimiento.ENTRADA);
    }

    private void darDeBajaDesdeAlertas(Producto p) {
        if (!(SessionManager.isAdmin() || SessionManager.isSecretario())) return;

        TextInputDialog dlg = new TextInputDialog();
        DialogUtil.applyOwner(dlg);
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        dlg.setTitle("Dar de baja");
        dlg.setHeaderText("Dar de baja: " + p.getNombre());
        dlg.setContentText("Motivo:");
        dlg.getEditor().setPromptText("Ej. Pérdida total, robo, deterioro irreparable…");

        Optional<String> result = dlg.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) return;
        String motivo = result.get().trim();

        AppExecutor.submit(() -> {
            try {
                productoService.darDeBaja(p.getId(), motivo);
                Platform.runLater(() -> {
                    if (tableAgotados.getScene() != null)
                        NotificacionUtil.exito(tableAgotados.getScene(), "Bien dado de baja correctamente");
                    loadData();
                });
            } catch (Exception ex) {
                log.error("Error al dar de baja desde Alertas: {}", ex.getMessage(), ex);
                Platform.runLater(() -> {
                    if (tableAgotados.getScene() != null)
                        NotificacionUtil.error(tableAgotados.getScene(),
                            ex.getMessage() != null ? ex.getMessage() : "No se pudo dar de baja el bien");
                });
            }
        });
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

    private void imprimirFicha(Producto p) {
        DialogUtil.runAsyncWithProgress(tableAgotados.getScene(), "Generando ficha técnica…",
            () -> {
                var movs = movimientoService.getByProducto(p.getId());
                return reporteService.exportFichaTecnica(p, movs);
            },
            file -> DialogUtil.showExportResultDialog(tableAgotados.getScene(), file),
            ex -> NotificacionUtil.error(tableAgotados.getScene(), "No se pudo generar la ficha técnica")
        );
    }

    @FXML private void onExportarCsv() { exportar("Generando CSV…", reporteService::exportAlertasCsv); }

    private void updateSumCards() {
        int nAg = allAgotados.size(), nBs = allBajoStock.size(), nGa = allGarantias.size();
        int total = nAg + nBs + nGa;
        double denom = total > 0 ? total : 1.0;

        AnimationUtils.animateCount(lblSumAgotados,  nAg, 600);
        AnimationUtils.animateCount(lblSumBajoStock, nBs, 600);
        AnimationUtils.animateCount(lblSumGarantias, nGa, 600);

        animateProgressBar(pbAgotados,  nAg / denom);
        animateProgressBar(pbBajoStock, nBs / denom);
        animateProgressBar(pbGarantias, nGa / denom);

        if (lblSumGarantiasDetalle != null) {
            long vencidas = allGarantias.stream()
                .filter(p -> p.getFechaVencimiento() != null
                    && p.getFechaVencimiento().isBefore(LocalDate.now()))
                .count();
            long proximas = nGa - vencidas;
            lblSumGarantiasDetalle.setText(
                vencidas + (vencidas == 1 ? " vencida" : " vencidas")
                + " · " + proximas + " próximas");
        }

        javafx.animation.PauseTransition pop =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(620));
        pop.setOnFinished(e -> {
            if (statCardAgotados     != null && nAg > 0) AnimationUtils.statCardPop(statCardAgotados);
            if (statCardBajoStockSum != null && nBs > 0) AnimationUtils.statCardPop(statCardBajoStockSum);
            if (statCardGarantiasSum != null && nGa > 0) AnimationUtils.statCardPop(statCardGarantiasSum);
        });
        pop.play();
    }

    private static void animateProgressBar(
            javafx.scene.control.ProgressBar pb, double target) {
        if (pb == null) return;
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                new javafx.animation.KeyValue(pb.progressProperty(), pb.getProgress())),
            new javafx.animation.KeyFrame(javafx.util.Duration.millis(700),
                new javafx.animation.KeyValue(pb.progressProperty(), target,
                    javafx.animation.Interpolator.EASE_BOTH)));
        tl.play();
    }

    /** Stops the auto-refresh timer and cleans up listeners. Must be called before this controller's view is discarded. */
    public void stopAutoRefresh() {
        if (autoRefresh != null) autoRefresh.stop();
        if (keyFilter != null && rootPane != null) {
            rootPane.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, keyFilter);
            keyFilter = null;
        }
    }

    private static javafx.scene.Node searchEmptyNode(String q) {
        FontIcon icon = new FontIcon("mdi2m-magnify-close");
        icon.setIconSize(40);
        icon.getStyleClass().add("empty-icon-lg");
        Label lbl = new Label("Sin resultados para «" + q + "»");
        lbl.getStyleClass().add("empty-state-msg");
        Label hint = new Label("Prueba con otro término de búsqueda");
        hint.getStyleClass().add("empty-state-hint");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(8, icon, lbl, hint);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.getStyleClass().add("empty-state-pane");
        return box;
    }

    private static javafx.scene.Node alertaOkNode(String msg) {
        FontIcon icon = new FontIcon("mdi2c-check-circle-outline");
        icon.setIconSize(40);
        icon.getStyleClass().add("alert-ok-icon");
        Label lbl = new Label(msg);
        lbl.getStyleClass().add("alert-ok-label");
        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, icon, lbl);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        box.setPadding(new javafx.geometry.Insets(24));
        return box;
    }
}
