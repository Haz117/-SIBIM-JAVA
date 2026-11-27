package com.sibim.controller.dialogs;

import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A photo of the bien in its form, large, with the bien's serie, marca and resguardante under it
 * to read against what the photo says. On 24-nov-2026 five chalecos of Seguridad Pública had the
 * label of another chaleco as their photo (its serie was the one of another bien) and four more
 * were found on 26-nov among the 27 still with the photo of the load: the serie is read
 * on a label of a few centimetres, and the form only showed it at 110 × 80. A click on the photo
 * shows it at its real size, to read a small serie.
 */
final class FotoEnGrande {

    private FotoEnGrande() {}

    /** What is shown under the photo; any part may be empty. */
    record Pie(String titulo, String serie, String marcaModelo, String resguardante) {
        /** «Serie G999001 · SMITH & WESSON · JUAN PÉREZ»: empty without any. */
        String datos() {
            List<String> partes = new ArrayList<>();
            if (!vacio(serie)) partes.add("Serie " + serie.trim());
            if (!vacio(marcaModelo)) partes.add(marcaModelo.trim());
            if (!vacio(resguardante)) partes.add(resguardante.trim());
            return String.join("  ·  ", partes);
        }

        private static boolean vacio(String s) { return s == null || s.isBlank(); }
    }

    /** What was asked for the photo in view when the window closed. */
    enum Accion { NINGUNA, PRINCIPAL, QUITAR }

    record Resultado(int indice, Accion accion) {}

