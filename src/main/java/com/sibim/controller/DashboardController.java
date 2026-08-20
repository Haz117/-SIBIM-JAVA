package com.sibim.controller;

import com.sibim.model.Movimiento;
import com.sibim.model.Producto;
import com.sibim.repository.MovimientoRepository;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.FormatUtils;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

public class DashboardController {

    // ── Stats cards ──────────────────────────────────────────────────
    @FXML private Label lblTotalBienes;
    @FXML private Label lblValorTotal;
    @FXML private Label lblBajoStock;
    @FXML private Label lblAgotados;
    @FXML private Label lblMovimientosHoy;
    @FXML private Label lblCategorias;

    // ── Banner ───────────────────────────────────────────────────────
    @FXML private Label lblBienvenida;
    @FXML private Label lblUsuario;
    @FXML private Label lblFechaDia;
    @FXML private Label lblFechaMes;
    @FXML private HBox  alertBanner;
    @FXML private Label lblStatsActualizacion;

    // ── Health bar ───────────────────────────────────────────────────
    @FXML private VBox  healthSection;
    @FXML private HBox  healthBar;
    @FXML private Label lblHealthActivos;
    @FXML private Label lblHealthBajo;
    @FXML private Label lblHealthAgotado;
    @FXML private Label lblHealthVencido;
    @FXML private Label lblHealthDetail;

    // ── Charts ───────────────────────────────────────────────────────
    @FXML private LineChart<String, Number> chartMovimientos;
    @FXML private PieChart chartValorCategoria;
    @FXML private VBox pieEmptyState;

    // ── Layout ───────────────────────────────────────────────────────
    @FXML private GridPane statsGrid;
    @FXML private TableView<Movimiento> tablaReciente;
    @FXML private Label lblCountReciente;

    private final ProductoRepository productoRepo     = new ProductoRepository();
    private final MovimientoRepository movimientoRepo = new MovimientoRepository();

    private List<Producto> lastAgotados  = List.of();
    private List<Producto> lastBajoStock = List.of();

