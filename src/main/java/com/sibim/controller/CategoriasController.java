package com.sibim.controller;

import com.sibim.model.Categoria;
import com.sibim.service.CategoriaService;
import com.sibim.session.NavigationContext;
import com.sibim.session.SessionManager;
import com.sibim.util.AnimationUtils;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.EmptyStateUtil;
import com.sibim.util.NotificacionUtil;
import com.sibim.util.SearchUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

public class CategoriasController {

    @FXML private VBox rootPane;
    @FXML private TextField searchField;
    @FXML private TableView<Categoria> table;
    @FXML private Button btnResetColumns;
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
    @FXML private VBox  statCardTotal;
    @FXML private VBox  statCardClasificados;
    @FXML private VBox  statCardTop;
    @FXML private Label lblStatTotal;
    @FXML private Label lblStatClasificados;
    @FXML private Label lblStatTop;
    @FXML private Label helpStatTotal;
    @FXML private Label helpStatClasificados;
    @FXML private Label helpStatTop;
    @FXML private VBox resumenBox;
    @FXML private Button btnToggleResumen;
    @FXML private Label helpResumen;

    private static final Preferences STICKY =
        Preferences.userRoot().node("sibim/filters/categorias");

    private final CategoriaService categoriaService = new CategoriaService();
    private ObservableList<Categoria> allData = FXCollections.observableArrayList();
    private Timeline skeletonPulse;
    private Node defaultPlaceholder;

