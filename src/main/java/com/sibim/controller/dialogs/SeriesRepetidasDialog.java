package com.sibim.controller.dialogs;

import com.sibim.model.Producto;
import com.sibim.service.CalidadDatosService;
import com.sibim.service.CalidadDatosService.SerieRepetida;
import com.sibim.service.ProductoService;
import com.sibim.util.AppColors;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.*;

/** Bienes › Series repetidas: a serie identifies one bien, so one on several is a
 *  capture error, or something else typed in that column (a factura, "S/N"), or two
 *  records of one bien. Which of them it is takes looking at the bien: here each can be
 *  corrected or left without serie. */
public final class SeriesRepetidasDialog {

    private final ProductoService service = new ProductoService();
    private final List<Producto> bienes;
    private final Runnable alCambiar;
    private final ObservableList<SerieRepetida> grupos = FXCollections.observableArrayList();
    private final ListView<SerieRepetida> lista = new ListView<>(grupos);
    private final VBox filas = new VBox(8);
    private final ScrollPane scroll = new ScrollPane(filas);
    private final VBox vacio = new VBox(8);
    private final Label aviso = new Label();
    private final Label cuantos = new Label();
    private Dialog<ButtonType> dialog;
    private boolean cambio = false;

    private SeriesRepetidasDialog(List<Producto> bienes, Runnable alCambiar) {
        this.bienes = new ArrayList<>(bienes);
        this.alCambiar = alCambiar;
    }

    /** @param alCambiar run on closing when something was corrected */
    public static void show(List<Producto> bienes, Runnable alCambiar) {
        new SeriesRepetidasDialog(bienes, alCambiar).abrir();
    }

    private void abrir() {
        dialog = new Dialog<>();
        DialogUtil.applyOwner(dialog);
        dialog.setTitle("Series repetidas");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.geometry.Rectangle2D pantalla = javafx.stage.Screen.getPrimary().getVisualBounds();
        dialog.getDialogPane().setPrefSize(Math.min(1100, pantalla.getWidth() * 0.94), Math.min(680, pantalla.getHeight() * 0.88));
        dialog.setResizable(true);
        DialogUtil.applyStylesheet(dialog.getDialogPane());
        HBox header = DialogUtil.gradientHeader("mdi2b-barcode-scan", "Series repetidas",
            "Una serie es de un solo bien: corrige la que esté mal o déjalo sin serie",
            AppColors.PRIMARY, AppColors.PRIMARY_D);

        lista.getStyleClass().add("trf-areas");
        lista.setCellFactory(lv -> new CeldaSerie(lv));
        lista.setPlaceholder(new Label("Ya no hay series repetidas"));
        lista.getSelectionModel().selectedItemProperty().addListener((o, a, g) -> mostrar(g));
        VBox.setVgrow(lista, Priority.ALWAYS);
        cuantos.getStyleClass().add("table-count-label");
        VBox izquierda = new VBox(8, TransferenciaArrastreDialog.paso(1, "Elige una serie"), lista, cuantos);
        izquierda.getStyleClass().add("trf-panel");
        izquierda.setPrefWidth(340);
        izquierda.setMinWidth(270);

        FontIcon icono = new FontIcon("mdi2b-barcode-scan");
        icono.getStyleClass().add("empty-icon-lg");
        Label titulo = new Label("Elige una serie de la lista");
        titulo.getStyleClass().add("empty-state-msg");
        vacio.getChildren().setAll(icono, titulo);
        vacio.setAlignment(Pos.CENTER);
        VBox.setVgrow(vacio, Priority.ALWAYS);

        filas.setPadding(new Insets(2, 4, 4, 2));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        aviso.getStyleClass().addAll("field-hint", "field-hint-warn");
        aviso.setWrapText(true);

        HBox paso2 = TransferenciaArrastreDialog.paso(2, "Corrige cada bien");
        paso2.setPadding(Insets.EMPTY);
        VBox derecha = new VBox(8, paso2, aviso, vacio, scroll);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        HBox cuerpo = new HBox(16, izquierda, derecha);
        VBox.setVgrow(cuerpo, Priority.ALWAYS);
        VBox debajo = new VBox(cuerpo);
        debajo.setPadding(new Insets(0, 22, 14, 22));
        VBox.setVgrow(debajo, Priority.ALWAYS);
        dialog.getDialogPane().setContent(new VBox(12, header, debajo));

        dialog.setOnHidden(e -> { if (cambio && alCambiar != null) alCambiar.run(); });
        recalcular(null);
        Platform.runLater(() -> {
            if (!grupos.isEmpty()) lista.getSelectionModel().select(0);
            lista.requestFocus();
        });
        dialog.showAndWait();
    }

    /** @param serie what to stay on, if it is still repeated */
    private void recalcular(String serie) {
        List<SerieRepetida> nuevos = CalidadDatosService.seriesRepetidas(bienes);
        int antes = lista.getSelectionModel().getSelectedIndex();
        grupos.setAll(nuevos);
        cuantos.setText(nuevos.isEmpty() ? "Nada por revisar"
            : FormatUtils.plural(nuevos.size(), "serie por revisar", "series por revisar"));
        if (nuevos.isEmpty()) { mostrar(null); return; }
        Optional<SerieRepetida> misma = nuevos.stream().filter(g -> g.serie().equals(serie)).findFirst();
        if (misma.isPresent()) lista.getSelectionModel().select(misma.get());
        else if (serie != null) lista.getSelectionModel().select(Math.min(Math.max(antes, 0), nuevos.size() - 1));
        else mostrar(null);
    }

