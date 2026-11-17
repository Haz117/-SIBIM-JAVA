package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.Collectors;

/** Completar datos › Color › «Tomar el color del nombre»: the color each bien's name already
 *  says, to look over before it is saved. A wrong one is unticked or written over; Space ticks
 *  the row the keyboard is on. Laid out like «Unir proveedores». */
public final class ColoresDelNombreDialog {

    /** One bien, whether it takes the color and which. */
    static final class Renglon {
        final Producto bien;
        final SimpleBooleanProperty poner = new SimpleBooleanProperty(true);
        final SimpleStringProperty color;
        Renglon(Producto bien, String color) { this.bien = bien; this.color = new SimpleStringProperty(color); }
    }

    private static final String TODOS = "Todos los colores";

    private ColoresDelNombreDialog() {}

    /** @return the bienes ticked with their color (as written over), or empty if cancelled */
    public static Optional<Map<Producto, String>> show(Map<Producto, String> propuestos) {
        List<Renglon> renglones = propuestos.entrySet().stream()
            .map(e -> new Renglon(e.getKey(), e.getValue()))
            .sorted(Comparator.comparing((Renglon r) -> r.color.get())
                .thenComparing(r -> Objects.requireNonNullElse(r.bien.getCodigo(), ""), com.sibim.util.OrdenCodigo.POR_CODIGO))
            .collect(Collectors.toCollection(ArrayList::new));

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Tomar el color del nombre");
        ButtonType poner = new ButtonType("Poner el color", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(poner, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(980, pantalla.getWidth() * 0.94), Math.min(600, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        HBox header = DialogUtil.gradientHeader("mdi2p-palette-outline", "Tomar el color del nombre",
            "El color que ya viene escrito en el nombre o la descripción de cada bien", AppColors.PRIMARY, AppColors.PRIMARY_D);

        ObservableList<Renglon> todos = FXCollections.observableArrayList(renglones);
        FilteredList<Renglon> visibles = new FilteredList<>(todos, r -> true);
        TableView<Renglon> tabla = new TableView<>(visibles);
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Renglon, Boolean> si = new TableColumn<>("Poner");
        si.setCellValueFactory(c -> c.getValue().poner);
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(70);
        si.setMinWidth(70);
        si.setMaxWidth(80);
        TableColumn<Renglon, String> numero = new TableColumn<>("Número");
        numero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().bien.getCodigo()));
        numero.setPrefWidth(110);
        numero.setMinWidth(90);
        numero.setEditable(false);
        TableColumn<Renglon, String> bien = new TableColumn<>("Bien (de donde sale el color)");
        bien.setCellValueFactory(c -> new SimpleStringProperty(Objects.requireNonNullElse(c.getValue().bien.getNombre(), "")));
        bien.setPrefWidth(500);
        bien.setEditable(false);
        TableColumn<Renglon, String> color = new TableColumn<>("Color");
        color.setCellValueFactory(c -> c.getValue().color);
        color.setCellFactory(TextFieldTableCell.forTableColumn());
        color.setOnEditCommit(e -> {
            String nuevo = e.getNewValue() == null ? "" : e.getNewValue().trim().replaceAll("\\s+", " ").toUpperCase(Locale.forLanguageTag("es-MX"));
            e.getRowValue().color.set(nuevo);
            if (nuevo.isEmpty()) e.getRowValue().poner.set(false);
        });
        color.setPrefWidth(240);
        color.setEditable(true);
        tabla.getColumns().addAll(List.of(si, numero, bien, color));
        // The name whole, also when it is longer than the column.
        tabla.setRowFactory(t -> {
            TableRow<Renglon> row = new TableRow<>();
            row.itemProperty().addListener((o, a, r) -> row.setTooltip(r == null ? null : new Tooltip(
                Objects.requireNonNullElse(r.bien.getNombre(), "") + (r.bien.getDescripcion() == null || r.bien.getDescripcion().isBlank()
                    ? "" : "\n" + r.bien.getDescripcion()))));
            return row;
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE || tabla.getEditingCell() != null) return;
            Renglon r = tabla.getSelectionModel().getSelectedItem();
            if (r == null) return;
            r.poner.set(!r.poner.get());
            e.consume();
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        // To look over one color at a time: «NEGRO (245)», «BLANCO (100)»…
        Map<String, Long> porColor = renglones.stream().collect(Collectors.groupingBy(r -> r.color.get(), LinkedHashMap::new, Collectors.counting()));
        ComboBox<String> filtro = new ComboBox<>();
        filtro.getItems().add(TODOS);
        porColor.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> filtro.getItems().add(e.getKey() + " (" + e.getValue() + ")"));
        filtro.setValue(TODOS);
        filtro.setAccessibleText("Ver un solo color");
        filtro.valueProperty().addListener((o, a, b) -> {
            String elegido = b == null || TODOS.equals(b) ? null : b.substring(0, b.lastIndexOf(" ("));
            visibles.setPredicate(r -> elegido == null || elegido.equals(r.color.get()));
        });
        CheckBox todosLosVisibles = new CheckBox("Marcar los que se ven");
        todosLosVisibles.setSelected(true);
        todosLosVisibles.setOnAction(e -> visibles.forEach(r -> r.poner.set(todosLosVisibles.isSelected() && !r.color.get().isBlank())));

        Label ayuda = new Label("Revisa la lista: quita la marca a un bien si el color del nombre no es el suyo (por ejemplo, el de una pieza "
            + "o el de la luz de una cámara), o cámbialo con doble clic. Solo se pone a los bienes que siguen sin color.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        Label total = new Label();
        total.getStyleClass().add("table-count-label");
        Button ok = (Button) dialog.getDialogPane().lookupButton(poner);
        Runnable contar = () -> {
            long n = todos.stream().filter(r -> r.poner.get() && !r.color.get().isBlank()).count();
            total.setText(FormatUtils.plural(n, "bien marcado", "bienes marcados") + " de " + todos.size());
            ok.setText("Poner el color a " + FormatUtils.plural(n, "bien", "bienes"));
            ok.setDisable(n == 0);
        };
        todos.forEach(r -> { r.poner.addListener((o, a, b) -> contar.run()); r.color.addListener((o, a, b) -> contar.run()); });
        contar.run();

        Label ver = new Label("Ver:");
        HBox arriba = new HBox(10, ver, filtro, new Region(), todosLosVisibles);
        HBox.setHgrow(arriba.getChildren().get(2), Priority.ALWAYS);
        arriba.setAlignment(Pos.CENTER_LEFT);
        HBox abajo = new HBox(10, total);
        abajo.setAlignment(Pos.CENTER_LEFT);

        VBox cuerpo = new VBox(10, ayuda, arriba, tabla, abajo);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != poner) return Optional.empty();
        Map<Producto, String> elegidos = new LinkedHashMap<>();
        for (Renglon x : todos) if (x.poner.get() && !x.color.get().isBlank()) elegidos.put(x.bien, x.color.get());
        return elegidos.isEmpty() ? Optional.empty() : Optional.of(elegidos);
    }
}
