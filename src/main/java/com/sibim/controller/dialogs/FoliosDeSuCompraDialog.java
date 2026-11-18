package com.sibim.controller.dialogs;

import com.sibim.service.CalidadDatosService.FolioDeSuCompra;
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

/** Completar datos › Folio fiscal por revisar › «Corregir con el de su compra»: each folio as it
 *  is written, as it would be, and the bien it is taken from, to look over before saving; one
 *  that is not the same purchase is unticked. Space ticks the row the keyboard is on. Laid out
 *  like «Tomar de los del mismo nombre». */
public final class FoliosDeSuCompraDialog {

    private FoliosDeSuCompraDialog() {}

    /** @return the ticked ones, or empty if cancelled */
    public static Optional<List<FolioDeSuCompra>> show(List<FolioDeSuCompra> folios) {
        Map<FolioDeSuCompra, SimpleBooleanProperty> poner = new LinkedHashMap<>();
        folios.forEach(f -> poner.put(f, new SimpleBooleanProperty(true)));

        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Corregir con el folio de su compra");
        ButtonType ok = new ButtonType("Corregir", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ok, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1240, pantalla.getWidth() * 0.94), Math.min(560, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        DialogUtil.styleOkButton(dialog.getDialogPane(), AppColors.PRIMARY_D);
        HBox header = DialogUtil.gradientHeader("mdi2f-file-swap-outline", "Corregir con el folio de su compra",
            "Folios fiscales incompletos o con un carácter de más o de menos que otro bien tiene bien escritos",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        TableView<FolioDeSuCompra> tabla = new TableView<>(FXCollections.observableArrayList(folios));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<FolioDeSuCompra, Boolean> si = new TableColumn<>("Corregir");
        si.setCellValueFactory(c -> poner.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(96);
        si.setMinWidth(96);
        si.setMaxWidth(100);
        TableColumn<FolioDeSuCompra, String> numero = columna("Número", 96, f -> f.bien().getCodigo());
        numero.setMinWidth(90);
        TableColumn<FolioDeSuCompra, String> bien = columna("Bien", 200, f -> f.bien().getNombre());
        bien.setMinWidth(120);
        // A UUID is 36 characters (some 320 px): both folios are read whole, to see where they differ.
        TableColumn<FolioDeSuCompra, String> escrito = columna("Escrito así", 335, f -> f.bien().getFolioFiscal().trim());
        escrito.setMinWidth(335);
        TableColumn<FolioDeSuCompra, String> queda = columna("Queda así", 335, FolioDeSuCompra::folio);
        queda.setMinWidth(335);
        TableColumn<FolioDeSuCompra, String> de = columna("Como lo tiene", 200, f -> f.de().getCodigo() + " · " + f.de().getNombre());
        de.setMinWidth(120);
        tabla.getColumns().addAll(List.of(si, numero, bien, escrito, queda, de));
        tabla.setRowFactory(t -> {
            TableRow<FolioDeSuCompra> row = new TableRow<>();
            row.itemProperty().addListener((o, a, f) -> row.setTooltip(f == null ? null : new Tooltip(
                f.bien().getCodigo() + " · " + Objects.requireNonNullElse(f.bien().getNombre(), "")
                    + "\nEscrito así: " + f.bien().getFolioFiscal().trim() + "\nQueda así:   " + f.folio()
                    + "\nComo lo tiene " + f.de().getCodigo() + " · " + Objects.requireNonNullElse(f.de().getNombre(), ""))));
            return row;
        });
        tabla.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE) return;
            FolioDeSuCompra f = tabla.getSelectionModel().getSelectedItem();
            if (f == null) return;
            poner.get(f).set(!poner.get(f).get());
            e.consume();
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label("Solo el final del folio, cuando el bien es de la misma compra (proveedor, fecha o costo) que otro que lo tiene "
            + "entero; o el folio con uno o dos caracteres de más, de menos o cambiados, cuando solo un folio de otro bien se le parece así. "
            + "Quita la marca al que no sea de esa compra. Se deshace desde el aviso.");
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
            total.setText(FormatUtils.plural(n, "folio marcado", "folios marcados") + " de " + folios.size());
            boton.setText("Corregir " + FormatUtils.plural(n, "folio", "folios"));
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
        List<FolioDeSuCompra> elegidos = folios.stream().filter(f -> poner.get(f).get()).toList();
        return elegidos.isEmpty() ? Optional.empty() : Optional.of(elegidos);
    }

    private static TableColumn<FolioDeSuCompra, String> columna(String titulo, double ancho,
                                                                java.util.function.Function<FolioDeSuCompra, String> valor) {
        TableColumn<FolioDeSuCompra, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(Objects.requireNonNullElse(valor.apply(cd.getValue()), "")));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
