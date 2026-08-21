package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import org.kordamp.ikonli.javafx.FontIcon;
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

    @FXML private TextField searchField;
    @FXML private Button btnClearSearch;
    @FXML private VBox orgTree;
    @FXML private ProgressIndicator spinner;
    @FXML private Label lblStatAreas;
    @FXML private Label lblStatBienes;
    @FXML private Label lblStatTopArea;
    @FXML private VBox  statCardAreas;
    @FXML private VBox  statCardBienes;
    @FXML private VBox  statCardTop;

    private final ProductoRepository productoRepo = new ProductoRepository();
    private Map<String, List<Producto>> productosPorArea = new HashMap<>();

    @FXML private void onRefresh() { loadData(true); }

    @FXML
    public void initialize() {
        SearchUtils.debounce(searchField, 280, this::buildTree);
        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        loadData(false);
        AnimationUtils.staggeredFadeInUp(
            java.util.List.of(statCardAreas, statCardBienes, statCardTop), 300, 55);
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void loadData(boolean showSuccessToast) {
        spinner.setVisible(true); spinner.setManaged(true);
        DialogUtil.runAsync(
            () -> productoRepo.findAll().stream().collect(Collectors.groupingBy(Producto::getArea)),
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
                NotificacionUtil.error(searchField.getScene(), "No se pudo cargar el organigrama");
            }
        );
    }

    private void updateStats() {
        if (lblStatAreas == null) return;
        int totalBienes = productosPorArea.values().stream().mapToInt(List::size).sum();
        AnimationUtils.animateCount(lblStatAreas,  productosPorArea.size(), 650);
        AnimationUtils.animateCount(lblStatBienes, totalBienes,             800);
        javafx.animation.PauseTransition pop = new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
        pop.setOnFinished(e -> {
            if (statCardAreas  != null) AnimationUtils.statCardPop(statCardAreas);
            if (statCardBienes != null) AnimationUtils.statCardPop(statCardBienes);
            if (statCardTop    != null) AnimationUtils.statCardPop(statCardTop);
        });
        pop.play();
        productosPorArea.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().size()))
            .ifPresentOrElse(
                e -> {
                    String topArea = e.getKey();
                    javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(820));
                    delay.setOnFinished(ev -> lblStatTopArea.setText(topArea));
                    delay.play();
                },
                () -> lblStatTopArea.setText("—"));
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
            VBox empty = new VBox(6);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(40, 0, 40, 0));
            FontIcon icon = new FontIcon("mdi2o-office-building-outline");
            icon.setIconSize(18);
            icon.getStyleClass().add("empty-icon-lg");
            Label msg = new Label(q.isBlank() ? "No hay áreas para mostrar" : "No se encontraron áreas para \"" + filter + "\"");
            msg.getStyleClass().add("empty-state-msg");
            empty.getChildren().addAll(icon, msg);
            orgTree.getChildren().add(empty);
        }
        AnimationUtils.staggeredFadeInUp(orgTree.getChildren(), 270, 50);
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
        if (!filter.isBlank()) {
            List<Producto> allAreaProdsForFilter = new java.util.ArrayList<>(
                productosPorArea.getOrDefault(parentName, List.of()));
            children.forEach(c -> allAreaProdsForFilter.addAll(productosPorArea.getOrDefault(c, List.of())));
            boolean nameMatch = parentName.toLowerCase().contains(filter)
                || children.stream().anyMatch(c -> c.toLowerCase().contains(filter));
            if (!nameMatch && !matchesFilter(allAreaProdsForFilter, filter)) return;
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
        List<Producto> allAreaProds = new java.util.ArrayList<>(prods);
        children.forEach(c -> allAreaProds.addAll(productosPorArea.getOrDefault(c, List.of())));
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
            alertDot.getStyleClass().add("org-alert-badge");
            Tooltip.install(alertDot, new Tooltip(alertasArea + " bien(es) agotado(s) o bajo stock en esta área"));
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
                alertDot.getStyleClass().add("org-alert-badge");
                Tooltip.install(alertDot, new Tooltip(alertasChild + " bien(es) agotado(s) o bajo stock en " + child));
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

    private void addProductPreview(Pane container, List<Producto> prods, String areaName) {
        int preview = Math.min(prods.size(), 5);
        for (int i = 0; i < preview; i++) {
            Producto p = prods.get(i);
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
            container.getChildren().add(row);
        }
        if (prods.size() > 5) {
            Hyperlink more = new Hyperlink("  +" + (prods.size() - 5) + " bienes más — ver todos");
            more.getStyleClass().add("org-more-link");
            more.setOnAction(e -> showAreaProductsDialog(areaName, prods));
            container.getChildren().add(more);
        }
    }

    private void showAreaProductsDialog(String areaName, List<Producto> prods) {
        ButtonType btnVerInventario = new ButtonType("Ver en Inventario →", ButtonBar.ButtonData.OTHER);
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(btnVerInventario, ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(580);
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

        HBox header = DialogUtil.gradientHeader("mdi2f-folder-outline", areaName,
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
        cNombre.setPrefWidth(230);

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
        tbl.getColumns().add(cStock);
        tbl.getColumns().add(cEstado);
        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, tbl), 270, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, tbl));
        dialog.showAndWait();
    }
}
