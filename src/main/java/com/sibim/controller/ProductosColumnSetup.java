package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.DialogUtil;
import com.sibim.util.FormatUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;

import java.time.format.DateTimeFormatter;
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
            private final Tooltip tip = new Tooltip();
            {
                iv.setFitWidth(38); iv.setFitHeight(38); iv.setPreserveRatio(true);
                lbl.setIconSize(16);
                lbl.getStyleClass().add("foto-placeholder");
                box = new StackPane(lbl, iv);
                box.setPrefSize(40, 40); box.setMinSize(40, 40); box.setMaxSize(40, 40);
                box.getStyleClass().add("foto-cell-box");
                Tooltip.install(box, tip);
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
                        Image cached = thumbnailCache.get(url);
                        if (cached == null || cached.isError()) {
                            boolean remote = url.startsWith("http://") || url.startsWith("https://");
                            String imageUri = com.sibim.util.ImageUtils.uri(url);
                            // backgroundLoading only for remote — local files are instant
                            cached = new Image(imageUri, 38, 38, true, true, remote);
                            if (!cached.isError()) thumbnailCache.put(url, cached);
                        }
                        if (cached.isError()) {
                            iv.setImage(null); iv.setVisible(false); lbl.setVisible(true);
                            tip.setText("Sin foto");
                        } else {
                            iv.setImage(cached);
                            iv.setVisible(true); lbl.setVisible(false);
                            box.getStyleClass().add("foto-cell-box-clickable");
                            tip.setText("Foto — clic para ampliar");
                        }
                    } catch (Exception ex) {
                        log.warn("No se pudo cargar thumbnail: {}", url, ex);
                        iv.setVisible(false); lbl.setVisible(true);
                        tip.setText("Sin foto");
                    }
                } else {
                    iv.setImage(null); iv.setVisible(false); lbl.setVisible(true);
                    tip.setText("Sin foto");
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
                    String fecha = p.getCreadoEn().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
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
                setTooltip(new Tooltip("Ctrl+C copia el código de las filas seleccionadas (también en el clic derecho)"));
            }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("codigo-cell");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                getStyleClass().add("codigo-cell");
            }
        });
    }

    static void configureCategoria(TableColumn<Producto, String> col) {
        col.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getCategoriaNombre() != null
                ? c.getValue().getCategoriaNombre() : ""));
        col.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(null); setText(null); setTooltip(null);
                if (empty || value == null || value.isBlank()) return;
                Producto p = getTableRow() != null ? (Producto) getTableRow().getItem() : null;
                String catColor = (p != null && p.getCategoriaColor() != null)
                    ? p.getCategoriaColor() : "#9F2241";
                String bgColor  = catColor + "14";
                // The short name («Cómputo»), the whole CONAC one on hover (19-nov-2026: «Equipo de D…», «Equipo de C…»).
                Label lbl = new Label(com.sibim.util.CategoriaCorta.de(value));
                setTooltip(new javafx.scene.control.Tooltip(value));
                FontIcon ico = com.sibim.util.CategoriaIcons.iconFor(value);
                ico.setIconSize(16);
                ico.setIconColor(javafx.scene.paint.Color.web(catColor));
                javafx.scene.layout.HBox box = new javafx.scene.layout.HBox(5, ico, lbl);
                box.setAlignment(Pos.CENTER_LEFT);
                box.getStyleClass().add("cat-badge");
                box.setStyle("-fx-background-color: " + bgColor + ";");
                lbl.setStyle("-fx-text-fill: #334155;");
                setGraphic(box);
            }
        });
    }

    static void configureArea(TableColumn<Producto, String> col) {
        col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
            java.util.Objects.requireNonNullElse(c.getValue().getArea(), "")));
        com.sibim.util.CeldaArea.instalar(col);
    }

    /** Who holds the bien (kept in sync with its active resguardo); a dash
     *  instead of an empty cell so "no one" reads as a fact, not missing data. */
    static void configureResguardante(TableColumn<Producto, String> col) {
        if (col == null) return;
        configureTextWithTooltip(col, Producto::getResguardante, "—");
        FichaPersona.columnaDeNombre(col);
    }

    private static void configureTextWithTooltip(TableColumn<Producto, String> col,
            java.util.function.Function<Producto, String> getter, String blankText) {
        col.setCellValueFactory(c -> {
            String v = getter.apply(c.getValue());
            return new javafx.beans.property.SimpleStringProperty(v != null ? v : "");
        });
        col.setCellFactory(column -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("muted-text");
                if (empty) { setText(null); setTooltip(null); return; }
                if (item == null || item.isBlank()) {
                    setText(blankText); setTooltip(null);
                    if (blankText != null) getStyleClass().add("muted-text");
                    return;
                }
                setText(item);
                tip.setText(item);
                setTooltip(tip);
            }
        });
    }

    static void configureStockYValor(TableColumn<Producto, Integer> colStock,
                                     TableColumn<Producto, String> colValor) {
        colStock.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("stockActual"));
        // «—» for a bien without its costo captured (1,089 on 17-nov-2026): «$0.00» read as worth nothing.
        colValor.setCellValueFactory(c -> {
            java.math.BigDecimal v = c.getValue().getValorUnitario();
            return new javafx.beans.property.SimpleStringProperty(v == null || v.signum() == 0 ? "—" : FormatUtils.formatCurrency(v));
        });

        colStock.setCellFactory(column -> new TableCell<>() {
            private final Label numLabel = new Label();
            private final ProgressBar bar = new ProgressBar(0);
            private final HBox box = new HBox(5, bar, numLabel);
            {
                bar.setPrefHeight(6); bar.setMaxHeight(6); bar.setPrefWidth(34); bar.setMinWidth(34);
                numLabel.setMinWidth(Region.USE_PREF_SIZE);   // never "…" for the count itself
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
                // Just the number: a bar against a "maximum stock" is shop logic.
                bar.setVisible(false);
                bar.setManaged(false);
                numLabel.setText(String.valueOf(value));
                String statusClass = p == null ? "stock-ok" : switch (p.getEstado()) {
                    default         -> "stock-ok";
                };
                getStyleClass().add(statusClass);
                bar.getStyleClass().removeAll("stock-progress-ok","stock-progress-warn","stock-progress-low");
                bar.getStyleClass().add(statusClass.replace("stock-ok","stock-progress-ok")
                    .replace("stock-warn","stock-progress-warn").replace("stock-low","stock-progress-low"));

                setGraphic(box);
            }
        });
    }

    /** The column is the garantía. It was "Estado: Activo" on every bien (almost none has a
     *  garantía captured), which read as the bien's own state: now Vigente, Vencida, or
     *  nothing when it has none. */
    static void configureEstado(TableColumn<Producto, String> col) {
        col.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(garantia(c.getValue())));

        col.setCellFactory(DialogUtil.iconBadgeCellFactory(
            item -> switch (item) {
                case "Vencida"    -> "cell-badge-purple";
                case "Vigente"    -> "cell-badge-success";
                default           -> null;
            },
            item -> switch (item) {
                case "Vencida"    -> "mdi2c-clock-alert-outline";
                default           -> "mdi2c-check-circle-outline";
            }
        ));
    }

    static String garantia(Producto p) {
        if (p.getEstado() == com.sibim.model.enums.EstadoProducto.VENCIDO) return "Vencida";
        return p.getFechaVencimiento() != null ? "Vigente" : "—";
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
