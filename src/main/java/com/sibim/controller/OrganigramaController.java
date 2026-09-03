package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.controller.dialogs.ProductoDetailDialog;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.service.MovimientoService;
import com.sibim.service.ProductoService;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.Collectors;

public class OrganigramaController {

    private static final Logger log = LoggerFactory.getLogger(OrganigramaController.class);

    @FXML private TextField searchField;
    @FXML private Button btnClearSearch;
    @FXML private ToggleButton btnSoloAlertas;
    @FXML private VBox orgTree;
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
    @FXML private VBox  areaDistribCard;
    @FXML private VBox  areaDistribBox;

    private static final java.util.prefs.Preferences STICKY =
        java.util.prefs.Preferences.userRoot().node("sibim/filters/organigrama");

    private final ProductoService productoService = new ProductoService();
    private final com.sibim.service.ReporteService reporteService = new com.sibim.service.ReporteService();
    private final MovimientoService movimientoService = new MovimientoService();
    private Map<String, List<Producto>> productosPorArea = new HashMap<>();
    private boolean soloAlertas = false;

    @FXML private void onRefresh() { loadData(true); }

    @FXML private void onToggleSoloAlertas() {
        soloAlertas = btnSoloAlertas != null && btnSoloAlertas.isSelected();
        STICKY.putBoolean("soloAlertas", soloAlertas);
        buildTree(searchField.getText() != null ? searchField.getText() : "");
    }

