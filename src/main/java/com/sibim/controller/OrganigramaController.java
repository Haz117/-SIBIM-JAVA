package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Comodato;
import com.sibim.model.Prestamo;
import com.sibim.model.Producto;
import com.sibim.service.ComodatoService;
import com.sibim.service.MovimientoService;
import com.sibim.service.PrestamoService;
import com.sibim.service.ProductoService;
import com.sibim.service.ReporteEntregaRecepcionService;
import com.sibim.service.ReporteOrganigramaService;
import com.sibim.service.ResguardoService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import com.sibim.session.SessionManager;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public class OrganigramaController {

    private static final Logger log = LoggerFactory.getLogger(OrganigramaController.class);

    @FXML private TextField    searchField;
    @FXML private Button       btnClearSearch;
    @FXML private ToggleButton btnSoloAlertas;
    @FXML private ToggleButton btnToggleVista;
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

    private final ProductoService                productoService    = new ProductoService();
    private final ReporteOrganigramaService      reporteService     = new ReporteOrganigramaService();
    private final MovimientoService              movimientoService  = new MovimientoService();
    private final ResguardoService               resguardoService   = new ResguardoService();
    private final PrestamoService                prestamoService    = new PrestamoService();
    private final ComodatoService                comodatoService    = new ComodatoService();
    private final ReporteEntregaRecepcionService entregaService     = new ReporteEntregaRecepcionService();

    private OrganigramaDataLoader dataLoader;
    private OrganigramaDialogs    dialogs;

    private Map<String, List<Producto>>                  productosPorArea  = new HashMap<>();
    private Map<String, List<com.sibim.model.Resguardo>> resguardosPorArea = new HashMap<>();
    private Map<String, List<Prestamo>>                  prestamosPorArea  = new HashMap<>();
    private Map<String, List<Comodato>>                  comodatosPorArea  = new HashMap<>();
    private boolean soloAlertas = false;
    private boolean vistaCards  = false;

    @FXML private void onRefresh() { loadData(true); }

    @FXML private void onToggleSoloAlertas() {
        soloAlertas = btnSoloAlertas != null && btnSoloAlertas.isSelected();
        STICKY.putBoolean("soloAlertas", soloAlertas);
        buildTree(searchField.getText() != null ? searchField.getText() : "");
    }

    @FXML private void onToggleVista() {
        vistaCards = btnToggleVista != null && btnToggleVista.isSelected();
        String filter = searchField.getText() != null ? searchField.getText() : "";
        if (vistaCards) buildCardView(filter);
        else buildTree(filter);
    }

    @FXML
    public void initialize() {
        dataLoader = new OrganigramaDataLoader(productoService, resguardoService, prestamoService, comodatoService, log);
        dialogs    = new OrganigramaDialogs(movimientoService, reporteService, entregaService, log);

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
                prestamosPorArea  = data.prestamosPorArea();
                comodatosPorArea  = data.comodatosPorArea();
                spinner.setVisible(false); spinner.setManaged(false);
                String filter = searchField.getText() != null ? searchField.getText() : "";
                if (vistaCards) buildCardView(filter);
                else buildTree(filter);
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
            prestamosPorArea, comodatosPorArea,
            (name, prods, soloAlerts) -> dialogs.showAreaProductsDialog(name, prods, soloAlerts, searchField.getScene()),
            (name, rsgs)   -> dialogs.showResguardosAreaDialog(name, rsgs, searchField.getScene()),
            (name, prests) -> dialogs.showPrestamosAreaDialog(name, prests, searchField.getScene()),
            (name, comods) -> dialogs.showComodatosAreaDialog(name, comods, searchField.getScene())
        ).build(orgTree, filter);
    }

    private void buildCardView(String filter) {
        orgTree.getChildren().clear();
        String q = filter == null ? "" : filter.toLowerCase();

        Map<String, List<Producto>> rollup = rollupByTopLevelArea();
        int totalBienes = productosPorArea.values().stream().mapToInt(List::size).sum();

        FlowPane flow = new FlowPane();
        flow.setHgap(14);
        flow.setVgap(14);
        flow.setPadding(new Insets(4, 0, 8, 0));

        Set<String> accessible = SessionManager.getAccessibleAreas();

        for (Map.Entry<String, List<Producto>> entry : rollup.entrySet()) {
            String areaName = entry.getKey();
            List<Producto> areaProds = entry.getValue();

            if (!q.isBlank() && !areaName.toLowerCase().contains(q)
                    && areaProds.stream().noneMatch(p ->
                        p.getNombre().toLowerCase().contains(q)
                        || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(q))))
                continue;

            VBox card = new VBox(8);
            card.getStyleClass().add("org-card-view-card");
            card.setOnMouseClicked(e -> dialogs.showAreaProductsDialog(areaName, areaProds, false, searchField.getScene()));

            // Header row
            HBox cardHeader = new HBox(8);
            cardHeader.setAlignment(Pos.CENTER_LEFT);
            FontIcon bldIcon = new FontIcon("mdi2o-office-building-outline");
            bldIcon.setIconSize(16);
            bldIcon.getStyleClass().add("org-section-icon");
            Label areaLbl = new Label(areaName);
            areaLbl.getStyleClass().addAll("org-area-name");
            areaLbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(areaLbl, Priority.ALWAYS);
            cardHeader.getChildren().addAll(bldIcon, areaLbl);

            boolean isMyArea = !SessionManager.isAdmin()
                && SessionManager.getCurrentUser().getArea() != null
                && (areaName.equals(SessionManager.getCurrentUser().getArea())
                    || areaProds.stream().anyMatch(p -> areaName.equals(p.getArea())));
            if (isMyArea) {
                Label myBadge = new Label("Tu área");
                myBadge.getStyleClass().add("org-my-area-badge");
                cardHeader.getChildren().add(myBadge);
            }

            // Stats row
            HBox statsRow = new HBox(10);
            statsRow.setAlignment(Pos.CENTER_LEFT);
            Label bienesChip = new Label(areaProds.size() + " bienes");
            bienesChip.getStyleClass().addAll("org-area-count");
            Label valorChip = new Label(FormatUtils.formatCurrency(OrganigramaTreeBuilder.valorPatrimonial(areaProds)));
            valorChip.getStyleClass().add("org-area-valor");
            statsRow.getChildren().addAll(bienesChip, valorChip);

            // Progress bar (% of total)
            ProgressBar pb = new ProgressBar(totalBienes > 0 ? (double) areaProds.size() / totalBienes : 0);
            pb.getStyleClass().addAll("area-bar-pb", "area-bar-pb-1");
            pb.setMaxWidth(Double.MAX_VALUE);

            // Badges row
            HBox badgesRow = new HBox(6);
            badgesRow.setAlignment(Pos.CENTER_LEFT);

            long alertas = areaProds.stream()
                .filter(p -> p.getEstado() == com.sibim.model.enums.EstadoProducto.AGOTADO
                          || p.getEstado() == com.sibim.model.enums.EstadoProducto.BAJO_STOCK)
                .count();
            if (alertas > 0) {
                FontIcon ai = new FontIcon("mdi2a-alert-circle");
                ai.setIconSize(11);
                Label alertBadge = new Label(" " + alertas);
                alertBadge.setGraphic(ai);
                alertBadge.setContentDisplay(ContentDisplay.LEFT);
                alertBadge.getStyleClass().addAll("org-alert-badge", "org-alert-badge-clickable");
                final List<Producto> prodsForAlert = areaProds;
                alertBadge.setOnMouseClicked(e -> { e.consume(); dialogs.showAreaProductsDialog(areaName, prodsForAlert, true, searchField.getScene()); });
                badgesRow.getChildren().add(alertBadge);
            }

            // Find resguardos for this top-level area (aggregate parent + children)
            List<String> children = getChildrenForTopLevel(areaName);
            List<com.sibim.model.Resguardo> rsgCard = new ArrayList<>(resguardosPorArea.getOrDefault(areaName, List.of()));
            children.forEach(c -> rsgCard.addAll(resguardosPorArea.getOrDefault(c, List.of())));
            if (!rsgCard.isEmpty()) {
                FontIcon ri = new FontIcon("mdi2c-clipboard-account-outline");
                ri.setIconSize(11);
                Label rsgBadge = new Label(" " + rsgCard.size());
                rsgBadge.setGraphic(ri);
                rsgBadge.setContentDisplay(ContentDisplay.LEFT);
                rsgBadge.getStyleClass().addAll("org-resguardo-badge", "org-alert-badge-clickable");
                final List<com.sibim.model.Resguardo> rsgFinal = List.copyOf(rsgCard);
                rsgBadge.setOnMouseClicked(e -> { e.consume(); dialogs.showResguardosAreaDialog(areaName, rsgFinal, searchField.getScene()); });
                badgesRow.getChildren().add(rsgBadge);
            }

            List<Prestamo> prestCard = new ArrayList<>(prestamosPorArea.getOrDefault(areaName, List.of()));
            children.forEach(c -> prestCard.addAll(prestamosPorArea.getOrDefault(c, List.of())));
            if (!prestCard.isEmpty()) {
                FontIcon pi = new FontIcon("mdi2c-clipboard-arrow-right-outline");
                pi.setIconSize(11);
                Label prestBadge = new Label(" " + prestCard.size());
                prestBadge.setGraphic(pi);
                prestBadge.setContentDisplay(ContentDisplay.LEFT);
                prestBadge.getStyleClass().addAll("org-prestamo-badge", "org-alert-badge-clickable");
                final List<Prestamo> prestFinal = List.copyOf(prestCard);
                prestBadge.setOnMouseClicked(e -> { e.consume(); dialogs.showPrestamosAreaDialog(areaName, prestFinal, searchField.getScene()); });
                badgesRow.getChildren().add(prestBadge);
            }

            List<Comodato> comodCard = new ArrayList<>(comodatosPorArea.getOrDefault(areaName, List.of()));
            children.forEach(c -> comodCard.addAll(comodatosPorArea.getOrDefault(c, List.of())));
            if (!comodCard.isEmpty()) {
                FontIcon ci = new FontIcon("mdi2h-handshake-outline");
                ci.setIconSize(11);
                Label comodBadge = new Label(" " + comodCard.size());
                comodBadge.setGraphic(ci);
                comodBadge.setContentDisplay(ContentDisplay.LEFT);
                comodBadge.getStyleClass().addAll("org-comodato-badge", "org-alert-badge-clickable");
                final List<Comodato> comodFinal = List.copyOf(comodCard);
                comodBadge.setOnMouseClicked(e -> { e.consume(); dialogs.showComodatosAreaDialog(areaName, comodFinal, searchField.getScene()); });
                badgesRow.getChildren().add(comodBadge);
            }

            card.getChildren().addAll(cardHeader, statsRow, pb);
            if (!badgesRow.getChildren().isEmpty()) card.getChildren().add(badgesRow);
            flow.getChildren().add(card);
        }

        if (flow.getChildren().isEmpty()) {
            FontIcon icon = new FontIcon("mdi2o-office-building-outline");
            icon.setIconSize(44);
            icon.getStyleClass().add("empty-icon-lg");
            Label msg = new Label(q.isBlank()
                ? "No hay áreas con bienes registrados"
                : "No se encontraron áreas para \"" + filter + "\"");
            msg.getStyleClass().add("empty-state-msg");
            Label hint = new Label(q.isBlank()
                ? "Registra bienes con área asignada en la sección Bienes"
                : "Intenta con otro término de búsqueda");
            hint.getStyleClass().add("empty-state-hint");
            VBox empty = new VBox(10, icon, msg, hint);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("empty-state-pane");
            empty.setPadding(new Insets(48, 24, 48, 24));
            orgTree.getChildren().add(empty);
        } else {
            orgTree.getChildren().add(flow);
            AnimationUtils.staggeredFadeInUp(flow.getChildren(), 220, 40);
        }
    }

    private List<String> getChildrenForTopLevel(String areaName) {
        if (Areas.PRESIDENCIA.equals(areaName)) return Areas.DIRECCIONES_PRESIDENCIA;
        for (Areas.SecretariaInfo sec : Areas.SECRETARIAS)
            if (sec.nombre().equals(areaName)) return sec.direcciones();
        if ("Organismos Autónomos".equals(areaName)) return Areas.AUTONOMOS;
        return List.of();
    }

    private void updateStats() {
        if (lblStatAreas == null) return;
        Map<String, List<Producto>> rollup = rollupByTopLevelArea();
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

    private Map<String, List<Producto>> rollupByTopLevelArea() {
        Map<String, List<Producto>> rollup = new LinkedHashMap<>();

        List<Producto> presidencia = new ArrayList<>(productosPorArea.getOrDefault(Areas.PRESIDENCIA, List.of()));
        Areas.DIRECCIONES_PRESIDENCIA.forEach(c -> presidencia.addAll(productosPorArea.getOrDefault(c, List.of())));
        if (!presidencia.isEmpty()) rollup.put(Areas.PRESIDENCIA, presidencia);

        for (Areas.SecretariaInfo sec : Areas.SECRETARIAS) {
            List<Producto> combined = new ArrayList<>(productosPorArea.getOrDefault(sec.nombre(), List.of()));
            sec.direcciones().forEach(c -> combined.addAll(productosPorArea.getOrDefault(c, List.of())));
            if (!combined.isEmpty()) rollup.put(sec.nombre(), combined);
        }

        List<Producto> autonomos = new ArrayList<>();
        Areas.AUTONOMOS.forEach(c -> autonomos.addAll(productosPorArea.getOrDefault(c, List.of())));
        if (!autonomos.isEmpty()) rollup.put("Organismos Autónomos", autonomos);

        return rollup;
    }

    private static final String[] DISTRIB_COLORS = {
        "area-bar-pb-1", "area-bar-pb-2", "area-bar-pb-3", "area-bar-pb-4", "area-bar-pb-5"
    };

    private void buildAreaDistrib(Map<String, List<Producto>> rollup) {
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
