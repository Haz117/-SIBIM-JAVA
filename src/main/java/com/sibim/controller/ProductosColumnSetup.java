package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import com.sibim.util.NotificacionUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Static column-setup helpers extracted from ProductosController.
 *  Package-private — only used by ProductosController. */
class ProductosColumnSetup {

    private ProductosColumnSetup() {}

    static void configureFoto(TableColumn<Producto, String> col,
                              TableView<Producto> table,
                              Map<String, Image> thumbnailCache,
                              Logger log) {
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getFotoUrl()));
        col.setCellFactory(column -> new TableCell<>() {
            private final ImageView iv = new ImageView();
            private final FontIcon lbl = new FontIcon("mdi2c-camera-outline");
            private final StackPane box;
            {
                iv.setFitWidth(38); iv.setFitHeight(38); iv.setPreserveRatio(true);
                lbl.setIconSize(16);
                lbl.getStyleClass().add("foto-placeholder");
                box = new StackPane(lbl, iv);
                box.setPrefSize(40, 40); box.setMinSize(40, 40); box.setMaxSize(40, 40);
                box.getStyleClass().add("foto-cell-box");
                box.setOnMouseClicked(e -> {
                    if (!iv.isVisible()) return;
                    Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                    DialogUtil.showPhotoViewer(p != null ? p.getFotoUrl() : null, p != null ? p.getNombre() : null);
                });
            }
            @Override
            protected void updateItem(String url, boolean empty) {
                super.updateItem(url, empty);
                setGraphic(null);
                box.getStyleClass().remove("foto-cell-box-clickable");
                if (empty) return;
                if (url != null && !url.isBlank()) {
                    try {
                        Image cached = thumbnailCache.computeIfAbsent(url,
                            u -> new Image(Path.of(u).toUri().toString(), 38, 38, true, true, true));
                        iv.setImage(cached);
                        iv.setVisible(true); lbl.setVisible(false);
                        box.getStyleClass().add("foto-cell-box-clickable");
                    } catch (Exception ex) { log.warn("No se pudo cargar thumbnail: {}", url, ex); iv.setVisible(false); lbl.setVisible(true); }
                } else {
                    iv.setImage(null); iv.setVisible(false); lbl.setVisible(true);
                }
                setGraphic(box);
                setAlignment(Pos.CENTER);
                setPadding(new Insets(3, 7, 3, 7));
            }
        });
    }

    static void configureNombre(TableColumn<Producto, String> col) {
        col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("nombre"));
        col.setCellFactory(column -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            { tip.setWrapText(true); tip.setMaxWidth(300); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setTooltip(null); return; }
                setText(item);
                Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                if (p != null && p.getCreadoEn() != null) {
                    String fecha = p.getCreadoEn().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    tip.setText(item + "\n\nRegistrado el " + fecha
                        + (p.getArea() != null ? "\nÁrea: " + p.getArea() : ""));
                } else {
                    tip.setText(item);
                }
                setTooltip(tip);
            }
        });
    }

    static void configureCodigo(TableColumn<Producto, String> col) {
        col.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("codigo"));
        col.setCellFactory(column -> new TableCell<>() {
            {
                setTooltip(new Tooltip("Clic para copiar el código"));
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("codigo-cell");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                getStyleClass().add("codigo-cell");
                setOnMouseClicked(e -> {
                    javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
                    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
                    cc.putString(item);
                    cb.setContent(cc);
                    NotificacionUtil.info(getScene(), "Código copiado: " + item);
                });
            }
        });
    }

    static void configureCategoria(TableColumn<Producto, String> col,
                                   Map<String, String> catIcon) {
        col.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getCategoriaNombre() != null
                ? c.getValue().getCategoriaNombre() : ""));
        col.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null); setText(null);
                if (empty || value == null || value.isBlank()) return;
                Producto p = getTableRow() != null ? (Producto) getTableRow().getItem() : null;
                String catColor = (p != null && p.getCategoriaColor() != null)
                    ? p.getCategoriaColor() : "#4338CA";
                String bgColor  = catColor + "22";
                Label lbl = new Label(value);
                String iconLiteral = catIcon.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(value))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse("mdi2t-tag-outline");
                FontIcon ico = new FontIcon(iconLiteral);
                ico.setIconSize(16);
                ico.setIconColor(javafx.scene.paint.Color.web(catColor));
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(5, ico, lbl);
                box.setAlignment(Pos.CENTER_LEFT);
                box.getStyleClass().add("cat-badge");
                box.setStyle("-fx-background-color: " + bgColor + ";");
                lbl.setStyle("-fx-text-fill: " + catColor + ";");
                setGraphic(box);
            }
        });
    }

    static void configureArea(TableColumn<Producto, String> col) {
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            c.getValue().getArea() != null ? c.getValue().getArea() : ""));
        col.setCellFactory(column -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) { setText(null); setTooltip(null); return; }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
    }

    static void configureStockYValor(TableColumn<Producto, Integer> colStock,
                                     TableColumn<Producto, String> colValor) {
        colStock.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("stockActual"));
        colValor.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(FormatUtils.formatCurrency(c.getValue().getPrecioVenta())));

        colStock.setCellFactory(column -> new TableCell<>() {
            private final Label numLabel = new Label();
            private final ProgressBar bar = new ProgressBar(0);
            private final HBox box = new HBox(5, bar, numLabel);
            {
                bar.setPrefHeight(6); bar.setMaxHeight(6); bar.setPrefWidth(44); bar.setMinWidth(44);
                bar.getStyleClass().add("stock-progress");
                numLabel.getStyleClass().add("stock-num");
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                getStyleClass().removeAll("stock-ok","stock-warn","stock-low");
                if (empty || value == null) { setGraphic(null); return; }
                Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                int max = p != null && p.getStockMaximo() > 0 ? p.getStockMaximo() : Math.max(value, 1);
                double pct = Math.min(1.0, (double) value / max);
                bar.setProgress(pct);
                numLabel.setText(String.valueOf(value));
                String statusClass = p == null ? "stock-ok" : switch (p.getEstado()) {
                    case AGOTADO    -> "stock-low";
                    case BAJO_STOCK -> "stock-warn";
                    default         -> "stock-ok";
                };
                getStyleClass().add(statusClass);
                bar.getStyleClass().removeAll("stock-progress-ok","stock-progress-warn","stock-progress-low");
                bar.getStyleClass().add(statusClass.replace("stock-ok","stock-progress-ok")
                    .replace("stock-warn","stock-progress-warn").replace("stock-low","stock-progress-low"));
                Tooltip.install(box, new Tooltip(value + " / " + max + " (máx)"));
                setGraphic(box);
            }
        });
    }

    static void configureEstado(TableColumn<Producto, String> col) {
        col.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getEstado().getEtiqueta()));

        col.setCellFactory(DialogUtil.badgeCellFactory(item -> switch (item) {
            case "Agotado"    -> "cell-badge-danger";
            case "Bajo Stock" -> "cell-badge-warning";
            case "Vencido"    -> "cell-badge-purple";
            default           -> "cell-badge-success";
        }));
    }

    static void configureStockMinMax(TableColumn<Producto, Integer> colMin,
                                     TableColumn<Producto, Integer> colMax,
                                     Consumer<Producto> onSave) {
        colMin.setCellValueFactory(c ->
            new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getStockMinimo()));
        colMax.setCellValueFactory(c ->
            new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getStockMaximo()));

        colMin.setCellFactory(col -> inlineIntCell(p -> p.getStockMinimo(), (p, v) -> p.setStockMinimo(v), onSave));
        colMax.setCellFactory(col -> inlineIntCell(p -> p.getStockMaximo(), (p, v) -> p.setStockMaximo(v), onSave));
    }

    private static TableCell<Producto, Integer> inlineIntCell(
            java.util.function.ToIntFunction<Producto> getter,
            java.util.function.ObjIntConsumer<Producto> setter,
            Consumer<Producto> onSave) {
        return new TableCell<>() {
            private final Label lbl = new Label();
            private final TextField tf = new TextField();
            {
                tf.getStyleClass().add("inline-edit-field");
                tf.setVisible(false); tf.setManaged(false);
                tf.setOnAction(e -> commit());
                tf.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused) commit();
                });
                tf.setOnKeyPressed(ev -> {
                    if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) cancel();
                });
                setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && getItem() != null) startEdit();
                });
            }

            private void commit() {
                if (!tf.isVisible()) return;
                try {
                    int val = Integer.parseInt(tf.getText().strip());
                    if (val < 0) { cancel(); return; }
                    Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                    if (p == null) { cancel(); return; }
                    setter.accept(p, val);
                    lbl.setText(String.valueOf(val));
                    tf.setVisible(false); tf.setManaged(false);
                    lbl.setVisible(true); lbl.setManaged(true);
                    onSave.accept(p);
                } catch (NumberFormatException ex) {
                    cancel();
                }
            }

            private void cancel() {
                tf.setVisible(false); tf.setManaged(false);
                lbl.setVisible(true); lbl.setManaged(true);
                updateItem(getItem(), isEmpty());
            }

            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || value == null) { setGraphic(null); return; }
                lbl.setText(String.valueOf(value));
                lbl.setVisible(true); lbl.setManaged(true);
                tf.setVisible(false); tf.setManaged(false);
                javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(lbl, tf);
                setGraphic(box);
            }

            @Override
            public void startEdit() {
                super.startEdit();
                Producto p = getTableRow() != null ? getTableRow().getItem() : null;
                if (p == null) return;
                tf.setText(String.valueOf(getter.applyAsInt(p)));
                lbl.setVisible(false); lbl.setManaged(false);
                tf.setVisible(true); tf.setManaged(true);
                tf.selectAll();
                tf.requestFocus();
            }
        };
    }

    static void configureRowFactory(TableView<Producto> table,
                                    Supplier<String> highlightIdSupplier) {
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                getStyleClass().removeAll("row-danger","row-warning","row-vencido","row-new");
                if (!empty && p != null) {
                    switch (p.getEstado()) {
                        case AGOTADO    -> getStyleClass().add("row-danger");
                        case BAJO_STOCK -> getStyleClass().add("row-warning");
                        case VENCIDO    -> getStyleClass().add("row-vencido");
                        default -> {}
                    }
                    if (p.getId() != null && p.getId().equals(highlightIdSupplier.get()))
                        getStyleClass().add("row-new");
                }
            }
        });
    }
}
