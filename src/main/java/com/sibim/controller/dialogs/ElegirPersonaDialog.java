package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.ResguardoService;
import com.sibim.service.ResguardoService.Resguardante;
import com.sibim.util.AppColors;
import com.sibim.util.ConfirmacionUtil;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Optional;

/** Bienes › Cambiar resguardante: who from the people already in the inventory, so a
 *  name is not typed a second way ("JUAN PEREZ" next to "PÉREZ JUAN"). Somebody who
 *  isn't there can still be typed, and is only offered when nobody matches. */
public final class ElegirPersonaDialog {

    private final List<Resguardante> personas;
    private final TextField buscar = new TextField();
    private final ListView<Resguardante> lista = new ListView<>();
    private final Button nueva = new Button();
    private final Label resumen = new Label();
    private final int cuantos;
    private Button asignar;
    /** The name typed as somebody new, when that was the choice. */
    private String nombreNuevo = null;

    private ElegirPersonaDialog(List<Producto> inventario, int cuantos) {
        this.personas = ResguardoService.porResguardante(List.of(), inventario);
        this.cuantos = cuantos;
    }

    /** @param inventario where the people come from
     *  @param cuantos    how many bienes get the person
     *  @return the name, spelled as the inventory has it; empty when cancelled */
    public static Optional<String> show(List<Producto> inventario, int cuantos) {
        return new ElegirPersonaDialog(inventario, cuantos).abrir();
    }

    private Optional<String> abrir() {
        Dialog<ButtonType> dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Cambiar resguardante");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(600, pantalla.getWidth() * 0.94), Math.min(600, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2b-badge-account-outline", "Cambiar resguardante",
            "Elige a la persona que queda a cargo de " + FormatUtils.plural(cuantos, "bien", "bienes"),
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        asignar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        asignar.setText("Asignar");
        asignar.getStyleClass().add("dialog-ok-btn");

        buscar.setId("buscarPersona");
        buscar.setPromptText("Nombre o área de la persona…");
        buscar.getStyleClass().add("form-input");
        buscar.textProperty().addListener((o, a, b) -> filtrar());
        buscar.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            int total = lista.getItems().size();
            int i = lista.getSelectionModel().getSelectedIndex();
            if (e.getCode() == KeyCode.DOWN && total > 0) {
                lista.getSelectionModel().select(Math.min(total - 1, i + 1));
                lista.scrollTo(Math.max(0, lista.getSelectionModel().getSelectedIndex() - 3));
                e.consume();
            } else if (e.getCode() == KeyCode.UP && total > 0) {
                lista.getSelectionModel().select(Math.max(0, i - 1));
                lista.scrollTo(Math.max(0, lista.getSelectionModel().getSelectedIndex() - 3));
                e.consume();
            }
        });

        lista.getStyleClass().add("trf-areas");
        lista.setCellFactory(lv -> new ResguardantePdfDialog.CeldaPersona(lv));
        lista.setPlaceholder(new Label("Nadie coincide con lo escrito"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> {
            if (p != null) nombreNuevo = null;
            actualizar();
        });
        VBox.setVgrow(lista, Priority.ALWAYS);

        nueva.setGraphic(new FontIcon("mdi2a-account-plus-outline"));
        nueva.getStyleClass().add("btn-secondary");
        nueva.setMaxWidth(Double.MAX_VALUE);
        nueva.setOnAction(e -> {
            nombreNuevo = buscar.getText().trim().replaceAll("\\s+", " ");
            actualizar();
        });

        resumen.getStyleClass().add("table-count-label");
        resumen.setWrapText(true);

        // A spelling that finds nobody would start a new person: say who it may be.
        List<String> nombres = personas.stream().map(Resguardante::nombre).toList();
        AvisoNombreParecido parecido = new AvisoNombreParecido(buscar, () -> nombres);
        VBox cuerpo = new VBox(8, buscar, parecido, lista, nueva, resumen);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, cuerpo));

        filtrar();
        Platform.runLater(buscar::requestFocus);
        Optional<ButtonType> r = dialog.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return Optional.empty();
        String elegido = elegido();
        return elegido == null ? Optional.empty() : Optional.of(confirmarNombre(elegido));
    }

    private void filtrar() {
        String q = buscar.getText() == null ? "" : buscar.getText().trim();
        nombreNuevo = null;
        List<Resguardante> hallados = DialogUtil.coincidencias(personas, Resguardante::texto, q);
        lista.getItems().setAll(hallados);
        if (!q.isEmpty() && !hallados.isEmpty()) {
            lista.getSelectionModel().select(0);
            lista.scrollTo(0);
        }
        boolean ofrecer = q.length() >= 3 && hallados.isEmpty();
        nueva.setVisible(ofrecer);
        nueva.setManaged(ofrecer);
        if (ofrecer) nueva.setText("Persona nueva: " + q.replaceAll("\\s+", " "));
        actualizar();
    }

    private String elegido() {
        if (nombreNuevo != null) return nombreNuevo;
        Resguardante p = lista.getSelectionModel().getSelectedItem();
        return p != null ? p.nombre() : null;
    }

    private void actualizar() {
        String quien = elegido();
        resumen.getStyleClass().remove("trf-seleccion-activa");
        if (quien != null) resumen.getStyleClass().add("trf-seleccion-activa");
        resumen.setText(quien == null ? "Elige a la persona en la lista"
            : FormatUtils.plural(cuantos, "bien pasa", "bienes pasan") + " a " + quien);
        asignar.setDisable(quien == null);
    }

    /** A new name with the same words as somebody in the list is probably that person. */
    private String confirmarNombre(String nombre) {
        if (nombreNuevo == null) return nombre;
        Optional<Resguardante> igual = personas.stream()
            .filter(p -> ResguardoService.mismasPalabras(p.nombre(), nombre)).findFirst();
        if (igual.isEmpty()) return nombre;
        return ConfirmacionUtil.confirmar("Nombre parecido",
            "Ya existe «" + igual.get().nombre() + "», con las mismas palabras.\n¿Es la misma persona? Se usará ese nombre.")
            ? igual.get().nombre() : nombre;
    }
}
