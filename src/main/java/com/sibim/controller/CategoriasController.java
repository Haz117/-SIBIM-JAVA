package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.repository.CategoriaRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CategoriasController {

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private TableView<Categoria> table;
    @FXML private TableColumn<Categoria, String> colNombre;
    @FXML private TableColumn<Categoria, String> colDescripcion;
    @FXML private TableColumn<Categoria, String> colColor;
    @FXML private TableColumn<Categoria, String> colIcono;
    @FXML private TableColumn<Categoria, Integer> colProductos;
    @FXML private Label lblTotal;
    @FXML private Button btnNueva;
    @FXML private Button btnEditCat;
    @FXML private Button btnDeleteCat;
    @FXML private Button btnClearSearch;
    @FXML private ProgressIndicator spinner;
    @FXML private Label helpCategorias;

    private final CategoriaRepository categoriaRepo = new CategoriaRepository();
    private ObservableList<Categoria> allData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        if (helpCategorias != null) DialogUtil.enableClickToShowTooltip(helpCategorias);

        boolean isAdmin = SessionManager.isAdmin();
        btnNueva.setVisible(isAdmin);    btnNueva.setManaged(isAdmin);
        if (btnEditCat   != null) { btnEditCat.setVisible(isAdmin);   btnEditCat.setManaged(isAdmin); }
        if (btnDeleteCat != null) { btnDeleteCat.setVisible(isAdmin); btnDeleteCat.setManaged(isAdmin); }
        if (rootPane != null) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.F && ev.isControlDown()) {
                    if (searchField != null) { searchField.requestFocus(); searchField.selectAll(); }
                    ev.consume();
                } else if (isAdmin && ev.getCode() == javafx.scene.input.KeyCode.N && ev.isControlDown()) {
                    onNuevaCategoria(); ev.consume();
                } else if (isAdmin && ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()
                        && table.getSelectionModel().getSelectedItem() != null) {
                    onEdit(); ev.consume();
                }
            });
        }

        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        searchField.textProperty().addListener((obs, o, n) -> applyFilter(n));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            boolean s = sel != null;
            if (btnEditCat   != null && isAdmin) btnEditCat.setDisable(!s);
            if (btnDeleteCat != null && isAdmin) btnDeleteCat.setDisable(!s);
        });
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.DELETE && isAdmin
                    && table.getSelectionModel().getSelectedItem() != null) {
                onDelete(); ev.consume();
            }
        });
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null)
                onEdit();
        });

        if (isAdmin) {
            ContextMenu cm = new ContextMenu();
            MenuItem cmEditar    = new MenuItem("Editar");
            cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
            MenuItem cmEliminar  = new MenuItem("Eliminar");
            cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
            cmEditar.setOnAction(e -> onEdit());
            cmEliminar.setOnAction(e -> onDelete());
            cm.getItems().addAll(cmEditar, new SeparatorMenuItem(), cmEliminar);
            table.setContextMenu(cm);
        }

        loadData();
        if (rootPane != null) {
            var children = rootPane.getChildren();
            if (!children.isEmpty()) {
                AnimationUtils.fadeInDown(children.get(0), 280, 0);
                if (children.size() > 1)
                    AnimationUtils.staggeredFadeInUp(
                        new java.util.ArrayList<>(children.subList(1, children.size())), 300, 55);
            }
        }
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colDescripcion.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getDescripcion() != null ? c.getValue().getDescripcion() : ""));
        colColor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getColor()));
        colColor.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(9);
            private final Label hex = new Label();
            private final javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(8, dot, hex);
            { box.setAlignment(javafx.geometry.Pos.CENTER_LEFT); hex.getStyleClass().add("cat-hex-label"); }
            @Override protected void updateItem(String color, boolean empty) {
                super.updateItem(color, empty);
                if (empty || color == null) { setGraphic(null); setText(null); return; }
                try { dot.setFill(javafx.scene.paint.Color.web(color)); } catch (Exception e) { dot.setFill(javafx.scene.paint.Color.GRAY); }
                dot.setEffect(new javafx.scene.effect.DropShadow(4, 0, 1, javafx.scene.paint.Color.color(0,0,0,0.18)));
                hex.setText(color);
                setGraphic(box); setText(null);
            }
        });
        colIcono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIcono() != null ? c.getValue().getIcono() : ""));
        colIcono.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("icon-cell");
                if (empty || item == null || item.isBlank()) { setText(null); return; }
                setText(item);
                getStyleClass().add("icon-cell");
            }
        });
        colProductos.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalProductos"));
        colProductos.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("cat-count-active", "cell-muted");
                if (empty || item == null) { setText(null); return; }
                setText(String.valueOf(item));
                getStyleClass().add(item > 0 ? "cat-count-active" : "cell-muted");
            }
        });
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        DialogUtil.runAsync(
            () -> categoriaRepo.findAll(),
            cats -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                allData.setAll(cats);
                applyFilter(searchField.getText());
            },
            e -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                NotificacionUtil.error(table.getScene(), "No se pudo cargar las categorías");
            }
        );
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<Categoria> filtered = allData.stream()
            .filter(c -> q.isBlank()
                || c.getNombre().toLowerCase().contains(q)
                || (c.getDescripcion() != null && c.getDescripcion().toLowerCase().contains(q)))
            .toList();
        table.getItems().setAll(filtered);
        AnimationUtils.animateCount(lblTotal, filtered.size(), 350, v -> v + (v == 1 ? " categoría" : " categorías"));
    }

    @FXML private void onRefresh() { loadData(); }

    @FXML
    private void onNuevaCategoria() { showDialog(null); }

    @FXML
    private void onEdit() {
        Categoria sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            NotificacionUtil.advertencia(table.getScene(), "Selecciona una categoría para editar");
            return;
        }
        showDialog(sel);
    }

    @FXML
    private void onDelete() {
        Categoria sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        if (!ConfirmacionUtil.confirmarEliminar(sel.getNombre())) return;
        DialogUtil.runAsync(
            () -> {
                if (categoriaRepo.tieneProductos(sel.getId())) {
                    throw new IllegalStateException("No se puede eliminar: hay bienes asignados a esta categoria");
                }
                categoriaRepo.delete(sel.getId());
            },
            () -> {
                allData.remove(sel);
                applyFilter(searchField.getText());
                NotificacionUtil.exito(table.getScene(), "Categoría \"" + sel.getNombre() + "\" eliminada");
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof IllegalStateException ? e.getMessage() : "No se pudo eliminar la categoría")
        );
    }

    private void showDialog(Categoria existing) {
        boolean isNew = existing == null;
        Dialog<Categoria> dialog = DialogUtil.create(460);
        DialogUtil.styleOkButton(dialog.getDialogPane(), isNew ? "#0891B2" : "#4338CA");

        HBox header = DialogUtil.gradientHeader(
            isNew ? "mdi2t-tag-plus-outline" : "mdi2p-pencil",
            isNew ? "Nueva Categoría" : "Editar Categoría",
            isNew ? "Agrega una nueva clasificación al inventario"
                  : "Actualiza los datos de la categoría " + (existing != null ? existing.getNombre() : ""),
            isNew ? "#0891B2" : "#4338CA",
            isNew ? "#0E7490" : "#3730A3");

        // ── Form grid ────────────────────────────────────────────────────
        GridPane grid = DialogUtil.formGrid(110);

        // OK button starts disabled until nombre is filled
        Node okBtn = DialogUtil.getOkButton(dialog.getDialogPane());

        TextField fNombre = new TextField(existing != null ? existing.getNombre() : "");
        fNombre.setPromptText("ej. Mobiliario, Equipo de Cómputo...");
        fNombre.setMaxWidth(Double.MAX_VALUE);

        TextArea fDesc = new TextArea(existing != null && existing.getDescripcion() != null
            ? existing.getDescripcion() : "");
        fDesc.setPrefRowCount(2); fDesc.setMaxWidth(Double.MAX_VALUE);
        fDesc.setPromptText("Descripción opcional de la categoría");

        String initColor = existing != null ? existing.getColor() : "#6366F1";
        String[] selectedColor = { initColor };

        // ── Color: a curated swatch palette, not a raw hex/RGBA field —
        // clicking a swatch is the only way to set it, so the value is
        // always a valid, on-brand color. ──
        String[] colorPalette = {
            "#6366F1", "#8B5CF6", "#3B82F6", "#0EA5E9", "#10B981", "#14B8A6",
            "#F59E0B", "#F97316", "#EF4444", "#EC4899", "#64748B", "#111827"
        };
        javafx.scene.shape.Circle colorDot = new javafx.scene.shape.Circle(10);
        colorDot.setEffect(new javafx.scene.effect.DropShadow(4, 0, 1,
            javafx.scene.paint.Color.color(0, 0, 0, 0.18)));
        Label hexLabel = new Label();
        hexLabel.getStyleClass().add("cat-hex-label");
        Runnable updateDot = () -> {
            try { colorDot.setFill(javafx.scene.paint.Color.web(selectedColor[0])); }
            catch (IllegalArgumentException ignored) { colorDot.setFill(javafx.scene.paint.Color.LIGHTGRAY); }
            hexLabel.setText(selectedColor[0]);
        };

        Label previewBadge = new Label(
            (existing != null && !existing.getNombre().isBlank()) ? existing.getNombre() : "Nombre");
        previewBadge.getStyleClass().add("cat-preview-badge");
        Runnable updatePreview = () -> {
            String name = fNombre.getText().isBlank() ? "Nombre" : fNombre.getText();
            previewBadge.setText(name);
            previewBadge.setStyle("-fx-background-color: " + selectedColor[0] + "22; -fx-text-fill: " + selectedColor[0] + ";");
        };
        fNombre.textProperty().addListener((o, a, b) -> updatePreview.run());

        ToggleGroup colorGroup = new ToggleGroup();
        javafx.scene.layout.FlowPane colorSwatches = new javafx.scene.layout.FlowPane(8, 8);
        for (String hex : colorPalette) {
            ToggleButton swatch = new ToggleButton();
            swatch.setToggleGroup(colorGroup);
            swatch.getStyleClass().add("color-swatch");
            swatch.setStyle("-fx-background-color: " + hex + ";");
            if (hex.equalsIgnoreCase(selectedColor[0])) swatch.setSelected(true);
            swatch.setOnAction(e -> { selectedColor[0] = hex; updateDot.run(); updatePreview.run(); });
            colorSwatches.getChildren().add(swatch);
        }
        updateDot.run();
        updatePreview.run();

        HBox colorPreviewRow = new HBox(8, colorDot, hexLabel);
        colorPreviewRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox colorSection = new VBox(8, colorSwatches, colorPreviewRow);

        // ── Ícono: a preset grid, not free-text — guarantees every category
        // gets a real, consistent icon instead of a blank/mistyped field. ──
        String[] iconoHolder = { existing != null && existing.getIcono() != null ? existing.getIcono() : "" };
        String[] iconPalette = {
            "🪑", "🚗", "💻", "🖨", "🔧", "📷", "📦", "🏷",
            "📁", "🔌", "🖥", "📱", "🧰", "🎨", "📚", "🏗", "⚙", "🚒"
        };
        ToggleGroup iconGroup = new ToggleGroup();
        javafx.scene.layout.FlowPane iconGrid = new javafx.scene.layout.FlowPane(6, 6);
        ToggleButton noneIcon = new ToggleButton("—");
        noneIcon.setToggleGroup(iconGroup);
        noneIcon.getStyleClass().add("icon-swatch");
        noneIcon.setSelected(iconoHolder[0].isBlank());
        noneIcon.setOnAction(e -> iconoHolder[0] = "");
        iconGrid.getChildren().add(noneIcon);
        for (String ic : iconPalette) {
            ToggleButton btn = new ToggleButton(ic);
            btn.setToggleGroup(iconGroup);
            btn.getStyleClass().add("icon-swatch");
            if (ic.equals(iconoHolder[0])) btn.setSelected(true);
            btn.setOnAction(e -> iconoHolder[0] = ic);
            iconGrid.getChildren().add(btn);
        }

        HBox previewRow = new HBox(8);
        previewRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        previewRow.setPadding(new Insets(4, 0, 4, 0));
        previewRow.getChildren().addAll(
            new Label("Vista previa:") {{ getStyleClass().add("muted"); }},
            previewBadge
        );

        // Disable OK until nombre is non-empty
        if (okBtn != null) {
            okBtn.setDisable(fNombre.getText().isBlank());
            fNombre.textProperty().addListener((obs, o, n) -> okBtn.setDisable(n.isBlank()));
        }

        int r = 0;
        grid.add(DialogUtil.fieldLabel("Nombre *"),    0, r); grid.add(fNombre,      1, r++);
        grid.add(DialogUtil.fieldLabel("Descripción"), 0, r); grid.add(fDesc,        1, r++);
        grid.add(DialogUtil.fieldLabel("Color *"),     0, r); grid.add(colorSection, 1, r++);
        grid.add(DialogUtil.fieldLabel("Ícono"),       0, r); grid.add(iconGrid,     1, r++);
        grid.add(new Label(),                          0, r); grid.add(previewRow,   1, r);

        AnimationUtils.staggeredFadeInUp(java.util.List.of(header, grid), 270, 70);
        dialog.getDialogPane().setContent(new VBox(0, header, grid));
        Platform.runLater(() -> fNombre.requestFocus());

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            if (fNombre.getText().isBlank()) {
                fNombre.getStyleClass().add("field-error");
                AnimationUtils.shake(fNombre);
                return null;
            }
            Categoria c = existing != null ? existing : new Categoria();
            if (c.getId() == null) { c.setId(UUID.randomUUID().toString()); c.setCreadoEn(LocalDateTime.now()); }
            c.setNombre(fNombre.getText().trim());
            c.setDescripcion(fDesc.getText().trim());
            c.setColor(selectedColor[0]);
            c.setIcono(iconoHolder[0]);
            return c;
        });

        dialog.showAndWait().ifPresent(c -> DialogUtil.runAsync(
            () -> categoriaRepo.save(c),
            () -> {
                if (isNew) allData.add(c);
                NotificacionUtil.exito(table.getScene(),
                    isNew ? "Categoría registrada exitosamente" : "Categoría actualizada correctamente");
                applyFilter(searchField.getText());
                table.getSelectionModel().select(c);
                table.scrollTo(c);
            },
            ex -> NotificacionUtil.error(table.getScene(), "No se pudo guardar la categoría")
        ));
    }
}
