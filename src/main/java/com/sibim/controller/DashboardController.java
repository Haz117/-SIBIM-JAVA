package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.service.DashboardService;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
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

public class DashboardController {

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

    // ── Help badges ("?") ────────────────────────────────────────────
    @FXML private Label helpStats;
    @FXML private Label helpTotalBienes;
    @FXML private Label helpValorTotal;
    @FXML private Label helpMovimientosHoy;
    @FXML private Label helpCategorias;
    @FXML private Label helpHealth;
    @FXML private Label helpAnalisis;

    // ── Charts ───────────────────────────────────────────────────────
    @FXML private LineChart<String, Number>  chartMovimientos;
    @FXML private VBox                       categoriaValorBox;
    @FXML private VBox                       pieEmptyState;
    @FXML private AreaChart<String, Number>  chartTendencia;
    @FXML private VBox                       trendCard;
    @FXML private Label                      lblTrendEmpty;

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
    @FXML private HBox  statusCardsRow;
    @FXML private VBox  areasCard;
    @FXML private VBox  areasBarBox;
    @FXML private HBox  areasSectionHdr;

    // ── Operaciones cards ─────────────────────────────────────────────
    @FXML private Label lblPrestamosVencidos;
    @FXML private Label lblPrestamosActivos;
    @FXML private Label lblResguardosActivos;
    @FXML private VBox  cardPrestamosVencidos;
    @FXML private VBox  cardPrestamosActivos;
    @FXML private VBox  cardResguardosActivos;
    @FXML private HBox  operacionesRow;

    private final DashboardService dashboardService = new DashboardService();
    private final com.sibim.repository.ConfiguracionRepository configRepo = new com.sibim.repository.ConfiguracionRepository();
    private final com.sibim.service.ReporteService reporteService = new com.sibim.service.ReporteService();
    private final com.sibim.service.PrestamoService prestamoService = new com.sibim.service.PrestamoService();
    private final com.sibim.service.ResguardoService resguardoService = new com.sibim.service.ResguardoService();

    private static final String CARDS_CONFIG_KEY = "dashboard_cards_visibles";
    private static final java.util.Set<String> ALL_CARDS = java.util.Set.of(
        "Total Bienes", "Movimientos hoy", "Bienes agotados", "Bajo stock", "Por área", "Actividad reciente");

    private List<Producto> lastAgotados  = List.of();
    private List<Producto> lastBajoStock = List.of();
    private DashboardService.Resumen lastResumen;
    private javafx.animation.Timeline autoRefresh;
    private javafx.beans.value.ChangeListener<javafx.scene.Scene> sceneReadyListener;
    private boolean chartsFirstLoad = true;
    private DashboardChartBuilder chartBuilder;
    private DashboardStatusSectionBuilder statusBuilder;

