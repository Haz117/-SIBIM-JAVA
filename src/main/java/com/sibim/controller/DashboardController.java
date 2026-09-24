package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.service.DashboardService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class DashboardController implements Refreshable {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final ProductoService productoService = new ProductoService();
    private final MovimientoService movimientoService = new MovimientoService();

    // ── Stats cards ──────────────────────────────────────────────────
    @FXML private Label lblTotalBienes;
    @FXML private Label lblValorTotal;
    @FXML private Label lblMovimientosHoy;
    @FXML private Label lblCategorias;

    // ── Banner ───────────────────────────────────────────────────────
    @FXML private Label lblBienvenida;
    @FXML private Label lblUsuario;
    @FXML private Label lblOrgBanner;
    @FXML private Label lblFechaDia;
    @FXML private Label lblFechaMes;
    @FXML private HBox  alertBanner;
    @FXML private Label lblAlertBannerText;
    @FXML private Label  lblStatsActualizacion;
    @FXML private javafx.scene.control.Button btnRefreshDash;
    @FXML private javafx.scene.control.Button btnPanelEjecutivo;

    // ── Help badges ("?") ────────────────────────────────────────────
    @FXML private Label helpStats;
    @FXML private Label helpTotalBienes;
    @FXML private Label helpValorTotal;
    @FXML private Label helpMovimientosHoy;
    @FXML private Label helpCategorias;
    @FXML private Label helpAnalisis;

    // ── Charts ───────────────────────────────────────────────────────
    @FXML private LineChart<String, Number>  chartMovimientos;
    @FXML private VBox                       categoriaValorBox;
    @FXML private VBox                       pieEmptyState;

    // ── Layout ───────────────────────────────────────────────────────
    @FXML private javafx.scene.control.ScrollPane rootScrollPane;
    @FXML private GridPane statsGrid;
    @FXML private TableView<Movimiento> tablaReciente;
    @FXML private Label lblCountReciente;
    @FXML private VBox  dashBanner;
    @FXML private HBox  chartsRow;
    @FXML private VBox  activityCard;
    @FXML private HBox  quickActionsRow;
    @FXML private VBox  cardNuevoBien;
    @FXML private VBox  cardNuevaEntrada;

    private final DashboardService dashboardService = new DashboardService();
    private final com.sibim.repository.ConfiguracionRepository configRepo = new com.sibim.repository.ConfiguracionRepository();
    private final com.sibim.service.ReporteService reporteService = com.sibim.service.ReporteService.getInstance();

    private List<Producto> lastAgotados  = List.of();
    private List<Producto> lastBajoStock = List.of();
    private DashboardService.Resumen lastResumen;
    private javafx.animation.Timeline autoRefresh;
    private javafx.beans.value.ChangeListener<javafx.scene.Scene> sceneReadyListener;
    private boolean chartsFirstLoad = true;
    private DashboardChartBuilder chartBuilder;

    @FXML
    public void initialize() {
        var user = SessionManager.getCurrentUser();
        if (user != null) lblUsuario.setText(user.getNombre());
        lblBienvenida.setText(getBienvenida());
        loadOrgNameAsync();
        setupDateBanner();

        new DashboardTablaRecienteSetup(tablaReciente, productoService, movimientoService, log).setup();
        chartBuilder = new DashboardChartBuilder(
            chartMovimientos, categoriaValorBox, pieEmptyState, this::navigarA);

        setupHelpBadges();
        setupPermissions();
        setupSceneReadyListener();
    }

    private void loadOrgNameAsync() {
        if (lblOrgBanner == null) return;
        com.sibim.util.DialogUtil.runAsync(
            () -> {
                com.sibim.repository.ConfiguracionRepository cr = new com.sibim.repository.ConfiguracionRepository();
                return cr.get("nombre_ayuntamiento", "H. Ayuntamiento de Ixmiquilpan")
                     + "  ·  Bienes Municipales";
            },
            txt -> { if (lblOrgBanner != null) lblOrgBanner.setText(txt); },
            e -> {}
        );
    }

    private void setupDateBanner() {
        LocalDate hoy = LocalDate.now();
        String[] meses = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                          "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        if (lblFechaDia != null) lblFechaDia.setText(String.valueOf(hoy.getDayOfMonth()));
        if (lblFechaMes != null) lblFechaMes.setText(meses[hoy.getMonthValue()-1] + " " + hoy.getYear());
    }

    private void setupHelpBadges() {
        for (Label badge : new Label[]{ helpStats, helpTotalBienes, helpValorTotal,
                helpMovimientosHoy, helpCategorias, helpAnalisis }) {
            if (badge != null) com.sibim.util.DialogUtil.enableClickToShowTooltip(badge);
        }
    }

    private void setupPermissions() {
        boolean canEdit = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (!canEdit) {
            if (cardNuevoBien    != null) { cardNuevoBien.setVisible(false);    cardNuevoBien.setManaged(false); }
            if (cardNuevaEntrada != null) { cardNuevaEntrada.setVisible(false); cardNuevaEntrada.setManaged(false); }
        }
        if (btnPanelEjecutivo != null) {
            boolean isAdmin = SessionManager.isAdmin();
            btnPanelEjecutivo.setVisible(isAdmin);
            btnPanelEjecutivo.setManaged(isAdmin);
        }
    }

    private void setupSceneReadyListener() {
        sceneReadyListener = (obs, old, newScene) -> {
            if (newScene == null) return;
            statsGrid.sceneProperty().removeListener(sceneReadyListener);
            sceneReadyListener = null;
            javafx.application.Platform.runLater(() -> {
                if (rootScrollPane != null) rootScrollPane.setVvalue(0);
            });
            if (dashBanner != null && !dashBanner.getChildren().isEmpty())
                AnimationUtils.staggeredFadeInUp(dashBanner.getChildren(), 300, 70);
            AnimationUtils.staggeredFadeInUp(statsGrid.getChildren(), 280, 45);
            if (quickActionsRow != null) AnimationUtils.staggeredFadeInUp(quickActionsRow.getChildren(), 260, 40);
            if (chartsRow      != null) chartsRow.setOpacity(0);
            if (activityCard   != null) activityCard.setOpacity(0);
            loadDataAsync();
            autoRefresh = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10), e -> loadDataAsync()));
            autoRefresh.setCycleCount(javafx.animation.Timeline.INDEFINITE);
            autoRefresh.play();
        };
        statsGrid.sceneProperty().addListener(sceneReadyListener);
    }

    private void loadDataAsync() {
        Task<DashboardService.Resumen> task = new Task<>() {
            @Override protected DashboardService.Resumen call() throws Exception {
                return dashboardService.getCachedOrFetch();
            }
            @Override protected void succeeded() {
                updateUI(getValue());
            }
            @Override protected void failed() {
                lblTotalBienes.setText("—");
                lblValorTotal.setText("Sin datos");
                if (statsGrid != null && statsGrid.getScene() != null)
                    com.sibim.util.NotificacionUtil.errorConAccion(statsGrid.getScene(),
                        "No se pudo cargar el resumen. Verifica la conexión.", "Reintentar", DashboardController.this::loadDataAsync);
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    private void updateUI(DashboardService.Resumen data) {
        lastAgotados  = data.agotados();
        lastBajoStock = data.bajoStock();
        lastResumen   = data;

        updateStatCards(data);
        updateAlertBanner(data);
        chartBuilder.buildMovimientosChart(data.movSemana());
        chartBuilder.buildCategoriaChart(data.catValores());
        updateTrendIndicator(data);
        updateNewBienesHint();
        updateTableReciente(data);

        if (chartsFirstLoad) {
            chartsFirstLoad = false;
            if (chartsRow    != null) AnimationUtils.fadeInUp(chartsRow,    350, 0);
            if (activityCard != null) AnimationUtils.fadeInUp(activityCard, 350, 80);
        }
    }

    private void updateStatCards(DashboardService.Resumen data) {
        var stats = data.stats();
        AnimationUtils.animateCount(lblTotalBienes,    stats.total(),              750);
        AnimationUtils.animateCount(lblMovimientosHoy, data.movHoy().size(),       580);
        AnimationUtils.animateCount(lblCategorias,     stats.categorias(),         580);
        AnimationUtils.animateCount(lblValorTotal,
            stats.valorTotal().longValue(), 850,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        javafx.animation.PauseTransition popDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
        popDelay.setOnFinished(ev -> statsGrid.getChildren().forEach(AnimationUtils::statCardPop));
        popDelay.play();
        if (lblStatsActualizacion != null) {
            lblStatsActualizacion.setText("Actualizado " +
                com.sibim.util.FormatUtils.formatTime(java.time.LocalTime.now()));
            AnimationUtils.pulse(lblStatsActualizacion, 2);
        }
    }

    private void updateAlertBanner(DashboardService.Resumen data) {
        var stats = data.stats();
        int proximasRevisiones = data.proximasRevisiones().size();
        boolean showAlert = stats.agotados() > 0 || stats.bajoStock() > 0 || proximasRevisiones > 0;
        if (showAlert && lblAlertBannerText != null) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (stats.agotados() > 0)
                parts.add(stats.agotados() + " agotado" + (stats.agotados() != 1 ? "s" : ""));
            if (stats.bajoStock() > 0)
                parts.add(stats.bajoStock() + " con bajo stock");
            if (proximasRevisiones > 0)
                parts.add(proximasRevisiones + " con revisión próxima");
            lblAlertBannerText.setText(String.join("  ·  ", parts) + " — requieren atención");
        }
        alertBanner.setVisible(showAlert);
        alertBanner.setManaged(showAlert);
        if (showAlert) AnimationUtils.springIn(alertBanner);
    }

    private void updateTrendIndicator(DashboardService.Resumen data) {
        if (lblMovimientosHoy == null || !(lblMovimientosHoy.getParent() instanceof VBox inner)) return;
        inner.getChildren().removeIf(n -> n instanceof Label l && l.getStyleClass().contains("trend-lbl"));
        long todayCount     = data.movHoy().size();
        java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
        long yesterdayCount = data.movSemana().stream()
            .filter(m -> m.getCreadoEn().toLocalDate().equals(yesterday)).count();
        String arrow; String cls;
        if      (todayCount > yesterdayCount) { arrow = "▲"; cls = "trend-up"; }
        else if (todayCount < yesterdayCount) { arrow = "▼"; cls = "trend-down"; }
        else                                  { arrow = "—"; cls = "trend-eq"; }
        long diff = Math.abs(todayCount - yesterdayCount);
        String diffStr = diff == 0 ? "igual que ayer"
            : (todayCount > yesterdayCount ? "+" : "-") + diff + " vs ayer";
        Label trendLbl = new Label(arrow + " " + diffStr);
        trendLbl.getStyleClass().addAll("trend-lbl", cls);
        int afterValue = inner.getChildren().indexOf(lblMovimientosHoy) + 1;
        inner.getChildren().add(Math.min(afterValue, inner.getChildren().size()), trendLbl);

        inner.getChildren().removeIf(n -> n instanceof Label l && l.getStyleClass().contains("muted-sm")
            && l.getText() != null && l.getText().contains("año pasado"));
        long movsActual   = data.movsAnioActual();
        long movsAnterior = data.movsAnioAnterior();
        if (movsAnterior > 0) {
            long yoyDiff = movsActual - movsAnterior;
            Label yoyLbl = new Label((yoyDiff >= 0 ? "+" : "") + yoyDiff + " vs año pasado");
            yoyLbl.getStyleClass().add("muted-sm");
            inner.getChildren().add(yoyLbl);
        }
    }

    private void updateNewBienesHint() {
        if (lblTotalBienes == null || !(lblTotalBienes.getParent() instanceof VBox bienesInner)) return;
        bienesInner.getChildren().removeIf(n -> n instanceof Label l
            && l.getText() != null && l.getText().contains("este año"));
        int anioActual = java.time.LocalDate.now().getYear();
        com.sibim.util.DialogUtil.runAsync(
            () -> new com.sibim.repository.ProductoRepository().countNuevosEnAnio(anioActual),
            nuevos -> {
                if (nuevos > 0) {
                    Label nuevosLbl = new Label("+" + nuevos + " registrados este año");
                    nuevosLbl.getStyleClass().add("muted-sm");
                    bienesInner.getChildren().add(nuevosLbl);
                }
            },
            ex -> {}
        );
    }

    private void updateTableReciente(DashboardService.Resumen data) {
        if (tablaReciente == null) return;
        List<Movimiento> ultimos = data.movSemana().stream()
            .sorted((a, b) -> b.getCreadoEn().compareTo(a.getCreadoEn()))
            .limit(8).toList();
        tablaReciente.getItems().setAll(ultimos);
        AnimationUtils.staggerTableRows(tablaReciente);
        if (lblCountReciente != null) {
            int n = ultimos.size();
            lblCountReciente.setVisible(n > 0);
            lblCountReciente.setManaged(n > 0);
            if (n > 0) AnimationUtils.animateCount(lblCountReciente, n, 380);
            else       lblCountReciente.setText("");
        }
    }


    // ── Navigation ───────────────────────────────────────────────────

    @FXML
    private void onAccionNuevoBien() {
        com.sibim.session.NavigationContext.setPendingNuevoBien();
        navigarA("Productos");
    }

    @FXML
    private void onAccionNuevaEntrada() {
        com.sibim.session.NavigationContext.setPendingNuevoMovimiento();
        navigarA("Movimientos");
    }

    @FXML
    private void onBusquedaGlobal() {
        javafx.scene.Scene scene = statsGrid != null ? statsGrid.getScene() : null;
        if (scene == null) return;
        scene.getRoot().fireEvent(new javafx.scene.input.KeyEvent(
            javafx.scene.input.KeyEvent.KEY_PRESSED, "k", "k",
            javafx.scene.input.KeyCode.K, false, true, false, false));
    }

    private void navigarA(String vista) {
        javafx.scene.Scene scene = null;
        if (statsGrid != null && statsGrid.getScene() != null) scene = statsGrid.getScene();
        else if (alertBanner != null && alertBanner.getScene() != null) scene = alertBanner.getScene();
        if (scene == null) return;
        String btnId = "#btn" + vista.substring(0, 1).toUpperCase() + vista.substring(1);
        javafx.scene.Node btn = scene.lookup(btnId);
        if (btn instanceof Button b) {
            javafx.animation.PauseTransition delay =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(80));
            delay.setOnFinished(ev -> b.fire());
            delay.play();
        }
    }

    @FXML private void onRefreshDash() { dashboardService.invalidateCache(); loadDataAsync(); }

    @FXML private void onVerProductos()    { navigarA("Productos"); }
    @FXML private void onVerMovimientos()  { navigarA("Movimientos"); }
    @FXML private void onVerReportes()     { navigarA("Reportes"); }
    @FXML private void onVerCategorias()   { navigarA("Categorias"); }
    @FXML private void onVerAlertas()      { navigarA("Alertas"); }

    @FXML
    private void onVerAgotados() {
        if (lastAgotados.isEmpty()) { navigarA("Alertas"); return; }
        showProductosMiniPanel("Bienes Agotados", "mdi2a-alert-octagon-outline",
            "Stock = 0 · " + lastAgotados.size() + " bienes requieren reposición",
            AppColors.DANGER, AppColors.DANGER_D, lastAgotados);
    }

    @FXML
    private void onVerBajoStock() {
        if (lastBajoStock.isEmpty()) { navigarA("Alertas"); return; }
        showProductosMiniPanel("Existencias Bajas", "mdi2a-alert-circle-outline",
            "Por debajo del mínimo · " + lastBajoStock.size() + " bienes",
            AppColors.WARNING, AppColors.WARNING_D, lastBajoStock);
    }

    private void showProductosMiniPanel(String titulo, String icono, String subtitulo,
                                         String color1, String color2, List<Producto> items) {
        javafx.scene.Scene scene = statsGrid != null ? statsGrid.getScene() : null;
        if (scene == null) return;

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(500);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        javafx.scene.layout.HBox header = com.sibim.util.DialogUtil.gradientHeader(
            icono, titulo, subtitulo, color1, color2);

        TableView<Producto> tbl = new TableView<>();
        tbl.setPrefHeight(250);
        tbl.getStyleClass().add("data-table");

        TableColumn<Producto, String> cNombre = new TableColumn<>("Nombre");
        cNombre.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNombre()));
        cNombre.setPrefWidth(220);

        TableColumn<Producto, String> cCodigo = new TableColumn<>("Código");
        cCodigo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getCodigo()));
        cCodigo.setPrefWidth(110);

        TableColumn<Producto, Integer> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getStockActual()));
        cStock.setPrefWidth(70);
        cStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty);
                getStyleClass().removeAll("stock-low","stock-warn");
                if (empty || v == null) { setText(null); return; }
                setText(String.valueOf(v));
                getStyleClass().add(v == 0 ? "stock-low" : "stock-warn");
            }
        });

        TableColumn<Producto, String> cArea = new TableColumn<>("Área");
        cArea.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getArea() != null ? c.getValue().getArea() : ""));
        cArea.setPrefWidth(160);

        tbl.getColumns().add(cNombre);
        tbl.getColumns().add(cCodigo);
        tbl.getColumns().add(cStock);
        tbl.getColumns().add(cArea);
        tbl.setItems(javafx.collections.FXCollections.observableArrayList(items));

        Button btnVerTodas = new Button("Ver todas las alertas →");
        btnVerTodas.getStyleClass().add("btn-primary");
        btnVerTodas.setOnAction(e -> { dlg.close(); navigarA("Alertas"); });

        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(0, header, tbl);
        javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(btnVerTodas);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        footer.setPadding(new javafx.geometry.Insets(10, 4, 0, 4));
        content.getChildren().add(footer);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, tbl), 270, 70);
        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String getBienvenida() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) return "Buenos días,";
        if (hour < 19) return "Buenas tardes,";
        return "Buenas noches,";
    }

    @FXML
    private void onExportarDashboardPdf() {
        if (lastResumen == null) {
            com.sibim.util.NotificacionUtil.advertencia(statsGrid.getScene(),
                "Los datos del dashboard aún se están cargando, intenta en un momento");
            return;
        }
        final DashboardService.Resumen resumen = lastResumen;
        DialogUtil.runAsyncWithProgress(
            statsGrid.getScene(),
            "Generando PDF del dashboard…",
            () -> reporteService.exportDashboardPdf(resumen, null),
            file -> {
                if (file != null) DialogUtil.showExportResultDialog(statsGrid.getScene(), file);
            },
            ex -> {
                log.error("Error al exportar dashboard PDF", ex);
                com.sibim.util.NotificacionUtil.error(statsGrid.getScene(),
                    "No se pudo generar el PDF del dashboard");
            }
        );
    }

    @FXML
    private void onPanelEjecutivo() {
        com.sibim.controller.dialogs.PanelEjecutivoDialog.show(statsGrid.getScene(), dashboardService);
    }

    public void stopAutoRefresh() {
        if (autoRefresh != null) autoRefresh.stop();
        if (sceneReadyListener != null) {
            statsGrid.sceneProperty().removeListener(sceneReadyListener);
            sceneReadyListener = null;
        }
    }

}
