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
import com.sibim.util.AccessibilityUtils;
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
    @FXML private ToggleButton btnVistaArbol;
    @FXML private ToggleButton btnVistaCards;
    @FXML private ToggleButton btnVistaTabla;
    @FXML private ToggleButton btnAcomodar;
    @FXML private Label lblVistaHint;
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
    private final com.sibim.service.AreaService  areaService        = new com.sibim.service.AreaService();
    private final ReporteEntregaRecepcionService entregaService     = new ReporteEntregaRecepcionService();

    private OrganigramaDataLoader dataLoader;
    private OrganigramaDialogs    dialogs;

    private Map<String, List<Producto>>                  productosPorArea  = new HashMap<>();
    private Map<String, List<com.sibim.model.Resguardo>> resguardosPorArea = new HashMap<>();
    private Map<String, List<Prestamo>>                  prestamosPorArea  = new HashMap<>();
    private Map<String, List<Comodato>>                  comodatosPorArea  = new HashMap<>();
    private boolean soloAlertas = false;

    private enum ViewMode { ARBOL, CARDS, TABLA, ACOMODO }
    private ViewMode viewMode = ViewMode.ARBOL;

    @FXML private void onRefresh() { loadData(true); }

    @FXML private void onToggleSoloAlertas() {
        soloAlertas = btnSoloAlertas != null && btnSoloAlertas.isSelected();
        STICKY.putBoolean("soloAlertas", soloAlertas);
        rebuildCurrentView();
    }

    @FXML private void onVistaArbol()  { setViewMode(ViewMode.ARBOL);  rebuildCurrentView(); }
    @FXML private void onVistaCards()  { setViewMode(ViewMode.CARDS);  rebuildCurrentView(); }
    @FXML private void onVistaTabla()  { setViewMode(ViewMode.TABLA);  rebuildCurrentView(); }

    /** Toggle: on = the drag-and-drop board, off = back to the tree. */
    @FXML private void onAcomodar() {
        setViewMode(btnAcomodar.isSelected() ? ViewMode.ACOMODO : ViewMode.ARBOL);
        rebuildCurrentView();
    }

    private void setViewMode(ViewMode m) {
        viewMode = m;
        if (btnVistaArbol  != null) btnVistaArbol.setSelected(m == ViewMode.ARBOL);
        if (btnVistaCards  != null) btnVistaCards.setSelected(m == ViewMode.CARDS);
        if (btnVistaTabla  != null) btnVistaTabla.setSelected(m == ViewMode.TABLA);
        if (btnAcomodar    != null) btnAcomodar.setSelected(m == ViewMode.ACOMODO);
        if (lblVistaHint   != null) lblVistaHint.setText(switch (m) {
            case ARBOL   -> "Expande cada sección para ver los bienes asignados";
            case CARDS   -> "Clic en un área para ver sus bienes";
            case TABLA   -> "Doble clic en un área para ver sus bienes";
            case ACOMODO -> "Arrastra una dirección a otra área";
        });
    }

    private void rebuildCurrentView() {
        String filter = searchField.getText() != null ? searchField.getText() : "";
        switch (viewMode) {
            case CARDS -> buildCardView(filter);
            case TABLA -> buildTableView(filter);
            case ACOMODO -> new OrganigramaAcomodoBuilder(productosPorArea, this::moverDireccion).build(orgTree, filter);
            default    -> buildTree(filter);
        }
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

        // Re-parenting changes who sees which bienes: Patrimonio only.
        if (btnAcomodar != null) {
            btnAcomodar.setMinWidth(Region.USE_PREF_SIZE);   // the toolbar is tight: never squeeze it to a blank pill
            if (!SessionManager.isAdmin()) { btnAcomodar.setVisible(false); btnAcomodar.setManaged(false); }
        }

        String stickySearch = STICKY.get("search", "");
        if (!stickySearch.isBlank() && searchField != null) searchField.setText(stickySearch);
        soloAlertas = STICKY.getBoolean("soloAlertas", false);
        if (btnSoloAlertas != null) btnSoloAlertas.setSelected(soloAlertas);

        SearchUtils.debounce(searchField, 280, q -> { STICKY.put("search", q == null ? "" : q); rebuildCurrentView(); });
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
                rebuildCurrentView();
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

            long alertas = areaProds.stream()
                .filter(p -> p.getEstado() == com.sibim.model.enums.EstadoProducto.VENCIDO)
                .count();

            VBox card = new VBox(8);
            card.getStyleClass().add("org-card-view-card");
            if (alertas >= 3) card.getStyleClass().add("org-card-danger");
            else if (alertas > 0) card.getStyleClass().add("org-card-warning");
            card.setOnMouseClicked(e -> dialogs.showAreaProductsDialog(areaName, areaProds, false, searchField.getScene()));
            AccessibilityUtils.asButton(card, areaName + " — ver bienes");

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
            Label bienesChip = new Label(FormatUtils.plural(areaProds.size(), "bien", "bienes"));
            bienesChip.getStyleClass().addAll("org-area-count");
            Label valorChip = new Label(com.sibim.session.Permisos.pesos(OrganigramaTreeBuilder.valorPatrimonial(areaProds)));
            valorChip.getStyleClass().add("org-area-valor");
            statsRow.getChildren().addAll(bienesChip, valorChip);

            // Progress bar (% of total)
            ProgressBar pb = new ProgressBar(totalBienes > 0 ? (double) areaProds.size() / totalBienes : 0);
            pb.getStyleClass().addAll("area-bar-pb", "area-bar-pb-1");
            pb.setMaxWidth(Double.MAX_VALUE);

            // Badges row
            HBox badgesRow = new HBox(6);
            badgesRow.setAlignment(Pos.CENTER_LEFT);

            if (alertas > 0) {
                FontIcon ai = new FontIcon("mdi2a-alert-circle");
                ai.setIconSize(11);
                Label alertBadge = new Label(" " + alertas);
                alertBadge.setGraphic(ai);
                alertBadge.setContentDisplay(ContentDisplay.LEFT);
                alertBadge.getStyleClass().addAll("org-alert-badge", "org-alert-badge-clickable");
                final List<Producto> prodsForAlert = areaProds;
                alertBadge.setOnMouseClicked(e -> { e.consume(); dialogs.showAreaProductsDialog(areaName, prodsForAlert, true, searchField.getScene()); });
                AccessibilityUtils.asButton(alertBadge, "Ver bienes con alerta de " + areaName);
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
                AccessibilityUtils.asButton(rsgBadge, "Ver resguardos de " + areaName);
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
                AccessibilityUtils.asButton(prestBadge, "Ver préstamos de " + areaName);
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
                AccessibilityUtils.asButton(comodBadge, "Ver comodatos de " + areaName);
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

    /** A dirección was dropped on another column of the "Acomodar" board. */
    private void moverDireccion(String direccion, String nuevoPadre) {
        javafx.scene.Scene scene = searchField.getScene();
        String padreActual = Areas.catalogo().buscar(direccion)
            .map(com.sibim.config.AreaCatalog.Entrada::padre).orElse(null);
        if (padreActual == null || padreActual.equals(nuevoPadre)) return;
        int bienes = productosPorArea.getOrDefault(direccion, List.of()).size();
        boolean ok = com.sibim.util.ConfirmacionUtil.confirmar("¿Mover esta área?",
            "\"" + direccion + "\" dejará de depender de \"" + padreActual + "\" y pasará a \""
            + nuevoPadre + "\".\n\n"
            + (bienes == 0 ? "No tiene bienes registrados." : "Sus " + FormatUtils.plural(bienes, "bien", "bienes")
                + " se contarán en \"" + nuevoPadre + "\" y los verá la cuenta de esa área.")
            + " Los códigos y resguardos no cambian.");
        if (!ok) return;
        DialogUtil.runAsync(() -> areaService.moverDireccion(direccion, nuevoPadre),
            () -> {
                rebuildCurrentView();
                updateStats();
                NotificacionUtil.exito(scene, "\"" + direccion + "\" ahora depende de \"" + nuevoPadre + "\"");
            },
            ex -> NotificacionUtil.error(scene, "No se pudo mover el área: " + ex.getMessage()));
    }

    private List<String> getChildrenForTopLevel(String areaName) {
        if (Areas.PRESIDENCIA.equals(areaName)) return Areas.direccionesPresidencia();
        for (Areas.SecretariaInfo sec : Areas.secretarias())
            if (sec.nombre().equals(areaName)) return sec.direcciones();
        if ("Organismos Autónomos".equals(areaName)) return Areas.autonomos();
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
            .map(Producto::getValorTotal)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
        pop.setOnFinished(e -> {
            if (statCardAreas  != null) AnimationUtils.statCardPop(statCardAreas);
            if (statCardBienes != null) AnimationUtils.statCardPop(statCardBienes);
            if (statCardTop    != null) AnimationUtils.statCardPop(statCardTop);
            if (statCardValor  != null) AnimationUtils.statCardPop(statCardValor);
            if (lblStatValor   != null) lblStatValor.setText(com.sibim.session.Permisos.pesos(totalValor));
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
        Areas.direccionesPresidencia().forEach(c -> presidencia.addAll(productosPorArea.getOrDefault(c, List.of())));
        if (!presidencia.isEmpty()) rollup.put(Areas.PRESIDENCIA, presidencia);

        for (Areas.SecretariaInfo sec : Areas.secretarias()) {
            List<Producto> combined = new ArrayList<>(productosPorArea.getOrDefault(sec.nombre(), List.of()));
            sec.direcciones().forEach(c -> combined.addAll(productosPorArea.getOrDefault(c, List.of())));
            if (!combined.isEmpty()) rollup.put(sec.nombre(), combined);
        }

        List<Producto> autonomos = new ArrayList<>();
        Areas.autonomos().forEach(c -> autonomos.addAll(productosPorArea.getOrDefault(c, List.of())));
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
                AnimationUtils.animateCount(countLbl, countL, 750, v -> FormatUtils.plural(v, "bien", "bienes"));
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

    // ── Vista tabla ───────────────────────────────────────────────────────────

    private record AreaRow(String nombre, int bienes, java.math.BigDecimal valor,
                           int alertas, int resguardos, int prestamos, int comodatos,
                           List<Producto> prods,
                           List<com.sibim.model.Resguardo> rsgs,
                           List<Prestamo> prests,
                           List<Comodato> comods) {}

    private void buildTableView(String filter) {
        orgTree.getChildren().clear();
        String q = filter == null ? "" : filter.toLowerCase();

        Map<String, List<Producto>> rollup = rollupByTopLevelArea();
        List<AreaRow> rows = new ArrayList<>();

        for (var entry : rollup.entrySet()) {
            String areaName = entry.getKey();
            List<Producto> areaProds = entry.getValue();
            if (!q.isBlank() && !areaName.toLowerCase().contains(q)
                    && areaProds.stream().noneMatch(p ->
                        p.getNombre().toLowerCase().contains(q)
                        || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(q))))
                continue;

            java.math.BigDecimal valor = OrganigramaTreeBuilder.valorPatrimonial(areaProds);
            int alertas = (int) areaProds.stream()
                .filter(p -> p.getEstado() == com.sibim.model.enums.EstadoProducto.VENCIDO)
                .count();

            List<String> children = getChildrenForTopLevel(areaName);
            List<com.sibim.model.Resguardo> rsgList = new ArrayList<>(resguardosPorArea.getOrDefault(areaName, List.of()));
            children.forEach(c -> rsgList.addAll(resguardosPorArea.getOrDefault(c, List.of())));
            List<Prestamo> prestList = new ArrayList<>(prestamosPorArea.getOrDefault(areaName, List.of()));
            children.forEach(c -> prestList.addAll(prestamosPorArea.getOrDefault(c, List.of())));
            List<Comodato> comodList = new ArrayList<>(comodatosPorArea.getOrDefault(areaName, List.of()));
            children.forEach(c -> comodList.addAll(comodatosPorArea.getOrDefault(c, List.of())));

            rows.add(new AreaRow(areaName, areaProds.size(), valor, alertas,
                rsgList.size(), prestList.size(), comodList.size(),
                List.copyOf(areaProds), List.copyOf(rsgList),
                List.copyOf(prestList), List.copyOf(comodList)));
        }

        if (rows.isEmpty()) {
            orgTree.getChildren().add(com.sibim.util.EmptyStateUtil.build(
                "mdi2o-office-building-outline",
                q.isBlank() ? "No hay áreas con bienes registrados" : "Sin resultados para «" + filter + "»",
                q.isBlank() ? "Registra bienes con área asignada en la sección Bienes" : "Prueba con otro término"));
            return;
        }

        int totalBienes = rows.stream().mapToInt(AreaRow::bienes).sum();
        java.math.BigDecimal totalValor = rows.stream().map(AreaRow::valor)
            .filter(Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        int maxBienes = rows.stream().mapToInt(AreaRow::bienes).max().orElse(0);

        TableView<AreaRow> table = new TableView<>();
        table.getStyleClass().addAll("data-table", "org-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // The table is as tall as its rows, so the totals bar sits right under the last área.
        table.setFixedCellSize(ORG_TABLE_ROW);
        table.setPrefHeight(ORG_TABLE_HEADER + rows.size() * ORG_TABLE_ROW + 2);
        table.setMinHeight(Region.USE_PREF_SIZE);
        table.setMaxHeight(Region.USE_PREF_SIZE);

        TableColumn<AreaRow, AreaRow> colArea   = new TableColumn<>("Área");
        TableColumn<AreaRow, Integer> colBienes = new TableColumn<>("Bienes");
        TableColumn<AreaRow, java.math.BigDecimal> colValor = new TableColumn<>("Valor patrimonial");
        TableColumn<AreaRow, Integer> colAlerts = new TableColumn<>("Alertas");
        TableColumn<AreaRow, Integer> colRsg    = new TableColumn<>("Resguardos");
        TableColumn<AreaRow, Integer> colPrest  = new TableColumn<>("Préstamos");
        TableColumn<AreaRow, Integer> colComod  = new TableColumn<>("Comodatos");

        colArea.setCellValueFactory(r -> new javafx.beans.property.SimpleObjectProperty<>(r.getValue()));
        colArea.setComparator(Comparator.comparing(AreaRow::nombre, String.CASE_INSENSITIVE_ORDER));
        colBienes.setCellValueFactory(r -> new javafx.beans.property.SimpleIntegerProperty(r.getValue().bienes()).asObject());
        colValor.setCellValueFactory(r -> new javafx.beans.property.SimpleObjectProperty<>(r.getValue().valor()));
        colAlerts.setCellValueFactory(r -> new javafx.beans.property.SimpleIntegerProperty(r.getValue().alertas()).asObject());
        colRsg.setCellValueFactory(r   -> new javafx.beans.property.SimpleIntegerProperty(r.getValue().resguardos()).asObject());
        colPrest.setCellValueFactory(r -> new javafx.beans.property.SimpleIntegerProperty(r.getValue().prestamos()).asObject());
        colComod.setCellValueFactory(r -> new javafx.beans.property.SimpleIntegerProperty(r.getValue().comodatos()).asObject());

        // Minimums keep every header whole ("Bienes" used to collapse to "Bie…" next to the sort arrow).
        colArea.setPrefWidth(300);   colArea.setMinWidth(200);
        colBienes.setPrefWidth(170); colBienes.setMinWidth(130);
        colValor.setPrefWidth(190);  colValor.setMinWidth(170);
        colAlerts.setPrefWidth(105); colAlerts.setMinWidth(100);
        colRsg.setPrefWidth(120);    colRsg.setMinWidth(115);
        colPrest.setPrefWidth(115);  colPrest.setMinWidth(110);
        colComod.setPrefWidth(120);  colComod.setMinWidth(115);
        colValor.getStyleClass().add("col-num");
        for (var col : List.of(colAlerts, colRsg, colPrest, colComod)) col.getStyleClass().add("col-center");

        colArea.setCellFactory(col -> new TableCell<>() {
            private final Label name = new Label();
            private final Label sub  = new Label();
            private final VBox  box  = new VBox(1, name, sub);
            {
                name.getStyleClass().add("org-table-area");
                sub.getStyleClass().add("org-table-sub");
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(AreaRow r, boolean empty) {
                super.updateItem(r, empty); setText(null);
                if (empty || r == null) { setGraphic(null); return; }
                name.setText(r.nombre());
                int deps = getChildrenForTopLevel(r.nombre()).size();
                sub.setText(deps == 0 ? "Sin dependencias" : deps == 1 ? "1 dependencia" : deps + " dependencias");
                setGraphic(box);
            }
        });

        colBienes.setCellFactory(col -> new TableCell<>() {
            private final Label  num   = new Label();
            private final Region fill  = new Region();
            private final StackPane track = new StackPane(fill);
            private final HBox   box   = new HBox(10, num, track);
            {
                num.getStyleClass().add("org-table-num");
                num.setMinWidth(30); num.setAlignment(Pos.CENTER_RIGHT);
                track.getStyleClass().add("org-table-track");
                fill.getStyleClass().add("org-table-fill");
                StackPane.setAlignment(fill, Pos.CENTER_LEFT);
                HBox.setHgrow(track, Priority.ALWAYS);
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty); setText(null);
                if (empty || v == null) { setGraphic(null); return; }
                num.setText(v.toString());
                double share = maxBienes == 0 ? 0 : (double) v / maxBienes;
                fill.maxWidthProperty().bind(track.widthProperty().multiply(share));
                setGraphic(box);
            }
        });

        colValor.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(java.math.BigDecimal v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : com.sibim.session.Permisos.pesos(v));
            }
        });

        colAlerts.setCellFactory(col -> countCell("cell-badge-danger"));
        colRsg.setCellFactory(col    -> countCell("cell-badge-purple"));
        colPrest.setCellFactory(col  -> countCell("cell-badge-blue"));
        colComod.setCellFactory(col  -> countCell("cell-badge-teal"));

        table.getColumns().addAll(List.of(colArea, colBienes, colValor, colAlerts, colRsg, colPrest, colComod));
        table.getItems().addAll(rows);
        colBienes.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(colBienes);
        table.sort();

        table.setOnMouseClicked(e -> {
            AreaRow sel = table.getSelectionModel().getSelectedItem();
            if (sel == null || e.getClickCount() != 2) return;
            dialogs.showAreaProductsDialog(sel.nombre(), sel.prods(), false, searchField.getScene());
        });

        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<AreaRow> row = new javafx.scene.control.TableRow<>();
            javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem miVer   = new javafx.scene.control.MenuItem("Ver bienes del área");
            javafx.scene.control.MenuItem miRsg   = new javafx.scene.control.MenuItem("Ver resguardos");
            javafx.scene.control.MenuItem miPrest = new javafx.scene.control.MenuItem("Ver préstamos");
            miVer.setGraphic(new FontIcon("mdi2p-package-variant"));
            miRsg.setGraphic(new FontIcon("mdi2c-clipboard-account-outline"));
            miPrest.setGraphic(new FontIcon("mdi2c-clipboard-arrow-right-outline"));
            miVer.setOnAction(e -> { AreaRow r = row.getItem(); if (r != null) dialogs.showAreaProductsDialog(r.nombre(), r.prods(), false, searchField.getScene()); });
            miRsg.setOnAction(e -> { AreaRow r = row.getItem(); if (r != null && !r.rsgs().isEmpty()) dialogs.showResguardosAreaDialog(r.nombre(), r.rsgs(), searchField.getScene()); });
            miPrest.setOnAction(e -> { AreaRow r = row.getItem(); if (r != null && !r.prests().isEmpty()) dialogs.showPrestamosAreaDialog(r.nombre(), r.prests(), searchField.getScene()); });
            javafx.scene.control.MenuItem miComod = new javafx.scene.control.MenuItem("Ver comodatos");
            miComod.setGraphic(new FontIcon("mdi2h-handshake-outline"));
            miComod.setOnAction(e -> { AreaRow r = row.getItem(); if (r != null && !r.comods().isEmpty()) dialogs.showComodatosAreaDialog(r.nombre(), r.comods(), searchField.getScene()); });
            menu.getItems().addAll(miVer, miRsg, miPrest, miComod);
            row.setTooltip(new Tooltip("Doble clic para ver los bienes del área"));
            row.setOnContextMenuRequested(e -> { if (!row.isEmpty()) menu.show(row, e.getScreenX(), e.getScreenY()); });
            return row;
        });

        HBox totals = new HBox(22,
            totalCount(rows.size(), "área", "áreas"),
            totalCount(totalBienes, "bien", "bienes"),
            totalItem("valor patrimonial", com.sibim.session.Permisos.pesos(totalValor)),
            totalCount(rows.stream().mapToInt(AreaRow::alertas).sum(), "alerta", "alertas"),
            totalCount(rows.stream().mapToInt(AreaRow::resguardos).sum(), "resguardo", "resguardos"),
            totalCount(rows.stream().mapToInt(AreaRow::prestamos).sum(), "préstamo", "préstamos"),
            totalCount(rows.stream().mapToInt(AreaRow::comodatos).sum(), "comodato", "comodatos"));
        totals.getStyleClass().add("org-table-totals");
        totals.setAlignment(Pos.CENTER_LEFT);
        Label lblTotal = new Label("TOTAL");
        lblTotal.getStyleClass().add("org-table-totals-title");
        totals.getChildren().add(0, lblTotal);

        VBox wrap = new VBox(table, totals);
        wrap.getStyleClass().add("org-table-wrap");
        orgTree.getChildren().add(wrap);
        AnimationUtils.staggeredFadeInUp(List.of(wrap), 280, 0);
    }

    private static final double ORG_TABLE_ROW = 54;
    private static final double ORG_TABLE_HEADER = 42;

    /** Count cell: a colored chip when there is something, a quiet dash when there is nothing. */
    private static TableCell<AreaRow, Integer> countCell(String badgeClass) {
        return new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().addAll("cell-badge", badgeClass); }
            @Override protected void updateItem(Integer v, boolean empty) {
                super.updateItem(v, empty); setText(null); setGraphic(null);
                getStyleClass().remove("muted-text");
                if (empty || v == null) return;
                if (v == 0) { setText("—"); getStyleClass().add("muted-text"); return; }
                badge.setText(v.toString());
                setGraphic(badge);
            }
        };
    }

    private static HBox totalCount(int n, String singular, String plural) {
        return totalItem(n == 1 ? singular : plural, String.valueOf(n));
    }

    private static HBox totalItem(String caption, String value) {
        Label v = new Label(value);
        v.getStyleClass().add("org-table-totals-value");
        Label c = new Label(caption);
        c.getStyleClass().add("org-table-totals-caption");
        HBox box = new HBox(5, v, c);
        box.setAlignment(Pos.BASELINE_LEFT);
        return box;
    }
}
