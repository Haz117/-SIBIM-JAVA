package com.sibim.controller.dialogs;

import com.sibim.service.CalidadDatosService.Dato;
import com.sibim.service.DelMismoNombre;
import com.sibim.service.DelMismoNombre.Propuesta;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.*;

/** Completar datos › «Tomar de los del mismo nombre»: each bien, what it would take and which
 *  bienes of its name say it, to look over before saving; one that is not the same purchase is
 *  unticked. Space ticks the row the keyboard is on. Laid out like «Tomar el color del nombre». */
public final class DelMismoNombreDialog {

    private DelMismoNombreDialog() {}

    /** @return the ticked ones, or empty if cancelled */
    public static Optional<List<Propuesta>> show(Dato dato, List<Propuesta> propuestas) {
        var ev = DelMismoNombre.comoSeGuarda(dato);
        String que = ev.etiqueta.toLowerCase(Locale.forLanguageTag("es-MX"));
        Map<Propuesta, SimpleBooleanProperty> poner = new LinkedHashMap<>();
        propuestas.forEach(p -> poner.put(p, new SimpleBooleanProperty(true)));

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Tomar de los del mismo nombre");
        ButtonType ok = new ButtonType("Poner", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1060, pantalla.getWidth() * 0.94), Math.min(600, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        HBox header = DialogUtil.gradientHeader("mdi2c-content-duplicate", "Tomar de los del mismo nombre",
            ev.etiqueta + " que ya tienen, todos igual, los demás bienes con el mismo nombre", AppColors.PRIMARY, AppColors.PRIMARY_D);

        TableView<Propuesta> tabla = new TableView<>(FXCollections.observableArrayList(propuestas));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Propuesta, Boolean> si = new TableColumn<>("Poner");
        si.setCellValueFactory(c -> poner.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(70);
        si.setMinWidth(70);
        si.setMaxWidth(80);
        TableColumn<Propuesta, String> numero = columna("Número", 100, p -> p.bien().getCodigo());
        numero.setMinWidth(90);
        TableColumn<Propuesta, String> bien = columna("Bien", 380, p -> p.bien().getNombre());
        TableColumn<Propuesta, String> area = columna("Área", 190, p -> p.bien().getArea());
        TableColumn<Propuesta, String> toma = columna(ev.etiqueta, 170, p -> ev.texto(p.valor()));
        TableColumn<Propuesta, String> de = columna("Lo dicen", 170, Propuesta::deTexto);
        tabla.getColumns().addAll(List.of(si, numero, bien, area, toma, de));
        tabla.setRowFactory(t -> {
            TableRow<Propuesta> row = new TableRow<>();
            row.itemProperty().addListener((o, a, p) -> row.setTooltip(p == null ? null : new Tooltip(
                Objects.requireNonNullElse(p.bien().getNombre(), "") + "\nLo dicen: " + String.join(", ", p.de()))));
            return row;
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            Propuesta p = tabla.getSelectionModel().getSelectedItem();
            if (p == null) return;
            poner.get(p).set(!poner.get(p).get());
            e.consume();
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label("Cada bien toma " + (ev == com.sibim.service.EditarVarios.Dato.FACTURA ? "la " : "el ") + que
            + " que tienen los demás bienes con su mismo nombre, que dicen todos lo mismo. Mismo nombre no siempre es la misma compra: "
            + "quita la marca a los que no sean. Se guarda bien por bien, como en el formulario, y se deshace desde el aviso.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);
        CheckBox todos = new CheckBox("Marcar todos");
        todos.setSelected(true);
        todos.setOnAction(e -> poner.values().forEach(p -> p.set(todos.isSelected())));
        HBox arriba = new HBox(10, new Region(), todos);
        HBox.setHgrow(arriba.getChildren().get(0), Priority.ALWAYS);
        arriba.setAlignment(Pos.CENTER_LEFT);

        Label total = new Label();
        total.getStyleClass().add("table-count-label");
        Button boton = (Button) dialog.getDialogPane().lookupButton(ok);
        Runnable contar = () -> {
            long n = poner.values().stream().filter(SimpleBooleanProperty::get).count();
            total.setText(FormatUtils.plural(n, "bien marcado", "bienes marcados") + " de " + propuestas.size());
            boton.setText("Poner a " + FormatUtils.plural(n, "bien", "bienes"));
            boton.setDisable(n == 0);
        };
        poner.values().forEach(p -> p.addListener((o, a, b) -> contar.run()));
        contar.run();

        VBox cuerpo = new VBox(10, ayuda, arriba, tabla, total);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ok) return Optional.empty();
        List<Propuesta> elegidas = propuestas.stream().filter(p -> poner.get(p).get()).toList();
        return elegidas.isEmpty() ? Optional.empty() : Optional.of(elegidas);
    }

    private static TableColumn<Propuesta, String> columna(String titulo, double ancho, java.util.function.Function<Propuesta, String> valor) {
        TableColumn<Propuesta, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(Objects.requireNonNullElse(valor.apply(cd.getValue()), "")));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
