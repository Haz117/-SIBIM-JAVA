package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteOrganigramaService;
import com.sibim.service.ResguardoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import com.sibim.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public class OrganigramaController {

    private static final Logger log = LoggerFactory.getLogger(OrganigramaController.class);

    @FXML private TextField    searchField;
    @FXML private Button       btnClearSearch;
    @FXML private ToggleButton btnSoloAlertas;
    @FXML private VBox         orgTree;
    @FXML private ProgressIndicator spinner;
    @FXML private Label lblStatAreas;
    @FXML private Label lblStatBienes;
    @FXML private Label lblStatTopArea;
    @FXML private VBox  statCardAreas;
    @FXML private VBox  statCardBienes;
    @FXML private VBox  statCardTop;
    @FXML private Label helpAreas;
    @FXML private Label helpBienes;
    @FXML private Label helpTopArea;
    @FXML private VBox  statCardValor;
    @FXML private Label lblStatValor;
    @FXML private Label helpValor;
    @FXML private Label helpResumen;
    @FXML private VBox  areaDistribCard;
    @FXML private VBox  areaDistribBox;
    @FXML private VBox  resumenBox;
    @FXML private Button btnToggleResumen;

    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/organigrama");

    private final ProductoService           productoService   = new ProductoService();
    private final ReporteOrganigramaService reporteService    = new ReporteOrganigramaService();
    private final MovimientoService         movimientoService = new MovimientoService();
    private final ResguardoService          resguardoService  = new ResguardoService();

    private OrganigramaDataLoader dataLoader;
    private OrganigramaDialogs    dialogs;

    private Map<String, List<Producto>>                  productosPorArea  = new HashMap<>();
    private Map<String, List<com.sibim.model.Resguardo>> resguardosPorArea = new HashMap<>();
    private boolean soloAlertas = false;

    @FXML private void onRefresh() { loadData(true); }

    @FXML private void onToggleSoloAlertas() {
        soloAlertas = btnSoloAlertas != null && btnSoloAlertas.isSelected();
        STICKY.putBoolean("soloAlertas", soloAlertas);
        buildTree(searchField.getText() != null ? searchField.getText() : "");
    }

    @FXML
    public void initialize() {
        dataLoader = new OrganigramaDataLoader(productoService, resguardoService, log);
        dialogs    = new OrganigramaDialogs(movimientoService, reporteService, log);

        for (Label badge : new Label[]{ helpAreas, helpBienes, helpTopArea, helpValor, helpResumen }) {
            if (badge != null) DialogUtil.enableClickToShowTooltip(badge);
        }
        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible("organigrama.resumen.colapsado", btnToggleResumen, resumenBox,
                "Mostrar resumen", "Ocultar resumen");

        String stickySearch = STICKY.get("search", "");
        if (!stickySearch.isBlank() && searchField != null) searchField.setText(stickySearch);
        soloAlertas = STICKY.getBoolean("soloAlertas", false);
        if (btnSoloAlertas != null) btnSoloAlertas.setSelected(soloAlertas);

        SearchUtils.debounce(searchField, 280, q -> { STICKY.put("search", q == null ? "" : q); buildTree(q); });
        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); STICKY.put("search", ""); searchField.requestFocus(); });
        }
        loadData(false);
        AnimationUtils.staggeredFadeInUp(
            List.of(statCardAreas, statCardBienes, statCardTop, statCardValor), 300, 55);
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });

        searchField.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) return;
            scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F,
                    javafx.scene.input.KeyCombination.CONTROL_DOWN),
                () -> { searchField.requestFocus(); searchField.selectAll(); });
            scene.getAccelerators().put(
                new javafx.scene.input.KeyCodeCombination(javafx.scene.input.KeyCode.F5),
                () -> loadData(true));
        });
    }

    private void loadData(boolean showSuccessToast) {
        spinner.setVisible(true); spinner.setManaged(true);
        dataLoader.load(
            data -> {
                productosPorArea  = data.productosPorArea();
                resguardosPorArea = data.resguardosPorArea();
                spinner.setVisible(false); spinner.setManaged(false);
                buildTree(searchField.getText() != null ? searchField.getText() : "");
                updateStats();
                if (showSuccessToast)
                    NotificacionUtil.info(searchField.getScene(), "Organigrama actualizado");
            },
            e -> {
                log.error("No se pudo cargar el organigrama", e);
                spinner.setVisible(false); spinner.setManaged(false);
                NotificacionUtil.errorConAccion(searchField.getScene(), "No se pudo cargar el organigrama", "Reintentar", () -> loadData(false));
            }
        );
    }

    private void buildTree(String filter) {
        new OrganigramaTreeBuilder(
            productosPorArea, resguardosPorArea, soloAlertas,
            (name, prods, soloAlerts) -> dialogs.showAreaProductsDialog(name, prods, soloAlerts, searchField.getScene()),
            (name, rsgs)             -> dialogs.showResguardosAreaDialog(name, rsgs, searchField.getScene())
        ).build(orgTree, filter);
    }

    private void updateStats() {
        if (lblStatAreas == null) return;
        Map<String, List<Produto>> rollup = rollupByTopLevelArea();
        int totalBienes = productosPorArea.values().stream().mapToInt(List::size).sum();
        AnimationUtils.animateCount(lblStatAreas,  rollup.size(), 650);
        AnimationUtils.animateCount(lblStatBienes, totalBienes,   800);

        java.math.BigDecimal totalValor = productosPorArea.values().stream()
            .flatMap(List::stream)
            .map(p -> {
                java.math.BigDecimal precio = p.getPrecioVenta() != null
                    ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
                return precio.multiply(java.math.BigDecimal.valueOf(p.getStockActual()));
            })
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
        pop.setOnFinished(e -> {
            if (statCardAreas  != null) AnimationUtils.statCardPop(statCardAreas);
            if (statCardBienes != null) AnimationUtils.statCardPop(statCardBienes);
            if (statCardTop    != null) AnimationUtils.statCardPop(statCardTop);
            if (statCardValor  != null) AnimationUtils.statCardPop(statCardValor);
            if (lblStatValor   != null) lblStatValor.setText(FormatUtils.formatCurrency(totalValor));
        });
        pop.play();

        rollup.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().size()))
            .ifPresentOrElse(
                e -> {
                    String topArea = e.getKey();
                    javafx.animation.PauseTransition delay =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
                    delay.setOnFinished(ev -> lblStatTopArea.setText(topArea));
                    delay.play();
                },
                () -> lblStatTopArea.setText("—"));

        buildAreaDistrib(rollup);
    }

    private Map<String, List<Produto>> rollupByTopLevelArea() {
        Map<String, List<Produto>> rollup = new LinkedHashMap<>();

        List<Produto> presidencia = new ArrayList<>(productosPorArea.getOrDefault(Areas.PRESIDENCIA, List.of()));
        Areas.DIRECCIONES_PRESIDENCIA.forEach(c -> presidencia.addAll(productosPorArea.getOrDefault(c, List.of())));
        if (!presidencia.isEmpty()) rollup.put(Areas.PRESIDENCIA, presidencia);

        for (Areas.SecretariaInfo sec : Areas.SECRETARIAS) {
            List<Produto> combined = new ArrayList<>(productosPorArea.getOrDefault(sec.nombre(), List.of()));
            sec.direcciones().forEach(c -> combined.addAll(productosPorArea.getOrDefault(c, List.of())));
            if (!combined.isEmpty()) rollup.put(sec.nombre(), combined);
        }

        List<Produto> autonomos = new ArrayList<>();
        Areas.AUTONOMOS.forEach(c -> autonomos.addAll(productosPorArea.getOrDefault(c, List.of())));
        if (!autonomos.isEmpty()) rollup.put("Organismos Autónomos", autonomos);

        return rollup;
    }

    private static final String[] DISTRIB_COLORS = {
        "area-bar-pb-1", "area-bar-pb-2", "area-bar-pb-3", "area-bar-pb-4", "area-bar-pb-5"
    };

    private void buildAreaDistrib(Map<String, List<Produto>> rollup) {
        if (areaDistribBox == null || areaDistribCard == null) return;
        areaDistribBox.getChildren().clear();

        var sorted = rollup.entrySet().stream()
            .sorted((a, b) -> b.getValue().size() - a.getValue().size())
            .toList();
        int top = Math.min(5, sorted.size());
        if (top == 0) { areaDistribCard.setVisible(false); areaDistribCard.setManaged(false); return; }

        areaDistribCard.setVisible(true); areaDistribCard.setManaged(true);
        int maxCount = sorted.get(0).getValue().size();

        for (int i = 0; i < top; i++) {
            var entry = sorted.get(i);
            int count = entry.getValue().size();

            Label nameLbl = new Label(entry.getKey());
            nameLbl.getStyleClass().add("area-bar-name");
            nameLbl.setMinWidth(120);
            nameLbl.setMaxWidth(180);

            javafx.scene.control.ProgressBar pb = new javafx.scene.control.ProgressBar(0);
            pb.getStyleClass().addAll("area-bar-pb", DISTRIB_COLORS[i]);
            pb.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(pb, Priority.ALWAYS);

            Label countLbl = new Label("0 bienes");
            countLbl.getStyleClass().add("area-bar-count");
            countLbl.setMinWidth(70);

            HBox row = new HBox(10, nameLbl, pb, countLbl);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            areaDistribBox.getChildren().add(row);

            double target = maxCount > 0 ? (double) count / maxCount : 0;
            long countL = count;
            int delay = 300 + i * 90;
            javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(delay));
            wait.setOnFinished(ev -> {
                javafx.animation.Timeline anim = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(pb.progressProperty(), 0)),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(800),
                        new javafx.animation.KeyValue(pb.progressProperty(), target,
                            javafx.animation.Interpolator.EASE_OUT)));
                anim.play();
                AnimationUtils.animateCount(countLbl, countL, 750, v -> v + " bienes");
            });
            wait.play();
        }
    }

    @FXML
    private void onExportarPdf() {
        if (productosPorArea.isEmpty()) {
            NotificacionUtil.advertencia(searchField.getScene(), "No hay datos de organigrama para exportar");
            return;
        }
        DialogUtil.runAsyncWithProgress(searchField.getScene(), "Generando reporte de organigrama…",
            () -> reporteService.exportOrganigrama(productosPorArea),
            file -> DialogUtil.showExportResultDialog(searchField.getScene(), file),
            ex -> NotificacionUtil.error(searchField.getScene(), "No se pudo exportar el organigrama")
        );
    }

    @FXML
    private void onExportarCsv() {
        if (productosPorArea.isEmpty()) {
            NotificacionUtil.advertencia(searchField.getScene(), "No hay datos de organigrama para exportar");
            return;
        }
        DialogUtil.runAsyncWithProgress(searchField.getScene(), "Generando CSV de organigrama…",
            () -> reporteService.exportOrganigramaCsv(productosPorArea),
            file -> DialogUtil.showExportResultDialog(searchField.getScene(), file),
            ex -> NotificacionUtil.error(searchField.getScene(), "No se pudo exportar el CSV")
        );
    }

    @FXML private void onExpandAll() {
        for (javafx.scene.Node n : orgTree.getChildren())
            if (n instanceof TitledPane pane) pane.setExpanded(true);
    }

    @FXML private void onCollapseAll() {
        for (javafx.scene.Node n : orgTree.getChildren())
            if (n instanceof TitledPane pane) pane.setExpanded(false);
    }
}
