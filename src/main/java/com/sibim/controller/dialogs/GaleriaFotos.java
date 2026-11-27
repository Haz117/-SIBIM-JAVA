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
 *  the formats show; a photo added goes first (since 1.0.38). {@link #fotos} is the live list: local paths for photos
 *  just picked, URLs for the ones already stored (ProductoDialogFactory
 *  #procesarArchivos uploads the former on saving). Shared by the full form
 *  and the áreas' "Actualizar datos". */
final class GaleriaFotos {

    final List<String> fotos;
    final VBox nodo;

    private final FlowPane galeria = new FlowPane(8, 8);
    private final Dialog<?> dialog;
    private final Runnable alCambiar;
    /** What the large view shows under the photo: read when it opens, so a serie just typed counts. */
    private java.util.function.Supplier<FotoEnGrande.Pie> pie = () -> null;

    /** @param pie the bien's número, serie, marca and resguardante, as the form has them now */
    void conPie(java.util.function.Supplier<FotoEnGrande.Pie> pie) {
        this.pie = pie != null ? pie : () -> null;
    }

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
            + "La que agregas queda como principal; usa \"Principal\" en otra foto para cambiarla.");
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
            // A click opens it large, with the serie beside it (26-nov-2026: photos of a label read
            // at 110 × 80 against a serie on another tab; 9 chalecos had another chaleco's label).
            iv.setOnMouseClicked(ev -> verEnGrande(i));
            iv.setStyle("-fx-cursor: hand;");
            Tooltip.install(iv, new Tooltip("Ver en grande, con la serie del bien"));
            com.sibim.util.AccessibilityUtils.asButton(iv, "Ver la foto " + (i + 1) + " en grande");
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

    private void verEnGrande(int desde) {
        if (desde < 0 || desde >= fotos.size()) return;
        javafx.stage.Window owner = dialog.getDialogPane().getScene() != null ? dialog.getDialogPane().getScene().getWindow() : null;
        FotoEnGrande.Resultado r = FotoEnGrande.show(owner, List.copyOf(fotos), desde, pie.get());
        aplicar(r);
    }

    /** What the large view asked for its photo: «Usar como principal» or «No es de este bien». */
    void aplicar(FotoEnGrande.Resultado r) {
        if (r == null || r.indice() < 0 || r.indice() >= fotos.size()) return;
        switch (r.accion()) {
            case PRINCIPAL -> { if (r.indice() > 0) { fotos.add(0, fotos.remove(r.indice())); cambio(); } }
            case QUITAR -> { fotos.remove(r.indice()); cambio(); }
            default -> { }
        }
    }

    private void agregar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar imagen del bien");
        java.io.File carpeta = com.sibim.util.CarpetaRecordada.FOTOS.ultima();
        if (carpeta != null) chooser.setInitialDirectory(carpeta);
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Imágenes",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        List<File> files = chooser.showOpenMultipleDialog(dialog.getOwner());
        if (files == null || files.isEmpty()) return;
        com.sibim.util.CarpetaRecordada.FOTOS.recordar(files.get(0));
        List<String> nuevas = new java.util.ArrayList<>();
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
            nuevas.add(file.getAbsolutePath());
        }
        // The new one first, as the principal: from 20 to 24-nov-2026, 112 of the 241 bienes that got a
        // photo had one already, and the one taken on the visit replaced it; added last it showed nowhere.
        fotos.addAll(0, nuevas);
        cambio();
    }
}
