package com.sibim.controller.dialogs;

import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.Repetido;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/** Bienes › Unir lo escrito de dos formas: a proveedor with and without «SA DE CV», a marca
 *  as «SMITH WESSON» and «SMITH & WESSON», a color as «NEBRO», a tipo as «MUELE» become one
 *  way, so the lists, the autocomplete and «Copiar datos de la misma factura» see one. Each
 *  way can be left out; undone from the notice. Laid out like «Quitar títulos». */
public final class UnirEscritosDialog {

    private final CalidadDatosService calidad = new CalidadDatosService();
    private final List<Repetido> repetidos;
    private final Runnable alCambiar;
    private final Map<Repetido, SimpleBooleanProperty> elegidos = new LinkedHashMap<>();
    private final Button aplicar = new Button();

    private UnirEscritosDialog(List<Repetido> repetidos, Runnable alCambiar) {
        this.repetidos = repetidos;
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run when something changed (or was put back) */
    public static void show(List<Repetido> repetidos, Runnable alCambiar) {
        new UnirEscritosDialog(repetidos, alCambiar).abrir();
    }

    private void abrir() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Unir lo escrito de dos formas");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1020, pantalla.getWidth() * 0.94), Math.min(580, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2s-spellcheck", "Unir lo escrito de dos formas",
            "Un mismo proveedor, marca, color o tipo escrito de dos formas queda escrito de una", AppColors.PRIMARY, AppColors.PRIMARY_D);

        repetidos.forEach(r -> elegidos.put(r, new SimpleBooleanProperty(true)));
        TableView<Repetido> tabla = new TableView<>(FXCollections.observableArrayList(repetidos));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Cada dato está escrito de una sola forma"));

        TableColumn<Repetido, Boolean> si = new TableColumn<>("Unir");
        si.setCellValueFactory(c -> elegidos.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(60);
        si.setMinWidth(60);
        si.setMaxWidth(70);
        TableColumn<Repetido, String> dato = columna("Dato", 110, r -> r.campo().etiqueta);
        dato.setMinWidth(100);
        TableColumn<Repetido, String> antes = columna("Se escribe así", 300, Repetido::variante);
        TableColumn<Repetido, String> cuantos = columna("Bienes", 90, r -> String.valueOf(r.bienes()));
        cuantos.setMinWidth(90);
        TableColumn<Repetido, String> queda = columna("Queda como", 340, Repetido::queda);
        tabla.getColumns().addAll(List.of(si, dato, antes, cuantos, queda));
        // Space ticks the row the keyboard is on, as in the other lists to pick from.
        tabla.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != javafx.scene.input.KeyCode.SPACE) return;
            Repetido r = tabla.getSelectionModel().getSelectedItem();
            if (r == null) return;
            elegidos.get(r).set(!elegidos.get(r).get());
            e.consume();
        });
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label("En el proveedor se queda la forma más completa del nombre; en la marca, el color y el tipo, la que "
            + "escriben más bienes, con sus acentos. Cambia en todos los bienes que lo tienen escrito así. "
            + "Quita la marca si no es lo mismo (dos marcas que se parecen, por ejemplo).");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        aplicar.setGraphic(new FontIcon("mdi2c-check"));
        aplicar.getStyleClass().add("btn-primary");
        aplicar.setMinWidth(Region.USE_PREF_SIZE);
        Runnable contar = () -> {
            long n = elegidos.values().stream().filter(SimpleBooleanProperty::get).count();
            aplicar.setText("Unir " + FormatUtils.plural(n, "forma", "formas"));
            aplicar.setDisable(n == 0);
        };
        elegidos.values().forEach(p -> p.addListener((o, a, b) -> contar.run()));
        contar.run();
        aplicar.setOnAction(e -> aplicar(dialog));

        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label total = new Label(FormatUtils.plural(repetidos.size(), "forma por unir", "formas por unir"));
        total.getStyleClass().add("table-count-label");
        HBox abajo = new HBox(10, total, hueco, aplicar);
        abajo.setAlignment(Pos.CENTER_LEFT);

        VBox cuerpo = new VBox(10, ayuda, tabla, abajo);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));
        dialog.showAndWait();
    }

    private void aplicar(Dialog<ButtonType> dialog) {
        List<Repetido> cuales = elegidos.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
        if (cuales.isEmpty()) return;
        Scene scene = dialog.getOwner() != null ? dialog.getOwner().getScene() : dialog.getDialogPane().getScene();
        aplicar.setDisable(true);
        DialogUtil.runAsync(() -> calidad.unirEscritos(cuales),
            hecho -> {
                dialog.close();
                if (alCambiar != null) alCambiar.run();
                int bienes = hecho.stream().mapToInt(u -> u.ids().size()).sum();
                NotificacionUtil.exitoConAccion(scene, "Escrito de una forma en " + FormatUtils.plural(bienes, "bien", "bienes"),
                    "Deshacer", () -> DialogUtil.runAsync(() -> calidad.separarEscritos(hecho),
                        n -> { if (alCambiar != null) alCambiar.run(); NotificacionUtil.info(scene, "Los datos regresaron como estaban escritos"); },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> {
                aplicar.setDisable(false);
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se unieron: " + com.sibim.util.MensajeError.de(ex));
            });
    }

    private static TableColumn<Repetido, String> columna(String titulo, double ancho,
                                                         java.util.function.Function<Repetido, String> valor) {
        TableColumn<Repetido, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
