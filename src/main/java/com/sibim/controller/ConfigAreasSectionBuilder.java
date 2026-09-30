package com.sibim.controller;

import com.sibim.config.AreaCatalog;
import com.sibim.config.Areas;
import com.sibim.db.DatabaseConfig;
import com.sibim.service.AreaService;
import com.sibim.util.AnimationUtils;
import com.sibim.util.DialogUtil;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Optional;

/** Configuración → "Áreas y prefijos": the areas table (V24) that used to be
 *  hard-coded in Areas/AreaCodigos. Admins add an area or change where it
 *  hangs and its código prefix; names can't be edited (bienes store them). */
class ConfigAreasSectionBuilder {

    private static final String[] GRUPO_LABELS = { "Presidencia", "Secretaría", "Dirección", "Organismo autónomo" };

    private final VBox anchorSection;
    private final AreaService areaService = new AreaService();
    private TableView<AreaCatalog.Entrada> table;

    ConfigAreasSectionBuilder(VBox anchorSection) {
        this.anchorSection = anchorSection;
    }

    void build() {
        if (anchorSection == null || !(anchorSection.getParent() instanceof VBox rootVBox)) return;

        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));

        FontIcon titleIcon = new FontIcon("mdi2s-sitemap");
        titleIcon.setIconSize(18);
        Label title = new Label("Áreas y prefijos");
        title.getStyleClass().add("card-section-title");
        HBox titleRow = new HBox(8, titleIcon, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("El organigrama y el prefijo con el que se numeran los bienes nuevos de cada área "
            + "(p. ej. TICS/01). Un cambio de prefijo solo afecta a los bienes que se registren después; "
            + "el nombre de un área no se puede cambiar porque los bienes y resguardos lo guardan.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted-sm");

        table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.setPrefHeight(280);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<AreaCatalog.Entrada, String> colNombre = new TableColumn<>("Área");
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        colNombre.setPrefWidth(300);
        TableColumn<AreaCatalog.Entrada, String> colTipo = new TableColumn<>("Tipo");
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(GRUPO_LABELS[c.getValue().grupo().ordinal()]));
        colTipo.setPrefWidth(140);
        TableColumn<AreaCatalog.Entrada, String> colPadre = new TableColumn<>("Depende de");
        colPadre.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().padre() == null ? "—" : c.getValue().padre()));
        colPadre.setPrefWidth(240);
        TableColumn<AreaCatalog.Entrada, String> colPrefijo = new TableColumn<>("Prefijo");
        colPrefijo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().prefijo()));
        colPrefijo.setPrefWidth(90);
        table.getColumns().addAll(List.of(colNombre, colTipo, colPadre, colPrefijo));
        refrescar();

        boolean editable = !DatabaseConfig.isDemoMode() && !DatabaseConfig.isOfflineMode();
        Button btnNueva = new Button("Nueva área");
        btnNueva.getStyleClass().add("btn-primary");
        btnNueva.setGraphic(new FontIcon("mdi2p-plus"));
        btnNueva.setOnAction(e -> editar(null));
        Button btnEditar = new Button("Editar");
        btnEditar.getStyleClass().add("btn-secondary");
        btnEditar.setGraphic(new FontIcon("mdi2p-pencil-outline"));
        btnEditar.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        btnEditar.setOnAction(e -> editar(table.getSelectionModel().getSelectedItem()));
        table.setRowFactory(tv -> {
            TableRow<AreaCatalog.Entrada> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (editable && ev.getClickCount() == 2 && !row.isEmpty()) editar(row.getItem());
            });
            return row;
        });
        HBox actions = new HBox(8, btnNueva, btnEditar);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (!editable) {
            btnNueva.setDisable(true);
            btnEditar.disableProperty().unbind();
            btnEditar.setDisable(true);
            Label offline = new Label("Sin conexión a la base de datos: las áreas se muestran pero no se pueden editar.");
            offline.getStyleClass().add("muted-sm");
            actions.getChildren().add(offline);
        }

        card.getChildren().addAll(titleRow, hint, table, actions);
        rootVBox.getChildren().add(rootVBox.getChildren().indexOf(anchorSection), card);
        AnimationUtils.fadeInUp(card, 320, 280);
    }

    private void refrescar() {
        table.getItems().setAll(Areas.catalogo().entradas());
    }

    /** {@code actual} null = new area. */
    private void editar(AreaCatalog.Entrada actual) {
        Dialog<AreaCatalog.Entrada> dlg = new Dialog<>();
        dlg.setTitle(actual == null ? "Nueva área" : "Editar área");
        DialogUtil.applyStylesheet(dlg.getDialogPane());
        if (table.getScene() != null) dlg.initOwner(table.getScene().getWindow());

        TextField tfNombre = new TextField(actual == null ? "" : actual.nombre());
        tfNombre.setPromptText("Nombre oficial del área");
        tfNombre.setDisable(actual != null);
        ComboBox<AreaCatalog.Grupo> cbGrupo = new ComboBox<>();
        cbGrupo.getItems().addAll(AreaCatalog.Grupo.SECRETARIA, AreaCatalog.Grupo.DIRECCION, AreaCatalog.Grupo.AUTONOMO);
        if (actual != null && actual.grupo() == AreaCatalog.Grupo.PRESIDENCIA) {
            cbGrupo.getItems().setAll(AreaCatalog.Grupo.PRESIDENCIA);
        }
        cbGrupo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(AreaCatalog.Grupo g) { return g == null ? "" : GRUPO_LABELS[g.ordinal()]; }
            @Override public AreaCatalog.Grupo fromString(String s) { return null; }
        });
        cbGrupo.setValue(actual == null ? AreaCatalog.Grupo.DIRECCION : actual.grupo());
        ComboBox<String> cbPadre = new ComboBox<>();
        Areas.catalogo().entradas().stream()
            .filter(e -> e.grupo() == AreaCatalog.Grupo.PRESIDENCIA || e.grupo() == AreaCatalog.Grupo.SECRETARIA)
            .filter(e -> actual == null || !e.nombre().equals(actual.nombre()))
            .forEach(e -> cbPadre.getItems().add(e.nombre()));
        cbPadre.setValue(actual != null ? actual.padre() : null);
        cbPadre.setMaxWidth(Double.MAX_VALUE);
        cbPadre.disableProperty().bind(cbGrupo.valueProperty().isNotEqualTo(AreaCatalog.Grupo.DIRECCION));
        TextField tfPrefijo = new TextField(actual == null ? "" : actual.prefijo());
        tfPrefijo.setPromptText("Ej. TICS");
        tfPrefijo.textProperty().addListener((o, a, b) -> {
            if (b != null && !b.equals(b.toUpperCase())) tfPrefijo.setText(b.toUpperCase());
        });
        Label lblError = new Label();
        lblError.getStyleClass().add("field-hint-error");
        lblError.setWrapText(true);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16, 20, 8, 20));
        g.addRow(0, new Label("Nombre"), tfNombre);
        g.addRow(1, new Label("Tipo"), cbGrupo);
        g.addRow(2, new Label("Depende de"), cbPadre);
        g.addRow(3, new Label("Prefijo"), tfPrefijo);
        g.add(lblError, 0, 4, 2, 1);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints(320); c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);
        dlg.getDialogPane().setContent(g);

        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(guardar, ButtonType.CANCEL);
        Button okBtn = (Button) dlg.getDialogPane().lookupButton(guardar);
        // Validate against the whole organigrama before closing, so a duplicate
        // prefix or a dirección without parent is explained in the dialog.
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            AreaCatalog.Entrada e = leer(tfNombre, cbGrupo, cbPadre, tfPrefijo);
            try {
                if (e.nombre().isBlank()) throw new IllegalArgumentException("Escribe el nombre del área");
                if (actual == null && Areas.catalogo().buscar(e.nombre()).isPresent())
                    throw new IllegalArgumentException("Ya existe un área con ese nombre");
                Areas.catalogo().con(e);
            } catch (IllegalArgumentException ex) {
                lblError.setText(ex.getMessage());
                ev.consume();
            }
        });
        dlg.setResultConverter(bt -> bt == guardar ? leer(tfNombre, cbGrupo, cbPadre, tfPrefijo) : null);

        Optional<AreaCatalog.Entrada> res = dlg.showAndWait();
        res.ifPresent(e -> {
            javafx.scene.Scene scene = table.getScene();
            DialogUtil.runAsync(() -> { areaService.guardar(e); return null; },
                v -> { refrescar(); NotificacionUtil.exito(scene, "Área \"" + e.nombre() + "\" guardada"); },
                ex -> NotificacionUtil.error(scene, "No se pudo guardar el área: " + ex.getMessage()));
        });
    }

    private static AreaCatalog.Entrada leer(TextField nombre, ComboBox<AreaCatalog.Grupo> grupo,
                                            ComboBox<String> padre, TextField prefijo) {
        AreaCatalog.Grupo g = grupo.getValue();
        return new AreaCatalog.Entrada(nombre.getText().trim(), g,
            g == AreaCatalog.Grupo.DIRECCION ? padre.getValue() : null,
            prefijo.getText().trim().toUpperCase());
    }
}
