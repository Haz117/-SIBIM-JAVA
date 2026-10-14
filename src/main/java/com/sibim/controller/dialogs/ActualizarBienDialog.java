package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.util.AppColors;
import com.sibim.util.AutocompleteUtil;
import com.sibim.util.DialogUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What an área keeps current about a bien — ubicación, estado físico,
 * descripción and photos — in one short form. The full one shows the other
 * twenty-odd fields disabled ("solo Patrimonio"), and ProductoService#save
 * keeps them as the database has them for anyone but Patrimonio anyway.
 */
public final class ActualizarBienDialog {

    /** The same values the full form offers (ProductoTabPatrimonioFields). */
    static final List<String> ESTADOS_FISICOS = List.of("", "BUENO", "REGULAR", "MALO", "DEFICIENTE");

    private ActualizarBienDialog() {}

    /**
     * @param fotos       the photos the bien has now (URLs)
     * @param ubicaciones suggestions for the ubicación field
     * @return a copy of {@code bien} with the four fields as captured, or empty if cancelled
     */
    public static Optional<Producto> show(Producto bien, List<String> fotos, List<String> ubicaciones) {
        Dialog<Producto> dialog = DialogUtil.create(600);
        dialog.setTitle("Actualizar datos del bien");
        DialogPane pane = dialog.getDialogPane();
        String codigo = bien.getCodigo() != null && !bien.getCodigo().isBlank() ? "  ·  " + bien.getCodigo() : "";
        pane.setHeader(DialogUtil.gradientHeader("mdi2p-pencil-outline", "Actualizar datos",
            bien.getNombre() + codigo, AppColors.PRIMARY_D, AppColors.INDIGO));

        TextField fUbicacion = new TextField(bien.getUbicacion() != null ? bien.getUbicacion() : "");
        fUbicacion.setPromptText("Dónde está el bien (edificio, oficina, piso…)");
        fUbicacion.getStyleClass().add("form-input");
        AutocompleteUtil.attach(fUbicacion, ubicaciones != null ? ubicaciones : List.of());

        ComboBox<String> fEstado = new ComboBox<>(FXCollections.observableArrayList(ESTADOS_FISICOS));
        fEstado.setValue(bien.getEstadoFisico() != null && ESTADOS_FISICOS.contains(bien.getEstadoFisico())
            ? bien.getEstadoFisico() : "");
        fEstado.setMaxWidth(Double.MAX_VALUE);
        fEstado.getStyleClass().add("form-input");
        fEstado.setAccessibleText("Estado físico");

        TextArea fDesc = new TextArea(bien.getDescripcion() != null ? bien.getDescripcion() : "");
        fDesc.setPromptText("Descripción, señas particulares, accesorios…");
        fDesc.setPrefRowCount(3);
        fDesc.setWrapText(true);
        fDesc.getStyleClass().add("form-input");

        List<String> fotosIniciales = new ArrayList<>(fotos != null ? fotos : List.of());
        if (fotosIniciales.isEmpty() && bien.getFotoUrl() != null && !bien.getFotoUrl().isBlank())
            fotosIniciales.add(bien.getFotoUrl());
        GaleriaFotos galeria = new GaleriaFotos(fotosIniciales, dialog, null);

        var grid = DialogUtil.formGrid(120);
        int r = 0;
        grid.add(DialogUtil.fieldLabel("Ubicación"),     0, r); grid.add(fUbicacion, 1, r++);
        grid.add(DialogUtil.fieldLabel("Estado físico"), 0, r); grid.add(fEstado,    1, r++);
        grid.add(DialogUtil.fieldLabel("Descripción"),   0, r); grid.add(fDesc,      1, r++);
        grid.add(DialogUtil.fieldLabel("Fotos"),         0, r); grid.add(galeria.nodo, 1, r++);

        Label nota = new Label("El código, los valores, el resguardante y los demás datos los actualiza "
            + "Recursos Materiales y Patrimonio.");
        nota.setWrapText(true);
        nota.getStyleClass().add("muted-sm");
        nota.setPadding(new javafx.geometry.Insets(0, 22, 14, 22));

        DialogUtil.setScrollableContent(pane, new VBox(grid, nota));
        Button ok = (Button) pane.lookupButton(ButtonType.OK);
        ok.setText("Guardar");
        ok.getStyleClass().add("dialog-ok-btn");

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            Producto p = bien.copia();
            p.setUbicacion(fUbicacion.getText().trim());
            String estado = fEstado.getValue();
            p.setEstadoFisico(estado == null || estado.isBlank() ? null : estado);
            p.setDescripcion(fDesc.getText().trim());
            p.setFotosUrls(new ArrayList<>(galeria.fotos));
            return p;
        });
        Platform.runLater(fUbicacion::requestFocus);
        return dialog.showAndWait();
    }
}
