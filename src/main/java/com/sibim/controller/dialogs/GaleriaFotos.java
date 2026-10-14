package com.sibim.controller.dialogs;

import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.ImageUtils;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.List;

/** The photos of a bien inside a form: thumbnails with × and "Principal",
 *  and "Agregar foto". The first one is the principal, the one the table and
 *  the formats show. {@link #fotos} is the live list: local paths for photos
 *  just picked, URLs for the ones already stored (ProductoDialogFactory
 *  #procesarArchivos uploads the former on saving). Shared by the full form
 *  and the áreas' "Actualizar datos". */
final class GaleriaFotos {

    final List<String> fotos;
    final VBox nodo;

    private final FlowPane galeria = new FlowPane(8, 8);
    private final Dialog<?> dialog;
    private final Runnable alCambiar;

    GaleriaFotos(List<String> fotos, Dialog<?> dialog, Runnable alCambiar) {
        this.fotos = fotos;
        this.dialog = dialog;
        this.alCambiar = alCambiar;
        galeria.setAlignment(Pos.CENTER_LEFT);
        redibujar();

        Button btnAgregarFoto = new Button("Agregar foto");
        btnAgregarFoto.setGraphic(new FontIcon("mdi2c-camera-plus-outline"));
        btnAgregarFoto.getStyleClass().add("btn-secondary");
        btnAgregarFoto.setOnAction(ev -> agregar());

        Label hint = new Label("La primera foto es la principal: la que sale en la tabla y en los formatos. "
            + "Usa \"Principal\" en otra foto para cambiarla.");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted-sm");

        nodo = new VBox(6, galeria, btnAgregarFoto, hint);
    }

    private void redibujar() {
        galeria.getChildren().clear();
        for (int idx = 0; idx < fotos.size(); idx++) {
            final int i = idx;
            final String url = fotos.get(i);
            ImageView iv = new ImageView();
            iv.setFitWidth(110); iv.setFitHeight(80);
            iv.setPreserveRatio(true);
            try {
                String imgUrl = com.sibim.util.SupabaseStorage.isRemoteUrl(url)
                    ? url
                    : java.nio.file.Path.of(url).toUri().toString();
                iv.setImage(new Image(imgUrl, 110, 80, true, true, true));
            } catch (Exception ex) {
                iv.setImage(null);
            }
            Button btnDel = new Button("×");
            btnDel.getStyleClass().add("btn-secondary");
            btnDel.setMinSize(20, 20); btnDel.setMaxSize(20, 20);
            btnDel.setAccessibleText("Quitar esta foto");
            btnDel.setTooltip(new Tooltip("Quitar esta foto"));
            btnDel.setOnAction(ev -> {
                fotos.remove(i);
                cambio();
            });
            StackPane cell = new StackPane(iv, btnDel);
            StackPane.setAlignment(btnDel, Pos.TOP_RIGHT);
            cell.getStyleClass().add("dlg-img-box");
            cell.setPrefSize(110, 80);
            if (i == 0) {
                Label badge = new Label("Principal");
                badge.getStyleClass().addAll("muted-sm");
                StackPane.setAlignment(badge, Pos.BOTTOM_LEFT);
                cell.getChildren().add(badge);
            } else {
                // A photo added later goes last, and the table and the formats
                // show the first one: without this the new photo never appeared.
                Button btnPrincipal = new Button("Principal");
                btnPrincipal.getStyleClass().add("btn-secondary");
                btnPrincipal.setTooltip(new Tooltip("Usar esta foto como la principal (la que sale en la tabla y en los formatos)"));
                btnPrincipal.setOnAction(ev -> {
                    fotos.add(0, fotos.remove(i));
                    cambio();
                });
                StackPane.setAlignment(btnPrincipal, Pos.BOTTOM_LEFT);
                cell.getChildren().add(btnPrincipal);
            }
            galeria.getChildren().add(cell);
        }
    }

    private void cambio() {
        redibujar();
        if (alCambiar != null) alCambiar.run();
    }

    private void agregar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen del bien");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Imágenes",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        List<File> files = chooser.showOpenMultipleDialog(dialog.getOwner());
        if (files == null) return;
        for (File file : files) {
            if (ImageUtils.exceedsMaxSize(file)) {
                Dialog<ButtonType> tooBig = DialogUtil.styledMessage(
                    "mdi2a-alert-circle-outline", "Imagen demasiado pesada",
                    "Elige un archivo más pequeño",
                    AppColors.WARNING, AppColors.WARNING_D,
                    "La imagen '" + file.getName() + "' pesa "
                    + (file.length() / (1024 * 1024)) + " MB — máximo "
                    + (ImageUtils.maxSourceBytes() / (1024 * 1024)) + " MB.",
                    dialog.getOwner());
                tooBig.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
                DialogUtil.styleButton(tooBig.getDialogPane(), ButtonType.OK, AppColors.WARNING);
                tooBig.showAndWait();
                continue;
            }
            fotos.add(file.getAbsolutePath());
        }
        cambio();
    }
}