    @FXML
    public void initialize() {
        var user = SessionManager.getCurrentUser();
        if (user != null) lblUsuario.setText(user.getNombre());
        lblBienvenida.setText(getBienvenida());

        LocalDate hoy = LocalDate.now();
        String[] meses = {"Enero","Febrero","Marzo","Abril","Mayo","Junio",
                          "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre"};
        if (lblFechaDia != null) lblFechaDia.setText(String.valueOf(hoy.getDayOfMonth()));
        if (lblFechaMes != null) lblFechaMes.setText(meses[hoy.getMonthValue()-1] + " " + hoy.getYear());

        setupTablaReciente();

        // Defer data loading until the node is in a scene so that charts render
        // correctly and don't get caught mid-animation during the page transition.
        statsGrid.sceneProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.Scene> obs,
                                javafx.scene.Scene old, javafx.scene.Scene newScene) {
                if (newScene != null) {
                    statsGrid.sceneProperty().removeListener(this);
                    AnimationUtils.staggeredFadeInUp(statsGrid.getChildren(), 280, 45);
                    loadDataAsync();
                }
            }
        });
    }

    private void loadDataAsync() {
        Task<DashboardData> task = new Task<>() {
            @Override protected DashboardData call() throws Exception {
                var stats      = productoRepo.getStats();
                var catValores = productoRepo.getValorPorCategoria();
                var agotados   = productoRepo.findAgotados();
                var bajoStock  = productoRepo.findBajoStock();
                var movHoy     = movimientoRepo.findToday();
                var movSemana  = movimientoRepo.findLastNDays(7);
                return new DashboardData(stats, catValores, agotados, bajoStock, movHoy, movSemana);
            }
            @Override protected void succeeded() {
                updateUI(getValue());
            }
            @Override protected void failed() {
                lblTotalBienes.setText("—");
                lblValorTotal.setText("Sin datos");
                if (statsGrid != null && statsGrid.getScene() != null)
                    com.sibim.util.NotificacionUtil.error(statsGrid.getScene(),
                        "No se pudo cargar el resumen. Verifica la conexión a la base de datos.");
            }
        };
        com.sibim.util.AppExecutor.submit(task);
    }

    private void updateUI(DashboardData data) {
        var stats = data.stats();

        lastAgotados  = data.agotados();
        lastBajoStock = data.bajoStock();

        AnimationUtils.animateCount(lblTotalBienes,    stats.total(),              750);
        AnimationUtils.animateCount(lblBajoStock,      stats.bajoStock(),          680);
        AnimationUtils.animateCount(lblAgotados,       stats.agotados(),           680);
        AnimationUtils.animateCount(lblMovimientosHoy, data.movHoy().size(),       580);
        AnimationUtils.animateCount(lblCategorias,     stats.categorias(),         580);
        // Currency: animate double value, format each tick for precision on last frame
        AnimationUtils.animateCount(lblValorTotal,
            stats.valorTotal().longValue(), 850,
            v -> FormatUtils.formatCurrency(BigDecimal.valueOf(v)));

        if (lblStatsActualizacion != null)
            lblStatsActualizacion.setText("Actualizado " +
                java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        boolean showAlert = stats.agotados() > 0 || stats.bajoStock() > 0;
        alertBanner.setVisible(showAlert);
        alertBanner.setManaged(showAlert);
        if (showAlert) AnimationUtils.fadeInDown(alertBanner, 350, 0);

        buildMovimientosChart(data.movSemana());
        buildCategoriaChart(data.catValores());
        buildHealthBar(stats);

        if (statsGrid != null && !statsGrid.getChildren().isEmpty())
            AnimationUtils.staggeredFadeInUp(statsGrid.getChildren(), 400, 70);

        if (tablaReciente != null) {
            List<Movimiento> ultimos = data.movSemana().stream()
                .sorted((a, b) -> b.getCreadoEn().compareTo(a.getCreadoEn()))
                .limit(8)
                .toList();
            tablaReciente.setItems(javafx.collections.FXCollections.observableArrayList(ultimos));

            if (lblCountReciente != null) {
                int n = ultimos.size();
                lblCountReciente.setVisible(n > 0);
                lblCountReciente.setManaged(n > 0);
                if (n > 0) AnimationUtils.animateCount(lblCountReciente, n, 380);
                else       lblCountReciente.setText("");
            }
        }
    }

    // ── Health bar ───────────────────────────────────────────────────

    private void buildHealthBar(com.sibim.repository.ProductoRepository.ProductoStats stats) {
        if (healthSection == null || healthBar == null || stats.total() == 0) return;

        long activos = stats.activos();
        long bajo    = stats.bajoStock();
        long agotado = stats.agotados();
        long vencido = stats.vencidos();
        long total   = stats.total();

        if (lblHealthActivos != null) lblHealthActivos.setText("Activos — " + activos);
        if (lblHealthBajo    != null) lblHealthBajo.setText("Bajo Stock — " + bajo);
        if (lblHealthAgotado != null) lblHealthAgotado.setText("Agotados — " + agotado);
        if (lblHealthVencido != null) lblHealthVencido.setText("Vencidos — " + vencido);
        if (lblHealthDetail  != null) {
            int pct = total > 0 ? (int) Math.round(activos * 100.0 / total) : 0;
            lblHealthDetail.setText(pct + "% en buen estado");
        }

        healthBar.getChildren().clear();
        record Seg(long count, String cssClass) {}
        List<Seg> segs = List.of(
            new Seg(activos, "dash-health-seg-green"),
            new Seg(bajo,    "dash-health-seg-amber"),
            new Seg(agotado, "dash-health-seg-red"),
            new Seg(vencido, "dash-health-seg-violet")
        );
        List<Seg> visible = segs.stream().filter(s -> s.count() > 0).toList();
        for (int i = 0; i < visible.size(); i++) {
            Seg seg = visible.get(i);
            Region r = new Region();
            r.setPrefHeight(10);
            boolean isFirst = i == 0, isLast = i == visible.size() - 1;
            String posClass = isFirst && isLast ? "dash-health-seg-only"
                            : isFirst ? "dash-health-seg-first"
                            : isLast  ? "dash-health-seg-last"
                            : "dash-health-seg-mid";
            r.getStyleClass().addAll(seg.cssClass(), posClass);
            HBox.setHgrow(r, Priority.SOMETIMES);
            r.setPrefWidth(160.0 * seg.count() / total);
            healthBar.getChildren().add(r);
        }

        healthSection.setVisible(true);
        healthSection.setManaged(true);

        // Reveal bar from left to right once layout finishes (two pulses away).
        javafx.application.Platform.runLater(() ->
            javafx.application.Platform.runLater(() ->
                AnimationUtils.revealBarLTR(healthBar, 900)));
    }

    // ── Charts ───────────────────────────────────────────────────────

    private void buildMovimientosChart(List<Movimiento> movimientos) {
        chartMovimientos.getData().clear();
        XYChart.Series<String, Number> entradas = new XYChart.Series<>(); entradas.setName("Entradas");
        XYChart.Series<String, Number> salidas  = new XYChart.Series<>(); salidas.setName("Salidas");

        LocalDate today = LocalDate.now();
        List<String> labels = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String raw = today.minusDays(i).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.of("es"))
                .replace(".", "");
            labels.add(raw.substring(0, 1).toUpperCase() + raw.substring(1));
        }

        Map<LocalDate, Integer> entMap = new HashMap<>(), salMap = new HashMap<>();
        for (Movimiento m : movimientos) {
            LocalDate d = m.getCreadoEn().toLocalDate();
            switch (m.getTipo()) {
                case ENTRADA -> entMap.merge(d, m.getCantidad(), Integer::sum);
                case SALIDA  -> salMap.merge(d, m.getCantidad(), Integer::sum);
                default -> {}
            }
        }
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String label = labels.get(6 - i);
            entradas.getData().add(new XYChart.Data<>(label, entMap.getOrDefault(day, 0)));
            salidas.getData().add(new XYChart.Data<>(label, salMap.getOrDefault(day, 0)));
        }
        chartMovimientos.getData().add(entradas);
        chartMovimientos.getData().add(salidas);
        // Chart nodes are created during layout — defer tooltip wiring one frame
        javafx.application.Platform.runLater(() -> {
            for (XYChart.Data<String, Number> d : entradas.getData())
                if (d.getNode() != null) Tooltip.install(d.getNode(), new Tooltip("Entradas " + d.getXValue() + ": " + d.getYValue()));
            for (XYChart.Data<String, Number> d : salidas.getData())
                if (d.getNode() != null) Tooltip.install(d.getNode(), new Tooltip("Salidas " + d.getXValue() + ": " + d.getYValue()));
        });
    }

    private void buildCategoriaChart(List<com.sibim.repository.ProductoRepository.CategoriaValor> catValores) {
        chartValorCategoria.getData().clear();
        catValores.forEach(cv -> chartValorCategoria.getData().add(
            new PieChart.Data(cv.nombre(), cv.valor().doubleValue())));

        javafx.application.Platform.runLater(() -> {
            for (PieChart.Data d : chartValorCategoria.getData()) {
                if (d.getNode() != null)
                    Tooltip.install(d.getNode(), new Tooltip(
                        d.getName() + ": " + FormatUtils.formatCurrency(BigDecimal.valueOf(d.getPieValue()))));
            }
        });

        boolean hasData = !chartValorCategoria.getData().isEmpty();
        chartValorCategoria.setVisible(hasData);
        chartValorCategoria.setManaged(hasData);
        if (pieEmptyState != null) {
            pieEmptyState.setVisible(!hasData);
            pieEmptyState.setManaged(!hasData);
        }
    }

    // ── Navigation ───────────────────────────────────────────────────

    private void navigarA(String vista) {
        javafx.scene.Scene scene = null;
        if (statsGrid != null && statsGrid.getScene() != null) scene = statsGrid.getScene();
        else if (alertBanner != null && alertBanner.getScene() != null) scene = alertBanner.getScene();
        if (scene == null) return;
        String btnId = "#btn" + vista.substring(0, 1).toUpperCase() + vista.substring(1);
        javafx.scene.Node btn = scene.lookup(btnId);
        if (btn instanceof Button b) b.fire();
    }

    @FXML private void onVerProductos()    { navigarA("Productos"); }
    @FXML private void onVerMovimientos()  { navigarA("Movimientos"); }
    @FXML private void onVerReportes()     { navigarA("Reportes"); }

    @FXML
    private void onVerAlertas() {
        if (alertBanner != null && alertBanner.getScene() != null) {
            Button btnAlertas = (Button) alertBanner.getScene().lookup("#btnAlertas");
            if (btnAlertas != null) btnAlertas.fire();
        }
    }

    @FXML
    private void onVerAgotados() {
        if (lastAgotados.isEmpty()) { navigarA("Alertas"); return; }
        showProductosMiniPanel("Bienes Agotados", "🚫",
            "Stock = 0 · " + lastAgotados.size() + " bienes requieren reposición",
            "#DC2626", "#B91C1C", lastAgotados);
    }

    @FXML
    private void onVerBajoStock() {
        if (lastBajoStock.isEmpty()) { navigarA("Alertas"); return; }
        showProductosMiniPanel("Existencias Bajas", "⚠",
            "Por debajo del mínimo · " + lastBajoStock.size() + " bienes",
            "#D97706", "#B45309", lastBajoStock);
    }

    private void showProductosMiniPanel(String titulo, String icono, String subtitulo,
                                         String color1, String color2, List<Producto> items) {
        javafx.scene.Scene scene = statsGrid != null ? statsGrid.getScene() : null;
        if (scene == null) return;

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(scene.getWindow());
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(500);
        com.sibim.util.DialogUtil.applyStylesheet(dlg.getDialogPane());

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

        dlg.getDialogPane().setContent(content);
        dlg.showAndWait();
    }

    // ── Recent activity table ────────────────────────────────────────

    private void setupTablaReciente() {
        if (tablaReciente == null) return;

        // Row factory — tint rows by movement type
        tablaReciente.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Movimiento m, boolean empty) {
                super.updateItem(m, empty);
                getStyleClass().removeAll("row-entrada","row-salida","row-ajuste","row-transferencia");
                if (!empty && m != null) {
                    String cls = switch (m.getTipo()) {
                        case ENTRADA      -> "row-entrada";
                        case SALIDA       -> "row-salida";
                        case AJUSTE       -> "row-ajuste";
                        case TRANSFERENCIA -> "row-transferencia";
                        default           -> "";
                    };
                    if (!cls.isEmpty()) getStyleClass().add(cls);
                }
            }
        });

        TableColumn<Movimiento, String> cProd = new TableColumn<>("Bien");
        cProd.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getProductoNombre() != null ? c.getValue().getProductoNombre() : ""));
        cProd.setPrefWidth(200);

        TableColumn<Movimiento, String> cTipo = new TableColumn<>("Tipo");
        cTipo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getTipo().getEtiqueta()));
        cTipo.setPrefWidth(100);
        cTipo.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().add("cell-badge"); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null); setText(null);
                if (empty || item == null) return;
                badge.setText(item);
                badge.getStyleClass().removeAll("cell-badge-success","cell-badge-danger",
                    "cell-badge-warning","cell-badge-blue","cell-badge-purple");
                badge.getStyleClass().add(switch (item) {
                    case "Entrada"       -> "cell-badge-success";
                    case "Salida"        -> "cell-badge-danger";
                    case "Ajuste"        -> "cell-badge-warning";
                    case "Transferencia" -> "cell-badge-blue";
                    default              -> "cell-badge-purple";
                });
                setGraphic(badge);
            }
        });

        TableColumn<Movimiento, Integer> cCant = new TableColumn<>("Cant.");
        cCant.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getCantidad()));
        cCant.setPrefWidth(60);

        TableColumn<Movimiento, String> cUsuario = new TableColumn<>("Usuario");
        cUsuario.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getUsuarioNombre() != null ? c.getValue().getUsuarioNombre() : ""));
        cUsuario.setPrefWidth(130);

        TableColumn<Movimiento, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            FormatUtils.formatDateTime(c.getValue().getCreadoEn())));
        cFecha.setPrefWidth(140);

        tablaReciente.getColumns().add(cProd);
        tablaReciente.getColumns().add(cTipo);
        tablaReciente.getColumns().add(cCant);
        tablaReciente.getColumns().add(cUsuario);
        tablaReciente.getColumns().add(cFecha);

        tablaReciente.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Movimiento sel = tablaReciente.getSelectionModel().getSelectedItem();
                if (sel != null) showMovimientoDetalle(sel);
            }
        });
    }

    private void showMovimientoDetalle(Movimiento m) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.initOwner(tablaReciente.getScene().getWindow());
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.getDialogPane().setPrefWidth(440);
        com.sibim.util.DialogUtil.applyStylesheet(dlg.getDialogPane());

        String icon  = switch (m.getTipo()) { case ENTRADA -> "⬆"; case SALIDA -> "⬇"; case AJUSTE -> "⇄"; default -> "→"; };
        String color = switch (m.getTipo()) { case ENTRADA -> "#059669"; case SALIDA -> "#DC2626"; case AJUSTE -> "#D97706"; default -> "#2563EB"; };
        String color2= switch (m.getTipo()) { case ENTRADA -> "#047857"; case SALIDA -> "#B91C1C"; case AJUSTE -> "#B45309"; default -> "#1D4ED8"; };

        javafx.scene.layout.HBox header = com.sibim.util.DialogUtil.gradientHeader(icon,
            m.getTipo().getEtiqueta() + "  —  " + m.getCantidad() + " uds.", m.getProductoNombre(), color, color2);

        javafx.scene.layout.GridPane grid = com.sibim.util.DialogUtil.formGrid(120);
        int r = 0;
        javafx.scene.control.Label antes = new javafx.scene.control.Label(String.valueOf(m.getStockAnterior()));
        antes.getStyleClass().add("dlg-stock-val");
        javafx.scene.control.Label arrow = new javafx.scene.control.Label("→");
        arrow.getStyleClass().add(m.getStockNuevo() > m.getStockAnterior() ? "dlg-stock-arrow-up" : "dlg-stock-arrow-down");
        javafx.scene.control.Label despues = new javafx.scene.control.Label(String.valueOf(m.getStockNuevo()));
        despues.getStyleClass().add(m.getStockNuevo() <= 0 ? "dlg-stock-new-empty" : m.getStockNuevo() > m.getStockAnterior() ? "dlg-stock-new-ok" : "dlg-stock-new-warn");
        javafx.scene.layout.HBox stockRow = new javafx.scene.layout.HBox(8, antes, arrow, despues);
        stockRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        grid.add(com.sibim.util.DialogUtil.fieldLabel("Bien"),     0, r); grid.add(new javafx.scene.control.Label(m.getProductoNombre()), 1, r++);
        grid.add(com.sibim.util.DialogUtil.fieldLabel("Stock"),    0, r); grid.add(stockRow, 1, r++);
        grid.add(com.sibim.util.DialogUtil.fieldLabel("Motivo"),   0, r); grid.add(new javafx.scene.control.Label(m.getMotivo() != null ? m.getMotivo() : "—"), 1, r++);
        grid.add(com.sibim.util.DialogUtil.fieldLabel("Usuario"),  0, r); grid.add(new javafx.scene.control.Label(m.getUsuarioNombre()), 1, r++);
        grid.add(com.sibim.util.DialogUtil.fieldLabel("Fecha"),    0, r); grid.add(new javafx.scene.control.Label(com.sibim.util.FormatUtils.formatDateTime(m.getCreadoEn())), 1, r);

        dlg.getDialogPane().setContent(new javafx.scene.layout.VBox(0, header, grid));
        dlg.showAndWait();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private String getBienvenida() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) return "Buenos días,";
        if (hour < 19) return "Buenas tardes,";
        return "Buenas noches,";
    }

    private record DashboardData(
            com.sibim.repository.ProductoRepository.ProductoStats stats,
            List<com.sibim.repository.ProductoRepository.CategoriaValor> catValores,
            List<Producto> agotados,
            List<Producto> bajoStock,
            List<Movimiento> movHoy,
            List<Movimiento> movSemana) {}
}
