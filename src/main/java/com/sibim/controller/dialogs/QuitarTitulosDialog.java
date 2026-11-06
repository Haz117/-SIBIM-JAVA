package com.sibim.controller.dialogs;

import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.ConTitulo;
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

/** Bienes › Quitar títulos: from 5-nov-2026 a person is written by name only, without
 *  "LIC.", "ING.", "ARQ."… The names that still have one, before and after, each one
 *  can be left out; applied at once on the bienes, the resguardos in force and the
 *  titulares, and undone from the notice. */
public final class QuitarTitulosDialog {

    private final CalidadDatosService calidad = new CalidadDatosService();
    private final List<ConTitulo> nombres;
    private final Runnable alCambiar;
    private final Map<ConTitulo, SimpleBooleanProperty> elegidos = new LinkedHashMap<>();
    private final Button aplicar = new Button();

    private QuitarTitulosDialog(List<ConTitulo> nombres, Runnable alCambiar) {
        this.nombres = nombres;
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run when the names changed (or were put back) */
    public static void show(List<ConTitulo> nombres, Runnable alCambiar) {
        new QuitarTitulosDialog(nombres, alCambiar).abrir();
    }

    private void abrir() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Quitar títulos de los nombres");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(980, pantalla.getWidth() * 0.94), Math.min(640, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2c-card-account-details-outline", "Quitar títulos de los nombres",
            "Solo queda el nombre: «LIC. ANA RUIZ» pasa a «ANA RUIZ»", AppColors.PRIMARY, AppColors.PRIMARY_D);

        nombres.forEach(n -> elegidos.put(n, new SimpleBooleanProperty(true)));
        TableView<ConTitulo> tabla = new TableView<>(FXCollections.observableArrayList(nombres));
        tabla.getStyleClass().add("data-table");
        tabla.setEditable(true);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tabla.setPlaceholder(new Label("Ningún nombre lleva título: ¡listo!"));

        TableColumn<ConTitulo, Boolean> si = new TableColumn<>("Quitar");
        si.setCellValueFactory(c -> elegidos.get(c.getValue()));
        si.setCellFactory(CheckBoxTableCell.forTableColumn(si));
        si.setEditable(true);
        si.setPrefWidth(70);
        si.setMinWidth(70);
        si.setMaxWidth(80);
        TableColumn<ConTitulo, String> antes = columna("Como está", 260, ConTitulo::antes);
        TableColumn<ConTitulo, String> despues = columna("Queda", 240, ConTitulo::despues);
        TableColumn<ConTitulo, String> donde = columna("Dónde", 300, ConTitulo::donde);
        tabla.getColumns().addAll(List.of(si, antes, despues, donde));
        VBox.setVgrow(tabla, Priority.ALWAYS);

        Label ayuda = new Label("Cambia en los bienes activos, los resguardos vigentes y los titulares de área. "
            + "Los resguardos cancelados y los bienes dados de baja se quedan como se firmaron. "
            + "Quita la marca de uno si lo de adelante no es un título.");
        ayuda.getStyleClass().add("muted-sm");
        ayuda.setWrapText(true);
        ayuda.setMinHeight(Region.USE_PREF_SIZE);

        aplicar.setGraphic(new FontIcon("mdi2c-check"));
        aplicar.getStyleClass().add("btn-primary");
        aplicar.setMinWidth(Region.USE_PREF_SIZE);
        Runnable contar = () -> {
            long n = elegidos.values().stream().filter(SimpleBooleanProperty::get).count();
            aplicar.setText("Quitar títulos a " + FormatUtils.plural(n, "nombre", "nombres"));
            aplicar.setDisable(n == 0);
        };
        elegidos.values().forEach(p -> p.addListener((o, a, b) -> contar.run()));
        contar.run();
        aplicar.setOnAction(e -> aplicar(dialog));

        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        Label total = new Label(FormatUtils.plural(nombres.size(), "nombre con título", "nombres con título"));
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
        List<ConTitulo> cuales = elegidos.entrySet().stream().filter(e -> e.getValue().get()).map(Map.Entry::getKey).toList();
        if (cuales.isEmpty()) return;
        Scene scene = dialog.getOwner() != null ? dialog.getOwner().getScene() : dialog.getDialogPane().getScene();
        aplicar.setDisable(true);
        DialogUtil.runAsync(() -> calidad.quitarTitulos(cuales),
            hecho -> {
                dialog.close();
                if (alCambiar != null) alCambiar.run();
                NotificacionUtil.exitoConAccion(scene, "Títulos quitados de " + FormatUtils.plural(hecho.size(), "nombre", "nombres"),
                    "Deshacer", () -> DialogUtil.runAsync(() -> calidad.ponerTitulos(hecho),
                        () -> { if (alCambiar != null) alCambiar.run(); NotificacionUtil.info(scene, "Regresaron los nombres como estaban"); },
                        ex -> NotificacionUtil.error(scene, "No se pudo deshacer: " + ex.getMessage())));
            },
            ex -> {
                aplicar.setDisable(false);
                NotificacionUtil.error(dialog.getDialogPane().getScene(),
                    "No se quitaron los títulos: " + (ex.getMessage() != null ? ex.getMessage() : "error desconocido"));
            });
    }

    private static TableColumn<ConTitulo, String> columna(String titulo, double ancho,
                                                          java.util.function.Function<ConTitulo, String> valor) {
        TableColumn<ConTitulo, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(cd -> new SimpleStringProperty(valor.apply(cd.getValue())));
        c.setPrefWidth(ancho);
        c.setEditable(false);
        return c;
    }
}