    /** @param fotos the URLs or local paths, in their order (the first is the principal)
     *  @param pie   null when the bien says nothing to compare */
    static Resultado show(Window owner, List<String> fotos, int desde, Pie pie) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner); else DialogUtil.applyOwner(dialog);
        dialog.setTitle("La foto en grande");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1100, pantalla.getWidth() * 0.92), Math.min(820, pantalla.getHeight() * 0.9));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2i-image-search-outline", "La foto en grande",
            "Compara lo que dice la foto (una etiqueta, una serie grabada, un nombre en la caja) con los datos del bien",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        int[] i = {Math.max(0, Math.min(desde, fotos.size() - 1))};
        Accion[] accion = {Accion.NINGUNA};
        boolean[] real = {false};

        ImageView vista = new ImageView();
        vista.setPreserveRatio(true);
        vista.setSmooth(true);
        Label cargando = new Label("Cargando la foto…");
        cargando.getStyleClass().add("muted-sm");
        StackPane ajustada = new StackPane() {
            // Takes what the window leaves: the photo follows the box, never the other way.
            @Override protected double computePrefHeight(double ancho) { return 300; }
            @Override protected double computeMinHeight(double ancho) { return 160; }
            @Override protected double computePrefWidth(double alto) { return 400; }
            @Override protected double computeMinWidth(double alto) { return 200; }
        };
        ajustada.getStyleClass().add("fdc-vista");
        ScrollPane tamanoReal = new ScrollPane();
        tamanoReal.setPannable(true);
        tamanoReal.getStyleClass().add("fdc-vista");
        StackPane caja = new StackPane(ajustada, tamanoReal, cargando);
        VBox.setVgrow(caja, Priority.ALWAYS);

        Label titulo = new Label(pie == null ? "" : Objects.requireNonNullElse(pie.titulo(), ""));
        titulo.getStyleClass().add("trf-paso-titulo");
        titulo.setWrapText(true);
        titulo.setMinHeight(Region.USE_PREF_SIZE);
        String datos = pie == null ? "" : pie.datos();
        Label comparar = new Label(datos.isEmpty() ? "Este bien no tiene serie, marca ni resguardante para comparar" : datos);
        comparar.getStyleClass().add(datos.isEmpty() ? "muted-sm" : "fdc-comparar");
        comparar.setGraphic(datos.isEmpty() ? null : new FontIcon("mdi2b-barcode"));
        comparar.setWrapText(true);
        comparar.setMinHeight(Region.USE_PREF_SIZE);

        Button anterior = new Button(null, new FontIcon("mdi2c-chevron-left"));
        anterior.getStyleClass().add("btn-secondary");
        anterior.setAccessibleText("Foto anterior");
        anterior.setTooltip(new Tooltip("Foto anterior (←)"));
        Button siguiente = new Button(null, new FontIcon("mdi2c-chevron-right"));
        siguiente.getStyleClass().add("btn-secondary");
        siguiente.setAccessibleText("Foto siguiente");
        siguiente.setTooltip(new Tooltip("Foto siguiente (→)"));
        Label cual = new Label();
        cual.getStyleClass().add("muted-sm");
        cual.setMinWidth(Region.USE_PREF_SIZE);
        Button verReal = new Button();
        verReal.getStyleClass().add("btn-secondary");
        verReal.setMinWidth(Region.USE_PREF_SIZE);
        Button principal = new Button("Usar como principal", new FontIcon("mdi2s-star-outline"));
        principal.getStyleClass().add("btn-secondary");
        principal.setMinWidth(Region.USE_PREF_SIZE);
        principal.setTooltip(new Tooltip("La principal es la que sale en la tabla y en los formatos"));
        Button quitar = new Button("No es de este bien: quitarla", new FontIcon("mdi2i-image-remove"));
        quitar.getStyleClass().add("btn-secondary");
        quitar.setMinWidth(Region.USE_PREF_SIZE);
        quitar.setTooltip(new Tooltip("Se quita al guardar el bien; «Cancelar» en el formulario la deja"));
        Region hueco = new Region();
        HBox.setHgrow(hueco, Priority.ALWAYS);
        HBox barra = new HBox(8, anterior, cual, siguiente, verReal, hueco, principal, quitar);
        barra.setAlignment(Pos.CENTER_LEFT);

        Runnable[] mostrar = new Runnable[1];
        cargando.setMouseTransparent(true);
        mostrar[0] = () -> {
            cargando.visibleProperty().unbind();
            String url = fotos.get(i[0]);
            Image img;
            try {
                img = new Image(com.sibim.util.ImageUtils.uri(url), true);
            } catch (RuntimeException e) {
                img = null;
            }
            vista.setImage(img);
            if (img == null || img.isError()) {
                cargando.setText("No se pudo abrir la foto");
                cargando.setVisible(true);
            } else {
                cargando.setText("Cargando la foto…");
                cargando.visibleProperty().bind(img.progressProperty().lessThan(1).and(img.errorProperty().not()));
                img.errorProperty().addListener((o, a, error) -> {
                    if (!error) return;
                    cargando.visibleProperty().unbind();
                    cargando.setText("No se pudo abrir la foto");
                    cargando.setVisible(true);
                });
            }
            vista.fitWidthProperty().unbind();
            vista.fitHeightProperty().unbind();
            if (real[0]) {
                vista.setFitWidth(0);
                vista.setFitHeight(0);
                tamanoReal.setContent(new StackPane(vista));
                ajustada.getChildren().clear();
            } else {
                tamanoReal.setContent(null);
                ajustada.getChildren().setAll(vista);
                vista.fitWidthProperty().bind(ajustada.widthProperty().subtract(16));
                vista.fitHeightProperty().bind(ajustada.heightProperty().subtract(16));
            }
            ajustada.setVisible(!real[0]);
            tamanoReal.setVisible(real[0]);
            verReal.setText(real[0] ? "Ajustar a la ventana" : "Tamaño real");
            verReal.setGraphic(new FontIcon(real[0] ? "mdi2m-magnify-minus-outline" : "mdi2m-magnify-plus-outline"));
            cual.setText("Foto " + (i[0] + 1) + " de " + fotos.size() + (i[0] == 0 ? " · principal" : ""));
            anterior.setDisable(i[0] == 0);
            siguiente.setDisable(i[0] == fotos.size() - 1);
            boolean varias = fotos.size() > 1;
            anterior.setVisible(varias); anterior.setManaged(varias);
            siguiente.setVisible(varias); siguiente.setManaged(varias);
            principal.setVisible(i[0] > 0); principal.setManaged(i[0] > 0);
        };
        Runnable alternarReal = () -> { real[0] = !real[0]; mostrar[0].run(); };
        anterior.setOnAction(e -> { if (i[0] > 0) { i[0]--; mostrar[0].run(); } });
        siguiente.setOnAction(e -> { if (i[0] < fotos.size() - 1) { i[0]++; mostrar[0].run(); } });
        verReal.setOnAction(e -> alternarReal.run());
        vista.setOnMouseClicked(e -> alternarReal.run());
        vista.setStyle("-fx-cursor: hand;");
        Tooltip.install(vista, new Tooltip("Clic: tamaño real / ajustar a la ventana"));
        principal.setOnAction(e -> { accion[0] = Accion.PRINCIPAL; dialog.close(); });
        quitar.setOnAction(e -> { accion[0] = Accion.QUITAR; dialog.close(); });
        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.LEFT && !anterior.isDisabled()) { anterior.fire(); e.consume(); }
            else if (e.getCode() == KeyCode.RIGHT && !siguiente.isDisabled()) { siguiente.fire(); e.consume(); }
        });

        VBox cuerpo = new VBox(10, caja, titulo, comparar, barra);
        cuerpo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox raiz = new VBox(12, header, cuerpo);
        dialog.getDialogPane().setContent(raiz);
        mostrar[0].run();
        dialog.showAndWait();
        return new Resultado(i[0], accion[0]);
    }
}
