package com.sibim.controller;

import com.sibim.model.Producto;
import com.sibim.util.AccessibilityUtils;
import com.sibim.util.FormatUtils;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** "Pendientes patrimoniales": bienes whose control record is incomplete —
 *  nobody signed for them (sin resguardante) or they carry no physical label
 *  (sin etiquetar). Stock-based alerts say nothing about an inventory where
 *  every bien is a single numbered item; these are what an audit asks for.
 *  Not counted in the red alert badge: they're paperwork to catch up on, not
 *  an emergency. */
class AlertasPatrimonialesSection {

    private static final String PREF_KEY = "alertas.patrimoniales.colapsado";
    private static final String BADGE_ID = "patrimonialesCountBadge";
    private static final String TABLE_ID = "tablePatrimoniales";

    /** One row per bien, with what's missing. */
    record Pendiente(Producto producto, String falta) {}

    static List<Pendiente> pendientes(List<Producto> bienes) {
        List<Pendiente> out = new ArrayList<>();
        for (Producto p : bienes) {
            List<String> falta = new ArrayList<>(2);
            if (p.getResguardante() == null || p.getResguardante().isBlank()) falta.add("Sin resguardante");
            if (!p.isEtiquetado()) falta.add("Sin etiquetar");
            if (!falta.isEmpty()) out.add(new Pendiente(p, String.join(" · ", falta)));
        }
        return out;
    }

    static VBox build(java.util.prefs.Preferences sticky, Supplier<javafx.scene.Node> okPlaceholder,
                      Consumer<Producto> abrirBien) {
        Label countBadge = new Label("0 bienes");
        countBadge.getStyleClass().add("badge-count-inv");
        countBadge.setId(BADGE_ID);

        FontIcon chevron = new FontIcon("mdi2c-chevron-up");
        chevron.getStyleClass().add("alert-section-chevron");

        FontIcon headerIcon = new FontIcon("mdi2c-clipboard-check-outline");
        headerIcon.getStyleClass().add("alert-section-icon");
        Label titleLbl = new Label("Pendientes patrimoniales");
        titleLbl.getStyleClass().add("alert-section-title-inv");
        Label subtitleLbl = new Label("Bienes sin resguardante asignado o sin etiqueta física");
        subtitleLbl.getStyleClass().add("alert-section-subtitle");
        VBox titleBox = new VBox(1, titleLbl, subtitleLbl);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        HBox header = new HBox(12, headerIcon, titleBox, countBadge, chevron);
        header.getStyleClass().add("alert-header-info");
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TableView<Pendiente> table = buildTable(okPlaceholder, abrirBien);
        VBox content = new VBox(10, table);
        content.setPadding(new javafx.geometry.Insets(14));

        boolean collapsed = sticky.getBoolean(PREF_KEY, false);
        AlertasComodatosSection.applyCollapsed(content, chevron, collapsed);
        header.setCursor(javafx.scene.Cursor.HAND);
        header.setOnMouseClicked(e -> {
            boolean nowCollapsed = content.isVisible();
            AlertasComodatosSection.applyCollapsed(content, chevron, nowCollapsed);
            sticky.putBoolean(PREF_KEY, nowCollapsed);
        });
        AccessibilityUtils.asButton(header, "Plegar o desplegar pendientes patrimoniales");

        VBox section = new VBox(0, header, content);
        section.getStyleClass().addAll("alert-section", "alert-info");
        section.setVisible(false);
        section.setManaged(false);
        return section;
    }

    @SuppressWarnings("unchecked")
    static void update(VBox section, List<Pendiente> pendientes) {
        if (section == null) return;
        boolean hay = !pendientes.isEmpty();
        section.setVisible(hay);
        section.setManaged(hay);
        if (!hay) return;
        section.lookupAll("#" + BADGE_ID).forEach(n -> {
            if (n instanceof Label lbl) lbl.setText(FormatUtils.plural(pendientes.size(), "bien", "bienes"));
        });
        section.lookupAll("#" + TABLE_ID).forEach(n -> {
            if (n instanceof TableView<?> tv) ((TableView<Pendiente>) tv).getItems().setAll(pendientes);
        });
    }

    private static TableView<Pendiente> buildTable(Supplier<javafx.scene.Node> okPlaceholder,
                                                   Consumer<Producto> abrirBien) {
        TableView<Pendiente> table = new TableView<>();
        table.setPrefHeight(220);
        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setId(TABLE_ID);
        table.setPlaceholder(okPlaceholder.get());

        TableColumn<Pendiente, String> colNombre = new TableColumn<>("Bien");
        colNombre.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().producto().getNombre()));
        colNombre.setPrefWidth(240);
        TableColumn<Pendiente, String> colCodigo = new TableColumn<>("Código");
        colCodigo.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().producto().getCodigo()));
        colCodigo.setPrefWidth(110);
        TableColumn<Pendiente, String> colArea = new TableColumn<>("Área");
        colArea.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().producto().getArea()));
        colArea.setPrefWidth(200);
        TableColumn<Pendiente, String> colFalta = new TableColumn<>("Qué falta");
        colFalta.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().falta()));
        colFalta.setPrefWidth(200);
        table.getColumns().addAll(java.util.List.of(colNombre, colCodigo, colArea, colFalta));

        table.setOnMouseClicked(e -> {
            Pendiente sel = table.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) abrirBien.accept(sel.producto());
        });
        table.setOnKeyPressed(e -> {
            Pendiente sel = table.getSelectionModel().getSelectedItem();
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER && sel != null) { abrirBien.accept(sel.producto()); e.consume(); }
        });
        return table;
    }
}