    private void mostrar(SerieRepetida g) {
        filas.getChildren().clear();
        ver(vacio, g == null);
        ver(scroll, g != null);
        ver(aviso, false);
        if (g == null) return;
        if (g.noEsSerie()) {
            aviso.setText("«" + g.serie() + "» no parece una serie: lo escrito indica que el bien no tiene. Quítala de cada uno.");
            ver(aviso, true);
        } else if (g.areas() > 1) {
            aviso.setText("Está en " + g.areas() + " áreas distintas: podrían ser dos registros del mismo bien, revísalos en físico.");
            ver(aviso, true);
        }
        for (Producto p : g.bienes()) filas.getChildren().add(fila(p, g.serie()));
    }

    private Node fila(Producto p, String serie) {
        Label codigo = new Label(p.getCodigo());
        codigo.getStyleClass().add("trf-codigo");
        codigo.setMinWidth(Region.USE_PREF_SIZE);
        Label nombre = new Label(p.getNombre());
        nombre.getStyleClass().add("org-child-label");
        nombre.setTextOverrun(OverrunStyle.ELLIPSIS);
        nombre.setMinWidth(0);
        nombre.setTooltip(new Tooltip(p.getNombre()));
        HBox linea = new HBox(8, codigo, nombre);
        linea.setAlignment(Pos.CENTER_LEFT);
        Label detalle = new Label((p.getResguardante() != null && !p.getResguardante().isBlank() ? p.getResguardante() : "Sin resguardante")
            + (p.getArea() != null ? " · " + p.getArea() : ""));
        detalle.getStyleClass().add("org-table-sub");
        detalle.setTextOverrun(OverrunStyle.ELLIPSIS);
        detalle.setMinWidth(0);
        VBox textos = new VBox(3, linea, detalle);
        textos.setMinWidth(0);
        textos.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(textos, Priority.ALWAYS);

        TextField campo = new TextField(p.getNumeroSerie());
        campo.setPromptText("Serie correcta");
        campo.getStyleClass().add("form-input");
        campo.setPrefWidth(190);
        campo.setMinWidth(190);
        Button quitar = new Button("Sin serie");
        quitar.setGraphic(new FontIcon("mdi2c-close"));
        quitar.getStyleClass().add("btn-secondary");
        quitar.setMinWidth(Region.USE_PREF_SIZE);
        quitar.setTooltip(new Tooltip("Deja este bien sin número de serie"));
        Label error = new Label();
        error.getStyleClass().addAll("cell-badge", "cell-badge-danger");
        error.setMinWidth(Region.USE_PREF_SIZE);
        ver(error, false);

        Runnable corregir = () -> {
            String nueva = campo.getText() == null ? "" : campo.getText().trim();
            if (nueva.equalsIgnoreCase(p.getNumeroSerie() == null ? "" : p.getNumeroSerie().trim())) return;
            guardar(p, nueva.isEmpty() ? null : nueva, serie, error);
        };
        campo.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER) { corregir.run(); e.consume(); } });
        campo.focusedProperty().addListener((o, a, si) -> { if (!si) corregir.run(); });
        quitar.setOnAction(e -> guardar(p, null, serie, error));

        HBox f = new HBox(10, textos, campo, quitar, error);
        f.setAlignment(Pos.CENTER_LEFT);
        f.getStyleClass().add("cnt-fila");
        f.setStyle("-fx-cursor: default;");
        return f;
    }

    private void guardar(Producto p, String nueva, String serie, Label error) {
        String anterior = p.getNumeroSerie();
        p.setNumeroSerie(nueva);
        DialogUtil.runAsync(() -> service.save(p),
            guardado -> {
                int i = bienes.indexOf(p);
                if (i >= 0) bienes.set(i, guardado);
                cambio = true;
                recalcular(serie);
            },
            e -> {
                p.setNumeroSerie(anterior);
                error.setText("No se guardó");
                error.setTooltip(new Tooltip(e.getMessage()));
                ver(error, true);
            });
    }

    private static void ver(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    private static final class CeldaSerie extends ListCell<SerieRepetida> {
        private final Label serie = new Label();
        private final Label areas = new Label();
        private final Label cuenta = new Label();
        private final HBox caja;

        CeldaSerie(ListView<SerieRepetida> lv) {
            serie.getStyleClass().add("org-child-label");
            serie.setTextOverrun(OverrunStyle.ELLIPSIS);
            areas.getStyleClass().add("org-table-sub");
            VBox textos = new VBox(1, serie, areas);
            textos.setMinWidth(0);
            textos.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(textos, Priority.ALWAYS);
            cuenta.getStyleClass().add("org-area-count");
            cuenta.setMinWidth(Region.USE_PREF_SIZE);
            caja = new HBox(10, textos, cuenta);
            caja.setAlignment(Pos.CENTER_LEFT);
            caja.maxWidthProperty().bind(lv.widthProperty().subtract(30));
            caja.setMinWidth(0);
        }

        @Override protected void updateItem(SerieRepetida g, boolean empty) {
            super.updateItem(g, empty);
            setText(null);
            if (empty || g == null) { setGraphic(null); return; }
            serie.setText(g.serie());
            serie.setTooltip(new Tooltip(g.serie()));
            areas.setText(g.noEsSerie() ? "No parece una serie" : g.areas() > 1 ? "En " + g.areas() + " áreas" : "En la misma área");
            cuenta.setText(FormatUtils.plural(g.bienes().size(), "bien", "bienes"));
            setGraphic(caja);
        }
    }
}