    @FXML
    public void initialize() {
        var user = SessionManager.getCurrentUser();
        if (user != null) lblUsuario.setText(user.getNombre());
        lblBienvenida.setText(getBienvenida());

        // Load org name from configuracion (best-effort — fallback is the FXML default)
        if (lblOrgBanner != null) {
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

        LocalDate hoy = LocalDate.now();
        String[] meses = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                          "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        if (lblFechaDia != null) lblFechaDia.setText(String.valueOf(hoy.getDayOfMonth()));
        if (lblFechaMes != null) lblFechaMes.setText(meses[hoy.getMonthValue()-1] + " " + hoy.getYear());

        new DashboardTablaRecienteSetup(tablaReciente, productoService, movimientoService, log).setup();

        chartBuilder = new DashboardChartBuilder(
            chartMovimientos, categoriaValorBox, pieEmptyState,
            chartTendencia, trendCard, lblTrendEmpty, this::navigarA);
        statusBuilder = new DashboardStatusSectionBuilder(
            statusCardsRow, areasCard, areasBarBox, areasSectionHdr,
            () -> navigarA("Productos"), this::onVerBajoStock, this::onVerAgotados, () -> navigarA("Alertas"));

        // JavaFX's default tooltip only appears after ~1s of hovering, which
        // reads as "broken" on a small icon — same click-to-show behavior
        // used for the "?" badges everywhere else in the app (DialogUtil).
        for (Label badge : new Label[]{ helpStats, helpTotalBienes, helpValorTotal,
                helpMovimientosHoy, helpCategorias, helpHealth, helpAnalisis }) {
            if (badge != null) com.sibim.util.DialogUtil.enableClickToShowTooltip(badge);
        }

        applyCardVisibility();

        // Hide create-only cards for users without edit permissions
        boolean canEdit = SessionManager.isAdmin() || SessionManager.isSecretario();
        if (!canEdit) {
            if (cardNuevoBien   != null) { cardNuevoBien.setVisible(false);   cardNuevoBien.setManaged(false); }
            if (cardNuevaEntrada != null) { cardNuevaEntrada.setVisible(false); cardNuevaEntrada.setManaged(false); }
        }

        // Operaciones row starts invisible — fades in after async data loads
        if (operacionesRow != null) operacionesRow.setOpacity(0);

        // Defer data loading until the node is in a scene so that charts render
        // correctly and don't get caught mid-animation during the page transition.
        sceneReadyListener = (obs, old, newScene) -> {
            if (newScene != null) {
                    statsGrid.sceneProperty().removeListener(sceneReadyListener);
                    sceneReadyListener = null;
                    // Reset scroll to top before animations so nodes rendered
                    // during stagger don't pull the viewport down.
                    javafx.application.Platform.runLater(() -> {
                        if (rootScrollPane != null) rootScrollPane.setVvalue(0);
                    });
                    if (dashBanner != null && !dashBanner.getChildren().isEmpty())
                        AnimationUtils.staggeredFadeInUp(dashBanner.getChildren(), 300, 70);
                    AnimationUtils.staggeredFadeInUp(statsGrid.getChildren(),          280,  45);
                    if (quickActionsRow != null) AnimationUtils.staggeredFadeInUp(quickActionsRow.getChildren(), 260, 40);
                    // chartsRow, activityCard, statusCardsRow stay invisible until data
                    // arrives — they fade in from updateUI() on first load (skeleton effect).
                    if (chartsRow      != null) chartsRow.setOpacity(0);
                    if (activityCard   != null) activityCard.setOpacity(0);
                    if (statusCardsRow != null) statusCardsRow.setOpacity(0);
                    loadDataAsync();
                    autoRefresh = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.minutes(10),
                            e -> loadDataAsync()));
                    autoRefresh.setCycleCount(javafx.animation.Timeline.INDEFINITE);
                    autoRefresh.play();
                }
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
        var stats = data.stats();

        lastAgotados  = data.agotados();
        lastBajoStock = data.bajoStock();
        lastResumen   = data;

        AnimationUtils.animateCount(lblTotalBienes,    stats.total(),              750);
        AnimationUtils.animateCount(lblMovimientosHoy, data.movHoy().size(),       580);
        AnimationUtils.animateCount(lblCategorias,     stats.categorias(),         580);
        // Currency: animate double value, format each tick for precision on last frame
        AnimationUtils.animateCount(lblValorTotal,
            stats.valorTotal().longValue(), 850,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));
        // Subtle pop on stat cards after their numbers finish counting
        javafx.animation.PauseTransition popDelay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
        popDelay.setOnFinished(ev -> statsGrid.getChildren().forEach(AnimationUtils::statCardPop));
        popDelay.play();

        if (lblStatsActualizacion != null) {
            lblStatsActualizacion.setText("Actualizado " +
                com.sibim.util.FormatUtils.formatTime(java.time.LocalTime.now()));
            AnimationUtils.pulse(lblStatsActualizacion, 2);
        }