    @FXML
    public void initialize() {
        setupTable();
        for (Label badge : new Label[]{ helpCategorias, helpStatTotal, helpStatClasificados, helpStatTop, helpResumen }) {
            if (badge != null) DialogUtil.enableClickToShowTooltip(badge);
        }
        if (btnToggleResumen != null && resumenBox != null)
            DialogUtil.makeCollapsible("categorias.resumen.colapsado", btnToggleResumen, resumenBox,
                "Mostrar resumen", "Ocultar resumen");

        boolean isAdmin = SessionManager.isAdmin();
        btnNueva.setVisible(isAdmin);    btnNueva.setManaged(isAdmin);
        if (btnEditCat   != null) { btnEditCat.setVisible(isAdmin);   btnEditCat.setManaged(isAdmin); }
        if (btnDeleteCat != null) { btnDeleteCat.setVisible(isAdmin); btnDeleteCat.setManaged(isAdmin); }
        if (rootPane != null) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
                if (ev.getCode() == KeyCode.F && ev.isControlDown()) {
                    if (searchField != null) { searchField.requestFocus(); searchField.selectAll(); }
                    ev.consume();
                } else if (isAdmin && ev.getCode() == KeyCode.N && ev.isControlDown()) {
                    onNuevaCategoria(); ev.consume();
                } else if (isAdmin && ev.getCode() == KeyCode.E && ev.isControlDown()
                        && table.getSelectionModel().getSelectedItem() != null) {
                    onEdit(); ev.consume();
                }
            });
        }

        if (btnClearSearch != null) {
            searchField.textProperty().addListener((obs, o, n) -> btnClearSearch.setVisible(!n.isBlank()));
            btnClearSearch.setOnAction(e -> { searchField.clear(); searchField.requestFocus(); });
        }
        SearchUtils.setupSearchHistory("sibim/search-history/categorias", searchField, () -> applyFilter(searchField.getText()));
        SearchUtils.debounce(searchField, 250, q -> { STICKY.put("search", q != null ? q : ""); applyFilter(q); });
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) -> {
            boolean s = sel != null;
            if (btnEditCat   != null && isAdmin) btnEditCat.setDisable(!s);
            if (btnDeleteCat != null && isAdmin) btnDeleteCat.setDisable(!s);
        });
        if (btnEditCat   != null && isAdmin) {
            btnEditCat.setDisable(true);
            javafx.scene.control.Tooltip.install(btnEditCat, new javafx.scene.control.Tooltip("Selecciona una categoría para editarla"));
        }
        if (btnDeleteCat != null && isAdmin) {
            btnDeleteCat.setDisable(true);
            javafx.scene.control.Tooltip.install(btnDeleteCat, new javafx.scene.control.Tooltip("Selecciona una categoría para eliminarla"));
        }
        table.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.DELETE && isAdmin
                    && table.getSelectionModel().getSelectedItem() != null) {
                onDelete(); ev.consume();
            } else if (ev.getCode() == KeyCode.ESCAPE) {
                table.getSelectionModel().clearSelection(); ev.consume();
            }
        });
        table.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) return;
            Categoria sel = table.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (isAdmin) {
                onEdit();
            } else if (sel.getTotalProductos() > 0) {
                NavigationContext.setPendingCategoryFilter(sel.getNombre());
                MainController mc = MainController.getInstance();
                if (mc != null) mc.navigateTo("productos");
            }
        });

        {
            ContextMenu cm = new ContextMenu();
            MenuItem cmVerBienes = new MenuItem("Ver bienes de esta categoría");
            cmVerBienes.setGraphic(new FontIcon("mdi2p-package-variant"));
            cmVerBienes.setOnAction(e -> {
                Categoria sel = table.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getTotalProductos() > 0) {
                    NavigationContext.setPendingCategoryFilter(sel.getNombre());
                    MainController mc = MainController.getInstance();
                    if (mc != null) mc.navigateTo("productos");
                }
            });
            cm.setOnShowing(e -> {
                Categoria sel = table.getSelectionModel().getSelectedItem();
                cmVerBienes.setDisable(sel == null || sel.getTotalProductos() == 0);
            });
            cm.getItems().add(cmVerBienes);
            if (isAdmin) {
                MenuItem cmEditar   = new MenuItem("Editar");
                cmEditar.setGraphic(new FontIcon("mdi2p-pencil"));
                MenuItem cmFusionar = new MenuItem("Fusionar con…");
                cmFusionar.setGraphic(new FontIcon("mdi2s-source-merge"));
                MenuItem cmEliminar = new MenuItem("Eliminar");
                cmEliminar.setGraphic(new FontIcon("mdi2d-delete-outline"));
                cmEditar.setOnAction(e -> onEdit());
                cmFusionar.setOnAction(e -> onFusionar());
                cmEliminar.setOnAction(e -> onDelete());
                cm.getItems().addAll(new SeparatorMenuItem(), cmEditar, cmFusionar, new SeparatorMenuItem(), cmEliminar);
            }
            table.setContextMenu(cm);
        }

        loadData();
        if (rootPane != null) {
            var children = rootPane.getChildren();
            if (!children.isEmpty()) {
                AnimationUtils.fadeInDown(children.get(0), 280, 0);
                if (children.size() > 1)
                    AnimationUtils.staggeredFadeInUp(
                        new ArrayList<>(children.subList(1, children.size())), 300, 55);
            }
        }
        String savedSearch = STICKY.get("search", "");
        if (!savedSearch.isBlank()) searchField.setText(savedSearch);
        Platform.runLater(() -> { if (searchField != null) searchField.requestFocus(); });
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        skeletonPulse = AnimationUtils.buildSkeletonPlaceholder(table, 7);
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colNombre.setCellFactory(DialogUtil.highlightCellFactory(
            () -> searchField != null ? searchField.getText() : ""));
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
        // Same vector icon the category gets in Bienes, in its own color — the
        // stored emoji rendered as monochrome, often unrecognizable glyphs.
        colIcono.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));
        colIcono.setCellFactory(col -> new TableCell<>() {
            { setContentDisplay(ContentDisplay.GRAPHIC_ONLY); setAlignment(javafx.geometry.Pos.CENTER); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                Categoria c = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (c == null) { setGraphic(null); return; }
                org.kordamp.ikonli.javafx.FontIcon ico = com.sibim.util.CategoriaIcons.iconFor(c.getNombre());
                ico.setIconSize(18);
                // Inline style: a table-cell icon rule in styles.css outranks setIconColor(),
                // and this is a user-picked DB color (same exception as the swatch).
                String color = c.getColor() != null && c.getColor().matches("#[0-9A-Fa-f]{3,8}") ? c.getColor() : "#4338CA";
                String base = ico.getStyle() == null ? "" : ico.getStyle();   // keeps ikonli's own icon-font style
                ico.setStyle(base + (base.isBlank() || base.endsWith(";") ? "" : ";") + "-fx-icon-color: " + color + ";");
                setGraphic(ico);
            }
        });
        colProductos.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalProductos"));
        colProductos.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("cat-count-active", "cell-muted", "org-area-count-clickable");
                Tooltip.uninstall(this, null);
                setOnMouseClicked(null);
                setCursor(null);
                if (empty || item == null) { setText(null); return; }
                setText(String.valueOf(item));
                if (item > 0) {
                    getStyleClass().addAll("cat-count-active", "org-area-count-clickable");
                    setCursor(javafx.scene.Cursor.HAND);
                    Tooltip.install(this, new Tooltip("Ver " + FormatUtils.plural(item, "el bien", "los bienes") + " de esta categoría"));
                    setOnMouseClicked(e -> {
                        Categoria cat = getTableView().getItems().get(getIndex());
                        NavigationContext.setPendingCategoryFilter(cat.getNombre());
                        MainController mc = MainController.getInstance();
                        if (mc != null) mc.navigateTo("productos");
                    });
                } else {
                    getStyleClass().add("cell-muted");
                }
            }
        });
        DialogUtil.setupColumnReset(table, btnResetColumns, STICKY);
        DialogUtil.persistTableSort(table, STICKY, "sort");
        DialogUtil.persistColumnWidths(table, STICKY, "colW");
    }

    private void stopSkeleton() {
        if (skeletonPulse != null) { skeletonPulse.stop(); skeletonPulse = null; }
        String hint = SessionManager.isAdmin() ? "Presiona Ctrl+N para crear la primera" : "";
        defaultPlaceholder = EmptyStateUtil.build("mdi2t-tag-multiple-outline", "No hay categorías registradas", hint);
        table.setPlaceholder(defaultPlaceholder);
    }

    private void loadData() {
        if (spinner != null) { spinner.setVisible(true); spinner.setManaged(true); }
        DialogUtil.runAsync(
            () -> categoriaService.findAll(),
            cats -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                stopSkeleton();
                allData.setAll(cats);
                applyFilter(searchField.getText());
                updateStats();
            },
            e -> {
                if (spinner != null) { spinner.setVisible(false); spinner.setManaged(false); }
                stopSkeleton();
                NotificacionUtil.errorConAccion(table.getScene(), "No se pudo cargar las categorías", "Reintentar", this::loadData);
            }
        );
    }

    /** Stats always reflect the full unfiltered set (like Bienes/Depreciación
     *  do), not whatever the search box currently narrows the table to —
     *  they describe the whole catalog, not the current view. */
    private void updateStats() {
        if (lblStatTotal != null) AnimationUtils.animateCount(lblStatTotal, allData.size(), 600);
        int clasificados = allData.stream().mapToInt(Categoria::getTotalProductos).sum();
        if (lblStatClasificados != null) AnimationUtils.animateCount(lblStatClasificados, clasificados, 650);
        if (lblStatTop != null) {
            Categoria top = allData.stream()
                .max(Comparator.comparingInt(Categoria::getTotalProductos))
                .filter(c -> c.getTotalProductos() > 0)
                .orElse(null);
            lblStatTop.setText(top != null ? top.getNombre() : "—");
        }
        PauseTransition pop = new PauseTransition(Duration.millis(700));
        pop.setOnFinished(e -> {
            for (VBox card : new VBox[]{ statCardTotal, statCardClasificados, statCardTop }) {
                if (card != null) AnimationUtils.statCardPop(card);
            }
        });
        pop.play();
    }

    private void applyFilter(String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<Categoria> filtered = allData.stream()
            .filter(c -> q.isBlank()
                || c.getNombre().toLowerCase().contains(q)
                || (c.getDescripcion() != null && c.getDescripcion().toLowerCase().contains(q)))
            .toList();
        if (!q.isBlank() && filtered.isEmpty() && !allData.isEmpty()) {
            table.setPlaceholder(EmptyStateUtil.buildSearch(q));
        } else if (defaultPlaceholder != null) {
            table.setPlaceholder(defaultPlaceholder);
        }
        table.getItems().setAll(filtered);
        AnimationUtils.staggerTableRows(table);
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
                if (categoriaService.tieneProductos(sel.getId())) {
                    throw new IllegalStateException("No se puede eliminar: hay bienes asignados a esta categoria");
                }
                categoriaService.delete(sel.getId());
            },
            () -> {
                allData.remove(sel);
                applyFilter(searchField.getText());
                // save() upserts by id — re-running it after the delete recreates
                // the exact same row (same id/nombre/color/ícono), the same undo
                // pattern already used for "dar de baja" in ProductosController.
                NotificacionUtil.exitoConAccion(table.getScene(),
                    "Categoría \"" + sel.getNombre() + "\" eliminada",
                    "Deshacer",
                    () -> DialogUtil.runAsync(
                        () -> categoriaService.save(sel),
                        () -> { loadData(); NotificacionUtil.info(table.getScene(), "\"" + sel.getNombre() + "\" restaurada"); },
                        e2 -> NotificacionUtil.error(table.getScene(), "No se pudo deshacer la eliminación")
                    )
                );
            },
            e -> NotificacionUtil.error(table.getScene(),
                e instanceof IllegalStateException ? e.getMessage() : "No se pudo eliminar la categoría")
        );
    }

    private void showDialog(Categoria existing) {
        boolean isNew = existing == null;
        Dialog<Categoria> dialog = DialogUtil.create(460);
        DialogUtil.styleOkButton(dialog.getDialogPane(), isNew ? AppColors.CYAN : AppColors.INDIGO);

        HBox header = DialogUtil.gradientHeader(
            isNew ? "mdi2t-tag-plus-outline" : "mdi2p-pencil",
            isNew ? "Nueva Categoría" : "Editar Categoría",
            isNew ? "Agrega una nueva clasificación al inventario"
                  : "Actualiza los datos de la categoría " + (existing != null ? existing.getNombre() : ""),
            isNew ? AppColors.CYAN : AppColors.INDIGO,
            isNew ? AppColors.CYAN_D : AppColors.INDIGO_D);

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

        TextField fCodigoConac = new TextField(existing != null && existing.getCodigoConac() != null
            ? existing.getCodigoConac() : "");
        fCodigoConac.setMaxWidth(Double.MAX_VALUE);
        fCodigoConac.setPromptText("Ej. 1.2.4.1.1.511");

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
        grid.add(DialogUtil.fieldLabelWithHelp("Código CONAC",
            "Clave de clasificación contable federal (CONAC).\nEj: 1.2.4.1.1.511 = Muebles de oficina y estantería"),
                                               0, r); grid.add(fCodigoConac,  1, r++);
        grid.add(DialogUtil.fieldLabel("Color *"),     0, r); grid.add(colorSection, 1, r++);
        grid.add(DialogUtil.fieldLabel("Ícono"),       0, r); grid.add(iconGrid,     1, r++);
        grid.add(new Label(),                          0, r); grid.add(previewRow,   1, r);

        AnimationUtils.staggeredFadeInUp(List.of(header, grid), 270, 70);
        DialogUtil.setScrollableContent(dialog.getDialogPane(), new VBox(0, header, grid));
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
            String conacTxt = fCodigoConac.getText().trim();
            c.setCodigoConac(conacTxt.isEmpty() ? null : conacTxt);
            return c;
        });

        dialog.showAndWait().ifPresent(c -> DialogUtil.runAsync(
            () -> categoriaService.save(c),
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

    private void onFusionar() {
        Categoria source = table.getSelectionModel().getSelectedItem();
        if (source == null) return;
        List<Categoria> others = allData.stream()
            .filter(c -> !c.getId().equals(source.getId()))
            .toList();
        if (others.isEmpty()) {
            NotificacionUtil.advertencia(table.getScene(), "No hay otras categorías disponibles para fusionar");
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        DialogUtil.applyOwner(dlg);
        ButtonType okType = new ButtonType("Fusionar", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);
        dlg.getDialogPane().setPrefWidth(430);
        DialogUtil.applyStylesheet(dlg.getDialogPane());

        HBox header = DialogUtil.gradientHeader("mdi2s-source-merge",
            "Fusionar Categoría",
            "Mueve todos los bienes a otra categoría y elimina \"" + source.getNombre() + "\"",
            AppColors.WARNING, AppColors.WARNING_D);

        GridPane form = DialogUtil.formGrid(130);
        String origen = source.getNombre()
            + (source.getTotalProductos() > 0 ? "  (" + source.getTotalProductos() + " bienes)" : "");
        form.add(DialogUtil.fieldLabel("Categoría origen:"), 0, 0);
        form.add(new Label(origen), 1, 0);

        ComboBox<Categoria> targetCombo = new ComboBox<>(FXCollections.observableArrayList(others));
        targetCombo.setMaxWidth(Double.MAX_VALUE);
        targetCombo.setPromptText("Selecciona la categoría destino…");
        targetCombo.getStyleClass().add("form-input");
        targetCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Categoria c) { return c == null ? "" : c.getNombre(); }
            @Override public Categoria fromString(String s) { return null; }
        });
        form.add(DialogUtil.fieldLabel("Fusionar en *:"), 0, 1);
        form.add(targetCombo, 1, 1);

        Label warnLbl = new Label("\"" + source.getNombre() + "\" será eliminada. Esta acción no se puede deshacer.");
        warnLbl.getStyleClass().add("muted-sm");
        warnLbl.setWrapText(true);

        VBox content = new VBox(12, header, form, warnLbl);
        content.setPadding(new Insets(0, 16, 16, 16));
        dlg.getDialogPane().setContent(content);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(okType);
        okBtn.getStyleClass().add("btn-danger");
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            if (targetCombo.getValue() == null) { AnimationUtils.shake(targetCombo); ev.consume(); }
        });
        Platform.runLater(targetCombo::requestFocus);

        dlg.showAndWait().filter(bt -> bt == okType).ifPresent(bt -> {
            Categoria target = targetCombo.getValue();
            if (target == null) return;
            DialogUtil.runAsync(
                () -> { categoriaService.fusionar(source.getId(), target.getId()); return null; },
                v -> {
                    loadData();
                    NotificacionUtil.exito(table.getScene(),
                        "\"" + source.getNombre() + "\" fusionada en \"" + target.getNombre() + "\"");
                },
                e -> NotificacionUtil.error(table.getScene(), "No se pudo fusionar: "
                    + (e.getMessage() != null ? e.getMessage() : "Error"))
            );
        });
    }
}
