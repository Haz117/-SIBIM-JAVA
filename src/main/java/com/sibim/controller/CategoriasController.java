package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.repository.CategoriaRepository;
import com.sibim.session.SessionManager;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
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

    private final CategoriaRepository categoriaRepo = new CategoriaRepository();
    private ObservableList<Categoria> allData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();

        boolean isAdmin = SessionManager.isAdmin();
        btnNueva.setVisible(isAdmin);    btnNueva.setManaged(isAdmin);
        if (btnEditCat   != null) { btnEditCat.setVisible(isAdmin);   btnEditCat.setManaged(isAdmin); }
        if (btnDeleteCat != null) { btnDeleteCat.setVisible(isAdmin); btnDeleteCat.setManaged(isAdmin); }
        if (rootPane != null && isAdmin) {
            rootPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == javafx.scene.input.KeyCode.N && ev.isControlDown()) {
                    onNuevaCategoria(); ev.consume();
                } else if (ev.getCode() == javafx.scene.input.KeyCode.E && ev.isControlDown()
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
            MenuItem cmEditar    = new MenuItem("✏  Editar");
            MenuItem cmEliminar  = new MenuItem("✕  Eliminar");
            cmEditar.setOnAction(e -> onEdit());
            cmEliminar.setOnAction(e -> onDelete());
            cm.getItems().addAll(cmEditar, new SeparatorMenuItem(), cmEliminar);
            table.setContextMenu(cm);
        }

        loadData();
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
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
        new Thread(() -> {
            try {
                List<Categoria> cats = categoriaRepo.findAll();
                Platform.runLater(() -> {
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                    allData.setAll(cats);
                    applyFilter(searchField.getText());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                    NotificacionUtil.error(table.getScene(), "No se pudo cargar las categorías");
                });
            }
        }).start();
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<Categoria> filtered = allData.stream()
            .filter(c -> q.isBlank()
                || c.getNombre().toLowerCase().contains(q)
                || (c.getDescripcion() != null && c.getDescripcion().toLowerCase().contains(q)))
            .toList();
        table.setItems(FXCollections.observableArrayList(filtered));
        lblTotal.setText(filtered.size() + (filtered.size() == 1 ? " categoría" : " categorías"));
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
        new Thread(() -> {
            try {
                if (categoriaRepo.tieneProductos(sel.getId())) {
                    Platform.runLater(() -> NotificacionUtil.error(table.getScene(),
                        "No se puede eliminar: hay bienes asignados a esta categoria"));
                    return;
                }
                categoriaRepo.delete(sel.getId());
                Platform.runLater(() -> {
                    allData.remove(sel);
                    applyFilter(searchField.getText());
                    NotificacionUtil.exito(table.getScene(), "Categoría \"" + sel.getNombre() + "\" eliminada");
                });
            } catch (Exception e) {
                Platform.runLater(() -> NotificacionUtil.error(table.getScene(), "No se pudo eliminar la categoría"));
            }
        }).start();
    }

    private void showDialog(Categoria existing) {
        boolean isNew = existing == null;
        Dialog<Categoria> dialog = DialogUtil.create(460);
        DialogUtil.styleOkButton(dialog.getDialogPane(), isNew ? "#0891B2" : "#4338CA");

        HBox header = DialogUtil.gradientHeader(
            isNew ? "🏷" : "✏",
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
        TextField fColor = new TextField(initColor);
        fColor.setPromptText("#RRGGBB");
        fColor.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.shape.Circle colorDot = new javafx.scene.shape.Circle(10);
        colorDot.setEffect(new javafx.scene.effect.DropShadow(4, 0, 1,
            javafx.scene.paint.Color.color(0, 0, 0, 0.18)));
        Runnable updateDot = () -> {
            try { colorDot.setFill(javafx.scene.paint.Color.web(fColor.getText().trim())); }
            catch (Exception ignored) { colorDot.setFill(javafx.scene.paint.Color.LIGHTGRAY); }
        };
        updateDot.run();
        fColor.textProperty().addListener((obs, o, n) -> updateDot.run());

        Label previewBadge = new Label(
            (existing != null && !existing.getNombre().isBlank()) ? existing.getNombre() : "Nombre");
        previewBadge.getStyleClass().add("cat-preview-badge");
        Runnable updatePreview = () -> {
            String name = fNombre.getText().isBlank() ? "Nombre" : fNombre.getText();
            String col = initColor;
            try { javafx.scene.paint.Color.web(fColor.getText().trim()); col = fColor.getText().trim(); }
            catch (Exception ignored) {}
            previewBadge.setText(name);
            previewBadge.setStyle("-fx-background-color: " + col + "22; -fx-text-fill: " + col + ";");
        };
        fNombre.textProperty().addListener((o, a, b) -> updatePreview.run());
        fColor.textProperty().addListener((o, a, b) -> updatePreview.run());
        updatePreview.run();

        HBox colorRow = new HBox(8);
        colorRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        colorRow.getChildren().addAll(colorDot, fColor);
        HBox.setHgrow(fColor, Priority.ALWAYS);

        TextField fIcono = new TextField(existing != null && existing.getIcono() != null
            ? existing.getIcono() : "");
        fIcono.setPromptText("Emoji o código, ej. 🖥 📦 🪑");
        fIcono.setMaxWidth(Double.MAX_VALUE);

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
        grid.add(DialogUtil.fieldLabel("Nombre *"),    0, r); grid.add(fNombre,    1, r++);
        grid.add(DialogUtil.fieldLabel("Descripción"), 0, r); grid.add(fDesc,      1, r++);
        grid.add(DialogUtil.fieldLabel("Color *"),     0, r); grid.add(colorRow,   1, r++);
        grid.add(DialogUtil.fieldLabel("Ícono"),       0, r); grid.add(fIcono,     1, r++);
        grid.add(new Label(),                          0, r); grid.add(previewRow, 1, r);

        dialog.getDialogPane().setContent(new VBox(0, header, grid));
        Platform.runLater(() -> fNombre.requestFocus());

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            if (fNombre.getText().isBlank()) {
                fNombre.getStyleClass().add("field-error");
                return null;
            }
            String colorVal = fColor.getText().trim();
            if (!colorVal.isEmpty() && !colorVal.matches("^#[0-9A-Fa-f]{6}$")) {
                NotificacionUtil.advertencia(fColor.getScene(), "Color inválido — usa formato #RRGGBB");
                return null;
            }
            Categoria c = existing != null ? existing : new Categoria();
            if (c.getId() == null) { c.setId(UUID.randomUUID().toString()); c.setCreadoEn(LocalDateTime.now()); }
            c.setNombre(fNombre.getText().trim());
            c.setDescripcion(fDesc.getText().trim());
            c.setColor(colorVal);
            c.setIcono(fIcono.getText().trim());
            return c;
        });

        dialog.showAndWait().ifPresent(c -> DialogUtil.runAsync(
            () -> categoriaRepo.save(c),
            () -> {
                if (isNew) allData.add(c);
                NotificacionUtil.exito(table.getScene(),
                    isNew ? "Categoría registrada exitosamente" : "Categoría actualizada correctamente");
                applyFilter(searchField.getText());
            },
            ex -> NotificacionUtil.error(table.getScene(), "No se pudo guardar la categoría")
        ));
    }
}