    @FXML
    public void initialize() {
        for (Label badge : new Label[]{ helpAreas, helpBienes, helpTopArea, helpValor }) {
            if (badge != null) DialogUtil.enableClickToShowTooltip(badge);
        }

        // Restore sticky state
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
            java.util.List.of(statCardAreas, statCardBienes, statCardTop, statCardValor), 300, 55);
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
        DialogUtil.runAsync(
            () -> productoService.getAll().stream()
                .filter(p -> p.getArea() != null && !p.getArea().isBlank())
                .collect(Collectors.groupingBy(Producto::getArea)),
            porArea -> {
                productosPorArea = porArea;
                spinner.setVisible(false); spinner.setManaged(false);
                buildTree(searchField.getText() != null ? searchField.getText() : "");
                updateStats();
                if (showSuccessToast)
                    NotificacionUtil.info(searchField.getScene(), "Organigrama actualizado");
            },
            e -> {
                spinner.setVisible(false); spinner.setManaged(false);
                NotificacionUtil.errorConAccion(searchField.getScene(), "No se pudo cargar el organigrama", "Reintentar", () -> loadData(false));
            }
        );
    }

    private void updateStats() {
        if (lblStatAreas == null) return;
        int totalBienes = productosPorArea.values().stream().mapToInt(List::size).sum();
        AnimationUtils.animateCount(lblStatAreas,  productosPorArea.size(), 650);
        AnimationUtils.animateCount(lblStatBienes, totalBienes,             800);

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

        productosPorArea.entrySet().stream()
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

        buildAreaDistrib();
    }

    private static final String[] DISTRIB_COLORS = {
        "area-bar-pb-1", "area-bar-pb-2", "area-bar-pb-3", "area-bar-pb-4", "area-bar-pb-5"
    };

    private void buildAreaDistrib() {
        if (areaDistribBox == null || areaDistribCard == null) return;
        areaDistribBox.getChildren().clear();

        var sorted = productosPorArea.entrySet().stream()
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

            Label countLbl = new Label(count + " bienes");
            countLbl.getStyleClass().add("area-bar-count");
            countLbl.setMinWidth(70);

            HBox row = new HBox(10, nameLbl, pb, countLbl);
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            areaDistribBox.getChildren().add(row);

            double target = maxCount > 0 ? (double) count / maxCount : 0;
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
            });
            wait.play();
        }
    }

    private void buildTree(String filter) {
        orgTree.getChildren().clear();
        String q = filter == null ? "" : filter.toLowerCase();
        Set<String> accessible = SessionManager.getAccessibleAreas();

        // Presidencia
        if (accessible == null || accessible.contains(Areas.PRESIDENCIA) || anyAccessible(accessible, Areas.DIRECCIONES_PRESIDENCIA)) {
            addAreaSection(Areas.PRESIDENCIA, Areas.DIRECCIONES_PRESIDENCIA, q, true, accessible);
        }

        // Secretarías
        for (Areas.SecretariaInfo sec : Areas.SECRETARIAS) {
            if (accessible == null || accessible.contains(sec.nombre()) || anyAccessible(accessible, sec.direcciones())) {
                addAreaSection(sec.nombre(), sec.direcciones(), q, false, accessible);
            }
        }

        // Autónomos
        if (accessible == null || anyAccessible(accessible, Areas.AUTONOMOS)) {
            addAreaSection("Organismos Autónomos", Areas.AUTONOMOS, q, false, accessible);
        }

        if (orgTree.getChildren().isEmpty()) {
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
        }
        AnimationUtils.staggeredFadeInUp(orgTree.getChildren(), 270, 50);
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
        for (javafx.scene.Node n : orgTree.getChildren()) {
            if (n instanceof TitledPane pane) pane.setExpanded(true);
        }
    }

    @FXML private void onCollapseAll() {
        for (javafx.scene.Node n : orgTree.getChildren()) {
            if (n instanceof TitledPane pane) pane.setExpanded(false);
        }
    }

    /** True if any of {@code areas} is in {@code accessible} (or {@code accessible} is null = admin). */
    private boolean anyAccessible(Set<String> accessible, List<String> areas) {
        return accessible == null || areas.stream().anyMatch(accessible::contains);
    }

    private boolean matchesFilter(List<Producto> prods, String filter) {
        return prods.stream().anyMatch(p ->
            p.getNombre().toLowerCase().contains(filter)
            || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(filter)));
    }

    private void addAreaSection(String parentName, List<String> children, String filter, boolean expanded, Set<String> accessible) {
        List<Producto> allAreaProdsCheck = new java.util.ArrayList<>(
            productosPorArea.getOrDefault(parentName, List.of()));
        children.forEach(c -> allAreaProdsCheck.addAll(productosPorArea.getOrDefault(c, List.of())));

        if (soloAlertas) {
            boolean hasAlert = allAreaProdsCheck.stream().anyMatch(
                p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK);
            if (!hasAlert) return;
        }

        if (!filter.isBlank()) {
            boolean nameMatch = parentName.toLowerCase().contains(filter)
                || children.stream().anyMatch(c -> c.toLowerCase().contains(filter));
            if (!nameMatch && !matchesFilter(allAreaProdsCheck, filter)) return;
        }

        TitledPane section = new TitledPane();
        section.setExpanded(expanded || !filter.isBlank());

        // ── Header graphic ─────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 4, 2, 0));

        // Icon badge (secretaría-level)
        StackPane iconBadge = new StackPane();
        iconBadge.getStyleClass().add("org-section-icon-badge");
        FontIcon iconLbl = new FontIcon("mdi2o-office-building-outline");
        iconLbl.setIconSize(18);
        iconLbl.getStyleClass().add("org-section-icon");
        iconBadge.getChildren().add(iconLbl);

        Label nameLabel = new Label(parentName);
        nameLabel.getStyleClass().add("org-area-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        List<Producto> prods = productosPorArea.getOrDefault(parentName, List.of());
        List<Producto> allAreaProds = allAreaProdsCheck;
        int totalBienes = allAreaProds.size();

        Label countLabel = new Label(totalBienes + " bienes");
        countLabel.getStyleClass().add("org-area-count");
        if (totalBienes > 0) {
            countLabel.getStyleClass().add("org-area-count-clickable");
            countLabel.setOnMouseClicked(e -> { e.consume(); showAreaProductsDialog(parentName, allAreaProds); });
            Tooltip.install(countLabel, new Tooltip("Ver todos los bienes de " + parentName + " y sus dependencias"));
        }

        Label valorLabel = new Label(FormatUtils.formatCurrency(valorPatrimonial(allAreaProds)));
        valorLabel.getStyleClass().add("org-area-valor");

        header.getChildren().addAll(iconBadge, nameLabel, valorLabel, countLabel);

        long alertasArea = allAreaProds.stream()
            .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
            .count();
        if (alertasArea > 0) {
            FontIcon alertIcon = new FontIcon("mdi2a-alert-circle");
            alertIcon.setIconSize(12);
            Label alertDot = new Label(" " + alertasArea);
            alertDot.setGraphic(alertIcon);
            alertDot.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
            alertDot.getStyleClass().addAll("org-alert-badge", "org-alert-badge-clickable");
            alertDot.setOnMouseClicked(e -> { e.consume(); showAreaProductsDialog(parentName, allAreaProds, true); });
            Tooltip.install(alertDot, new Tooltip(alertasArea + " bien(es) agotado(s) o bajo stock en esta área — clic para verlos"));
            header.getChildren().add(alertDot);
        }

        if (!SessionManager.isAdmin()
                && SessionManager.getCurrentUser().getArea() != null
                && SessionManager.getCurrentUser().getArea().equals(parentName)) {
            Label badge = new Label("Tu área");
            badge.getStyleClass().add("org-my-area-badge");
            header.getChildren().add(badge);
        }
        section.setGraphic(header);

        // ── Content ─────────────────────────────────────────────────────
        VBox content = new VBox(8);
        content.setPadding(new Insets(10, 8, 10, 12));

        // Products assigned directly to the parent area
        if (!prods.isEmpty()) {
            addProductPreview(content, prods, parentName);
            if (!children.isEmpty()) {
                Separator sep = new Separator();
                sep.getStyleClass().add("org-sep");
                content.getChildren().add(sep);
            }
        }

        // ── Child areas as expandable mini-cards ──────────────────────
        for (String child : children) {
            if (accessible != null && !accessible.contains(child)) continue;
            List<Producto> childProds = productosPorArea.getOrDefault(child, List.of());
            if (!filter.isBlank() && !child.toLowerCase().contains(filter) && !matchesFilter(childProds, filter)) continue;

            boolean isMyArea = !SessionManager.isAdmin()
                && child.equals(SessionManager.getCurrentUser().getArea());

            VBox childCard = new VBox(0);
            childCard.getStyleClass().add("org-child-card");
            if (isMyArea) childCard.getStyleClass().add("org-child-card-myarea");
            VBox.setMargin(childCard, new Insets(0, 0, 0, 8));

            // Child header row
            HBox childHeader = new HBox(8);
            childHeader.getStyleClass().add("org-child-header");
            childHeader.setAlignment(Pos.CENTER_LEFT);
            childHeader.setPadding(new Insets(9, 12, 9, 12));

            FontIcon dirIcon = new FontIcon("mdi2f-folder-outline");
            dirIcon.setIconSize(14);
            dirIcon.getStyleClass().add("org-dir-icon");
            Label childName = new Label(child);
            childName.getStyleClass().add("org-child-label");
            childName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(childName, Priority.ALWAYS);

            Label childValor = new Label(FormatUtils.formatCurrency(valorPatrimonial(childProds)));
            childValor.getStyleClass().add("org-area-valor");

            Label childCount = new Label(String.valueOf(childProds.size()));
            childCount.getStyleClass().add("org-area-count");
            if (isMyArea) childCount.getStyleClass().add("org-area-count-myarea");

            childHeader.getChildren().addAll(dirIcon, childName, childValor, childCount);

            long alertasChild = childProds.stream()
                .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
                .count();
            if (alertasChild > 0) {
                FontIcon alertIcon = new FontIcon("mdi2a-alert-circle");
                alertIcon.setIconSize(12);
                Label alertDot = new Label(" " + alertasChild);
                alertDot.setGraphic(alertIcon);
                alertDot.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                alertDot.getStyleClass().addAll("org-alert-badge", "org-alert-badge-clickable");
                alertDot.setOnMouseClicked(e -> { e.consume(); showAreaProductsDialog(child, childProds, true); });
                Tooltip.install(alertDot, new Tooltip(alertasChild + " bien(es) agotado(s) o bajo stock en " + child + " — clic para verlos"));
                childHeader.getChildren().add(alertDot);
            }

            if (isMyArea) {
                Label badge = new Label("Tu área");
                badge.getStyleClass().add("org-my-area-badge");
                childHeader.getChildren().add(badge);
            }

            // Click child header to see all products for this area
            if (!childProds.isEmpty()) {
                String areaKey = child;
                childHeader.setOnMouseClicked(e -> showAreaProductsDialog(areaKey, childProds));
                Tooltip.install(childHeader, new Tooltip("Ver todos los bienes de " + child));
            }

            childCard.getChildren().add(childHeader);

            // Product preview inside child card
            if (!childProds.isEmpty()) {
                VBox prodsBox = new VBox(0);
                prodsBox.getStyleClass().add("org-products-inner-box");
                prodsBox.setPadding(new Insets(0, 8, 8, 8));
                addProductPreview(prodsBox, childProds, child);
                childCard.getChildren().add(prodsBox);
            }

            content.getChildren().add(childCard);
        }

        section.setContent(content);
        orgTree.getChildren().add(section);
    }

    private java.math.BigDecimal valorPatrimonial(List<Producto> prods) {
        return prods.stream()
            .map(p -> {
                java.math.BigDecimal precio = p.getPrecioVenta() != null ? p.getPrecioVenta() : java.math.BigDecimal.ZERO;
                return precio.multiply(java.math.BigDecimal.valueOf(p.getStockActual()));
            })
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    private HBox buildProductRow(Producto p) {
        HBox row = new HBox(8);
        row.getStyleClass().add("org-product-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 8, 4, 12));
        Label code = new Label(p.getCodigo());
        code.getStyleClass().add("org-product-code");
        code.setMinWidth(80);
        Label name = new Label(p.getNombre());
        name.getStyleClass().add("org-product-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        Label stock = new Label("Stock: " + p.getStockActual());
        stock.getStyleClass().add("org-product-stock");
        stock.getStyleClass().add(switch (p.getEstado()) {
            case AGOTADO    -> "stock-low";
            case BAJO_STOCK -> "stock-warn";
            default         -> "stock-ok";
        });
        row.getChildren().addAll(code, name, stock);
        return row;
    }

    private void addProductPreview(Pane container, List<Producto> prods, String areaName) {
        int preview = Math.min(prods.size(), 5);
        for (int i = 0; i < preview; i++)
            container.getChildren().add(buildProductRow(prods.get(i)));

        if (prods.size() > 5) {
            List<javafx.scene.Node> extras = new ArrayList<>();
            for (int j = 5; j < prods.size(); j++) {
                HBox row = buildProductRow(prods.get(j));
                row.setVisible(false);
                row.setManaged(false);
                container.getChildren().add(row);
                extras.add(row);
            }

            Hyperlink more = new Hyperlink("  +" + (prods.size() - 5) + " bienes más");
            more.getStyleClass().add("org-more-link");
            Hyperlink less = new Hyperlink("  Mostrar menos");
            less.getStyleClass().add("org-more-link");
            less.setVisible(false);
            less.setManaged(false);

            more.setOnAction(e -> {
                extras.forEach(n -> { n.setVisible(true); n.setManaged(true); });
                more.setVisible(false); more.setManaged(false);
                less.setVisible(true); less.setManaged(true);
            });
            less.setOnAction(e -> {
                extras.forEach(n -> { n.setVisible(false); n.setManaged(false); });
                less.setVisible(false); less.setManaged(false);
                more.setVisible(true); more.setManaged(true);
            });

            container.getChildren().addAll(more, less);
        }
    }

    private void showAreaProductsDialog(String areaName, List<Producto> prods) {
        showAreaProductsDialog(areaName, prods, false);
    }

    /** Same dialog, but when {@code soloAlertas} is true it's opened from the
     *  area's "⚠ N" alert badge instead of its bien count: filters the list
     *  down to agotados/bajo-stock only, so clicking the alert count actually
     *  shows those bienes instead of the area's full inventory. */
    private void showAreaProductsDialog(String areaName, List<Producto> allProds, boolean soloAlertas) {
        List<Producto> prods = soloAlertas
            ? allProds.stream()
                .filter(p -> p.getEstado() == EstadoProducto.AGOTADO || p.getEstado() == EstadoProducto.BAJO_STOCK)
                .toList()
            : allProds;

        ButtonType btnVerInventario = new ButtonType("Ver en Inventario →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerInventario, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(680);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        // Jumps to the real Inventario module pre-filtered by this área, so
        // the user can actually act on the bienes (editar, dar de baja,
        // etc.) instead of only viewing them in this read-only table.
        javafx.scene.Node verBtn = dialog.getDialogPane().lookupButton(btnVerInventario);
        if (verBtn != null) {
            verBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
                com.sibim.session.NavigationContext.setPendingAreaFilter(areaName);
                if (MainController.getInstance() != null) MainController.getInstance().navigateTo("productos");
            });
        }

        HBox header = soloAlertas
            ? DialogUtil.gradientHeader("mdi2a-alert-circle-outline", areaName,
                prods.size() + (prods.size() == 1 ? " bien agotado o con bajo stock" : " bienes agotados o con bajo stock"),
                "#D97706", "#B45309")
            : DialogUtil.gradientHeader("mdi2f-folder-outline", areaName,
                prods.size() + (prods.size() == 1 ? " bien registrado en esta área" : " bienes registrados en esta área"),
                "#4338CA", "#6D28D9");

        TableView<Producto> tbl = new TableView<>(FXCollections.observableArrayList(prods));
        tbl.setPrefHeight(360);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Producto, String> cCod = new TableColumn<>("Código");
        cCod.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));
        cCod.setPrefWidth(90);

        TableColumn<Producto, String> cNombre = new TableColumn<>("Bien");
        cNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        cNombre.setPrefWidth(180);

        TableColumn<Producto, String> cResguard = new TableColumn<>("Resguardante");
        cResguard.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getResguardante() != null ? c.getValue().getResguardante() : "—"));
        cResguard.setPrefWidth(110);

        TableColumn<Producto, String> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getStockActual())));
        cStock.setPrefWidth(65);
        cStock.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null); getStyleClass().removeAll("stock-ok", "stock-warn", "stock-low");
                if (empty || item == null || getTableRow() == null || getTableRow().getItem() == null) return;
                setText(item);
                getStyleClass().add(switch (getTableRow().getItem().getEstado()) {
                    case AGOTADO    -> "stock-low";
                    case BAJO_STOCK -> "stock-warn";
                    default         -> "stock-ok";
                });
            }
        });

        TableColumn<Producto, String> cEstado = new TableColumn<>("Estado");
        cEstado.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEstado().getEtiqueta()));
        cEstado.setPrefWidth(100);
        cEstado.setCellFactory(com.sibim.util.DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Agotado"    -> "cell-badge-danger";
            case "Bajo Stock" -> "cell-badge-warning";
            case "Vencido"    -> "cell-badge-purple";
            default           -> "cell-badge-success";
        }));

        tbl.getColumns().add(cCod);
        tbl.getColumns().add(cNombre);
        tbl.getColumns().add(cResguard);
        tbl.getColumns().add(cStock);
        tbl.getColumns().add(cEstado);

        tbl.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = tbl.getSelectionModel().getSelectedItem();
                if (sel != null) ProductoDetailDialog.show(sel, searchField.getScene(), movimientoService, log);
            }
        });
        tbl.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                tbl.getSelectionModel().clearSelection(); e.consume();
            }
        });
        MenuItem cmDetalle = new MenuItem("Ver detalle");
        cmDetalle.setGraphic(new FontIcon("mdi2e-eye-outline"));
        cmDetalle.setOnAction(e -> {
            Producto sel = tbl.getSelectionModel().getSelectedItem();
            if (sel != null) ProductoDetailDialog.show(sel, searchField.getScene(), movimientoService, log);
        });
        MenuItem cmFicha = new MenuItem("Imprimir ficha técnica");
        cmFicha.setGraphic(new FontIcon("mdi2f-file-document-outline"));
        cmFicha.setOnAction(e -> {
            Producto sel = tbl.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            DialogUtil.runAsyncWithProgress(searchField.getScene(), "Generando ficha…",
                () -> reporteService.exportFichaTecnica(sel, movimientoService.getByProducto(sel.getId())),
                file -> DialogUtil.showExportResultDialog(searchField.getScene(), file),
                ex -> { log.error("Error ficha técnica desde organigrama", ex); NotificacionUtil.error(searchField.getScene(), "No se pudo generar la ficha técnica"); });
        });
        ContextMenu cm = new ContextMenu(cmDetalle, new SeparatorMenuItem(), cmFicha);
        cm.setOnShowing(e -> {
            boolean none = tbl.getSelectionModel().getSelectedItem() == null;
            cmDetalle.setDisable(none);
            cmFicha.setDisable(none);
        });
        tbl.setContextMenu(cm);

        TextField dlgSearch = new TextField();
        dlgSearch.setPromptText("Buscar por nombre o código…");
        dlgSearch.getStyleClass().add("search-field");
        dlgSearch.setPadding(new javafx.geometry.Insets(0, 12, 0, 12));
        dlgSearch.textProperty().addListener((obs, o, q) -> {
            String lower = q.toLowerCase();
            List<Producto> filtrado = prods.stream()
                .filter(p -> lower.isBlank()
                    || p.getNombre().toLowerCase().contains(lower)
                    || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(lower)))
                .toList();
            tbl.getItems().setAll(filtrado);
        });

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, dlgSearch, tbl), 270, 70);
        VBox content = new VBox(8, header, dlgSearch, tbl);
        content.setPadding(new javafx.geometry.Insets(0, 0, 0, 0));
        dialog.getDialogPane().setContent(content);
        Platform.runLater(() -> dlgSearch.requestFocus());
        dialog.showAndWait();
    }
}
