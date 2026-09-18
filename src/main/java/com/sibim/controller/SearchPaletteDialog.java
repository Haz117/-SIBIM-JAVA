package com.sibim.controller;

import com.sibim.model.Producto;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Command palette (Ctrl+K).
 * Default state: shows navigation entries. Typed text: filters nav entries
 * + searches bienes, resguardos and préstamos.
 */
public final class SearchPaletteDialog {

    public record NavEntry(String icon, String label, String shortcut, Runnable action) {}

    private SearchPaletteDialog() {}

    public static void show(Stage owner,
                            List<Producto> productos,
                            Consumer<Producto> onSelectProducto,
                            List<com.sibim.model.Resguardo> resguardos,
                            Consumer<com.sibim.model.Resguardo> onSelectResguardo,
                            List<com.sibim.model.Prestamo> prestamos,
                            Consumer<com.sibim.model.Prestamo> onSelectPrestamo,
                            List<NavEntry> navEntries) {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(null);
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.getDialogPane().lookup(".button-bar").setVisible(false);
        dialog.getDialogPane().lookup(".button-bar").setManaged(false);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().getStyleClass().add("search-palette-pane");

        // ── Search field ──────────────────────────────────────────────
        TextField searchField = new TextField();
        searchField.setPromptText("Navegar o buscar bienes, resguardos, préstamos…");
        searchField.getStyleClass().add("search-palette-field");
        FontIcon searchIcon = new FontIcon("mdi2m-magnify");
        searchIcon.getStyleClass().add("search-palette-icon");
        HBox searchRow = new HBox(8, searchIcon, searchField);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.getStyleClass().add("search-palette-header");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // ── Unified items list (String = group header, NavEntry, Producto, Resguardo, Prestamo) ──
        ObservableList<Object> allItems = FXCollections.observableArrayList();
        ListView<Object> listView = new ListView<>(allItems);
        listView.getStyleClass().add("search-palette-list");
        listView.setPrefHeight(360);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("palette-group-header-cell", "palette-nav-cell");
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;

                if (item instanceof String s) {
                    getStyleClass().add("palette-group-header-cell");
                    setText(s);

                } else if (item instanceof NavEntry ne) {
                    getStyleClass().add("palette-nav-cell");
                    FontIcon ico = new FontIcon(ne.icon());
                    ico.getStyleClass().add("palette-nav-icon");
                    Label lbl = new Label(ne.label());
                    lbl.getStyleClass().add("palette-item-nombre");
                    HBox.setHgrow(lbl, Priority.ALWAYS);
                    if (ne.shortcut() != null && !ne.shortcut().isBlank()) {
                        Label sc = new Label(ne.shortcut());
                        sc.getStyleClass().add("palette-item-meta");
                        setGraphic(new HBox(10, ico, lbl, sc));
                    } else {
                        setGraphic(new HBox(10, ico, lbl));
                    }
                    if (getGraphic() instanceof HBox hb) hb.setAlignment(Pos.CENTER_LEFT);

                } else if (item instanceof Producto p) {
                    Label nombre = new Label(p.getNombre());
                    nombre.getStyleClass().add("palette-item-nombre");
                    String metaText = (p.getCodigo() != null && !p.getCodigo().isBlank()
                            ? p.getCodigo() : "")
                            + (p.getArea() != null && !p.getArea().isBlank()
                            ? "  ·  " + p.getArea() : "");
                    Label meta = new Label(metaText.strip());
                    meta.getStyleClass().add("palette-item-meta");
                    VBox box = new VBox(2, nombre, meta);
                    box.getStyleClass().add("palette-item-box");
                    setGraphic(box);

                } else if (item instanceof com.sibim.model.Resguardo rsg) {
                    Label nombre = new Label(rsg.getResguardanteNombre() != null ? rsg.getResguardanteNombre() : "—");
                    nombre.getStyleClass().add("palette-item-nombre");
                    String metaText = (rsg.getNumero() != null ? rsg.getNumero() : "")
                        + (rsg.getResguardanteArea() != null ? "  ·  " + rsg.getResguardanteArea() : "")
                        + "  ·  " + (rsg.getEstado() != null ? rsg.getEstado() : "");
                    Label meta = new Label(metaText.strip());
                    meta.getStyleClass().add("palette-item-meta");
                    VBox box = new VBox(2, nombre, meta);
                    box.getStyleClass().add("palette-item-box");
                    setGraphic(box);

                } else if (item instanceof com.sibim.model.Prestamo prs) {
                    Label nombre = new Label(prs.getProductoNombre() != null ? prs.getProductoNombre() : "—");
                    nombre.getStyleClass().add("palette-item-nombre");
                    String metaText = (prs.getNumero() != null ? prs.getNumero() : "")
                        + (prs.getResponsableNombre() != null ? "  ·  " + prs.getResponsableNombre() : "")
                        + "  ·  " + (prs.getEstado() != null ? prs.getEstado() : "");
                    Label meta = new Label(metaText.strip());
                    meta.getStyleClass().add("palette-item-meta");
                    VBox box = new VBox(2, nombre, meta);
                    box.getStyleClass().add("palette-item-box");
                    setGraphic(box);
                }
            }
        });

        // Skip String group-header entries in keyboard selection
        listView.getSelectionModel().selectedIndexProperty().addListener((obs, old, newIdx) -> {
            int idx = newIdx.intValue();
            if (idx >= 0 && idx < allItems.size() && allItems.get(idx) instanceof String) {
                int next = idx + 1;
                Platform.runLater(() ->
                    listView.getSelectionModel().select(next < allItems.size() ? next : idx - 1));
            }
        });

        Label lblEmpty = new Label("Sin resultados");
        lblEmpty.getStyleClass().add("muted");
        lblEmpty.setAlignment(Pos.CENTER);
        lblEmpty.setMaxWidth(Double.MAX_VALUE);
        lblEmpty.setPadding(new Insets(20));
        lblEmpty.setVisible(false);
        lblEmpty.setManaged(false);

        // ── Execute selected item ─────────────────────────────────────
        Runnable executeSelected = () -> {
            Object sel = listView.getSelectionModel().getSelectedItem();
            if (sel instanceof NavEntry ne) {
                dialog.close();
                ne.action().run();
            } else if (sel instanceof Producto p) {
                dialog.setResult(p);
                dialog.close();
                onSelectProducto.accept(p);
            } else if (sel instanceof com.sibim.model.Resguardo rsg) {
                dialog.close();
                onSelectResguardo.accept(rsg);
            } else if (sel instanceof com.sibim.model.Prestamo prs) {
                dialog.close();
                onSelectPrestamo.accept(prs);
            }
        };

        // ── Rebuild list ──────────────────────────────────────────────
        Runnable rebuildList = () -> {
            String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            allItems.clear();

            if (q.isBlank()) {
                allItems.add("NAVEGAR A");
                allItems.addAll(navEntries);
                lblEmpty.setVisible(false);
                lblEmpty.setManaged(false);
                listView.setVisible(true);
                listView.setManaged(true);
                Platform.runLater(() -> { if (allItems.size() > 1) listView.getSelectionModel().select(1); });
            } else {
                List<NavEntry> matchedNav = navEntries.stream()
                    .filter(ne -> ne.label().toLowerCase().contains(q)).toList();
                if (!matchedNav.isEmpty()) {
                    allItems.add("NAVEGAR A");
                    allItems.addAll(matchedNav);
                }
                List<Producto> matchedProd = productos.stream()
                    .filter(p -> p.getNombre().toLowerCase().contains(q)
                        || (p.getCodigo()       != null && p.getCodigo().toLowerCase().contains(q))
                        || (p.getArea()         != null && p.getArea().toLowerCase().contains(q))
                        || (p.getResguardante() != null && p.getResguardante().toLowerCase().contains(q)))
                    .limit(8).toList();
                if (!matchedProd.isEmpty()) {
                    allItems.add("BIENES");
                    allItems.addAll(matchedProd);
                }
                List<com.sibim.model.Resguardo> matchedRsg = resguardos.stream()
                    .filter(r -> (r.getResguardanteNombre() != null && r.getResguardanteNombre().toLowerCase().contains(q))
                        || (r.getNumero() != null && r.getNumero().toLowerCase().contains(q))
                        || (r.getResguardanteArea() != null && r.getResguardanteArea().toLowerCase().contains(q)))
                    .limit(5).toList();
                if (!matchedRsg.isEmpty()) {
                    allItems.add("RESGUARDOS");
                    allItems.addAll(matchedRsg);
                }
                List<com.sibim.model.Prestamo> matchedPrs = prestamos.stream()
                    .filter(p -> (p.getProductoNombre() != null && p.getProductoNombre().toLowerCase().contains(q))
                        || (p.getNumero() != null && p.getNumero().toLowerCase().contains(q))
                        || (p.getResponsableNombre() != null && p.getResponsableNombre().toLowerCase().contains(q))
                        || (p.getAreaDestino() != null && p.getAreaDestino().toLowerCase().contains(q)))
                    .limit(5).toList();
                if (!matchedPrs.isEmpty()) {
                    allItems.add("PRÉSTAMOS");
                    allItems.addAll(matchedPrs);
                }
                boolean empty = allItems.isEmpty();
                lblEmpty.setVisible(empty);
                lblEmpty.setManaged(empty);
                listView.setVisible(!empty);
                listView.setManaged(!empty);
                if (!empty) {
                    Platform.runLater(() -> {
                        for (int i = 0; i < allItems.size(); i++) {
                            if (!(allItems.get(i) instanceof String)) {
                                listView.getSelectionModel().select(i);
                                break;
                            }
                        }
                    });
                }
            }
        };

        rebuildList.run();

        PauseTransition debounce = new PauseTransition(Duration.millis(180));
        debounce.setOnFinished(e -> rebuildList.run());
        searchField.textProperty().addListener((obs, ov, nv) -> { debounce.stop(); debounce.playFromStart(); });

        // ── Keyboard nav ──────────────────────────────────────────────
        searchField.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case DOWN -> {
                    listView.requestFocus();
                    if (listView.getSelectionModel().getSelectedIndex() < 0) {
                        for (int i = 0; i < allItems.size(); i++) {
                            if (!(allItems.get(i) instanceof String)) {
                                listView.getSelectionModel().select(i);
                                break;
                            }
                        }
                    }
                    e.consume();
                }
                case ENTER -> { executeSelected.run(); e.consume(); }
                case ESCAPE -> { dialog.close(); e.consume(); }
                default -> {}
            }
        });

        listView.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> { executeSelected.run(); e.consume(); }
                case ESCAPE -> { dialog.close(); e.consume(); }
                case UP -> {
                    int idx = listView.getSelectionModel().getSelectedIndex();
                    boolean atTop = idx <= 0 || (idx == 1 && allItems.get(0) instanceof String);
                    if (atTop) { searchField.requestFocus(); e.consume(); }
                }
                default -> {}
            }
        });

        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) executeSelected.run();
        });

        VBox content = new VBox(0, searchRow, new Separator(), listView, lblEmpty);
        dialog.getDialogPane().setContent(content);
        com.sibim.util.AnimationUtils.springIn(content);
        Platform.runLater(searchField::requestFocus);
        dialog.showAndWait();
    }
}
