package com.sibim.controller;

import com.sibim.config.Areas;
import com.sibim.model.Producto;
import com.sibim.model.enums.EstadoProducto;
import com.sibim.repository.ProductoRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;
import java.util.stream.Collectors;

public class OrganigramaController {

    @FXML private TextField searchField;
    @FXML private Button btnClearSearch;
    @FXML private VBox orgTree;
    @FXML private ProgressIndicator spinner;

    private final ProductoRepository productoRepo = new ProductoRepository();
    private Map<String, List<Producto>> productosPorArea = new HashMap<>();

    @FXML private void onRefresh() {
        loadData();
        NotificacionUtil.info(searchField.getScene(), "Organigrama actualizado");
    }

    @FXML
    public void initialize() {
        PauseTransition debounce = new PauseTransition(Duration.millis(280));
        searchField.textProperty().addListener((obs, old, val) -> {
            debounce.setOnFinished(e -> buildTree(val));
            debounce.playFromStart();
        });
        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        loadData();
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void loadData() {
        spinner.setVisible(true); spinner.setManaged(true);
        new Thread(() -> {
            try {
                List<Producto> todos = productoRepo.findAll();
                productosPorArea = todos.stream()
                    .collect(Collectors.groupingBy(Producto::getArea));
                Platform.runLater(() -> {
                    spinner.setVisible(false); spinner.setManaged(false);
                    buildTree(searchField.getText() != null ? searchField.getText() : "");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    spinner.setVisible(false); spinner.setManaged(false);
                    NotificacionUtil.error(searchField.getScene(), "No se pudo cargar el organigrama");
                });
            }
        }).start();
    }

    private void buildTree(String filter) {
        orgTree.getChildren().clear();
        String q = filter == null ? "" : filter.toLowerCase();

        // Presidencia
        addAreaSection("Despacho de la Presidencia", Areas.DIRECCIONES_PRESIDENCIA, q, true);

        // Secretarías
        for (Areas.SecretariaInfo sec : Areas.SECRETARIAS) {
            addAreaSection(sec.nombre(), sec.direcciones(), q, false);
        }

        // Autónomos
        addAreaSection("Organismos Autónomos", Areas.AUTONOMOS, q, false);
    }

    private void addAreaSection(String parentName, List<String> children, String filter, boolean expanded) {
        if (!filter.isBlank()
                && !parentName.toLowerCase().contains(filter)
                && children.stream().noneMatch(c -> c.toLowerCase().contains(filter))) {
            return;
        }

        TitledPane section = new TitledPane();
        section.setExpanded(expanded);

        // ── Header graphic ─────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 4, 2, 0));

        // Icon badge (secretaría-level)
        StackPane iconBadge = new StackPane();
        iconBadge.getStyleClass().add("org-section-icon-badge");
        Label iconLbl = new Label("🏛");
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
        header.getChildren().addAll(iconBadge, nameLabel, countLabel);

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
            if (!filter.isBlank() && !child.toLowerCase().contains(filter)) continue;
            List<Producto> childProds = productosPorArea.getOrDefault(child, List.of());

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

            Label dirIcon = new Label("📁");
            dirIcon.getStyleClass().add("org-dir-icon");
            Label childName = new Label(child);
            childName.getStyleClass().add("org-child-label");
            childName.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(childName, Priority.ALWAYS);

            Label childCount = new Label(String.valueOf(childProds.size()));
            childCount.getStyleClass().add("org-area-count");
            if (isMyArea) childCount.getStyleClass().add("org-area-count-myarea");

            childHeader.getChildren().addAll(dirIcon, childName, childCount);

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
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(580);
        DialogUtil.applyStylesheet(dialog.getDialogPane());

        HBox header = DialogUtil.gradientHeader("📁", areaName,
            prods.size() + (prods.size() == 1 ? " bien registrado en esta área" : " bienes registrados en esta área"),
            "#4338CA", "#6D28D9");

        TableView<Producto> tbl = new TableView<>(FXCollections.observableArrayList(prods));
        tbl.setPrefHeight(360);
        tbl.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

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
        cEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().add("cell-badge"); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); setGraphic(null); setText(null);
                if (empty || item == null) return;
                badge.setText(item);
                badge.getStyleClass().removeAll("cell-badge-success","cell-badge-warning","cell-badge-danger","cell-badge-purple");
                badge.getStyleClass().add(switch (item) {
                    case "Agotado"    -> "cell-badge-danger";
                    case "Bajo Stock" -> "cell-badge-warning";
                    case "Vencido"    -> "cell-badge-purple";
                    default           -> "cell-badge-success";
                });
                setGraphic(badge);
            }
        });

        tbl.getColumns().addAll(cCod, cNombre, cStock, cEstado);
        dialog.getDialogPane().setContent(new VBox(0, header, tbl));
        dialog.showAndWait();
    }
}