        boolean showAlert = stats.agotados() > 0 || stats.bajoStock() > 0;
        if (showAlert && lblAlertBannerText != null) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (stats.agotados() > 0)
                parts.add(stats.agotados() + " agotado" + (stats.agotados() != 1 ? "s" : ""));
            if (stats.bajoStock() > 0)
                parts.add(stats.bajoStock() + " con bajo stock");
            lblAlertBannerText.setText(String.join("  ·  ", parts) + " — requieren atención");
        }
        alertBanner.setVisible(showAlert);
        alertBanner.setManaged(showAlert);
        if (showAlert) AnimationUtils.springIn(alertBanner);

        chartBuilder.buildMovimientosChart(data.movSemana());
        chartBuilder.buildCategoriaChart(data.catValores());
        statusBuilder.buildStatusCards(stats);
        chartBuilder.buildTrendChart(data.movMensual());
        statusBuilder.buildAreasSection(data.byArea(), stats.total());

        // Trend indicator: today vs yesterday from movSemana data
        if (lblMovimientosHoy != null && lblMovimientosHoy.getParent() instanceof VBox inner) {
            inner.getChildren().removeIf(n -> n instanceof Label l && l.getStyleClass().contains("trend-lbl"));
            long todayCount = data.movHoy().size();
            java.time.LocalDate yesterday = java.time.LocalDate.now().minusDays(1);
            long yesterdayCount = data.movSemana().stream()
                .filter(m -> m.getCreadoEn().toLocalDate().equals(yesterday))
                .count();
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

            // Year-over-year comparison hint
            inner.getChildren().removeIf(n -> n instanceof Label l && l.getStyleClass().contains("muted-sm")
                && l.getText() != null && l.getText().contains("año pasado"));
            long movsActual   = data.movsAnioActual();
            long movsAnterior = data.movsAnioAnterior();
            if (movsAnterior > 0) {
                long yoyDiff = movsActual - movsAnterior;
                String yoyStr = (yoyDiff >= 0 ? "+" : "") + yoyDiff + " vs año pasado";
                Label yoyLbl = new Label(yoyStr);
                yoyLbl.getStyleClass().add("muted-sm");
                inner.getChildren().add(yoyLbl);
            }
        }

        // New bienes this year hint below total bienes card
        if (lblTotalBienes != null && lblTotalBienes.getParent() instanceof VBox bienesInner) {
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

        // First-load skeleton fade-in — charts were kept at opacity 0 until data arrives
        if (chartsFirstLoad) {
            chartsFirstLoad = false;
            if (chartsRow    != null) AnimationUtils.fadeInUp(chartsRow,    350, 0);
            if (activityCard != null) AnimationUtils.fadeInUp(activityCard, 350, 80);
            if (statusCardsRow != null) AnimationUtils.fadeInUp(statusCardsRow, 350, 40);
        }

        loadOperacionesAsync();

        if (tablaReciente != null) {
            List<Movimiento> ultimos = data.movSemana().stream()
                .sorted((a, b) -> b.getCreadoEn().compareTo(a.getCreadoEn()))
                .limit(8)
                .toList();
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
            // Short delay lets the :pressed CSS feedback render before the screen switches.
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

    // ── Operaciones ──────────────────────────────────────────────────

    private void loadOperacionesAsync() {
        com.sibim.util.AppExecutor.submit(() -> {
            try {
                long vencidos   = prestamoService.getVencidos().size();
                long activos    = prestamoService.getActivos().stream()
                    .filter(p -> com.sibim.model.Prestamo.ESTADO_ACTIVO.equals(p.getEstado())).count();
                long resguardos = resguardoService.getAll().stream()
                    .filter(r -> com.sibim.model.Resguardo.ESTADO_ACTIVO.equals(r.getEstado())).count();
                javafx.application.Platform.runLater(() -> {
                    if (lblPrestamosVencidos != null)
                        AnimationUtils.animateCount(lblPrestamosVencidos, vencidos, 700);
                    if (lblPrestamosActivos != null)
                        AnimationUtils.animateCount(lblPrestamosActivos, activos, 700);
                    if (lblResguardosActivos != null)
                        AnimationUtils.animateCount(lblResguardosActivos, resguardos, 700);
                    if (cardPrestamosVencidos != null) {
                        if (vencidos > 0) {
                            cardPrestamosVencidos.getStyleClass().removeAll("dash-stat-urgent");
                            cardPrestamosVencidos.getStyleClass().add("dash-stat-urgent");
                        } else {
                            cardPrestamosVencidos.getStyleClass().remove("dash-stat-urgent");
                        }
                    }
                    if (operacionesRow != null && operacionesRow.getOpacity() < 1)
                        AnimationUtils.fadeInUp(operacionesRow, 350, 0);
                });
            } catch (Exception e) {
                // non-critical; silently ignore
            }
        });
    }

    @FXML private void onVerPrestamos()         { navigarA("Prestamos"); }
    @FXML private void onVerPrestamosVencidos() { navigarA("Prestamos"); }
    @FXML private void onVerResguardos()        { navigarA("Resguardos"); }

    @FXML
    private void onVerAgotados() {
        if (lastAgotados.isEmpty()) { navigarA("Alertas"); return; }
        showProductosMiniPanel("Bienes Agotados", "mdi2a-alert-octagon-outline",
            "Stock = 0 · " + lastAgotados.size() + " bienes requieren reposición",
            "#DC2626", "#B91C1C", lastAgotados);
    }

    @FXML
    private void onVerBajoStock() {
        if (lastBajoStock.isEmpty()) { navigarA("Alertas"); return; }
        showProductosMiniPanel("Existencias Bajas", "mdi2a-alert-circle-outline",
            "Por debajo del mínimo · " + lastBajoStock.size() + " bienes",
            "#D97706", "#B45309", lastBajoStock);
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
    public void onPersonalizarDashboard() {
        java.util.Set<String> visible = loadVisibleCards();

        Dialog<ButtonType> dlg = new Dialog<>();
        com.sibim.util.DialogUtil.applyOwner(dlg);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(360);
        com.sibim.util.DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = com.sibim.util.DialogUtil.gradientHeader("mdi2t-tune-vertical",
            "Personalizar dashboard", "Elige qué secciones mostrar", "#4F46E5", "#4338CA");

        VBox checks = new VBox(10);
        checks.setPadding(new javafx.geometry.Insets(14));
        java.util.Map<String, CheckBox> checkMap = new java.util.LinkedHashMap<>();
        for (String name : new String[]{"Total Bienes","Movimientos hoy","Bienes agotados","Bajo stock","Por área","Actividad reciente"}) {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(visible.contains(name));
            checkMap.put(name, cb);
            checks.getChildren().add(cb);
        }

        dlg.getDialogPane().setContent(new VBox(0, header, checks));
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.getStyleClass().add("btn-primary");

        dlg.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            java.util.Set<String> selected = new java.util.LinkedHashSet<>();
            checkMap.forEach((name, cb) -> { if (cb.isSelected()) selected.add(name); });
            String value = String.join(",", selected);
            com.sibim.util.AppExecutor.submit(() -> {
                try { configRepo.set(CARDS_CONFIG_KEY, value); } catch (Exception ignored) {}
            });
            applyCardVisibilitySet(selected);
        });
    }

    private java.util.Set<String> loadVisibleCards() {
        String raw = configRepo.get(CARDS_CONFIG_KEY, "");
        if (raw.isBlank()) return new java.util.HashSet<>(ALL_CARDS);
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String s : raw.split(",")) { String t = s.trim(); if (!t.isBlank()) result.add(t); }
        return result;
    }

    private void applyCardVisibility() { applyCardVisibilitySet(loadVisibleCards()); }

    private void applyCardVisibilitySet(java.util.Set<String> visible) {
        boolean showStats = visible.contains("Total Bienes") || visible.contains("Movimientos hoy");
        setCardVisible(statsGrid, showStats);
        boolean showHealth = visible.contains("Bienes agotados") || visible.contains("Bajo stock");
        setCardVisible(statusCardsRow, showHealth);
        boolean showCharts = visible.contains("Por área");
        setCardVisible(chartsRow, showCharts);
        setCardVisible(areasCard, showCharts);
        setCardVisible(areasSectionHdr, showCharts);
        setCardVisible(trendCard, showCharts);
        setCardVisible(activityCard, visible.contains("Actividad reciente"));
    }

    private static void setCardVisible(javafx.scene.Node node, boolean show) {
        if (node == null) return;
        node.setVisible(show);
        node.setManaged(show);
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

    public void stopAutoRefresh() {
        if (autoRefresh != null) autoRefresh.stop();
        if (sceneReadyListener != null) {
            statsGrid.sceneProperty().removeListener(sceneReadyListener);
            sceneReadyListener = null;
        }
    }

}
