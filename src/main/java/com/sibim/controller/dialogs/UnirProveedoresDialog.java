package com.sibim.controller.dialogs;

import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.ProveedorRepetido;
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

/** Bienes › Unir proveedores: one proveedor written in two ways («… SA DE CV» and without
 *  it, a name missing its last surname) becomes one, so the lists, the autocomplete and
 *  «Copiar datos de la misma factura» see one proveedor. Each way can be left out; undone
 *  from the notice. Laid out like «Quitar títulos». */
public final class UnirProveedoresDialog {

    private final CalidadDatosService calidad = new CalidadDatosService();
    private final List<ProveedorRepetido> repetidos;
    private final Runnable alCambiar;
    private final Map<ProveedorRepetido, SimpleBooleanProperty> elegidos = new LinkedHashMap<>();
    private final Button aplicar = new Button();

    private UnirProveedoresDialog(List<ProveedorRepetido> repetidos, Runnable alCambiar) {
        this.repetidos = repetidos;
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run when the proveedores changed (or were put back) */
    public static void show(List<ProveedorRepetido> repetidos, Runnable alCambiar) {
        new UnirProveedoresDialog(repetidos, alCambiar).abrir();
    }

    private void abrir() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Unir proveedores");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(980, pantalla.getWidth() * 0.94), Math.min(560, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2s-store-outline", "Unir proveedores",
            "Un mismo proveedor escrito de dos formas queda escrito de una", AppColors.PRIMARY, AppColors.PRIMARY_D);

        repetidos.forEach(r -> elegidos.put(r, new SimpleBooleanProperty(true)));
        TableView<ProveedorRepetido> tabla = new TableView<>(FXCollections.observableArrayList(repetidos));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Cada proveedor está escrito de una sola forma"));

        TableColumn<ProveedorRepetido, Boolean> si = new TableColumn<>("Unir");
        si.setCellValueFactory(c -> elegidos.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(60);
        si.setMinWidth(60);
        si.setMaxWidth(70);
        TableColumn<ProveedorRepetido, String> antes = columna("Se escribe así", 330, ProveedorRepetido::variante);
        TableColumn<ProveedorRepetido, String> cuantos = columna("Bienes", 90, r -> String.valueOf(r.bienes()));
        cuantos.setMinWidth(90);
        TableColumn<ProveedorRepetido, String> queda = columna("Queda como", 380, ProveedorRepetido::queda);
        tabla.getColumns().addAll(List.of(si, antes, cuantos, queda));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label("Se queda la forma más completa del nombre. Cambia en todos los bienes que lo tienen escrito así. "
            + "Quita la marca si no es el mismo proveedor.");
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
        List<ProveedorRepetido> cuales = elegidos.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
        if (cuales.isEmpty()) return;
        Scene scene = dialog.getOwner() != null ? dialog.getOwner().getScene() : dialog.getDialogPane().getScene();
        aplicar.setDisable(true);
        DialogUtil.runAsync(() -> calidad.unirProveedores(cuales),
            hecho -> {
                dialog.close();
                if (alCambiar != null) alCambiar.run();
                int bienes = hecho.stream().mapToInt(u -> u.ids().size()).sum();
                NotificacionUtil.exitoConAccion(scene, "Proveedor unido en " + FormatUtils.plural(bienes, "bien", "bienes"),
                    "Deshacer", () -> DialogUtil.runAsync(() -> calidad.separarProveedores(hecho),
                        n -> { if (alCambiar != null) alCambiar.run(); NotificacionUtil.info(scene, "Los proveedores regresaron como estaban escritos"); },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + com.sibim.util.MensajeError.de(ex))));
            },
            ex -> {
                aplicar.setDisable(false);
                NotificacionUtil.error(dialog.getDialogPane().getScene(), "No se unieron: " + com.sibim.util.MensajeError.de(ex));
            });
    }

    private static TableColumn<ProveedorRepetido, String> columna(String titulo, double ancho,
                                                                  java.util.function.Function<ProveedorRepetido, String> valor) {
        TableColumn<ProveedorRepetido, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
