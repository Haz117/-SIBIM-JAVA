package com.sibim.controller;

import com.sibim.model.Producto;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;

/**
 * Focused product-search palette (Ctrl+K).
 * Lets the user search all bienes by name, code, area or resguardante,
 * then navigate directly to that item in the Productos table.
 */
public final class SearchPaletteDialog {

    private SearchPaletteDialog() {}

    public static void show(Stage owner, List<Producto> productos, Consumer<Producto> onSelect) {
        Dialog<Producto> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(null);
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        // hide the default button bar
        dialog.getDialogPane().lookup(".button-bar").setVisible(false);
        dialog.getDialogPane().lookup(".button-bar").setManaged(false);
        dialog.getDialogPane().setPrefWidth(540);
        dialog.getDialogPane().getStyleClass().add("search-palette-pane");

        // ── Search field ──
        TextField searchField = new TextField();
        searchField.setPromptText("Buscar bien por nombre, código o área…");
        searchField.getStyleClass().add("search-palette-field");
        FontIcon searchIcon = new FontIcon("mdi2m-magnify");
        searchIcon.getStyleClass().add("search-palette-icon");
        HBox searchRow = new HBox(8, searchIcon, searchField);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.getStyleClass().add("search-palette-header");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // ── Results list ──
        FilteredList<Producto> filtered =
                new FilteredList<>(FXCollections.observableArrayList(productos));
        ListView<Producto> listView = new ListView<>(filtered);
        listView.getStyleClass().add("search-palette-list");
        listView.setPrefHeight(320);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Producto p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); setGraphic(null); return; }
                Label nombre = new Label(p.getNombre());
                nombre.getStyleClass().add("palette-item-nombre");
                String metaText = (p.getCodigo() != null && !p.getCodigo().isBlank()
                        ? p.getCodigo() : "")
                        + (p.getArea() != null && !p.getArea().isBlank()
                        ? "  ·  " + p.getArea() : "");
                Label meta = new Label(metaText);
                meta.getStyleClass().add("palette-item-meta");
                VBox box = new VBox(2, nombre, meta);
                box.getStyleClass().add("palette-item-box");
                setGraphic(box);
                setText(null);
            }
        });

        Label lblEmpty = new Label("Sin resultados");
        lblEmpty.getStyleClass().add("muted");
        lblEmpty.setAlignment(Pos.CENTER);
        lblEmpty.setMaxWidth(Double.MAX_VALUE);
        lblEmpty.setPadding(new Insets(20));
        lblEmpty.setVisible(false);
        lblEmpty.setManaged(false);

        // ── Filter logic ──
        searchField.textProperty().addListener((obs, old, val) -> {
            String q = val == null ? "" : val.trim().toLowerCase();
            filtered.setPredicate(p -> q.isBlank()
                    || p.getNombre().toLowerCase().contains(q)
                    || (p.getCodigo() != null && p.getCodigo().toLowerCase().contains(q))
                    || (p.getArea() != null && p.getArea().toLowerCase().contains(q))
                    || (p.getResguardante() != null && p.getResguardante().toLowerCase().contains(q)));
            boolean empty = filtered.isEmpty();
            lblEmpty.setVisible(empty);
            lblEmpty.setManaged(empty);
            listView.setVisible(!empty);
            listView.setManaged(!empty);
            if (!empty) listView.getSelectionModel().selectFirst();
        });

        // Helper: accept selection and close
        Runnable[] acceptRef = {null};
        acceptRef[0] = () -> {
            Producto sel = listView.getSelectionModel().getSelectedItem();
            if (sel == null && !filtered.isEmpty()) sel = filtered.get(0);
            if (sel != null) {
                dialog.setResult(sel);
                dialog.close();
                onSelect.accept(sel);
            }
        };

        // ── Keyboard navigation ──
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listView.requestFocus();
                if (listView.getSelectionModel().getSelectedIndex() < 0)
                    listView.getSelectionModel().selectFirst();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                acceptRef[0].run();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
                e.consume();
            }
        });
        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Producto sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null) { dialog.setResult(sel); dialog.close(); onSelect.accept(sel); }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE
                    || (e.getCode() == KeyCode.UP
                            && listView.getSelectionModel().getSelectedIndex() == 0)) {
                searchField.requestFocus();
                e.consume();
            }
        });
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Producto sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null) { dialog.setResult(sel); dialog.close(); onSelect.accept(sel); }
            }
        });

        listView.getSelectionModel().selectFirst();
        VBox content = new VBox(0, searchRow, new Separator(), listView, lblEmpty);
        dialog.getDialogPane().setContent(content);
        Platform.runLater(searchField::requestFocus);
        dialog.showAndWait();
    }
}
